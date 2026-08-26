import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DistrictFeatureCollection } from '../models/ner-district.models';

@Injectable({
  providedIn: 'root'
})
export class NerContractAdapterService {
  private readonly DISTRICTS_URL = 'assets/data/districts.json';

  constructor(private readonly http: HttpClient) {}

  /**
   * Fetches the strictly-typed district registry and forecast geometries.
   * This simulates the GET /api/v1/ner/districts endpoint for the prototype.
   */
  getDistrictForecasts(): Observable<DistrictFeatureCollection> {
    return this.http.get<DistrictFeatureCollection>(this.DISTRICTS_URL);
  }
}
