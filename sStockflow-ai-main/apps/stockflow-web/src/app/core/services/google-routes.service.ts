import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { GoogleRouteRequest, GoogleRouteResponse } from '../models/google-routes.models';

@Injectable({ providedIn: 'root' })
export class GoogleRoutesService {
  private readonly routeUrl = `${API_BASE_URL}/api/v1/integrations/google-maps/routes`;

  constructor(private readonly http: HttpClient) {}

  computeRoute(request: GoogleRouteRequest): Observable<GoogleRouteResponse> {
    return this.http.post<GoogleRouteResponse>(this.routeUrl, request, {
      headers: new HttpHeaders({
        'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA'
      })
    });
  }
}
