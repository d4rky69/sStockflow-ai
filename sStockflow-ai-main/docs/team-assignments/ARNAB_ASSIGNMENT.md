# Arnab Assignment — Field Reporting and Offline Synchronization

## 1. Mission

Build the field-worker reporting experience inside the existing Angular application. A user must be able to capture an incident with GPS accuracy and photographs, continue while offline, survive a browser refresh, synchronize safely later and visibly resolve conflicts.

## 2. Scope

You own:

- Angular incident-report form and validation UX.
- Browser geolocation capture, accuracy and timestamp display.
- Photograph selection/camera input, preview, compression and upload queue.
- IndexedDB storage for unsynchronized reports and photographs.
- Connectivity detection, retry scheduling and queue status.
- Calling documented Spring Boot incident, media and sync endpoints.
- Idempotency keys and client-generated report IDs.
- Conflict, validation, failed-upload and retry UI.
- Responsive, field-readable and accessible interaction design.
- Unit/component tests for the client workflow.

You do not own:

- PostgreSQL tables or authoritative revisions.
- Server authorization, tenant enforcement or conflict decisions.
- Hazard scoring formulas.
- District status merge rules.
- Route ranking or sustainability formulas.
- A replacement React app or separate client backend.
- A browser SQLite file.

## 3. Required handoffs before coding

Obtain from the integration owner:

1. Frozen `IncidentReportV1` schema.
2. Photo upload request/response schema.
3. Offline sync batch request/response schema.
4. Conflict response and sync-receipt schema.
5. Standard error envelope and authentication rules.
6. Allowed district, segment, incident type, severity and status values.
7. Maximum photo count, size, type and compression limits.
8. Spring endpoint paths and idempotency behavior.

These sync/media contracts are not fully frozen in the current plan. Do not invent final field names in multiple components. Keep API mapping centralized.

## 4. Target implementation

Extend the existing Angular app:

```text
apps/stockflow-web/src/app/
├── core/
│   ├── models/
│   │   ├── incident-report.models.ts
│   │   └── incident-sync.models.ts
│   └── services/
│       ├── incident-api.service.ts
│       ├── incident-offline-store.service.ts
│       ├── incident-sync.service.ts
│       ├── photo-compression.service.ts
│       └── connectivity.service.ts
└── features/
    └── operations/
        └── incident-reporting/
            ├── incident-report-form.component.*
            ├── incident-queue.component.*
            ├── incident-conflict-dialog.component.*
            └── incident-reporting.routes.ts
```

Adapt names to the existing feature organization, but do not create a second Angular application or duplicate API layer.

## 5. Incident contract

The final payload must follow the frozen `IncidentReportV1`. It should cover at least:

- `schema_version`
- Client-generated `report_id`
- `tenant_id` supplied/enforced through the authenticated backend context
- Canonical `district_id`
- Optional canonical `segment_id`
- Incident/hazard type
- Severity and incident status
- Description
- GeoJSON point
- GPS `accuracy_metres`
- Device observation timestamp
- Client revision
- Source type
- Photo references after upload
- Client/application metadata approved by the schema

Do not send:

- Local Windows/Android/iOS file paths.
- Base64 photograph bodies inside the incident JSON unless the frozen contract explicitly requires it.
- API keys or authentication secrets.
- A user-editable authoritative tenant ID.
- A locally invented district or road identifier.

## 6. Form workflow

### Step 1 — Create a stable draft

- Generate a UUID when the user starts a report, not after upload.
- Preserve the same `report_id` through offline queueing and retries.
- Start the client revision at the frozen contract's agreed value.
- Persist the draft after meaningful changes so an accidental refresh does not destroy it.

### Step 2 — Capture incident information

Provide fields for:

- Incident/hazard type.
- Severity.
- District from the canonical registry.
- Optional road segment selected from the map or registry.
- Short description and optional structured notes.
- Observation time.
- GPS point and accuracy.
- Photograph attachments.

Use enums supplied by the shared contract. Do not let presentation labels leak into API values.

### Step 3 — Capture GPS safely

Use the browser Geolocation API only after a user action/permission explanation.

- Capture latitude, longitude, accuracy and device timestamp.
- Convert to GeoJSON `[longitude, latitude]` only in the contract adapter.
- Show accuracy to the user before submission.
- Warn when accuracy exceeds the agreed threshold.
- Allow retry and, if approved, manual pin placement with `source` clearly marked.
- Do not silently reuse a stale cached position.
- Record whether the location is device GPS, manual pin or another approved source.
- Explain permission-denied and unavailable-device states.

### Step 4 — Handle photographs

- Accept only approved MIME types.
- Validate count and original size.
- Correct orientation where possible.
- Compress to the agreed maximum dimensions/quality.
- Strip unnecessary metadata where appropriate, while preserving required evidence fields separately.
- Generate a local attachment ID.
- Store compressed data in IndexedDB, not `localStorage`.
- Show previews, size, upload state, retry and remove actions.
- Upload photographs before synchronizing the incident.
- Replace local attachment IDs with returned object-storage references.
- Delete local binary data only after both upload and incident acknowledgement succeed.

## 7. IndexedDB design

Suggested stores:

