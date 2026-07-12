# Database migration and seed

## Scope

This first slice provides a reproducible local/CI database for
[issue #105](https://github.com/TEAM-SSING/SSING-SERVER/issues/105).

- Flyway V1 owns the current full schema.
- Flyway V2 owns the mandatory active 0% platform fee policy.
- Base seed owns the minimum Vivaldi resort and two anonymized login personas.
- The matching-price-vivaldi FLOW scenario owns instructor exposure and price inputs.
- The matching request, payment, and lesson are created through the application flow,
  not direct SQL.

Development and production databases are not reset by these scripts. A dev reset
workflow is intentionally deferred until /dev/auth/** has a second access boundary
and an approved migration/seed workflow is implemented in issue #122.

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
6. The instructor accepts the offer and the consumer gives the final acceptance.
7. The server creates a PENDING payment for 85,000 and the consumer completes it.
8. The matching request becomes CONFIRMED and one confirmed lesson is created.

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
fresh MySQL 8.4.8 container. It first executes the same pinned Docker Flyway 12.10.0
runner and reset script used locally. The integration-test classpath separately uses
the Spring Boot 4.1.0 managed Flyway version (currently 12.4.0), lets Hibernate
validate the schema, reapplies the seed contract on another disposable MySQL,
creates the request through authenticated MockMvc, reads the result through consumer
and instructor APIs, and invokes the scheduler entrypoint once to prove the STABLE
price scenario remains unchanged. It then accepts the offer, accepts the consumer
confirmation, completes the payment, and verifies the request price snapshot,
85,000 payment, confirmed lesson, and participant through the real application flow.

The application runtime does not include Flyway. Operational migration remains the
responsibility of a pinned external runner.

## Existing database adoption

The current shared dev database is empty. Issue #122 therefore adopts it as a fresh
database by migrating V1 and then V2 without an explicit baseline or legacy backfill.
The approved dev workflow and second `/dev/auth/**` access boundary remain separate
from this local/CI slice.

Do not enable baselineOnMigrate. For a non-empty existing database:

1. compare the live schema with V1;
2. take a snapshot;
3. prove Hibernate validation against the live schema;
4. explicitly baseline at version 1;
5. run V2 and later migrations;
6. keep baselineOnMigrate=false.

V2 is separate from V1 so the mandatory policy still runs after an explicit V1
baseline.

## Outside the current MVP slice

- Issue #105 is complete when this local/CI seed foundation and single-request
  request-to-payment flow merge.
- public dev persona seed and dev reset workflow
- normal dev deployment migration wiring
- all PM spreadsheet personas and scenarios
- multi-request group payment, which the current MVP intentionally disables until
  lesson-price allocation and rounding policy are decided
- legacy data backfill and old-binary rollback for a future non-empty database;
  these do not apply to the currently empty dev database

## Deferred follow-ups

`realtime-reconnect-recovery` records the agreed deferral of WebSocket reconnect
and event-loss recovery. It is tracked by
[#79](https://github.com/TEAM-SSING/SSING-SERVER/issues/79),
[#86](https://github.com/TEAM-SSING/SSING-SERVER/issues/86), and
[#109](https://github.com/TEAM-SSING/SSING-SERVER/issues/109). This seed slice
mocks only the external real-time event dispatcher; request creation,
after-commit search, DB state, API readback, and the scheduler entrypoint remain
real.

This deferral does not block the matching-price seed contract. It must be resumed
when the real-time state-recovery batch starts.

Dev database adoption and an approved seed workflow are tracked by
[#122](https://github.com/TEAM-SSING/SSING-SERVER/issues/122). The scheduler
contract in this slice proves one-request stability only; 100-request batch
fairness is tracked separately by
[#123](https://github.com/TEAM-SSING/SSING-SERVER/issues/123).
