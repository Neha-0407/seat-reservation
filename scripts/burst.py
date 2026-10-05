#!/usr/bin/env python3
"""Exercise hot-seat contention, idempotency, limits, and metrics on a live API."""

from __future__ import annotations

import argparse
import asyncio
import base64
import json
import math
import os
import re
import sys
import uuid
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Callable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

import aiohttp


@dataclass(frozen=True)
class ApiResult:
    status: int
    body: object
    error: str | None = None


def jwt_subject(token: str) -> str:
    parts = token.split(".")
    if len(parts) != 3:
        raise ValueError("User tokens must be JWTs with a subject claim")
    payload = parts[1]
    payload += "=" * (-len(payload) % 4)
    claims = json.loads(base64.urlsafe_b64decode(payload.encode("ascii")))
    subject = claims.get("sub")
    if not isinstance(subject, str) or not subject:
        raise ValueError("User token is missing a subject claim")
    return subject


def load_user_tokens(path: Path, minimum_count: int = 2) -> list[str]:
    tokens = []
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.lower().startswith("bearer "):
            line = line[7:].strip()
        tokens.append(line)

    if len(tokens) < minimum_count:
        raise ValueError(f"Need at least {minimum_count} user tokens; found {len(tokens)}")

    subjects = [jwt_subject(token) for token in tokens]
    if len(set(subjects)) != len(subjects):
        raise ValueError("Each token in the pool must have a distinct subject")
    return tokens


def _decode_json(data: bytes) -> object:
    if not data:
        return {}
    try:
        return json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return {"text": data.decode("utf-8", errors="replace")}


def api_request(
    base_url: str,
    method: str,
    path: str,
    token: str | None = None,
    body: dict | None = None,
    timeout: float = 30,
    accept: str = "application/json",
) -> ApiResult:
    headers = {"Accept": accept, "X-Request-ID": str(uuid.uuid4())}
    data = None
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body, separators=(",", ":")).encode("utf-8")

    request = Request(f"{base_url.rstrip('/')}{path}", data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=timeout) as response:
            return ApiResult(response.status, _decode_json(response.read()))
    except HTTPError as error:
        return ApiResult(error.code, _decode_json(error.read()))
    except (URLError, TimeoutError, OSError) as error:
        return ApiResult(0, {}, str(error))


def classify(result: ApiResult) -> str:
    if result.error or result.status == 0:
        return "network-error"
    if result.status == 201:
        return "confirmed"
    if result.status == 409:
        message = str(result.body.get("message", "")).lower() if isinstance(result.body, dict) else ""
        if "already taken" in message:
            return "seat-taken"
        if "per-user limit" in message:
            return "per-user-limit"
        if "idempotency key was already used" in message:
            return "idempotency-key-reused"
        return "other-409"
    if 500 <= result.status <= 599:
        return "5xx"
    return f"http-{result.status}"


def metric_value(text: str, name: str, labels: dict[str, str] | None = None) -> float:
    expected = labels or {}
    for line in text.splitlines():
        if line.startswith("#") or not line.startswith(name):
            continue
        metric, _, value = line.partition(" ")
        if not value:
            continue
        actual_labels: dict[str, str] = {}
        if "{" in metric and metric.endswith("}"):
            label_text = metric[metric.find("{") + 1 : -1]
            actual_labels = dict(re.findall(r'([a-zA-Z_][a-zA-Z0-9_]*)="([^"]*)"', label_text))
        elif metric != name:
            continue
        if all(actual_labels.get(key) == expected_value for key, expected_value in expected.items()):
            try:
                return float(value)
            except ValueError:
                continue
    return 0.0


def fetch_metrics(base_url: str, timeout: float) -> str:
    result = api_request(
        base_url,
        "GET",
        "/actuator/prometheus",
        timeout=timeout,
        accept="text/plain; version=0.0.4",
    )
    if result.status != 200:
        detail = result.error or result.body
        raise RuntimeError(f"Prometheus scrape failed with HTTP {result.status}: {detail}")
    if not isinstance(result.body, dict) or "text" not in result.body:
        raise RuntimeError("Prometheus endpoint did not return a text scrape")
    return str(result.body["text"])


def post_json(base_url: str, path: str, token: str, body: dict, timeout: float) -> ApiResult:
    return api_request(base_url, "POST", path, token, body, timeout)


