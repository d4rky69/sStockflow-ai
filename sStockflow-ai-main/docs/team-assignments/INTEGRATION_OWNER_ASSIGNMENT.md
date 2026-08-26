# Integration Owner Assignment — Contracts, Spring Orchestration and Release

## 1. Mission

Own the boundaries between every contributor. Freeze and publish contracts, implement the authoritative Spring Boot/API/PostgreSQL integration, prevent silent incompatibilities, merge reviewed work and prove the complete demo works in one running system.

The team must attach a person's name to this role before parallel feature work resumes.

## 2. Scope

The integration owner owns:

- Contract decision meetings and versioning.
- Formal JSON Schema and example fixtures.
- Canonical district/road ID registry governance.
- Spring Boot public/internal orchestration endpoints.
- PostgreSQL migrations, tenant isolation and audit persistence.
- Object-storage integration and server-side authorization.
- Internal service clients, timeouts, retries and circuit/degraded states.
- Cross-service contract tests.
- Integration branch/merge sequence.
- Secrets/configuration documentation.
- End-to-end testing, startup instructions and demo rehearsal.

The integration owner does not silently rewrite another person's algorithm or approve incompatible schema changes alone.

## 3. Contract freeze procedure

For each contract:

1. Identify every producer and consumer.
2. Review required versus optional/null fields.
3. Define units, coordinate order, timestamps, IDs and enums.
4. Define no-data, error, pagination, batch and partial-failure behavior.
5. Create a JSON Schema in `contracts/ner/`.
6. Create valid, boundary and invalid fixtures.
7. Add validation in every producer and consumer.
8. Record the decision in a changelog.
9. Tag the approved contract commit, such as `contracts-ner-v1`.

Minimum contracts to freeze:

```text
district-registry-v1
risk-score-v1
risk-score-batch-v1
incident-report-v1
incident-sync-v1
photo-upload-v1
district-status-v1
route-accessibility-v1
alert-event-v1
standard-error-v1
pagination-v1
contract-status-v1
```

Do not declare the current candidates frozen until this process finishes.

## 4. Spring Boot responsibilities

Extend `services/stockflow-core-api` as the only browser-facing orchestration boundary.

Implement or finalize:

- Authenticated tenant-aware incident/photo/sync endpoints.
- Risk-scoring internal client and mapping.
- Authoritative district status persistence/orchestration.
- Route-assessment orchestration across Google Routes, optimization and carbon services.
- Read APIs for GIS layers and assistant evidence.
- Standard validation/error envelopes.
- Idempotency receipts and revision conflicts.
- Audit metadata and data freshness.

Rules:

- Derive/verify tenant from the authenticated context and existing tenant conventions.
- Never accept a browser-supplied tenant blindly.
- Internal services are not public browser endpoints.
- Apply bounded timeouts and controlled degraded states.
- Do not map a service outage/no response to safe/zero/open.
- Never expose API keys or upstream secrets.

## 5. PostgreSQL responsibilities

PostgreSQL is authoritative. Minimum domain storage includes:

```text
ner_district
ner_road_segment
ner_risk_score
ner_incident_report
ner_incident_revision
ner_photo_reference
ner_district_status_snapshot
ner_route_assessment
ner_sync_receipt
```

Requirements:

- Tenant ID on tenant-owned rows.
- Stable IDs matching contracts.
- Audit-friendly revisions instead of destructive overwrite.
- Unique constraints for idempotency keys/receipts.
- Server timestamps for ordering.
- Status transitions authorized by role.
- Geometry representation/indexing chosen consistently.
- Migration rollback/recovery documented.

## 6. Offline sync authority

Arnab owns the browser queue; the integration owner owns server behavior:

- Same idempotency key returns the original result.
- Lower client revision cannot overwrite a newer server revision.
- Unauthorized verification/clear/reject transitions fail.
- Conflicts return `409` with the current server record and revision metadata.
- Photo acknowledgement and incident acknowledgement are distinct.
- Server time/revision is authoritative when clocks differ.

## 7. Internal service reliability

For hazard, optimization, carbon, copilot, Google and Fleetbase calls:

- Configure URLs/keys using environment variables.
- Set connect/read timeouts.
- Validate responses at the boundary.
- Propagate a trace/request ID.
- Log identifiers and status without secrets/personal photo data.
- Distinguish validation failure, upstream rejection, timeout and service outage.
- Return cached/stale data only with an explicit stale label.
- Provide health/configuration status without secret values.

## 8. Merge order

Recommended order:

1. Contracts, registries and fixtures.
2. Spring migrations and endpoint scaffolding.
3. Dharmanshu scorer and client integration.
4. Arnab incident/sync workflow.
5. Pari routing/carbon workflow.
6. Shreyas GIS and copilot presentation.
7. End-to-end fixes only; defer unrelated polish.

Require each pull request to include:

- Contract versions.
- Files/endpoints changed.
- Tests and commands run.
- Example request/response.
- Security/data implications.
- Known limitations.

## 9. Required tests

- Schema fixtures validate in producer and consumer languages.
- Invalid version/enum/coordinates are rejected.
- Tenant A cannot read/write Tenant B records.
- Idempotency prevents duplicate incident/photo/dispatch operations.
- Revision conflict returns visible `409` data.
- Missing scorer data becomes `NO_DATA`.
- Service timeout becomes unavailable/degraded, not safe.
- Expired risks and cleared incidents do not affect status/routing.
- At least two road routes can be ranked with evidence.
- Angular → Spring → PostgreSQL → services → Angular works in running processes.
- Secrets and local databases are absent from Git history/staged files.

Run the existing project checks:

```cmd
cd apps\stockflow-web
npm run build
```

```cmd
cd services\stockflow-core-api
mvn test
```

Run each Python service's documented test command as well.

## 10. Demo/release checklist

Before the demo:

- Document exact startup order and environment variable names.
- Verify required services from a clean terminal session.
- Seed one repeatable scenario using approved fixtures.
- Complete the full offline-to-route-to-assistant flow.
- Rehearse external API failure and provide backup media.
- Clearly label mock, heuristic and simulated content.
- Record known limitations and unsupported provider coverage.
- Verify the Git branch/commit used for the demo.
- Confirm the working tree is clean and no secrets are committed.

## 11. Definition of done

- A named person owns integration decisions.
- All required contracts are formal, versioned and tested.
- Spring Boot and PostgreSQL remain authoritative and tenant-safe.
- Every contributor's module runs through documented boundaries.
- Offline sync is idempotent and conflicts are visible.
- External outages degrade honestly.
- One-command or exact step-by-step startup works.
- Complete end-to-end demo passes from field report through GIS/route/assistant.
- Reviewed work is merged without secrets or incompatible stack changes.
