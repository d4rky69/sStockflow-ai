import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CreateCustomerOrderRequest, CustomerOrderDetail, CustomerOrderView } from '../models/customer-order.models';

@Injectable({ providedIn: 'root' })
export class CustomerOrderService {
  private readonly baseUrl = `${API_BASE_URL}/api/v1/orders`;

  constructor(private readonly http: HttpClient) {}

  list(): Observable<CustomerOrderView[]> {
    return this.http.get<CustomerOrderView[]>(this.baseUrl, { headers: this.tenantHeaders() });
  }

  create(body: CreateCustomerOrderRequest): Observable<CustomerOrderDetail> {
    return this.http.post<CustomerOrderDetail>(this.baseUrl, body, {
      headers: this.tenantHeaders().set('Idempotency-Key', `web-order-${crypto.randomUUID()}`)
    });
  }

  advance(orderId: string, comment = ''): Observable<CustomerOrderDetail> {
    return this.http.post<CustomerOrderDetail>(`${this.baseUrl}/${encodeURIComponent(orderId)}/advance`, { comment }, {
      headers: this.tenantHeaders()
    });
  }

  private tenantHeaders(): HttpHeaders {
    return new HttpHeaders({ 'X-Tenant-ID': localStorage.getItem('stockflowTenantId') ?? 'TEN-ACME-PHARMA' });
  }
}
