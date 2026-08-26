import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { HazardAlertLocation, HazardAlertsResponse, RouteWeatherForecast } from '../models/google-weather.models';

@Injectable({ providedIn: 'root' })
export class GoogleWeatherService {
  private readonly forecastUrl = `${API_BASE_URL}/api/v1/integrations/google-weather/route-forecast`;
  private readonly alertsUrl = `${API_BASE_URL}/api/v1/integrations/google-weather/hazard-alerts`;

  constructor(private readonly http: HttpClient) {}

  routeForecast(latitude: number, longitude: number, etaMinutes: number, locationLabel: string): Observable<RouteWeatherForecast> {
    return this.http.get<RouteWeatherForecast>(this.forecastUrl, {
      headers: new HttpHeaders({ 'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA' }),
      params: new HttpParams()
        .set('latitude', latitude)
        .set('longitude', longitude)
        .set('etaMinutes', etaMinutes)
        .set('locationLabel', locationLabel)
    });
  }

  hazardAlerts(locations: HazardAlertLocation[]): Observable<HazardAlertsResponse> {
    let params = new HttpParams();
    locations.forEach(location => {
      params = params.append('latitude', location.latitude);
      params = params.append('longitude', location.longitude);
    });
    return this.http.get<HazardAlertsResponse>(this.alertsUrl, {
      headers: new HttpHeaders({ 'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA' }),
      params
    });
  }
}
