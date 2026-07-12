# Database migration and seed

## Scope

This first slice provides a reproducible local/CI database for issue #105.

- Flyway V1 owns the current full schema.
- Flyway V2 owns the mandatory active 0% platform fee policy.
- Base seed owns the minimum Vivaldi resort and two anonymized login personas.
- The matching-price-vivaldi FLOW scenario owns instructor exposure and price inputs.
- The active matching request is created through the application flow, not direct SQL.

Development and production databases are not reset by these scripts. A dev reset
workflow is intentionally deferred until /dev/auth/** has a second access boundary,
the existing database adoption strategy is chosen, and an RDS snapshot is taken.

## Local reset

Prerequisites:

- Docker with Compose
- no local data that must be preserved
- the local application process is stopped

Run:

    ./scripts/db/reset-local.sh --confirm-local-reset matching-price-vivaldi

The command verifies the exact local container and schema before it connects, then
runs:

    Flyway clean
    → Flyway migrate
    → Flyway validate
    → base seed
    → scenario seed
    → SQL verification
    → second migrate with no pending migration
    → final validate

Seed SQL uses strict inserts against a clean schema. Do not execute an individual
seed file twice and do not add INSERT IGNORE, unconditional upserts, or disabled
foreign-key checks. Repeatability is provided by running the complete reset command.

After reset, create the ignored local Spring configuration if needed:

    cp config/application-local.example.yml application-local.yml

Hibernate runs with ddl-auto=validate; it no longer owns local schema creation.

## Golden price flow

The source mapping is documented in
db/seed/scenarios/matching-price-vivaldi/scenario.yml.

1. Issue a local token for consumer-default through POST /dev/auth/token.
2. Send db/seed/scenarios/matching-price-vivaldi/request.json to
   POST /api/v1/consumer/matching-requests.
3. The initial response is SEARCHING.
4. The server creates the group, offer, and price snapshot through the normal flow.
5. The expected next status is WAITING_FOR_INSTRUCTOR.

Expected price:

    lesson price       60,000
    resort pass fee  + 25,000
    platform fee      +     0
    total payment     = 85,000

The request maps PM request_006_a to instructor_profile_004 only as a technical
price-flow match. The PM persona description does not establish child-teaching
expertise, so this is not labeled an ideal persona match.

## Spreadsheet normalization recorded in this slice

- lessonPassFeeAmount maps to resorts.pass_fee_amount.
- Missing displayName reuses the confirmed resort name.
- The setting sheet's resort belongs to instructor_profiles.resort_id.
- minLevel/maxLevel becomes the lessonLevels collection.
- Missing availableDurationMinutes is derived as 120 minutes from request_006_a.
- Certification details are reduced to the current enum KSIA_SKI_LEVEL_1.
- PM names and phone numbers are not copied into executable seed data.

## CI

.github/workflows/db-seed-check.yml runs the dedicated integrationTest task on a
fresh MySQL 8.4.8 container. It first executes the same pinned Docker Flyway runner
and reset script used locally. It then uses Flyway 12.10.0 on the integration-test
classpath, lets Hibernate validate the schema, reapplies the seed contract on a
separate disposable MySQL, creates the request through authenticated MockMvc,
reads the result through consumer and instructor APIs, and invokes the scheduler
entrypoint once to prove the STABLE price scenario remains unchanged.

The application runtime does not include Flyway. Operational migration remains the
responsibility of a pinned external runner.

## Existing database adoption

Do not enable baselineOnMigrate. For a non-empty existing database:

1. compare the live schema with V1;
2. take a snapshot;
3. prove Hibernate validation against the live schema;
4. explicitly baseline at version 1;
5. run V2 and later migrations;
6. keep baselineOnMigrate=false.

V2 is separate from V1 so the mandatory policy still runs after an explicit V1
baseline.

## Deliberately not completed in this slice

- public dev persona seed and dev reset workflow
- normal dev deployment migration wiring
- all PM spreadsheet personas and scenarios
- multi-request payment verification (the current service rejects grouped payment
  creation when more than one request is present)
- legacy data backfill when an existing database must be preserved
- old-binary rollback compatibility for the current NOT NULL price columns

## Deferred follow-ups

`realtime-reconnect-recovery` records the agreed deferral of WebSocket reconnect
and event-loss recovery. This seed slice mocks only the external real-time event
dispatcher; request creation, after-commit search, DB state, API readback, and the
scheduler entrypoint remain real.

This deferral does not block the matching-price seed contract. It must be resumed
when the real-time state-recovery batch starts, and a GitHub tracking issue must be
linked before that broader recovery work is declared complete. Until then, this
document and the scenario manifest are the repository-local tracking source.
