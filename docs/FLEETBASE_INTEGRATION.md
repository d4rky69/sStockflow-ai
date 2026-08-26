# Fleetbase Integration

StockFlow is integrating Fleetbase in gated phases. StockFlow remains the system of record for inventory, recommendations, approval, FEFO reservation and receipt. Fleetbase will become the system of execution for vehicles, drivers, transport orders and live tracking only after each API stage is verified.

## Completed phases

### Phase 1: read-only backend connectivity

The current implementation provides:

- server-side Fleetbase configuration only;
- Bearer authentication over HTTPS;
- a secret-safe integration status endpoint;
- read-only vehicle listing;
- bounded connection/read timeouts;
- sanitized upstream errors that never return the API key or Fleetbase response body;
- no database migration and no changes to StockFlow transfer state.

Write operations are intentionally disabled.

### Phase 2: StockFlow vehicle workspace

The Angular application now provides a native **Operations → Vehicle Fleet** workspace with:

- Fleetbase connection and credential-mode status;
- read-only vehicle synchronization through the StockFlow backend;
- total, online and available vehicle metrics;
- search and operational-status filters;
- normalized name, plate, internal ID, make, model, year and payload fields;
- a vehicle detail panel and explicit read-only/write-disabled messaging;
- tenant-aware refresh behavior and sanitized API errors.

Local Angular development proxies `/api` to `http://127.0.0.1:8080`. Restart both the Core API and Angular dev server after changing integration configuration or frontend code.

### Phase 3: tenant-to-organization mapping

Fleetbase API credentials belong to a single Fleetbase organization. StockFlow now binds that credential to one explicitly configured StockFlow tenant and rejects requests from every other tenant before contacting Fleetbase.

The optional organization ID enables strict upstream verification through Fleetbase's current-organization endpoint. When configured, vehicle reads are blocked if the credential resolves to a different organization.

### Phase 4: durable transfer-to-order linkage

Approved StockFlow transfer executions can now prepare a durable Fleetbase order link. Preparation stores the execution, proposal, pinned organization, optional vehicle, generated Fleetbase `internal_id`, idempotency key and immutable request fingerprint in migration `V020`.

This stage performs **no Fleetbase write**. A prepared link has status `PREPARED`, zero attempts, no Fleetbase order ID and `remoteWritePerformed: false`. Repeating preparation returns the same record, including when a browser retry supplies a replacement idempotency key. Cancelling the StockFlow execution also cancels a prepared link.

### Phase 5: human-approved undispatched order creation

An approved StockFlow transfer execution with a prepared link can now create its Fleetbase order through an explicit operator action. StockFlow sends the source and destination warehouse names, addresses and coordinates, the optional Fleetbase vehicle ID, an immutable StockFlow internal ID and trace metadata.

Every create request forces `dispatch: false`. A successful response persists the Fleetbase order ID and moves the link to `CREATED`; repeated StockFlow requests return that same durable link without a second Fleetbase call. Failed upstream attempts are sanitized and recorded with an attempt count for audit and retry. Fleetbase writes remain disabled unless the backend-only write gate is explicitly enabled.

## Configuration

Create a **test** or restricted key in Fleetbase Console under **Developers → API Keys**. Configure it only on the Core API process:

```env
FLEETBASE_ENABLED=true
FLEETBASE_API_URL=https://api.fleetbase.io/v1
FLEETBASE_API_KEY=<server-side test or restricted key>
FLEETBASE_TENANT_ID=TEN-ACME-PHARMA
FLEETBASE_ORGANIZATION_ID=<Fleetbase organization ID; recommended>
FLEETBASE_WRITE_OPERATIONS_ENABLED=false
FLEETBASE_WEBHOOK_SECRET=<secret copied from the Fleetbase webhook configuration>
FLEETBASE_CONNECT_TIMEOUT_SECONDS=5
FLEETBASE_READ_TIMEOUT_SECONDS=15
```

Never put the key in Angular runtime configuration, Cloudflare Pages variables, browser storage, screenshots, source control or chat messages.

## StockFlow endpoints

```http
GET /api/v1/integrations/fleetbase/status
GET /api/v1/integrations/fleetbase/organization
GET /api/v1/integrations/fleetbase/vehicles?limit=50
GET /api/v1/integrations/fleetbase/audit
POST /api/v1/integrations/fleetbase/webhooks
POST /api/v1/actions/transfer-executions/{executionId}/fleetbase-link
POST /api/v1/actions/transfer-executions/{executionId}/fleetbase-order
GET /api/v1/actions/transfer-executions/{executionId}/fleetbase-link
GET /api/v1/actions/transfer-executions/{executionId}/fleetbase-tracking
POST /api/v1/actions/transfer-executions/{executionId}/fleetbase-reconcile
GET /api/v1/actions/fleetbase-order-links/{linkId}
```

`/status` reports only `enabled`, `configured`, API URL, credential mode, tenant-mapping state and whether write operations are enabled. It never returns the key.

`/organization` resolves the organization associated with the configured Fleetbase credential. It returns only non-secret organization identity fields and whether the ID matches the strict mapping.

`/vehicles` requires `X-Tenant-ID`. In secured deployments, the normal Supabase bearer token and tenant membership checks also apply.

