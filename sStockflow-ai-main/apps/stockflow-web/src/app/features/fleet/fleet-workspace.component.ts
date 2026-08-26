import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, forkJoin, of, Subscription, timer } from 'rxjs';
import { FleetbaseAuditSummary, FleetbaseIntegrationStatus, FleetbaseOrganization, FleetbaseVehicle } from '../../core/models/fleetbase.models';
import { RouteWeatherForecast } from '../../core/models/google-weather.models';
import { FleetbaseService } from '../../core/services/fleetbase.service';
import { GoogleWeatherService } from '../../core/services/google-weather.service';
import { FleetMapShellComponent } from './fleet-map-shell.component';

type FleetFilter = 'all' | 'online' | 'offline' | 'available';

@Component({
  selector: 'sf-fleet-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule, FleetMapShellComponent],
  templateUrl: './fleet-workspace.component.html',
  styleUrl: './fleet-workspace.component.css'
})
export class FleetWorkspaceComponent implements OnInit, OnChanges, OnDestroy {
  @Input({ required: true }) tenantLabel = '';
  @Input({ required: true }) tenantId = '';
  @ViewChild(FleetMapShellComponent) gisMap?: FleetMapShellComponent;

  integration?: FleetbaseIntegrationStatus;
  organization?: FleetbaseOrganization;
  audit?: FleetbaseAuditSummary;
  vehicles: FleetbaseVehicle[] = [];
  selectedVehicle?: FleetbaseVehicle;
  search = '';
  filter: FleetFilter = 'all';
  loading = false;
  error = '';
  weatherForecast?: RouteWeatherForecast;
  weatherLoading = false;
  weatherError = '';
  lastUpdated?: Date;
  private liveSync?: Subscription;
  private positionSyncing = false;

  constructor(
    private readonly fleetbase: FleetbaseService,
    private readonly googleWeather: GoogleWeatherService
  ) {}

