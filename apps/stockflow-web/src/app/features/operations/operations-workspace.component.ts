import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PrototypeStateService } from '../../core/services/prototype-state.service';
import { CarbonApiService } from '../../core/services/carbon-api.service';
import { ActionProposal, FleetbaseTracking, ProposalHistory, ProposalType, PurchaseOrder, PurchaseOrderDetail, TransferExecution, TransferExecutionDetail } from '../../core/models/action.models';
import { ActionProposalService } from '../../core/services/action-proposal.service';
import { SkuView, WarehouseView } from '../../core/models/foundation.models';
import { FoundationDataService } from '../../core/services/foundation-data.service';
import { AuthService } from '../../core/services/auth.service';
import { ReplenishmentSummary } from '../../core/models/replenishment.models';
import { ReplenishmentService } from '../../core/services/replenishment.service';
import { CustomerOrderService } from '../../core/services/customer-order.service';
import { CreateCustomerOrderRequest, CustomerOrderView } from '../../core/models/customer-order.models';

export type OperationView = 'transfers' | 'purchase' | 'orders' | 'returns' | 'routes' | 'sustainability';

interface TransferPlan {
  id: string;
  sku: string;
  product: string;
  from: string;
  to: string;
  quantity: number;
  priority: string;
  status: string;
  distanceKm: number;
  eta: string;
  reason: string;
  co2SavedKg: number;
  serviceLift: number;
}

interface PurchasePlan {
  id: string;
  sku: string;
  product: string;
  supplier: string;
  quantity: number;
  unitCost: number;
  needBy: string;
  leadTimeDays: number;
  coverDays: number;
  confidence: number;
  risk: string;
  status: string;
  warehouseId?: string;
  warehouseName?: string;
  explanation?: string;
  openPurchaseQuantity?: number;
  demandSource?: string;
}

interface CustomerOrder {
  orderId?: string;
  id: string;
  customer: string;
  city: string;
  channel: string;
  warehouse: string;
  itemCount: number;
  value: number;
  promisedDate: string;
  fulfillment: number;
  status: string;
  skuId?: string;
  skuName?: string;
  quantity?: number;
}

interface OrderForm {
  customerName: string;
  customerCity: string;
  channel: string;
  warehouseId: string;
  skuId: string;
  quantity: number;
  promisedAt: string;
  unitPrice?: number;
}

interface ReturnCase {
  id: string;
  orderId: string;
  customer: string;
  product: string;
  quantity: number;
  reason: string;
  disposition: string;
  value: number;
  receivedDate: string;
  warehouse: string;
  status: string;
}

interface RoutePlan {
  id: string;
  lane: string;
  stops: string[];
  vehicle: string;
  loadKg: number;
  capacityKg: number;
  baselineKm: number;
  optimizedKm: number;
  duration: string;
  costInr: number;
  co2Kg: number;
  co2SavedKg: number;
  priority: string;
  status: string;
}

interface SustainabilityRecord {
  location: string;
  state: string;
  trips: number;
  distanceKm: number;
  emissionsKg: number;
  emissionsAvoidedKg: number;
  wasteAvoidedKg: number;
  intensity: number;
  status: string;
}

interface ProposalForm {
  type: ProposalType;
  skuId: string;
  quantity: number;
  sourceWarehouseId: string;
  destinationWarehouseId: string;
  supplierReference: string;
  unitCost?: number;
  transportCost?: number;
  reason: string;
  recommendationEvidence: string;
}

@Component({
  selector: 'sf-operations-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './operations-workspace.component.html',
  styleUrl: './operations-workspace.component.css'
})
export class OperationsWorkspaceComponent implements OnChanges, OnDestroy, OnInit {
  @Input({ required: true }) view: OperationView = 'transfers';
  @Input() tenantLabel = 'Selected tenant';
  @Input() searchQuery = '';

  readonly prototype = inject(PrototypeStateService);
  private readonly carbonApi = inject(CarbonApiService);
  private readonly actionApi = inject(ActionProposalService);
  private readonly foundationApi = inject(FoundationDataService);
  readonly auth = inject(AuthService);
  private readonly replenishmentApi = inject(ReplenishmentService);
  private readonly orderApi = inject(CustomerOrderService);

  statusFilter = 'ALL';
  locationFilter = 'ALL';
  routeObjective = 'Balanced cost and carbon';
  vehicleType = 'All eligible vehicles';
  selectedRouteId = 'RTE-301';
  toastMessage = '';
  routeOptimizationRunning = false;
  proposals: ActionProposal[] = [];
  proposalHistory: ProposalHistory[] = [];
  proposalsLoading = false;
  proposalSaving = false;
  proposalError = '';
  proposalDialogOpen = false;
  selectedProposal?: ActionProposal;
  reviewComment = '';
  proposalForm: ProposalForm = this.emptyProposal('TRANSFER');
  proposalWarehouses: WarehouseView[] = [];
  proposalSkus: SkuView[] = [];
  transferExecutions: TransferExecution[] = [];
  selectedExecution?: TransferExecutionDetail;
  fleetbaseTracking?: FleetbaseTracking;
  fleetbaseTrackingLoading = false;
  purchaseOrders: PurchaseOrder[] = [];
  selectedPurchaseOrder?: PurchaseOrderDetail;
  executionComment = '';
  actualTransportCost?: number;
  actualCarbonKg?: number;
  poExpectedDate = '';
  poAcknowledgement = '';
  receiptQuantity = 1;
  receiptBatchNumber = '';
  receiptManufactureDate = '';
  receiptExpiryDate = '';
  receiptStorage = 'AMBIENT';
  replenishmentSummary?: ReplenishmentSummary;
  replenishmentLoading = false;
  transferRecommendationsLoading = false;
  targetCoverDays = 30;
  ordersLoading = false;
  orderSaving = false;
  orderActionId = '';
  orderError = '';
  orderDialogOpen = false;
  orderForm: OrderForm = this.emptyOrder();
  private toastTimer?: number;

