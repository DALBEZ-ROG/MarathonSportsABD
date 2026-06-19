import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="dashboard-content">
      <div class="welcome-card">
        <h2>Bienvenido, {{ userName }}</h2>
        <p>Sistema de Gestión de Pedidos — Panel de Control</p>
        <p class="role-badge">Rol: {{ userRol }}</p>
      </div>
      <div class="modules-grid">
        <a routerLink="/datos-maestros" class="module-card" *ngIf="isAdmin">
          <span class="module-icon">📋</span><h3>Datos Maestros</h3><p>Ciudades, Categorías, Unidades</p>
        </a>
        <a routerLink="/proveedores" class="module-card" *ngIf="isAdmin">
          <span class="module-icon">🏢</span><h3>Proveedores</h3><p>Gestión de proveedores</p>
        </a>
        <a routerLink="/productos" class="module-card" *ngIf="isAdmin">
          <span class="module-icon">📦</span><h3>Productos</h3><p>Catálogo de productos</p>
        </a>
        <a routerLink="/bodegas" class="module-card" *ngIf="isAdmin">
          <span class="module-icon">🏭</span><h3>Bodegas</h3><p>Gestión de bodegas</p>
        </a>
        <a routerLink="/inventario" class="module-card" *ngIf="isAdmin || isOperadorBodega">
          <span class="module-icon">📊</span><h3>Inventario</h3><p>Stock y movimientos</p>
        </a>
        <a routerLink="/clientes" class="module-card" *ngIf="isAdmin || isOperadorPedidos">
          <span class="module-icon">🧑‍💼</span><h3>Clientes</h3><p>Gestión de clientes</p>
        </a>
        <a routerLink="/pedidos" class="module-card">
          <span class="module-icon">🛒</span><h3>Pedidos</h3><p>Gestión de pedidos</p>
        </a>
        <a routerLink="/pedidos/especiales" class="module-card">
          <span class="module-icon">⭐</span><h3>Pedidos Especiales</h3><p>{{ pedidosEspeciales }} pedidos especiales activos</p>
        </a>
        <a routerLink="/comprobantes" class="module-card">
          <span class="module-icon">🧾</span><h3>Comprobantes</h3><p>Comprobantes internos y PDF</p>
        </a>
        <a routerLink="/picking" class="module-card" *ngIf="isAdmin || isOperadorBodega">
          <span class="module-icon">📥</span><h3>Picking</h3><p>{{ pedidosPicking }} pedidos por preparar</p>
        </a>
        <a routerLink="/empaque" class="module-card" *ngIf="isAdmin || isOperadorBodega">
          <span class="module-icon">📦</span><h3>Empaque</h3><p>Empaque y confirmación de envíos</p>
        </a>
        <a routerLink="/despachos" class="module-card" *ngIf="isAdmin || isOperadorBodega || isSupervisor">
          <span class="module-icon">🚚</span><h3>Despachos</h3><p>Pedidos enviados y entregados</p>
        </a>
        <a routerLink="/usuarios" class="module-card" *ngIf="isAdmin">
          <span class="module-icon">👥</span><h3>Usuarios</h3><p>Gestión de usuarios del sistema</p>
        </a>
        <a routerLink="/roles" class="module-card" *ngIf="isAdmin">
          <span class="module-icon">🔐</span><h3>Roles</h3><p>Roles y permisos</p>
        </a>
        <a routerLink="/perfil" class="module-card">
          <span class="module-icon">👤</span><h3>Mi Perfil</h3><p>Datos personales y contraseña</p>
        </a>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-content{padding:2rem;max-width:900px;margin:0 auto}
    .welcome-card{background:#fff;border-radius:12px;padding:2rem;box-shadow:0 2px 12px rgba(0,0,0,.08);text-align:center;margin-bottom:2rem}
    .welcome-card h2{color:#2d5a27;margin-bottom:.5rem}
    .welcome-card p{color:#666}
    .role-badge{display:inline-block;background:#e8f5e9;color:#2d5a27;padding:.3rem 1rem;border-radius:20px;font-weight:600;font-size:.9rem;margin-top:.5rem}
    .modules-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:1rem}
    .module-card{background:#fff;border-radius:10px;padding:1.5rem;box-shadow:0 2px 8px rgba(0,0,0,.06);text-decoration:none;color:inherit;transition:transform .2s;border-left:4px solid #2d5a27}
    .module-card:hover{transform:translateY(-2px);box-shadow:0 4px 16px rgba(0,0,0,.12)}
    .module-icon{font-size:2rem}
    .module-card h3{color:#2d5a27;margin:.5rem 0 .3rem}
    .module-card p{color:#666;font-size:.85rem}
  `]
})
export class DashboardComponent {
  userName = ''; userRol = ''; isAdmin = false; isOperadorBodega = false; isOperadorPedidos = false; isSupervisor = false;
  pedidosEspeciales = 0;
  pedidosPicking = 0;
  constructor(private authService: AuthService, private http: HttpClient) {
    const user = this.authService.getCurrentUser();
    if (user) { this.userName = `${user.nombre} ${user.apellido}`; this.userRol = user.rol; this.isAdmin = this.authService.hasRol('Administrador'); this.isOperadorBodega = this.authService.hasRol('Operador de Bodega'); this.isOperadorPedidos = this.authService.hasRol('Operador de Pedidos'); this.isSupervisor = this.authService.hasRol('Supervisor E-Commerce'); }
    this.http.get<{ totalElements: number }>(`${environment.apiUrl}/pedidos/especiales?page=0&size=1`).subscribe({
      next: res => { this.pedidosEspeciales = res.totalElements; }
    });
    if (this.isAdmin || this.isOperadorBodega) {
      this.http.get<{ totalElements: number }>(`${environment.apiUrl}/picking/pedidos?page=0&size=1`).subscribe({
        next: res => { this.pedidosPicking = res.totalElements; }
      });
    }
  }
}
