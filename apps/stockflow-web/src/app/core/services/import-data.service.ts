import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  ImportErrorView,
  ImportJobView,
  ImportMode,
  ImportPackageType
} from '../models/import.models';

@Injectable({ providedIn: 'root' })
export class ImportDataService {
  constructor(private readonly http: HttpClient) {}

  recentJobs(): Observable<ImportJobView[]> {
    return this.http.get<ImportJobView[]>(`${API_BASE_URL}/api/v1/imports`, {
      headers: this.tenantHeaders()
    });
  }

  job(importJobId: string): Observable<ImportJobView> {
    return this.http.get<ImportJobView>(`${API_BASE_URL}/api/v1/imports/${encodeURIComponent(importJobId)}`, {
      headers: this.tenantHeaders()
    });
  }

  errors(importJobId: string): Observable<ImportErrorView[]> {
    return this.http.get<ImportErrorView[]>(`${API_BASE_URL}/api/v1/imports/${encodeURIComponent(importJobId)}/errors`, {
      headers: this.tenantHeaders()
    });
  }

  upload(
    packageType: ImportPackageType,
    file: File,
    mode: ImportMode,
    strict: boolean
  ): Observable<ImportJobView> {
    const formData = new FormData();
    formData.append('file', file);

    const endpoint = packageType === 'SYNTHETIC_FOUNDATION'
      ? 'synthetic-foundation'
      : 'synthetic-sales';

    const params = new HttpParams()
      .set('mode', mode)
      .set('strict', String(strict));

    return this.http.post<ImportJobView>(`${API_BASE_URL}/api/v1/imports/${endpoint}`, formData, {
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
