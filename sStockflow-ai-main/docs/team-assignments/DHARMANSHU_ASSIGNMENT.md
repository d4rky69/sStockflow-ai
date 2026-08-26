# Dharmanshu Assignment — Hazard Scoring and District Merge

## 1. Mission

Own the deterministic hazard-scoring logic, expose it through a small internal FastAPI service, and define/test the district-status merge rules. Your output must be safe under missing data, traceable to its inputs and compatible with the frozen StockFlow contracts.

Your scorer is a **heuristic prototype**, not a trained AI/ML prediction model. Keep that statement in the service documentation and demo notes.

## 2. Scope

You own:

- Rainfall/slope/soil validation and normalization.
- Separate flood and landslide heuristic scoring.
- Mapping numeric scores to agreed risk levels.
- Confidence calculation with documented assumptions.
- `RiskScoreV1` serialization after the contract is frozen.
- Batch-scoring behavior.
- Internal FastAPI endpoints, OpenAPI documentation and health checks.
- Pure district merge rules and their unit tests.
- Missing, invalid, stale and expired data behavior.
- Test fixtures and limitations documentation.

You do not own:

- Angular screens or map rendering.
- Browser offline storage or synchronization UI.
- Spring Boot orchestration, authentication or public endpoints.
- PostgreSQL persistence or migrations.
- Route ranking or emissions calculations.
- Photograph upload or field-report capture.
- Claims that the heuristic is trained AI.

## 3. Required handoffs before coding

Obtain these from the integration owner:

1. Frozen `RiskScoreV1` JSON Schema.
2. Frozen batch request and response schemas.
3. Canonical `district-registry.json` and segment identifiers.
4. Allowed enum values and threshold table.
5. Agreed units and allowed ranges for every scorer input.
6. Decision on whether district merge executes in Python or Spring Boot.

The current document contains candidate shapes only. Until schemas are frozen, keep all payload mapping inside a dedicated contract adapter.

## 4. Target location and structure

Use a small internal service under:

```text
services/hazard-scoring-service/
├── pyproject.toml
├── README.md
├── src/
│   └── stockflow_hazard/
│       ├── __init__.py
│       ├── main.py              # FastAPI app and endpoint wiring
│       ├── config.py            # environment-based configuration
│       ├── contracts.py         # Pydantic request/response models
│       ├── validation.py        # unit/range validation
│       ├── scorer.py            # pure flood/landslide scoring
│       ├── risk_levels.py       # threshold mapping
│       ├── district_merge.py    # pure merge rules
│       └── errors.py            # controlled error mapping
└── tests/
    ├── fixtures/
    ├── test_contracts.py
    ├── test_scorer.py
    ├── test_batch_api.py
    ├── test_district_merge.py
    └── test_health.py
```

If a compatible hazard service already exists when work begins, extend it instead of creating a duplicate.

## 5. Contract requirements

The proposed `RiskScoreV1` contains:

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
    "coordinates": [[91.8201, 25.5301], [91.8514, 25.5632]]
  },
  "inputs": {
    "rainfall_mm_hr": 62.0,
    "slope_degrees": 38.0,
    "soil_type": "clay"
  },
  "evidence": [{
    "type": "DATASET",
    "label": "Prototype NER terrain scenario",
    "reference": "mock://ner-terrain-v1"
  }],
  "model": {
    "name": "ner-rule-scorer",
    "version": "1.0.0",
    "method": "HEURISTIC_WEIGHTED_SCORE"
  }
}
```

Do not treat this example as final until its formal schema is approved.

### Required invariants

- `risk_score` and `confidence` are numeric values from `0` through `1`.
- `risk_level` is derived from a single documented threshold table.
- `source_type` is `HEURISTIC` for calculated output.
- `valid_until` is later than `valid_from`.
- Geometry uses valid GeoJSON and `[longitude, latitude]` coordinates.
- IDs match the canonical district and segment registries.
- Repeating the same request produces the same score and risk level.
- Every output identifies scorer name, version and method.
- Evidence identifies whether the input is mock, official or another approved source.

## 6. Scoring implementation

### Step 1 — Preserve and isolate the existing formula

- Move calculation code into pure functions with no HTTP, file or database access.
- Do not rewrite working mathematics merely for style.
- Document every weight, threshold and unit.
- Do not silently clamp invalid values unless the contract explicitly requires clamping.
- Keep flood and landslide scoring separate, even if they share helper functions.

Suggested interfaces:

```python
def score_flood(inputs: FloodInputs) -> ScoreResult: ...
def score_landslide(inputs: LandslideInputs) -> ScoreResult: ...
def calculate_risk_score(request: ScoreItemRequest) -> RiskScoreV1: ...
def batch_score(request: RiskScoreBatchRequestV1) -> RiskScoreBatchResponseV1: ...
```

### Step 2 — Validate before calculating

Validate:

- Required values are present.
- Values are real finite numbers, not `NaN` or infinity.
- Units match the frozen contract.
- Rainfall and slope fall within approved physical ranges.
- Soil type is from the agreed vocabulary.
- Hazard type is supported by the relevant formula.
- District and segment IDs exist.
- Geometry is valid and belongs to the identified segment.
- Timestamps are valid and ordered.

### Step 3 — Fail safely

Missing or unusable data must never become a low score.

Once the contract team decides the exact representation, use one of these consistently:

- Return a valid `RiskScoreV1` with `risk_level: "NO_DATA"`, explicit missing-input evidence and contract-approved nullable score fields; or
- Reject structurally invalid requests with the standard validation error envelope.

Do not invent nullability locally. The JSON Schema must decide it first.

### Step 4 — Confidence

Confidence must not be a random or decorative number. Document how it is calculated from factors such as:

- Input completeness.
- Data age.
- Source reliability.
- Spatial resolution.
- Whether the input is mock or observed.

If no defensible confidence formula is agreed, use a documented fixed prototype confidence per source class and disclose that limitation.

### Step 5 — Validity and expiry

- Use UTC timestamps.
- Set validity using an agreed rule based on data age/update frequency.
- Never allow an expired result to affect the current district status.
- Include tests at one instant before, exactly at and after expiry.

## 7. FastAPI service

Required internal endpoints:

```text
GET  /health
GET  /contracts
POST /v1/risk-scores/batch
```

### `GET /health`

Return service state without leaking secrets:

```json
{
  "status": "UP",
  "service": "hazard-scoring-service",
  "version": "1.0.0",
  "contract_versions": ["RiskScoreV1:1.0"]
}
```

### `GET /contracts`

Return supported contract names/versions or links to repository schemas. It must not claim support for an unapproved schema.

### `POST /v1/risk-scores/batch`

- Accept only the frozen batch request.
- Enforce a maximum batch size.
- Preserve item correlation IDs and input ordering if the contract requires it.
- Define whether one invalid item rejects the batch or returns item-level errors.
- Return controlled `400`, `413`, `422` and `500/503` responses.
- Never include Python traces, environment values or secrets in responses.

The service is internal. Angular must call Spring Boot, and Spring Boot calls this service.

## 8. District merge rules

Implement merge behavior as a pure function, independent of `dashboard.py` rendering:

```python
def calculate_district_status(
    district,
    current_risks,
    current_incidents,
    manual_overrides,
    calculated_at,
) -> DistrictStatusV1:
    ...
