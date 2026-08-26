import { CommonModule } from '@angular/common';
import { Component, Input, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { FleetbaseVehicle } from '../../core/models/fleetbase.models';
import { LeafletFleetGisMapComponent } from './fleet-gis-map.component';
import { GoogleFleetGisMapComponent } from './google-fleet-gis-map.component';

type MapProvider = 'google' | 'osm';

@Component({
  selector: 'sf-fleet-gis-map',
  standalone: true,
  imports: [CommonModule, FormsModule, LeafletFleetGisMapComponent, GoogleFleetGisMapComponent],
  templateUrl: './fleet-map-shell.component.html',
  styleUrl: './fleet-map-shell.component.css'
})
export class FleetMapShellComponent {
  @Input() vehicles: FleetbaseVehicle[] = [];
  @ViewChild(LeafletFleetGisMapComponent) leafletMap?: LeafletFleetGisMapComponent;
  @ViewChild(GoogleFleetGisMapComponent) googleMap?: GoogleFleetGisMapComponent;

  googleKey = localStorage.getItem('stockflowGoogleMapsBrowserKey') || '';
  provider: MapProvider = this.googleKey && localStorage.getItem('stockflowMapProvider') !== 'osm' ? 'google' : 'osm';
  setupOpen = false;
  setupKey = '';
  setupError = '';

  trackVehicle(vehicle: FleetbaseVehicle): void {
    if (this.provider === 'google') this.googleMap?.trackVehicle(vehicle);
    else this.leafletMap?.trackVehicle(vehicle);
  }

  chooseProvider(provider: MapProvider): void {
    if (provider === 'google' && !this.googleKey) {
      this.setupOpen = true;
      return;
    }
    localStorage.setItem('stockflowMapProvider', provider);
    this.provider = provider;
  }

  saveGoogleKey(): void {
    const key = this.setupKey.trim();
    if (!/^AIza[\w-]{20,}$/.test(key)) {
      this.setupError = 'Enter the restricted Google Maps browser key beginning with AIza.';
      return;
    }
    localStorage.setItem('stockflowGoogleMapsBrowserKey', key);
    localStorage.setItem('stockflowMapProvider', 'google');
    this.googleKey = key;
    this.setupKey = '';
    this.setupError = '';
    this.setupOpen = false;
    this.provider = 'google';
  }

  forgetGoogleKey(): void {
    localStorage.removeItem('stockflowGoogleMapsBrowserKey');
    localStorage.setItem('stockflowMapProvider', 'osm');
    this.googleKey = '';
    this.provider = 'osm';
  }
}
