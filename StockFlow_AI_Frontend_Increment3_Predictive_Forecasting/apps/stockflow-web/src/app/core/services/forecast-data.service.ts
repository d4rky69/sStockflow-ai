import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  CreateForecastRunRequest,
  ForecastExceptionView,
  ForecastModelPerformanceView,
  ForecastPositionView,
  ForecastRunView,
  ForecastSummaryView
} from '../models/forecast.models';

@Injectable({ providedIn: 'root' })
export class ForecastDataService {
  constructor(private readonly http: HttpClient) {}

  createRun(request: CreateForecastRunRequest): Observable<ForecastRunView> {
    return this.http.post<ForecastRunView>(`${API_BASE_URL}/api/v1/forecasts/runs`, request, {
      headers: this.tenantHeaders()
    });
  }

  runs(): Observable<ForecastRunView[]> {
    return this.http.get<ForecastRunView[]>(`${API_BASE_URL}/api/v1/forecasts/runs`, {
      headers: this.tenantHeaders()
    });
  }

  run(runId: string): Observable<ForecastRunView> {
    return this.http.get<ForecastRunView>(`${API_BASE_URL}/api/v1/forecasts/runs/${encodeURIComponent(runId)}`, {
      headers: this.tenantHeaders()
    });
  }

  latest(runId = '', warehouseId = '', skuId = '', limit = 250): Observable<ForecastPositionView[]> {
    let params = new HttpParams().set('limit', limit);
    if (runId) params = params.set('runId', runId);
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    if (skuId) params = params.set('skuId', skuId);

    return this.http.get<ForecastPositionView[]>(`${API_BASE_URL}/api/v1/forecasts/latest`, {
      headers: this.tenantHeaders(),
      params
    });
  }

  summary(runId = ''): Observable<ForecastSummaryView> {
    let params = new HttpParams();
    if (runId) params = params.set('runId', runId);

    return this.http.get<ForecastSummaryView>(`${API_BASE_URL}/api/v1/forecasts/summary`, {
      headers: this.tenantHeaders(),
      params
    });
  }

  modelPerformance(runId = ''): Observable<ForecastModelPerformanceView[]> {
    let params = new HttpParams();
    if (runId) params = params.set('runId', runId);

    return this.http.get<ForecastModelPerformanceView[]>(`${API_BASE_URL}/api/v1/forecasts/model-performance`, {
      headers: this.tenantHeaders(),
      params
    });
  }

  exceptions(runId: string): Observable<ForecastExceptionView[]> {
    return this.http.get<ForecastExceptionView[]>(
      `${API_BASE_URL}/api/v1/forecasts/runs/${encodeURIComponent(runId)}/exceptions`,
      { headers: this.tenantHeaders() }
    );
  }

  private tenantHeaders(): HttpHeaders {
    return new HttpHeaders({
      'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA'
    });
  }
}
