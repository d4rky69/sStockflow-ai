export interface GoogleRoutePoint {
  latitude: number;
  longitude: number;
}

export interface GoogleRouteRequest {
  originLatitude: number;
  originLongitude: number;
  destinationLatitude: number;
  destinationLongitude: number;
}

export interface GoogleRouteResponse {
  distanceMeters: number;
  durationSeconds: number;
  points: GoogleRoutePoint[];
  source: 'GOOGLE_ROUTES_API';
}
