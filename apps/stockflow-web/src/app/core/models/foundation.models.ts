export interface FoundationSummary {
  tenant: TenantView;
  warehouseCount: number;
  productCount: number;
  skuCount: number;
  batchCount: number;
}

export interface TenantView {
  tenantId: string;
  tenantName: string;
  vertical: string;
  currency: string;
  timezone: string;
}

export interface WarehouseView {
  warehouseId: string;
  warehouseName: string;
  city: string;
  state: string;
  country: string;
  capacityUnits: number;
  coldChainAvailable: boolean;
}

export interface SkuView {
  skuId: string;
  productId: string;
  skuName: string;
  baseUom: string;
  unitCost: number;
  sellingPrice: number;
  currency: string;
  minimumSafetyStock: number;
  reorderMultiple: number;
  defaultShelfLifeDays: number | null;
  fefoRequired: boolean;
  demandProfile: string;
}

export interface BatchInventoryView {
  batchInventoryId: string;
  snapshotDate: string;
  warehouseId: string;
  skuId: string;
  batchNumber: string;
  manufactureDate: string | null;
  expiryDate: string | null;
  availableQuantity: number;
  reservedQuantity: number;
  blockedQuantity: number;
  usableQuantity: number;
  unitCost: number;
  currency: string;
  storageConditionCode: string;
  lastMovementAt: string | null;
}
