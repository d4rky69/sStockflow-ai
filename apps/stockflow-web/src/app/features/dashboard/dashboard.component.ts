import { CommonModule } from '@angular/common';
import { Component, ElementRef, HostListener, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { DashboardOverview } from '../../core/models/dashboard.models';
import { createPrototypeFoundationData } from '../../core/data/prototype-foundation.data';
import {
  BatchInventoryView,
  FoundationSummary,
  SkuView,
  WarehouseView
} from '../../core/models/foundation.models';
import {
  ImportErrorView,
  ImportJobView,
  ImportMode,
  ImportPackageType
} from '../../core/models/import.models';
import {
  DemandSku,
  DemandSummary,
  DemandTrend,
  InventoryRisk,
  InventoryRiskSummary
} from '../../core/models/intelligence.models';
import { DashboardDataService } from '../../core/services/dashboard-data.service';
import { AuthService } from '../../core/services/auth.service';
import { FoundationDataService } from '../../core/services/foundation-data.service';
import { ImportDataService } from '../../core/services/import-data.service';
import { IntelligenceDataService } from '../../core/services/intelligence-data.service';
import { PrototypeStateService } from '../../core/services/prototype-state.service';
import { CopilotService } from '../../core/services/copilot.service';
import { ForecastGovernanceAlert, ForecastJob, ForecastSchedule, LatestForecastPosition } from '../../core/models/forecast-operations.models';
import { ForecastOperationsService } from '../../core/services/forecast-operations.service';
import { ReplenishmentPlan, TransferRecommendation } from '../../core/models/replenishment.models';
import { ReplenishmentService } from '../../core/services/replenishment.service';
import { AdminView, AdminWorkspaceComponent } from '../admin/admin-workspace.component';
import { OperationsWorkspaceComponent, OperationView } from '../operations/operations-workspace.component';
import { FleetWorkspaceComponent } from '../fleet/fleet-workspace.component';

type ViewId =
  | 'dashboard'
  | 'demand'
  | 'inventory'
  | 'risks'
  | 'recommendations'
  | 'transfers'
  | 'purchase'
  | 'orders'
  | 'returns'
  | 'routes'
  | 'sustainability'
  | 'fleet'
  | 'warehouses'
  | 'products'
  | 'batches'
  | 'users'
  | 'settings'
  | 'integrations'
  | 'activity';

interface NavigationItem {
  label: string;
  icon: string;
  view: ViewId;
}

type TopbarPanel = 'notifications' | 'help' | 'profile' | null;
type NotificationTone = 'critical' | 'warning' | 'info' | 'success';
type MasterDataEditor = 'warehouse' | 'sku' | 'batch' | null;

interface TopbarNotification {
  id: number;
  title: string;
  detail: string;
  time: string;
  view: ViewId;
  tone: NotificationTone;
  read: boolean;
}

interface PrototypeRecommendationView {
  title: string;
  subtitle: string;
  benefit: string;
  tone: 'critical' | 'warning' | 'success';
  target: 'batches' | 'routes';
}

interface DecisionRecommendationView {
  id: string;
  type: 'TRANSFER' | 'PURCHASE';
  skuId: string;
  skuName: string;
  risk: string;
  title: string;
  subtitle: string;
  quantity: number;
  primaryBenefit: number;
  primaryBenefitLabel: string;
  cost: number;
  carbonKg?: number;
  confidence: number;
  asOf: string;
  explanation: string;
  evidence: string[];
  assumptions: string[];
  target: 'transfers' | 'purchase';
}

@Component({
  selector: 'sf-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, OperationsWorkspaceComponent, AdminWorkspaceComponent, FleetWorkspaceComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  data?: DashboardOverview;
  riskSummary?: InventoryRiskSummary;
  inventoryRisks: InventoryRisk[] = [];
  demandSummary?: DemandSummary;
  demandSkus: DemandSku[] = [];
  demandTrend?: DemandTrend;
  forecastJobs: ForecastJob[] = [];
  forecastSchedules: ForecastSchedule[] = [];
  forecastAlerts: ForecastGovernanceAlert[] = [];
  latestForecasts: LatestForecastPosition[] = [];
  forecastOpsBusy = false;
  forecastOpsMessage = '';
  forecastHorizon = 30;
  forecastHistory = 180;
  scheduleName = 'Daily network forecast';
  scheduleCadence = 'DAILY';
  scheduleHour = 2;
  decisionRecommendations: DecisionRecommendationView[] = [];
  decisionRecommendationsLoading = false;
  decisionRecommendationsError = '';
  selectedDecisionRecommendation?: DecisionRecommendationView;

  foundationSummary?: FoundationSummary;
  warehouses: WarehouseView[] = [];
  skus: SkuView[] = [];
  batches: BatchInventoryView[] = [];

  importJobs: ImportJobView[] = [];
  importErrors: ImportErrorView[] = [];
  selectedImportJob?: ImportJobView;
  importResult?: ImportJobView;
  selectedImportFile?: File;
  activeEditor: MasterDataEditor = null;
  warehouseDraft?: WarehouseView;
  skuDraft?: SkuView;
  batchDraft?: BatchInventoryView;
  editorError = '';

  activeView: ViewId = 'dashboard';
  loading = true;
  pageLoading = false;
  importRunning = false;
  error = '';
  pageError = '';
  pageNotice = '';
  importError = '';
  copilotInput = '';
  copilotOpen = false;
  copilotLoading = false;
  private readonly copilotConversationId = `stockflow-${crypto.randomUUID?.() ?? Date.now()}`;
  globalSearch = '';
  sidebarCollapsed = true;
  sidebarHoverExpanded = false;
  isPillHovered = false;
  private pillHoverTimeout?: any;

  @ViewChild('globalSearchInput') globalSearchInput?: ElementRef<HTMLInputElement>;

  activeTopbarPanel: TopbarPanel = null;
  darkMode = localStorage.getItem('stockflowTheme') === 'dark';
  topbarToast = '';
  private topbarToastTimer?: number;

  selectedTenant = localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA';
  selectedWindowDays = 30;
  selectedRiskType = '';
  selectedSeverity = '';
  riskLimit = 100;
  selectedDemandProfile = '';
  selectedFefoFilter = 'ALL';
  selectedWarehouseId = '';
  selectedSkuId = '';
  selectedExpiryFilter = 'ALL';
  selectedImportPackage: ImportPackageType = 'SYNTHETIC_FOUNDATION';
  selectedImportMode: ImportMode = 'VALIDATE_ONLY';
  strictImport = true;
  foundationDataSource: 'LIVE API' | 'DEMO FALLBACK' = 'LIVE API';

  readonly tenants = [
    { id: 'TEN-ACME-PHARMA', label: 'Acme Pharma' },
    { id: 'TEN-FRESH-MART', label: 'Fresh Mart' },
    { id: 'TEN-URBAN-TRADE', label: 'Urban Trade' }
  ];


  readonly notifications: TopbarNotification[] = [
    {
      id: 1,
      title: '16 operational stock risks',
      detail: 'Low-cover positions require replenishment or transfer review.',
      time: 'Live',
      view: 'risks',
      tone: 'critical',
      read: false
    },
    {
      id: 2,
      title: '117 inventory data gaps',
      detail: 'Warehouse-SKU demand positions are missing inventory snapshots.',
      time: 'Live',
      view: 'risks',
      tone: 'warning',
      read: false
    },
    {
      id: 3,
      title: 'Near-expiry batch detected',
      detail: 'One batch requires expiry review within the configured window.',
      time: 'Today',
      view: 'batches',
      tone: 'warning',
      read: false
    },
    {
      id: 4,
      title: '13 demand surges detected',
      detail: 'Review recent demand acceleration across active warehouse-SKU positions.',
      time: 'Today',
      view: 'demand',
      tone: 'info',
      read: false
    },
    {
      id: 5,
      title: 'Warehouse workspace is live',
      detail: 'Capacity, cold-chain readiness and batch footprint are available.',
      time: 'Today',
      view: 'warehouses',
      tone: 'success',
      read: false
    },
    {
      id: 6,
      title: 'Product and SKU workspace is live',
      detail: 'Review safety stock, margins, reorder multiples and FEFO settings.',
      time: 'Today',
      view: 'products',
      tone: 'success',
      read: false
    },
    {
      id: 7,
      title: 'Foundation import completed',
      detail: 'Master-data import history and row-level errors are available.',
      time: 'Recent',
      view: 'integrations',
      tone: 'success',
      read: false
    },
    {
      id: 8,
      title: 'Sales history import completed',
      detail: '178,156 sales-history rows are available for analytics.',
      time: 'Recent',
      view: 'inventory',
      tone: 'success',
      read: false
    }
  ];

  readonly riskTypes = [
    { value: '', label: 'All alert types' },
    { value: 'STOCKOUT_RISK', label: 'Stockout risk' },
    { value: 'SAFETY_STOCK_BREACH', label: 'Safety-stock breach' },
    { value: 'INVENTORY_DATA_GAP', label: 'Inventory data gap' },
    { value: 'NEAR_EXPIRY', label: 'Near expiry' },
    { value: 'EXPIRED_INVENTORY', label: 'Expired inventory' },
    { value: 'EXCESS_INVENTORY', label: 'Excess inventory' },
    { value: 'SLOW_MOVING', label: 'Slow moving' },
    { value: 'DEMAND_SURGE', label: 'Demand surge' }
  ];

  readonly navGroups: { title: string; items: NavigationItem[] }[] = [
    {
      title: '',
      items: [{ label: 'Dashboard', icon: 'assets/nav-icons/icons8-home-48.png', view: 'dashboard' }]
    },
    {
      title: 'INTELLIGENCE',
      items: [
        { label: 'Demand Forecast', icon: 'assets/nav-icons/icons8-graph-50.png', view: 'demand' },
        { label: 'Inventory Analytics', icon: 'assets/nav-icons/icons8-analysis-50.png', view: 'inventory' },
        { label: 'Risk & Alerts', icon: 'assets/nav-icons/icons8-risk-30.png', view: 'risks' },
        { label: 'Recommendations', icon: 'assets/nav-icons/icons8-recommendation-30.png', view: 'recommendations' }
      ]
    },
    {
      title: 'OPERATIONS',
      items: [
        { label: 'Vehicle Fleet', icon: 'assets/nav-icons/icons8-logistics-32-2.png', view: 'fleet' },
        { label: 'Transfers', icon: 'assets/nav-icons/icons8-transfer-30.png', view: 'transfers' },
        { label: 'Route Optimization', icon: 'assets/nav-icons/img.icons8.com.png', view: 'routes' },
        { label: 'Sustainability', icon: 'assets/nav-icons/icons8-recycle-50.png', view: 'sustainability' },
        { label: 'Purchase Planning', icon: 'assets/nav-icons/icons8-timeline-week-50.png', view: 'purchase' },
        { label: 'Orders', icon: 'assets/nav-icons/icons8-product-30.png', view: 'orders' },
        { label: 'Returns', icon: 'assets/nav-icons/icons8-return-box-64.png', view: 'returns' }
      ]
    },
    {
      title: 'INVENTORY',
      items: [
        { label: 'Warehouses', icon: 'assets/nav-icons/icons8-country-house-48.png', view: 'warehouses' },
        { label: 'Products & SKUs', icon: 'assets/nav-icons/icons8-product-30.png', view: 'products' },
        { label: 'Batches', icon: 'assets/nav-icons/icons8-inventory-30.png', view: 'batches' }
      ]
    },
    {
      title: 'ADMIN',
      items: [
        { label: 'Demo Activity', icon: 'assets/nav-icons/icons8-logistics-32-2.png', view: 'activity' },
        { label: 'Users & Roles', icon: 'assets/nav-icons/icons8-user-30.png', view: 'users' },
        { label: 'Settings', icon: 'assets/nav-icons/icons8-settings-50.png', view: 'settings' },
        { label: 'Data Imports', icon: 'assets/nav-icons/icons8-data-protection-30.png', view: 'integrations' }
      ]
    }
  ];

  constructor(
    readonly auth: AuthService,
    private readonly dashboardData: DashboardDataService,
    private readonly intelligenceData: IntelligenceDataService,
    private readonly foundationData: FoundationDataService,
    private readonly importData: ImportDataService,
    readonly prototype: PrototypeStateService,
    private readonly copilot: CopilotService,
    private readonly forecastOps: ForecastOperationsService,
    private readonly replenishment: ReplenishmentService
  ) {}

  ngOnInit(): void {
    this.restoreNotificationState();
    this.applyThemePreference();
    this.loadDashboard();
    this.loadDashboardWarehouses();
  }

  selectView(view: ViewId): void {
    this.closeTopbarPanels();
    this.activeView = view;
    this.pageError = '';
    this.pageNotice = '';
    this.importError = '';

    this.isPillHovered = false;
    if (this.pillHoverTimeout) {
      clearTimeout(this.pillHoverTimeout);
      this.pillHoverTimeout = undefined;
    }

    if (window.innerWidth <= 900) {
      this.sidebarCollapsed = true;
    }

    if (view === 'dashboard' || view === 'recommendations') {
      if (!this.data) this.loadDashboard();
      if (view === 'recommendations') {
        this.loadPrototypeRecommendationData();
        this.loadDecisionRecommendations();
      }
      return;
    }

    if (view === 'demand') {
      this.loadDemandWorkspace();
      return;
    }

    if (view === 'inventory') {
      this.loadInventoryWorkspace();
      return;
    }

    if (view === 'risks') {
      this.loadRiskWorkspace();
      return;
    }

    if (view === 'warehouses') {
      this.loadWarehouseWorkspace();
      return;
    }

    if (view === 'products') {
      this.loadProductWorkspace();
      return;
    }

    if (view === 'batches') {
      this.loadBatchWorkspace();
      return;
    }

    if (view === 'integrations') {
      this.loadImportWorkspace();
      return;
    }

    if (this.activeOperationView()) {
      return;
    }

    if (this.activeAdminView()) {
      return;
    }
  }

  onTenantChange(): void {
    this.cancelEditor();
    localStorage.setItem('stockflowTenantId', this.selectedTenant);
    this.data = undefined;
    this.riskSummary = undefined;
    this.inventoryRisks = [];
    this.demandSummary = undefined;
    this.demandSkus = [];
    this.demandTrend = undefined;
    this.foundationSummary = undefined;
    this.warehouses = [];
    this.skus = [];
    this.batches = [];
    this.importJobs = [];
    this.importErrors = [];
    this.selectedImportJob = undefined;
    this.importResult = undefined;
    this.globalSearch = '';
    this.selectedWarehouseId = '';
    this.selectedSkuId = '';

    this.loadDashboard(() => {
      if (this.activeView !== 'dashboard' && this.activeView !== 'recommendations') {
        this.selectView(this.activeView);
      }
    });
    this.loadDashboardWarehouses();
  }

  onDemandWindowChange(): void {
    this.loadDemandWorkspace();
  }

  onDashboardWarehouseChange(): void {
    this.loadDashboard();
  }

  applyRiskFilters(): void {
    this.pageLoading = true;
    this.pageError = '';
    this.intelligenceData.risks(this.selectedRiskType, this.selectedSeverity, this.riskLimit)
      .pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: risks => this.inventoryRisks = risks,
        error: () => this.pageError = 'Risk records could not be loaded from the API.'
      });
  }

  toggleSidebar(): void {
    this.sidebarHoverExpanded = false;
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }

  expandSidebarOnHover(): void {
    if (this.sidebarCollapsed && window.innerWidth > 900) {
      this.sidebarHoverExpanded = true;
    }
  }

  collapseSidebarAfterHover(): void {
    this.sidebarHoverExpanded = false;
  }


  @HostListener('document:click')
  onDocumentClick(): void {
    this.closeTopbarPanels();
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.closeTopbarPanels();
      this.closeCopilot();
      return;
    }

    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      this.closeTopbarPanels();
      this.globalSearchInput?.nativeElement.focus();
      this.globalSearchInput?.nativeElement.select();
    }
  }

  toggleTopbarPanel(panel: Exclude<TopbarPanel, null>, event: MouseEvent): void {
    event.stopPropagation();
    this.activeTopbarPanel = this.activeTopbarPanel === panel ? null : panel;
  }

  closeTopbarPanels(): void {
    this.activeTopbarPanel = null;
  }

  unreadNotificationCount(): number {
    return this.notifications.filter(item => !item.read).length;
  }

  openNotification(item: TopbarNotification): void {
    item.read = true;
    this.saveNotificationState();
    this.selectView(item.view);
    this.showTopbarToast(`Opened: ${item.title}`);
  }

  markAllNotificationsRead(): void {
    this.notifications.forEach(item => item.read = true);
    this.saveNotificationState();
    this.showTopbarToast('All notifications marked as read.');
  }

  toggleTheme(): void {
    this.closeTopbarPanels();
    this.darkMode = !this.darkMode;
    localStorage.setItem('stockflowTheme', this.darkMode ? 'dark' : 'light');
    this.applyThemePreference();
    this.showTopbarToast(`${this.darkMode ? 'Dark' : 'Light'} theme enabled.`);
  }

  currentTenantLabel(): string {
    return this.tenants.find(item => item.id === this.selectedTenant)?.label ?? this.selectedTenant;
  }

  profileInitials(): string {
    const name = this.auth.displayName();
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(part => part.charAt(0).toUpperCase())
      .join('') || 'SF';
  }

  profileName(): string {
    return this.auth.displayName();
  }

  profileEmail(): string {
    return this.auth.user()?.email ?? '';
  }

  async signOut(): Promise<void> {
    this.closeTopbarPanels();
    const error = await this.auth.signOut();
    if (error) this.showTopbarToast(error);
  }

  openHelpDestination(view: ViewId): void {
    this.selectView(view);
    this.showTopbarToast(`Opened ${this.pageTitle()}.`);
  }

  copySupportDetails(): void {
    const details = [
      'StockFlow AI support details',
      `Tenant: ${this.selectedTenant}`,
      `Workspace: ${this.pageTitle()}`,
      `URL: ${window.location.href}`,
      `Theme: ${this.darkMode ? 'dark' : 'light'}`
    ].join('\n');

    if (!navigator.clipboard) {
      this.showTopbarToast('Clipboard access is unavailable in this browser.');
      return;
    }

    navigator.clipboard.writeText(details)
      .then(() => this.showTopbarToast('Support details copied.'))
      .catch(() => this.showTopbarToast('Support details could not be copied.'));
  }

  resetDemoSession(): void {
    localStorage.removeItem('stockflowTenantId');
    this.selectedTenant = 'TEN-ACME-PHARMA';
    this.globalSearch = '';
    this.selectedWarehouseId = '';
    this.selectedSkuId = '';
    this.selectedRiskType = '';
    this.selectedSeverity = '';
    this.activeView = 'dashboard';
    this.closeTopbarPanels();
    this.onTenantChange();
    this.showTopbarToast('Demo session reset to Acme Pharma.');
  }

  onPillMouseEnter(): void {
    this.isPillHovered = true;
    if (this.pillHoverTimeout) {
      clearTimeout(this.pillHoverTimeout);
      this.pillHoverTimeout = undefined;
    }
  }

  onPillMouseLeave(): void {
    this.pillHoverTimeout = setTimeout(() => {
      this.isPillHovered = false;
    }, 5000);
  }

  toggleCopilot(event?: MouseEvent): void {
    event?.stopPropagation();
    this.copilotOpen = !this.copilotOpen;
    if (this.copilotOpen) this.closeTopbarPanels();
  }

  closeCopilot(event?: MouseEvent): void {
    event?.stopPropagation();
    this.copilotOpen = false;
  }

  useCopilotSuggestion(message: string): void {
    this.copilotInput = message;
    this.sendCopilotMessage();
  }

  sendCopilotMessage(): void {
    const message = this.copilotInput.trim();
    if (!message || !this.data || this.copilotLoading) return;
    this.data.copilotMessages.push({ role: 'user', text: message, timestamp: 'Now' });
    this.copilotInput = '';
    this.copilotLoading = true;
    this.copilot.chat({
      conversationId: this.copilotConversationId,
      message,
      currentWorkspace: this.activeView,
      selectedWarehouseId: this.selectedWarehouseId || undefined,
      selectedSkuId: this.selectedSkuId || undefined
    }).pipe(finalize(() => this.copilotLoading = false)).subscribe({
      next: response => this.data?.copilotMessages.push({
        role: 'assistant',
        text: response.answer,
        timestamp: response.evidence?.[0]?.freshness === 'CURRENT' ? 'Just now · verified' : 'Just now'
      }),
      error: () => this.data?.copilotMessages.push({
        role: 'assistant',
        text: 'I could not reach the StockFlow Copilot service. Please check your connection and try again. No inventory value was inferred.',
        timestamp: 'Connection issue'
      })
    });
  }

  polyline(values: number[], width = 360, height = 160, padding = 12): string {
    if (!values.length) return '';
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = Math.max(max - min, 1);
    return values.map((value, index) => {
      const x = padding + index * (width - padding * 2) / Math.max(values.length - 1, 1);
      const y = height - padding - ((value - min) / span) * (height - padding * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  }

  forecastPoints(): string {
    return this.data ? this.polyline(this.data.demandForecast.forecast) : '';
  }

  actualPoints(): string {
    return this.data ? this.polyline(this.data.demandForecast.actual) : '';
  }

  inventoryTrendPoints(): string {
    return this.data ? this.polyline(this.data.inventoryTrend.values, 260, 160) : '';
  }

  demandActualPoints(): string {
    return this.demandTrend ? this.polyline(this.demandTrend.actual, 900, 260, 20) : '';
  }

  demandForecastPoints(): string {
    return this.demandTrend ? this.polyline(this.demandTrend.forecast, 900, 260, 20) : '';
  }

  riskDonutStyle(): string {
    if (!this.data) return '#e5e7eb';
    let start = 0;
    const segments = this.data.riskBreakdown.map(item => {
      const end = start + item.percentage * 3.6;
      const value = `${item.color} ${start}deg ${end}deg`;
      start = end;
      return value;
    });
    return `conic-gradient(${segments.join(', ')})`;
  }

  riskTypeLabel(type: string): string {
    return type.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, value => value.toUpperCase());
  }

  pageTitle(): string {
    const titles: Record<ViewId, string> = {
      dashboard: 'Dashboard',
      demand: 'Demand Forecast',
      inventory: 'Inventory Analytics',
      risks: 'Risk & Alerts',
      recommendations: 'Recommendations',
      transfers: 'Transfers',
      purchase: 'Purchase Planning',
      orders: 'Orders',
      returns: 'Returns',
      routes: 'Route Optimization',
      sustainability: 'Carbon & Waste Impact',
      fleet: 'Vehicle Fleet',
      warehouses: 'Warehouses',
      products: 'Products & SKUs',
      batches: 'Batch Inventory',
      users: 'Users & Roles',
      settings: 'Settings',
      integrations: 'Data Imports',
      activity: 'Demo Activity'
    };
    return titles[this.activeView];
  }

  operationalRiskCount(): number {
    if (!this.riskSummary) return 0;
    return this.riskSummary.stockoutRiskCount
      + this.riskSummary.safetyStockBreachCount
      + this.riskSummary.nearExpiryCount
      + this.riskSummary.expiredCount
      + this.riskSummary.excessInventoryCount
      + this.riskSummary.slowMovingCount
      + this.riskSummary.demandSurgeCount;
  }

  isImplementedView(): boolean {
    return [
      'dashboard',
      'demand',
      'inventory',
      'risks',
      'recommendations',
      'transfers',
      'purchase',
      'orders',
      'returns',
      'routes',
      'sustainability',
      'fleet',
      'warehouses',
      'products',
      'batches',
      'users',
      'settings',
      'integrations',
      'activity'
    ].includes(this.activeView);
  }

  activeOperationView(): OperationView | null {
    return ['transfers', 'purchase', 'orders', 'returns', 'routes', 'sustainability'].includes(this.activeView)
      ? this.activeView as OperationView
      : null;
  }

  activeAdminView(): AdminView | null {
    return ['users', 'settings'].includes(this.activeView)
      ? this.activeView as AdminView
      : null;
  }

  resetPrototype(): void {
    const confirmed = window.confirm('Reset all locally saved prototype changes and restore the original demo data?');
    if (!confirmed) return;
    this.prototype.reset();
    window.location.reload();
  }

  prototypeCollection(name: 'warehouses' | 'skus' | 'batches'): string {
    return `${this.selectedTenant}:${name}`;
  }

  startWarehouseEdit(warehouse: WarehouseView): void {
    this.activeEditor = 'warehouse';
    this.warehouseDraft = { ...warehouse };
    this.editorError = '';
  }

  startSkuEdit(sku: SkuView): void {
    this.activeEditor = 'sku';
    this.skuDraft = { ...sku };
    this.editorError = '';
  }

  startBatchEdit(batch: BatchInventoryView): void {
    this.activeEditor = 'batch';
    this.batchDraft = { ...batch };
    this.editorError = '';
  }

  cancelEditor(): void {
    this.activeEditor = null;
    this.warehouseDraft = undefined;
    this.skuDraft = undefined;
    this.batchDraft = undefined;
    this.editorError = '';
  }

  saveWarehouseEdit(): void {
    const draft = this.warehouseDraft;
    if (!draft) return;
    draft.warehouseName = draft.warehouseName.trim();
    draft.city = draft.city.trim();
    draft.state = draft.state.trim();
    draft.capacityUnits = Number(draft.capacityUnits);
    if (!draft.warehouseName || !draft.city || !draft.state || !Number.isFinite(draft.capacityUnits) || draft.capacityUnits <= 0) {
      this.editorError = 'Enter a warehouse name, city, state and a capacity greater than zero.';
      return;
    }
    const record = this.warehouses.find(item => item.warehouseId === draft.warehouseId);
    if (record) Object.assign(record, draft);
    this.prototype.patchRecord(this.prototypeCollection('warehouses'), draft.warehouseId, { ...draft }, {
      module: 'Warehouses',
      title: `${draft.warehouseName} updated`,
      detail: `Capacity is ${draft.capacityUnits.toLocaleString()} units; cold-chain support is ${draft.coldChainAvailable ? 'enabled' : 'disabled'}.`,
      tone: 'success'
    });
    this.showTopbarToast(`${draft.warehouseName} saved locally.`);
    this.cancelEditor();
  }

  saveSkuEdit(): void {
    const draft = this.skuDraft;
    if (!draft) return;
    draft.skuName = draft.skuName.trim();
    draft.unitCost = Number(draft.unitCost);
    draft.sellingPrice = Number(draft.sellingPrice);
    draft.minimumSafetyStock = Number(draft.minimumSafetyStock);
    draft.reorderMultiple = Number(draft.reorderMultiple);
    draft.defaultShelfLifeDays = draft.defaultShelfLifeDays === null ? null : Number(draft.defaultShelfLifeDays);
    const numericValues = [draft.unitCost, draft.sellingPrice, draft.minimumSafetyStock, draft.reorderMultiple];
    if (!draft.skuName || numericValues.some(value => !Number.isFinite(value) || value < 0) || draft.reorderMultiple <= 0) {
      this.editorError = 'Enter a name and valid non-negative pricing and stock values. Reorder multiple must exceed zero.';
      return;
    }
    if (draft.defaultShelfLifeDays !== null && (!Number.isFinite(draft.defaultShelfLifeDays) || draft.defaultShelfLifeDays <= 0)) {
      this.editorError = 'Shelf life must be empty or greater than zero days.';
      return;
    }
    const record = this.skus.find(item => item.skuId === draft.skuId);
    if (record) Object.assign(record, draft);
    this.prototype.patchRecord(this.prototypeCollection('skus'), draft.skuId, { ...draft }, {
      module: 'Products & SKUs',
      title: `${draft.skuName} policy updated`,
      detail: `Safety stock is ${draft.minimumSafetyStock.toLocaleString()} units with a reorder multiple of ${draft.reorderMultiple.toLocaleString()}.`,
      tone: 'success'
    });
    this.showTopbarToast(`${draft.skuId} saved locally.`);
    this.cancelEditor();
  }

  saveBatchEdit(): void {
    const draft = this.batchDraft;
    if (!draft) return;
    draft.availableQuantity = Number(draft.availableQuantity);
    draft.reservedQuantity = Number(draft.reservedQuantity);
    draft.blockedQuantity = Number(draft.blockedQuantity);
    const quantities = [draft.availableQuantity, draft.reservedQuantity, draft.blockedQuantity];
    if (quantities.some(value => !Number.isFinite(value) || value < 0)) {
      this.editorError = 'Inventory quantities must be valid numbers greater than or equal to zero.';
      return;
    }
    if (draft.reservedQuantity + draft.blockedQuantity > draft.availableQuantity) {
      this.editorError = 'Reserved plus blocked quantity cannot exceed available quantity.';
      return;
    }
    draft.usableQuantity = draft.availableQuantity - draft.reservedQuantity - draft.blockedQuantity;
    draft.storageConditionCode = draft.storageConditionCode.trim() || 'AMBIENT';
    const record = this.batches.find(item => item.batchInventoryId === draft.batchInventoryId);
    if (record) Object.assign(record, draft);
    const risk = this.batchOperationalStatus(draft);
    this.prototype.patchRecord(this.prototypeCollection('batches'), draft.batchInventoryId, { ...draft }, {
      module: 'Batch Inventory',
      title: `${draft.batchNumber} inventory adjusted`,
      detail: `${draft.usableQuantity.toLocaleString()} usable units remain at ${draft.warehouseId}. Resulting signal: ${risk}.`,
      tone: risk === 'Stockout' || risk === 'Expired' ? 'critical' : risk === 'Below safety stock' || risk === 'Near expiry' ? 'warning' : 'success'
    });
    this.showTopbarToast(`${draft.batchNumber} saved; totals and recommendations were recalculated.`);
    this.cancelEditor();
  }

  prototypeChangedCount(collection: 'warehouses' | 'skus' | 'batches'): number {
    const records = collection === 'warehouses' ? this.warehouses : collection === 'skus' ? this.skus : this.batches;
    const idOf = (record: WarehouseView | SkuView | BatchInventoryView): string => {
      if ('warehouseId' in record && !('batchInventoryId' in record)) return record.warehouseId;
      if ('skuId' in record && !('batchInventoryId' in record)) return record.skuId;
      return (record as BatchInventoryView).batchInventoryId;
    };
    return records.filter(record => this.prototype.isChanged(this.prototypeCollection(collection), idOf(record))).length;
  }

  batchOperationalStatus(batch: BatchInventoryView): string {
    const expiryStatus = this.batchStatus(batch);
    if (expiryStatus === 'Expired' || expiryStatus === 'Near expiry') return expiryStatus;
    if (batch.usableQuantity <= 0) return 'Stockout';
    const safetyStock = this.skus.find(item => item.skuId === batch.skuId)?.minimumSafetyStock ?? 0;
    if (batch.usableQuantity < safetyStock) return 'Below safety stock';
    return 'Healthy';
  }

  operationalStatusClass(batch: BatchInventoryView): string {
    return this.batchOperationalStatus(batch).toLowerCase().replaceAll(' ', '-');
  }

  prototypeRecommendations(): PrototypeRecommendationView[] {
    const recommendations: PrototypeRecommendationView[] = [];
    for (const batch of this.batches) {
      if (!this.prototype.isChanged(this.prototypeCollection('batches'), batch.batchInventoryId)) continue;
      const sku = this.skus.find(item => item.skuId === batch.skuId);
      const status = this.batchOperationalStatus(batch);
      if (status === 'Stockout' || status === 'Below safety stock') {
        const target = sku?.minimumSafetyStock ?? 0;
        const gap = Math.max(target - batch.usableQuantity, sku?.reorderMultiple ?? 1);
        recommendations.push({
          title: `Replenish ${sku?.skuName ?? batch.skuId}`,
          subtitle: `${batch.warehouseId} has ${batch.usableQuantity.toLocaleString()} usable units against ${target.toLocaleString()} safety stock.`,
          benefit: `${gap.toLocaleString()} unit gap`,
          tone: status === 'Stockout' ? 'critical' : 'warning',
          target: 'batches'
        });
      } else if (status === 'Expired' || status === 'Near expiry') {
        recommendations.push({
          title: `${status === 'Expired' ? 'Block' : 'Reallocate'} batch ${batch.batchNumber}`,
          subtitle: `${batch.usableQuantity.toLocaleString()} units at ${batch.warehouseId} require ${status === 'Expired' ? 'quality control' : 'FEFO transfer review'}.`,
          benefit: `${this.daysToExpiry(batch) ?? 0} days`,
          tone: status === 'Expired' ? 'critical' : 'warning',
          target: status === 'Expired' ? 'batches' : 'routes'
        });
      } else {
        recommendations.push({
          title: `${batch.batchNumber} returned to healthy cover`,
          subtitle: `${batch.usableQuantity.toLocaleString()} usable units now satisfy the configured inventory policy at ${batch.warehouseId}.`,
          benefit: 'Risk reduced',
          tone: 'success',
          target: 'batches'
        });
      }
    }
    return recommendations.slice(0, 6);
  }

  filteredWarehouses(): WarehouseView[] {
    const query = this.globalSearch.trim().toLowerCase();
    if (!query) return this.warehouses;
    return this.warehouses.filter(item =>
      [item.warehouseId, item.warehouseName, item.city, item.state, item.country]
        .some(value => value.toLowerCase().includes(query))
    );
  }

  filteredSkus(): SkuView[] {
    const query = this.globalSearch.trim().toLowerCase();
    return this.skus.filter(item => {
      const matchesSearch = !query || [item.skuId, item.productId, item.skuName, item.demandProfile]
        .some(value => value.toLowerCase().includes(query));
      const matchesProfile = !this.selectedDemandProfile || item.demandProfile === this.selectedDemandProfile;
      const matchesFefo = this.selectedFefoFilter === 'ALL'
        || (this.selectedFefoFilter === 'YES' && item.fefoRequired)
        || (this.selectedFefoFilter === 'NO' && !item.fefoRequired);
      return matchesSearch && matchesProfile && matchesFefo;
    });
  }

  filteredBatches(): BatchInventoryView[] {
    const query = this.globalSearch.trim().toLowerCase();
    return this.batches.filter(item => {
      const matchesSearch = !query || [item.batchNumber, item.skuId, item.warehouseId, item.storageConditionCode]
        .some(value => value.toLowerCase().includes(query));
      const matchesWarehouse = !this.selectedWarehouseId || item.warehouseId === this.selectedWarehouseId;
      const matchesSku = !this.selectedSkuId || item.skuId === this.selectedSkuId;
      const status = this.batchStatus(item);
      const matchesExpiry = this.selectedExpiryFilter === 'ALL'
        || (this.selectedExpiryFilter === 'EXPIRING' && status === 'Near expiry')
        || (this.selectedExpiryFilter === 'EXPIRED' && status === 'Expired')
        || (this.selectedExpiryFilter === 'HEALTHY' && status === 'Healthy')
        || (this.selectedExpiryFilter === 'NO_EXPIRY' && status === 'No expiry');
      return matchesSearch && matchesWarehouse && matchesSku && matchesExpiry;
    });
  }

  demandProfiles(): string[] {
    return [...new Set(this.skus.map(item => item.demandProfile))].sort();
  }

  totalWarehouseCapacity(): number {
    return this.warehouses.reduce((sum, item) => sum + item.capacityUnits, 0);
  }

  coldChainWarehouseCount(): number {
    return this.warehouses.filter(item => item.coldChainAvailable).length;
  }

  warehouseCityCount(): number {
    return new Set(this.warehouses.map(item => item.city)).size;
  }

  warehouseBatchCount(warehouseId: string): number {
    return this.batches.filter(item => item.warehouseId === warehouseId).length;
  }

  warehouseUsableQuantity(warehouseId: string): number {
    return this.batches
      .filter(item => item.warehouseId === warehouseId)
      .reduce((sum, item) => sum + item.usableQuantity, 0);
  }

  warehouseInventoryValue(warehouseId: string): number {
    return this.batches
      .filter(item => item.warehouseId === warehouseId)
      .reduce((sum, item) => sum + item.usableQuantity * item.unitCost, 0);
  }

  averageSkuMarginPercent(): number {
    if (!this.skus.length) return 0;
    const total = this.skus.reduce((sum, item) => sum + this.skuMarginPercent(item), 0);
    return total / this.skus.length;
  }

  skuMarginPercent(item: SkuView): number {
    if (item.sellingPrice <= 0) return 0;
    return ((item.sellingPrice - item.unitCost) / item.sellingPrice) * 100;
  }

  fefoSkuCount(): number {
    return this.skus.filter(item => item.fefoRequired).length;
  }

  totalMinimumSafetyStock(): number {
    return this.skus.reduce((sum, item) => sum + item.minimumSafetyStock, 0);
  }

  totalBatchUsableQuantity(): number {
    return this.filteredBatches().reduce((sum, item) => sum + item.usableQuantity, 0);
  }

  totalBatchReservedQuantity(): number {
    return this.filteredBatches().reduce((sum, item) => sum + item.reservedQuantity, 0);
  }

  totalBatchBlockedQuantity(): number {
    return this.filteredBatches().reduce((sum, item) => sum + item.blockedQuantity, 0);
  }

  totalBatchInventoryValue(): number {
    return this.filteredBatches().reduce((sum, item) => sum + item.usableQuantity * item.unitCost, 0);
  }

  expiringBatchCount(): number {
    return this.filteredBatches().filter(item => ['Near expiry', 'Expired'].includes(this.batchStatus(item))).length;
  }

  batchStatus(item: BatchInventoryView): string {
    const days = this.daysToExpiry(item);
    if (days === null) return 'No expiry';
    if (days < 0) return 'Expired';
    if (days <= 60) return 'Near expiry';
    return 'Healthy';
  }

  batchStatusClass(item: BatchInventoryView): string {
    const status = this.batchStatus(item);
    if (status === 'Expired') return 'expired';
    if (status === 'Near expiry') return 'expiring';
    if (status === 'Healthy') return 'healthy';
    return 'neutral';
  }

  daysToExpiry(item: BatchInventoryView): number | null {
    if (!item.expiryDate) return null;
    const reference = new Date(`${item.snapshotDate}T00:00:00Z`).getTime();
    const expiry = new Date(`${item.expiryDate}T00:00:00Z`).getTime();
    return Math.floor((expiry - reference) / 86_400_000);
  }

  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedImportFile = input.files?.[0];
    this.importResult = undefined;
    this.importError = '';
  }

  runImport(): void {
    if (!this.selectedImportFile) {
      this.importError = 'Select a ZIP package before running the import.';
      return;
    }

    this.importRunning = true;
    this.importError = '';
    this.importResult = undefined;

    this.importData.upload(
      this.selectedImportPackage,
      this.selectedImportFile,
      this.selectedImportMode,
      this.strictImport
    ).pipe(finalize(() => this.importRunning = false))
      .subscribe({
        next: result => {
          this.importResult = result;
          this.importJobs = [result, ...this.importJobs.filter(item => item.importJobId !== result.importJobId)];
        },
        error: () => this.importError = 'The import request failed. Check the selected package and Cloud Run logs.'
      });
  }

  inspectImportJob(job: ImportJobView): void {
    this.selectedImportJob = job;
    this.importErrors = [];
    this.importError = '';
    this.importData.errors(job.importJobId).subscribe({
      next: errors => this.importErrors = errors,
      error: () => this.importError = 'Import errors could not be loaded.'
    });
  }

  importStatusClass(status: string): string {
    return status.toLowerCase().replaceAll('_', '-');
  }

  private restoreNotificationState(): void {
    try {
      const savedIds = JSON.parse(localStorage.getItem('stockflowReadNotifications') ?? '[]') as number[];
      const readIds = new Set(savedIds);
      this.notifications.forEach(item => item.read = readIds.has(item.id));
    } catch {
      localStorage.removeItem('stockflowReadNotifications');
    }
  }

  private saveNotificationState(): void {
    const readIds = this.notifications.filter(item => item.read).map(item => item.id);
    localStorage.setItem('stockflowReadNotifications', JSON.stringify(readIds));
  }

  private applyThemePreference(): void {
    document.documentElement.style.colorScheme = this.darkMode ? 'dark' : 'light';
    document.body.style.backgroundColor = this.darkMode ? '#0d1422' : '';
  }

  private showTopbarToast(message: string): void {
    this.topbarToast = message;
    if (this.topbarToastTimer) window.clearTimeout(this.topbarToastTimer);
    this.topbarToastTimer = window.setTimeout(() => this.topbarToast = '', 2600);
  }

  private loadDashboard(afterLoad?: () => void): void {
    this.loading = true;
    this.error = '';
    this.dashboardData.loadOverview(this.selectedWarehouseId)
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: data => {
          this.data = data;
          afterLoad?.();
        },
        error: () => this.error = 'The dashboard data could not be loaded.'
      });
  }

  private loadDashboardWarehouses(): void {
    this.foundationData.warehouses().subscribe({
      next: warehouses => {
        this.foundationDataSource = 'LIVE API';
        this.warehouses = this.applyPrototypePatches(
          this.prototypeCollection('warehouses'),
          warehouses,
          item => item.warehouseId
        );
      },
      error: () => {
        const fallback = createPrototypeFoundationData(this.selectedTenant, this.currentTenantLabel());
        this.foundationDataSource = 'DEMO FALLBACK';
        this.warehouses = this.applyPrototypePatches(
          this.prototypeCollection('warehouses'),
          fallback.warehouses,
          item => item.warehouseId
        );
      }
    });
  }

  private loadDemandWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    forkJoin({
      summary: this.intelligenceData.demandSummary(this.selectedWindowDays),
      skus: this.intelligenceData.demandSkus(this.selectedWindowDays, 50),
      trend: this.intelligenceData.demandTrend(16),
      forecasts: this.forecastOps.latest(50)
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.demandSummary = result.summary;
          this.demandSkus = result.skus;
          this.demandTrend = result.trend;
          this.latestForecasts = result.forecasts;
        },
        error: () => this.pageError = 'Demand analytics could not be loaded from the API.'
      });
    this.loadForecastOperations();
  }

  loadDecisionRecommendations(): void {
    this.decisionRecommendationsLoading = true;
    this.decisionRecommendationsError = '';
    forkJoin({
      purchases: this.replenishment.plans(30),
      transfers: this.replenishment.transferRecommendations(30)
    }).pipe(finalize(() => this.decisionRecommendationsLoading = false)).subscribe({
      next: ({ purchases, transfers }) => {
        const transferCards = transfers.recommendations.map(item => this.transferDecision(item));
        const transferPositions = new Set(transfers.recommendations.map(item => `${item.destinationWarehouseId}:${item.skuId}`));
        const purchaseCards = purchases.plans
          .filter(item => item.recommendedQuantity > 0 && !transferPositions.has(`${item.warehouseId}:${item.skuId}`))
          .map(item => this.purchaseDecision(item));
        this.decisionRecommendations = [...transferCards, ...purchaseCards]
          .sort((a, b) => this.riskRank(a.risk) - this.riskRank(b.risk) || b.primaryBenefit - a.primaryBenefit)
          .slice(0, 50);
      },
      error: error => {
        this.decisionRecommendations = [];
        this.decisionRecommendationsError = error?.error?.message || error?.error?.detail || 'Live decision recommendations could not be loaded.';
      }
    });
  }

  reviewDecisionRecommendation(item: DecisionRecommendationView): void {
    this.selectedDecisionRecommendation = item;
  }

  closeDecisionRecommendation(): void {
    this.selectedDecisionRecommendation = undefined;
  }

  decisionTypeCount(type: 'TRANSFER' | 'PURCHASE'): number {
    return this.decisionRecommendations.filter(item => item.type === type).length;
  }

  continueDecisionRecommendation(item: DecisionRecommendationView): void {
    this.selectedDecisionRecommendation = undefined;
    this.selectView(item.target);
    this.showTopbarToast(`${item.type === 'TRANSFER' ? 'Transfer' : 'Purchase'} planning opened for ${item.skuName}. Create a proposal to begin the approval workflow.`);
  }

  private transferDecision(item: TransferRecommendation): DecisionRecommendationView {
    return {
      id: item.recommendationId, type: 'TRANSFER', skuId: item.skuId, skuName: item.skuName, risk: item.risk,
      title: `Transfer ${item.recommendedQuantity.toLocaleString()} units from ${item.sourceWarehouseName}`,
      subtitle: `Rebalance into ${item.destinationWarehouseName} before purchasing additional inventory.`,
      quantity: item.recommendedQuantity, primaryBenefit: item.estimatedSavings, primaryBenefitLabel: 'Estimated savings',
      cost: item.estimatedTransferCost, carbonKg: item.estimatedCarbonKgCo2e, confidence: item.confidencePercent,
      asOf: item.asOfDate, explanation: item.explanation,
      evidence: [
        `Source after transfer: ${item.sourceUsableAfter.toLocaleString()} units; safety stock: ${item.sourceSafetyStock.toLocaleString()}.`,
        `Destination before transfer: ${item.destinationUsableBefore.toLocaleString()} units; target: ${item.destinationTargetStock.toLocaleString()}.`,
        `Purchase alternative: INR ${item.estimatedPurchaseCost.toLocaleString('en-IN')}; transfer: INR ${item.estimatedTransferCost.toLocaleString('en-IN')}.`,
        `${item.distanceKm.toLocaleString()} km, ${item.trips} trip(s), ${item.vehicleCapacityUnits.toLocaleString()}-unit vehicle capacity.`
      ], assumptions: item.assumptions, target: 'transfers'
    };
  }

  private purchaseDecision(item: ReplenishmentPlan): DecisionRecommendationView {
    return {
      id: item.recommendationId, type: 'PURCHASE', skuId: item.skuId, skuName: item.skuName, risk: item.risk,
      title: `Purchase ${item.recommendedQuantity.toLocaleString()} units from ${item.supplierName}`,
      subtitle: `Replenish ${item.warehouseName} by ${item.needBy}; no eligible transfer currently covers this position.`,
      quantity: item.recommendedQuantity, primaryBenefit: item.plannedValue, primaryBenefitLabel: 'Planned commitment',
      cost: item.plannedValue, confidence: item.confidencePercent, asOf: item.asOfDate, explanation: item.explanation,
      evidence: [
        `Usable: ${item.usableQuantity.toLocaleString()} units; safety stock: ${item.safetyStock.toLocaleString()}; target: ${item.targetStock.toLocaleString()}.`,
        `Demand: ${item.averageDailyDemand.toLocaleString()} units/day from ${item.demandSource.replaceAll('_', ' ').toLowerCase()}.`,
        `Lead time: ${item.leadTimeDays} days; open supply already deducted: ${item.openPurchaseQuantity.toLocaleString()} units.`,
        `Reorder multiple: ${item.reorderMultiple.toLocaleString()}; unit cost: INR ${item.unitCost.toLocaleString('en-IN')}.`
      ], assumptions: ['Latest persisted forecast, with 30-day sales fallback', 'Preferred active supplier', 'Human approval required before order creation'], target: 'purchase'
    };
  }

  private riskRank(risk: string): number {
    return risk === 'CRITICAL' ? 0 : risk === 'HIGH' ? 1 : 2;
  }

  loadForecastOperations(): void {
    forkJoin({jobs:this.forecastOps.jobs(),schedules:this.forecastOps.schedules(),alerts:this.forecastOps.alerts()}).subscribe({
      next:r=>{this.forecastJobs=r.jobs;this.forecastSchedules=r.schedules;this.forecastAlerts=r.alerts;},
      error:()=>this.forecastOpsMessage='Forecast operations could not be loaded.'
    });
  }

  queueForecast():void{
    if(this.forecastOpsBusy)return;this.forecastOpsBusy=true;this.forecastOpsMessage='';
    this.forecastOps.queue({horizonDays:this.forecastHorizon,historyDays:this.forecastHistory}).pipe(finalize(()=>this.forecastOpsBusy=false)).subscribe({next:j=>{this.forecastJobs=[j,...this.forecastJobs];this.forecastOpsMessage='Forecast job queued. Run it now or allow the worker to claim it.';},error:e=>this.forecastOpsMessage=e?.error?.detail||'Forecast job could not be queued.'});
  }

  processForecastQueue():void{
    if(this.forecastOpsBusy)return;this.forecastOpsBusy=true;this.forecastOpsMessage='Running the next queued forecast…';
    this.forecastOps.processNext().pipe(finalize(()=>this.forecastOpsBusy=false)).subscribe({next:j=>{this.forecastOpsMessage=j?`Forecast job finished with status ${j.status}.`:'No queued forecast job was available.';this.loadForecastOperations();this.loadDemandWorkspace();},error:e=>this.forecastOpsMessage=e?.error?.detail||'Queued forecast could not be processed.'});
  }

  createForecastSchedule():void{
    if(this.forecastOpsBusy||!this.scheduleName.trim())return;this.forecastOpsBusy=true;
    this.forecastOps.createSchedule({scheduleName:this.scheduleName.trim(),cadence:this.scheduleCadence,dayOfWeek:this.scheduleCadence==='WEEKLY'?1:undefined,runHour:this.scheduleHour,runMinute:0,timezone:'Asia/Kolkata',horizonDays:this.forecastHorizon,historyDays:this.forecastHistory,active:true}).pipe(finalize(()=>this.forecastOpsBusy=false)).subscribe({next:s=>{this.forecastSchedules=[s,...this.forecastSchedules];this.forecastOpsMessage='Forecast schedule created.';},error:e=>this.forecastOpsMessage=e?.error?.detail||'Schedule could not be created.'});
  }

  toggleForecastSchedule(item:ForecastSchedule):void{this.forecastOps.setActive(item.scheduleId,!item.active).subscribe({next:s=>this.forecastSchedules=this.forecastSchedules.map(x=>x.scheduleId===s.scheduleId?s:x),error:()=>this.forecastOpsMessage='Schedule status could not be changed.'});}
  cancelForecastJob(item:ForecastJob):void{this.forecastOps.cancel(item.jobId).subscribe({next:j=>this.forecastJobs=this.forecastJobs.map(x=>x.jobId===j.jobId?j:x),error:e=>this.forecastOpsMessage=e?.error?.detail||'Job could not be cancelled.'});}
  retryForecastJob(item:ForecastJob):void{this.forecastOps.retry(item.jobId).subscribe({next:j=>this.forecastJobs=[j,...this.forecastJobs],error:e=>this.forecastOpsMessage=e?.error?.detail||'Job could not be retried.'});}
  acknowledgeForecastAlert(item:ForecastGovernanceAlert):void{this.forecastOps.acknowledge(item.alertId).subscribe({next:a=>this.forecastAlerts=this.forecastAlerts.map(x=>x.alertId===a.alertId?a:x),error:()=>this.forecastOpsMessage='Alert could not be acknowledged.'});}
  forecastStatusLabel(value:string):string{return value.replaceAll('_',' ').toLowerCase().replace(/\b\w/g,c=>c.toUpperCase());}

  loadRiskWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    forkJoin({
      summary: this.intelligenceData.riskSummary(),
      risks: this.intelligenceData.risks(this.selectedRiskType, this.selectedSeverity, this.riskLimit)
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.riskSummary = result.summary;
          this.inventoryRisks = result.risks;
        },
        error: () => this.pageError = 'Inventory risks could not be loaded from the API.'
      });
  }

  private loadInventoryWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    forkJoin({
      demand: this.intelligenceData.demandSummary(30),
      skus: this.intelligenceData.demandSkus(30, 20),
      risk: this.intelligenceData.riskSummary()
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.demandSummary = result.demand;
          this.demandSkus = result.skus;
          this.riskSummary = result.risk;
        },
        error: () => this.pageError = 'Inventory analytics could not be loaded from the API.'
      });
  }

  loadWarehouseWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    this.pageNotice = '';
    forkJoin({
      summary: this.foundationData.summary(),
      warehouses: this.foundationData.warehouses(),
      batches: this.foundationData.batches()
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.foundationDataSource = 'LIVE API';
          this.foundationSummary = result.summary;
          this.warehouses = this.applyPrototypePatches(this.prototypeCollection('warehouses'), result.warehouses, item => item.warehouseId);
          this.batches = this.applyPrototypePatches(this.prototypeCollection('batches'), result.batches, item => item.batchInventoryId);
        },
        error: () => this.usePrototypeFoundationFallback('warehouses')
      });
  }

  loadProductWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    this.pageNotice = '';
    forkJoin({
      summary: this.foundationData.summary(),
      skus: this.foundationData.skus()
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.foundationDataSource = 'LIVE API';
          this.foundationSummary = result.summary;
          this.skus = this.applyPrototypePatches(this.prototypeCollection('skus'), result.skus, item => item.skuId);
        },
        error: () => this.usePrototypeFoundationFallback('products')
      });
  }

  loadBatchWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    this.pageNotice = '';
    forkJoin({
      summary: this.foundationData.summary(),
      warehouses: this.foundationData.warehouses(),
      skus: this.foundationData.skus(),
      batches: this.foundationData.batches()
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.foundationDataSource = 'LIVE API';
          this.foundationSummary = result.summary;
          this.warehouses = this.applyPrototypePatches(this.prototypeCollection('warehouses'), result.warehouses, item => item.warehouseId);
          this.skus = this.applyPrototypePatches(this.prototypeCollection('skus'), result.skus, item => item.skuId);
          this.batches = this.applyPrototypePatches(this.prototypeCollection('batches'), result.batches, item => item.batchInventoryId);
        },
        error: () => this.usePrototypeFoundationFallback('batches')
      });
  }

  loadImportWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    this.importData.recentJobs()
      .pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: jobs => this.importJobs = jobs,
        error: () => this.pageError = 'Import history could not be loaded from the API.'
      });
  }

  private loadPrototypeRecommendationData(): void {
    forkJoin({
      skus: this.foundationData.skus(),
      batches: this.foundationData.batches()
    }).subscribe({
      next: result => {
        this.skus = this.applyPrototypePatches(this.prototypeCollection('skus'), result.skus, item => item.skuId);
        this.batches = this.applyPrototypePatches(this.prototypeCollection('batches'), result.batches, item => item.batchInventoryId);
      },
      error: () => this.usePrototypeFoundationFallback('recommendations')
    });
  }

  private usePrototypeFoundationFallback(scope: 'warehouses' | 'products' | 'batches' | 'recommendations'): void {
    const fallback = createPrototypeFoundationData(this.selectedTenant, this.currentTenantLabel());
    this.foundationDataSource = 'DEMO FALLBACK';
    this.foundationSummary = fallback.summary;
    this.warehouses = this.applyPrototypePatches(this.prototypeCollection('warehouses'), fallback.warehouses, item => item.warehouseId);
    this.skus = this.applyPrototypePatches(this.prototypeCollection('skus'), fallback.skus, item => item.skuId);
    this.batches = this.applyPrototypePatches(this.prototypeCollection('batches'), fallback.batches, item => item.batchInventoryId);
    this.pageError = '';
    if (scope !== 'recommendations') {
      this.pageNotice = 'The live API is unavailable, so StockFlow switched to the presentation-safe demo dataset. Your saved changes still work normally.';
    }
  }

  private applyPrototypePatches<T extends object>(collection: string, records: T[], id: (record: T) => string): T[] {
    return records.map(record => ({ ...record, ...this.prototype.recordPatch<T>(collection, id(record)) }));
  }
}
