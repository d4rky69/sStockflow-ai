import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, HostListener, OnInit, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { DashboardOverview } from '../../core/models/dashboard.models';
import {
  ForecastModelPerformanceView,
  ForecastPositionView,
  ForecastRunView,
  ForecastSummaryView,
  ForecastValueView
} from '../../core/models/forecast.models';
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
import { FoundationDataService } from '../../core/services/foundation-data.service';
import { ForecastDataService } from '../../core/services/forecast-data.service';
import { ImportDataService } from '../../core/services/import-data.service';
import { IntelligenceDataService } from '../../core/services/intelligence-data.service';

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
  | 'warehouses'
  | 'products'
  | 'batches'
  | 'users'
  | 'settings'
  | 'integrations';

interface NavigationItem {
  label: string;
  icon: string;
  view: ViewId;
}

type TopbarPanel = 'notifications' | 'help' | 'profile' | null;
type NotificationTone = 'critical' | 'warning' | 'info' | 'success';

interface TopbarNotification {
  id: number;
  title: string;
  detail: string;
  time: string;
  view: ViewId;
  tone: NotificationTone;
  read: boolean;
}

@Component({
  selector: 'sf-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
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

  forecastSummary?: ForecastSummaryView;
  forecastPositions: ForecastPositionView[] = [];
  forecastPerformance: ForecastModelPerformanceView[] = [];
  forecastRuns: ForecastRunView[] = [];
  selectedForecastPosition?: ForecastPositionView;
  selectedForecastPositionKey = '';
  forecastRunning = false;
  forecastResultsLoading = false;
  forecastLoaded = false;
  forecastError = '';
  forecastNotice = '';

  foundationSummary?: FoundationSummary;
  warehouses: WarehouseView[] = [];
  skus: SkuView[] = [];
  batches: BatchInventoryView[] = [];

  importJobs: ImportJobView[] = [];
  importErrors: ImportErrorView[] = [];
  selectedImportJob?: ImportJobView;
  importResult?: ImportJobView;
  selectedImportFile?: File;

  activeView: ViewId = 'dashboard';
  loading = true;
  pageLoading = false;
  importRunning = false;
  error = '';
  pageError = '';
  importError = '';
  copilotInput = '';
  globalSearch = '';
  sidebarCollapsed = window.innerWidth <= 900;

  @ViewChild('globalSearchInput') globalSearchInput?: ElementRef<HTMLInputElement>;

  activeTopbarPanel: TopbarPanel = null;
  darkMode = localStorage.getItem('stockflowTheme') === 'dark';
  topbarToast = '';
  private topbarToastTimer?: number;

  selectedTenant = localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA';
  selectedWindowDays = 30;

  selectedForecastWarehouseId = '';
  selectedForecastSkuId = '';
  forecastHorizonDays = 7;
  forecastHistoryDays = 180;
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
      items: [{ label: 'Dashboard', icon: '⌂', view: 'dashboard' }]
    },
    {
      title: 'INTELLIGENCE',
      items: [
        { label: 'Demand Forecast', icon: '▥', view: 'demand' },
        { label: 'Inventory Analytics', icon: '⌁', view: 'inventory' },
        { label: 'Risk & Alerts', icon: '△', view: 'risks' },
        { label: 'Recommendations', icon: '▣', view: 'recommendations' }
      ]
    },
    {
      title: 'OPERATIONS',
      items: [
        { label: 'Transfers', icon: '⇄', view: 'transfers' },
        { label: 'Purchase Planning', icon: '🛒', view: 'purchase' },
        { label: 'Orders', icon: '▤', view: 'orders' },
        { label: 'Returns', icon: '↶', view: 'returns' }
      ]
    },
    {
      title: 'INVENTORY',
      items: [
        { label: 'Warehouses', icon: '⌂', view: 'warehouses' },
        { label: 'Products & SKUs', icon: '◇', view: 'products' },
        { label: 'Batches', icon: '▰', view: 'batches' }
      ]
    },
    {
      title: 'ADMIN',
      items: [
        { label: 'Users & Roles', icon: '♙', view: 'users' },
        { label: 'Settings', icon: '⚙', view: 'settings' },
        { label: 'Data Imports', icon: '⇩', view: 'integrations' }
      ]
    }
  ];

  constructor(
    private readonly dashboardData: DashboardDataService,
    private readonly intelligenceData: IntelligenceDataService,
    private readonly foundationData: FoundationDataService,
    private readonly importData: ImportDataService,
    private readonly forecastData: ForecastDataService
  ) {}

  ngOnInit(): void {
    this.restoreNotificationState();
    this.applyThemePreference();
    this.loadDashboard();
  }

  selectView(view: ViewId): void {
    this.closeTopbarPanels();
    this.activeView = view;
    this.pageError = '';
    this.importError = '';

    if (window.innerWidth <= 900) {
      this.sidebarCollapsed = true;
    }

    if (view === 'dashboard' || view === 'recommendations') {
      if (!this.data) this.loadDashboard();
      return;
    }

    if (view === 'demand') {
      this.loadForecastWorkspace();
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
    }
  }

  onTenantChange(): void {
    localStorage.setItem('stockflowTenantId', this.selectedTenant);
    this.data = undefined;
    this.riskSummary = undefined;
    this.inventoryRisks = [];
    this.demandSummary = undefined;
    this.demandSkus = [];
    this.demandTrend = undefined;
    this.forecastSummary = undefined;
    this.forecastPositions = [];
    this.forecastPerformance = [];
    this.forecastRuns = [];
    this.selectedForecastPosition = undefined;
    this.selectedForecastPositionKey = '';
    this.forecastLoaded = false;
    this.forecastError = '';
    this.forecastNotice = '';
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
  }

  onDemandWindowChange(): void {
    this.loadHistoricalDemandContext();
  }


  runForecast(): void {
    this.forecastRunning = true;
    this.forecastError = '';
    this.forecastNotice = '';

    const request = {
      horizonDays: this.forecastHorizonDays,
      historyDays: this.forecastHistoryDays,
      ...(this.selectedForecastWarehouseId ? { warehouseId: this.selectedForecastWarehouseId } : {}),
      ...(this.selectedForecastSkuId ? { skuId: this.selectedForecastSkuId } : {})
    };

    this.forecastData.createRun(request)
      .pipe(finalize(() => this.forecastRunning = false))
      .subscribe({
        next: run => {
          this.forecastRuns = [run, ...this.forecastRuns.filter(item => item.forecastRunId !== run.forecastRunId)]
            .slice(0, 20);
          this.forecastNotice = run.message ?? 'Forecast run completed.';
          this.loadForecastRun(run.forecastRunId);
        },
        error: (error: HttpErrorResponse) => {
          this.forecastError = this.apiMessage(error, 'The forecast run could not be created.');
        }
      });
  }

  refreshForecastWorkspace(): void {
    this.loadForecastWorkspace();
  }

  viewForecastRun(run: ForecastRunView): void {
    if (run.status !== 'COMPLETED' && run.status !== 'COMPLETED_WITH_ERRORS') return;
    this.forecastNotice = `Viewing forecast run from ${run.asOfDate}.`;
    this.loadForecastRun(run.forecastRunId);
  }

  onForecastPositionChange(): void {
    this.selectedForecastPosition = this.forecastPositions.find(
      item => this.forecastPositionKey(item) === this.selectedForecastPositionKey
    ) ?? this.forecastPositions[0];

    if (this.selectedForecastPosition) {
      this.selectedForecastPositionKey = this.forecastPositionKey(this.selectedForecastPosition);
    }
  }

  selectForecastPosition(position: ForecastPositionView): void {
    this.selectedForecastPosition = position;
    this.selectedForecastPositionKey = this.forecastPositionKey(position);
  }

  forecastPositionKey(position: ForecastPositionView): string {
    return `${position.warehouseId}::${position.skuId}`;
  }

  filteredForecastPositions(): ForecastPositionView[] {
    const query = this.globalSearch.trim().toLowerCase();
    return this.forecastPositions.filter(position => {
      const matchesWarehouse = !this.selectedForecastWarehouseId
        || position.warehouseId === this.selectedForecastWarehouseId;
      const matchesSku = !this.selectedForecastSkuId || position.skuId === this.selectedForecastSkuId;
      const matchesSearch = !query || [
        position.warehouseId,
        position.warehouseName,
        position.skuId,
        position.skuName,
        position.selectedModel,
        position.confidence
      ].some(value => value.toLowerCase().includes(query));
      return matchesWarehouse && matchesSku && matchesSearch;
    });
  }

  selectedModelPerformance(): ForecastModelPerformanceView[] {
    const position = this.selectedForecastPosition;
    if (!position) return [];
    return this.forecastPerformance.filter(item =>
      item.warehouseId === position.warehouseId && item.skuId === position.skuId
    );
  }

  modelLabel(model: string): string {
    return model.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, value => value.toUpperCase());
  }

  runStatusLabel(status: string): string {
    return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, value => value.toUpperCase());
  }

  forecastChartPoints(field: keyof Pick<ForecastValueView, 'forecastQuantity' | 'lowerBound' | 'upperBound'>): string {
    const values = this.selectedForecastPosition?.forecastValues ?? [];
    if (!values.length) return '';

    const width = 900;
    const height = 280;
    const padding = 28;
    const upperMaximum = Math.max(...values.map(item => Number(item.upperBound)), 1);

    return values.map((value, index) => {
      const x = padding + index * (width - padding * 2) / Math.max(values.length - 1, 1);
      const y = height - padding - (Number(value[field]) / upperMaximum) * (height - padding * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');
  }

  forecastBandPoints(): string {
    const values = this.selectedForecastPosition?.forecastValues ?? [];
    if (!values.length) return '';

    const width = 900;
    const height = 280;
    const padding = 28;
    const upperMaximum = Math.max(...values.map(item => Number(item.upperBound)), 1);
    const point = (value: number, index: number): string => {
      const x = padding + index * (width - padding * 2) / Math.max(values.length - 1, 1);
      const y = height - padding - (value / upperMaximum) * (height - padding * 2);
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    };

    const upper = values.map((item, index) => point(Number(item.upperBound), index));
    const lower = [...values].reverse().map((item, reverseIndex) => {
      const index = values.length - reverseIndex - 1;
      return point(Number(item.lowerBound), index);
    });
    return [...upper, ...lower].join(' ');
  }

  modelUsageEntries(): { model: string; count: number }[] {
    const usage = this.forecastSummary?.modelUsage ?? {};
    return Object.entries(usage).map(([model, count]) => ({ model, count: Number(count) }));
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
    this.sidebarCollapsed = !this.sidebarCollapsed;
  }


  @HostListener('document:click')
  onDocumentClick(): void {
    this.closeTopbarPanels();
  }

  @HostListener('document:keydown', ['$event'])
  onDocumentKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.closeTopbarPanels();
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
    const name = this.data?.userName || 'StockFlow User';
    return name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map(part => part.charAt(0).toUpperCase())
      .join('') || 'SF';
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

  sendCopilotMessage(): void {
    const message = this.copilotInput.trim();
    if (!message || !this.data) return;
    this.data.copilotMessages.push({ role: 'user', text: message, timestamp: 'Now' });
    this.data.copilotMessages.push({
      role: 'assistant',
      text: 'The live AI agent is planned for Phase 3. This frontend currently uses verified dashboard, demand, risk and master-data APIs.',
      timestamp: 'Now'
    });
    this.copilotInput = '';
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
      warehouses: 'Warehouses',
      products: 'Products & SKUs',
      batches: 'Batch Inventory',
      users: 'Users & Roles',
      settings: 'Settings',
      integrations: 'Data Imports'
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
      'warehouses',
      'products',
      'batches',
      'integrations'
    ].includes(this.activeView);
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

  private apiMessage(error: HttpErrorResponse, fallback: string): string {
    const message = error.error && typeof error.error === 'object' && typeof error.error.message === 'string'
      ? error.error.message
      : '';
    return message || fallback;
  }

  private showTopbarToast(message: string): void {
    this.topbarToast = message;
    if (this.topbarToastTimer) window.clearTimeout(this.topbarToastTimer);
    this.topbarToastTimer = window.setTimeout(() => this.topbarToast = '', 2600);
  }

  private loadDashboard(afterLoad?: () => void): void {
    this.loading = true;
    this.error = '';
    this.dashboardData.loadOverview()
      .pipe(finalize(() => this.loading = false))
      .subscribe({
        next: data => {
          this.data = data;
          afterLoad?.();
        },
        error: () => this.error = 'The dashboard data could not be loaded.'
      });
  }

  private loadForecastWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    this.forecastError = '';
    this.forecastLoaded = false;

    forkJoin({
      warehouses: this.foundationData.warehouses(),
      skus: this.foundationData.skus(),
      runs: this.forecastData.runs(),
      demandSummary: this.intelligenceData.demandSummary(this.selectedWindowDays),
      demandSkus: this.intelligenceData.demandSkus(this.selectedWindowDays, 50),
      demandTrend: this.intelligenceData.demandTrend(16)
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.warehouses = result.warehouses;
          this.skus = result.skus;
          this.forecastRuns = result.runs;
          this.demandSummary = result.demandSummary;
          this.demandSkus = result.demandSkus;
          this.demandTrend = result.demandTrend;

          const latestCompleted = result.runs.find(run =>
            run.status === 'COMPLETED' || run.status === 'COMPLETED_WITH_ERRORS'
          );
          if (latestCompleted) {
            this.selectedForecastWarehouseId = latestCompleted.requestedWarehouseId ?? '';
            this.selectedForecastSkuId = latestCompleted.requestedSkuId ?? '';
            this.forecastHorizonDays = latestCompleted.horizonDays;
            this.forecastHistoryDays = latestCompleted.historyDays;
            this.loadForecastRun(latestCompleted.forecastRunId);
          } else {
            this.forecastLoaded = true;
            this.forecastSummary = undefined;
            this.forecastPositions = [];
            this.forecastPerformance = [];
          }
        },
        error: (error: HttpErrorResponse) => {
          this.pageError = this.apiMessage(error, 'Forecasting workspace data could not be loaded from the API.');
        }
      });
  }

  private loadForecastRun(runId: string): void {
    this.forecastResultsLoading = true;
    this.forecastError = '';

    forkJoin({
      summary: this.forecastData.summary(runId),
      positions: this.forecastData.latest(runId, '', '', 250),
      performance: this.forecastData.modelPerformance(runId)
    }).pipe(finalize(() => {
      this.forecastResultsLoading = false;
      this.forecastLoaded = true;
    })).subscribe({
      next: result => {
        this.forecastSummary = result.summary;
        this.forecastPositions = result.positions;
        this.forecastPerformance = result.performance;

        const requestedMatch = result.positions.find(position =>
          (!this.selectedForecastWarehouseId || position.warehouseId === this.selectedForecastWarehouseId)
          && (!this.selectedForecastSkuId || position.skuId === this.selectedForecastSkuId)
        );
        this.selectedForecastPosition = requestedMatch ?? result.positions[0];
        this.selectedForecastPositionKey = this.selectedForecastPosition
          ? this.forecastPositionKey(this.selectedForecastPosition)
          : '';
      },
      error: (error: HttpErrorResponse) => {
        this.forecastError = this.apiMessage(error, 'Forecast results could not be loaded.');
      }
    });
  }

  private loadHistoricalDemandContext(): void {

    this.pageLoading = true;
    this.pageError = '';
    forkJoin({
      summary: this.intelligenceData.demandSummary(this.selectedWindowDays),
      skus: this.intelligenceData.demandSkus(this.selectedWindowDays, 50),
      trend: this.intelligenceData.demandTrend(16)
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.demandSummary = result.summary;
          this.demandSkus = result.skus;
          this.demandTrend = result.trend;
        },
        error: () => this.pageError = 'Demand analytics could not be loaded from the API.'
      });
  }

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
    forkJoin({
      summary: this.foundationData.summary(),
      warehouses: this.foundationData.warehouses(),
      batches: this.foundationData.batches()
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.foundationSummary = result.summary;
          this.warehouses = result.warehouses;
          this.batches = result.batches;
        },
        error: () => this.pageError = 'Warehouse master data could not be loaded from the API.'
      });
  }

  loadProductWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    forkJoin({
      summary: this.foundationData.summary(),
      skus: this.foundationData.skus()
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.foundationSummary = result.summary;
          this.skus = result.skus;
        },
        error: () => this.pageError = 'Product and SKU data could not be loaded from the API.'
      });
  }

  loadBatchWorkspace(): void {
    this.pageLoading = true;
    this.pageError = '';
    forkJoin({
      summary: this.foundationData.summary(),
      warehouses: this.foundationData.warehouses(),
      skus: this.foundationData.skus(),
      batches: this.foundationData.batches()
    }).pipe(finalize(() => this.pageLoading = false))
      .subscribe({
        next: result => {
          this.foundationSummary = result.summary;
          this.warehouses = result.warehouses;
          this.skus = result.skus;
          this.batches = result.batches;
        },
        error: () => this.pageError = 'Batch inventory could not be loaded from the API.'
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
}
