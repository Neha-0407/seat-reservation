# Seat Reservation Design Notes

## Atomic Reservation Decision

Reservations run in a Spring transaction against PostgreSQL. The service first locks a persistent `(show_id, user_id)` row with `SELECT ... FOR UPDATE` to serialize that user's reservations for the show and enforce the per-user seat limit. It then fetches requested seat rows in ascending seat UUID order with a pessimistic write lock. Only after all requested rows are locked and verified as available does it create the reservation and update the seat rows. Any unavailable seat aborts the transaction, so multi-seat requests are all-or-nothing.

The stable seat lock order reduces deadlock risk when concurrent multi-seat requests overlap. PostgreSQL uniqueness constraints independently protect seat numbers within a show and the idempotency tuple. The live local test completed 20,000 attempts across 64 distinct users, with at most 64 requests in flight: one hot-seat winner, 19,999 `409` declines, and no observed 5xx or transport errors. It does not prove capacity for 20,000 simultaneously open requests.

## Idempotency

The key and a canonical hash of sorted requested seat numbers are stored on `reservations`. The database enforces `UNIQUE (show_id, user_id, idempotency_key)`. A matching retry returns the existing reservation; reuse with a different seat list returns `409`. The `(show_id, user_id)` lock serializes concurrent uses of the same key before the existing reservation lookup.

## Release and Multi-Seat Semantics

This implementation uses explicit cancellation, not time-boxed holds. Only the reservation owner may cancel. Cancellation locks the user's show row, the reservation row, and its seat rows in deterministic order; the transaction verifies current ownership/status before making seats available. There is no active `held` state or expiration worker, so `held_seats` is currently zero. A Java integration test verifies a multi-seat request that includes an already-taken seat leaves its other requested seat available.

## Consistency and Availability

PostgreSQL is the source of truth. The API does not accept reservations if it cannot reach the database; readiness includes the database health indicator and was manually observed returning HTTP 503 while local Postgres was stopped. On a partition, the design favors consistency over availability: a request that cannot commit against the primary database must fail rather than confirm from a stale local view. There is no multi-primary or cross-region write design here.

## Observability and Operations

The request filter emits structured completion logs with request ID, path, method, status, and duration. Reservation confirmation/cancellation events and unexpected exceptions have structured logs. Local logs go to the `bootRun` console; no public log aggregation or alert delivery is configured.

Prometheus exposes confirmed reservations, declines by reason, idempotent replays, and per-show available-seat gauges. Grafana is provisioned locally with request rate, 5xx rate, p95 latency, reservation outcomes, and seat availability panels. Useful alert candidates include sustained 5xx or transport failures, readiness down, elevated database connection wait/lock time, reconciliation mismatch, and abnormal decline or latency rates. Alert routing is not configured.

The burst runner samples show state during load and compares final state and metrics. Sampling is not a proof that the invariant held at every instant. The database transaction and seat row locks are the correctness mechanism; the monitoring samples are evidence, not the mechanism.

## Validation and Gaps

The Java/Testcontainers suite covers a 500-contender hot-seat race, a concurrent per-user limit, idempotency, owner-only cancellation, token-derived identity, health checks, metrics, and all-or-nothing multi-seat failure. The local live run covered 20,000 total hot-seat requests across 64 distinct tokens at 64 in-flight requests. A 20,000-in-flight run did not complete reliably on the local environment. Deployment-sized 20,000-in-flight capacity remains unverified.

The repository is public and has incremental history. Local Docker Compose includes an optional API image profile, Postgres, Keycloak, Prometheus, and Grafana. No public application deployment or public log endpoint is currently provided.

## AI Usage

AI assistance was used to inspect the existing implementation, propose focused tests and local observability/deployment configuration, and interpret test output. The repository's Java code and its behavior were checked through source inspection and the Java integration suite. The 20,000-request result reported here was obtained by running the provided script against the local service; it represents 64 in-flight requests, not 20,000 simultaneous connections. No claim is made that AI independently verified production-scale capacity.

## Next Steps

Deploy the API and managed PostgreSQL in a public environment, configure secrets and log aggregation, and run a staged capacity test with a load generator and sufficient clients. Increase in-flight load gradually while tracking connection pools, lock wait, database CPU/IO, latency, and dropped connections. Add alert rules and a documented operational rollback procedure.