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
    .layout { min-height: 100vh; background: #f5f5f5; }
    .header { background: #2d5a27; color: #fff; padding: 1rem 2rem; display: flex; justify-content: space-between; align-items: center; }
    .header h1 { font-size: 1.3rem; }
    .btn-back { color: #fff; text-decoration: none; font-size: 0.9rem; opacity: 0.8; }
    .btn-back:hover { opacity: 1; }
    .content-wrapper { display: flex; min-height: calc(100vh - 60px); }
    .sidebar { width: 220px; background: #fff; padding: 1.5rem 1rem; border-right: 1px solid #e0e0e0; }
    .sidebar h3 { color: #2d5a27; margin-bottom: 1rem; font-size: 1rem; }
    .nav-item { display: block; padding: 0.6rem 1rem; margin-bottom: 0.3rem; border-radius: 6px; color: #333; text-decoration: none; font-size: 0.9rem; }
    .nav-item:hover { background: #e8f5e9; }
    .nav-item.active { background: #2d5a27; color: #fff; }
    .main-content { flex: 1; padding: 1.5rem; }
  `]
})
export class DatosMaestrosComponent {}
