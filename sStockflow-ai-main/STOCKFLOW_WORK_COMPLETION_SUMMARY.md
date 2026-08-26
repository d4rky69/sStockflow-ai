# StockFlow AI — Website Work and Completion Summary

## 1. Project overview

StockFlow AI is an intelligent logistics and inventory platform being developed for the SIH problem statement focused on logistics and accessibility intelligence for Northeast India.

The current system connects inventory risks, transfer recommendations, human approval, fleet operations, route planning, GPS tracking, weather awareness and GIS hazard information through one web application.

The main implementation stack is:

- Frontend: Angular
- Core backend: Kotlin with Spring Boot
- Primary database: PostgreSQL
- Authentication and tenant security: Supabase Auth and tenant-aware backend controls
- Fleet operations: Fleetbase
- Maps, routing and weather: Google Maps Platform, Google Routes and Google Weather
- Specialized risk or analytics services: Python where required

## 2. Website work completed

### Application and dashboard

- Built the main StockFlow AI web dashboard in Angular.
- Added responsive layouts, loading states, retry actions, empty states and integration-error messages.
- Added application sections for:
  - Dashboard
  - Demand Forecast
  - Inventory Analytics
  - Risk and Alerts
  - Recommendations
  - Vehicle Fleet
  - Transfers
  - Route Optimization
  - Sustainability
  - Purchase Planning
  - Orders
  - Returns
- Added a compact navigation sidebar that expands on hover.

### Fleetbase integration

- Connected the StockFlow backend to the Fleetbase live API.
- Added tenant-aware Fleetbase organization validation.
- Retrieved real Fleetbase vehicle information through the secured StockFlow backend.
- Added a vehicle registry with search and status filters.
- Added a vehicle detail side panel displaying:
  - Fleetbase ID
  - Internal ID
  - Vehicle name and model
  - Plate number
  - Operational and online status
  - Vehicle type
  - Payload capacity
  - Speed and heading
  - GPS update time
  - Route progress
  - Remaining distance
  - ETA and next checkpoint
- Added Fleetbase order and dispatch integration foundations.
- Protected write operations using approval and configuration controls.
- Added signed Fleetbase webhook handling for order lifecycle events and reconciliation.
- Kept Fleetbase server credentials on the backend instead of exposing them in the browser.

### GPS tracking and maps

- Integrated Google Maps into the vehicle fleet dashboard.
- Added a GIS accessibility map as an overlay within the StockFlow application.
- Added vehicle markers, route lines, checkpoints and map controls.
- Added a **Show live tracking on map** action in the vehicle panel.
- Added an accelerated prototype tracking mode that demonstrates vehicle movement.
- Displayed speed, heading, ETA, remaining distance and corridor progress during tracking.
- Clearly labelled simulated tracking data so that it is not presented as real GPS telemetry.

### Routing and ETA

- Integrated Google Routes for road-based routing between origin and destination.
- Replaced simple straight-line prototype paths with proper road routes.
- Added route distance, estimated travel time, checkpoints and visual route progress.
- Added the foundation for vehicle assignment and route-aware dispatch.

### GIS, weather and hazards

- Added a GIS accessibility dashboard with operational corridors and map layers.
- Integrated Google Weather for arrival forecasts and route-disruption information.
- Added weather information to the selected vehicle panel.
- Integrated Google Public Alerts for official flood and landslide alerts where provider coverage is available.
- Added support for official hazard polygons and markers on the Google map.
- Added active, future, unavailable, error and no-alert states.
- Removed misleading hard-coded hazard zones from the live Google Maps view.
- Added a clear limitation notice when an official alert provider does not cover the selected region.

### Inventory transfers and approval

- Built a transfer recommendation dashboard.
- Displayed recommended products, quantities, source and destination warehouses, routes, service impact, ETA and sustainability estimates.
- Added a transfer proposal form with:
  - Product and SKU
  - Quantity
  - Source and destination warehouse
  - Unit and transport cost
  - Business reason
  - Recommendation evidence
- Added backend-persisted proposal records.
- Added a human-gated workflow separating proposal creation from approval and execution.
- Added the foundation for auditable approval history.

### Security and reliability

- Kept Google backend keys and Fleetbase credentials outside the frontend bundle.
- Added tenant-aware backend requests and integration checks.
- Added configuration status endpoints and protected integration writes.
- Added backend and frontend tests for relevant integration components.
- Added explicit error handling for authorization, CORS, unavailable APIs and invalid configuration.

## 3. Demonstrated workflow

The current prototype demonstrates the following end-to-end process:

1. Inventory or accessibility risk is detected.
2. StockFlow recommends an inventory transfer.
3. A user reviews the evidence, route, quantity and expected impact.
4. A draft proposal is created.
5. A separate authorized user approves the proposal.
6. A Fleetbase vehicle can be selected for dispatch.
7. Google Routes provides the road route and ETA.
8. The vehicle is displayed on the StockFlow GIS map.
9. Weather and official hazard information are shown alongside the route.
10. Webhooks and reconciliation provide the foundation for order-status synchronization.

## 4. Feature completion assessment

The percentages below estimate functional completion. They consider backend behavior, frontend visibility, data quality, integration testing and production readiness—not only whether a screen exists.

