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
    }

    .main-content {
      min-height: 100vh;
      transition: margin-left .3s cubic-bezier(.4,0,.2,1);
    }

    .main-content.with-sidebar {
      margin-left: 260px;
    }

    @media(max-width: 768px) {
      .main-content.with-sidebar {
        margin-left: 0;
        padding-top: 56px;
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