  transfers: TransferPlan[] = [
    { id: 'TRF-2048', sku: 'SKU-PARA-650', product: 'Paracetamol 650 mg', from: 'Guwahati Central', to: 'Shillong Hub', quantity: 840, priority: 'Critical', status: 'Awaiting approval', distanceKm: 102, eta: 'Today, 18:30', reason: 'Stockout projected in 2.1 days', co2SavedKg: 18.4, serviceLift: 12 },
    { id: 'TRF-2047', sku: 'SKU-AMOX-500', product: 'Amoxicillin 500 mg', from: 'Imphal Hub', to: 'Guwahati Central', quantity: 460, priority: 'High', status: 'Approved', distanceKm: 485, eta: 'Tomorrow, 09:00', reason: 'Safety stock breach at destination', co2SavedKg: 11.2, serviceLift: 8 },
    { id: 'TRF-2046', sku: 'SKU-ORS-21', product: 'ORS Sachet 21 g', from: 'Shillong Hub', to: 'Dimapur DC', quantity: 1200, priority: 'Medium', status: 'In transit', distanceKm: 335, eta: 'Today, 15:45', reason: 'Demand surge after regional campaign', co2SavedKg: 22.8, serviceLift: 6 },
    { id: 'TRF-2045', sku: 'SKU-CET-10', product: 'Cetirizine 10 mg', from: 'Guwahati Central', to: 'Agartala West', quantity: 320, priority: 'Medium', status: 'Delivered', distanceKm: 599, eta: 'Delivered 10:24', reason: 'Balanced excess inventory', co2SavedKg: 8.6, serviceLift: 4 }
  ];

  purchasePlans: PurchasePlan[] = [
    { id: 'PLAN-8821', sku: 'SKU-INS-GLR', product: 'Insulin Glargine', supplier: 'MedAxis Biologics', quantity: 480, unitCost: 618, needBy: '09 Aug 2026', leadTimeDays: 4, coverDays: 3.2, confidence: 92, risk: 'Critical', status: 'Ready for approval' },
    { id: 'PLAN-8820', sku: 'SKU-AMOX-500', product: 'Amoxicillin 500 mg', supplier: 'NovaCure Labs', quantity: 2400, unitCost: 7.8, needBy: '11 Aug 2026', leadTimeDays: 6, coverDays: 6.8, confidence: 88, risk: 'High', status: 'Supplier review' },
    { id: 'PLAN-8819', sku: 'SKU-PARA-650', product: 'Paracetamol 650 mg', supplier: 'Apex Remedies', quantity: 5000, unitCost: 2.45, needBy: '13 Aug 2026', leadTimeDays: 5, coverDays: 8.4, confidence: 95, risk: 'High', status: 'Draft' },
    { id: 'PLAN-8818', sku: 'SKU-ORS-21', product: 'ORS Sachet 21 g', supplier: 'WellSpring Pharma', quantity: 3200, unitCost: 5.1, needBy: '16 Aug 2026', leadTimeDays: 7, coverDays: 11.7, confidence: 84, risk: 'Medium', status: 'Approved' }
  ];

  orders: CustomerOrder[] = [
    { id: 'SO-10842', customer: 'Lotus Care Pharmacy', city: 'Guwahati', channel: 'B2B Portal', warehouse: 'Guwahati Central', itemCount: 14, value: 68420, promisedDate: 'Today, 16:00', fulfillment: 100, status: 'Ready to ship' },
    { id: 'SO-10841', customer: 'GreenCross Medicals', city: 'Shillong', channel: 'EDI', warehouse: 'Shillong Hub', itemCount: 8, value: 42180, promisedDate: 'Today, 18:30', fulfillment: 86, status: 'Picking' },
    { id: 'SO-10840', customer: 'City Health Mart', city: 'Imphal', channel: 'Sales desk', warehouse: 'Imphal Hub', itemCount: 22, value: 116750, promisedDate: 'Tomorrow, 10:00', fulfillment: 64, status: 'Allocated' },
    { id: 'SO-10839', customer: 'MediPoint Stores', city: 'Agartala', channel: 'B2B Portal', warehouse: 'Agartala West', itemCount: 6, value: 27990, promisedDate: '08 Aug 2026', fulfillment: 100, status: 'Shipped' },
    { id: 'SO-10838', customer: 'Aarogya Distributors', city: 'Dimapur', channel: 'EDI', warehouse: 'Dimapur DC', itemCount: 11, value: 53760, promisedDate: '08 Aug 2026', fulfillment: 38, status: 'On hold' }
  ];

  returns: ReturnCase[] = [
    { id: 'RET-3621', orderId: 'SO-10791', customer: 'Lotus Care Pharmacy', product: 'Insulin Glargine', quantity: 12, reason: 'Cold-chain excursion', disposition: 'Quality inspection', value: 8856, receivedDate: 'Today, 09:42', warehouse: 'Guwahati Central', status: 'Needs review' },
    { id: 'RET-3620', orderId: 'SO-10768', customer: 'MediPoint Stores', product: 'Paracetamol 650 mg', quantity: 80, reason: 'Transit damage', disposition: 'Supplier claim', value: 3120, receivedDate: 'Yesterday', warehouse: 'Agartala West', status: 'Approved' },
    { id: 'RET-3619', orderId: 'SO-10744', customer: 'GreenCross Medicals', product: 'Cetirizine 10 mg', quantity: 44, reason: 'Short-dated stock', disposition: 'FEFO reallocation', value: 2464, receivedDate: '04 Aug 2026', warehouse: 'Shillong Hub', status: 'Processing' },
    { id: 'RET-3618', orderId: 'SO-10712', customer: 'City Health Mart', product: 'ORS Sachet 21 g', quantity: 120, reason: 'Order entry error', disposition: 'Return to stock', value: 1044, receivedDate: '03 Aug 2026', warehouse: 'Imphal Hub', status: 'Closed' }
  ];

  routePlans: RoutePlan[] = [
    { id: 'RTE-301', lane: 'Guwahati → Shillong → Dimapur', stops: ['Guwahati Central', 'Shillong Hub', 'Dimapur DC'], vehicle: '12T electric-assisted truck', loadKg: 10860, capacityKg: 12000, baselineKm: 440, optimizedKm: 382, duration: '8h 35m', costInr: 28400, co2Kg: 86.2, co2SavedKg: 31.8, priority: 'Critical', status: 'Ready for approval' },
    { id: 'RTE-302', lane: 'Imphal → Guwahati', stops: ['Imphal Hub', 'Nagaon Cross-dock', 'Guwahati Central'], vehicle: '16T diesel BS-VI truck', loadKg: 13120, capacityKg: 16000, baselineKm: 520, optimizedKm: 485, duration: '10h 20m', costInr: 36150, co2Kg: 142.6, co2SavedKg: 12.4, priority: 'High', status: 'Optimized' },
    { id: 'RTE-303', lane: 'Guwahati → Agartala', stops: ['Guwahati Central', 'Silchar Hub', 'Agartala West'], vehicle: '9T CNG truck', loadKg: 7960, capacityKg: 9000, baselineKm: 620, optimizedKm: 599, duration: '12h 05m', costInr: 23800, co2Kg: 73.4, co2SavedKg: 16.7, priority: 'High', status: 'Approved' },
    { id: 'RTE-304', lane: 'Shillong → Dimapur', stops: ['Shillong Hub', 'Jowai Drop', 'Dimapur DC'], vehicle: '6T electric truck', loadKg: 5160, capacityKg: 6000, baselineKm: 360, optimizedKm: 335, duration: '6h 10m', costInr: 9400, co2Kg: 18.8, co2SavedKg: 14.2, priority: 'Medium', status: 'In transit' }
  ];

