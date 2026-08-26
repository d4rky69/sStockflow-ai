# StockFlow NER Prototype — Team Integration and Execution Plan

**Status:** Proposed contract freeze for team review
**Target:** SIH26002 internal-hackathon prototype
**Last updated:** 26 August 2026
**Primary rule:** Do not continue feature development until the contracts in this document are reviewed, corrected where necessary, and frozen as `v1`.

---

## 1. Purpose

This document is the single implementation and handoff guide for the StockFlow NER logistics prototype. It defines:

- What the prototype will and will not claim.
- Which parts of the existing StockFlow platform must be reused.
- Who owns each component.
- The JSON structures exchanged between components.
- The API and persistence boundaries.
- How offline reports synchronize safely.
- How hazard scores affect district status and routes.
- How the team integrates, tests and demonstrates the complete system.

If code, chat messages or older documents conflict with this file after contract freeze, this file takes precedence until the team deliberately publishes a newer schema version.

---

## 2. Prototype outcome

The prototype should demonstrate this complete decision flow:

```text
Mock/official weather + terrain/soil scenario data
                         │
                         ▼
            Heuristic hazard risk scoring
                         │
          ┌──────────────┴──────────────┐
          ▼                             ▼
Geo-tagged field reports       Risk-aware route comparison
          │                             │
          └──────────────┬──────────────┘
                         ▼
             District accessibility status
                         │
                         ▼
      GIS dashboard + alerts + logistics assistant
```

The prototype must allow a judge to:

1. View NER districts and logistics corridors on a GIS map.
2. See which data is mock, heuristic, official or field-reported.
3. Submit or simulate a geo-tagged road incident.
4. Observe the report enter the district accessibility calculation.
5. Compare a normal route with a safer alternative.
6. Inspect route ETA, risk exposure and sustainability evidence.
7. Track a Fleetbase vehicle or run the clearly labelled GPS simulation.
8. Ask the assistant a grounded logistics question.
9. Demonstrate an offline report being queued and later synchronized.

---

## 3. Non-negotiable truthfulness rules

### 3.1 Data labels

Every risk, alert, incident and route must identify its origin using one of these values:

| `source_type` | Meaning |
|---|---|
| `MOCK` | Designed demonstration data; not a real observation |
| `HEURISTIC` | Calculated by the prototype rule-based scorer |
| `OFFICIAL` | Returned by an identified authority or official external feed |
| `FIELD_REPORT` | Submitted by a field user and not necessarily verified |
| `MANUAL_OVERRIDE` | Deliberate dispatcher or administrator decision |

### 3.2 Claims

- The current scorer is a **heuristic risk-scoring prototype**, not a trained AI prediction model.
- Google Public Alerts are authority-issued alerts, not StockFlow predictions.
- Absence of an external alert does not prove that an area is safe.
- Simulated GPS movement must remain visibly marked as a prototype simulation.
- A mock or heuristic risk zone must never be styled or described as an official warning.
- The system must never invent a road closure, vehicle position or government alert.

### 3.3 No-data behavior

Unknown data must be shown as `NO_DATA`/grey. Unknown or malformed values must never silently become green/safe.

---

## 4. Architecture decision

### 4.1 Keep the existing StockFlow platform

Do not rewrite the application as a separate React/FastAPI/SQLite product. Reuse the working system:

| Layer | Approved technology | Responsibility |
|---|---|---|
| Web frontend | Existing Angular application | Dashboard, GIS, forms, route comparison, assistant |
| Core API | Existing Kotlin + Spring Boot service | Authentication, tenants, API orchestration, reports, district status, route requests |
| Server database | Existing PostgreSQL | Authoritative shared records and audit history |
| Hazard scorer | Small Python + FastAPI service | Heuristic batch scoring only |
| Client offline store | IndexedDB for web; SQLite only if a native mobile client is built | Unsynchronized reports and photo upload queue |
| Object storage | Supabase Storage or another approved bucket | Field photographs; database stores references only |
| GIS/routing | Existing Google Maps/Routes with OSM/Leaflet fallback | Basemap, route geometry, markers and hazard overlays |
| Fleet tracking | Existing Fleetbase integration | Vehicle registry, GPS/tracker/ETA and lifecycle events |
| Authentication | Existing Supabase authentication | User identity and session |

