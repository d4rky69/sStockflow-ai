import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  DemandSku,
  DemandSummary,
  DemandTrend,
  InventoryRisk,
  InventoryRiskSummary
} from '../models/intelligence.models';

@Injectable({ providedIn: 'root' })
export class IntelligenceDataService {
  constructor(private readonly http: HttpClient) {}

  demandSummary(windowDays: number): Observable<DemandSummary> {
    return this.http.get<DemandSummary>(`${API_BASE_URL}/api/v1/analytics/demand/summary`, {
      headers: this.tenantHeaders(),
      params: new HttpParams().set('windowDays', windowDays)
    });
  }

  demandSkus(windowDays: number, limit = 50): Observable<DemandSku[]> {
    return this.http.get<DemandSku[]>(`${API_BASE_URL}/api/v1/analytics/demand/skus`, {
      headers: this.tenantHeaders(),
      params: new HttpParams().set('windowDays', windowDays).set('limit', limit)
    });
  }

  demandTrend(weeks = 16): Observable<DemandTrend> {
    return this.http.get<DemandTrend>(`${API_BASE_URL}/api/v1/analytics/demand/trend`, {
      headers: this.tenantHeaders(),
      params: new HttpParams().set('weeks', weeks)
    });
  }

  riskSummary(): Observable<InventoryRiskSummary> {
    return this.http.get<InventoryRiskSummary>(`${API_BASE_URL}/api/v1/risks/summary`, {
      headers: this.tenantHeaders()
    });
  }

  risks(type = '', severity = '', limit = 100): Observable<InventoryRisk[]> {
    let params = new HttpParams().set('limit', limit);
    if (type) params = params.set('type', type);
    if (severity) params = params.set('severity', severity);

    return this.http.get<InventoryRisk[]>(`${API_BASE_URL}/api/v1/risks/inventory`, {
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