  sustainabilityRecords: SustainabilityRecord[] = [
    { location: 'Guwahati Central', state: 'Assam', trips: 42, distanceKm: 8240, emissionsKg: 1840, emissionsAvoidedKg: 318, wasteAvoidedKg: 462, intensity: 0.223, status: 'On target' },
    { location: 'Shillong Hub', state: 'Meghalaya', trips: 36, distanceKm: 6910, emissionsKg: 1395, emissionsAvoidedKg: 284, wasteAvoidedKg: 386, intensity: 0.202, status: 'On target' },
    { location: 'Imphal Hub', state: 'Manipur', trips: 31, distanceKm: 7550, emissionsKg: 1928, emissionsAvoidedKg: 172, wasteAvoidedKg: 318, intensity: 0.255, status: 'Needs attention' },
    { location: 'Agartala West', state: 'Tripura', trips: 24, distanceKm: 3860, emissionsKg: 792, emissionsAvoidedKg: 146, wasteAvoidedKg: 274, intensity: 0.205, status: 'On target' },
    { location: 'Dimapur DC', state: 'Nagaland', trips: 19, distanceKm: 2140, emissionsKg: 438, emissionsAvoidedKg: 96, wasteAvoidedKg: 181, intensity: 0.205, status: 'Improving' }
  ];

