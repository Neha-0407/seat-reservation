# Seat Reservation API

Spring Boot JSON API with PostgreSQL persistence and Keycloak JWT authentication.

## Local Run

Start PostgreSQL, Keycloak, Prometheus, and Grafana, then run the application on the host:

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

The API base URL is `http://localhost:8080`. The API contract and request examples are in [HELP.md](HELP.md).

To run the API itself in Docker instead, set `$env:APP_PORT = "8082"` and use `docker compose --profile app up -d --build`. The container listens on 8080 internally and is exposed at the configured host port, allowing it to coexist with a host-run app on 8080.

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

## Railway Deployment

Create three Railway services from this repository: the API, Keycloak, and two PostgreSQL databases (one for the API and one for Keycloak). The root [railway.toml](railway.toml) configures the API's Docker build and readiness check. For the Keycloak service, set its **Config-as-code file path** to `docker/keycloak/railway.toml`; this uses the Railway-specific Dockerfile and imports a production realm with registration enabled and no local demo users.

Configure Keycloak with `KC_HTTP_ENABLED=true`, `KC_PROXY_HEADERS=xforwarded`, `KC_DB=postgres`, `KC_BOOTSTRAP_ADMIN_USERNAME=admin`, and a secret `KC_BOOTSTRAP_ADMIN_PASSWORD`. Set `KC_HOSTNAME` to the Keycloak service's public URL (for example, `https://your-keycloak-domain`). Map the Keycloak PostgreSQL service's `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD` into `KC_DB_URL_HOST`, `KC_DB_URL_PORT`, `KC_DB_URL_DATABASE`, `KC_DB_USERNAME`, and `KC_DB_PASSWORD` respectively.

For the API, set `SPRING_DATASOURCE_URL` to `jdbc:postgresql://${{seat-reservation-db.PGHOST}}:${{seat-reservation-db.PGPORT}}/${{seat-reservation-db.PGDATABASE}}`, with `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` referencing that database's `PGUSER` and `PGPASSWORD`. Set `KEYCLOAK_PUBLIC_URL` to the Keycloak public URL and `JWT_AUDIENCE=seat-reservation-api`. Railway's variable reference picker can insert the database and service references; replace `seat-reservation-db` with the actual API database service name. Generate a public domain for both web services, then deploy. Keep all database credentials and the Keycloak bootstrap password in Railway variables, never in Git.

After deployment, use the API's public URL. Register buyer accounts in Keycloak, then create a separate user with the `show_admin` realm role before using `POST /shows`. The Railway Keycloak realm deliberately imports no users from the local development realm. Size Railway resources and test capacity before treating it as a 20,000-concurrent production deployment.

Run the script's local fake-API tests with:

```powershell
python -m unittest discover -s scripts -p "test_*.py"
```