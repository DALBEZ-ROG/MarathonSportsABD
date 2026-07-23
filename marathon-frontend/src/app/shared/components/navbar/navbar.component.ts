import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

interface NavItem {
  label: string;
  route: string;
  icon: string;
  roles?: string[];
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
        <button class="collapse-btn" (click)="isCollapsed = !isCollapsed" *ngIf="!isMobile">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline *ngIf="!isCollapsed" points="15 18 9 12 15 6"/>
            <polyline *ngIf="isCollapsed" points="9 18 15 12 9 6"/>
          </svg>
        </button>
      </div>

      <!-- Navigation -->
      <nav class="sidebar-nav">
        <div class="nav-section" *ngFor="let section of navSections">
          <span class="section-label" *ngIf="!isCollapsed">{{ section.title }}</span>
          <a *ngFor="let item of section.items"
             [routerLink]="item.route"
             routerLinkActive="active"
             [routerLinkActiveOptions]="{exact: item.route === '/dashboard'}"
             class="nav-item"
             [title]="item.label"
             (click)="closeMobile()">
            <span class="nav-icon" [innerHTML]="item.icon"></span>
            <span class="nav-label" *ngIf="!isCollapsed">{{ item.label }}</span>
          </a>
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
      font-size: 1.05rem; font-weight: 400; letter-spacing: 1.5px;
      background: linear-gradient(135deg, #fff, #C9A84C);
      -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
    }
    .brand-sub { font-size: .7rem; color: rgba(255,255,255,0.3); letter-spacing: 2px; text-transform: uppercase; }

    .collapse-btn {
      background: none; border: none; color: rgba(255,255,255,0.3);
      cursor: pointer; padding: 6px; border-radius: 6px;
      transition: all .2s;
    }
    .collapse-btn:hover { color: #C9A84C; background: rgba(255,255,255,0.04); }

    /* ── Nav ── */
    .sidebar-nav {
      flex: 1; overflow-y: auto; overflow-x: hidden;
      padding: .75rem .5rem;
      scrollbar-width: thin;
      scrollbar-color: rgba(255,255,255,0.08) transparent;
    }

    .nav-section { margin-bottom: 1rem; }

    .section-label {
      display: block; padding: .4rem .75rem; margin-bottom: .25rem;
      font-size: .65rem; color: rgba(255,255,255,0.25);
      text-transform: uppercase; letter-spacing: 1.5px; font-weight: 500;
    }

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
export class NavbarComponent {
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

  navSections: { title: string; items: NavItem[] }[] = [];

  private allItems: NavItem[] = [
    { label: 'Dashboard', route: '/dashboard', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/></svg>' },
    { label: 'Datos Maestros', route: '/datos-maestros', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 7V4h16v3"/><path d="M9 20h6"/><path d="M12 4v16"/></svg>', roles: ['Administrador'] },
    { label: 'Proveedores', route: '/proveedores', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>', roles: ['Administrador'] },
    { label: 'Productos', route: '/productos', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 01-8 0"/></svg>', roles: ['Administrador'] },
    { label: 'Bodegas', route: '/bodegas', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/></svg>', roles: ['Administrador'] },
    { label: 'Inventario', route: '/inventario', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>', roles: ['Administrador', 'Operador de Bodega'] },
    { label: 'Clientes', route: '/clientes', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 00-3-3.87"/><path d="M16 3.13a4 4 0 010 7.75"/></svg>', roles: ['Administrador', 'Operador de Pedidos'] },
    { label: 'Pedidos', route: '/pedidos', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>' },
    { label: 'Pedidos Especiales', route: '/pedidos/especiales', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>' },
    { label: 'Comprobantes', route: '/comprobantes', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="2" y="3" width="20" height="18" rx="2"/><line x1="8" y1="7" x2="16" y2="7"/><line x1="8" y1="11" x2="16" y2="11"/><line x1="8" y1="15" x2="12" y2="15"/></svg>' },
    { label: 'Picking', route: '/picking', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><polyline points="9 11 12 14 22 4"/><path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/></svg>', roles: ['Administrador', 'Operador de Bodega'] },
    { label: 'Empaque', route: '/empaque', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>', roles: ['Administrador', 'Operador de Bodega'] },
    { label: 'Despachos', route: '/despachos', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>', roles: ['Administrador', 'Operador de Bodega', 'Supervisor E-Commerce'] },
    { label: 'Compras', route: '/compras', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"/></svg>', roles: ['Administrador', 'Encargado de Compras'] },
    { label: 'Materia Prima', route: '/materia-prima', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>', roles: ['Administrador', 'Encargado de Producción'] },
    { label: 'Reportes', route: '/reportes', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><line x1="18" y1="20" x2="18" y2="10"/><line x1="12" y1="20" x2="12" y2="4"/><line x1="6" y1="20" x2="6" y2="14"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce'] },
    { label: 'Asistente IA', route: '/ia', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9c.1-.48-.02-.99-.33-1.38"/></svg>', roles: ['Administrador', 'Supervisor E-Commerce'] },
    { label: 'Auditoría', route: '/auditoria', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>', roles: ['Administrador'] },
    { label: 'Usuarios', route: '/usuarios', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>', roles: ['Administrador'] },
    { label: 'Roles', route: '/roles', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0110 0v4"/></svg>', roles: ['Administrador'] },
    { label: 'Mi Perfil', route: '/perfil', icon: '<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>' },
  ];

  constructor(private authService: AuthService, private router: Router) {
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

    // Group into sections
    const main = filtered.filter(i => ['/dashboard'].includes(i.route));
    const gestion = filtered.filter(i => ['/datos-maestros', '/proveedores', '/productos', '/bodegas', '/inventario', '/clientes'].includes(i.route));
    const pedidos = filtered.filter(i => ['/pedidos', '/pedidos/especiales', '/comprobantes'].includes(i.route));
    const operaciones = filtered.filter(i => ['/picking', '/empaque', '/despachos'].includes(i.route));
    const compras = filtered.filter(i => ['/compras', '/materia-prima'].includes(i.route));
    const analytics = filtered.filter(i => ['/reportes', '/ia', '/auditoria'].includes(i.route));
    const cuenta = filtered.filter(i => ['/usuarios', '/roles', '/perfil'].includes(i.route));

    this.navSections = [
      { title: 'Principal', items: main },
      ...(gestion.length ? [{ title: 'Gestión', items: gestion }] : []),
      ...(pedidos.length ? [{ title: 'Pedidos', items: pedidos }] : []),
      ...(operaciones.length ? [{ title: 'Operaciones', items: operaciones }] : []),
      ...(compras.length ? [{ title: 'Compras', items: compras }] : []),
      ...(analytics.length ? [{ title: 'Análisis', items: analytics }] : []),
      ...(cuenta.length ? [{ title: 'Cuenta', items: cuenta }] : []),
    ];
  }

  closeMobile(): void {
    if (this.isMobile) this.sidebarOpen = false;
  }

  logout(): void {
    this.authService.logout();
  }
}
