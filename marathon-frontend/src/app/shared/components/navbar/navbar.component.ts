import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { filter, Subscription } from 'rxjs';
import { AuthService } from '../../../core/services/auth.service';

interface NavItem {
  label: string;
  route: string;
  icon: string;
  iconSafe?: SafeHtml;
  roles?: string[];
}

interface NavSection {
  title: string;
  items: NavItem[];
  collapsed: boolean;
}

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <!-- Mobile Top Bar -->
    <div class="mobile-topbar">
      <button class="hamburger" (click)="sidebarOpen = !sidebarOpen">
        <span></span><span></span><span></span>
      </button>
      <span class="mobile-brand">Marathon Sports</span>
    </div>

    <!-- Overlay -->
    <div class="overlay" [class.visible]="sidebarOpen" (click)="sidebarOpen = false"></div>

    <!-- Sidebar -->
    <aside class="sidebar" [class.open]="sidebarOpen" [class.collapsed]="isCollapsed">
      <!-- Header -->
      <div class="sidebar-header">
        <div class="brand-area" *ngIf="!isCollapsed">
          <svg class="brand-icon" width="32" height="32" viewBox="0 0 48 48" fill="none">
            <circle cx="24" cy="24" r="22" stroke="url(#sg)" stroke-width="2" fill="none"/>
            <path d="M16 28 L22 20 L28 26 L34 18" stroke="url(#sg)" stroke-width="2.5" stroke-linecap="round" fill="none"/>
            <defs><linearGradient id="sg" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" stop-color="#C9A84C"/><stop offset="100%" stop-color="#F4E28D"/></linearGradient></defs>
          </svg>
          <div class="brand-text">
            <span class="brand-name">Marathon</span>
            <span class="brand-sub">Sports</span>
          </div>
        </div>
        <div class="header-actions" *ngIf="!isMobile">
          <button
            class="collapse-btn"
            (click)="toggleSidebar()"
            [title]="isCollapsed ? 'Expandir menú' : 'Compactar menú'"
            [attr.aria-label]="isCollapsed ? 'Expandir menú' : 'Compactar menú'"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline *ngIf="!isCollapsed" points="15 18 9 12 15 6"/>
              <polyline *ngIf="isCollapsed" points="9 18 15 12 9 6"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- Navigation -->
      <nav class="sidebar-nav">
        <div class="nav-section" *ngFor="let section of navSections" [class.folded]="section.collapsed && !isCollapsed">
          <button
            type="button"
            class="section-label"
            *ngIf="!isCollapsed"
            (click)="toggleSection(section)"
            [attr.aria-expanded]="!section.collapsed"
          >
            <span>{{ section.title }}</span>
            <svg class="section-chevron" [class.open]="!section.collapsed" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <div class="section-items" *ngIf="isCollapsed || !section.collapsed">
            <a *ngFor="let item of section.items"
               [routerLink]="item.route"
               routerLinkActive="active"
               [routerLinkActiveOptions]="{exact: item.route === '/inicio'}"
               class="nav-item"
               [title]="item.label"
               (click)="closeMobile()">
              <span class="nav-icon" [innerHTML]="item.iconSafe"></span>
              <span class="nav-label" *ngIf="!isCollapsed">{{ item.label }}</span>
            </a>
          </div>
        </div>
      </nav>

      <!-- Footer / User -->
      <div class="sidebar-footer">
        <div class="user-card" *ngIf="!isCollapsed">
          <div class="user-avatar">{{ userInitials }}</div>
          <div class="user-info">
            <span class="user-name">{{ userName }}</span>
            <span class="user-role">{{ userRole }}</span>
          </div>
        </div>
        <button class="btn-logout" (click)="logout()" [title]="'Cerrar sesión'">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 01-2-2V5a2 2 0 012-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          <span *ngIf="!isCollapsed">Cerrar sesión</span>
        </button>
      </div>
    </aside>
  `,
  styles: [`
    /* ═══════════════════════════════════
       PREMIUM SIDEBAR — GLASSMORPHISM DARK
       ═══════════════════════════════════ */

    :host { display: block; }

    /* ── Mobile Top Bar ── */
    .mobile-topbar {
      display: none;
      position: fixed; top: 0; left: 0; right: 0; z-index: 200;
      height: 56px;
      background: rgba(10,10,15,0.95);
      backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
      border-bottom: 1px solid rgba(255,255,255,0.05);
      align-items: center; padding: 0 1rem; gap: 1rem;
    }

    .hamburger {
      background: none; border: none; cursor: pointer;
      display: flex; flex-direction: column; gap: 4px; padding: 6px;
    }
    .hamburger span {
      width: 22px; height: 2px; background: rgba(255,255,255,0.7);
      border-radius: 2px; transition: all .3s;
    }

    .mobile-brand {
      color: #C9A84C; font-weight: 400; font-size: 1rem; letter-spacing: 1px;
    }

    .overlay {
      display: none; position: fixed; inset: 0; z-index: 250;
      background: rgba(0,0,0,0.6); backdrop-filter: blur(4px);
      opacity: 0; transition: opacity .3s;
    }
    .overlay.visible { display: block; opacity: 1; }

    /* ── Sidebar ── */
    .sidebar {
      position: fixed; top: 0; left: 0; bottom: 0; z-index: 300;
      width: 260px;
      background: rgba(12,12,18,0.97);
      backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
      border-right: 1px solid rgba(255,255,255,0.05);
      display: flex; flex-direction: column;
      transition: width .3s cubic-bezier(.4,0,.2,1), transform .3s cubic-bezier(.4,0,.2,1);
      overflow: hidden;
    }

    .sidebar.collapsed { width: 72px; }

    /* ── Header ── */
    .sidebar-header {
      display: flex; align-items: center; justify-content: space-between;
      padding: 1.25rem 1rem;
      border-bottom: 1px solid rgba(255,255,255,0.04);
      min-height: 72px;
    }

    .brand-area { display: flex; align-items: center; gap: .75rem; }
    .brand-text { display: flex; flex-direction: column; }
    .brand-name {
      font-size: 1.05rem; font-weight: 500; letter-spacing: 1.5px;
      color: #fff;
    }
    .brand-sub { font-size: .7rem; color: rgba(255,255,255,0.3); letter-spacing: 2px; text-transform: uppercase; }

    .header-actions { display: flex; align-items: center; gap: 2px; }

    .collapse-btn {
      background: none; border: none; color: rgba(255,255,255,0.3);
      cursor: pointer; padding: 6px; border-radius: 6px;
      transition: all .2s; line-height: 0;
    }
    .collapse-btn:hover { color: #C9A84C; background: rgba(255,255,255,0.04); }

    /* ── Nav ── */
    .sidebar-nav {
      flex: 1; overflow-y: auto; overflow-x: hidden;
      padding: .75rem .5rem;
      scrollbar-width: thin;
      scrollbar-color: rgba(255,255,255,0.08) transparent;
    }

    .nav-section { margin-bottom: .85rem; }

    .section-label {
      display: flex; align-items: center; justify-content: space-between;
      width: 100%; padding: .4rem .75rem; margin-bottom: .15rem;
      font-size: .65rem; color: rgba(255,255,255,0.25);
      text-transform: uppercase; letter-spacing: 1.5px; font-weight: 500;
      background: none; border: none; cursor: pointer; text-align: left;
      border-radius: 8px; transition: color .2s, background .2s;
    }
    .section-label:hover { color: #C9A84C; background: rgba(255,255,255,0.03); }

    .section-chevron {
      flex-shrink: 0; opacity: .5; transition: transform .2s ease;
      transform: rotate(-90deg);
    }
    .section-chevron.open { transform: rotate(0deg); opacity: .8; }

    .nav-item {
      display: flex; align-items: center; gap: .75rem;
      padding: .6rem .75rem; margin: 2px 0;
      border-radius: 10px; text-decoration: none;
      color: rgba(255,255,255,0.55); font-size: .85rem; font-weight: 400;
      transition: all .2s cubic-bezier(.4,0,.2,1);
      white-space: nowrap; overflow: hidden;
    }
    .nav-item:hover { background: rgba(255,255,255,0.04); color: rgba(255,255,255,0.85); }
    .nav-item.active {
      background: rgba(201,168,76,0.1);
      color: #C9A84C;
      border-left: 3px solid #C9A84C;
      padding-left: calc(.75rem - 3px);
    }

    .nav-icon {
      width: 20px; height: 20px; flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
      opacity: .7;
    }
    .nav-icon svg { width: 18px; height: 18px; display: block; }
    .nav-item.active .nav-icon { opacity: 1; }
    .nav-item:hover .nav-icon { opacity: 1; }

    .nav-label { transition: opacity .2s; }

    /* ── Footer ── */
    .sidebar-footer {
      padding: 1rem; border-top: 1px solid rgba(255,255,255,0.04);
    }

    .user-card {
      display: flex; align-items: center; gap: .75rem;
      padding: .75rem; margin-bottom: .75rem;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.04);
      border-radius: 12px;
    }

    .user-avatar {
      width: 36px; height: 36px; border-radius: 10px;
      background: linear-gradient(135deg, #C9A84C, #a08339);
      display: flex; align-items: center; justify-content: center;
      font-size: .8rem; font-weight: 600; color: #0a0a0f;
      flex-shrink: 0;
    }

    .user-info { display: flex; flex-direction: column; overflow: hidden; }
    .user-name { font-size: .8rem; color: rgba(255,255,255,0.8); font-weight: 500; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .user-role { font-size: .65rem; color: rgba(255,255,255,0.3); }

    .btn-logout {
      display: flex; align-items: center; gap: .6rem;
      width: 100%; padding: .65rem .75rem;
      background: rgba(220,38,38,0.06); border: 1px solid rgba(220,38,38,0.12);
      border-radius: 10px; color: #E57373;
      cursor: pointer; font-size: .85rem; font-weight: 400;
      transition: all .2s;
    }
    .btn-logout:hover { background: rgba(220,38,38,0.12); border-color: rgba(220,38,38,0.25); }

    /* ── Collapsed state ── */
    .sidebar.collapsed .brand-area { display: none; }
    .sidebar.collapsed .section-label { display: none; }
    .sidebar.collapsed .header-actions { width: 100%; justify-content: center; }
    .sidebar.collapsed .nav-label { display: none; }
    .sidebar.collapsed .nav-item { justify-content: center; padding: .7rem; }
    .sidebar.collapsed .nav-item.active { border-left: none; padding-left: .7rem; }
    .sidebar.collapsed .nav-icon { width: 24px; height: 24px; opacity: .85; }
    .sidebar.collapsed .user-card { display: none; }
    .sidebar.collapsed .btn-logout span { display: none; }
    .sidebar.collapsed .btn-logout { justify-content: center; }
    .sidebar.collapsed .sidebar-header { justify-content: center; }

    /* ── Mobile ── */
    @media(max-width: 768px) {
      .mobile-topbar { display: flex; }
      .sidebar { transform: translateX(-100%); }
      .sidebar.open { transform: translateX(0); }
      .sidebar.collapsed { width: 260px; }
    }
  `]
})
export class NavbarComponent implements OnInit, OnDestroy {
  isAdmin = false;
  isOperadorBodega = false;
  isOperadorPedidos = false;
  isSupervisor = false;
  isEncargadoCompras = false;
  isEncargadoProduccion = false;
  userName = '';
  userRole = '';
  userInitials = '';
  sidebarOpen = false;
  isCollapsed = false;
  isMobile = false;

  navSections: NavSection[] = [];
  private navSub?: Subscription;

  private allItems: NavItem[] = [
    { label: 'Inicio', route: '/inicio', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>' },
    { label: 'Indicadores', route: '/indicadores', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>' },
    { label: 'Categorías', route: '/datos-maestros/categorias', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d=\"M3 7h18M3 12h18M3 17h12\"/></svg>', roles: ['Administrador'] },
    { label: 'Unidades de medida', route: '/datos-maestros/unidades-medida', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d=\"M2 12h20M6 9v6M10 10v4M14 9v6M18 10v4\"/></svg>', roles: ['Administrador'] },
    { label: 'Ciudades', route: '/datos-maestros/ciudades', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d=\"M21 21H3V9l6-4 6 4v3h6z\"/><path d=\"M9 21v-6h4v6\"/></svg>', roles: ['Administrador'] },
    { label: 'Proveedores', route: '/datos-maestros/proveedores', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>', roles: ['Administrador'] },
    { label: 'Productos', route: '/datos-maestros/productos', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 01-8 0"/></svg>', roles: ['Administrador'] },
    { label: 'Bodegas', route: '/datos-maestros/bodegas', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/></svg>', roles: ['Administrador'] },
    { label: 'Inventario', route: '/inventario', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>', roles: ['Administrador', 'Operador de Bodega', 'Supervisor E-Commerce', 'Encargado de Compras'] },
    { label: 'Clientes', route: '/clientes', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>', roles: ['Administrador', 'Operador de Pedidos', 'Supervisor E-Commerce'] },
    { label: 'Pedidos', route: '/pedidos', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] },
    { label: 'Pedidos Especiales', route: '/pedidos/especiales', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] },
    { label: 'Comprobantes', route: '/comprobantes', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="3" width="20" height="18" rx="2"/><line x1="8" y1="7" x2="16" y2="7"/><line x1="8" y1="11" x2="16" y2="11"/><line x1="8" y1="15" x2="12" y2="15"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] },
    { label: 'Picking', route: '/picking', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg>', roles: ['Administrador', 'Operador de Bodega'] },
    { label: 'Empaque', route: '/empaque', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>', roles: ['Administrador', 'Operador de Bodega'] },
    { label: 'Despachos', route: '/despachos', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>', roles: ['Administrador', 'Operador de Bodega', 'Supervisor E-Commerce'] },
    { label: 'Devoluciones', route: '/devoluciones', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Operador de Pedidos', 'Operador de Bodega'] },
    { label: 'Compras', route: '/compras', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"/></svg>', roles: ['Administrador', 'Encargado de Compras'] },
    { label: 'Cuentas por Pagar', route: '/cuentas-por-pagar', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>', roles: ['Administrador', 'Encargado de Compras', 'Supervisor E-Commerce'] },
    { label: 'Dev. a Proveedor', route: '/devoluciones-proveedor', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 14l-4-4 4-4"/><path d="M5 10h11a4 4 0 110 8h-1"/></svg>', roles: ['Administrador', 'Encargado de Compras'] },
    // F31 — se añade Encargado de Compras: la ruta y el backend ya le permiten
    // leer materia prima (la compra), faltaba el enlace en el navbar.
    { label: 'Materia Prima', route: '/materia-prima', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>', roles: ['Administrador', 'Encargado de Producción', 'Encargado de Compras'] },
    { label: 'Producción', route: '/produccion', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2 20h20"/><path d="M4 20V8l6 4V8l6 4V8l4 3v9"/></svg>', roles: ['Administrador', 'Encargado de Producción'] },
    { label: 'Dashboard Producción', route: '/produccion/dashboard', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="9"/><rect x="14" y="3" width="7" height="5"/><rect x="14" y="12" width="7" height="9"/><rect x="3" y="16" width="7" height="5"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Encargado de Producción'] },
    { label: 'Análisis de Costos', route: '/produccion/costos', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce', 'Encargado de Producción'] },
    { label: 'Análisis del negocio', route: '/analitica', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 3v18h18"/><path d="M7 15l4-5 3 3 5-7"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce'] },
    { label: 'Reportes', route: '/reportes', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce'] },
    { label: 'Asistente IA', route: '/ia', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9c.1-.48-.02-.99-.33-1.38"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce'] },
    { label: 'Auditoría', route: '/auditoria', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>', roles: ['Administrador'] },
    // F92 — el Supervisor entra a mirar el diario de respaldos, no a tocarlo.
    //   Los botones que destruyen algo los cierra el backend por permiso.
    { label: 'Respaldos y recuperación', route: '/respaldos', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce'] },
    { label: 'Usuarios', route: '/usuarios', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>', roles: ['Administrador'] },
    { label: 'Roles', route: '/roles', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>', roles: ['Administrador'] },
    { label: 'Mi Perfil', route: '/perfil', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>' },
  ];

  constructor(
    private authService: AuthService,
    private router: Router,
    private sanitizer: DomSanitizer
  ) {
    // Angular sanitiza innerHTML y elimina los SVG; hay que marcarlos como seguros,
    // igual que en portal.component.ts.
    this.allItems = this.allItems.map(item => ({
      ...item,
      iconSafe: this.sanitizer.bypassSecurityTrustHtml(item.icon)
    }));

    const user = this.authService.getCurrentUser();
    if (user) {
      this.userName = `${user.nombre} ${user.apellido}`;
      this.userInitials = `${user.nombre.charAt(0)}${user.apellido.charAt(0)}`;
      this.isAdmin = this.authService.hasRol('Administrador');
      this.isOperadorBodega = this.authService.hasRol('Operador de Bodega');
      this.isOperadorPedidos = this.authService.hasRol('Operador de Pedidos');
      this.isSupervisor = this.authService.hasRol('Supervisor E-Commerce');
      this.isEncargadoCompras = this.authService.hasRol('Encargado de Compras');
      this.isEncargadoProduccion = this.authService.hasRol('Encargado de Producción');

      if (this.isAdmin) this.userRole = 'Administrador';
      else if (this.isSupervisor) this.userRole = 'Supervisor';
      else if (this.isOperadorBodega) this.userRole = 'Op. Bodega';
      else if (this.isOperadorPedidos) this.userRole = 'Op. Pedidos';
      else if (this.isEncargadoCompras) this.userRole = 'Enc. Compras';
      else if (this.isEncargadoProduccion) this.userRole = 'Enc. Producción';
    }

    this.buildNav();
    this.isMobile = window.innerWidth <= 768;
    this.expandSectionForUrl(this.router.url);
  }

  ngOnInit(): void {
    this.navSub = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => this.expandSectionForUrl((event as NavigationEnd).urlAfterRedirects));
  }

  ngOnDestroy(): void {
    this.navSub?.unsubscribe();
  }

  private expandSectionForUrl(url: string): void {
    const path = url.split('?')[0];
    for (const section of this.navSections) {
      const matches = section.items.some(item =>
        path === item.route || path.startsWith(item.route + '/')
      );
      if (matches) {
        section.collapsed = false;
      }
    }
  }

  private buildNav(): void {
    const userRoles: string[] = [];
    if (this.isAdmin) userRoles.push('Administrador');
    if (this.isSupervisor) userRoles.push('Supervisor E-Commerce');
    if (this.isOperadorBodega) userRoles.push('Operador de Bodega');
    if (this.isOperadorPedidos) userRoles.push('Operador de Pedidos');
    if (this.isEncargadoCompras) userRoles.push('Encargado de Compras');
    if (this.isEncargadoProduccion) userRoles.push('Encargado de Producción');

    const filtered = this.allItems.filter(item => {
      if (!item.roles) return true;
      return item.roles.some(r => userRoles.includes(r));
    });

    // ------------------------------------------------------------------
    // D3 — el menú sigue el flujo del negocio, no el orden en que se fueron
    // programando las pantallas.
    // ------------------------------------------------------------------
    // El orden anterior era: Portal, Dashboard, Datos Maestros, Proveedores,
    // Productos, Bodegas, Inventario, Clientes, Pedidos... Mezclaba
    // configuración con operación diaria, y ponía el abastecimiento DESPUÉS de
    // la venta, que es al revés de como ocurre. Ahora se recorre igual que la
    // mercancía: se define, se compra, se fabrica, se vende, se despacha, se
    // devuelve, y al final se analiza y se administra.
    //
    // Cada sección declara sus rutas en el orden en que se van a usar; el
    // `filtered` de arriba ya ha quitado las que el rol no puede abrir, así que
    // una sección que quede vacía no se pinta.
    const flujo: { title: string; rutas: string[] }[] = [
      { title: 'Inicio',           rutas: ['/inicio', '/indicadores'] },
      // D5 — las seis maestras, en el orden en que se dan de alta: primero el
      //   producto, luego lo que lo clasifica y lo mide, luego dónde vive y a
      //   quién se le compra.
      { title: 'Datos maestros',   rutas: ['/datos-maestros/productos', '/datos-maestros/categorias',
                                           '/datos-maestros/unidades-medida', '/datos-maestros/ciudades',
                                           '/datos-maestros/bodegas', '/datos-maestros/proveedores'] },
      { title: 'Abastecimiento',   rutas: ['/compras', '/cuentas-por-pagar', '/devoluciones-proveedor', '/materia-prima'] },
      { title: 'Producción',       rutas: ['/produccion', '/produccion/dashboard', '/produccion/costos'] },
      { title: 'Venta',            rutas: ['/clientes', '/pedidos', '/pedidos/especiales', '/comprobantes'] },
      { title: 'Almacén y salida', rutas: ['/inventario', '/picking', '/empaque', '/despachos'] },
      { title: 'Posventa',         rutas: ['/devoluciones'] },
      { title: 'Análisis',         rutas: ['/analitica', '/reportes', '/ia'] },
      // F92 — sección propia. Auditoría estaba dentro de «Análisis», entre el
      //   informe de ventas y el asistente, y no es lo mismo: los análisis
      //   contestan «cómo va el negocio» y esto contesta «quién tocó qué, y
      //   cómo vuelvo atrás si sale mal». Son las dos preguntas que se hacen
      //   cuando algo ya ha pasado, y ahora viven juntas.
      { title: 'Auditoría y control', rutas: ['/auditoria', '/respaldos'] },
      { title: 'Administración',   rutas: ['/usuarios', '/roles'] },
      { title: 'Mi cuenta',        rutas: ['/perfil'] },
    ];

    const porRuta = new Map(filtered.map(i => [i.route, i]));

    this.navSections = flujo
      .map(({ title, rutas }) => ({
        title,
        items: rutas.map(r => porRuta.get(r)).filter((i): i is NavItem => !!i),
        // Solo Inicio arranca abierto. Lo demás se despliega al entrar en él,
        // de eso se encarga expandSectionForUrl.
        collapsed: title !== 'Inicio'
      }))
      .filter(s => s.items.length > 0);
  }

  toggleSection(section: NavSection): void {
    section.collapsed = !section.collapsed;
  }

  toggleSidebar(): void {
    this.isCollapsed = !this.isCollapsed;
    document.body.classList.toggle('sidebar-narrow', this.isCollapsed);
  }

  closeMobile(): void {
    if (this.isMobile) this.sidebarOpen = false;
  }

  logout(): void {
    this.authService.logout();
  }
}