### 4.2 Request flow

```text
Angular frontend
    │ Supabase JWT + X-Tenant-ID
    ▼
Spring Boot core API
    ├── PostgreSQL
    ├── Fleetbase
    ├── Google Routes / Weather / Public Alerts
    └── Internal HTTP call → Python hazard scorer
```

The browser must not call the Python scorer, Fleetbase server API or protected Google server APIs directly.

### 4.3 Repository placement

Add new work inside the existing repository:

```text
stockflow-repair/
├── apps/stockflow-web/
│   └── src/app/features/ner-operations/
│       ├── district-accessibility/
│       ├── incident-reporting/
│       └── route-accessibility/
├── services/stockflow-core-api/
│   └── src/main/kotlin/com/stockflow/ner/
│       ├── incidents/
│       ├── accessibility/
│       └── routing/
├── services/hazard-scoring-service/
│   ├── pyproject.toml
│   ├── src/stockflow_hazard/
│   │   ├── main.py
│   │   ├── scorer.py
│   │   └── contracts.py
│   └── tests/
└── contracts/ner/
    ├── risk-score-v1.schema.json
    ├── incident-report-v1.schema.json
    ├── district-status-v1.schema.json
    └── route-accessibility-v1.schema.json
```

Do not create a second top-level frontend or duplicate the dashboard.

---

## 5. Ownership and handoffs

| Owner | Primary deliverables | Must not own alone |
|---|---|---|
| Dharmanshu | Heuristic scorer, scorer FastAPI endpoints, district merge rules, scorer tests | Angular dashboard or client offline queue |
| Arnab | Incident form, GPS/timestamp capture, photograph compression/upload, offline queue and sync client | Server conflict rules or risk-score formula |
| Pari | Route risk penalties, alternate-route ranking and sustainability/green score | Hazard-data cleanup or district dashboard |
| Shreyas | NER data preparation, Angular GIS/dashboard UI, assistant prompts and field-readable UX | Server persistence or offline synchronization algorithm |
| Integration owner | Contract repository, ID registry, API handoff, integration branch, final test, deployment and demo rehearsal | Unreviewed schema changes |

The team must explicitly name the integration owner before parallel development resumes.

### 5.1 Handoff rule

Members exchange data only through a frozen JSON contract or documented function/API. No one should import another member's internal database tables or private implementation functions.

---

## 6. Contract governance

### 6.1 General rules

- Use `snake_case` in JSON.
- Use UTF-8.
- Use ISO 8601 UTC timestamps ending in `Z`.
- Use GeoJSON order: `[longitude, latitude]`.
- Use scores from `0.0` through `1.0`.
- Use kilometres, kilometres/hour, millimetres/hour and degrees unless a field says otherwise.
- IDs are lowercase ASCII slugs or UUIDs as defined below.
- Required properties cannot be silently omitted.
- New optional properties may be added within `v1`; renamed or removed properties require `v2`.
- Every response includes `schema_version`.

### 6.2 Canonical IDs

| Entity | Format | Example |
|---|---|---|
| District | `<district>_<state>` | `east_khasi_hills_meghalaya` |
| Road segment | `seg_<district-code>_<number>` | `seg_ekh_001` |
| Report | UUID | `96e87825-2c4d-4a17-919a-337926f3473f` |
| Risk score | `<segment-id>:<hazard>:<valid-from>` or UUID | `seg_ekh_001:landslide:2026-08-26T06:00:00Z` |
| Route request | UUID | `95a375f4-0967-48ba-aa52-fcb054a29d27` |

Create one version-controlled `district-registry.json` containing the allowed district IDs, display names, state, centroid and boundary reference. Do not let each member invent district IDs.

### 6.3 Shared enums

