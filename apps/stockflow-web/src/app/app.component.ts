import { CommonModule } from '@angular/common';
import { Component, effect, signal } from '@angular/core';
import { AuthService } from './core/services/auth.service';
import { LoginComponent } from './features/auth/login.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { ParticleTextComponent } from './shared/components/particle-text/particle-text.component';

@Component({
  selector: 'sf-root',
  standalone: true,
  imports: [CommonModule, DashboardComponent, LoginComponent, ParticleTextComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly showWelcomeAnimation = signal(false);
  private hasSeenAnimation = false;

  constructor(readonly auth: AuthService) {
    effect(() => {
      const hasSession = !!this.auth.session();
      
      if (hasSession && !this.hasSeenAnimation) {
        this.showWelcomeAnimation.set(true);
        this.hasSeenAnimation = true;
        
        setTimeout(() => {
          this.showWelcomeAnimation.set(false);
        }, 2500);
      } else if (!hasSession) {
        this.hasSeenAnimation = false;
        this.showWelcomeAnimation.set(false);
      }
    }, { allowSignalWrites: true });
  }
}
