# Tracker

[![CI](https://github.com/Someones-account/FinanceTracker/actions/workflows/ci.yml/badge.svg)](https://github.com/Someones-account/FinanceTracker/actions/workflows/ci.yml)

A personal finance backend built with **event sourcing** and **CQRS** in Java 21 and Spring Boot.
Instead of storing a mutable balance, it stores every change as an immutable event and derives
balances and reports from that history.

## Features

- Accounts with deposits and withdrawals, validated by business rules (no overdrafts)
- **Append-only event store** on PostgreSQL with optimistic concurrency control
- **Read models (projections)** for balances and monthly category reports, each with its own checkpoint
- **Rebuild on demand**: drop a read model and replay the full history to the same result
- **Time travel**: the balance of an account at any past moment
- **Reversals**: undo a transaction with a compensating event; nothing is ever deleted
- **Idempotency keys**: a retried HTTP request does not post twice
- Money stored as integer minor units (e.g. grosze), never floating point

## Tech stack

Java 21 (records, sealed interfaces, pattern matching in `switch`), Spring Boot 3, plain JDBC,
PostgreSQL 16, Flyway, JUnit 5, AssertJ, Testcontainers, Docker Compose, GitHub Actions.

The event store is written by hand on JDBC instead of using a framework such as Axon,
so the mechanics are visible in the code.

## How it works

**Write side.** A command (for example "withdraw 25.00") reaches `AccountCommandHandler`, which
loads the account's events, folds them into the current state, and asks the `Account` aggregate to
decide. The aggregate is pure logic: `decide(state, command)` returns new events or throws a
`DomainException`, and `evolve(state, event)` applies an event to produce the next state.
The new events are appended with the expected version. A `UNIQUE (aggregate_id, version)` constraint
rejects a concurrent writer, and the handler reloads and retries up to three times.

**Read side.** Projections read the global event log in order and update denormalized tables.
Each batch of table updates and the checkpoint move are committed in a single transaction, so after
a crash a batch is either fully applied or not at all. The balance and the category report
projections have independent checkpoints and can be rebuilt independently.

**Queries.** Current balances and reports come from the read tables. The time-travel query replays
one account's events up to the requested timestamp and needs no extra storage.

Package layout (`src/main/java`):

| Package | Responsibility |
|---|---|
| `domain` | Events, commands and the `Account` aggregate; no framework imports |
| `store` | `EventStore` and `EventLog` interfaces, in-memory and PostgreSQL implementations |
| `app` | Command handler with retry, idempotency service, time-travel query |
| `projection` | Checkpointed projections: balances and monthly category totals |
| `api` | REST controllers and error mapping |
| `config` | Spring wiring and the projection scheduler |
| `tracker` | Application entry point |

## Running it

Requirements: JDK 21, Maven, Docker.

```bash
git clone https://github.com/<your-github-user>/<your-repo>.git
cd <your-repo>

docker compose up -d      # PostgreSQL
mvn spring-boot:run       # starts the API on http://localhost:8080
```

Flyway creates the schema on startup. If port 5432 is already in use on your machine, change the
left side of the port mapping in `docker-compose.yml` (for example `5433:5432`) and the port in the
datasource URL in `src/main/resources/application.yml`.

Run the tests (Docker must be running, because integration tests start PostgreSQL containers):

```bash
mvn clean verify
```

## API walkthrough

The examples use a Unix shell. On Windows, see the quoting notes below.

```bash
# 1. Open an account (returns its id)
curl -X POST localhost:8080/accounts -H "Content-Type: application/json" \
  -d '{"name":"Main","currency":"PLN"}'

ID=<id from the response>

# 2. Deposit and withdraw (amounts are in minor units: 10000 = 100.00)
curl -X POST localhost:8080/accounts/$ID/deposits -H "Content-Type: application/json" \
  -d '{"amountMinor":10000,"category":"salary"}'
curl -X POST localhost:8080/accounts/$ID/withdrawals -H "Content-Type: application/json" \
  -d '{"amountMinor":2500,"category":"food"}'

# 3. Raw event history (versions are used to reverse transactions)
curl localhost:8080/accounts/$ID/events

# 4. Current balance from the read model (updates within about a second)
curl localhost:8080/accounts/$ID

# 5. Time travel
curl "localhost:8080/accounts/$ID/balance?at=2030-01-01T00:00:00Z"

# 6. Monthly report by category
curl "localhost:8080/accounts/$ID/reports/monthly?month=2026-11"

# 7. Reverse the withdrawal stored at event version 3
curl -X POST localhost:8080/accounts/$ID/transactions/3/reversal \
  -H "Content-Type: application/json" -d '{"reason":"wrong amount"}'

# 8. Idempotent retry: sending this twice changes the balance only once
curl -X POST localhost:8080/accounts/$ID/deposits -H "Content-Type: application/json" \
  -H "Idempotency-Key: abc-1" -d '{"amountMinor":500,"category":"gift"}'

# 9. Rebuild a read model from the event log
curl -X POST localhost:8080/admin/projections/balances/rebuild
curl -X POST localhost:8080/admin/projections/categories/rebuild
```

| Situation | Status |
|---|---|
| Account created | 201 |
| Command accepted | 204 |
| Invalid request body (blank name, non-positive amount) | 400 |
| Unknown account | 404 |
| Business rule violated (insufficient funds, already reversed) | 422 |
| Concurrent modification after retries, or idempotency key still in progress | 409 |
| Idempotency key reused with a different request | 422 |

**Windows quoting.** In `cmd` and PowerShell, use `curl.exe`, wrap the JSON in double quotes and
escape the inner ones: `-d "{\"name\":\"Main\",\"currency\":\"PLN\"}"`, or put the JSON in a file
and use `-d @file.json`.

## Testing

- **Aggregate tests**: pure unit tests, no database, written as given events, when command, then events
- **Handler tests**: command handler against the in-memory store, including a simulated write conflict and retry exhaustion
- **Event store tests**: PostgreSQL via Testcontainers
- **Projection and API tests**: full Spring context on a real PostgreSQL container, covering projections, rebuild, error mapping, reversals and idempotency

## Design decisions and trade-offs

- **Events are facts.** `evolve` never validates and never throws; all validation lives in `decide`.
- **Undo is an appended event.** History stays auditable, and the time-travel query still shows the balance as it was before a reversal.
- **Eventual consistency.** Read models are updated by a scheduler every 500 ms, so a balance can lag a write slightly.
- **Integer money.** Amounts are `long` minor units, so there are no rounding errors.
- **Explicit event type names.** Stored type names are mapped to classes in one place, so renaming a Java class does not break stored data.
- **Flat package layout.** Top-level packages are listed in `scanBasePackages` on `TrackerApplication`; a new top-level package must be added there.

## Known limitations

- **Projection ordering.** `BIGSERIAL` values are assigned at insert time, but transactions can commit out of order. A projection could in theory skip an event whose transaction commits late. Production systems read only up to a safe watermark.
- **Idempotency keys** are claimed before the command runs. If the process dies between the command and marking the key completed, a retry receives 409 until the key is cleaned up. Storing the key in the same transaction as the events would close this gap. Old keys are not purged yet.
- **Months in reports are UTC.** Per-user time zones are not supported.
- **No authentication.** All endpoints are open.

## Roadmap

- Transfers between accounts as a saga with compensating events
- Snapshots to speed up loading long histories
- Event versioning (upcasting) for schema changes
- Authentication and per-user accounts