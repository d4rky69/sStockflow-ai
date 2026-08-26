export type FleetbaseMode = 'TEST' | 'LIVE' | 'UNCONFIGURED' | 'RESTRICTED_OR_CUSTOM';

export interface FleetbaseIntegrationStatus {
  enabled: boolean;
  configured: boolean;
  apiUrl: string;
  mode: FleetbaseMode;
  writeOperationsEnabled: boolean;
  tenantMapping: FleetbaseTenantMapping | null;
}

export interface FleetbaseTenantMapping {
  tenantId: string;
  mapped: boolean;
  expectedOrganizationId: string | null;
  organizationVerificationEnabled: boolean;
}

export interface FleetbaseOrganization {
  id: string;
  name: string | null;
  timezone: string | null;
  country: string | null;
  currency: string | null;
  matchesExpectedOrganization: boolean;
}

export interface FleetbaseVehicle {
  id: string;
  name: string | null;
  internalId: string | null;
  plateNumber: string | null;
  type: string | null;
  status: string | null;
  online: boolean | null;
  payloadCapacity: number | null;
  make: string | null;
  model: string | null;
  year: string | null;
  latitude: number | null;
  longitude: number | null;
  heading: number | null;
  speed: number | null;
  altitude: number | null;
  positionUpdatedAt: string | null;
}

export interface FleetbaseVehicleList {
  vehicles: FleetbaseVehicle[];
  count: number;
  source: string;
}

export interface FleetbaseAuditSummary {
  tenantId: string;
  totalLinks: number;
  prepared: number;
  created: number;
  dispatched: number;
  failed: number;
  reconciliationIssues: number;
  webhookEvents: number;
  lastWebhookAt: string | null;
  writesEnabled: boolean;
  webhookConfigured: boolean;
  rolloutStatus: string;
}
