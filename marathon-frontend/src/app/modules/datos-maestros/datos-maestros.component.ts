import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-datos-maestros',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="dm-shell">
      <nav class="tabs">
        <a routerLink="ciudades" routerLinkActive="active">Ciudades</a>
        <a routerLink="categorias" routerLinkActive="active">Categorías</a>
        <a routerLink="unidades-medida" routerLinkActive="active">Unidades de Medida</a>
      </nav>
    </div>
    <router-outlet></router-outlet>
  `,
  styles: [`
    .dm-shell {
      max-width: 1200px;
      margin: 0 auto;
      padding: 1.5rem 2rem 0;
    }
    .tabs { margin-bottom: 0; }
  `]
})
export class DatosMaestrosComponent {}
