import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { DashboardOverview } from '../models/dashboard.models';

@Injectable({ providedIn: 'root' })
export class DashboardDataService {
  constructor(private readonly http: HttpClient) {}

  loadOverview(warehouseId = ''): Observable<DashboardOverview> {
    const tenantId = localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA';
    let params = new HttpParams();
    if (warehouseId) params = params.set('warehouseId', warehouseId);
    return this.http.get<DashboardOverview>(`${API_BASE_URL}/api/v1/dashboard/overview`, {
      headers: { 'X-Tenant-ID': tenantId },
      params
    }).pipe(
      catchError(() => this.http.get<DashboardOverview>('/assets/mock/dashboard-overview.json'))
    );
  }
}
