import { BatchInventoryView, FoundationSummary, SkuView, WarehouseView } from '../models/foundation.models';

export interface PrototypeFoundationData {
  summary: FoundationSummary;
  warehouses: WarehouseView[];
  skus: SkuView[];
  batches: BatchInventoryView[];
}

export function createPrototypeFoundationData(tenantId: string, tenantName: string): PrototypeFoundationData {
  const warehouses: WarehouseView[] = [
    { warehouseId: 'WH-GUWAHATI', warehouseName: 'Guwahati Central', city: 'Guwahati', state: 'Assam', country: 'India', capacityUnits: 180000, coldChainAvailable: true },
    { warehouseId: 'WH-SHILLONG', warehouseName: 'Shillong Hub', city: 'Shillong', state: 'Meghalaya', country: 'India', capacityUnits: 145000, coldChainAvailable: true },
    { warehouseId: 'WH-IMPHAL', warehouseName: 'Imphal Hub', city: 'Imphal', state: 'Manipur', country: 'India', capacityUnits: 165000, coldChainAvailable: true },
    { warehouseId: 'WH-AGARTALA', warehouseName: 'Agartala West', city: 'Agartala', state: 'Tripura', country: 'India', capacityUnits: 92000, coldChainAvailable: false },
    { warehouseId: 'WH-DIMAPUR', warehouseName: 'Dimapur DC', city: 'Dimapur', state: 'Nagaland', country: 'India', capacityUnits: 78000, coldChainAvailable: false }
  ];

  const skus: SkuView[] = [
    { skuId: 'SKU-PARA-650', productId: 'PROD-PARA', skuName: 'Paracetamol 650 mg', baseUom: 'TABLET', unitCost: 1.7, sellingPrice: 2.45, currency: 'INR', minimumSafetyStock: 900, reorderMultiple: 500, defaultShelfLifeDays: 730, fefoRequired: true, demandProfile: 'STABLE' },
    { skuId: 'SKU-AMOX-500', productId: 'PROD-AMOX', skuName: 'Amoxicillin 500 mg', baseUom: 'CAPSULE', unitCost: 5.4, sellingPrice: 7.8, currency: 'INR', minimumSafetyStock: 600, reorderMultiple: 300, defaultShelfLifeDays: 540, fefoRequired: true, demandProfile: 'TRENDING' },
    { skuId: 'SKU-ORS-21', productId: 'PROD-ORS', skuName: 'ORS Sachet 21 g', baseUom: 'SACHET', unitCost: 3.6, sellingPrice: 5.1, currency: 'INR', minimumSafetyStock: 1200, reorderMultiple: 600, defaultShelfLifeDays: 730, fefoRequired: true, demandProfile: 'SEASONAL' },
    { skuId: 'SKU-CET-10', productId: 'PROD-CET', skuName: 'Cetirizine 10 mg', baseUom: 'TABLET', unitCost: 1.25, sellingPrice: 1.95, currency: 'INR', minimumSafetyStock: 500, reorderMultiple: 250, defaultShelfLifeDays: 730, fefoRequired: true, demandProfile: 'STABLE' },
    { skuId: 'SKU-INS-GLR', productId: 'PROD-INS', skuName: 'Insulin Glargine', baseUom: 'VIAL', unitCost: 494, sellingPrice: 618, currency: 'INR', minimumSafetyStock: 180, reorderMultiple: 60, defaultShelfLifeDays: 540, fefoRequired: true, demandProfile: 'INTERMITTENT' }
  ];

  const batches: BatchInventoryView[] = [
    { batchInventoryId: 'BATCHINV-001', snapshotDate: '2026-07-26', warehouseId: 'WH-GUWAHATI', skuId: 'SKU-PARA-650', batchNumber: 'PARA-G26-04', manufactureDate: '2026-02-15', expiryDate: '2027-11-30', availableQuantity: 6200, reservedQuantity: 840, blockedQuantity: 0, usableQuantity: 5360, unitCost: 1.7, currency: 'INR', storageConditionCode: 'AMBIENT', lastMovementAt: '2026-07-26T09:30:00Z' },
    { batchInventoryId: 'BATCHINV-002', snapshotDate: '2026-07-26', warehouseId: 'WH-SHILLONG', skuId: 'SKU-PARA-650', batchNumber: 'PARA-S26-07', manufactureDate: '2026-03-12', expiryDate: '2027-12-31', availableQuantity: 420, reservedQuantity: 120, blockedQuantity: 0, usableQuantity: 300, unitCost: 1.7, currency: 'INR', storageConditionCode: 'AMBIENT', lastMovementAt: '2026-07-26T08:45:00Z' },
    { batchInventoryId: 'BATCHINV-003', snapshotDate: '2026-07-26', warehouseId: 'WH-IMPHAL', skuId: 'SKU-AMOX-500', batchNumber: 'AMOX-I26-02', manufactureDate: '2025-12-10', expiryDate: '2026-08-22', availableQuantity: 1840, reservedQuantity: 280, blockedQuantity: 0, usableQuantity: 1560, unitCost: 5.4, currency: 'INR', storageConditionCode: 'AMBIENT', lastMovementAt: '2026-07-25T16:10:00Z' },
    { batchInventoryId: 'BATCHINV-004', snapshotDate: '2026-07-26', warehouseId: 'WH-GUWAHATI', skuId: 'SKU-AMOX-500', batchNumber: 'AMOX-G26-05', manufactureDate: '2026-02-08', expiryDate: '2027-07-31', availableQuantity: 260, reservedQuantity: 40, blockedQuantity: 0, usableQuantity: 220, unitCost: 5.4, currency: 'INR', storageConditionCode: 'AMBIENT', lastMovementAt: '2026-07-26T11:20:00Z' },
    { batchInventoryId: 'BATCHINV-005', snapshotDate: '2026-07-26', warehouseId: 'WH-SHILLONG', skuId: 'SKU-ORS-21', batchNumber: 'ORS-S26-03', manufactureDate: '2026-01-18', expiryDate: '2027-08-31', availableQuantity: 4100, reservedQuantity: 740, blockedQuantity: 80, usableQuantity: 3280, unitCost: 3.6, currency: 'INR', storageConditionCode: 'AMBIENT', lastMovementAt: '2026-07-26T07:55:00Z' },
    { batchInventoryId: 'BATCHINV-006', snapshotDate: '2026-07-26', warehouseId: 'WH-DIMAPUR', skuId: 'SKU-ORS-21', batchNumber: 'ORS-D26-06', manufactureDate: '2026-03-20', expiryDate: '2027-10-31', availableQuantity: 360, reservedQuantity: 90, blockedQuantity: 0, usableQuantity: 270, unitCost: 3.6, currency: 'INR', storageConditionCode: 'AMBIENT', lastMovementAt: '2026-07-25T14:25:00Z' },
    { batchInventoryId: 'BATCHINV-007', snapshotDate: '2026-07-26', warehouseId: 'WH-AGARTALA', skuId: 'SKU-CET-10', batchNumber: 'CET-A25-11', manufactureDate: '2025-06-05', expiryDate: '2026-07-15', availableQuantity: 640, reservedQuantity: 0, blockedQuantity: 640, usableQuantity: 0, unitCost: 1.25, currency: 'INR', storageConditionCode: 'AMBIENT', lastMovementAt: '2026-07-15T10:00:00Z' },
    { batchInventoryId: 'BATCHINV-008', snapshotDate: '2026-07-26', warehouseId: 'WH-GUWAHATI', skuId: 'SKU-INS-GLR', batchNumber: 'INS-G26-01', manufactureDate: '2026-01-05', expiryDate: '2027-05-31', availableQuantity: 126, reservedQuantity: 38, blockedQuantity: 0, usableQuantity: 88, unitCost: 494, currency: 'INR', storageConditionCode: 'COLD_CHAIN_2_8C', lastMovementAt: '2026-07-26T06:30:00Z' }
  ];

  return {
    summary: {
      tenant: { tenantId, tenantName, vertical: 'Pharmaceutical distribution', currency: 'INR', timezone: 'Asia/Kolkata' },
      warehouseCount: warehouses.length,
      productCount: skus.length,
      skuCount: skus.length,
      batchCount: batches.length
    },
    warehouses,
    skus,
    batches
  };
}