```text
hazard_type:      FLOOD | LANDSLIDE | ROAD_BLOCK | BRIDGE_DAMAGE | OTHER
risk_level:       LOW | MEDIUM | HIGH | EXTREME | NO_DATA
source_type:      MOCK | HEURISTIC | OFFICIAL | FIELD_REPORT | MANUAL_OVERRIDE
incident_status:  OPEN | VERIFIED | CLEARED | REJECTED
severity:         LOW | MEDIUM | HIGH | EXTREME
district_status:  OPEN | CAUTION | RESTRICTED | ISOLATED | NO_DATA
sync_status:      LOCAL_ONLY | QUEUED | SYNCED | CONFLICT | FAILED
```

---

## 7. Frozen contract candidates

These structures are the candidates to review and freeze. Once approved, copy them into formal JSON Schema files under `contracts/ner/`.

### 7.1 `RiskScoreV1`

```json
{
  "schema_version": "1.0",
  "risk_id": "seg_ekh_001:landslide:2026-08-26T06:00:00Z",
  "tenant_id": "TEN-ACME-PHARMA",
  "district_id": "east_khasi_hills_meghalaya",
  "segment_id": "seg_ekh_001",
  "hazard_type": "LANDSLIDE",
  "risk_score": 0.82,
  "risk_level": "HIGH",
  "confidence": 0.55,
  "source_type": "HEURISTIC",
  "observed_at": "2026-08-26T05:55:00Z",
  "valid_from": "2026-08-26T06:00:00Z",
  "valid_until": "2026-08-26T12:00:00Z",
  "geometry": {
    "type": "LineString",
    "coordinates": [
      [91.8201, 25.5301],
      [91.8514, 25.5632]
    ]
  },
  "inputs": {
    "rainfall_mm_hr": 62.0,
    "slope_degrees": 38.0,
    "soil_type": "clay"
  },
  "evidence": [
    {
      "type": "DATASET",
      "label": "Prototype NER terrain scenario",
      "reference": "mock://ner-terrain-v1"
    }
  ],
  "model": {
    "name": "ner-rule-scorer",
    "version": "1.0.0",
    "method": "HEURISTIC_WEIGHTED_SCORE"
  }
}
```

Validation rules:

- `risk_score` and `confidence` must be between `0` and `1`.
- `valid_until` must be later than `valid_from`.
- `risk_level` must be derived consistently from documented thresholds.
- `source_type=OFFICIAL` requires an authority name and source URL in `evidence`.
- `source_type=MOCK` must display a demo label in the UI.
- Expired risk scores cannot determine the current district status.

### 7.2 `IncidentReportV1`

```json
{
  "schema_version": "1.0",
  "report_id": "96e87825-2c4d-4a17-919a-337926f3473f",
  "tenant_id": "TEN-ACME-PHARMA",
  "district_id": "east_khasi_hills_meghalaya",
  "segment_id": "seg_ekh_001",
  "incident_type": "LANDSLIDE",
  "severity": "HIGH",
  "status": "OPEN",
  "description": "Debris blocks one lane near the bend.",
  "location": {
    "type": "Point",
    "coordinates": [91.8421, 25.5482],
    "accuracy_metres": 18.5,
    "snapped_to_road": true,
    "snap_distance_metres": 9.2
  },
  "device_observed_at": "2026-08-26T06:10:00Z",
  "client_created_at": "2026-08-26T06:10:12Z",
  "server_received_at": null,
  "reporter": {
    "user_id": "supabase-user-id",
    "role": "FIELD_OFFICER"
  },
  "photos": [
    {
      "upload_id": "local-photo-01",
      "object_url": null,
      "content_type": "image/jpeg",
      "size_bytes": 184220,
      "sha256": "hex-encoded-sha256"
    }
  ],
  "source_type": "FIELD_REPORT",
  "verification": {
    "verified": false,
    "verified_by": null,
    "verified_at": null
  },
  "sync": {
    "idempotency_key": "96e87825-2c4d-4a17-919a-337926f3473f:create",
    "client_revision": 1,
    "server_revision": null,
    "sync_status": "QUEUED",
    "last_attempt_at": null
  }
}
```

