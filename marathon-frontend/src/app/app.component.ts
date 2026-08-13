import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent],
  template: `
    <div class="app-layout" [class.has-sidebar]="showNavbar">
      <ng-container *ngIf="showNavbar">
        <div class="bg-gradient"></div>
        <div class="bg-orb orb-1"></div>
        <div class="bg-orb orb-2"></div>
      </ng-container>
      <app-navbar *ngIf="showNavbar"></app-navbar>
      <main class="main-content" [class.with-sidebar]="showNavbar">
        <router-outlet></router-outlet>
      </main>
    </div>
  `,
  styles: [`
    .app-layout {
      min-height: 100vh;
      background: #0a0a0f;
      position: relative;
    }

    .bg-gradient {
      position: fixed;
      inset: 0;
      background: linear-gradient(160deg, #0a0a0f 0%, #0f1623 30%, #0a1628 60%, #0d0d14 100%);
      z-index: 0;
      pointer-events: none;
    }

    .bg-orb {
      position: fixed;
      border-radius: 50%;
      filter: blur(100px);
      opacity: 0.22;
      z-index: 0;
      pointer-events: none;
      animation: float 25s ease-in-out infinite;
    }
    .orb-1 { width: 600px; height: 600px; background: radial-gradient(circle, #C9A84C, transparent 70%); top: -10%; right: -5%; }
    .orb-2 { width: 400px; height: 400px; background: radial-gradient(circle, #1a5c3a, transparent 70%); bottom: -5%; left: -5%; animation-delay: -12s; }

    @keyframes float {
      0%, 100% { transform: translate(0, 0) scale(1); }
      33% { transform: translate(20px, -20px) scale(1.03); }
      66% { transform: translate(-15px, 15px) scale(0.97); }
    }

    .main-content {
      position: relative;
      z-index: 1;
      min-height: 100vh;
      transition: margin-left .3s cubic-bezier(.4,0,.2,1);
    }

    .main-content.with-sidebar {
      margin-left: 260px;
    }

    :host-context(body.sidebar-narrow) .main-content.with-sidebar {
      margin-left: 72px;
    }

    @media(max-width: 768px) {
      .main-content.with-sidebar {
        margin-left: 0;
        padding-top: 56px;
      }
      :host-context(body.sidebar-narrow) .main-content.with-sidebar {
        margin-left: 0;
      }
    }
  `]
})
export class AppComponent {
  get showNavbar(): boolean {
    return this.authService.isAuthenticated() && !this.router.url.includes('/login');
  }

  constructor(private authService: AuthService, private router: Router) {}
}
