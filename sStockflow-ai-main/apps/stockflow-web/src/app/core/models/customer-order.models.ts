export interface CustomerOrderView {
  orderId: string;
  orderNumber: string;
  tenantId: string;
  customerName: string;
  customerCity: string;
  channel: string;
  warehouseId: string;
  warehouseName: string;
  status: string;
  promisedAt: string;
  fulfilmentPercent: number;
  totalValue: number;
  currency: string;
  itemCount: number;
  skuId: string;
  skuName: string;
  quantity: number;
  unitPrice: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CustomerOrderDetail {
  order: CustomerOrderView;
  events: Array<{
    eventId: string;
    fromStatus: string | null;
    toStatus: string;
    changedBy: string;
    comment: string | null;
    occurredAt: string;
  }>;
}

export interface CreateCustomerOrderRequest {
  customerName: string;
  customerCity: string;
  channel: string;
  warehouseId: string;
  skuId: string;
  quantity: number;
  promisedAt: string;
  unitPrice?: number;
}
