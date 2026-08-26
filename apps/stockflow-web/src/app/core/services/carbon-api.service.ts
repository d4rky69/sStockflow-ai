import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { CARBON_API_BASE_URL } from '../config/api.config';

export interface RouteOptimisationInput {
  id: string;
  lane: string;
  stops: string[];
  vehicle: string;
  loadKg: number;
  capacityKg: number;
  baselineKm: number;
  priority: string;
  status: string;
}

export interface OptimisedRoute extends RouteOptimisationInput {
  optimizedKm: number;
  duration: string;
  costInr: number;
  co2Kg: number;
  co2SavedKg: number;
  vehicleFamily: string;
  explanation: string[];
}

export interface RouteOptimisationResponse {
  tenantId: string;
  objective: string;
  routes: OptimisedRoute[];
  solver: string;
  limitations: string[];
}

@Injectable({ providedIn: 'root' })
export class CarbonApiService {
  constructor(private readonly http: HttpClient) {}

  optimiseRoutes(objective: string, vehicleType: string, routes: RouteOptimisationInput[]): Observable<RouteOptimisationResponse> {
    return this.http.post<RouteOptimisationResponse>(`${CARBON_API_BASE_URL}/api/v1/routes/optimise`, {
      objective,
      vehicleType,
      routes
    }, { headers: this.tenantHeaders() });
  }

  private tenantHeaders(): HttpHeaders {
    return new HttpHeaders({
      'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA'
    });
  }
}
