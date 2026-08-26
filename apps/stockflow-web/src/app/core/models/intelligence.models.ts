export interface DemandSummary {
  tenantId: string;
  asOfDate: string;
  windowDays: number;
  transactionRows: number;
  skuCount: number;
  warehouseCount: number;
  salesQuantity: number;
  returnQuantity: number;
  lostSalesQuantity: number;
  stockoutRows: number;
  averageDailyDemand: number;
  grossSalesValue: number;
  fulfilmentRatePercent: number;
}

export interface DemandSku {
  warehouseId: string;
  warehouseName: string;
  skuId: string;
  skuName: string;
  salesQuantity: number;
  returnQuantity: number;
  lostSalesQuantity: number;
  stockoutRows: number;
  averageDailyDemand: number;
  grossSalesValue: number;
}

export interface DemandTrend {
  tenantId: string;
  asOfDate: string;
  labels: string[];
  actual: number[];
  forecast: number[];
}

export interface InventoryRiskSummary {
  tenantId: string;
  asOfDate: string;
  totalRisks: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  stockoutRiskCount: number;
  safetyStockBreachCount: number;
  inventoryDataGapCount: number;
  nearExpiryCount: number;
  expiredCount: number;
  excessInventoryCount: number;
  slowMovingCount: number;
  demandSurgeCount: number;
  riskExposureValue: number;
}

export interface InventoryRisk {
  riskId: string;
  tenantId: string;
  riskType: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM';
  asOfDate: string;
  warehouseId: string;
  warehouseName: string;
  skuId: string;
  skuName: string;
  batchNumber: string | null;
  availableQuantity: number;
  usableQuantity: number;
  minimumSafetyStock: number;
  sales7: number;
  sales30: number;
  averageDailyDemand30: number;
  daysOfCover: number | null;
  expiryDate: string | null;
  daysToExpiry: number | null;
  inventoryValue: number;
  lostSales30: number;
  stockoutRows30: number;
  reason: string;
  recommendedAction: string;
}
