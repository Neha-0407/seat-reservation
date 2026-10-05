# Seat Reservation API

Spring Boot JSON API with PostgreSQL persistence and Keycloak JWT authentication.

## Local Run

Start PostgreSQL, Keycloak, Prometheus, and Grafana, then run the application on the host:

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

The API base URL is `http://localhost:8080`. The API contract and request examples are in [HELP.md](HELP.md).

To run the API itself in Docker instead, use `docker compose --profile app up -d --build`. Do not run both host and container app variants at the same time because both bind port 8080.

Application logs appear in the VS Code terminal running `bootRun` (the `java` terminal). Container logs are available with `docker compose logs -f postgres keycloak prometheus grafana`.

## Metrics and Health

- Liveness: `GET /actuator/health/liveness`
- Readiness (includes database): `GET /actuator/health/readiness`
- Prometheus scrape: `GET /actuator/prometheus`
- Prometheus UI and target health: `http://localhost:9090` and `http://localhost:9090/targets`
- Grafana dashboard: `http://localhost:3000` (local login: `admin` / `admin`, dashboard: **Seat Reservation API**)

## Burst Test

Put a pool of distinct user access tokens in `burst-user-tokens.txt`, one per line. The file is read locally and ignored by Git. The pool must contain at least two tokens with different JWT `sub` claims. The script rotates through the pool for hot-seat requests, so the number of requests can exceed the number of users. For a many-user stampede, provide a larger pool of distinct users. The admin token must have the `show_admin` role.

Install the burst runner's async HTTP dependency once:

```powershell
python -m pip install -r scripts/requirements.txt
```

```powershell
$env:BURST_ADMIN_TOKEN = "<show_admin access token>"
python scripts/burst.py --base-url http://localhost:8080 --user-tokens-file .\burst-user-tokens.txt --hot-seat-requests 20000 --per-user-requests 10 --workers 64 --timeout 300
```

`--hot-seat-requests` is the total request count; `--workers` is the maximum number of async requests in flight. This local profile avoids opening 20,000 simultaneous sockets and allows enough time for the full queue to drain. For a true 20,000-in-flight deployment test, raise `--workers` only on a client and API environment sized for that load. The imported local Keycloak realm contains one admin and only one buyer, so add at least one more buyer and obtain a distinct access token before running. For a deployed instance, replace the base URL and provide a `show_admin` token plus a distinct user-token pool issued by that environment; third-party testers do not need to use this repo's Keycloak realm.

The script creates three shows and leaves them in the database for inspection. It reports response outcomes, samples reconciliation during each load phase, and checks final API/Prometheus reconciliation, idempotent replay and key reuse, per-user limits, token-derived identity despite a spoofed body field, and owner-only cancellation. For realistic multi-user contention, use many distinct tokens; a two-user pool can run a 20,000-request test but does not represent 20,000 distinct buyers.

Run the script's local fake-API tests with:

```powershell
python -m unittest discover -s scripts -p "test_*.py"
```