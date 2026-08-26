import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, Input, OnChanges, OnDestroy, SimpleChanges, ViewChild } from '@angular/core';
import { importLibrary, setOptions } from '@googlemaps/js-api-loader';
import { firstValueFrom } from 'rxjs';
import { FleetbaseVehicle } from '../../core/models/fleetbase.models';
import { HazardAlert, HazardAlertLocation } from '../../core/models/google-weather.models';
import { GoogleRoutesService } from '../../core/services/google-routes.service';
import { GoogleWeatherService } from '../../core/services/google-weather.service';

type DemoMode = 'idle' | 'running' | 'paused' | 'completed';
type MapLayerKey = 'vehicles' | 'prototype' | 'hazards' | 'infrastructure' | 'corridors' | 'checkpoints';

interface MapLayerDefinition {
  key: MapLayerKey;
  label: string;
  description: string;
  tone: string;
  enabled: boolean;
}

@Component({
  selector: 'sf-google-fleet-gis-map',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './google-fleet-gis-map.component.html',
  styleUrls: ['./fleet-gis-map.component.css', './google-fleet-gis-map.component.css']
})
export class GoogleFleetGisMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input({ required: true }) apiKey = '';
  @Input() vehicles: FleetbaseVehicle[] = [];
  @ViewChild('map', { static: true }) mapElement!: ElementRef<HTMLDivElement>;

  loading = true;
  mapError = '';
  demoMode: DemoMode = 'idle';
  demoProgress = 0;
  demoSpeed = 46;
  demoVehicleName = 'StockFlow Prototype Truck';
  routeLoading = false;
  routeError = '';
  routeSource = 'Waiting for Google Routes API';
  routeDurationSeconds = 0;
  routeDistanceMeters = 0;
  hazardAlerts: HazardAlert[] = [];
  hazardLoading = false;
  hazardError = '';
  hazardLocationsMonitored = 0;
  layersPanelOpen = true;
  readonly mapLayers: MapLayerDefinition[] = [
    { key: 'vehicles', label: 'Fleetbase GPS', description: 'Reported vehicle positions', tone: 'green', enabled: true },
    { key: 'prototype', label: 'Prototype live tracker', description: 'Simulated route and truck', tone: 'violet', enabled: true },
    { key: 'hazards', label: 'Flood & landslide alerts', description: 'Official Google Public Alerts', tone: 'red', enabled: true },
    { key: 'infrastructure', label: 'Roads & bridges', description: 'Accessibility checkpoints', tone: 'orange', enabled: true },
    { key: 'corridors', label: 'Essential-supply corridors', description: 'Operational movement lanes', tone: 'blue', enabled: true },
    { key: 'checkpoints', label: 'GIS checkpoints', description: 'Logistics hubs and nodes', tone: 'indigo', enabled: true }
  ];

  private map?: google.maps.Map;
  private infoWindow?: google.maps.InfoWindow;
  private vehicleMarkers = new Map<string, google.maps.Marker>();
  private demoTimer?: number;
  private demoMarker?: google.maps.Marker;
  private demoBaseRoute?: google.maps.Polyline;
  private demoTravelledRoute?: google.maps.Polyline;
  private hazardOverlays: Array<google.maps.Polygon | google.maps.Marker> = [];
  private infrastructureOverlays: Array<google.maps.Marker | google.maps.Polyline> = [];
  private corridorOverlays: google.maps.Polyline[] = [];
  private checkpointOverlays: google.maps.Marker[] = [];
  private readonly fallbackRoute: google.maps.LatLngLiteral[] = [
    { lat: 26.1445, lng: 91.7362 },
    { lat: 26.0368, lng: 91.8856 },
    { lat: 25.9044, lng: 91.8789 },
    { lat: 25.7924, lng: 91.8767 },
    { lat: 25.6758, lng: 91.8933 },
    { lat: 25.5788, lng: 91.8933 }
  ];
  private demoRoute: google.maps.LatLngLiteral[] = [...this.fallbackRoute];
  private routeSignature = '';

  constructor(
    private readonly googleRoutes: GoogleRoutesService,
    private readonly googleWeather: GoogleWeatherService
  ) {}

  get positionedVehicles(): FleetbaseVehicle[] {
    return this.vehicles.filter(vehicle => this.hasUsablePosition(vehicle));
  }

  get waitingVehicles(): number {
    return this.vehicles.length - this.positionedVehicles.length;
  }

  get activeHazards(): number {
    return this.hazardAlerts.filter(alert => alert.phase === 'ACTIVE').length;
  }

  get forecastHazards(): number {
    return this.hazardAlerts.filter(alert => alert.phase === 'FORECAST').length;
  }

  get demoEtaMinutes(): number {
    const remaining = 1 - this.demoProgress / 100;
    if (this.routeDurationSeconds > 0) return Math.max(0, Math.round(this.routeDurationSeconds * remaining / 60));
    return Math.max(0, Math.round((this.demoRouteLengthKm * remaining / Math.max(this.demoSpeed, 1)) * 60));
  }

  get demoRouteLengthKm(): number {
    return this.routeDistanceMeters > 0 ? this.routeDistanceMeters / 1000 : this.routeDistance(this.demoRoute);
  }

  get nextCheckpoint(): string {
    if (this.demoProgress < 35) return 'Nongpoh checkpoint';
    if (this.demoProgress < 75) return 'Umiam bridge corridor';
    if (this.demoProgress < 100) return 'Shillong relief hub';
    return 'Delivery completed';
  }

  async ngAfterViewInit(): Promise<void> {
    this.restoreLayerState();
    await this.initializeMap();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['vehicles'] && this.map) this.renderVehicles();
  }

  ngOnDestroy(): void {
    this.stopDemoTimer();
    this.clearVehicleMarkers();
    this.demoMarker?.setMap(null);
    this.demoBaseRoute?.setMap(null);
    this.demoTravelledRoute?.setMap(null);
    this.clearHazardOverlays();
    this.clearStaticOverlays();
  }

  toggleLayer(key: MapLayerKey): void {
    const layer = this.mapLayers.find(candidate => candidate.key === key);
    if (!layer) return;
    layer.enabled = !layer.enabled;
    this.applyLayerVisibility(key);
    this.saveLayerState();
  }

  setAllLayers(enabled: boolean): void {
    this.mapLayers.forEach(layer => layer.enabled = enabled);
    this.mapLayers.forEach(layer => this.applyLayerVisibility(layer.key));
    this.saveLayerState();
  }

  layerCount(key: MapLayerKey): number {
    if (key === 'vehicles') return this.positionedVehicles.length;
    if (key === 'prototype') return this.demoMode === 'idle' ? 0 : 1;
    if (key === 'hazards') return this.hazardAlerts.length;
    if (key === 'infrastructure') return this.infrastructureOverlays.length;
    if (key === 'corridors') return this.corridorOverlays.length;
    return this.checkpointOverlays.length;
  }

  async startDemo(vehicle?: FleetbaseVehicle): Promise<void> {
    if (!this.map) return;
    this.enableLayer('prototype');
    if (vehicle) this.demoVehicleName = vehicle.name || vehicle.plateNumber || vehicle.internalId || 'StockFlow Prototype Truck';
    if (this.demoMode === 'completed') this.resetDemo(false);
    if (this.demoMode !== 'paused') await this.loadRoadRoute(vehicle);
    this.demoMode = 'running';
    this.renderDemoPosition();
    this.fitDemoRoute();
    this.stopDemoTimer();
    this.demoTimer = window.setInterval(() => {
      this.demoProgress = Math.min(100, this.demoProgress + .8);
      this.demoSpeed = Math.round(43 + Math.sin(this.demoProgress / 8) * 8);
      this.renderDemoPosition();
      if (this.demoProgress >= 100) {
        this.demoMode = 'completed';
        this.stopDemoTimer();
      }
    }, 500);
  }

  pauseDemo(): void {
    if (this.demoMode !== 'running') return;
    this.demoMode = 'paused';
    this.stopDemoTimer();
  }

  resetDemo(returnToNetwork = true): void {
    this.stopDemoTimer();
    this.demoMode = 'idle';
    this.demoProgress = 0;
    this.demoSpeed = 46;
    this.demoMarker?.setMap(null);
    this.demoBaseRoute?.setMap(null);
    this.demoTravelledRoute?.setMap(null);
    this.demoMarker = undefined;
    this.demoBaseRoute = undefined;
    this.demoTravelledRoute = undefined;
    if (returnToNetwork) this.fitToNetwork();
  }

  private async loadRoadRoute(vehicle?: FleetbaseVehicle): Promise<void> {
    const origin = vehicle && this.hasUsablePosition(vehicle)
      ? { lat: vehicle!.latitude!, lng: vehicle!.longitude! }
      : { lat: 26.1445, lng: 91.7362 };
    const destination = { lat: 25.5788, lng: 91.8933 };
    const signature = `${origin.lat},${origin.lng}:${destination.lat},${destination.lng}`;
    if (this.routeSignature === signature && this.routeSource === 'Google Routes API') return;

    this.routeLoading = true;
    this.routeError = '';
    this.routeSource = 'Computing traffic-aware driving route…';
    try {
      const route = await firstValueFrom(this.googleRoutes.computeRoute({
        originLatitude: origin.lat,
        originLongitude: origin.lng,
        destinationLatitude: destination.lat,
        destinationLongitude: destination.lng
      }));
      if (!route.points || route.points.length < 2) throw new Error('The route response did not contain enough road points.');
      this.demoRoute = route.points.map(point => ({ lat: point.latitude, lng: point.longitude }));
      this.routeDistanceMeters = route.distanceMeters;
      this.routeDurationSeconds = route.durationSeconds;
      this.routeSignature = signature;
      this.routeSource = 'Google Routes API';
    } catch (error: any) {
      this.demoRoute = [...this.fallbackRoute];
      this.routeDistanceMeters = 0;
      this.routeDurationSeconds = 0;
      this.routeSignature = '';
      this.routeSource = 'Fallback prototype route';
      this.routeError = error?.error?.message || error?.message || 'Google Routes API was unavailable; the fallback route is being used.';
    } finally {
      this.routeLoading = false;
    }
  }

  trackVehicle(vehicle: FleetbaseVehicle): void {
    this.mapElement.nativeElement.closest('.gis-shell')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    if (this.hasUsablePosition(vehicle)) {
      this.enableLayer('vehicles');
      this.resetDemo(false);
      this.map?.panTo({ lat: vehicle.latitude!, lng: vehicle.longitude! });
      this.map?.setZoom(14);
      const marker = this.vehicleMarkers.get(vehicle.id);
      if (marker) window.setTimeout(() => google.maps.event.trigger(marker, 'click'), 500);
    } else {
      this.enableLayer('prototype');
      this.startDemo(vehicle);
    }
  }

  fitToNetwork(): void {
    if (!this.map) return;
    const bounds = new google.maps.LatLngBounds();
    if (this.positionedVehicles.length) {
      this.positionedVehicles.forEach(vehicle => bounds.extend({ lat: vehicle.latitude!, lng: vehicle.longitude! }));
    } else {
      bounds.extend({ lat: 23.6, lng: 88.4 });
      bounds.extend({ lat: 28.2, lng: 96.2 });
    }
    this.map.fitBounds(bounds, 50);
  }

  async loadHazardAlerts(): Promise<void> {
    if (!this.map || this.hazardLoading) return;
    this.hazardLoading = true;
    this.hazardError = '';
    const locations = this.hazardMonitoringLocations();
    try {
      const response = await firstValueFrom(this.googleWeather.hazardAlerts(locations));
      this.hazardAlerts = response.alerts;
      this.hazardLocationsMonitored = response.monitoredLocations;
      this.renderHazardAlerts();
    } catch (error: any) {
      this.hazardAlerts = [];
      this.hazardLocationsMonitored = locations.length;
      this.clearHazardOverlays();
      this.hazardError = error?.error?.message || 'Google Public Alerts could not be loaded.';
    } finally {
      this.hazardLoading = false;
    }
  }

  private async initializeMap(): Promise<void> {
    try {
      if (!this.apiKey) throw new Error('Google Maps browser key is not configured.');
      setOptions({ key: this.apiKey, v: 'weekly', language: 'en', region: 'IN' });
      await importLibrary('maps');
      this.map = new google.maps.Map(this.mapElement.nativeElement, {
        center: { lat: 25.55, lng: 92.55 },
        zoom: 6,
        mapTypeId: google.maps.MapTypeId.ROADMAP,
        mapTypeControl: true,
        streetViewControl: false,
        fullscreenControl: true,
        gestureHandling: 'greedy',
        styles: [
          { featureType: 'poi.business', stylers: [{ visibility: 'off' }] },
          { featureType: 'transit', stylers: [{ visibility: 'simplified' }] }
        ]
      });
      this.infoWindow = new google.maps.InfoWindow();
      this.addGisLayers();
      this.renderVehicles();
      this.loading = false;
      await this.loadHazardAlerts();
    } catch (error) {
      this.loading = false;
      this.mapError = error instanceof Error ? error.message : 'Google Maps could not be loaded.';
    }
  }

  private addGisLayers(): void {
    if (!this.map) return;
    this.clearStaticOverlays();
    const assets = [
      { name: 'Guwahati logistics hub', position: { lat: 26.1445, lng: 91.7362 }, detail: 'Assam · Accessible' },
      { name: 'Shillong corridor checkpoint', position: { lat: 25.5788, lng: 91.8933 }, detail: 'Meghalaya · Monitoring' },
      { name: 'Imphal supply node', position: { lat: 24.817, lng: 93.9368 }, detail: 'Manipur · Accessible' },
      { name: 'Teesta bridge checkpoint', position: { lat: 27.187, lng: 88.505 }, detail: 'Sikkim · Inspection due' }
    ];
    assets.forEach(asset => {
      const marker = new google.maps.Marker({ map: this.layerMap('checkpoints'), position: asset.position, title: asset.name, label: { text: '◆', color: '#ffffff', fontSize: '12px' }, icon: this.circleIcon('#2563eb', 15) });
      marker.addListener('click', () => this.openInfo(asset.position, `<strong>${asset.name}</strong><br>${asset.detail}`));
      this.checkpointOverlays.push(marker);
    });

    [
      { points: [{ lat: 26.1445, lng: 91.7362 }, { lat: 25.91, lng: 91.88 }, { lat: 25.5788, lng: 91.8933 }], color: '#5b3ee5' },
      { points: [{ lat: 26.1445, lng: 91.7362 }, { lat: 25.62, lng: 92.82 }, { lat: 24.817, lng: 93.9368 }], color: '#2563eb' }
    ].forEach(corridor => this.corridorOverlays.push(new google.maps.Polyline({ map: this.layerMap('corridors'), path: corridor.points, strokeColor: corridor.color, strokeWeight: 4, strokeOpacity: .72 })));

    const infrastructure = [
      { name: 'Nongpoh road accessibility post', position: { lat: 25.9044, lng: 91.8789 }, detail: 'NH-6 · Road condition monitoring' },
      { name: 'Umiam bridge corridor', position: { lat: 25.6758, lng: 91.8933 }, detail: 'Bridge approach · Accessibility checkpoint' },
      { name: 'Teesta bridge inspection', position: { lat: 27.187, lng: 88.505 }, detail: 'Sikkim · Inspection due' }
    ];
    infrastructure.forEach(asset => {
      const marker = new google.maps.Marker({
        map: this.layerMap('infrastructure'), position: asset.position, title: asset.name,
        label: { text: '＋', color: '#ffffff', fontSize: '12px', fontWeight: '700' }, icon: this.circleIcon('#f59e0b', 13)
      });
      marker.addListener('click', () => this.openInfo(asset.position, `<strong>${asset.name}</strong><br>${asset.detail}`));
      this.infrastructureOverlays.push(marker);
    });
  }

  private renderHazardAlerts(): void {
    if (!this.map) return;
    this.clearHazardOverlays();
    this.hazardAlerts.forEach(alert => {
      const paths = this.geoJsonPaths(alert.polygonGeoJson);
      const color = this.hazardColor(alert);
      if (paths.length) {
        paths.forEach(path => {
          const polygon = new google.maps.Polygon({
            map: this.layerMap('hazards'),
            paths: path,
            strokeColor: color,
            strokeOpacity: alert.phase === 'FORECAST' ? .65 : .95,
            strokeWeight: alert.phase === 'FORECAST' ? 2 : 3,
            fillColor: color,
            fillOpacity: alert.phase === 'FORECAST' ? .10 : .20,
            zIndex: 20
          });
          polygon.addListener('click', (event: google.maps.MapMouseEvent) => this.openInfo(
            event.latLng ?? { lat: alert.matchedLatitude, lng: alert.matchedLongitude },
            this.hazardPopup(alert)
          ));
          this.hazardOverlays.push(polygon);
        });
      } else {
        const marker = new google.maps.Marker({
          map: this.layerMap('hazards'),
          position: { lat: alert.matchedLatitude, lng: alert.matchedLongitude },
          title: alert.title,
          label: { text: '!', color: '#ffffff', fontWeight: '700' },
          icon: this.circleIcon(color, 17),
          zIndex: 40
        });
        marker.addListener('click', () => this.openInfo(marker.getPosition()!, this.hazardPopup(alert)));
        this.hazardOverlays.push(marker);
      }
    });
  }

  private hazardMonitoringLocations(): HazardAlertLocation[] {
    const candidates: HazardAlertLocation[] = [
      { latitude: 26.1445, longitude: 91.7362 },
      { latitude: 25.5788, longitude: 91.8933 },
      { latitude: 27.187, longitude: 88.505 },
      { latitude: 24.817, longitude: 93.9368 },
      ...this.positionedVehicles.map(vehicle => ({ latitude: vehicle.latitude!, longitude: vehicle.longitude! }))
    ];
    return candidates.filter((location, index, all) =>
      all.findIndex(other => Math.abs(other.latitude - location.latitude) < .001 && Math.abs(other.longitude - location.longitude) < .001) === index
    ).slice(0, 8);
  }

  private geoJsonPaths(value: string | null): google.maps.LatLngLiteral[][][] {
    if (!value) return [];
    try {
      const geometry = JSON.parse(value);
      const polygons = geometry?.type === 'Polygon'
        ? [geometry.coordinates]
        : geometry?.type === 'MultiPolygon' ? geometry.coordinates : [];
      return polygons.map((polygon: number[][][]) => polygon.map((ring: number[][]) =>
        ring.filter(point => Array.isArray(point) && point.length >= 2 && Number.isFinite(point[0]) && Number.isFinite(point[1]))
          .map(point => ({ lat: point[1], lng: point[0] }))
      )).filter((polygon: google.maps.LatLngLiteral[][]) => polygon.some(ring => ring.length >= 3));
    } catch {
      return [];
    }
  }

  private hazardColor(alert: HazardAlert): string {
    if (alert.severity === 'EXTREME' || alert.severity === 'SEVERE') return '#dc2626';
    return alert.hazardType === 'LANDSLIDE' ? '#f59e0b' : '#2563eb';
  }

  private hazardPopup(alert: HazardAlert): string {
    const source = alert.dataSourceUri
      ? `<a href="${this.escape(alert.dataSourceUri)}" target="_blank" rel="noopener">${this.escape(alert.dataSourceName)}</a>`
      : this.escape(alert.dataSourceName);
    const timing = alert.phase === 'FORECAST' ? 'Future/expected alert' : 'Active alert';
    const description = alert.description ? `<br><small>${this.escape(alert.description.slice(0, 420)).replace(/\n/g, '<br>')}</small>` : '';
    const instruction = alert.instruction ? `<br><b>Action:</b> ${this.escape(alert.instruction.slice(0, 280))}` : '';
    return `<strong>${this.escape(alert.title)}</strong><br>` +
      `${this.escape(alert.hazardType)} · ${this.escape(alert.severity)} · ${timing}<br>` +
      `${this.escape(alert.areaName)}${description}${instruction}<br>` +
      `<small>Source: ${source}</small>`;
  }

  private clearHazardOverlays(): void {
    this.hazardOverlays.forEach(overlay => overlay.setMap(null));
    this.hazardOverlays = [];
  }

  private renderVehicles(): void {
    if (!this.map) return;
    this.clearVehicleMarkers();
    this.positionedVehicles.forEach(vehicle => {
      const marker = new google.maps.Marker({
        map: this.layerMap('vehicles'),
        position: { lat: vehicle.latitude!, lng: vehicle.longitude! },
        title: vehicle.name || vehicle.plateNumber || vehicle.id,
        label: { text: '🚚', fontSize: '18px' },
        icon: this.circleIcon(vehicle.online ? '#22c55e' : '#f59e0b', 19)
      });
      marker.addListener('click', () => this.openInfo(
        marker.getPosition()!,
        `<strong>${this.escape(vehicle.name || vehicle.plateNumber || 'Fleet vehicle')}</strong><br>` +
        `${this.escape(vehicle.plateNumber || vehicle.internalId || vehicle.id)}<br>` +
        `Status: ${this.escape(vehicle.online ? 'Online' : vehicle.status || 'Offline')} · Speed: ${vehicle.speed ?? 'Not reported'} km/h<br>` +
        `<small>Fleetbase GPS${vehicle.positionUpdatedAt ? ` · ${this.escape(vehicle.positionUpdatedAt)}` : ''}</small>`
      ));
      this.vehicleMarkers.set(vehicle.id, marker);
    });
  }

  private renderDemoPosition(): void {
    if (!this.map) return;
    const position = this.positionAlongRoute(this.demoProgress / 100);
    if (!this.demoBaseRoute) this.demoBaseRoute = new google.maps.Polyline({ map: this.layerMap('prototype'), path: this.demoRoute, strokeColor: '#94a3b8', strokeWeight: 5, strokeOpacity: .55 });
    if (!this.demoTravelledRoute) this.demoTravelledRoute = new google.maps.Polyline({ map: this.layerMap('prototype'), strokeColor: '#16a34a', strokeWeight: 6, strokeOpacity: .9 });
    this.demoTravelledRoute.setPath(this.travelledRoute(this.demoProgress / 100));
    if (!this.demoMarker) {
      this.demoMarker = new google.maps.Marker({ map: this.layerMap('prototype'), position, title: this.demoVehicleName, label: { text: '🚚', fontSize: '20px' }, icon: this.circleIcon('#22c55e', 21), zIndex: 50 });
      this.demoMarker.addListener('click', () => this.openInfo(this.demoMarker!.getPosition()!, `<strong>${this.escape(this.demoVehicleName)}</strong><br>Accelerated live-tracking demo`));
    } else {
      this.demoMarker.setPosition(position);
      this.demoMarker.setTitle(this.demoVehicleName);
    }
  }

  private fitDemoRoute(): void {
    if (!this.map) return;
    const bounds = new google.maps.LatLngBounds();
    this.demoRoute.forEach(point => bounds.extend(point));
    this.map.fitBounds(bounds, 70);
  }

  private positionAlongRoute(progress: number): google.maps.LatLngLiteral {
    const target = this.routeDistance(this.demoRoute) * Math.min(1, Math.max(0, progress));
    let travelled = 0;
    for (let index = 1; index < this.demoRoute.length; index++) {
      const start = this.demoRoute[index - 1];
      const end = this.demoRoute[index];
      const segment = this.distanceKm(start, end);
      if (travelled + segment >= target) {
        const fraction = segment ? (target - travelled) / segment : 0;
        return { lat: start.lat + (end.lat - start.lat) * fraction, lng: start.lng + (end.lng - start.lng) * fraction };
      }
      travelled += segment;
    }
    return this.demoRoute[this.demoRoute.length - 1];
  }

  private travelledRoute(progress: number): google.maps.LatLngLiteral[] {
    const target = this.routeDistance(this.demoRoute) * Math.min(1, Math.max(0, progress));
    const points = [this.demoRoute[0]];
    let travelled = 0;
    for (let index = 1; index < this.demoRoute.length; index++) {
      const segment = this.distanceKm(this.demoRoute[index - 1], this.demoRoute[index]);
      if (travelled + segment <= target) {
        points.push(this.demoRoute[index]);
        travelled += segment;
      } else {
        points.push(this.positionAlongRoute(progress));
        break;
      }
    }
    return points;
  }

  private routeDistance(route: google.maps.LatLngLiteral[]): number {
    return route.slice(1).reduce((total, point, index) => total + this.distanceKm(route[index], point), 0);
  }

  private distanceKm(start: google.maps.LatLngLiteral, end: google.maps.LatLngLiteral): number {
    const radians = (degrees: number) => degrees * Math.PI / 180;
    const lat = radians(end.lat - start.lat);
    const lng = radians(end.lng - start.lng);
    const a = Math.sin(lat / 2) ** 2 + Math.cos(radians(start.lat)) * Math.cos(radians(end.lat)) * Math.sin(lng / 2) ** 2;
    return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private circleIcon(color: string, scale: number): google.maps.Symbol {
    return { path: google.maps.SymbolPath.CIRCLE, scale, fillColor: color, fillOpacity: .95, strokeColor: '#ffffff', strokeWeight: 3 };
  }

  private openInfo(position: google.maps.LatLng | google.maps.LatLngLiteral, content: string): void {
    this.infoWindow?.setPosition(position);
    this.infoWindow?.setContent(content);
    this.infoWindow?.open({ map: this.map });
  }

  private clearVehicleMarkers(): void {
    this.vehicleMarkers.forEach(marker => marker.setMap(null));
    this.vehicleMarkers.clear();
  }

  private layerMap(key: MapLayerKey): google.maps.Map | null {
    return this.mapLayers.find(layer => layer.key === key)?.enabled ? this.map ?? null : null;
  }

  private enableLayer(key: MapLayerKey): void {
    const layer = this.mapLayers.find(candidate => candidate.key === key);
    if (!layer || layer.enabled) return;
    layer.enabled = true;
    this.applyLayerVisibility(key);
    this.saveLayerState();
  }

  private applyLayerVisibility(key: MapLayerKey): void {
    const map = this.layerMap(key);
    if (key === 'vehicles') this.vehicleMarkers.forEach(marker => marker.setMap(map));
    if (key === 'prototype') [this.demoMarker, this.demoBaseRoute, this.demoTravelledRoute].forEach(overlay => overlay?.setMap(map));
    if (key === 'hazards') this.hazardOverlays.forEach(overlay => overlay.setMap(map));
    if (key === 'infrastructure') this.infrastructureOverlays.forEach(overlay => overlay.setMap(map));
    if (key === 'corridors') this.corridorOverlays.forEach(overlay => overlay.setMap(map));
    if (key === 'checkpoints') this.checkpointOverlays.forEach(overlay => overlay.setMap(map));
    if (!map) this.infoWindow?.close();
  }

  private clearStaticOverlays(): void {
    this.infrastructureOverlays.forEach(overlay => overlay.setMap(null));
    this.corridorOverlays.forEach(overlay => overlay.setMap(null));
    this.checkpointOverlays.forEach(overlay => overlay.setMap(null));
    this.infrastructureOverlays = [];
    this.corridorOverlays = [];
    this.checkpointOverlays = [];
  }

  private restoreLayerState(): void {
    try {
      const value = JSON.parse(localStorage.getItem('stockflowGoogleMapLayers') || '{}') as Partial<Record<MapLayerKey, boolean>>;
      this.mapLayers.forEach(layer => {
        if (typeof value[layer.key] === 'boolean') layer.enabled = value[layer.key]!;
      });
    } catch {
      localStorage.removeItem('stockflowGoogleMapLayers');
    }
  }

  private saveLayerState(): void {
    const value = Object.fromEntries(this.mapLayers.map(layer => [layer.key, layer.enabled]));
    localStorage.setItem('stockflowGoogleMapLayers', JSON.stringify(value));
  }

  private hasUsablePosition(vehicle: FleetbaseVehicle): boolean {
    return vehicle.latitude !== null && vehicle.latitude !== undefined && vehicle.longitude !== null && vehicle.longitude !== undefined
      && Number.isFinite(vehicle.latitude) && Number.isFinite(vehicle.longitude)
      && Math.abs(vehicle.latitude) <= 90 && Math.abs(vehicle.longitude) <= 180
      && !(vehicle.latitude === 0 && vehicle.longitude === 0);
  }

  private stopDemoTimer(): void {
    if (this.demoTimer !== undefined) window.clearInterval(this.demoTimer);
    this.demoTimer = undefined;
  }

  private escape(value: string): string {
    return value.replace(/[&<>'"]/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character] || character);
  }
}