Validation rules:

- The API accepts a photograph reference/metadata, not a local file path.
- Photographs are uploaded separately and linked using `upload_id` or `object_url`.
- Latitude must be `-90..90`; longitude must be `-180..180`.
- `accuracy_metres` is required for device GPS reports.
- A report can be cleared only through a later revision or status event; do not represent clearance only as `severity=LOW`.
- Client timestamps are evidence, but the server timestamp is authoritative for synchronization ordering when clocks disagree.

### 7.3 `DistrictStatusV1`

```json
{
  "schema_version": "1.0",
  "tenant_id": "TEN-ACME-PHARMA",
  "district_id": "east_khasi_hills_meghalaya",
  "display_name": "East Khasi Hills",
  "state_name": "Meghalaya",
  "accessibility_status": "RESTRICTED",
  "color": "RED",
  "score": 0.86,
  "calculated_at": "2026-08-26T06:15:00Z",
  "valid_until": "2026-08-26T07:15:00Z",
  "reason_codes": [
    "VERIFIED_HIGH_SEVERITY_INCIDENT",
    "HIGH_LANDSLIDE_RISK"
  ],
  "worst_segments": [
    {
      "segment_id": "seg_ekh_001",
      "hazard_type": "LANDSLIDE",
      "risk_score": 0.82
    }
  ],
  "active_report_ids": [
    "96e87825-2c4d-4a17-919a-337926f3473f"
  ],
  "data_freshness": "CURRENT",
  "source_summary": {
    "mock": 0,
    "heuristic": 1,
    "official": 0,
    "field_report": 1
  }
}
```

District merge rules:

1. A verified `EXTREME` open incident may produce `ISOLATED`.
2. A verified `HIGH` open incident produces at least `RESTRICTED`.
3. An unverified high incident produces at least `CAUTION`, unless confirmed by another source.
4. A current high/extreme risk score may produce `RESTRICTED` according to agreed thresholds.
5. Expired risks and cleared/rejected incidents are excluded.
6. No current inputs produces `NO_DATA`, never `OPEN`.
7. Manual overrides require an actor, reason and expiry time.

### 7.4 `RouteAccessibilityRequestV1`

```json
{
  "schema_version": "1.0",
  "request_id": "95a375f4-0967-48ba-aa52-fcb054a29d27",
  "tenant_id": "TEN-ACME-PHARMA",
  "origin": {
    "latitude": 26.1445,
    "longitude": 91.7362,
    "label": "Guwahati logistics hub"
  },
  "destination": {
    "latitude": 25.5788,
    "longitude": 91.8933,
    "label": "Shillong relief hub"
  },
  "vehicle": {
    "vehicle_id": "vehicle_nphifra6up",
    "vehicle_type": "TRUCK",
    "payload_kg": 1400.0,
    "fuel_type": "DIESEL"
  },
  "cargo": {
    "category": "MEDICINE",
    "priority": "ESSENTIAL",
    "temperature_controlled": false
  },
  "objectives": {
    "safety_weight": 0.50,
    "eta_weight": 0.25,
    "cost_weight": 0.15,
    "emissions_weight": 0.10
  },
  "departure_time": "2026-08-26T06:30:00Z",
  "alternatives_requested": 3
}
```

### 7.5 `RouteAccessibilityResponseV1`

