import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <nav class="navbar">
      <a routerLink="/dashboard" class="brand">Marathon Sports</a>
      <button class="menu-toggle" (click)="menuOpen=!menuOpen">☰</button>
      <div class="nav-links" [class.open]="menuOpen">
        <a routerLink="/dashboard" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}">Dashboard</a>
        <a routerLink="/datos-maestros" routerLinkActive="active" *ngIf="isAdmin">Datos Maestros</a>
        <a routerLink="/proveedores" routerLinkActive="active" *ngIf="isAdmin">Proveedores</a>
        <a routerLink="/productos" routerLinkActive="active" *ngIf="isAdmin">Productos</a>
        <a routerLink="/bodegas" routerLinkActive="active" *ngIf="isAdmin">Bodegas</a>
        <a routerLink="/inventario" routerLinkActive="active" *ngIf="isAdmin || isOperadorBodega">Inventario</a>
        <a routerLink="/clientes" routerLinkActive="active" *ngIf="isAdmin || isOperadorPedidos">Clientes</a>
        <a routerLink="/pedidos" routerLinkActive="active">Pedidos</a>
        <a routerLink="/usuarios" routerLinkActive="active" *ngIf="isAdmin">Usuarios</a>
        <a routerLink="/roles" routerLinkActive="active" *ngIf="isAdmin">Roles</a>
        <a routerLink="/perfil" routerLinkActive="active">Mi Perfil</a>
      </div>
      <div class="user-section">
        <span class="user-name">{{userName}}</span>
        <button class="btn-logout" (click)="logout()">Salir</button>
      </div>
    </nav>
  `,
  styles: [`
    .navbar{display:flex;align-items:center;background:#2d5a27;padding:.7rem 1.5rem;gap:1rem;flex-wrap:wrap}
    .brand{color:#fff;font-weight:700;font-size:1.1rem;text-decoration:none}
    .menu-toggle{display:none;background:none;border:none;color:#fff;font-size:1.3rem;cursor:pointer}
    .nav-links{display:flex;gap:.3rem;flex:1}
    .nav-links a{color:rgba(255,255,255,.8);text-decoration:none;padding:.4rem .8rem;border-radius:4px;font-size:.85rem}
    .nav-links a:hover{background:rgba(255,255,255,.1);color:#fff}
    .nav-links a.active{background:rgba(255,255,255,.2);color:#fff}
    .user-section{display:flex;align-items:center;gap:.8rem}
    .user-name{color:rgba(255,255,255,.9);font-size:.85rem}
    .btn-logout{background:transparent;border:1px solid rgba(255,255,255,.5);color:#fff;padding:.3rem .7rem;border-radius:4px;cursor:pointer;font-size:.8rem}
    .btn-logout:hover{background:rgba(255,255,255,.1)}
    @media(max-width:768px){
      .menu-toggle{display:block}
      .nav-links{display:none;width:100%;flex-direction:column}
      .nav-links.open{display:flex}
      .user-section{margin-left:auto}
    }
  `]
})
export class NavbarComponent {
  isAdmin = false;
  isOperadorBodega = false;
  isOperadorPedidos = false;
  userName = '';
  menuOpen = false;

  constructor(private authService: AuthService) {
    const user = this.authService.getCurrentUser();
    if (user) {
      this.userName = `${user.nombre} ${user.apellido}`;
      this.isAdmin = this.authService.hasRol('Administrador');
      this.isOperadorBodega = this.authService.hasRol('Operador de Bodega');
      this.isOperadorPedidos = this.authService.hasRol('Operador de Pedidos');
    }
  }

  logout() { this.authService.logout(); }
}
