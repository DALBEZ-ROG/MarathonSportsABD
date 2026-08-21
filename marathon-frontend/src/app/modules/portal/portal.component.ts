import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../../core/services/auth.service';

interface PortalItem {
  titulo: string;
  descripcion: string;
  route: string;
  icon: string;
  color: 'green' | 'orange' | 'gray' | 'gold' | 'blue';
  roles?: string[];
  favorito?: boolean;
  iconSafe?: SafeHtml;
}

interface PortalSection {
  id: string;
  title: string;
  items: PortalItem[];
}

@Component({
  selector: 'app-portal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="portal">
      <header class="portal-header">
        <div>
          <h1>Portal de Servicios</h1>
          <p>Selecciona un módulo para continuar</p>
        </div>
        <input
          class="portal-search"
          type="search"
          [(ngModel)]="busqueda"
          placeholder="Buscar servicio…"
          aria-label="Buscar servicio"
        />
      </header>

      <div class="portal-sections" *ngIf="seccionesVisibles.length; else vacio">
        <section class="portal-section" *ngFor="let section of seccionesVisibles">
          <h2 class="portal-section-title">{{ section.title }}</h2>
          <div class="portal-row">
            <article
              class="portal-card"
              *ngFor="let item of section.items"
              (click)="irA(item.route)"
              (keydown.enter)="irA(item.route)"
              tabindex="0"
              role="link"
            >
              <button
                type="button"
                class="favorite"
                [class.is-favorite]="item.favorito"
                (click)="toggleFavorito(item); $event.stopPropagation()"
                [attr.aria-label]="item.favorito ? 'Quitar de favoritos' : 'Marcar favorito'"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/>
                </svg>
              </button>

              <span class="portal-icon"
                    [class.ic-green]="item.color === 'green'"
                    [class.ic-orange]="item.color === 'orange'"
                    [class.ic-gray]="item.color === 'gray'"
                    [class.ic-gold]="item.color === 'gold'"
                    [class.ic-blue]="item.color === 'blue'"
                    [innerHTML]="item.iconSafe"></span>

              <h3 class="portal-title">{{ item.titulo }}</h3>
              <p class="portal-desc">{{ item.descripcion }}</p>
            </article>
          </div>
        </section>
      </div>

      <ng-template #vacio>
        <p class="portal-empty">No hay servicios que coincidan con la búsqueda.</p>
      </ng-template>
    </div>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; }

    .portal {
      padding: 2.5rem 2rem;
      max-width: 1400px;
      margin: 0 auto;
    }

    .portal-header {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 1.5rem;
      flex-wrap: wrap;
      margin-bottom: 2rem;
    }
    .portal-header h1 {
      font-size: 1.6rem;
      font-weight: 400;
      letter-spacing: 0.5px;
      color: #fff;
      margin: 0 0 .35rem;
    }
    .portal-header p {
      color: var(--ms-text-muted);
      font-size: .9rem;
      margin: 0;
    }

    .portal-search {
      min-width: 240px;
      padding: 0.65rem 1rem;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid var(--ms-border);
      border-radius: var(--ms-radius-sm);
      color: var(--ms-text);
      font-size: 0.85rem;
      outline: none;
    }
    .portal-search:focus {
      border-color: rgba(201, 168, 76, 0.4);
      box-shadow: 0 0 0 3px rgba(201, 168, 76, 0.08);
    }
    .portal-search::placeholder { color: var(--ms-text-muted); }

    .portal-sections {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .portal-section-title {
      font-size: .72rem;
      font-weight: 600;
      letter-spacing: 1.5px;
      text-transform: uppercase;
      color: rgba(255, 255, 255, 0.35);
      margin: 0 0 .85rem;
    }

    .portal-row {
      display: flex;
      flex-direction: row;
      flex-wrap: wrap;
      gap: 1.1rem;
    }

    .portal-card {
      position: relative;
      width: 180px;
      flex: 0 0 180px;
      background: rgba(255, 255, 255, 0.03);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 14px;
      padding: 1.35rem 1rem 1.2rem;
      cursor: pointer;
      transition: transform .18s ease, box-shadow .25s ease, border-color .2s ease, background .2s ease;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      outline: none;
    }
    .portal-card:hover, .portal-card:focus-visible {
      transform: translateY(-3px);
      background: rgba(255, 255, 255, 0.05);
      box-shadow: 0 8px 32px rgba(0, 0, 0, .3);
      border-color: rgba(201, 168, 76, 0.25);
    }

    .favorite {
      position: absolute;
      top: .6rem;
      left: .6rem;
      background: none;
      border: none;
      cursor: pointer;
      padding: 4px;
      color: rgba(255, 255, 255, 0.18);
      transition: color .2s, transform .2s;
      line-height: 0;
    }
    .favorite:hover { transform: scale(1.15); color: rgba(201, 168, 76, 0.7); }
    .favorite.is-favorite { color: var(--ms-gold); }

    .portal-icon {
      width: 52px;
      height: 52px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin: .2rem 0 .9rem;
    }
    .portal-icon svg { width: 26px; height: 26px; }

    .ic-green { background: rgba(76, 175, 80, 0.12); color: #81C784; }
    .ic-orange { background: rgba(255, 152, 0, 0.12); color: #FFB74D; }
    .ic-gray { background: rgba(255, 255, 255, 0.06); color: rgba(255, 255, 255, 0.55); }
    .ic-gold { background: rgba(201, 168, 76, 0.12); color: #C9A84C; }
    .ic-blue { background: rgba(33, 150, 243, 0.12); color: #64B5F6; }

    .portal-title {
      font-size: .92rem;
      font-weight: 600;
      color: rgba(255, 255, 255, 0.9);
      margin: 0 0 .3rem;
      line-height: 1.25;
    }

    .portal-desc {
      font-size: .76rem;
      color: var(--ms-text-muted);
      margin: 0 auto;
      max-width: 160px;
      line-height: 1.35;
    }

    .portal-empty {
      text-align: center;
      color: var(--ms-text-muted);
      padding: 3rem 1rem;
      font-style: italic;
    }

    @media(max-width: 600px) {
      .portal { padding: 1.5rem 1rem; }
      .portal-row {
        flex-wrap: nowrap;
        overflow-x: auto;
        padding-bottom: .35rem;
        scrollbar-width: thin;
      }
      .portal-card {
        width: 150px;
        flex: 0 0 150px;
      }
      .portal-search { width: 100%; min-width: 0; }
    }
  `]
})
export class PortalComponent implements OnInit {

  busqueda = '';
  items: PortalItem[] = [];

  private readonly favKey = 'marathon_portal_favs';

  /** Misma agrupación que el sidebar (navbar). */
  private readonly sectionDefs: { id: string; title: string; routes: string[] }[] = [
    { id: 'principal', title: 'Principal', routes: ['/dashboard'] },
    { id: 'gestion', title: 'Gestión', routes: ['/datos-maestros', '/proveedores', '/productos', '/bodegas', '/inventario', '/clientes'] },
    { id: 'pedidos', title: 'Pedidos', routes: ['/pedidos', '/pedidos/especiales', '/comprobantes'] },
    { id: 'operaciones', title: 'Operaciones', routes: ['/picking', '/empaque', '/despachos', '/devoluciones'] },
    { id: 'compras', title: 'Compras', routes: ['/compras', '/cuentas-por-pagar', '/devoluciones-proveedor'] },
    { id: 'manufactura', title: 'Manufactura', routes: ['/materia-prima', '/produccion', '/produccion/dashboard', '/produccion/costos'] },
    { id: 'analisis', title: 'Análisis', routes: ['/reportes', '/ia', '/auditoria'] },
    { id: 'cuenta', title: 'Cuenta', routes: ['/usuarios', '/roles', '/perfil'] },
  ];

  private allItems: PortalItem[] = [
    { titulo: 'Dashboard', descripcion: 'Vista general del sistema', route: '/dashboard', color: 'gold', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>' },
    { titulo: 'Datos Maestros', descripcion: 'Ciudades, categorías y unidades de medida', route: '/datos-maestros', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7V4h16v3"/><path d="M9 20h6"/><path d="M12 4v16"/></svg>', roles: ['Administrador'] },
    { titulo: 'Proveedores', descripcion: 'Gestión de proveedores de la empresa', route: '/proveedores', color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>', roles: ['Administrador'] },
    { titulo: 'Productos', descripcion: 'Catálogo de productos disponibles', route: '/productos', color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 01-8 0"/></svg>', roles: ['Administrador'] },
    { titulo: 'Bodegas', descripcion: 'Almacenes y ubicaciones del inventario', route: '/bodegas', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/></svg>', roles: ['Administrador'] },
    { titulo: 'Inventario', descripcion: 'Stock y existencias disponibles', route: '/inventario', color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>', roles: ['Administrador', 'Operador de Bodega', 'Supervisor E-Commerce', 'Encargado de Compras'] },
    { titulo: 'Clientes', descripcion: 'Base de datos de clientes', route: '/clientes', color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>', roles: ['Administrador', 'Operador de Pedidos', 'Supervisor E-Commerce'] },
    { titulo: 'Pedidos', descripcion: 'Órdenes de pedidos de clientes', route: '/pedidos', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] },
    { titulo: 'Pedidos Especiales', descripcion: 'Órdenes de clientes personalizadas', route: '/pedidos/especiales', color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] },
    { titulo: 'Comprobantes', descripcion: 'Facturas y comprobantes emitidos', route: '/comprobantes', color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="3" width="20" height="18" rx="2"/><line x1="8" y1="7" x2="16" y2="7"/><line x1="8" y1="11" x2="16" y2="11"/><line x1="8" y1="15" x2="12" y2="15"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] },
    { titulo: 'Picking', descripcion: 'Preparación y despacho de pedidos', route: '/picking', color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg>', roles: ['Administrador', 'Operador de Bodega'] },
    { titulo: 'Empaque', descripcion: 'Empaque de productos por pedido', route: '/empaque', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>', roles: ['Administrador', 'Operador de Bodega'] },
    { titulo: 'Despachos', descripcion: 'Envíos, entregas y seguimiento', route: '/despachos', color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>', roles: ['Administrador', 'Operador de Bodega', 'Supervisor E-Commerce'] },
    { titulo: 'Devoluciones', descripcion: 'Devoluciones de clientes', route: '/devoluciones', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] },
    { titulo: 'Compras', descripcion: 'Órdenes de compra a proveedores', route: '/compras', color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"/></svg>', roles: ['Administrador', 'Encargado de Compras'] },
    { titulo: 'Cuentas por Pagar', descripcion: 'Obligaciones con proveedores', route: '/cuentas-por-pagar', color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>', roles: ['Administrador', 'Encargado de Compras', 'Supervisor E-Commerce'] },
    { titulo: 'Dev. a Proveedor', descripcion: 'Devoluciones a proveedores', route: '/devoluciones-proveedor', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 14l-4-4 4-4"/><path d="M5 10h11a4 4 0 110 8h-1"/></svg>', roles: ['Administrador', 'Encargado de Compras'] },
    { titulo: 'Materia Prima', descripcion: 'Insumos y kárdex de producción', route: '/materia-prima', color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>', roles: ['Administrador', 'Encargado de Producción', 'Encargado de Compras'] },
    { titulo: 'Producción', descripcion: 'Órdenes de producción', route: '/produccion', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2 20h20"/><path d="M4 20V8l6 4V8l6 4V8l4 3v9"/></svg>', roles: ['Administrador', 'Encargado de Producción'] },
    { titulo: 'Dashboard Producción', descripcion: 'Indicadores de manufactura', route: '/produccion/dashboard', color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="9"/><rect x="14" y="3" width="7" height="5"/><rect x="14" y="12" width="7" height="9"/><rect x="3" y="16" width="7" height="5"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Encargado de Producción'] },
    { titulo: 'Análisis de Costos', descripcion: 'Costos de producción', route: '/produccion/costos', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Encargado de Producción'] },
    { titulo: 'Reportes', descripcion: 'Informes y estadísticas', route: '/reportes', color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce'] },
    { titulo: 'Asistente IA', descripcion: 'Chat con inteligencia artificial', route: '/ia', color: 'blue', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09A1.65 1.65 0 0015 4.68a1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09A1.65 1.65 0 0019.4 15z"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce'] },
    { titulo: 'Auditoría', descripcion: 'Trazabilidad de actividades', route: '/auditoria', color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>', roles: ['Administrador'] },
    { titulo: 'Usuarios', descripcion: 'Gestión de cuentas de usuario', route: '/usuarios', color: 'green', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>', roles: ['Administrador'] },
    { titulo: 'Roles', descripcion: 'Permisos y accesos por rol', route: '/roles', color: 'orange', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>', roles: ['Administrador'] },
    { titulo: 'Mi Perfil', descripcion: 'Datos de tu cuenta de usuario', route: '/perfil', color: 'gray', icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>' },
  ];

  constructor(
    private authService: AuthService,
    private router: Router,
    private sanitizer: DomSanitizer
  ) {}

  get seccionesVisibles(): PortalSection[] {
    const q = this.busqueda.trim().toLowerCase();
    const filtrados = q
      ? this.items.filter(i =>
          i.titulo.toLowerCase().includes(q) || i.descripcion.toLowerCase().includes(q))
      : this.items;

    const byRoute = new Map(filtrados.map(item => [item.route, item]));
    const sections: PortalSection[] = [];

    const favoritos = filtrados.filter(item => item.favorito);
    if (favoritos.length) {
      sections.push({ id: 'favoritos', title: 'Favoritos', items: favoritos });
    }

    for (const def of this.sectionDefs) {
      const items = def.routes
        .map(route => byRoute.get(route))
        .filter((item): item is PortalItem => !!item);
      if (items.length) {
        sections.push({ id: def.id, title: def.title, items });
      }
    }

    return sections;
  }

  ngOnInit(): void {
    const roles = [
      'Administrador',
      'Supervisor E-Commerce',
      'Operador de Bodega',
      'Operador de Pedidos',
      'Encargado de Compras',
      'Encargado de Producción'
    ].filter(rol => this.authService.hasRol(rol));

    const favs = this.leerFavoritos();

    this.items = this.allItems
      .filter(item => !item.roles || item.roles.some(r => roles.includes(r)))
      .map(item => ({
        ...item,
        favorito: favs.includes(item.route),
        iconSafe: this.sanitizer.bypassSecurityTrustHtml(item.icon)
      }));
  }

  toggleFavorito(item: PortalItem): void {
    item.favorito = !item.favorito;
    const favs = this.items.filter(i => i.favorito).map(i => i.route);
    localStorage.setItem(this.favKey, JSON.stringify(favs));
  }

  irA(ruta: string): void {
    this.router.navigateByUrl(ruta);
  }

  private leerFavoritos(): string[] {
    try {
      const raw = localStorage.getItem(this.favKey);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }
}