  ngOnInit(): void {
    this.refresh();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['tenantId']?.firstChange && changes['tenantId']) {
      this.refresh();
    }
  }

  ngOnDestroy(): void {
    this.liveSync?.unsubscribe();
  }

  get filteredVehicles(): FleetbaseVehicle[] {
    const needle = this.search.trim().toLowerCase();
    return this.vehicles.filter(vehicle => {
      const matchesFilter = this.filter === 'all'
        || (this.filter === 'online' && vehicle.online === true)
        || (this.filter === 'offline' && vehicle.online !== true)
        || (this.filter === 'available' && vehicle.status?.toLowerCase() === 'available');
      const searchable = [vehicle.name, vehicle.internalId, vehicle.plateNumber, vehicle.make, vehicle.model]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return matchesFilter && (!needle || searchable.includes(needle));
    });
  }

  get onlineCount(): number {
    return this.vehicles.filter(vehicle => vehicle.online === true).length;
  }

  get availableCount(): number {
    return this.vehicles.filter(vehicle => vehicle.status?.toLowerCase() === 'available').length;
  }

  refresh(): void {
    if (this.loading) return;
    this.loading = true;
    this.error = '';
    forkJoin({
      integration: this.fleetbase.status(),
      fleet: this.fleetbase.vehicles(),
      organization: this.fleetbase.organization().pipe(catchError(() => of(undefined))),
      audit: this.fleetbase.audit().pipe(catchError(() => of(undefined)))
    }).pipe(finalize(() => this.loading = false)).subscribe({
      next: result => {
        this.integration = result.integration;
        this.organization = result.organization;
        this.audit = result.audit;
        this.vehicles = result.fleet.vehicles;
        this.lastUpdated = new Date();
        if (this.selectedVehicle) {
          this.selectedVehicle = this.vehicles.find(vehicle => vehicle.id === this.selectedVehicle?.id);
        }
        if (result.integration.enabled && result.integration.configured && !this.liveSync) {
          this.liveSync = timer(15000, 15000).subscribe(() => this.syncVehiclePositions());
        }
      },
      error: (error: HttpErrorResponse) => {
        this.error = this.errorMessage(error);
      }
    });
  }

  private syncVehiclePositions(): void {
    if (this.positionSyncing) return;
    this.positionSyncing = true;
    this.fleetbase.vehicles().pipe(finalize(() => this.positionSyncing = false)).subscribe({
      next: fleet => {
        this.vehicles = fleet.vehicles;
        this.lastUpdated = new Date();
        if (this.selectedVehicle) {
          this.selectedVehicle = this.vehicles.find(vehicle => vehicle.id === this.selectedVehicle?.id);
        }
      }
    });
  }

  selectVehicle(vehicle: FleetbaseVehicle): void {
    this.selectedVehicle = vehicle;
    this.loadWeatherForecast(vehicle);
  }

  closeDetails(): void {
    this.selectedVehicle = undefined;
  }

  showVehicleTracking(vehicle: FleetbaseVehicle): void {
    this.closeDetails();
    window.setTimeout(() => this.gisMap?.trackVehicle(vehicle), 80);
  }

  hasVehiclePosition(vehicle: FleetbaseVehicle): boolean {
    return vehicle.latitude !== null && vehicle.latitude !== undefined
      && vehicle.longitude !== null && vehicle.longitude !== undefined
      && Number.isFinite(vehicle.latitude) && Number.isFinite(vehicle.longitude)
      && !(vehicle.latitude === 0 && vehicle.longitude === 0);
  }

  vehicleCoordinates(vehicle: FleetbaseVehicle): string {
    return this.hasVehiclePosition(vehicle)
      ? `${vehicle.latitude!.toFixed(5)}, ${vehicle.longitude!.toFixed(5)}`
      : 'Awaiting first GPS fix';
  }

  vehicleHeading(vehicle: FleetbaseVehicle): string {
    if (vehicle.heading === null || vehicle.heading === undefined) return 'Not reported';
    return `${Math.round(vehicle.heading)}° ${this.cardinalDirection(vehicle.heading)}`;
  }

  prototypeEta(vehicle: FleetbaseVehicle): string {
    const minutes = this.prototypeEtaMinutes(vehicle);
    return minutes >= 60 ? `${Math.floor(minutes / 60)} hr ${minutes % 60} min` : `${minutes} min`;
  }

  prototypeEtaMinutes(vehicle: FleetbaseVehicle): number {
    if (!this.hasVehiclePosition(vehicle)) return 84;
    const remaining = this.distanceKm(vehicle.latitude!, vehicle.longitude!, 25.5788, 91.8933);
    const speed = Math.max(vehicle.speed || 46, 25);
    return Math.max(1, Math.round(remaining / speed * 60));
  }

  loadWeatherForecast(vehicle: FleetbaseVehicle): void {
    this.weatherLoading = true;
    this.weatherError = '';
    this.weatherForecast = undefined;
    this.googleWeather.routeForecast(25.5788, 91.8933, this.prototypeEtaMinutes(vehicle), 'Shillong relief hub')
      .pipe(finalize(() => this.weatherLoading = false))
      .subscribe({
        next: forecast => this.weatherForecast = forecast,
        error: (error: HttpErrorResponse) => {
          const upstream = error.error as { message?: string } | null;
          this.weatherError = upstream?.message || (error.status === 0
            ? 'StockFlow could not reach the weather backend.'
            : `Weather forecast could not be loaded (HTTP ${error.status}).`);
        }
      });
  }

  prototypeDistance(vehicle: FleetbaseVehicle): string {
    const distance = this.hasVehiclePosition(vehicle)
      ? this.distanceKm(vehicle.latitude!, vehicle.longitude!, 25.5788, 91.8933)
      : 68;
    return `${Math.round(distance)} km`;
  }

  prototypeProgress(vehicle: FleetbaseVehicle): number {
    if (!this.hasVehiclePosition(vehicle)) return 0;
    const remaining = this.distanceKm(vehicle.latitude!, vehicle.longitude!, 25.5788, 91.8933);
    return Math.max(0, Math.min(100, Math.round((1 - remaining / 68) * 100)));
  }

  vehicleTitle(vehicle: FleetbaseVehicle): string {
    return vehicle.name || vehicle.plateNumber || vehicle.internalId || 'Unnamed vehicle';
  }

  vehicleSpecification(vehicle: FleetbaseVehicle): string {
    const specification = [vehicle.year, vehicle.make, vehicle.model].filter(Boolean).join(' ');
    return specification || vehicle.type || 'Vehicle specification not provided';
  }

  private cardinalDirection(heading: number): string {
    const directions = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
    return directions[Math.round((((heading % 360) + 360) % 360) / 45) % 8];
  }

  private distanceKm(latitude1: number, longitude1: number, latitude2: number, longitude2: number): number {
    const radians = (degrees: number) => degrees * Math.PI / 180;
    const latitudeDelta = radians(latitude2 - latitude1);
    const longitudeDelta = radians(longitude2 - longitude1);
    const a = Math.sin(latitudeDelta / 2) ** 2
      + Math.cos(radians(latitude1)) * Math.cos(radians(latitude2)) * Math.sin(longitudeDelta / 2) ** 2;
    return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }

  private errorMessage(error: HttpErrorResponse): string {
    const upstream = error.error as { message?: string } | null;
    if (upstream?.message) return upstream.message;
    if (error.status === 0) return 'StockFlow could not reach the local backend. Confirm that the API is running on port 8080.';
    return `Fleet data could not be loaded (HTTP ${error.status}).`;
  }
}
