import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-datos-maestros',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterOutlet],
  template: `
    <div class="layout">
      <header class="header">
        <h1>Marathon Sports</h1>
        <a routerLink="/dashboard" class="btn-back">← Volver al Dashboard</a>
      </header>
      <div class="content-wrapper">
        <nav class="sidebar">
          <h3>Datos Maestros</h3>
          <a routerLink="ciudades" routerLinkActive="active" class="nav-item">Ciudades</a>
          <a routerLink="categorias" routerLinkActive="active" class="nav-item">Categorías</a>
          <a routerLink="unidades-medida" routerLinkActive="active" class="nav-item">Unidades de Medida</a>
        </nav>
        <main class="main-content">
          <router-outlet></router-outlet>
        </main>
      </div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class DatosMaestrosComponent {}
