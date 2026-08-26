import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { FleetbaseAuditSummary, FleetbaseIntegrationStatus, FleetbaseOrganization, FleetbaseVehicleList } from '../models/fleetbase.models';

@Injectable({ providedIn: 'root' })
export class FleetbaseService {
  private readonly baseUrl = `${API_BASE_URL}/api/v1/integrations/fleetbase`;

  constructor(private readonly http: HttpClient) {}

  status(): Observable<FleetbaseIntegrationStatus> {
    return this.http.get<FleetbaseIntegrationStatus>(`${this.baseUrl}/status`, { headers: this.headers() });
  }

  vehicles(limit = 100): Observable<FleetbaseVehicleList> {
    return this.http.get<FleetbaseVehicleList>(`${this.baseUrl}/vehicles`, {
      headers: this.headers(),
      params: new HttpParams().set('limit', limit)
    });
  }

  organization(): Observable<FleetbaseOrganization> {
    return this.http.get<FleetbaseOrganization>(`${this.baseUrl}/organization`, { headers: this.headers() });
  }

  audit(): Observable<FleetbaseAuditSummary> {
    return this.http.get<FleetbaseAuditSummary>(`${this.baseUrl}/audit`, { headers: this.headers() });
  }

  private headers(): HttpHeaders {
    return new HttpHeaders({
      'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA'
    });
  }
}