```json
{
  "schema_version": "1.0",
  "request_id": "95a375f4-0967-48ba-aa52-fcb054a29d27",
  "generated_at": "2026-08-26T06:15:10Z",
  "recommended_route_id": "route_safe_01",
  "routes": [
    {
      "route_id": "route_fast_01",
      "label": "Fastest",
      "distance_km": 102.4,
      "eta_minutes": 163,
      "estimated_cost_inr": 5340.0,
      "estimated_co2e_kg": 48.2,
      "green_score": 71,
      "hazard_exposure_score": 0.74,
      "accessibility": "RESTRICTED",
      "intersecting_risks": [
        {
          "risk_id": "seg_ekh_001:landslide:2026-08-26T06:00:00Z",
          "segment_id": "seg_ekh_001",
          "hazard_type": "LANDSLIDE",
          "risk_score": 0.82
        }
      ],
      "geometry": {
        "type": "LineString",
        "coordinates": [[91.7362, 26.1445], [91.8933, 25.5788]]
      },
      "source_type": "HEURISTIC"
    },
    {
      "route_id": "route_safe_01",
      "label": "Safer alternative",
      "distance_km": 116.8,
      "eta_minutes": 188,
      "estimated_cost_inr": 5810.0,
      "estimated_co2e_kg": 52.4,
      "green_score": 68,
      "hazard_exposure_score": 0.18,
      "accessibility": "OPEN",
      "intersecting_risks": [],
      "geometry": {
        "type": "LineString",
        "coordinates": [[91.7362, 26.1445], [91.8100, 25.9000], [91.8933, 25.5788]]
      },
      "source_type": "HEURISTIC"
    }
  ],
  "explanation": "The safer route adds 25 minutes but avoids the high-risk landslide segment.",
  "warnings": [
    "Prototype risk scores include heuristic data. Confirm field conditions before dispatch."
  ]
}
```

---

## 8. API boundary

### 8.1 Browser-facing Spring Boot endpoints

```text
POST   /api/v1/ner/incidents
GET    /api/v1/ner/incidents
PATCH  /api/v1/ner/incidents/{reportId}/status
POST   /api/v1/ner/incidents/sync
POST   /api/v1/ner/photos/upload-url
GET    /api/v1/ner/districts/status
GET    /api/v1/ner/districts/{districtId}/status
POST   /api/v1/ner/routes/compare
GET    /api/v1/ner/contracts/status
```

All endpoints require:

```text
Authorization: Bearer <Supabase access token>
X-Tenant-ID: <tenant ID>
```

Write requests also require:

```text
Idempotency-Key: <stable operation key>
```

### 8.2 Internal hazard-service endpoints

```text
GET  /health
GET  /contracts
POST /v1/risk-scores/batch
```

The Spring Boot service calls these endpoints. They should not be exposed directly to the public browser.

### 8.3 Standard error envelope

```json
{
  "timestamp": "2026-08-26T06:20:00Z",
  "status": 400,
  "code": "INVALID_INCIDENT_REPORT",
  "message": "severity must be one of LOW, MEDIUM, HIGH or EXTREME",
  "field_errors": [
    {
      "field": "severity",
      "reason": "INVALID_ENUM"
    }
  ],
  "trace_id": "request-trace-id"
}
```

---

## 9. Persistence

### 9.1 Server database

PostgreSQL is the system of record. Minimum tables:

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

Each tenant-owned table must contain `tenant_id`. Incident and status changes must be auditable rather than overwritten without history.

### 9.2 Client offline database

IndexedDB stores:

```text
pending_incidents
pending_photos
sync_receipts
cached_district_status
cached_contract_metadata
```

The browser database is not authoritative and must not be shared as a physical file with the server.

---

## 10. Offline synchronization protocol

### 10.1 Queue behavior

1. Generate the report UUID on the client.
2. Validate against `IncidentReportV1`.
3. Compress each photograph before queueing.
4. Store the report and photo metadata locally in one transaction.
5. Display `LOCAL_ONLY` or `QUEUED` visibly.
6. When online, upload photographs first.
7. Replace local upload IDs with returned object references.
8. Send report revisions in a batch with idempotency keys.
9. Store the server revision and receipt.
10. Remove local binary photos only after confirmed upload and report acknowledgement.

### 10.2 Conflict rules

- The server never trusts device time alone.
- Report identity is `report_id`; revisions increment monotonically.
- A repeated idempotency key returns the original result.
- A lower client revision cannot overwrite a higher server revision.
- `VERIFIED`, `CLEARED` and `REJECTED` transitions require server permission.
- Conflicting edits produce `409 CONFLICT` with the current server record.
- The UI asks the user to reconcile a conflict; it must not silently discard either version.

### 10.3 Retry policy

