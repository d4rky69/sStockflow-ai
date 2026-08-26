export type ProposalType = 'TRANSFER' | 'PURCHASE';
export type ProposalStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface ActionProposal {
  proposalId: string;
  tenantId: string;
  proposalType: ProposalType;
  status: ProposalStatus;
  skuId: string;
  quantity: number;
  sourceWarehouseId?: string;
  destinationWarehouseId?: string;
  supplierReference?: string;
  unitCost?: number;
  transportCost?: number;
  currency: string;
  reason: string;
  recommendationEvidence?: string;
  createdBy: string;
  submittedBy?: string;
  reviewedBy?: string;
  reviewComment?: string;
  createdAt: string;
  updatedAt: string;
  submittedAt?: string;
  reviewedAt?: string;
  version: number;
}

export interface ProposalHistory {
  historyId: string;
  proposalId: string;
  fromStatus?: ProposalStatus;
  toStatus: ProposalStatus;
  changedBy: string;
  comment?: string;
  changedAt: string;
}

export interface TransferProposalRequest {
  skuId: string;
  quantity: number;
  sourceWarehouseId: string;
  destinationWarehouseId: string;
  unitCost?: number;
  transportCost?: number;
  currency: string;
  reason: string;
  recommendationEvidence?: string;
}

export interface PurchaseProposalRequest {
  skuId: string;
  quantity: number;
  destinationWarehouseId: string;
  supplierReference?: string;
  unitCost?: number;
  currency: string;
  reason: string;
  recommendationEvidence?: string;
}

export type TransferExecutionStatus = 'PLANNED' | 'RESERVED' | 'IN_TRANSIT' | 'RECEIVED' | 'CANCELLED';
export interface TransferExecution {
  executionId: string; proposalId: string; tenantId: string; status: TransferExecutionStatus;
  skuId: string; sourceWarehouseId: string; destinationWarehouseId: string; quantity: number;
  routeReference?: string; vehicleReference?: string; actualTransportCost?: number; actualCarbonKg?: number;
  createdBy: string; dispatchedBy?: string; receivedBy?: string; createdAt: string;
  reservedAt?: string; dispatchedAt?: string; receivedAt?: string; updatedAt: string; version: number;
}
export interface TransferAllocation { batchNumber: string; quantity: number; expiryDate: string; unitCost: number; currency: string; }
export interface TransferExecutionEvent { eventId: string; fromStatus?: TransferExecutionStatus; toStatus: TransferExecutionStatus; changedBy: string; comment?: string; occurredAt: string; }
export type FleetbaseOrderLinkStatus = 'PREPARED' | 'CREATED' | 'DISPATCHED' | 'FAILED' | 'CANCELLED';
export interface FleetbaseOrderLink {
  linkId: string; tenantId: string; transferExecutionId: string; proposalId: string;
  fleetbaseOrganizationId: string; fleetbaseOrderId?: string; fleetbaseInternalId: string;
  vehicleId?: string; status: FleetbaseOrderLinkStatus; attemptCount: number;
  lastErrorCode?: string; lastErrorMessage?: string; createdBy: string; createdAt: string;
  updatedAt: string; remoteCreatedAt?: string; dispatchedAt?: string; remoteWritePerformed: boolean;
  remoteStatus?: string; trackingNumber?: string; progressPercentage?: number; etaSeconds?: number;
  latitude?: number; longitude?: number; lastTrackerAt?: string; lastReconciledAt?: string;
  lastWebhookAt?: string; reconciliationStatus: string;
}
export interface FleetbaseTracking {
  transferExecutionId: string; fleetbaseOrderId: string; remoteStatus?: string; trackingNumber?: string;
  latitude?: number; longitude?: number; progressPercentage?: number; totalDistanceMeters?: number;
  completedDistanceMeters?: number; currentDestinationEtaSeconds?: number; completionEtaSeconds?: number;
  estimatedCompletionTime?: string; currentDestination?: string; etaByDestination: Record<string, number>;
  reconciliationStatus: string; synchronizedAt: string;
}
export interface TransferExecutionDetail { execution: TransferExecution; allocations: TransferAllocation[]; events: TransferExecutionEvent[]; fleetbaseOrderLink?: FleetbaseOrderLink; }

export type PurchaseOrderStatus = 'PO_CREATED' | 'SENT_TO_SUPPLIER' | 'ACKNOWLEDGED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';
export interface PurchaseOrder {
  purchaseOrderId: string; proposalId: string; tenantId: string; status: PurchaseOrderStatus; skuId: string;
  destinationWarehouseId: string; supplierReference: string; orderedQuantity: number; receivedQuantity: number;
  remainingQuantity: number; unitCost: number; currency: string; expectedDeliveryDate?: string;
  supplierAcknowledgementReference?: string; createdBy: string; sentBy?: string; acknowledgedBy?: string;
  lastReceivedBy?: string; createdAt: string; sentAt?: string; acknowledgedAt?: string; lastReceivedAt?: string;
  updatedAt: string; version: number;
}
export interface PurchaseReceipt { receiptId: string; quantity: number; batchNumber: string; manufactureDate?: string; expiryDate: string; unitCost: number; storageConditionCode: string; receivedBy: string; receivedAt: string; }
export interface PurchaseOrderEvent { eventId: string; fromStatus?: PurchaseOrderStatus; toStatus: PurchaseOrderStatus; changedBy: string; comment?: string; occurredAt: string; }
export interface PurchaseOrderDetail { purchaseOrder: PurchaseOrder; receipts: PurchaseReceipt[]; events: PurchaseOrderEvent[]; }
