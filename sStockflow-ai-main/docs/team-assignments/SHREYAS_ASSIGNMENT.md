# Shreyas Assignment — NER Data, GIS Dashboard and Assistant UX

## 1. Mission

Prepare traceable Northeast India prototype datasets, build the Angular GIS/accessibility experience using frozen contracts, and make the existing StockFlow assistant explain current operational evidence clearly and honestly.

## 2. Scope

You own:

- Canonical district registry and approved GIS fixtures.
- Dataset provenance, source labels, timestamps and limitations.
- Angular NER district/accessibility map and supporting status panels.
- Rendering districts, corridors, incidents, risks, routes and vehicles.
- Grey/no-data, stale, offline and unavailable visual states.
- Responsive, accessible, field-readable dashboard UX.
- Assistant prompts/tool-result presentation for NER logistics questions.
- UI and data-fixture tests.

You do not own:

- PostgreSQL migrations or server persistence.
- Offline synchronization algorithm.
- Hazard score formula.
- District merge algorithm.
- Route ranking or carbon calculations.
- Public API authentication or tenant enforcement.
- Unsupported claims that mock data is official/live/predictive.
- A separate React dashboard.

## 3. Required handoffs before coding

Obtain:

1. Frozen district registry schema and approved district IDs.
2. Frozen `RiskScoreV1`, `IncidentReportV1`, `DistrictStatusV1` and route schemas.
3. Spring Boot endpoints for current district status, incidents, risks and routes.
4. Fleetbase vehicle endpoint already exposed by Spring.
5. Source/provenance vocabulary and demo-data labeling rules.
6. Assistant tool/API boundary from the existing copilot service.
7. Agreed map libraries and Google Maps configuration.

Do not bind Angular templates directly to arbitrary teammate JSON. Parse every API response through typed models and one adapter/service.

## 4. Canonical NER data preparation

### District registry

Create one version-controlled registry, for example:

```text
data/ner/district-registry.json
data/ner/boundaries/
data/ner/road-segments.geojson
data/ner/demo-scenarios/
data/ner/README.md
```

Each district entry should contain, according to the frozen schema:

- Canonical lowercase district ID.
- Display name.
- State ID/name.
- Centroid in GeoJSON coordinate order.
- Boundary reference/version.
- Source/provider.
- Source URL or document reference.
- Retrieval/publication date.
- License/use notes.
- Geometry quality/resolution.
- Demo/official classification.

Rules:

- Never invent alternate IDs in Angular.
- Keep display labels separate from stable IDs.
- Normalize spelling variants once in the registry.
- Validate coordinate ranges and GeoJSON structure.
- Simplify very large geometry only with a documented process and retained source reference.
- Do not combine boundaries from incompatible administrative years without disclosure.

### Road and logistics fixtures

For prototype corridors include:

- Stable segment ID.
- District/state association.
- Road/corridor name.
- GeoJSON geometry.
- Segment length or derivation method.
- Essential-supply classification where applicable.
- Source and date.
- Whether the segment is real, simplified or simulated.

### Provenance classification

Every record/layer must be one of:

```text
MOCK | HEURISTIC | OFFICIAL | FIELD_REPORT | MANUAL_OVERRIDE
```

UI labels must make this visible. A mock scenario may look realistic but must never be presented as an official live alert.

## 5. Angular implementation

Extend the existing Angular application:

```text
apps/stockflow-web/src/app/
├── core/
│   ├── models/
│   │   ├── ner-district.models.ts
│   │   ├── ner-risk.models.ts
│   │   ├── ner-incident.models.ts
│   │   └── ner-route.models.ts
│   └── services/
│       ├── ner-accessibility-api.service.ts
│       └── ner-contract-adapter.service.ts
└── features/
    └── operations/
        └── ner-accessibility/
            ├── ner-accessibility-dashboard.component.*
            ├── district-map.component.*
            ├── district-status-panel.component.*
            ├── map-layer-control.component.*
            ├── map-legend.component.*
            └── evidence-panel.component.*
```

Reuse existing dashboard, map, Fleetbase and design-system components before adding new ones.

## 6. GIS dashboard requirements

### Required layers

- District boundaries colored by `DistrictStatusV1`.
- Road/logistics corridors.
- Current Fleetbase vehicle positions.
- Current heuristic risk segments.
- Official hazard polygons/markers when coverage exists.
- Field incident markers.
- Candidate and selected routes.
- Essential-supply checkpoints/corridors when supported.

### Required layer controls

Allow the user to toggle layers without losing current selection. Display:

- Layer name.
- Source type.
- Last updated/data age.
- Availability/error state.

### District status colors

Use the frozen design mapping, with text/icons in addition to color. At minimum:

```text
OPEN        → accessible styling
CAUTION     → warning styling
RESTRICTED  → high-risk styling
ISOLATED    → critical styling
NO_DATA     → grey/hatched unknown styling
```

Never render `NO_DATA` as green/open.

### Selection and evidence panel

Clicking a district, segment, incident, route or vehicle should open a detail panel containing relevant:

- Stable IDs.
- Status/severity/risk.
- Source badge.
- Observation/calculation time.
- Validity/expiry.
- Confidence where available.
- Reason codes.
- Related risk/report IDs.
- Data age and limitations.
- Route impact and next action.

### Map accessibility

- Provide keyboard-accessible controls where the map library allows.
- Ensure popups/dialogs manage focus and have accessible names.
- Do not rely on hover alone for critical details.
- Use high contrast and non-color status indicators.
- Avoid tiny touch targets.
- Provide a list/table alternative for important map records.

## 7. Data states and honesty

Every layer/panel needs explicit states:

- Loading.
- Current data.
- Stale data.
- No records.
- `NO_DATA`/coverage unavailable.
- API error.
- Offline cached data.
- Mock/demo data.

Important distinctions:

- “No active official alert” is not “no hazard.”
- “Provider does not cover this region” is not “safe.”
- Heuristic risk is not an official warning.
- Simulated GPS is not live telemetry.
- A model output is not verified field evidence.

## 8. Dashboard panels

Build panels that help a judge or operator answer:

- Which districts are restricted or isolated?
- Why did a district status change?
- Which evidence is official, field, heuristic or mock?
- How old is the data?
- Which road segments and essential-supply routes are affected?
- Which vehicles/routes are currently associated with the area?
- What is the safer alternative and its trade-off?

Suggested UI elements:

- Summary cards by accessibility status.
- Source and freshness legend.
- District table with search/filter.
- Evidence timeline.
- Selected-route comparison.
- Incident queue/status link supplied by Arnab's feature.
- Assistant launcher scoped to the selected district/route.

## 9. Assistant integration

Extend the existing `services/copilot-service`; do not create a second chatbot backend.

The assistant must answer using current API/tool evidence, not hard-coded prompt facts.

Required behavior:

- Retrieve current district/route/incident data before operational answers.
- Cite district IDs, risk IDs, report IDs or route IDs used.
- Include timestamps and data age.
- Distinguish official, field, heuristic and mock evidence.
- Explain why a route was penalized/excluded/recommended.
- State uncertainty and provider-coverage limitations.
- Refuse to declare a route safe solely because no alert was returned.
- Avoid inventing incident details, bridge closures or predictions.
- Keep answers concise enough for field use.

Suggested questions to support:

```text
Why is East Khasi Hills restricted?
Which route to Shillong is currently recommended and why?
What official alerts affect this route?
Which evidence is simulated?
When was this district status last calculated?
What is the ETA, cost and CO2 difference between the two routes?
```

### Multilingual behavior

If multilingual support is included:

- Keep IDs and enum/API values unchanged.
- Translate presentation text, not contracts.
- Maintain a reviewed glossary for logistics, hazard and accessibility terms.
- Provide an English fallback.
- Test critical warnings with human review where possible.
- Do not claim full language support based only on automatic translation.

## 10. API boundary

Angular calls existing Spring Boot endpoints through typed services. A likely read flow is:

```text
GET /api/v1/ner/districts
GET /api/v1/ner/district-status
GET /api/v1/ner/incidents
GET /api/v1/ner/risks
POST /api/v1/ner/routes/assess
GET /api/v1/integrations/fleetbase/vehicles
```

These are candidate paths. Use only the final documented endpoints.

Do not:

- Read PostgreSQL directly from Angular.
- Call the hazard-scoring FastAPI service from Angular.
- Put Google backend/Fleetbase keys in frontend code.
- Parse multiple inconsistent payload variants throughout components.

## 11. Tests

Dataset/fixture tests:

- District IDs are unique and canonical.
- Display names/states are present.
- Centroids and boundaries have valid coordinates.
- Every record includes source/provenance.
- Road segment district references exist.
- Fixtures validate against frozen schemas.
- Mock records are visibly labelled.

Angular tests:

- All district statuses render with correct text/icon styling.
- `NO_DATA` renders grey/unknown, not green.
- Expired/stale data is labelled.
- Cleared/rejected incidents do not appear active.
- Source badges and timestamps display correctly.
- District registry mismatch fails visibly.
- Layer toggles work independently.
- Selected evidence panel shows IDs and provenance.
- API outage/empty/coverage-unavailable states differ.
- Keyboard and focus behavior works for panel/modal interactions.
- Responsive layout works at agreed desktop/tablet/mobile sizes.

Assistant tests:

- Uses current tool/API result.
- Cites evidence IDs and data age.
- Distinguishes mock from official information.
- Does not call no-alert state safe.
- Explains the same route decision returned by Pari's service.
- Handles missing/stale evidence honestly.

Run:

```cmd
cd apps\stockflow-web
npm run build
```

Run the existing copilot-service test command documented in its README after prompt/tool changes.

## 12. Demo acceptance scenario

Prepare a repeatable GIS story:

1. Open the NER accessibility dashboard.
2. Show the provenance and freshness legend.
3. Select East Khasi Hills.
4. Display a clearly labelled heuristic risk and field incident.
5. Show the district move to restricted after recalculation.
6. Display normal and safer Guwahati–Shillong routes.
7. Select the safer route and show its evidence/trade-offs.
8. Display the Fleetbase vehicle or labelled GPS simulation.
9. Ask the assistant why the route changed.
10. Confirm the answer cites the same IDs shown in the UI.

Keep backup screenshots/video for external API outages.

## 13. Documentation and handoff

Provide:

- Branch and commit hash.
- District registry and geometry versions.
- Source/provenance list and licenses.
- Contract/API versions consumed.
- Layer-to-contract mapping.
- UI screenshots for current, no-data, stale, mock and error states.
- Assistant prompt/tool changes and example grounded responses.
- Angular/copilot test results.
- Known data coverage and translation limitations.

## 14. Definition of done

- One canonical NER district registry is used everywhere.
- Every dataset/layer has provenance and a mock/official/etc. classification.
- GIS map renders districts, corridors, incidents, risks, routes and vehicles from APIs/contracts.
- `NO_DATA` and provider-unavailable states are honest and visible.
- Detail panels expose IDs, source, timestamp and validity.
- UI is responsive, accessible and readable.
- Assistant answers from current evidence and cites IDs/data age.
- Assistant never treats missing alerts as proof of safety.
- Angular and copilot tests pass.
- No duplicate frontend, direct database access or exposed server credential is introduced.