- Retry transient failures with exponential backoff.
- Do not retry validation errors until the payload changes.
- Preserve failed records across refresh/restart.
- Show the number of queued and failed reports.

---

## 11. Hazard scoring

### 11.1 Prototype formula

The team may use the supplied rule-based scorer for the internal prototype, subject to these requirements:

- Document the formula and thresholds.
- Validate units and allowed ranges.
- Score flood and landslide separately.
- Return `NO_DATA` when required inputs are absent.
- Include scorer name and version.
- Include input evidence in the response.
- Add tests for boundaries, missing data, invalid enums and expiry.
- Do not call it a trained ML model.

### 11.2 Future ML replacement

The `RiskScoreV1` contract must remain stable when the heuristic scorer is later replaced by XGBoost or another validated model. Consumers should not depend on internal model features.

### 11.3 Recommended threshold review

Do not finalize `LOW/MEDIUM/HIGH/EXTREME` thresholds without a team decision. Store thresholds in configuration and include the configuration version in output.

---

## 12. Routing and accessibility

### 12.1 Base routes

Use the existing Google Routes integration to obtain road-aligned alternatives. Keep OSM/OSRM as a fallback only if the Google route service is unavailable or the team deliberately chooses an open-source demo mode.

### 12.2 Risk exposure

For each candidate route:

1. Decode the route geometry.
2. Intersect it with current risk/incident geometries.
3. Measure exposed distance and maximum risk.
4. Apply configurable penalties.
5. Calculate ETA, cost and emissions.
6. Rank using the request's objective weights.
7. Return all evidence, not only the winning route.

An illustrative route score is:

```text
total_penalty =
    safety_weight    × hazard_exposure_score
  + eta_weight       × normalized_eta
  + cost_weight      × normalized_cost
  + emissions_weight × normalized_emissions
```

The exact formula and normalization must be versioned and tested.

### 12.3 Hard constraints

- A verified closed/isolated segment is excluded, not merely penalized.
- Essential cargo may prioritize safety and service differently from ordinary cargo.
- Unknown accessibility adds uncertainty; it does not imply safe passage.

---

## 13. GIS dashboard behavior

Required layers:

- District boundaries colored by `DistrictStatusV1`.
- Road/logistics corridors.
- Current Fleetbase vehicle positions.
- Heuristic risk segments with clear source labels.
- Official weather/hazard polygons where available.
- Field incident markers and photographs.
- Selected route and alternate routes.
- Warehouses, logistics hubs and checkpoints.

Required legend:

```text
Green  = Open with current supporting data
Amber  = Caution
Red    = Restricted/isolated
Grey   = No current data
Blue   = Official flood alert
Orange = Landslide risk/alert
```

Every popup must show source, timestamp, validity, verification state and whether the data is mock.

---

## 14. Assistant behavior

The logistics assistant may answer questions such as:

- “Is the NH-27 corridor clear?”
- “Show safer alternatives to Shillong.”
- “Which districts currently have no accessibility data?”
- “Why was this route rejected?”

Rules:

- Retrieve live API data before answering operational questions.
- Cite district status, risk IDs, report IDs or route evidence.
- Distinguish official, field, heuristic and mock data.
- State uncertainty and data age.
- Never declare a route safe based only on missing alerts.
- Do not autonomously dispatch a vehicle or approve a transfer.

---

## 15. Security and privacy

- Never put backend Google, Fleetbase or service-role keys in the browser.
- Validate Supabase JWTs at the Spring Boot boundary.
- Verify tenant membership before trusting `X-Tenant-ID`.
- Apply roles to incident verification, override and route-dispatch actions.
- Strip image metadata that is not required.
- Reject unsupported files and enforce size limits.
- Store content hashes for duplicate detection.
- Use signed upload/download URLs for private photographs.
- Log actor, tenant, action, timestamp and result for every status change.
- Do not expose reporter personal data in public map popups.

---

## 16. Build and integration sequence

### Stage 0 — Stop and inventory current work (30–60 minutes)

- Everyone stops producing incompatible modules.
- Each person lists files, functions and payloads already created.
- No one deletes work; reusable logic is mapped to the approved architecture.

