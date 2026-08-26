export type ForecastModelCode =
  | 'NAIVE'
  | 'MOVING_AVERAGE'
  | 'WEIGHTED_MOVING_AVERAGE'
  | 'SEASONAL_NAIVE';

export type ForecastConfidence = 'HIGH' | 'MEDIUM' | 'LOW';

export type ForecastRunStatus =
  | 'RUNNING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'FAILED';

export interface CreateForecastRunRequest {
  asOfDate?: string;
  horizonDays: number;
  historyDays?: number;
  warehouseId?: string;
  skuId?: string;
  models?: ForecastModelCode[];
}

export interface ForecastRunView {
  forecastRunId: string;
  tenantId: string;
  asOfDate: string;
  horizonDays: number;
  historyDays: number;
  requestedWarehouseId: string | null;
  requestedSkuId: string | null;
  status: ForecastRunStatus;
  positionsRequested: number;
  positionsProcessed: number;
  positionsFailed: number;
  startedAt: string;
  completedAt: string | null;
  message: string | null;
}

export interface ForecastValueView {
  forecastDate: string;
  horizonDay: number;
  forecastQuantity: number;
  lowerBound: number;
  upperBound: number;
}

export interface ForecastPositionView {
  forecastRunId: string;
  tenantId: string;
  asOfDate: string;
  warehouseId: string;
  warehouseName: string;
  skuId: string;
  skuName: string;
  selectedModel: ForecastModelCode;
  confidence: ForecastConfidence;
  trainingSampleCount: number;
  backtestPoints: number;
  mae: number;
  rmse: number;
  mape: number | null;
  bias: number;
  horizonDays: number;
  totalForecastQuantity: number;
  averageDailyForecast: number;
  usableInventory: number | null;
  inventoryDataAvailable: boolean;
  projectedStockoutDate: string | null;
  forecastValues: ForecastValueView[];
}

export interface ForecastSummaryView {
  tenantId: string;
  forecastRunId: string;
  asOfDate: string;
  horizonDays: number;
  positionsForecasted: number;
  highConfidenceCount: number;
  mediumConfidenceCount: number;
  lowConfidenceCount: number;
  projectedStockoutCount: number;
  totalForecastQuantity: number;
  modelUsage: Partial<Record<ForecastModelCode, number>>;
}

export interface ForecastModelPerformanceView {
  forecastRunId: string;
  warehouseId: string;
  warehouseName: string;
  skuId: string;
  skuName: string;
  modelCode: ForecastModelCode;
  trainingSampleCount: number;
  backtestPoints: number;
  mae: number;
  rmse: number;
  mape: number | null;
  bias: number;
  selectedModel: boolean;
}

export interface ForecastExceptionView {
  exceptionCode: string;
  warehouseId: string | null;
  skuId: string | null;
  message: string;
  createdAt: string;
}
