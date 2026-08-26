# Pari Assignment — Risk-Aware Routing and Sustainability

## 1. Mission

Build the decision logic that compares real road-aligned route candidates, intersects them with current accessibility evidence, excludes impassable segments, applies explainable risk penalties and returns safer route alternatives with ETA, distance, cost and emissions trade-offs.

## 2. Scope

You own:

- Route-candidate normalization and comparison.
- Spatial intersection between route segments and current risks/incidents/status.
- Exclusion of closed or isolated segments.
- Configurable risk penalties.
- Objective-weighted ranking.
- Alternate route selection.
- Transport cost and sustainability/green-score calculations.
- Explainable route evidence and assumptions.
- Route algorithm and contract tests.

You do not own:

- Hazard input cleanup or scorer formula.
- District-status dashboard rendering.
- Browser incident form or offline synchronization.
- Fleetbase vehicle registry or GPS ingestion.
- Google API credentials in the browser.
- PostgreSQL migrations or public authentication.
- A new standalone frontend or duplicate routing product.

## 3. Existing components to extend

Prefer extending:

```text
services/optimisation-service/
services/carbon-service/
services/stockflow-core-api/
apps/stockflow-web/   # only minimal integration UI if assigned
```

Do not create another route service until the integration owner confirms that the existing optimization service cannot support the feature.

The Spring Boot core API remains the public orchestration boundary. Google Routes and backend keys remain behind approved backend integrations.

## 4. Required handoffs before coding

Obtain:

1. Frozen `RiskScoreV1` schema.
2. Frozen `DistrictStatusV1` schema.
3. Frozen route request and response schemas.
4. Canonical district and road-segment registry.
5. Current incident/status API from Spring Boot.
6. Google Routes candidate representation or normalized route geometry.
7. Vehicle capacity/fuel/emissions data rules.
8. Agreed penalty configuration and default objective weights.

The current route contracts are candidates. Keep external payload parsing in one adapter until formal schemas are approved.

## 5. Input requirements

The route request should ultimately contain or reference:

- `schema_version`
- `tenant_id`
- Stable `request_id`
- Origin and destination coordinates/IDs
- Vehicle and capacity constraints
- Departure time
- Required arrival or service constraint, if any
- Objective weights for time, risk, cost and emissions
- Avoid/exclude rules
- Contract versions or evidence snapshot time

The route engine also needs:

- At least two road-aligned candidate routes where available.
- Segment-level current risk scores.
- Open/verified incident reports.
- District accessibility status.
- Manual closures/overrides.
- Data validity and freshness.
- Vehicle/fuel/load assumptions used for cost and emissions.

Reject or degrade safely if identifiers, geometry, timestamps or units are invalid. Missing hazard data means `NO_DATA`, not safe.

## 6. Processing pipeline

Implement the routing logic as explicit stages:

```text
request validation
→ candidate acquisition
→ geometry normalization
→ evidence snapshot/filtering
→ spatial intersection
→ hard exclusion
→ risk/cost/time/emissions calculation
→ weighted ranking
→ explanation generation
→ contract serialization
```

Keep these stages testable independently.

### Step 1 — Validate the request

- Validate contract version and required IDs.
- Validate coordinate ranges and `[longitude, latitude]` ordering.
- Validate objective weights and decide whether they must sum to `1`.
- Validate vehicle capacity against requested load.
- Validate departure/arrival timestamps.
- Reject unsupported routing modes clearly.

### Step 2 — Obtain road-aligned candidates

- Use the approved backend Google Routes integration or its normalized output.
- Request alternatives where supported.
- Never rank a straight line as an operational road route.
- Preserve provider route IDs/tokens when allowed.
- Decode/normalize polylines into a documented internal geometry format.
- Record provider, request time and traffic assumptions.

If only one provider route is returned, do not fabricate a second route and call it real. Return one candidate plus a limitation, or use an approved waypoint strategy clearly labelled as generated.

### Step 3 — Build a current evidence snapshot

Filter evidence at the route calculation time:

- Exclude expired risks.
- Exclude cleared/rejected incidents.
- Respect verification state.
- Preserve official, field, heuristic and mock source labels.
- Record the exact risk IDs, report IDs and status timestamp used.
- Treat missing coverage as uncertainty, not zero risk.

### Step 4 — Intersect routes and hazards

- Use proper spatial intersection/buffering, not string matching by district name.
- Split or reference the route by known road segments when possible.
- Define buffer width and justify it.
- Avoid double-counting the same risk across overlapping geometry fragments.
- Compute exposure distance/time, not only a boolean intersection.
- Keep district-level caution separate from segment-level closure.

Suggested evidence per affected segment:

```json
{
  "segment_id": "seg_ekh_001",
  "accessibility": "RESTRICTED",
  "exposure_distance_km": 4.8,
  "intersecting_risks": [
    {
      "risk_id": "seg_ekh_001:landslide:2026-08-26T06:00:00Z",
      "hazard_type": "LANDSLIDE",
      "risk_score": 0.82,
      "risk_level": "HIGH"
    }
  ]
}
```

Use the final frozen route schema, not this example, for implementation.

### Step 5 — Apply hard exclusions

A route candidate must be marked infeasible when it intersects a currently closed/isolated segment under the approved rule set.