def create_show(
    base_url: str,
    admin_token: str,
    name: str,
    seat_numbers: list[str],
    timeout: float,
) -> str:
    result = post_json(
        base_url,
        "/shows",
        admin_token,
        {"name": name, "seats": seat_numbers, "price_paise": 25000, "per_user_limit": 4},
        timeout,
    )
    if result.status != 201 or not isinstance(result.body, dict) or not result.body.get("id"):
        raise RuntimeError(f"Create-show failed with HTTP {result.status}: {result.body}")
    return str(result.body["id"])


async def run_parallel_with_monitor(
    requests: list[tuple[str, str, dict]],
    workers: int,
    base_url: str,
    show_id: str,
    timeout: float,
) -> tuple[list[ApiResult], int, list[str]]:
    stop = asyncio.Event()
    started = asyncio.Event()
    load_started = asyncio.Event()
    observations = 0
    failures: list[str] = []
    client_timeout = aiohttp.ClientTimeout(total=timeout)
    connector = aiohttp.TCPConnector(limit=workers, limit_per_host=workers)

    async with aiohttp.ClientSession(
        timeout=client_timeout,
        connector=connector,
    ) as session:
        async def monitor() -> None:
            nonlocal observations
            while not stop.is_set():
                try:
                    state = await asyncio.to_thread(show_state, base_url, show_id, timeout)
                    valid, summary = check_state_invariant(state)
                    if load_started.is_set():
                        observations += 1
                    if not valid:
                        failures.append(f"in-flight reconciliation failed: {summary}")
                except RuntimeError as error:
                    failures.append(f"in-flight reconciliation request failed: {error}")
                started.set()
                try:
                    await asyncio.wait_for(stop.wait(), timeout=0.05)
                except asyncio.TimeoutError:
                    pass

        async def send_request(path: str, token: str, body: dict) -> ApiResult:
            headers = {
                "Accept": "application/json",
                "Authorization": f"Bearer {token}",
                "X-Request-ID": str(uuid.uuid4()),
            }
            try:
                async with session.post(
                    f"{base_url.rstrip('/')}{path}",
                    json=body,
                    headers=headers,
                ) as response:
                    return ApiResult(response.status, _decode_json(await response.read()))
            except (aiohttp.ClientError, asyncio.TimeoutError, OSError) as error:
                return ApiResult(0, {}, str(error))

        monitor_task = asyncio.create_task(monitor())
        try:
            await asyncio.wait_for(started.wait(), timeout=timeout + 1)
        except asyncio.TimeoutError:
            failures.append("in-flight reconciliation monitor did not start")
        load_started.set()
        try:
            results = await asyncio.gather(
                *(send_request(path, token, body) for path, token, body in requests)
            )
        finally:
            stop.set()
            await monitor_task
    return results, observations, failures


def show_state(base_url: str, show_id: str, timeout: float) -> dict:
    result = api_request(base_url, "GET", f"/shows/{show_id}", timeout=timeout)
    if result.status != 200 or not isinstance(result.body, dict):
        raise RuntimeError(f"Show-state request failed with HTTP {result.status}: {result.body}")
    return result.body


def check_state_invariant(state: dict) -> tuple[bool, str]:
    available = int(state["available_seats"])
    held = int(state["held_seats"])
    confirmed = int(state["confirmed_seats"])
    total = int(state["total_seats"])
    valid = available + held + confirmed == total
    return valid, f"total={total} available={available} held={held} confirmed={confirmed}"


def check_reconciliation(
    base_url: str,
    show_id: str,
    state: dict,
    scrape: str,
) -> tuple[bool, str]:
    invariant_ok, state_summary = check_state_invariant(state)
    available = int(state["available_seats"])
    gauge = metric_value(
        scrape,
        "seat_reservation_seats_available",
        {"show_id": show_id},
    )
    gauge_ok = math.isclose(gauge, available, abs_tol=0.001)
    summary = f"show={show_id} {state_summary} gauge={gauge:g}"
    return invariant_ok and gauge_ok, summary