### Stage 1 — Contract freeze (maximum 2 hours)

- Approve the four contracts in Section 7.
- Approve enums and district ID registry.
- Name the integration owner.
- Publish JSON Schema files and example fixtures.
- Add contract validation tests.
- Tag the contract commit, for example `contracts-ner-v1`.

No parallel feature work resumes before this stage passes.

### Stage 2 — Parallel implementation

Dharmanshu:

- Adapt scorer output to `RiskScoreV1`.
- Return `NO_DATA` safely.
- Wrap the scorer in the internal FastAPI service.
- Implement district merge rules and tests.

Arnab:

- Build the Angular incident form and client queue.
- Capture location accuracy and timestamp.
- Compress photos and use upload references.
- Implement retry and conflict UI against the sync contract.

Pari:

- Consume only `RiskScoreV1` and route contracts.
- Implement route intersection, penalty and ranking logic.
- Return multiple route options with evidence.
- Implement green-score calculations with stated assumptions.

Shreyas:

- Create the district registry/boundary fixture with provenance.
- Build the Angular district map and status panels.
- Update assistant prompts and tool calls.
- Ensure source labels, high contrast and responsive layouts.

Integration owner:

- Scaffold Spring endpoints and database migrations.
- Maintain fixtures and contract test pipeline.
- Resolve cross-module questions without silently changing schemas.

### Stage 3 — Mandatory integration checkpoint

- Run one report from Angular → Spring → PostgreSQL.
- Run one batch from Spring → scorer → Spring.
- Recalculate one district status.
- Generate at least two route options.
- Render the result on the map.
- Ask the assistant about that exact route.
- Record every mismatch before continuing polish.

### Stage 4 — Offline and failure rehearsal

- Disconnect the browser/network.
- Create a field report and attach a compressed test photo.
- Refresh/reopen and verify the queued report remains.
- Restore connectivity and synchronize once.
- Retry the same idempotency key and confirm no duplicate.
- Create a revision conflict and verify visible resolution.
- Disable the scorer and show a controlled degraded state.

### Stage 5 — Demo freeze and polish

- Stop schema and architectural changes.
- Fix only defects that block the defined demo.
- Freeze a known demonstration dataset.
- Document exact startup commands.
- Rehearse on the actual presentation machine and network.

---

## 17. Git workflow

- Do not send ZIP archives as the primary collaboration method after repository access is available.
- Create one branch per bounded responsibility.
- Pull/rebase from the agreed integration branch before handoff.
- Keep schema changes in dedicated commits.
- Never commit secrets, local databases, uploaded photographs or build output.
- Require at least one teammate to review contract and integration changes.

Suggested branches:

```text
feature/ner-hazard-scorer
feature/ner-incident-reporting
feature/ner-route-risk
feature/ner-district-dashboard
integration/ner-prototype
```

Every handoff message should include:

```text
Branch/commit:
Contract version:
What works:
What remains mocked:
How to test:
Known limitations:
```

---

## 18. Testing requirements

### 18.1 Contract tests

- Valid fixture accepted by every producer and consumer.
- Missing required fields rejected.
- Invalid enum rejected.
- Invalid coordinates rejected.
- Wrong schema version rejected with a clear error.

### 18.2 Hazard scorer tests

- Zero, boundary and maximum inputs.
- Missing rainfall/slope/soil behavior.
- Flood versus landslide scoring.
- Risk threshold boundaries.
- Expiry and `NO_DATA` output.
- Deterministic result for identical inputs.

### 18.3 Sync tests

- Offline persistence survives reload.
- Duplicate idempotency key does not duplicate a report.
- Failed photo upload does not mark report synced.
- Newer server revision causes conflict.
- Cleared incident does not remain active.
- Retry resumes after connectivity restoration.

### 18.4 Dashboard tests

- High verified incident overrides a low heuristic score.
- Cleared/rejected incidents do not override.
- Expired risks are ignored.
- No data renders grey.
- Source badges and timestamps render correctly.
- District registry mismatch fails loudly.