- Do not merely add a small penalty to an impassable segment.
- Return the exact exclusion reason and evidence IDs.
- Preserve excluded candidates for audit/explanation if the contract supports them.
- If all candidates are excluded, return a controlled `NO_FEASIBLE_ROUTE` result rather than selecting the least impossible route.

### Step 6 — Calculate risk penalty

Use a documented, configurable formula. A possible structure is:

```text
route_risk = Σ(exposure_fraction × severity_weight × source_weight × freshness_weight)
```

Requirements:

- Keep weights in versioned configuration, not scattered constants.
- Document thresholds and normalization.
- Ensure official/verified evidence can outweigh weak heuristic evidence where agreed.
- Represent no-data exposure separately from confirmed low risk.
- Cap or normalize scores predictably.
- Provide component values for explanation/testing.

Do not finalize the formula without team review.

### Step 7 — Time, cost and emissions

For every feasible candidate calculate:

- Distance.
- Base and traffic-aware ETA where available.
- Delay/risk adjustment, if agreed.
- Estimated transport cost.
- Estimated fuel/energy consumption.
- Estimated CO2e.
- Capacity/load utilization.
- Sustainability or green score.

Every output must disclose assumptions such as:

- Vehicle type/fuel type.
- Load or utilization.
- Fuel economy or energy rate.
- Emission factor and source/version.
- Currency and price date.
- Whether traffic is live, typical or unavailable.

Reuse `services/carbon-service` for carbon logic. Do not create a second conflicting formula in the route service.

### Step 8 — Rank candidates

Rank only feasible candidates using approved normalized objectives:

```text
total_score =
  time_weight × normalized_time
  + risk_weight × normalized_risk
  + cost_weight × normalized_cost
  + emissions_weight × normalized_emissions
```

- Document whether lower or higher scores are better.
- Normalize consistently when candidates have identical values.
- Make tie-breaking deterministic.
- Return all candidates and identify the recommended one.
- Show why a longer route won when it is safer.
- Never hide the trade-offs that caused selection.

## 7. Output requirements

Each route option should expose, according to the frozen schema:

- Route/candidate ID.
- Rank and recommendation flag.
- Road-aligned geometry.
- Distance and ETA.
- Feasibility/accessibility status.
- Risk score and no-data exposure.
- Intersecting risk/report IDs.
- Excluded/penalized segments and reason codes.
- Cost and currency.
- Emissions and green score.
- Vehicle/load assumptions.
- Provider and evidence timestamps.
- Explanation/trade-off summary.

The response must include all compared alternatives, not only the winner.

## 8. Service boundary

The intended flow is:

```text
Angular
  → Spring Boot core API
      → Google Routes/backend route provider
      → current risks/incidents/status
      → optimisation-service
      → carbon-service
  ← versioned route response
```

Do not expose internal optimization/carbon endpoints directly to the public browser unless the architecture is explicitly changed and secured.

## 9. Tests

Minimum algorithm tests:

- Two normal routes rank deterministically.
- Closed segment is excluded.
- Isolated district/segment is handled by agreed policy.
- High-risk segment receives a larger penalty than low risk.
- Verified incident changes ranking.
- Cleared/rejected/expired evidence does not change ranking.
- Safer route can win despite longer ETA.
- Objective weights change ranking predictably.
- Identical objective values do not divide by zero.
- Missing hazard coverage increases uncertainty rather than appearing safe.
- Duplicate/overlapping risk geometry is not double-counted.
- All routes blocked returns `NO_FEASIBLE_ROUTE`.
- Invalid geometry/coordinates/contracts are rejected.
- Route response includes geometry and evidence IDs.
- Cost/emissions assumptions are included.
- Capacity constraint can reject a vehicle/route combination.

Integration tests:

- Spring sends a frozen request and accepts the response.
- Two Google road candidates are normalized correctly.
- Route output renders on the Angular map.
- Carbon service outage produces a controlled degraded response rather than false zero emissions.
- Hazard scorer/status outage is shown as unavailable/no-data rather than safe.

## 10. Demo acceptance scenario

For a Guwahati-to-Shillong essential-medicine movement:

1. Load at least two road-aligned candidates.
2. Show the normal fastest option.
3. Add a current verified landslide incident/risk to one segment.
4. Recalculate.
5. Show the affected route penalized or excluded.
6. Recommend the safer alternative.
7. Display ETA, distance, cost and CO2e differences.
8. Show exact risk/report IDs and data age.
9. Ensure the assistant can explain the same decision using returned evidence.

## 11. Documentation and handoff

Provide:

- Branch and commit hash.
- Contract versions consumed/produced.
- Formula and configuration version.
- Route-provider assumptions.
- Emission factor sources and limitations.
- Valid/invalid fixtures.
- Unit and integration test output.
- Example showing why route ranking changed.
- Known fallback behavior when Google, risk or carbon services are unavailable.

## 12. Definition of done

- At least two real road-aligned candidates are compared when available.
- Closed segments are excluded, not lightly penalized.
- Current risk evidence changes ranking predictably.
- No-data is not treated as confirmed low risk.
- Every alternative includes geometry, ETA, distance and evidence.
- Cost/emissions/green-score assumptions are explicit.
- Response matches the frozen route contract.
- Spring integration and Angular rendering work end to end.
- Tests cover boundary, outage and no-feasible-route cases.
- Existing optimization and carbon services are reused without introducing conflicting implementations.
