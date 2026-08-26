import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CreateForecastSchedule, ForecastGovernanceAlert, ForecastJob, ForecastSchedule, LatestForecastPosition, QueueForecastJob } from '../models/forecast-operations.models';

@Injectable({providedIn:'root'})
export class ForecastOperationsService {
  private readonly base=`${API_BASE_URL}/api/v1/forecast-operations`;
  constructor(private readonly http:HttpClient){}
  jobs():Observable<ForecastJob[]>{return this.http.get<ForecastJob[]>(`${this.base}/jobs`,{headers:this.headers()});}
  queue(body:QueueForecastJob):Observable<ForecastJob>{return this.http.post<ForecastJob>(`${this.base}/jobs`,body,{headers:this.headers()});}
  processNext():Observable<ForecastJob|null>{return this.http.post<ForecastJob|null>(`${this.base}/jobs/process-next`,{},{headers:this.headers()});}
  cancel(id:string):Observable<ForecastJob>{return this.http.post<ForecastJob>(`${this.base}/jobs/${id}/cancel`,{},{headers:this.headers()});}
  retry(id:string):Observable<ForecastJob>{return this.http.post<ForecastJob>(`${this.base}/jobs/${id}/retry`,{},{headers:this.headers()});}
  schedules():Observable<ForecastSchedule[]>{return this.http.get<ForecastSchedule[]>(`${this.base}/schedules`,{headers:this.headers()});}
  createSchedule(body:CreateForecastSchedule):Observable<ForecastSchedule>{return this.http.post<ForecastSchedule>(`${this.base}/schedules`,body,{headers:this.headers()});}
  setActive(id:string,active:boolean):Observable<ForecastSchedule>{return this.http.post<ForecastSchedule>(`${this.base}/schedules/${id}/active`,{},{headers:this.headers(),params:new HttpParams().set('active',active)});}
  alerts():Observable<ForecastGovernanceAlert[]>{return this.http.get<ForecastGovernanceAlert[]>(`${this.base}/alerts`,{headers:this.headers()});}
  acknowledge(id:string):Observable<ForecastGovernanceAlert>{return this.http.post<ForecastGovernanceAlert>(`${this.base}/alerts/${id}/acknowledge`,{},{headers:this.headers()});}
  latest(limit=50):Observable<LatestForecastPosition[]>{return this.http.get<LatestForecastPosition[]>(`${API_BASE_URL}/api/v1/forecasts/latest`,{headers:this.headers(),params:new HttpParams().set('limit',limit)});}
  private headers(){return new HttpHeaders({'X-Tenant-ID':localStorage.getItem('stockflowTenantId')??'TEN-ACME-PHARMA'});}
}