```text
pending_incidents
pending_photos
sync_receipts
cached_district_status
cached_contract_metadata
```

Suggested queued incident metadata:

```text
report_id
client_revision
payload
sync_status
idempotency_key
attempt_count
last_attempt_at
next_attempt_at
last_error_code
created_at
updated_at
```

Requirements:

- Use an explicit IndexedDB schema version and migration function.
- Write report and attachment metadata atomically where possible.
- Keep binary photographs separate from searchable report metadata.
- Survive refresh, browser restart and temporary loss of service.
- Never mark a record `SYNCED` before receiving a server acknowledgement/receipt.
- Keep failed validation records until the user corrects or deletes them.
- Avoid storing Supabase access tokens in queue records.

## 8. Synchronization state machine

Use visible states such as:

```text
LOCAL_ONLY → QUEUED → UPLOADING_PHOTOS → SUBMITTING → SYNCED
                                            ↘ CONFLICT
                                            ↘ FAILED
```

Behavior:

1. Validate locally against the frozen schema.
2. Queue even when offline.
3. Upload pending photographs first.
4. Store returned object references.
5. Submit the incident/revision with a stable `Idempotency-Key`.
6. Save the server revision and sync receipt.
7. Treat a repeated successful idempotency key as success, not a duplicate.
8. Retry transient network/`5xx`/`429` failures using bounded exponential backoff.
9. Do not automatically retry schema/validation failures until the payload changes.
10. Pause and show authentication recovery for `401`/`403`.
11. Preserve queued work when the scorer or another unrelated service is down.

Do not rely only on `navigator.onLine`; an actual request failure is authoritative.

## 9. Conflict handling

The server owns authoritative revision ordering. Device time alone never wins a conflict.

When Spring returns `409 CONFLICT`:

- Keep the local record and server record.
- Display both revisions and changed fields.
- Explain why automatic synchronization stopped.
- Allow the user to accept the server copy or prepare a new permitted revision.
- Do not allow unauthorized transitions to `VERIFIED`, `CLEARED` or `REJECTED`.
- Record the resolution as a new action rather than silently overwriting history.
- Maintain the original `report_id` according to the server contract.

## 10. User experience requirements

- Show whether the device is online, offline or reconnecting.
- Show queued, failed and conflicting report counts.
- Provide an explicit **Save offline** or equivalent clear action.
- Use large touch targets and readable contrast.
- Do not use color alone for severity or synchronization state.
- Show timestamps and data age.
- Clearly label simulated/demo incidents.
- Prevent accidental double submission while still allowing safe idempotent retry.
- Provide a short permission explanation before GPS/camera prompts.
- Preserve form data when a modal closes accidentally or navigation is interrupted.

## 11. Spring API boundary

Angular calls only documented Spring Boot endpoints. A likely shape is:

```text
POST /api/v1/ner/photos
POST /api/v1/ner/incidents/sync
GET  /api/v1/ner/incidents/{report_id}
GET  /api/v1/ner/sync/receipts/{idempotency_key}
```

These paths are examples, not final. The integration owner must freeze them and their payloads.

Every request uses existing authentication and tenant conventions. Do not add direct browser access to PostgreSQL, object storage admin APIs or internal Python services.

## 12. Testing

Minimum unit/component tests:

- Required-field and enum validation.
- GeoJSON longitude/latitude conversion.
- GPS success, denial, timeout and poor-accuracy states.
- Photograph type, count, size and compression behavior.
- Form draft survives component reload.
- Queue survives page refresh/browser reopen.
- Failed upload does not mark an incident synced.
- Stable report ID and idempotency key across retry.
- Transient error schedules retry.
- Validation error waits for correction.
- Duplicate acknowledgement does not create a second record.
- Newer server revision opens conflict UI.
- Conflict resolution preserves both versions until user action.
- Cleared/rejected records do not appear as active incidents.
- Logout/login does not leak one tenant's queue to another user.

Manual failure rehearsal:

1. Open the incident form online.
2. Capture GPS and attach a compressed test image.
3. Disconnect the network.
4. Save the report and refresh the page.
5. Confirm the queue still contains it.
6. Restore connectivity.
7. Confirm photos upload before the report.
8. Confirm exactly one server report exists.
9. Retry the same idempotency key.
10. Create a newer server revision and verify visible conflict handling.

Run the Angular verification command:

```cmd
cd apps\stockflow-web
npm run build
```

## 13. Documentation and handoff

Provide:

- Branch and commit hash.
- IndexedDB schema/version and migration notes.
- Exact Spring endpoints and contract versions consumed.
- Screenshot/video of offline persistence and conflict UI.
- Test output.
- Known browser/device limitations.
- Sample incident fixture with no personal information.

## 14. Definition of done

- Form produces a payload matching the frozen incident schema.
- GPS coordinate, accuracy and timestamp are captured and shown.
- Photos are compressed, queued and uploaded by reference.
- No local path is transmitted.
- Offline reports survive refresh/restart.
- Sync uses stable IDs, revisions and idempotency keys.
- Failed uploads do not falsely mark reports synced.
- Conflict UI works with a real `409` response.
- Authentication and tenant isolation are preserved.
- Angular build and relevant tests pass.
- No SQLite browser layer, replacement frontend or exposed secret is introduced.