  ngOnInit(): void {
    this.applyStoredPatches('transfers', this.transfers);
    this.applyStoredPatches('purchasePlans', this.purchasePlans);
    this.applyStoredPatches('orders', this.orders);
    this.applyStoredPatches('returns', this.returns);
    this.applyStoredPatches('routePlans', this.routePlans);
    this.sustainabilityRecords.forEach(record => {
      Object.assign(record, this.prototype.recordPatch<SustainabilityRecord>('sustainability', record.location));
    });
    this.loadProposals();
    this.loadProposalOptions();
    this.loadExecutions();
    this.loadPurchaseOrders();
    this.loadReplenishmentPlans();
    this.loadTransferRecommendations();
    this.loadOrders();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['view']) {
      this.statusFilter = 'ALL';
      this.locationFilter = 'ALL';
      if (this.view === 'purchase') this.loadReplenishmentPlans();
      if (this.view === 'orders') this.loadOrders();
    }
  }

  ngOnDestroy(): void {
    if (this.toastTimer) window.clearTimeout(this.toastTimer);
  }

  get pageCopy(): { eyebrow: string; title: string; description: string; action: string } {
    const copy: Record<OperationView, { eyebrow: string; title: string; description: string; action: string }> = {
      transfers: { eyebrow: 'NETWORK ORCHESTRATION', title: 'Smart Transfers', description: 'Rebalance inventory across warehouses with route-aware, low-carbon transfer recommendations.', action: 'Generate transfer plan' },
      purchase: { eyebrow: 'REPLENISHMENT CONTROL', title: 'Purchase Planning', description: 'Convert forecast signals into reviewable supplier plans while protecting cash and service levels.', action: 'Create purchase plan' },
      orders: { eyebrow: 'ORDER FULFILMENT', title: 'Orders', description: 'Prioritize, allocate and track customer orders across the distribution network.', action: 'Create order' },
      returns: { eyebrow: 'REVERSE LOGISTICS', title: 'Returns', description: 'Triage returns quickly, protect quality and recover value through the right disposition path.', action: 'Register return' },
      routes: { eyebrow: 'LOGISTICS INTELLIGENCE', title: 'Route Optimization', description: 'Consolidate warehouse movements into capacity-aware routes ranked by service, cost, distance and estimated emissions.', action: 'Optimize routes' },
      sustainability: { eyebrow: 'SUSTAINABILITY CONTROL', title: 'Carbon & Waste Impact', description: 'Track estimated logistics emissions, avoided kilometres and product-waste reduction with transparent calculation evidence.', action: 'Export impact report' }
    };
    return copy[this.view];
  }

  locations(): string[] {
    const values = this.view === 'transfers'
      ? this.transfers.flatMap(item => [item.from, item.to])
      : this.view === 'routes'
        ? this.routePlans.flatMap(item => item.stops)
        : this.view === 'sustainability'
          ? this.sustainabilityRecords.map(item => item.location)
      : this.view === 'purchase'
        ? this.purchasePlans.map(item => item.supplier)
        : this.view === 'orders'
          ? this.orders.map(item => item.warehouse)
          : this.returns.map(item => item.warehouse);
    return [...new Set(values)].sort();
  }

  statuses(): string[] {
    const values = this.view === 'transfers'
      ? this.transfers.map(item => item.status)
      : this.view === 'routes'
        ? this.routePlans.map(item => item.status)
        : this.view === 'sustainability'
          ? this.sustainabilityRecords.map(item => item.status)
      : this.view === 'purchase'
        ? this.purchasePlans.map(item => item.status)
        : this.view === 'orders'
          ? this.orders.map(item => item.status)
          : this.returns.map(item => item.status);
    return [...new Set(values)].sort();
  }

  filteredTransfers(): TransferPlan[] {
    return this.transfers.filter(item => this.matches(item.status, [item.id, item.sku, item.product, item.from, item.to, item.reason], [item.from, item.to]));
  }

  filteredPurchasePlans(): PurchasePlan[] {
    return this.purchasePlans.filter(item => this.matches(item.status, [item.id, item.sku, item.product, item.supplier, item.risk], [item.supplier]));
  }

  filteredOrders(): CustomerOrder[] {
    return this.orders.filter(item => this.matches(item.status, [item.id, item.customer, item.city, item.channel, item.warehouse], [item.warehouse]));
  }

  filteredReturns(): ReturnCase[] {
    return this.returns.filter(item => this.matches(item.status, [item.id, item.orderId, item.customer, item.product, item.reason, item.disposition], [item.warehouse]));
  }

  filteredRoutes(): RoutePlan[] {
    return this.routePlans.filter(item => this.matches(item.status, [item.id, item.lane, item.vehicle, item.priority, ...item.stops], item.stops));
  }

  filteredSustainability(): SustainabilityRecord[] {
    return this.sustainabilityRecords.filter(item => this.matches(item.status, [item.location, item.state, item.status], [item.location]));
  }

  selectedRoute(): RoutePlan {
    return this.routePlans.find(item => item.id === this.selectedRouteId) ?? this.routePlans[0];
  }

  totalOptimizedKm(): number {
    return this.routePlans.reduce((sum, item) => sum + item.optimizedKm, 0);
  }

  totalRouteKmSaved(): number {
    return this.routePlans.reduce((sum, item) => sum + item.baselineKm - item.optimizedKm, 0);
  }

  averageLoadUtilization(): number {
    return this.routePlans.reduce((sum, item) => sum + item.loadKg / item.capacityKg * 100, 0) / Math.max(this.routePlans.length, 1);
  }

  routeCo2Saved(): number {
    return this.routePlans.reduce((sum, item) => sum + item.co2SavedKg, 0);
  }

  totalEmissions(): number {
    return this.sustainabilityRecords.reduce((sum, item) => sum + item.emissionsKg, 0);
  }

  totalEmissionsAvoided(): number {
    return this.sustainabilityRecords.reduce((sum, item) => sum + item.emissionsAvoidedKg, 0);
  }

  totalWasteAvoided(): number {
    return this.sustainabilityRecords.reduce((sum, item) => sum + item.wasteAvoidedKg, 0);
  }

  averageCarbonIntensity(): number {
    const distance = this.sustainabilityRecords.reduce((sum, item) => sum + item.distanceKm, 0);
    return this.totalEmissions() / Math.max(distance, 1);
  }

  transferUnits(): number {
    return this.transfers.reduce((sum, item) => sum + item.quantity, 0);
  }

  transferCo2Saved(): number {
    return this.transfers.reduce((sum, item) => sum + item.co2SavedKg, 0);
  }

  purchasePlanValue(): number {
    return this.purchasePlans.reduce((sum, item) => sum + item.quantity * item.unitCost, 0);
  }

  orderValue(): number {
    return this.orders.reduce((sum, item) => sum + item.value, 0);
  }

  averageFulfillment(): number {
    return this.orders.reduce((sum, item) => sum + item.fulfillment, 0) / Math.max(this.orders.length, 1);
  }

  returnValue(): number {
    return this.returns.reduce((sum, item) => sum + item.value, 0);
  }

  transferCount(status: string): number {
    return this.transfers.filter(item => item.status === status).length;
  }

  orderCount(status: string): number {
    return this.orders.filter(item => item.status === status).length;
  }

  statusClass(value: string): string {
    return value.toLowerCase().replaceAll(' ', '-');
  }

  approveTransfer(item: TransferPlan): void {
    this.openTransferProposal(item);
  }

  averagePurchaseConfidence(): number {
    return this.purchasePlans.reduce((sum, item) => sum + item.confidence, 0) / Math.max(this.purchasePlans.length, 1);
  }

  criticalPurchaseCount(): number { return this.purchasePlans.filter(item => item.risk === 'CRITICAL' || item.risk === 'Critical').length; }

  loadReplenishmentPlans(): void {
    this.replenishmentLoading = true;
    this.replenishmentApi.plans(this.targetCoverDays).subscribe({
      next: summary => {
        this.replenishmentSummary = summary;
        this.purchasePlans = summary.plans.map(item => ({
          id: item.recommendationId, sku: item.skuId, product: item.skuName, supplier: item.supplierName,
          quantity: item.recommendedQuantity, unitCost: item.unitCost, needBy: item.needBy, leadTimeDays: item.leadTimeDays,
          coverDays: item.coverDays ?? 0, confidence: item.confidencePercent, risk: item.risk, status: item.status,
          warehouseId: item.warehouseId, warehouseName: item.warehouseName, explanation: item.explanation,
          openPurchaseQuantity: item.openPurchaseQuantity, demandSource: item.demandSource
        }));
        this.replenishmentLoading = false;
      },
      error: error => {
        this.replenishmentLoading = false;
        this.proposalError = this.apiError(error, 'Live replenishment plans could not be loaded.');
      }
    });
  }

  approvePurchase(item: PurchasePlan): void {
    this.openPurchaseProposal(item);
  }

  advanceOrder(item: CustomerOrder): void {
    if (!item.orderId || this.orderActionId) return;
    this.orderActionId = item.orderId;
    this.orderError = '';
    this.orderApi.advance(item.orderId, `Advanced from the StockFlow order workspace`).subscribe({
      next: detail => {
        this.orderActionId = '';
        const updated = this.mapOrder(detail.order);
        this.orders = this.orders.map(candidate => candidate.orderId === updated.orderId ? updated : candidate);
        this.showToast(`${updated.id} moved to ${updated.status}.`);
      },
      error: error => {
        this.orderActionId = '';
        this.orderError = this.apiError(error, 'The order could not be advanced.');
      }
    });
  }

  approveReturn(item: ReturnCase): void {
    item.status = item.status === 'Needs review' ? 'Approved' : 'Processing';
    this.prototype.patchRecord('returns', item.id, { status: item.status }, {
      module: 'Returns',
      title: `${item.id} moved to ${item.status}`,
      detail: `${item.quantity} returned units of ${item.product} follow the ${item.disposition.toLowerCase()} path.`,
      tone: 'info'
    });
    this.showToast(`${item.id} moved to ${item.status} and was saved.`);
  }

  selectRoute(item: RoutePlan): void {
    this.selectedRouteId = item.id;
  }

  optimizeRoutes(): void {
    if (this.routeOptimizationRunning) return;
    this.routeOptimizationRunning = true;
    const candidates = this.routePlans.map(({ id, lane, stops, vehicle, loadKg, capacityKg, baselineKm, priority, status }) => ({
      id, lane, stops, vehicle, loadKg, capacityKg, baselineKm, priority, status
    }));
    this.carbonApi.optimiseRoutes(this.routeObjective, this.vehicleType, candidates).subscribe({
      next: response => {
        response.routes.forEach(result => {
          const route = this.routePlans.find(item => item.id === result.id);
          if (!route) return;
          Object.assign(route, {
            optimizedKm: result.optimizedKm,
            duration: result.duration,
            costInr: result.costInr,
            co2Kg: result.co2Kg,
            co2SavedKg: result.co2SavedKg,
            status: result.status
          });
          this.prototype.patchRecord('routePlans', route.id, { ...route }, {
            module: 'Route Optimization',
            title: `${route.id} recalculated`,
            detail: `${route.lane}: ${route.optimizedKm} km and ${route.co2Kg} kg CO₂e using the ${response.solver}.`,
            tone: 'info'
          });
        });
        this.prototype.addActivity({
          module: 'Route Optimization',
          title: 'Backend route candidates recalculated',
          detail: `${response.routes.length} routes ranked using ${response.objective.toLowerCase()}.`,
          tone: 'success'
        });
        this.routeOptimizationRunning = false;
        this.showToast(`${response.routes.length} routes recalculated by the carbon and route backend.`);
      },
      error: () => {
        this.routeOptimizationRunning = false;
        this.showToast('The route and carbon backend could not be reached. Existing route values were preserved.');
      }
    });
  }

  advanceRoute(item: RoutePlan): void {
    const nextStatus: Record<string, string> = {
      'Ready for approval': 'Approved',
      Optimized: 'Approved',
      Approved: 'In transit',
      'In transit': 'Delivered'
    };
    const previousStatus = item.status;
    item.status = nextStatus[item.status] ?? item.status;
    this.selectedRouteId = item.id;
    this.prototype.patchRecord('routePlans', item.id, { status: item.status }, {
      module: 'Route Optimization',
      title: `${item.id} moved to ${item.status}`,
      detail: `${item.lane} advanced from ${previousStatus.toLowerCase()} to ${item.status.toLowerCase()}.`,
      tone: item.status === 'Delivered' ? 'success' : 'info'
    });

    if (item.status === 'Delivered' && previousStatus !== 'Delivered') {
      this.applyDeliveredImpact(item);
    }
    this.showToast(`${item.id} moved to ${item.status}; related impact has been updated.`);
  }

  routeActionLabel(item: RoutePlan): string {
    const labels: Record<string, string> = {
      'Ready for approval': 'Review & approve',
      Optimized: 'Review & approve',
      Approved: 'Start dispatch',
      'In transit': 'Mark delivered',
      Delivered: 'Completed'
    };
    return labels[item.status] ?? 'View route';
  }

  triggerPrimaryAction(): void {
    if (this.view === 'routes') {
      this.optimizeRoutes();
      return;
    }
    if (this.view === 'sustainability') {
      this.showToast('Impact report preview prepared. Backend evidence export is not connected yet.');
      return;
    }
    if (this.view === 'transfers' || this.view === 'purchase') {
      this.openProposalDialog(this.view === 'transfers' ? 'TRANSFER' : 'PURCHASE');
      return;
    }
    if (this.view === 'orders') {
      this.openOrderDialog();
      return;
    }
    this.showToast(`${this.pageCopy.action} opened as a UI preview. Backend submission is not connected yet.`);
  }

  visibleProposals(): ActionProposal[] {
    const type: ProposalType | undefined = this.view === 'transfers' ? 'TRANSFER' : this.view === 'purchase' ? 'PURCHASE' : undefined;
    return type ? this.proposals.filter(item => item.proposalType === type) : [];
  }

  loadProposals(): void {
    this.proposalsLoading = true;
    this.proposalError = '';
    this.actionApi.list().subscribe({
      next: proposals => { this.proposals = proposals; this.proposalsLoading = false; },
      error: error => { this.proposalsLoading = false; this.proposalError = this.apiError(error, 'Proposal queue could not be loaded.'); }
    });
  }

  loadProposalOptions(): void {
    this.foundationApi.warehouses().subscribe({ next: values => this.proposalWarehouses = values, error: () => undefined });
    this.foundationApi.skus().subscribe({ next: values => this.proposalSkus = values, error: () => undefined });
  }

  loadExecutions(): void {
    this.actionApi.executions().subscribe({ next: values => this.transferExecutions = values, error: () => undefined });
  }

  loadOrders(): void {
    if (this.ordersLoading) return;
    this.ordersLoading = true;
    this.orderApi.list().subscribe({
      next: values => {
        this.ordersLoading = false;
        this.orderError = '';
        this.orders = values.map(value => this.mapOrder(value));
      },
      error: error => {
        this.ordersLoading = false;
        this.orderError = this.apiError(error, 'Live customer orders could not be loaded.');
      }
    });
  }

  openOrderDialog(): void {
    this.orderForm = this.emptyOrder();
    this.orderError = '';
    this.orderDialogOpen = true;
    if (!this.proposalWarehouses.length || !this.proposalSkus.length) this.loadProposalOptions();
  }

  closeOrderDialog(): void {
    if (!this.orderSaving) this.orderDialogOpen = false;
  }

  submitOrder(): void {
    if (this.orderSaving) return;
    const form = this.orderForm;
    if (!form.customerName.trim() || !form.customerCity.trim() || !form.channel || !form.warehouseId || !form.skuId || form.quantity < 1 || !form.promisedAt) {
      this.orderError = 'Complete the customer, channel, warehouse, product, quantity and promised-time fields.';
      return;
    }
    if (new Date(form.promisedAt).getTime() <= Date.now()) {
      this.orderError = 'Promised time must be in the future.';
      return;
    }
    const body: CreateCustomerOrderRequest = {
      customerName: form.customerName.trim(), customerCity: form.customerCity.trim(), channel: form.channel,
      warehouseId: form.warehouseId, skuId: form.skuId, quantity: form.quantity,
      promisedAt: form.promisedAt,
      ...(form.unitPrice !== undefined && form.unitPrice !== null ? { unitPrice: form.unitPrice } : {})
    };
    this.orderSaving = true;
    this.orderError = '';
    this.orderApi.create(body).subscribe({
      next: detail => {
        this.orderSaving = false;
        const created = this.mapOrder(detail.order);
        this.orders = [created, ...this.orders.filter(item => item.orderId !== created.orderId)];
        this.orderDialogOpen = false;
        this.showToast(`${created.id} created and persisted. Inventory has not been shipped or deducted.`);
      },
      error: error => {
        this.orderSaving = false;
        this.orderError = this.apiError(error, 'The customer order could not be created.');
      }
    });
  }

  loadTransferRecommendations(): void {
    this.transferRecommendationsLoading = true;
    this.replenishmentApi.transferRecommendations(this.targetCoverDays).subscribe({
      next: summary => {
        this.transferRecommendationsLoading = false;
        this.transfers = summary.recommendations.map(item => ({
          id: item.recommendationId, sku: item.skuId, product: item.skuName,
          from: item.sourceWarehouseName, to: item.destinationWarehouseName,
          quantity: item.recommendedQuantity, priority: this.proposalStatus(item.risk), status: 'Awaiting approval',
          distanceKm: item.distanceKm, eta: `${item.trips} trip${item.trips === 1 ? '' : 's'}`,
          reason: item.explanation, co2SavedKg: item.estimatedCarbonKgCo2e,
          serviceLift: Math.max(1, Math.round(item.recommendedQuantity / Math.max(item.destinationTargetStock, 1) * 100))
        }));
      },
      error: () => { this.transferRecommendationsLoading = false; }
    });
  }

  loadPurchaseOrders(): void { this.actionApi.purchaseOrders().subscribe({ next: values => this.purchaseOrders = values, error: () => undefined }); }

  openProposalDialog(type: ProposalType): void {
    this.proposalForm = this.emptyProposal(type);
    this.selectedProposal = undefined;
    this.proposalHistory = [];
    this.proposalError = '';
    this.proposalDialogOpen = true;
  }

  openTransferProposal(item: TransferPlan): void {
    this.proposalForm = {
      ...this.emptyProposal('TRANSFER'), skuId: item.sku, quantity: item.quantity,
      sourceWarehouseId: this.warehouseId(item.from), destinationWarehouseId: this.warehouseId(item.to),
      transportCost: Math.round(item.distanceKm * 34), reason: item.reason,
      recommendationEvidence: `${item.id}; ${item.distanceKm} km; ${item.co2SavedKg} kg CO2e avoided; estimated service lift ${item.serviceLift}%.`
    };
    this.selectedProposal = undefined;
    this.proposalError = '';
    this.proposalDialogOpen = true;
  }

  openPurchaseProposal(item: PurchasePlan): void {
    this.proposalForm = {
      ...this.emptyProposal('PURCHASE'), skuId: item.sku, quantity: item.quantity,
      destinationWarehouseId: item.warehouseId ?? 'WH-GUWAHATI', supplierReference: item.supplier, unitCost: item.unitCost,
      reason: `${item.risk} stock risk with ${item.coverDays} days of cover remaining.`,
      recommendationEvidence: `${item.id}; ${item.explanation ?? `forecast confidence ${item.confidence}%; need by ${item.needBy}; lead time ${item.leadTimeDays} days.`}`
    };
    this.selectedProposal = undefined;
    this.proposalError = '';
    this.proposalDialogOpen = true;
  }

  saveProposal(): void {
    if (this.proposalSaving || !this.proposalForm.skuId.trim() || this.proposalForm.quantity <= 0 || !this.proposalForm.destinationWarehouseId.trim() || !this.proposalForm.reason.trim()) return;
    this.proposalSaving = true;
    this.proposalError = '';
    const key = `web-${crypto.randomUUID()}`;
    const request = this.proposalForm.type === 'TRANSFER'
      ? this.actionApi.createTransfer({ skuId: this.proposalForm.skuId.trim(), quantity: this.proposalForm.quantity, sourceWarehouseId: this.proposalForm.sourceWarehouseId.trim(), destinationWarehouseId: this.proposalForm.destinationWarehouseId.trim(), unitCost: this.proposalForm.unitCost, transportCost: this.proposalForm.transportCost, currency: 'INR', reason: this.proposalForm.reason.trim(), recommendationEvidence: this.proposalForm.recommendationEvidence.trim() || undefined }, key)
      : this.actionApi.createPurchase({ skuId: this.proposalForm.skuId.trim(), quantity: this.proposalForm.quantity, destinationWarehouseId: this.proposalForm.destinationWarehouseId.trim(), supplierReference: this.proposalForm.supplierReference.trim() || undefined, unitCost: this.proposalForm.unitCost, currency: 'INR', reason: this.proposalForm.reason.trim(), recommendationEvidence: this.proposalForm.recommendationEvidence.trim() || undefined }, key);
    request.subscribe({
      next: proposal => { this.proposalSaving = false; this.proposals.unshift(proposal); this.proposalDialogOpen = false; this.showToast(`${this.shortProposalId(proposal)} created as a draft. No stock was moved.`); },
      error: error => { this.proposalSaving = false; this.proposalError = this.apiError(error, 'Proposal could not be created.'); }
    });
  }

  reviewProposal(proposal: ActionProposal): void {
    this.selectedProposal = proposal;
    this.reviewComment = '';
    this.proposalHistory = [];
    this.proposalError = '';
    this.selectedExecution = undefined;
    this.selectedPurchaseOrder = undefined;
    this.proposalDialogOpen = true;
    this.actionApi.history(proposal.proposalId).subscribe({ next: history => this.proposalHistory = history, error: error => this.proposalError = this.apiError(error, 'Proposal history could not be loaded.') });
    const execution = this.transferExecutions.find(item => item.proposalId === proposal.proposalId);
    if (execution) this.loadExecution(execution.executionId);
    const order = this.purchaseOrders.find(item => item.proposalId === proposal.proposalId);
    if (order) this.loadPurchaseOrder(order.purchaseOrderId);
  }

  createExecution(): void {
    if (!this.selectedProposal || this.proposalSaving) return;
    this.proposalSaving = true; this.proposalError = '';
    this.actionApi.createExecution(this.selectedProposal.proposalId).subscribe({
      next: detail => { this.proposalSaving = false; this.selectedExecution = detail; this.upsertExecution(detail.execution); this.showToast('Transfer execution created. Reserve stock before dispatch.'); },
      error: error => { this.proposalSaving = false; this.proposalError = this.apiError(error, 'Execution could not be created.'); }
    });
  }

  loadExecution(id: string): void {
    this.fleetbaseTracking = undefined;
    this.actionApi.execution(id).subscribe({
      next: detail => {
        this.selectedExecution = detail;
        if (detail.fleetbaseOrderLink?.status === 'DISPATCHED') this.refreshFleetbaseTracking(false);
      },
      error: error => this.proposalError = this.apiError(error, 'Execution details could not be loaded.')
    });
  }

  transitionExecution(action: 'reserve' | 'dispatch' | 'receive' | 'cancel'): void {
    const detail = this.selectedExecution; if (!detail || this.proposalSaving) return;
    this.proposalSaving = true; this.proposalError = '';
    const id = detail.execution.executionId;
    const request = action === 'reserve' ? this.actionApi.reserveExecution(id, this.executionComment)
      : action === 'dispatch' ? this.actionApi.dispatchExecution(id, this.executionComment)
      : action === 'receive' ? this.actionApi.receiveExecution(id, this.executionComment, this.actualTransportCost, this.actualCarbonKg)
      : this.actionApi.cancelExecution(id, this.executionComment);
    request.subscribe({
      next: updated => {
        this.proposalSaving = false;
        this.selectedExecution = updated;
        this.upsertExecution(updated.execution);
        this.executionComment = '';
        const fleetbaseDispatched = action === 'dispatch' && updated.fleetbaseOrderLink?.status === 'DISPATCHED';
        this.showToast(fleetbaseDispatched ? 'FEFO stock consumed and Fleetbase dispatch confirmed.' : `Execution moved to ${this.proposalStatus(updated.execution.status)}.`);
      },
      error: error => { this.proposalSaving = false; this.proposalError = this.apiError(error, 'Execution status could not be changed.'); }
    });
  }

  prepareFleetbaseOrderLink(): void {
    const detail = this.selectedExecution;
    if (!detail || detail.fleetbaseOrderLink || this.proposalSaving) return;
    this.proposalSaving = true;
    this.proposalError = '';
    this.actionApi.prepareFleetbaseOrderLink(detail.execution.executionId).subscribe({
      next: link => {
        this.proposalSaving = false;
        this.selectedExecution = { ...detail, fleetbaseOrderLink: link };
        this.showToast('Fleetbase order linkage prepared. No Fleetbase order was created or dispatched.');
      },
      error: error => {
        this.proposalSaving = false;
        this.proposalError = this.apiError(error, 'Fleetbase order linkage could not be prepared.');
      }
    });
  }

  createFleetbaseOrder(): void {
    const detail = this.selectedExecution;
    if (!detail?.fleetbaseOrderLink || this.proposalSaving) return;
    this.proposalSaving = true;
    this.proposalError = '';
    this.actionApi.createFleetbaseOrder(detail.execution.executionId).subscribe({
      next: link => {
        this.proposalSaving = false;
        this.selectedExecution = { ...detail, fleetbaseOrderLink: link };
        this.showToast('Fleetbase order created without dispatch. Reserve FEFO stock before dispatching it.');
      },
      error: error => {
        this.proposalSaving = false;
        this.proposalError = this.apiError(error, 'Fleetbase order could not be created.');
        this.loadExecution(detail.execution.executionId);
      }
    });
  }

  refreshFleetbaseTracking(reconcile = false): void {
    const detail = this.selectedExecution;
    if (!detail?.fleetbaseOrderLink?.remoteWritePerformed || this.fleetbaseTrackingLoading) return;
    this.fleetbaseTrackingLoading = true;
    const request = reconcile
      ? this.actionApi.reconcileFleetbase(detail.execution.executionId)
      : this.actionApi.fleetbaseTracking(detail.execution.executionId);
    request.subscribe({
      next: tracking => {
        this.fleetbaseTrackingLoading = false;
        this.fleetbaseTracking = tracking;
        this.loadExecutionLinkOnly(detail.execution.executionId);
        if (reconcile) this.showToast(`Fleetbase reconciliation: ${this.proposalStatus(tracking.reconciliationStatus)}.`);
      },
      error: error => {
        this.fleetbaseTrackingLoading = false;
        this.proposalError = this.apiError(error, 'Fleetbase tracking could not be synchronized.');
      }
    });
  }

  trackingDuration(seconds?: number): string {
    if (seconds === undefined || seconds === null) return 'Not available';
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.ceil((seconds % 3600) / 60);
    return hours ? `${hours}h ${minutes}m` : `${minutes} min`;
  }

  private loadExecutionLinkOnly(id: string): void {
    this.actionApi.execution(id).subscribe({ next: detail => this.selectedExecution = detail });
  }

  executionFor(proposal: ActionProposal): TransferExecution | undefined { return this.transferExecutions.find(item => item.proposalId === proposal.proposalId); }
  private upsertExecution(execution: TransferExecution): void { this.transferExecutions = [execution, ...this.transferExecutions.filter(item => item.executionId !== execution.executionId)]; }

  createPurchaseOrder(): void {
    if (!this.selectedProposal || this.proposalSaving) return;
    this.proposalSaving = true; this.proposalError = '';
    this.actionApi.createPurchaseOrder(this.selectedProposal.proposalId, this.poExpectedDate).subscribe({
      next: detail => { this.proposalSaving = false; this.selectedPurchaseOrder = detail; this.upsertPurchaseOrder(detail.purchaseOrder); this.showToast('Purchase order created from the approved proposal.'); },
      error: error => { this.proposalSaving = false; this.proposalError = this.apiError(error, 'Purchase order could not be created.'); }
    });
  }

  loadPurchaseOrder(id: string): void { this.actionApi.purchaseOrder(id).subscribe({ next: detail => this.selectedPurchaseOrder = detail, error: error => this.proposalError = this.apiError(error, 'Purchase order details could not be loaded.') }); }

  transitionPurchaseOrder(action: 'send' | 'acknowledge' | 'receive' | 'cancel'): void {
    const detail = this.selectedPurchaseOrder; if (!detail || this.proposalSaving) return;
    if (action === 'receive' && (!this.receiptBatchNumber.trim() || !this.receiptExpiryDate || this.receiptQuantity <= 0)) { this.proposalError = 'Receipt quantity, batch number and expiry date are required.'; return; }
    this.proposalSaving = true; this.proposalError = ''; const id = detail.purchaseOrder.purchaseOrderId;
    const request = action === 'send' ? this.actionApi.sendPurchaseOrder(id, this.executionComment)
      : action === 'acknowledge' ? this.actionApi.acknowledgePurchaseOrder(id, this.poAcknowledgement, this.poExpectedDate, this.executionComment)
      : action === 'receive' ? this.actionApi.receivePurchaseOrder(id, { quantity: this.receiptQuantity, batchNumber: this.receiptBatchNumber.trim(), manufactureDate: this.receiptManufactureDate || undefined, expiryDate: this.receiptExpiryDate, storageConditionCode: this.receiptStorage, comment: this.executionComment })
      : this.actionApi.cancelPurchaseOrder(id, this.executionComment);
    request.subscribe({ next: updated => { this.proposalSaving = false; this.selectedPurchaseOrder = updated; this.upsertPurchaseOrder(updated.purchaseOrder); this.executionComment = ''; this.receiptBatchNumber = ''; this.loadReplenishmentPlans(); this.showToast(`Purchase order moved to ${this.proposalStatus(updated.purchaseOrder.status)}.`); }, error: error => { this.proposalSaving = false; this.proposalError = this.apiError(error, 'Purchase-order status could not be changed.'); } });
  }

  private upsertPurchaseOrder(order: PurchaseOrder): void { this.purchaseOrders = [order, ...this.purchaseOrders.filter(item => item.purchaseOrderId !== order.purchaseOrderId)]; }

  transitionProposal(action: 'submit' | 'approve' | 'reject' | 'cancel'): void {
    const proposal = this.selectedProposal;
    if (!proposal || this.proposalSaving) return;
    if (action === 'reject' && !this.reviewComment.trim()) { this.proposalError = 'Enter a rejection reason before rejecting.'; return; }
    this.proposalSaving = true;
    this.proposalError = '';
    const request = action === 'submit' ? this.actionApi.submit(proposal.proposalId, this.reviewComment)
      : action === 'approve' ? this.actionApi.approve(proposal.proposalId, this.reviewComment)
      : action === 'reject' ? this.actionApi.reject(proposal.proposalId, this.reviewComment)
      : this.actionApi.cancel(proposal.proposalId, this.reviewComment);
    request.subscribe({
      next: updated => {
        this.proposalSaving = false;
        this.selectedProposal = updated;
        this.proposals = this.proposals.map(item => item.proposalId === updated.proposalId ? updated : item);
        this.actionApi.history(updated.proposalId).subscribe(history => this.proposalHistory = history);
        this.showToast(`${this.shortProposalId(updated)} moved to ${this.proposalStatus(updated.status)}. No inventory transaction was executed.`);
      },
      error: error => { this.proposalSaving = false; this.proposalError = this.apiError(error, 'The proposal status could not be changed.'); }
    });
  }

  closeProposalDialog(): void { if (!this.proposalSaving) this.proposalDialogOpen = false; }
  shortProposalId(item: ActionProposal): string { return `${item.proposalType === 'TRANSFER' ? 'TRF' : 'PUR'}-${item.proposalId.slice(0, 8).toUpperCase()}`; }
  proposalStatus(status: string): string { return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, char => char.toUpperCase()); }
  isProposalOwner(proposal: ActionProposal): boolean { return this.auth.user()?.id === proposal.createdBy; }

  private emptyProposal(type: ProposalType): ProposalForm {
    return { type, skuId: '', quantity: 1, sourceWarehouseId: '', destinationWarehouseId: '', supplierReference: '', reason: '', recommendationEvidence: '' };
  }

  private emptyOrder(): OrderForm {
    const promised = new Date(Date.now() + 24 * 60 * 60 * 1000);
    promised.setMinutes(promised.getMinutes() - promised.getTimezoneOffset());
    return { customerName: '', customerCity: '', channel: 'B2B Portal', warehouseId: '', skuId: '', quantity: 1, promisedAt: promised.toISOString().slice(0, 16) };
  }

  private mapOrder(value: CustomerOrderView): CustomerOrder {
    return {
      orderId: value.orderId, id: value.orderNumber, customer: value.customerName, city: value.customerCity,
      channel: value.channel, warehouse: value.warehouseName, itemCount: value.itemCount, value: value.totalValue,
      promisedDate: new Date(value.promisedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }),
      fulfillment: value.fulfilmentPercent, status: this.proposalStatus(value.status),
      skuId: value.skuId, skuName: value.skuName, quantity: value.quantity
    };
  }

  private warehouseId(label: string): string {
    const ids: Record<string, string> = { 'Guwahati Central': 'WH-GUWAHATI', 'Shillong Hub': 'WH-SHILLONG', 'Imphal Hub': 'WH-IMPHAL', 'Dimapur DC': 'WH-DIMAPUR', 'Agartala West': 'WH-AGARTALA' };
    return ids[label] ?? label;
  }

  private apiError(error: { error?: unknown; message?: string; status?: number }, fallback: string): string {
    const payload = error?.error;
    if (typeof payload === 'object' && payload !== null) {
      const body = payload as { message?: string; detail?: string; error?: string; errors?: Array<{ defaultMessage?: string }> };
      return body.detail || body.message || body.errors?.[0]?.defaultMessage || body.error || fallback;
    }
    if (typeof payload === 'string' && payload.trim()) {
      try {
        const body = JSON.parse(payload) as { message?: string; detail?: string };
        return body.detail || body.message || payload;
      } catch { return payload; }
    }
    return error?.status ? `${fallback} The server returned HTTP ${error.status}.` : error?.message || fallback;
  }

  private matches(status: string, searchable: string[], locations: string[]): boolean {
    const query = this.searchQuery.trim().toLowerCase();
    const matchesSearch = !query || searchable.some(value => value.toLowerCase().includes(query));
    const matchesStatus = this.statusFilter === 'ALL' || status === this.statusFilter;
    const matchesLocation = this.locationFilter === 'ALL' || locations.includes(this.locationFilter);
    return matchesSearch && matchesStatus && matchesLocation;
  }

  private applyDeliveredImpact(item: RoutePlan): void {
    const destination = item.stops[item.stops.length - 1];
    const record = this.sustainabilityRecords.find(candidate => candidate.location === destination);
    if (!record) return;
    record.emissionsAvoidedKg = Math.round((record.emissionsAvoidedKg + item.co2SavedKg) * 10) / 10;
    record.wasteAvoidedKg += Math.max(12, Math.round(item.loadKg * 0.004));
    record.status = 'On target';
    this.prototype.patchRecord('sustainability', record.location, {
      emissionsAvoidedKg: record.emissionsAvoidedKg,
      wasteAvoidedKg: record.wasteAvoidedKg,
      status: record.status
    }, {
      module: 'Sustainability',
      title: `${destination} impact updated`,
      detail: `${item.co2SavedKg} kg CO₂e savings were realized when ${item.id} was delivered.`,
      tone: 'success'
    });
  }

  private applyStoredPatches<T extends { id: string }>(collection: string, records: T[]): void {
    records.forEach(record => Object.assign(record, this.prototype.recordPatch<T>(collection, record.id)));
  }

  private showToast(message: string): void {
    this.toastMessage = message;
    if (this.toastTimer) window.clearTimeout(this.toastTimer);
    this.toastTimer = window.setTimeout(() => this.toastMessage = '', 3200);
  }
}
