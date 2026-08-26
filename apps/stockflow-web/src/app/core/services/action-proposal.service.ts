import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { ActionProposal, FleetbaseOrderLink, FleetbaseTracking, ProposalHistory, PurchaseOrder, PurchaseOrderDetail, PurchaseProposalRequest, TransferExecution, TransferExecutionDetail, TransferProposalRequest } from '../models/action.models';

@Injectable({ providedIn: 'root' })
export class ActionProposalService {
  private readonly baseUrl = `${API_BASE_URL}/api/v1/actions`;

  constructor(private readonly http: HttpClient) {}

  list(status?: string, type?: string): Observable<ActionProposal[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);
    if (type) params = params.set('type', type);
    return this.http.get<ActionProposal[]>(`${this.baseUrl}/proposals`, { headers: this.tenantHeaders(), params });
  }

  history(proposalId: string): Observable<ProposalHistory[]> {
    return this.http.get<ProposalHistory[]>(`${this.baseUrl}/proposals/${proposalId}/history`, { headers: this.tenantHeaders() });
  }

  createTransfer(body: TransferProposalRequest, idempotencyKey: string): Observable<ActionProposal> {
    return this.http.post<ActionProposal>(`${this.baseUrl}/transfers`, body, { headers: this.tenantHeaders().set('Idempotency-Key', idempotencyKey) });
  }

  createPurchase(body: PurchaseProposalRequest, idempotencyKey: string): Observable<ActionProposal> {
    return this.http.post<ActionProposal>(`${this.baseUrl}/purchases`, body, { headers: this.tenantHeaders().set('Idempotency-Key', idempotencyKey) });
  }

  submit(proposalId: string, comment = ''): Observable<ActionProposal> { return this.transition(proposalId, 'submit', comment); }
  approve(proposalId: string, comment = ''): Observable<ActionProposal> { return this.transition(proposalId, 'approve', comment); }
  reject(proposalId: string, comment: string): Observable<ActionProposal> { return this.transition(proposalId, 'reject', comment); }
  cancel(proposalId: string, comment = ''): Observable<ActionProposal> { return this.transition(proposalId, 'cancel', comment); }

  executions(): Observable<TransferExecution[]> { return this.http.get<TransferExecution[]>(`${this.baseUrl}/transfer-executions`, { headers: this.tenantHeaders() }); }
  execution(executionId: string): Observable<TransferExecutionDetail> { return this.http.get<TransferExecutionDetail>(`${this.baseUrl}/transfer-executions/${executionId}`, { headers: this.tenantHeaders() }); }
  createExecution(proposalId: string, routeReference = '', vehicleReference = ''): Observable<TransferExecutionDetail> {
    return this.http.post<TransferExecutionDetail>(`${this.baseUrl}/proposals/${proposalId}/execution`, { routeReference: routeReference || null, vehicleReference: vehicleReference || null }, { headers: this.tenantHeaders().set('Idempotency-Key', `web-execution-${proposalId}`) });
  }
  reserveExecution(id: string, comment = ''): Observable<TransferExecutionDetail> { return this.executionTransition(id, 'reserve', { comment }); }
  dispatchExecution(id: string, comment = ''): Observable<TransferExecutionDetail> { return this.executionTransition(id, 'dispatch', { comment }); }
  receiveExecution(id: string, comment = '', actualTransportCost?: number, actualCarbonKg?: number): Observable<TransferExecutionDetail> { return this.executionTransition(id, 'receive', { comment, actualTransportCost, actualCarbonKg }); }
  cancelExecution(id: string, comment = ''): Observable<TransferExecutionDetail> { return this.executionTransition(id, 'cancel', { comment }); }
  prepareFleetbaseOrderLink(executionId: string): Observable<FleetbaseOrderLink> {
    return this.http.post<FleetbaseOrderLink>(`${this.baseUrl}/transfer-executions/${executionId}/fleetbase-link`, {}, {
      headers: this.tenantHeaders().set('Idempotency-Key', `web-fleetbase-link-${executionId}`)
    });
  }
  createFleetbaseOrder(executionId: string): Observable<FleetbaseOrderLink> {
    return this.http.post<FleetbaseOrderLink>(`${this.baseUrl}/transfer-executions/${executionId}/fleetbase-order`, {}, {
      headers: this.tenantHeaders().set('Idempotency-Key', `web-fleetbase-create-${executionId}`)
    });
  }
  fleetbaseTracking(executionId: string): Observable<FleetbaseTracking> {
    return this.http.get<FleetbaseTracking>(`${this.baseUrl}/transfer-executions/${executionId}/fleetbase-tracking`, { headers: this.tenantHeaders() });
  }
  reconcileFleetbase(executionId: string): Observable<FleetbaseTracking> {
    return this.http.post<FleetbaseTracking>(`${this.baseUrl}/transfer-executions/${executionId}/fleetbase-reconcile`, {}, { headers: this.tenantHeaders() });
  }

  purchaseOrders(): Observable<PurchaseOrder[]> { return this.http.get<PurchaseOrder[]>(`${this.baseUrl}/purchase-orders`, { headers: this.tenantHeaders() }); }
  purchaseOrder(id: string): Observable<PurchaseOrderDetail> { return this.http.get<PurchaseOrderDetail>(`${this.baseUrl}/purchase-orders/${id}`, { headers: this.tenantHeaders() }); }
  createPurchaseOrder(proposalId: string, expectedDeliveryDate?: string): Observable<PurchaseOrderDetail> {
    return this.http.post<PurchaseOrderDetail>(`${this.baseUrl}/proposals/${proposalId}/purchase-order`, { expectedDeliveryDate: expectedDeliveryDate || null }, { headers: this.tenantHeaders().set('Idempotency-Key', `web-po-${proposalId}`) });
  }
  sendPurchaseOrder(id: string, comment = ''): Observable<PurchaseOrderDetail> { return this.purchaseTransition(id, 'send', { comment }); }
  acknowledgePurchaseOrder(id: string, acknowledgementReference = '', expectedDeliveryDate = '', comment = ''): Observable<PurchaseOrderDetail> { return this.purchaseTransition(id, 'acknowledge', { acknowledgementReference: acknowledgementReference || null, expectedDeliveryDate: expectedDeliveryDate || null, comment }); }
  receivePurchaseOrder(id: string, body: { quantity: number; batchNumber: string; manufactureDate?: string; expiryDate: string; unitCost?: number; storageConditionCode: string; comment?: string }): Observable<PurchaseOrderDetail> {
    return this.http.post<PurchaseOrderDetail>(`${this.baseUrl}/purchase-orders/${id}/receive`, body, { headers: this.tenantHeaders().set('Idempotency-Key', `web-receipt-${crypto.randomUUID()}`) });
  }
  cancelPurchaseOrder(id: string, comment = ''): Observable<PurchaseOrderDetail> { return this.purchaseTransition(id, 'cancel', { comment }); }

  private transition(proposalId: string, action: string, comment: string): Observable<ActionProposal> {
    return this.http.post<ActionProposal>(`${this.baseUrl}/proposals/${proposalId}/${action}`, { comment }, { headers: this.tenantHeaders() });
  }
  private executionTransition(id: string, action: string, body: object): Observable<TransferExecutionDetail> { return this.http.post<TransferExecutionDetail>(`${this.baseUrl}/transfer-executions/${id}/${action}`, body, { headers: this.tenantHeaders() }); }
  private purchaseTransition(id: string, action: string, body: object): Observable<PurchaseOrderDetail> { return this.http.post<PurchaseOrderDetail>(`${this.baseUrl}/purchase-orders/${id}/${action}`, body, { headers: this.tenantHeaders() }); }

  private tenantHeaders(): HttpHeaders {
    return new HttpHeaders({ 'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA' });
  }
}