### 18.5 Route tests

- Closed segment excluded.
- High-risk segment receives penalty.
- Safer alternative can win despite longer ETA.
- Objective weights change ranking predictably.
- Route output includes evidence and geometry.

### 18.6 Existing project verification

```cmd
cd apps\stockflow-web
npm run build
```

```cmd
cd services\stockflow-core-api
mvn test
```

```cmd
cd services\hazard-scoring-service
pytest
```

The final test must run the complete application, not only Python functions imported into one process.

---

## 19. Demo scenario

Use one controlled, repeatable story:

1. Open the NER GIS dashboard.
2. Show the source legend and explain mock versus official data.
3. Select an essential-medicine transfer from Guwahati to Shillong.
4. Display the normal route and current ETA.
5. Submit a simulated field landslide report while offline.
6. Restore connectivity and synchronize the report.
7. Show East Khasi Hills change from caution/open to restricted.
8. Recalculate routes.
9. Show the safer route avoiding the affected segment, including time/cost/CO2 trade-offs.
10. Track the selected Fleetbase vehicle or clearly labelled GPS simulation.
11. Ask the assistant why the route changed.
12. Show the evidence and limitations rather than claiming unsupported prediction accuracy.

The demo owner must write a click-by-click script and keep backup screenshots/video in case an external API is unavailable.

---

## 20. Definition of done

### Dharmanshu

- Scorer accepts and returns frozen contracts.
- FastAPI docs expose the exact internal API.
- Invalid/missing data fails safely.
- District merge uses status, freshness and verification.
- Tests pass and limitations are documented.

### Arnab

- Form captures required fields and GPS accuracy.
- Photos are compressed and uploaded by reference.
- Offline queue survives reload.
- Retry/idempotency/conflict behavior is demonstrated.
- No local filesystem path is sent as a server photo reference.

### Pari

- At least two real road-aligned route candidates are compared.
- Hazard intersection changes ranking predictably.
- Closed segments are excluded.
- ETA, cost, emissions and assumptions are returned.
- Route output matches the frozen contract.

### Shreyas

- District map uses the canonical registry.
- Grey no-data state exists.
- Popups expose provenance, time and validity.
- Assistant answers from current API evidence.
- UI remains readable and responsive.

### Integration owner

- One-command or clearly documented startup succeeds.
- Authentication and tenant isolation remain intact.
- Complete end-to-end scenario passes.
- No secrets or local databases are committed.
- Demo script and fallback assets are ready.

---

## 21. Immediate team checklist

- [ ] Stop feature production until contract review is complete.
- [ ] Name the integration owner.
- [ ] Approve the existing Angular/Spring/PostgreSQL architecture.
- [ ] Decide whether a native mobile client is actually required for the internal round.
- [ ] Freeze district IDs and boundaries.
- [ ] Freeze `RiskScoreV1`.
- [ ] Freeze `IncidentReportV1`.
- [ ] Freeze route request/response contracts.
- [ ] Freeze `DistrictStatusV1`.
- [ ] Mark every fixture as mock, heuristic, official or field data.
- [ ] Assign API scaffolding and database migrations.
- [ ] Schedule the mandatory integration checkpoint.
- [ ] Assign final integration-test ownership.
- [ ] Assign demo-script ownership.

---

## 22. Final decision summary

The team should keep Dharmanshu's scorer, dashboard merge concept, mock fixtures and staged-integration idea, but adapt them to the existing StockFlow architecture and the contracts above.

Do not:

- Rewrite the frontend in React.
- Replace the core API with one Python file.
- Share one SQLite file between clients and server.
- Present heuristic/mock output as verified AI prediction.
- Resume parallel implementation before contract freeze.

Do:

- Use the existing Angular and Spring Boot application.
- Isolate Python scoring behind a small internal FastAPI service.
- Use PostgreSQL as server truth and IndexedDB/device SQLite for offline queues.
- Exchange only versioned, validated JSON contracts.
- Integrate early, rehearse failure modes and clearly disclose prototype limitations.