Preparing a link requires `X-Tenant-ID`, the authenticated `TRANSFER_EXECUTE` permission and an `Idempotency-Key` header. It is allowed only while the transfer execution is `PLANNED` or `RESERVED`, and requires `FLEETBASE_ORGANIZATION_ID` to be pinned.

Creating the remote order requires the same permission, tenant mapping and execution states plus a prepared link and `FLEETBASE_WRITE_OPERATIONS_ENABLED=true`. It never dispatches the order. Keep the write gate false until organization and vehicle reads have been verified and the operator is ready to create a real Fleetbase record.

## Verification sequence

1. Keep `FLEETBASE_ENABLED=false`; confirm `/status` reports disabled and unconfigured.
2. Configure a Fleetbase test/restricted key on the backend only.
3. Set `FLEETBASE_TENANT_ID` to the StockFlow tenant allowed to use the credential.
4. Set `FLEETBASE_ENABLED=true` and restart the Core API.
5. Call `/organization`, copy its `id` into `FLEETBASE_ORGANIZATION_ID`, and restart the Core API again.
6. Confirm `/status` reports `tenantMapping.mapped: true` and `organizationVerificationEnabled: true`.
7. Call `/organization` and confirm `matchesExpectedOrganization: true`.
8. Call `/vehicles` and confirm only the mapped Fleetbase organization's vehicles are returned.
9. Repeat `/vehicles` with a different `X-Tenant-ID`; confirm StockFlow returns `FLEETBASE_TENANT_NOT_MAPPED` without calling Fleetbase.
10. Keep `FLEETBASE_WRITE_OPERATIONS_ENABLED=false` until these checks are reliable.
11. Enable the write gate, restart the API, create an approved transfer execution and prepare its link.
12. Use the explicit **Create Fleetbase order** action and confirm Fleetbase shows the order as created and not dispatched.
13. Reserve FEFO inventory, dispatch the linked transfer and confirm both StockFlow `IN_TRANSIT` and Fleetbase `dispatched` state.
14. Configure the public HTTPS webhook URL as `/api/v1/integrations/fleetbase/webhooks`, set the same secret on the backend and subscribe to order lifecycle events.
15. Open **Vehicle Fleet** to confirm the control centre reports signed webhook readiness and no reconciliation exceptions.
16. Open a dispatched transfer and use **Refresh tracking** or **Reconcile** to see live progress, coordinates and ETA.

Official references:

- [Fleetbase API authentication](https://fleetbase.io/docs/api)
- [Fleetbase API key management](https://fleetbase.io/docs/platform/developer-console/api-keys)
- [Fleetbase vehicle API](https://fleetbase.io/docs/api/fleetbase/vehicles)
- [Fleetbase order API](https://fleetbase.io/docs/api/fleetbase/orders)
- [Fleetbase webhook configuration](https://fleetbase.io/docs/platform/developer-console/webhooks)

### Phase 6 - FEFO-gated dispatch (completed locally)

- The existing StockFlow dispatch action now checks for a linked Fleetbase order.
- Linked orders can dispatch only when the transfer is `RESERVED`, its FEFO allocations cover the complete transfer quantity and every source batch still holds the matching reservation.
- StockFlow calls Fleetbase's explicit `PATCH /orders/{id}/dispatch` transition and requires a confirmed `dispatched=true` response.
- A successful response marks the durable link `DISPATCHED`, then consumes the reserved source inventory and advances the transfer to `IN_TRANSIT`.
- Retrying an already-confirmed link is idempotent and does not send a second Fleetbase dispatch request.
- Transfers without a Fleetbase link keep the original StockFlow-only execution path.

### Phase 7 - tracking, ETA and webhook reconciliation (completed locally)

- Dispatched transfer details expose Fleetbase tracking position, route progress, current destination and completion ETA.
- Manual refresh uses Fleetbase's order, tracker and ETA read endpoints through the secured backend.
- Incoming webhook payloads require an HMAC-SHA256 `X-Fleetbase-Signature`; the secret remains backend-only.
- Webhook event IDs are persisted idempotently so Fleetbase retries cannot duplicate processing.
- Remote lifecycle events update the remote-status snapshot without silently consuming or receiving StockFlow inventory.
- Local and remote states are classified as `MATCHED`, `REMOTE_AHEAD`, `LOCAL_AHEAD` or `REVIEW_REQUIRED`.

### Phase 8 - recovery, audit and rollout controls (completed locally)

- Failed order creation remains retryable from the transfer workflow using the durable StockFlow identity.
- Already-created and already-dispatched operations are idempotent and return the existing link.
- Manual reconciliation refreshes remote order, tracker and ETA state and records the result.
- The **Vehicle Fleet** page now contains a production-readiness control centre showing links, dispatches, signed webhook events, failures and reconciliation issues.
- Rollout reports `INTEGRATION_DISABLED`, `WEBHOOK_SETUP_REQUIRED`, `ATTENTION_REQUIRED` or `READY` from live configuration and audit data.
- Database migration `V021` retains tracking, webhook and reconciliation evidence for operational review.

All eight Fleetbase integration phases are implemented. A production deployment still needs its real HTTPS webhook URL, secret rotation policy, monitoring destination and operator runbook configured for the target environment.
