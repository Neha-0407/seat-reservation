import base64
import json
import threading
import unittest
import uuid
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from burst import ApiResult, classify, jwt_subject, metric_value, run_burst


def fake_token(subject: str) -> str:
    payload = base64.urlsafe_b64encode(json.dumps({"sub": subject}).encode()).decode().rstrip("=")
    return f"header.{payload}.signature"


class FakeApiState:
    def __init__(self):
        self.lock = threading.Lock()
        self.shows = {}
        self.reservations = {}
        self.confirmed = 0
        self.replays = 0
        self.declines = Counter()


class FakeApiHandler(BaseHTTPRequestHandler):
    def log_message(self, format_string, *args):
        return

    def send_json(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        state = self.server.state
        if self.path == "/actuator/prometheus":
            with state.lock:
                lines = [
                    f"seat_reservations_confirmed_total {state.confirmed}",
                    f"seat_reservations_idempotent_replays_total {state.replays}",
                ]
                lines.extend(
                    f'seat_reservations_declined_total{{reason="{reason}"}} {count}'
                    for reason, count in state.declines.items()
                )
                lines.extend(
                    f'seat_reservation_seats_available{{show_id="{show_id}"}} '
                    f'{sum(not taken for taken in show["seats"].values())}'
                    for show_id, show in state.shows.items()
                )
            payload = ("\n".join(lines) + "\n").encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/plain; version=0.0.4")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return
        if self.path.startswith("/shows/"):
            show_id = self.path.rsplit("/", 1)[-1]
            show = state.shows[show_id]
            available = sum(not taken for taken in show["seats"].values())
            confirmed = len(show["seats"]) - available
            self.send_json(200, {
                "id": show_id,
                "total_seats": len(show["seats"]),
                "available_seats": available,
                "held_seats": 0,
                "confirmed_seats": confirmed,
                "seats": [],
            })
            return
        self.send_json(404, {"message": "not found"})

    def do_POST(self):
        raw_body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        body = json.loads(raw_body) if raw_body else {}
        state = self.server.state
        if self.path == "/shows":
            show_id = str(uuid.uuid4())
            state.shows[show_id] = {
                "seats": {seat: False for seat in body["seats"]},
                "limit": body.get("per_user_limit", 4),
                "user_counts": Counter(),
            }
            self.send_json(201, {"id": show_id})
            return

        if self.path.startswith("/reservations/"):
            reservation_id = self.path.strip("/").split("/")[1]
            token = self.headers.get("Authorization", "").removeprefix("Bearer ")
            user_id = jwt_subject(token)
            with state.lock:
                reservation_entry = next(
                    (
                        entry
                        for entry in state.reservations.values()
                        if entry[1]["reservation_id"] == reservation_id
                    ),
                    None,
                )
                if reservation_entry is None:
                    self.send_json(404, {"message": "reservation not found"})
                    return
                reservation = reservation_entry[1]
                if reservation["user_id"] != user_id:
                    self.send_json(403, {"message": "reservation belongs to another user"})
                    return
                show = state.shows[reservation["show_id"]]
                for seat in reservation["seats"]:
                    show["seats"][seat] = False
                reservation["status"] = "cancelled"
            self.send_json(200, reservation)
            return

        parts = self.path.strip("/").split("/")
        show_id = parts[1]
        show = state.shows[show_id]
        token = self.headers.get("Authorization", "").removeprefix("Bearer ")
        user_id = jwt_subject(token)
        seats = sorted(body["seats"])
        key = (show_id, user_id, body["idempotency_key"])
        request_hash = tuple(seats)
        with state.lock:
            if key in state.reservations:
                original_hash, original = state.reservations[key]
                if original_hash != request_hash:
                    state.declines["idempotency-key-reused"] += 1
                    self.send_json(409, {"message": "Idempotency key was already used with different seats"})
                    return
                state.replays += 1
                self.send_json(201, original)
                return
            if show["user_counts"][user_id] + len(seats) > show["limit"]:
                state.declines["per-user-limit"] += 1
                self.send_json(409, {"message": "Seat selection exceeds per-user limit"})
                return
            if any(seat not in show["seats"] or show["seats"][seat] for seat in seats):
                state.declines["seat-taken"] += 1
                self.send_json(409, {"message": "Seat is already taken"})
                return
            for seat in seats:
                show["seats"][seat] = True
            show["user_counts"][user_id] += len(seats)
            state.confirmed += 1
            reservation = {
                "reservation_id": str(uuid.uuid4()),
                "show_id": show_id,
                "user_id": user_id,
                "seats": seats,
                "amount_paise": len(seats) * 25000,
                "status": "confirmed",
            }
            state.reservations[key] = (request_hash, reservation)
        self.send_json(201, reservation)


class BurstScriptTests(unittest.TestCase):
    def test_classifies_domain_declines_and_transport_failures(self):
        self.assertEqual(classify(ApiResult(409, {"message": "Seat is already taken"})), "seat-taken")
        self.assertEqual(classify(ApiResult(409, {"message": "Seat selection exceeds per-user limit"})), "per-user-limit")
        self.assertEqual(classify(ApiResult(503, {})), "5xx")
        self.assertEqual(classify(ApiResult(0, {}, "connection refused")), "network-error")
        self.assertEqual(classify(ApiResult(0, {})), "network-error")

    def test_parses_prometheus_metric_with_labels(self):
        text = 'seat_reservation_seats_available{show_id="show-1"} 7.0\n'
        self.assertEqual(metric_value(text, "seat_reservation_seats_available", {"show_id": "show-1"}), 7)

    def test_full_burst_against_concurrent_fake_api(self):
        server = ThreadingHTTPServer(("127.0.0.1", 0), FakeApiHandler)
        server.state = FakeApiState()
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            tokens = [fake_token(f"buyer-{index}") for index in range(2)]
            messages = []
            report = run_burst(
                f"http://127.0.0.1:{server.server_port}",
                "admin-token",
                tokens,
                hot_seat_requests=20,
                per_user_requests=10,
                workers=8,
                timeout=2,
                emit=messages.append,
            )
            self.assertTrue(report["ok"], messages)
            self.assertEqual(report["hot_outcomes"]["confirmed"], 1)
            self.assertEqual(report["hot_outcomes"]["seat-taken"], 19)
            self.assertEqual(report["limit_outcomes"]["confirmed"], 4)
            self.assertEqual(report["limit_outcomes"]["per-user-limit"], 6)
            self.assertTrue(report["identity_ok"], messages)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()