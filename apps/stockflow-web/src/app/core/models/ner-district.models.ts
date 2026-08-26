export type DistrictStatusV1 = 'OPEN' | 'CAUTION' | 'RESTRICTED' | 'ISOLATED' | 'NO_DATA';
export type ProvenanceClassification = 'MOCK' | 'HEURISTIC' | 'OFFICIAL' | 'FIELD_REPORT' | 'MANUAL_OVERRIDE';

export interface ProvenanceV1 {
  source: string;
  extractedAt: string;
  validUntil: string;
  confidenceScore: number;
  classification: ProvenanceClassification;
}

export interface DistrictProperties {
  districtId: string;
  name: string;
  status: DistrictStatusV1;
  provenance: ProvenanceV1;
}

export interface DistrictFeature {
  type: 'Feature';
  geometry: any; // GeoJSON geometry
  properties: DistrictProperties;
}

export interface DistrictFeatureCollection {
  type: 'FeatureCollection';
  features: DistrictFeature[];
}
