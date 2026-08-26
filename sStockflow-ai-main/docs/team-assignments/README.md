# StockFlow NER Team Assignments

This directory translates the shared integration plan into a detailed, individual execution guide for each contributor.

## Assignment files

- [Dharmanshu — hazard scoring and district merge](./DHARMANSHU_ASSIGNMENT.md)
- [Arnab — field reporting and offline synchronization](./ARNAB_ASSIGNMENT.md)
- [Pari — risk-aware routing and sustainability](./PARI_ASSIGNMENT.md)
- [Shreyas — NER data, GIS dashboard and assistant UX](./SHREYAS_ASSIGNMENT.md)
- [Integration owner — contracts, Spring orchestration and release](./INTEGRATION_OWNER_ASSIGNMENT.md)

The source of truth for shared architecture and candidate contracts remains [`NER_TEAM_INTEGRATION_AND_EXECUTION_PLAN.md`](../../NER_TEAM_INTEGRATION_AND_EXECUTION_PLAN.md).

## Important status: contracts are proposed, not frozen yet

`RiskScoreV1`, `IncidentReportV1`, `DistrictStatusV1` and the route contracts are currently **contract candidates**. They must not be described as final until the team:

1. Reviews every required and optional field.
2. Resolves missing batch, sync, media, pagination and error payloads.
3. Creates formal JSON Schema files under `contracts/ner/`.
4. Adds one valid and several invalid fixtures for each schema.
5. Runs producer and consumer validation tests.
6. Commits and tags the approved contract version.

Until then, each person should isolate contract mapping in one adapter/module so a field change does not require rewriting their whole feature.

## Non-negotiable architecture

| Layer | Approved implementation |
|---|---|
| Web application | Existing Angular app in `apps/stockflow-web` |
| Core/public API | Existing Kotlin/Spring Boot service in `services/stockflow-core-api` |
| Authoritative database | Existing PostgreSQL database |
| Hazard calculation | Small internal Python/FastAPI service |
| Route optimization | Existing `services/optimisation-service` and Spring orchestration |
| Sustainability | Existing `services/carbon-service` |
| Assistant | Existing `services/copilot-service` |
| Browser offline data | IndexedDB; never a shared SQLite file |
| Native mobile offline data | Device SQLite only if a native client is explicitly approved |
| Maps/routes | Existing Google Maps and Routes integration, with documented fallback |
| Vehicle operations | Existing Fleetbase integration |

Do not create a second React frontend, a replacement standalone FastAPI product, a second authoritative database, or a new authentication system.

## Shared data rules

- JSON uses `snake_case`.
- Timestamps use ISO-8601 UTC, such as `2026-08-26T06:00:00Z`.
- GeoJSON coordinates use `[longitude, latitude]`.
- Every tenant-owned payload contains `tenant_id`.
- Every versioned payload contains `schema_version`.
- Enumerations use the exact agreed uppercase value.
- Unknown, missing, malformed or expired information becomes `NO_DATA`; it never silently becomes safe/open/green.
- Mock, heuristic, official, field and manual sources remain distinguishable.
- Browser clients call Spring Boot; they do not call internal Python services directly.
- API keys and service credentials remain on the backend.
- Photos are uploaded to object storage and represented by references; JSON never contains local filesystem paths.

## Mandatory handoff sequence

1. **Freeze contracts:** integration owner publishes schemas, fixtures and API examples.
2. **Implement independently:** each contributor builds only against the frozen contract.
3. **Contract test:** producer output validates against the same schema used by consumers.
4. **Integration checkpoint:** run Angular → Spring → PostgreSQL → internal services → Angular.
5. **Failure rehearsal:** test offline, invalid data, service outage, stale data and conflicts.
6. **Demo rehearsal:** use one repeatable Guwahati–Shillong scenario and disclose simulated elements.

## Branch and pull-request rules

- Pull or fetch the latest `main` before starting.
- Use one feature branch per assignment.
- Do not mix unrelated refactors into an integration pull request.
- Never commit `.env` files, API keys, tokens, local databases, uploaded photographs or build output.
- Include contract examples, tests and run instructions in the same pull request as the feature.
- A schema change requires approval from the integration owner and every affected producer/consumer.
- A contributor must not push directly over another contributor's unfinished branch.

Suggested branches:

```text
feature/ner-hazard-scorer
feature/ner-field-reporting
feature/ner-risk-routing
feature/ner-gis-assistant
```

## Integration checkpoint definition

The team is integrated only when all of these work in one running application:

1. An Angular incident form creates or queues an incident.
2. Spring validates and persists it with tenant isolation.
3. Spring calls the hazard scorer using the frozen internal contract.
4. A district status is recalculated using current risks and reports.
5. At least two road-aligned route candidates are assessed.
6. The GIS screen renders the district, incident, risks and selected route.
7. The assistant explains the route using current IDs, evidence and timestamps.
8. Offline retry is idempotent and a deliberate conflict is visible to the user.

## Team communication rule

When blocked, report:

```text
Owner:
Branch/commit:
Blocked component:
Endpoint or contract:
Expected payload:
Actual payload/error:
Reproduction steps:
Decision required:
```

Do not solve a cross-team mismatch by silently renaming fields or changing enums.
