export interface RouteWeatherForecast {
  locationLabel: string;
  forecastTime: string | null;
  condition: string;
  iconUrl: string | null;
  temperatureCelsius: number;
  feelsLikeCelsius: number;
  precipitationProbabilityPercent: number;
  precipitationMillimeters: number;
  thunderstormProbabilityPercent: number;
  windSpeedKph: number;
  windGustKph: number;
  visibilityKm: number;
  humidityPercent: number;
  riskScore: number;
  riskLevel: 'LOW' | 'MODERATE' | 'HIGH';
  operationalAdvice: string;
  source: 'GOOGLE_WEATHER_API';
  attribution: string;
}

export interface HazardAlertLocation {
  latitude: number;
  longitude: number;
}

export interface HazardAlert {
  id: string;
  title: string;
  eventType: string;
  hazardType: 'FLOOD' | 'LANDSLIDE';
  areaName: string;
  polygonGeoJson: string | null;
  severity: string;
  certainty: string;
  urgency: string;
  description: string | null;
  instruction: string | null;
  startTime: string | null;
  expirationTime: string | null;
  phase: 'ACTIVE' | 'FORECAST';
  matchedLatitude: number;
  matchedLongitude: number;
  dataSourceName: string;
  dataSourceUri: string | null;
}

export interface HazardAlertsResponse {
  alerts: HazardAlert[];
  count: number;
  monitoredLocations: number;
  regionCodes: string[];
  source: 'GOOGLE_WEATHER_PUBLIC_ALERTS';
  disclaimer: string;
}