```

Required rules:

1. Verified open `EXTREME` incident may produce `ISOLATED`.
2. Verified open `HIGH` incident produces at least `RESTRICTED`.
3. Unverified high incident produces at least `CAUTION` unless corroborated.
4. Current high/extreme risk may produce `RESTRICTED` using agreed thresholds.
5. Expired risks do not participate.
6. Cleared/rejected incidents do not participate.
7. No current usable inputs produces `NO_DATA`, never `OPEN`.
8. Manual override requires actor, reason and expiry.
9. Output lists reason codes, active report IDs and worst affected segments.
10. Output records source counts and data freshness.

The integration owner must decide whether this function is hosted by FastAPI or implemented in Spring using the same approved rule table. Do not expose a new district endpoint without that decision and a frozen request/response schema.

## 9. Tests

Minimum scorer tests:

- Zero, normal, boundary and maximum allowed inputs.
- Just below, at and just above every risk threshold.
- Flood and landslide generate separate results.
- Missing rainfall, slope and soil individually.
- Multiple missing inputs.
- Negative/out-of-range/`NaN`/infinite values.
- Invalid soil and hazard enum.
- Invalid district/segment ID.
- Reversed coordinates and invalid geometry.
- Invalid or reversed validity timestamps.
- Deterministic output for identical input.
- Expired output is excluded from a current merge.
- Batch-size and mixed-validity behavior.

Minimum district tests:

- Verified high incident overrides low heuristic risk.
- Cleared/rejected incidents are ignored.
- Unverified high incident produces at least caution.
- Extreme verified incident can isolate.
- Expired risks are ignored.
- Manual override expires correctly.
- No current input yields `NO_DATA`.
- Source counts, reason codes and active IDs are correct.

Also run a real HTTP test against the FastAPI process. Importing functions in one Python process is not sufficient end-to-end verification.

## 10. Documentation and handoff

Your README must include:

- Exact startup and test commands.
- Environment variables with placeholder values only.
- Supported contract versions.
- Formula, weights, thresholds and units.
- Confidence and validity rules.
- Example request/response and controlled errors.
- Prototype limitations.
- Spring Boot integration URL and timeout expectations.

Provide the integration owner:

- Branch and commit hash.
- Schema/fixture versions tested.
- FastAPI base URL and OpenAPI URL.
- Known failure modes.
- One valid batch fixture and invalid fixtures.
- Test output.

## 11. Definition of done

- Frozen contracts validate at the API boundary.
- `/health`, `/contracts` and batch scoring work over HTTP.
- Missing/invalid data fails safely.
- Flood and landslide are scored separately.
- Output includes provenance, model version and validity.
- District merge respects verification, status, expiry and no-data rules.
- Unit, contract and HTTP integration tests pass.
- No frontend, PostgreSQL, SQLite or authentication replacement is introduced.
- No secrets, local databases or generated caches are committed.
