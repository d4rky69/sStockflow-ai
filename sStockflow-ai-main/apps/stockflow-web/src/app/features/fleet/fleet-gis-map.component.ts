import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ElementRef, Input, OnChanges, OnDestroy, SimpleChanges, ViewChild } from '@angular/core';
import * as L from 'leaflet';
import { FleetbaseVehicle } from '../../core/models/fleetbase.models';

@Component({
  selector: 'sf-leaflet-fleet-gis-map',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './fleet-gis-map.component.html',
  styleUrl: './fleet-gis-map.component.css'
})
export class LeafletFleetGisMapComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() vehicles: FleetbaseVehicle[] = [];
  @ViewChild('map', { static: true }) mapElement!: ElementRef<HTMLDivElement>;

  private map?: L.Map;
  private readonly vehicleLayer = L.layerGroup();
  private readonly riskLayer = L.layerGroup();
  private readonly infrastructureLayer = L.layerGroup();
  private readonly routeLayer = L.layerGroup();
  private readonly demoLayer = L.layerGroup();
  private readonly vehicleMarkers = new Map<string, L.Marker>();
  private demoTimer?: number;
  private readonly demoRoute: L.LatLngTuple[] = [
    [26.1445, 91.7362],
    [26.0368, 91.8856],
    [25.9044, 91.8789],
    [25.7924, 91.8767],
    [25.6758, 91.8933],
    [25.5788, 91.8933]
  ];

  demoMode: 'idle' | 'running' | 'paused' | 'completed' = 'idle';
  demoProgress = 0;
  demoSpeed = 46;
  demoPosition: L.LatLng = L.latLng(this.demoRoute[0]);
  demoVehicleName = 'StockFlow Prototype Truck';

  get positionedVehicles(): FleetbaseVehicle[] {
    return this.vehicles.filter(vehicle => this.hasUsablePosition(vehicle));
  }

  get waitingVehicles(): number {
    return this.vehicles.length - this.positionedVehicles.length;
  }

  get demoEtaMinutes(): number {
    return Math.max(0, Math.round((this.demoRouteLengthKm * (1 - this.demoProgress / 100) / Math.max(this.demoSpeed, 1)) * 60));
  }

  get demoRouteLengthKm(): number {
    return this.routeDistance(this.demoRoute) / 1000;
  }

  get nextCheckpoint(): string {
    if (this.demoProgress < 35) return 'Nongpoh checkpoint';
    if (this.demoProgress < 75) return 'Umiam bridge corridor';
    if (this.demoProgress < 100) return 'Shillong relief hub';
    return 'Delivery completed';
  }

  ngAfterViewInit(): void {
    this.initializeMap();
    this.renderVehicles();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['vehicles'] && this.map) this.renderVehicles();
  }

  ngOnDestroy(): void {
    this.stopDemoTimer();
    this.map?.remove();
  }

  startDemo(vehicle?: FleetbaseVehicle): void {
    if (vehicle) this.demoVehicleName = vehicle.name || vehicle.plateNumber || vehicle.internalId || 'StockFlow Prototype Truck';
    if (this.demoMode === 'completed') this.resetDemo(false);
    this.demoMode = 'running';
    this.renderDemoPosition();
    this.map?.fitBounds(L.latLngBounds(this.demoRoute).pad(.28), { maxZoom: 10 });
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
    this.demoPosition = L.latLng(this.demoRoute[0]);
    this.demoLayer.clearLayers();
    if (returnToNetwork) this.fitToNetwork();
  }

  trackVehicle(vehicle: FleetbaseVehicle): void {
    this.mapElement.nativeElement.closest('.gis-shell')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    if (this.hasUsablePosition(vehicle)) {
      this.resetDemo(false);
      this.map?.setView([vehicle.latitude!, vehicle.longitude!], 13, { animate: true });
      window.setTimeout(() => this.vehicleMarkers.get(vehicle.id)?.openPopup(), 450);
    } else {
      this.startDemo(vehicle);
    }
  }

  fitToNetwork(): void {
    if (!this.map) return;
    const positions = this.positionedVehicles.map(vehicle => L.latLng(vehicle.latitude!, vehicle.longitude!));
    if (positions.length) {
      this.map.fitBounds(L.latLngBounds(positions).pad(.35), { maxZoom: 12 });
    } else {
      this.map.setView([25.55, 92.55], 6);
    }
  }

  private initializeMap(): void {
    this.map = L.map(this.mapElement.nativeElement, {
      center: [25.55, 92.55],
      zoom: 6,
      minZoom: 4,
      maxZoom: 18,
      zoomControl: false
    });

    L.control.zoom({ position: 'bottomright' }).addTo(this.map);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors'
    }).addTo(this.map);

    this.addGisLayers();
    this.vehicleLayer.addTo(this.map);
    this.riskLayer.addTo(this.map);
    this.infrastructureLayer.addTo(this.map);
    this.routeLayer.addTo(this.map);
    this.demoLayer.addTo(this.map);
    L.control.layers(undefined, {
      'Fleetbase GPS': this.vehicleLayer,
      'Prototype live tracker': this.demoLayer,
      'Risk overlays (demo)': this.riskLayer,
      'Roads & bridges': this.infrastructureLayer,
      'Essential-supply corridors': this.routeLayer
    }, { collapsed: false, position: 'topright' }).addTo(this.map);

    setTimeout(() => this.map?.invalidateSize(), 0);
  }

  private addGisLayers(): void {
    const riskZones = [
      { name: 'Assam flood watch', point: [26.37, 92.68] as L.LatLngTuple, radius: 42000, level: 'High', color: '#ef4444' },
      { name: 'Meghalaya landslide watch', point: [25.48, 91.35] as L.LatLngTuple, radius: 28000, level: 'Medium', color: '#f59e0b' },
      { name: 'Sikkim slope-risk watch', point: [27.32, 88.58] as L.LatLngTuple, radius: 21000, level: 'High', color: '#ef4444' }
    ];
    riskZones.forEach(zone => L.circle(zone.point, {
      radius: zone.radius,
      color: zone.color,
      weight: 2,
      fillColor: zone.color,
      fillOpacity: .14
    }).bindPopup(`<strong>${zone.name}</strong><br>${zone.level} demo risk overlay<br><small>Connect forecast APIs for live scoring.</small>`).addTo(this.riskLayer));

    const assets = [
      { name: 'Guwahati logistics hub', point: [26.1445, 91.7362] as L.LatLngTuple, state: 'Assam', status: 'Accessible' },
      { name: 'Shillong corridor checkpoint', point: [25.5788, 91.8933] as L.LatLngTuple, state: 'Meghalaya', status: 'Monitoring' },
      { name: 'Imphal supply node', point: [24.817, 93.9368] as L.LatLngTuple, state: 'Manipur', status: 'Accessible' },
      { name: 'Teesta bridge checkpoint', point: [27.187, 88.505] as L.LatLngTuple, state: 'Sikkim', status: 'Inspection due' }
    ];
    assets.forEach(asset => L.marker(asset.point, {
      icon: this.divIcon('gis-asset-marker', '&#9670;')
    }).bindPopup(`<strong>${asset.name}</strong><br>${asset.state}<br>Status: ${asset.status}`).addTo(this.infrastructureLayer));

    const corridors: Array<{ name: string; points: L.LatLngExpression[]; color: string }> = [
      { name: 'Guwahati → Shillong essential supplies', points: [[26.1445, 91.7362], [25.91, 91.88], [25.5788, 91.8933]], color: '#5b3ee5' },
      { name: 'Guwahati → Imphal medical corridor', points: [[26.1445, 91.7362], [25.62, 92.82], [24.817, 93.9368]], color: '#2563eb' }
    ];
    corridors.forEach(corridor => L.polyline(corridor.points, {
      color: corridor.color,
      weight: 4,
      opacity: .72,
      dashArray: '9 8'
    }).bindPopup(`<strong>${corridor.name}</strong><br>Illustrative corridor for GIS prototype.`).addTo(this.routeLayer));
  }

  private renderVehicles(): void {
    this.vehicleLayer.clearLayers();
    this.vehicleMarkers.clear();
    this.positionedVehicles.forEach(vehicle => {
      const status = vehicle.online ? 'Online' : (vehicle.status || 'Offline');
      const speed = vehicle.speed === null || vehicle.speed === undefined ? 'Not reported' : `${vehicle.speed.toFixed(0)} km/h`;
      const marker = L.marker([vehicle.latitude!, vehicle.longitude!], {
        icon: this.divIcon(vehicle.online ? 'gps-vehicle-marker online' : 'gps-vehicle-marker', '&#128666;'),
        title: vehicle.name || vehicle.plateNumber || vehicle.id
      }).bindPopup(
        `<strong>${this.escape(vehicle.name || vehicle.plateNumber || 'Fleet vehicle')}</strong><br>` +
        `${this.escape(vehicle.plateNumber || vehicle.internalId || vehicle.id)}<br>` +
        `Status: ${this.escape(status)} · Speed: ${speed}<br>` +
        `<small>Fleetbase GPS${vehicle.positionUpdatedAt ? ` · ${this.escape(vehicle.positionUpdatedAt)}` : ''}</small>`
      ).addTo(this.vehicleLayer);
      this.vehicleMarkers.set(vehicle.id, marker);
    });
  }

  private renderDemoPosition(): void {
    this.demoPosition = this.positionAlongRoute(this.demoProgress / 100);
    this.demoLayer.clearLayers();
    const travelled = this.travelledRoute(this.demoProgress / 100);
    L.polyline(this.demoRoute, { color: '#94a3b8', weight: 5, opacity: .55, dashArray: '7 9' }).addTo(this.demoLayer);
    L.polyline(travelled, { color: '#16a34a', weight: 6, opacity: .9 }).addTo(this.demoLayer);
    L.marker(this.demoPosition, {
      icon: this.divIcon('gps-vehicle-marker online demo-pulse', '&#128666;'),
      title: 'StockFlow Prototype Truck'
    }).bindPopup(
      `<strong>${this.escape(this.demoVehicleName)}</strong><br>` +
      `Accelerated live-tracking demo<br>` +
      `Speed: ${this.demoSpeed} km/h · Progress: ${this.demoProgress.toFixed(0)}%<br>` +
      `<small>Next: ${this.nextCheckpoint}</small>`
    ).addTo(this.demoLayer);
  }

  private positionAlongRoute(progress: number): L.LatLng {
    const target = this.routeDistance(this.demoRoute) * Math.min(1, Math.max(0, progress));
    let travelled = 0;
    for (let index = 1; index < this.demoRoute.length; index++) {
      const start = L.latLng(this.demoRoute[index - 1]);
      const end = L.latLng(this.demoRoute[index]);
      const segment = start.distanceTo(end);
      if (travelled + segment >= target) {
        const fraction = segment ? (target - travelled) / segment : 0;
        return L.latLng(
          start.lat + (end.lat - start.lat) * fraction,
          start.lng + (end.lng - start.lng) * fraction
        );
      }
      travelled += segment;
    }
    return L.latLng(this.demoRoute[this.demoRoute.length - 1]);
  }

  private travelledRoute(progress: number): L.LatLngExpression[] {
    const target = this.routeDistance(this.demoRoute) * Math.min(1, Math.max(0, progress));
    const points: L.LatLngExpression[] = [this.demoRoute[0]];
    let travelled = 0;
    for (let index = 1; index < this.demoRoute.length; index++) {
      const start = L.latLng(this.demoRoute[index - 1]);
      const end = L.latLng(this.demoRoute[index]);
      const segment = start.distanceTo(end);
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

  private routeDistance(route: L.LatLngTuple[]): number {
    return route.slice(1).reduce((total, point, index) => total + L.latLng(route[index]).distanceTo(L.latLng(point)), 0);
  }

  private stopDemoTimer(): void {
    if (this.demoTimer !== undefined) window.clearInterval(this.demoTimer);
    this.demoTimer = undefined;
  }

  private hasUsablePosition(vehicle: FleetbaseVehicle): boolean {
    const latitude = vehicle.latitude;
    const longitude = vehicle.longitude;
    return latitude !== null && latitude !== undefined && longitude !== null && longitude !== undefined
      && Number.isFinite(latitude) && Number.isFinite(longitude)
      && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
      && !(latitude === 0 && longitude === 0);
  }

  private divIcon(className: string, content: string): L.DivIcon {
    return L.divIcon({ className: '', html: `<span class="${className}">${content}</span>`, iconSize: [34, 34], iconAnchor: [17, 17], popupAnchor: [0, -18] });
  }

  private escape(value: string): string {
    return value.replace(/[&<>'"]/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character] || character);
  }
}
