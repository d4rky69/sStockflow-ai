import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { ReplenishmentSummary, TransferRecommendationSummary } from '../models/replenishment.models';

@Injectable({ providedIn: 'root' })
export class ReplenishmentService {
  constructor(private readonly http: HttpClient) {}

  plans(targetCoverDays = 30): Observable<ReplenishmentSummary> {
    const headers = new HttpHeaders({ 'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA' });
    const params = new HttpParams().set('targetCoverDays', targetCoverDays);
    return this.http.get<ReplenishmentSummary>(`${API_BASE_URL}/api/v1/replenishment/plans`, { headers, params });
  }

  transferRecommendations(targetCoverDays = 30): Observable<TransferRecommendationSummary> {
    const headers = new HttpHeaders({ 'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA' });
    const params = new HttpParams().set('targetCoverDays', targetCoverDays);
    return this.http.get<TransferRecommendationSummary>(`${API_BASE_URL}/api/v1/replenishment/transfer-recommendations`, { headers, params });
  }
}
