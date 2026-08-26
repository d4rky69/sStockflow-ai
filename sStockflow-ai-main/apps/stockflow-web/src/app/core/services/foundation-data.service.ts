import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  BatchInventoryView,
  FoundationSummary,
  SkuView,
  WarehouseView
} from '../models/foundation.models';

@Injectable({ providedIn: 'root' })
export class FoundationDataService {
  constructor(private readonly http: HttpClient) {}

  summary(): Observable<FoundationSummary> {
    return this.http.get<FoundationSummary>(`${API_BASE_URL}/api/v1/foundation/summary`, {
      headers: this.tenantHeaders()
    });
  }

  warehouses(): Observable<WarehouseView[]> {
    return this.http.get<WarehouseView[]>(`${API_BASE_URL}/api/v1/warehouses`, {
      headers: this.tenantHeaders()
    });
  }

  warehouse(warehouseId: string): Observable<WarehouseView> {
    return this.http.get<WarehouseView>(`${API_BASE_URL}/api/v1/warehouses/${encodeURIComponent(warehouseId)}`, {
      headers: this.tenantHeaders()
    });
  }

  skus(): Observable<SkuView[]> {
    return this.http.get<SkuView[]>(`${API_BASE_URL}/api/v1/skus`, {
      headers: this.tenantHeaders()
    });
  }

  batches(warehouseId = '', skuId = ''): Observable<BatchInventoryView[]> {
    let params = new HttpParams();
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    if (skuId) params = params.set('skuId', skuId);

    return this.http.get<BatchInventoryView[]>(`${API_BASE_URL}/api/v1/inventory/batches`, {
      headers: this.tenantHeaders(),
      params
    });
  }

  private tenantHeaders(): HttpHeaders {
    return new HttpHeaders({
      'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA'
    });
  }
}