def run_burst(
    base_url: str,
    admin_token: str,
    user_tokens: list[str],
    hot_seat_requests: int = 20,
    per_user_requests: int = 10,
    workers: int = 64,
    timeout: float = 30,
    emit: Callable[[str], None] = print,
) -> dict:
    if hot_seat_requests < 2:
        raise ValueError("Hot-seat test needs at least two requests")
    if per_user_requests < 5:
        raise ValueError("Per-user limit test needs at least five requests")
    if workers < 1:
        raise ValueError("Worker count must be positive")
    if len(user_tokens) < 2:
        raise ValueError("Hot-seat test needs at least two distinct user tokens")
    subjects = [jwt_subject(token) for token in user_tokens]
    if len(set(subjects)) != len(subjects):
        raise ValueError("Each token in the pool must have a distinct subject")
    tokens = [user_tokens[index % len(user_tokens)] for index in range(hot_seat_requests)]

    metrics_before = fetch_metrics(base_url, timeout)
    run_id = uuid.uuid4().hex[:12]
    hot_show_id = create_show(
        base_url, admin_token, f"burst-hot-{run_id}", ["A12"], timeout
    )

    hot_keys = [f"{run_id}-hot-{index}" for index in range(hot_seat_requests)]
    hot_calls = [
        (
            f"/shows/{hot_show_id}/reserve",
            token,
            {"seats": ["A12"], "idempotency_key": key},
        )
        for token, key in zip(tokens, hot_keys)
    ]
    hot_results, hot_samples, hot_monitor_failures = asyncio.run(
        run_parallel_with_monitor(hot_calls, workers, base_url, hot_show_id, timeout)
    )
    hot_outcomes = Counter(classify(result) for result in hot_results)
    winners = [
        (tokens[index], hot_keys[index], result)
        for index, result in enumerate(hot_results)
        if result.status == 201 and isinstance(result.body, dict)
    ]

    replay_result = ApiResult(0, {}, "No hot-seat winner available for replay check")
    replay_ok = False
    mismatch_result = ApiResult(0, {}, "No hot-seat winner available for mismatch check")
    mismatch_ok = False
    if winners:
        winner_token, winner_key, original = winners[0]
        replay_result = post_json(
            base_url,
            f"/shows/{hot_show_id}/reserve",
            winner_token,
            {"seats": ["A12"], "idempotency_key": winner_key},
            timeout,
        )
        replay_ok = (
            replay_result.status == 201
            and isinstance(replay_result.body, dict)
            and replay_result.body.get("reservation_id") == original.body.get("reservation_id")
        )
        mismatch_result = post_json(
            base_url,
            f"/shows/{hot_show_id}/reserve",
            winner_token,
            {"seats": ["NOT-A-SEAT"], "idempotency_key": winner_key},
            timeout,
        )
        mismatch_ok = classify(mismatch_result) == "idempotency-key-reused"

    limit_seats = [f"L{index:02d}" for index in range(1, per_user_requests + 1)]
    limit_show_id = create_show(
        base_url,
        admin_token,
        f"burst-limit-{run_id}",
        limit_seats,
        timeout,
    )
    limit_user_token = tokens[0]
    limit_calls = [
        (
            f"/shows/{limit_show_id}/reserve",
            limit_user_token,
            {"seats": [seat], "idempotency_key": f"{run_id}-limit-{index}"},
        )
        for index, seat in enumerate(limit_seats)
    ]
    limit_results, limit_samples, limit_monitor_failures = asyncio.run(
        run_parallel_with_monitor(limit_calls, workers, base_url, limit_show_id, timeout)
    )
    limit_outcomes = Counter(classify(result) for result in limit_results)

    identity_show_id = create_show(
        base_url, admin_token, f"burst-identity-{run_id}", ["I01", "I02"], timeout
    )
    identity_token = user_tokens[0]
    other_token = user_tokens[1]
    identity_subject = jwt_subject(identity_token)
    spoofed_subject = jwt_subject(other_token)
    identity_result = post_json(
        base_url,
        f"/shows/{identity_show_id}/reserve",
        identity_token,
        {
            "seats": ["I01"],
            "idempotency_key": f"{run_id}-identity",
            "user_id": spoofed_subject,
        },
        timeout,
    )
    identity_reservation_id = (
        identity_result.body.get("reservation_id")
        if isinstance(identity_result.body, dict)
        else None
    )
    spoof_rejected = (
        identity_result.status == 201
        and isinstance(identity_result.body, dict)
        and identity_result.body.get("user_id") == identity_subject
        and identity_subject != spoofed_subject
    )
    unauthorized_cancel = ApiResult(0, {}, "No identity reservation was created")
    unauthorized_cancel_blocked = False
    unauthorized_state_unchanged = False
    owner_cancel = ApiResult(0, {}, "No identity reservation was created")
    owner_cancel_succeeded = False
    if identity_reservation_id:
        unauthorized_cancel = api_request(
            base_url,
            "POST",
            f"/reservations/{identity_reservation_id}/cancel",
            other_token,
            timeout=timeout,
        )
        unauthorized_cancel_blocked = unauthorized_cancel.status == 403
        state_after_denied_cancel = show_state(base_url, identity_show_id, timeout)
        unauthorized_state_unchanged = (
            int(state_after_denied_cancel["confirmed_seats"]) == 1
            and int(state_after_denied_cancel["available_seats"]) == 1
        )
        owner_cancel = api_request(
            base_url,
            "POST",
            f"/reservations/{identity_reservation_id}/cancel",
            identity_token,
            timeout=timeout,
        )
        owner_cancel_succeeded = owner_cancel.status == 200

    hot_state = show_state(base_url, hot_show_id, timeout)
    limit_state = show_state(base_url, limit_show_id, timeout)
    identity_state = show_state(base_url, identity_show_id, timeout)
    metrics_after = fetch_metrics(base_url, timeout)

    hot_reconciles, hot_summary = check_reconciliation(
        base_url, hot_show_id, hot_state, metrics_after
    )
    limit_reconciles, limit_summary = check_reconciliation(
        base_url, limit_show_id, limit_state, metrics_after
    )
    identity_reconciles, identity_summary = check_reconciliation(
        base_url, identity_show_id, identity_state, metrics_after
    )

    confirmed_delta = metric_value(metrics_after, "seat_reservations_confirmed_total") - metric_value(
        metrics_before, "seat_reservations_confirmed_total"
    )
    replay_delta = metric_value(
        metrics_after, "seat_reservations_idempotent_replays_total"
    ) - metric_value(metrics_before, "seat_reservations_idempotent_replays_total")
    seat_taken_delta = metric_value(
        metrics_after, "seat_reservations_declined_total", {"reason": "seat-taken"}
    ) - metric_value(
        metrics_before, "seat_reservations_declined_total", {"reason": "seat-taken"}
    )
    limit_decline_delta = metric_value(
        metrics_after, "seat_reservations_declined_total", {"reason": "per-user-limit"}
    ) - metric_value(
        metrics_before, "seat_reservations_declined_total", {"reason": "per-user-limit"}
    )
    mismatch_delta = metric_value(
        metrics_after,
        "seat_reservations_declined_total",
        {"reason": "idempotency-key-reused"},
    ) - metric_value(
        metrics_before,
        "seat_reservations_declined_total",
        {"reason": "idempotency-key-reused"},
    )

    confirmed_count = hot_outcomes["confirmed"] + limit_outcomes["confirmed"]
    hot_five_xx = hot_outcomes["5xx"] + hot_outcomes["network-error"]
    limit_five_xx = limit_outcomes["5xx"] + limit_outcomes["network-error"]
    identity_results = [identity_result, unauthorized_cancel, owner_cancel]
    identity_five_xx = sum(
        classify(result) in ("5xx", "network-error") for result in identity_results
    )
    hot_409_count = sum(
        hot_outcomes[outcome]
        for outcome in ("seat-taken", "per-user-limit", "idempotency-key-reused", "other-409")
    )
    identity_ok = (
        spoof_rejected
        and unauthorized_cancel_blocked
        and unauthorized_state_unchanged
        and owner_cancel_succeeded
    )
    failures = []
    if hot_outcomes["confirmed"] != 1:
        failures.append(f"hot-seat storm expected one winner, got {hot_outcomes['confirmed']}")
    if hot_409_count != hot_seat_requests - 1:
        failures.append("every non-winning hot-seat request should be declined with HTTP 409")
    if limit_outcomes["confirmed"] != 4:
        failures.append(f"per-user test expected four reservations, got {limit_outcomes['confirmed']}")
    if limit_outcomes["per-user-limit"] != per_user_requests - 4:
        failures.append("per-user-limit declines did not match expected count")
    if hot_five_xx or limit_five_xx or identity_five_xx:
        failures.append("at least one request returned 5xx or had a transport failure")
    failures.extend(hot_monitor_failures)
    failures.extend(limit_monitor_failures)
    if not replay_ok:
        failures.append("same-key retry did not return the original reservation")
    if not mismatch_ok:
        failures.append("same key with different seats was not declined as idempotency-key-reused")
    if not hot_reconciles:
        failures.append("hot-seat show state did not reconcile with its availability gauge")
    if not limit_reconciles:
        failures.append("limit-test show state did not reconcile with its availability gauge")
    if not identity_ok:
        failures.append("token identity or owner-only cancellation check failed")
    if not identity_reconciles:
        failures.append("identity-test show state did not reconcile with its availability gauge")
    if not math.isclose(confirmed_delta, confirmed_count + 1, abs_tol=0.001):
        failures.append("confirmed counter delta does not match new confirmed reservations")
    if not math.isclose(replay_delta, 1, abs_tol=0.001):
        failures.append("idempotent replay counter delta should be one")
    if not math.isclose(seat_taken_delta, hot_outcomes["seat-taken"], abs_tol=0.001):
        failures.append("seat-taken counter delta does not match hot-seat outcomes")
    if not math.isclose(limit_decline_delta, per_user_requests - 4, abs_tol=0.001):
        failures.append("per-user-limit counter delta does not match limit declines")
    if not math.isclose(mismatch_delta, 1, abs_tol=0.001):
        failures.append("idempotency-key-reused counter delta should be one")

    emit(f"Hot-seat show: {hot_show_id}")
    emit(
        f"Hot-seat requests: {hot_seat_requests}; distinct users: {len(user_tokens)}; "
        f"workers: {workers}; outcomes: {dict(hot_outcomes)}"
    )
    emit(f"In-flight invariant samples: hot-seat={hot_samples}, per-user={limit_samples}")
    emit(f"Idempotent replay: HTTP {replay_result.status}, same reservation={replay_ok}")
    emit(f"Same-key/different-seats: HTTP {mismatch_result.status}, correctly declined={mismatch_ok}")
    emit(f"Per-user-limit show: {limit_show_id}")
    emit(f"Per-user request outcomes: {dict(limit_outcomes)}")
    emit(
        f"Identity spoof rejected={spoof_rejected}; foreign cancel HTTP "
        f"{unauthorized_cancel.status}, blocked={unauthorized_cancel_blocked}; "
        f"owner cancel HTTP {owner_cancel.status}, succeeded={owner_cancel_succeeded}"
    )
    emit(f"Combined new reservations confirmed: {confirmed_count}; idempotent retry responses are reported separately")
    emit(f"5xx + transport errors: {hot_five_xx + limit_five_xx + identity_five_xx}")
    emit(f"Reconciliation: {hot_summary}; valid={hot_reconciles}")
    emit(f"Reconciliation: {limit_summary}; valid={limit_reconciles}")
    emit(f"Reconciliation: {identity_summary}; valid={identity_reconciles}")
    emit(
        "Metric deltas: "
        f"confirmed={confirmed_delta:g}, seat-taken={seat_taken_delta:g}, "
        f"per-user-limit={limit_decline_delta:g}, idempotency-key-reused={mismatch_delta:g}, "
        f"idempotent-replays={replay_delta:g}"
    )
    for failure in failures:
        emit(f"FAIL: {failure}")
    if not failures:
        emit("PASS: burst outcomes, idempotency, limits, metrics, and reconciliation agree")

    return {
        "ok": not failures,
        "failures": failures,
        "hot_outcomes": hot_outcomes,
        "limit_outcomes": limit_outcomes,
        "identity_ok": identity_ok,
        "hot_show_id": hot_show_id,
        "limit_show_id": limit_show_id,
        "identity_show_id": identity_show_id,
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True, help="API origin, for example https://seat-api.example.com")
    parser.add_argument(
        "--user-tokens-file",
        required=True,
        type=Path,
        help="Pool of distinct user JWTs, one per line; requests rotate through the pool",
    )
    parser.add_argument(
        "--admin-token-env",
        default="BURST_ADMIN_TOKEN",
        help="Environment variable containing a show_admin access token (default: BURST_ADMIN_TOKEN)",
    )
    parser.add_argument("--hot-seat-requests", type=int, default=20)
    parser.add_argument("--per-user-requests", type=int, default=10)
    parser.add_argument("--workers", type=int, default=64, help="Maximum in-flight async HTTP requests")
    parser.add_argument("--timeout", type=float, default=30)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    admin_token = os.environ.get(args.admin_token_env)
    if not admin_token:
        print(f"Set {args.admin_token_env} to an admin access token", file=sys.stderr)
        return 2
    try:
        user_tokens = load_user_tokens(args.user_tokens_file)
        report = run_burst(
            args.base_url,
            admin_token,
            user_tokens,
            args.hot_seat_requests,
            args.per_user_requests,
            args.workers,
            args.timeout,
        )
    except (OSError, ValueError, RuntimeError) as error:
        print(f"Burst failed: {error}", file=sys.stderr)
        return 2
    return 0 if report["ok"] else 1


if __name__ == "__main__":
    raise SystemExit(main())