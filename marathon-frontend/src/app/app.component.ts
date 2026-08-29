import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { AsistenteBurbujaComponent } from './shared/components/asistente-burbuja/asistente-burbuja.component';
import { AuthService } from './core/services/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent, AsistenteBurbujaComponent],
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

      <!-- F88: el asistente, a mano en todas las pantallas. Va FUERA del <main>
           a propósito: dentro heredaría su relleno lateral, y la burbuja tiene
           que anclarse a la esquina de la ventana, no a la del contenido. El
           propio componente decide si se ve —sesión, permiso, y no estar ya
           en /ia—. -->
      <app-asistente-burbuja *ngIf="showNavbar"></app-asistente-burbuja>
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

    /* D3 — el hueco de la barra lateral se hace con padding, no con margen.
       Con margen había 260 px de desbordamiento horizontal: styles.scss fuerza
       .main-content { width: 100% } para que el contenido pueda encogerse, y
       un elemento que ya mide el 100% más un margen de 260 px se sale por la
       derecha. No se veía porque el contenido estaba topado a 1200 px y nunca
       llegaba al borde; al hacer los contenedores fluidos, salió a la luz.
       Con box-sizing border-box el padding se descuenta del 100%, así que el
       contenido cabe exactamente en lo que queda. */
    /* Sin z-index a propósito (D4). Con z-index: 1 este elemento creaba un
       contexto de apilamiento propio, y dentro de él el z-index 1000 de
       .modal-overlay no servía de nada: el que contaba era el 1 de aquí, contra
       el 300 de la barra lateral. Resultado: el borde izquierdo de todos los
       modales quedaba tapado por el menú y no se leían ni las etiquetas.
       Position relative basta para pintar por encima del fondo, que va en
       elementos fijos declarados antes en el DOM. */
    .main-content {
      position: relative;
      min-height: 100vh;
      transition: padding-left .3s cubic-bezier(.4,0,.2,1);
    }

    .main-content.with-sidebar {
      padding-left: 260px;
    }

    :host-context(body.sidebar-narrow) .main-content.with-sidebar {
      padding-left: 72px;
    }

    @media(max-width: 768px) {
      .main-content.with-sidebar {
        padding-left: 0;
        padding-top: 56px;
      }
      :host-context(body.sidebar-narrow) .main-content.with-sidebar {
        padding-left: 0;
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
