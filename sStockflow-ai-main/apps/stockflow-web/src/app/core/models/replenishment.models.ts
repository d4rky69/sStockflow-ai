export interface ReplenishmentPlan {
  recommendationId: string; warehouseId: string; warehouseName: string; skuId: string; skuName: string;
  supplierId?: string; supplierName: string; leadTimeDays: number; usableQuantity: number; openPurchaseQuantity: number;
  averageDailyDemand: number; demandSource: string; coverDays?: number; safetyStock: number; targetStock: number;
  reorderMultiple: number; recommendedQuantity: number; unitCost: number; plannedValue: number; needBy: string;
  confidencePercent: number; risk: string; status: string; explanation: string; asOfDate: string;
}

export interface ReplenishmentSummary {
  asOfDate?: string; targetCoverDays: number; recommendationCount: number; criticalCount: number;
  plannedSpend: number; openPurchaseQuantity: number; plans: ReplenishmentPlan[];
}

export interface TransferRecommendation {
  recommendationId: string; skuId: string; skuName: string;
  sourceWarehouseId: string; sourceWarehouseName: string;
  destinationWarehouseId: string; destinationWarehouseName: string;
  recommendedQuantity: number; sourceUsableBefore: number; sourceUsableAfter: number;
  sourceSafetyStock: number; destinationUsableBefore: number; destinationTargetStock: number;
  distanceKm: number; vehicleType: string; vehicleCapacityUnits: number; trips: number;
  estimatedTransferCost: number; estimatedPurchaseCost: number; estimatedSavings: number;
  workingCapitalMoved: number; estimatedCarbonKgCo2e: number; risk: string;
  confidencePercent: number; explanation: string; constraintsChecked: string[];
  assumptions: string[]; asOfDate: string;
}

export interface TransferRecommendationSummary {
  asOfDate?: string; recommendationCount: number; recommendedUnits: number;
  estimatedSavings: number; workingCapitalMoved: number; estimatedCarbonKgCo2e: number;
  recommendations: TransferRecommendation[];
}