| Feature | Estimated completion | Completed | Remaining work |
|---|---:|---|---|
| Web dashboard and UI | 90% | Angular dashboard, responsive screens, navigation and UI states | Final visual cleanup and device testing |
| Fleetbase integration | 85% | Live organization and vehicles, secure backend access, dispatch foundation and webhooks | Full real dispatch and long-running webhook verification |
| Vehicle and GPS tracking | 70% | Map, vehicle panel, ETA, distance, speed, heading and prototype movement | Real GPS or telematics feed and tracking-history storage |
| Route optimization | 65% | Google road route, ETA, distance and checkpoints | Alternative-route comparison and genuine risk-weighted rerouting |
| GIS accessibility dashboard | 75% | Vehicles, routes, checkpoints and hazard layers | Real road closure, bridge and district accessibility datasets |
| Weather intelligence | 70% | Weather-backed arrival and disruption display | Route-wide forecasts, scheduled refresh and calibrated risk logic |
| Flood and landslide alerts | 45% | Official Public Alerts integration and hazard rendering | Reliable India/NER providers and predictive hazard models |
| Inventory analytics | 70% | Analytics screens, inventory context and recommendations | Complete real datasets and final contracts |
| Demand forecasting | 55% | Forecasting UI and prototype analytics | Historical data, validated model and accuracy evaluation |
| Risk scoring | 50% | Heuristic or mock scores and dashboard display | Frozen contracts, real data and calibrated scoring model |
| Transfer recommendations | 80% | Recommendations, route, quantity, cost, evidence and CO2 estimates | Replace remaining demonstration values with live calculations |
| Human approval workflow | 85% | Proposal persistence, review and approval separation | Complete rejection flow, authorization and audit testing |
| Alerts and notifications | 50% | In-app risk, weather and integration states | Push, SMS or email delivery and acknowledgement tracking |
| Field incident reporting | 30% | Contract and integration design | GPS/photo form, upload, validation and review workflow |
| Offline operation and sync | 25% | Architecture and prototype planning | Real client queue, retry, conflict resolution and recovery testing |
| Mobile application | 20% | Responsive web foundation and documented requirements | PWA/native field functions, camera, GPS and offline validation |
| Security and tenancy | 80% | Backend-held keys, tenant controls and protected writes | Complete role matrix, security testing and key rotation |
| Sustainability metrics | 60% | CO2 savings, route efficiency and utilization indicators | Verified formulas using live route and vehicle data |
| Chatbot and multilingual access | 20% | Responsibility and integration approach identified | Working chatbot, backend connection and language testing |
| Shared JSON contracts | 65% | Main risk, incident, district, route and status candidates | Sync, media, pagination, alert, registry and batch contracts |

## 5. Overall status

- Website and demonstration readiness: approximately **70%**
- Production-grade system readiness: approximately **50–55%**
- Major problem-statement areas represented in the application: **all**, although several remain partial or prototype-based

### Strongest areas

- Angular web dashboard and user experience
- Fleetbase organization and vehicle integration
- Vehicle details and map presentation
- Transfer recommendation and human approval workflow
- Google road routing and ETA
- Backend credential protection and tenant-aware integration

### Largest remaining gaps

- Real NER-specific formatted datasets
- Real GPS or telematics device feed
- Field incident reporting with GPS and photographs
- Complete offline synchronization and conflict resolution
- Reliable India/NER flood and landslide data sources
- Validated AI/ML demand, risk and route models
- Multilingual chatbot implementation
- Final versioned JSON contracts for every team integration

## 6. Important prototype limitations

- The animated vehicle route is a clearly labelled prototype until a real GPS device or Fleetbase Navigator feed is connected.
- A lack of Google Public Alerts does not prove that no hazard exists. It may mean that no supported official provider has published an alert for the region.
- Some recommendation, forecasting, sustainability and risk values use demonstration or heuristic inputs until the team supplies the agreed datasets.
- Current risk scoring should not be described as a trained AI model unless it is replaced or supported by a trained and evaluated model.
- Approval currently represents an authorized decision step; complete external execution must be verified separately.

## 7. Recommended next priorities

1. Freeze and version all shared JSON contracts.
2. Connect the final agreed NER datasets without changing the existing stack.
3. Implement field incident reporting with GPS and photograph uploads.
4. Add a real offline queue, retry strategy and revision-based conflict handling.
5. Connect real GPS telemetry or Fleetbase Navigator.
6. Integrate a verified India/NER hazard data provider.
7. Replace static risk and recommendation values with validated calculations.
8. Complete role-based approval, rejection and audit testing.
9. Run an end-to-end demo test covering recommendation through tracking.
10. Prepare screenshots, architecture diagrams, test evidence and a limitation statement for judging.

## 8. Summary statement

StockFlow AI already provides a strong, integrated prototype covering the full logistics decision flow from inventory risk to transfer recommendation, approval, fleet selection, road routing, GPS visualization, ETA, weather awareness and hazard mapping.

The remaining work is primarily about replacing demonstration inputs with reliable real data, completing offline and field-reporting capabilities, connecting real telemetry, validating the intelligence models and finalizing stable integration contracts across the team.
