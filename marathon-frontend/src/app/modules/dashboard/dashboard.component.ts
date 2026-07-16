import { Component, OnInit, OnDestroy, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Chart, registerables } from 'chart.js';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
import { IAChatComponent } from '../ia/ia-chat.component';

Chart.register(...registerables);

interface DashboardKpis {
  pedidosPendientes: number;
  pedidosProcesados: number;
  pedidosEnviados: number;
  pedidosEntregados: number;
  pedidosAnulados: number;
  pedidosHoy: number;
  totalVentasHoy: number;
  totalVentasMes: number;
  productosStockBajo: number;
  pedidosEspecialesActivos: number;
  pedidosPickingPendiente: number;
}

interface VentaDia { fecha: string; totalVentas: number; cantidadPedidos: number; }
interface EstadoPedido { estado: string; cantidad: number; }
interface TopProducto { idProducto: number; nombreProducto: string; categoria: string; totalVendido: number; totalIngresos: number; }

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, IAChatComponent],
  template: `
    <div class="dashboard">
      <!-- Background Effects -->
      <div class="bg-gradient"></div>
      <div class="bg-orb orb-1"></div>
      <div class="bg-orb orb-2"></div>

      <!-- Header -->
      <header class="dash-header">
        <div class="header-left">
          <h1 class="title">Dashboard</h1>
          <p class="subtitle">{{ fechaActual }} · {{ userName }}</p>
        </div>
        <div class="header-right">
          <button class="btn-refresh" (click)="cargarTodo()" [disabled]="cargando">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" [class.spinning]="cargando">
              <path d="M23 4v6h-6"/><path d="M1 20v-6h6"/>
              <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/>
            </svg>
            {{ cargando ? 'Actualizando…' : 'Actualizar' }}
          </button>
          <label class="toggle-auto">
            <input type="checkbox" [checked]="autoRefresh" (change)="toggleAutoRefresh()">
            <span class="toggle-slider"></span>
            <span class="toggle-label">Auto 60s</span>
          </label>
        </div>
      </header>

      <!-- KPI Grid -->
      <section class="kpi-grid" *ngIf="kpis">
        <div class="kpi-card">
          <div class="kpi-icon kpi-icon-blue">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 01-8 0"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosHoy }}</span>
            <span class="kpi-label">Pedidos hoy</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon kpi-icon-green">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosEntregados }}</span>
            <span class="kpi-label">Entregados</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon kpi-icon-orange">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosEnviados }}</span>
            <span class="kpi-label">Enviados</span>
          </div>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon kpi-icon-amber">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosPendientes }}</span>
            <span class="kpi-label">Pendientes</span>
          </div>
        </div>
        <div class="kpi-card kpi-wide">
          <div class="kpi-icon kpi-icon-gold">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value gold-text">{{ kpis.totalVentasHoy | currency:'USD':'symbol':'1.2-2' }}</span>
            <span class="kpi-label">Ventas hoy</span>
          </div>
        </div>
        <div class="kpi-card kpi-wide">
          <div class="kpi-icon kpi-icon-gold">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value gold-text">{{ kpis.totalVentasMes | currency:'USD':'symbol':'1.2-2' }}</span>
            <span class="kpi-label">Ventas del mes</span>
          </div>
        </div>
        <div class="kpi-card clickable" (click)="irA('/inventario')">
          <div class="kpi-icon kpi-icon-red">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.productosStockBajo }}</span>
            <span class="kpi-label">Stock bajo</span>
          </div>
        </div>
        <div class="kpi-card clickable" (click)="irA('/pedidos/especiales')">
          <div class="kpi-icon kpi-icon-purple">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosEspecialesActivos }}</span>
            <span class="kpi-label">Especiales</span>
          </div>
        </div>
      </section>

      <!-- Charts Row -->
      <section class="charts-row">
        <div class="glass-card chart-card">
          <div class="card-head">
            <h3>Ventas por día</h3>
            <select [value]="diasVentas" (change)="cambiarDias($event)">
              <option [value]="7">7 días</option>
              <option [value]="15">15 días</option>
              <option [value]="30">30 días</option>
            </select>
          </div>
          <div class="canvas-wrap"><canvas #ventasCanvas></canvas></div>
        </div>
        <div class="glass-card chart-card">
          <div class="card-head">
            <h3>Pedidos por estado</h3>
            <span class="total-badge">{{ totalPedidos }} total</span>
          </div>
          <div class="canvas-wrap"><canvas #estadosCanvas></canvas></div>
        </div>
      </section>

      <!-- Top Products -->
      <section class="glass-card">
        <div class="card-head">
          <h3>Top productos vendidos</h3>
          <select [value]="limiteTop" (change)="cambiarLimite($event)">
            <option [value]="5">Top 5</option>
            <option [value]="10">Top 10</option>
          </select>
        </div>
        <div class="table-wrap">
          <table class="top-table">
            <thead>
              <tr>
                <th>#</th><th>Producto</th><th>Categoría</th>
                <th class="num">Unidades</th><th class="num">Ingresos</th><th>Volumen</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let p of topProductos; let i = index">
                <td><span class="rank">{{ i + 1 }}</span></td>
                <td>{{ p.nombreProducto }}</td>
                <td><span class="cat-badge">{{ p.categoria }}</span></td>
                <td class="num">{{ p.totalVendido }}</td>
                <td class="num gold-text">{{ p.totalIngresos | currency:'USD':'symbol':'1.2-2' }}</td>
                <td>
                  <div class="bar-bg">
                    <div class="bar-fill" [style.width.%]="porcentaje(p.totalVendido)"></div>
                  </div>
                </td>
              </tr>
              <tr *ngIf="topProductos.length === 0">
                <td colspan="6" class="empty">Sin datos de ventas</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- Quick Access -->
      <section class="glass-card">
        <h3>Accesos rápidos</h3>
        <div class="quick-grid">
          <ng-container *ngIf="isAdmin">
            <button class="quick-btn" (click)="irA('/usuarios')"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>Usuarios</button>
            <button class="quick-btn" (click)="irA('/productos')"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/></svg>Productos</button>
            <button class="quick-btn" (click)="irA('/bodegas')"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z"/></svg>Bodegas</button>
            <button class="quick-btn" (click)="irA('/reportes')"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>Reportes</button>
            <button class="quick-btn" (click)="irA('/auditoria')"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>Auditoría</button>
          </ng-container>
          <ng-container *ngIf="isSupervisor">
            <button class="quick-btn" (click)="irA('/pedidos')">Pedidos</button>
            <button class="quick-btn" (click)="irA('/despachos')">Despachos</button>
            <button class="quick-btn" (click)="irA('/reportes')">Reportes</button>
          </ng-container>
          <ng-container *ngIf="isOperadorBodega">
            <button class="quick-btn" (click)="irA('/inventario')">Inventario</button>
            <button class="quick-btn" (click)="irA('/picking')">Picking</button>
            <button class="quick-btn" (click)="irA('/empaque')">Empaque</button>
          </ng-container>
          <ng-container *ngIf="isOperadorPedidos">
            <button class="quick-btn" (click)="irA('/pedidos/nuevo')">Nuevo Pedido</button>
            <button class="quick-btn" (click)="irA('/clientes')">Clientes</button>
            <button class="quick-btn" (click)="irA('/pedidos/especiales')">Especiales</button>
          </ng-container>
          <button class="quick-btn btn-ia" *ngIf="isAdmin || isSupervisor" (click)="irA('/ia')">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83 0 2 2 0 010-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 112.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06a1.65 1.65 0 00-.33 1.82V9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z"/></svg>
            Asistente IA
          </button>
        </div>
      </section>

      <!-- IA Chat -->
      <section class="glass-card ia-card" *ngIf="(isAdmin || isSupervisor) && iaAbierto">
        <div class="card-head">
          <h3>🤖 Asistente IA</h3>
          <div class="ia-actions">
            <button class="btn-toggle-ia" (click)="iaAbierto = false">✕ Cerrar</button>
            <a class="btn-fullscreen" routerLink="/ia">Pantalla completa</a>
          </div>
        </div>
        <div class="ia-embed">
          <app-ia-chat></app-ia-chat>
        </div>
      </section>

      <button class="fab-ia" *ngIf="(isAdmin || isSupervisor) && !iaAbierto" (click)="iaAbierto = true">
        🤖
      </button>
    </div>
  `,
  styles: [`
    /* ═══════════════════════════════════════════
       PREMIUM DARK DASHBOARD — DUBAI GLASSMORPHISM
       ═══════════════════════════════════════════ */

    :host { display: block; min-height: 100vh; }

    .dashboard {
      position: relative;
      padding: 2rem;
      max-width: 1320px;
      margin: 0 auto;
      min-height: 100vh;
      background: #0a0a0f;
      overflow: hidden;
    }

    /* ── Background ── */
    .bg-gradient {
      position: fixed;
      top: 0; left: 0; right: 0; bottom: 0;
      background: linear-gradient(160deg, #0a0a0f 0%, #0f1623 30%, #0a1628 60%, #0d0d14 100%);
      z-index: 0;
    }

    .bg-orb {
      position: fixed;
      border-radius: 50%;
      filter: blur(100px);
      opacity: 0.25;
      z-index: 0;
      animation: float 25s ease-in-out infinite;
    }
    .orb-1 { width: 600px; height: 600px; background: radial-gradient(circle, #C9A84C, transparent 70%); top: -10%; right: -5%; }
    .orb-2 { width: 400px; height: 400px; background: radial-gradient(circle, #1a5c3a, transparent 70%); bottom: -5%; left: -5%; animation-delay: -12s; }

    @keyframes float {
      0%, 100% { transform: translate(0, 0) scale(1); }
      33% { transform: translate(20px, -20px) scale(1.03); }
      66% { transform: translate(-15px, 15px) scale(0.97); }
    }

    /* ── Header ── */
    .dash-header {
      position: relative; z-index: 1;
      display: flex; justify-content: space-between; align-items: center;
      flex-wrap: wrap; gap: 1rem;
      margin-bottom: 2rem;
      padding-bottom: 1.5rem;
      border-bottom: 1px solid rgba(255,255,255,0.05);
    }
    .title {
      font-size: 2rem; font-weight: 300; letter-spacing: 1px;
      background: linear-gradient(135deg, #fff 0%, #C9A84C 100%);
      -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
      margin: 0;
    }
    .subtitle { color: rgba(255,255,255,0.4); font-size: 0.85rem; margin: .3rem 0 0; font-weight: 300; }

    .header-right { display: flex; align-items: center; gap: 1rem; }

    .btn-refresh {
      display: flex; align-items: center; gap: .5rem;
      background: rgba(201,168,76,0.1); border: 1px solid rgba(201,168,76,0.3);
      color: #C9A84C; padding: .6rem 1.2rem; border-radius: 10px;
      cursor: pointer; font-weight: 500; font-size: .85rem;
      transition: all .3s;
    }
    .btn-refresh:hover:not(:disabled) { background: rgba(201,168,76,0.2); border-color: #C9A84C; }
    .btn-refresh:disabled { opacity: .5; cursor: default; }
    .btn-refresh .spinning { animation: spin .8s linear infinite; }
    @keyframes spin { to { transform: rotate(360deg); } }

    .toggle-auto {
      display: flex; align-items: center; gap: .5rem; cursor: pointer;
      color: rgba(255,255,255,0.4); font-size: .8rem;
    }
    .toggle-auto input { display: none; }
    .toggle-slider {
      width: 36px; height: 20px; background: rgba(255,255,255,0.1);
      border-radius: 10px; position: relative; transition: background .3s;
    }
    .toggle-slider::after {
      content: ''; position: absolute; width: 16px; height: 16px;
      background: rgba(255,255,255,0.4); border-radius: 50%;
      top: 2px; left: 2px; transition: all .3s;
    }
    .toggle-auto input:checked + .toggle-slider { background: rgba(201,168,76,0.3); }
    .toggle-auto input:checked + .toggle-slider::after { left: 18px; background: #C9A84C; }

    /* ── KPI Grid ── */
    .kpi-grid {
      position: relative; z-index: 1;
      display: grid; grid-template-columns: repeat(4, 1fr);
      gap: 1rem; margin-bottom: 2rem;
    }
    @media(max-width: 1024px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
    @media(max-width: 520px) { .kpi-grid { grid-template-columns: 1fr; } }

    .kpi-card {
      background: rgba(255,255,255,0.03);
      backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 16px; padding: 1.25rem;
      display: flex; align-items: center; gap: 1rem;
      transition: all .3s cubic-bezier(.4,0,.2,1);
    }
    .kpi-card:hover { background: rgba(255,255,255,0.05); transform: translateY(-2px); box-shadow: 0 8px 32px rgba(0,0,0,.3); }
    .kpi-card.clickable { cursor: pointer; }

    .kpi-icon {
      width: 48px; height: 48px; border-radius: 12px;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
    }
    .kpi-icon-blue { background: rgba(33,150,243,0.12); color: #64B5F6; }
    .kpi-icon-green { background: rgba(76,175,80,0.12); color: #81C784; }
    .kpi-icon-orange { background: rgba(255,152,0,0.12); color: #FFB74D; }
    .kpi-icon-amber { background: rgba(255,193,7,0.12); color: #FFD54F; }
    .kpi-icon-gold { background: rgba(201,168,76,0.12); color: #C9A84C; }
    .kpi-icon-red { background: rgba(244,67,54,0.12); color: #E57373; }
    .kpi-icon-purple { background: rgba(156,39,176,0.12); color: #CE93D8; }

    .kpi-info { display: flex; flex-direction: column; }
    .kpi-value { font-size: 1.6rem; font-weight: 600; color: #fff; line-height: 1.2; }
    .kpi-label { font-size: .75rem; color: rgba(255,255,255,0.4); text-transform: uppercase; letter-spacing: .5px; margin-top: .2rem; }

    .gold-text {
      background: linear-gradient(135deg, #C9A84C, #F4E28D);
      -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
    }

    /* ── Glass Cards ── */
    .glass-card {
      position: relative; z-index: 1;
      background: rgba(255,255,255,0.02);
      backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 20px; padding: 1.5rem;
      margin-bottom: 1.5rem;
    }

    .glass-card h3 {
      color: #fff; font-weight: 400; font-size: 1rem; letter-spacing: .5px; margin: 0 0 1rem;
    }

    .card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; }
    .card-head h3 { margin: 0; }
    .card-head select {
      background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
      color: #fff; padding: .4rem .8rem; border-radius: 8px; font-size: .85rem;
    }

    .total-badge {
      background: rgba(201,168,76,0.12); color: #C9A84C;
      padding: .35rem .9rem; border-radius: 20px;
      font-weight: 500; font-size: .8rem;
    }

    /* ── Charts ── */
    .charts-row {
      position: relative; z-index: 1;
      display: grid; grid-template-columns: 1fr 1fr;
      gap: 1.5rem; margin-bottom: 1.5rem;
    }
    @media(max-width: 900px) { .charts-row { grid-template-columns: 1fr; } }

    .canvas-wrap { position: relative; height: 280px; }

    /* ── Table ── */
    .table-wrap { overflow-x: auto; }
    .top-table { width: 100%; border-collapse: collapse; }
    .top-table th, .top-table td {
      padding: .75rem .6rem; text-align: left;
      border-bottom: 1px solid rgba(255,255,255,0.04); font-size: .9rem;
    }
    .top-table th { color: rgba(255,255,255,0.35); font-size: .7rem; text-transform: uppercase; letter-spacing: .5px; font-weight: 500; }
    .top-table td { color: rgba(255,255,255,0.8); }
    .top-table .num { text-align: right; }
    .top-table .empty { text-align: center; color: rgba(255,255,255,0.3); padding: 2rem; }

    .rank {
      display: inline-flex; align-items: center; justify-content: center;
      width: 26px; height: 26px; border-radius: 8px;
      background: rgba(201,168,76,0.1); color: #C9A84C;
      font-weight: 600; font-size: .8rem;
    }

    .cat-badge {
      background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08);
      padding: .2rem .6rem; border-radius: 6px; font-size: .8rem;
      color: rgba(255,255,255,0.6);
    }

    .bar-bg { background: rgba(255,255,255,0.06); border-radius: 6px; height: 8px; width: 100%; overflow: hidden; }
    .bar-fill {
      background: linear-gradient(90deg, #C9A84C, #F4E28D);
      height: 100%; border-radius: 6px;
      transition: width .6s cubic-bezier(.4,0,.2,1);
    }

    /* ── Quick Access ── */
    .quick-grid { display: flex; flex-wrap: wrap; gap: .75rem; }

    .quick-btn {
      display: flex; align-items: center; gap: .5rem;
      background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.08);
      color: rgba(255,255,255,0.7); padding: .65rem 1.2rem;
      border-radius: 10px; cursor: pointer; font-weight: 400;
      font-size: .85rem; transition: all .3s;
    }
    .quick-btn:hover { background: rgba(201,168,76,0.1); border-color: rgba(201,168,76,0.3); color: #C9A84C; }
    .quick-btn svg { opacity: .6; }
    .quick-btn:hover svg { opacity: 1; }

    .btn-ia {
      background: rgba(201,168,76,0.1) !important;
      border-color: rgba(201,168,76,0.3) !important;
      color: #C9A84C !important;
    }
    .btn-ia:hover { background: rgba(201,168,76,0.2) !important; }

    /* ── IA Section ── */
    .ia-card { border-color: rgba(201,168,76,0.15); }
    .ia-actions { display: flex; align-items: center; gap: .6rem; }
    .btn-toggle-ia {
      background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.1);
      color: rgba(255,255,255,0.6); padding: .4rem .9rem;
      border-radius: 8px; cursor: pointer; font-size: .8rem;
      transition: all .3s;
    }
    .btn-toggle-ia:hover { border-color: rgba(201,168,76,0.4); color: #C9A84C; }
    .btn-fullscreen {
      background: rgba(201,168,76,0.12); color: #C9A84C;
      text-decoration: none; padding: .4rem 1rem;
      border-radius: 8px; font-weight: 500; font-size: .8rem;
    }
    .btn-fullscreen:hover { background: rgba(201,168,76,0.2); }
    .ia-embed { height: 400px; overflow: auto; border: 1px solid rgba(255,255,255,0.05); border-radius: 12px; margin-top: 1rem; }

    .fab-ia {
      position: fixed; bottom: 2rem; right: 2rem; z-index: 100;
      width: 56px; height: 56px; border-radius: 50%;
      background: linear-gradient(135deg, #C9A84C, #a08339);
      border: none; font-size: 1.5rem; cursor: pointer;
      box-shadow: 0 8px 32px rgba(201,168,76,0.3);
      transition: all .3s;
      display: flex; align-items: center; justify-content: center;
    }
    .fab-ia:hover { transform: scale(1.1); box-shadow: 0 12px 40px rgba(201,168,76,0.5); }

    /* ── Responsive ── */
    @media(max-width: 768px) {
      .dashboard { padding: 1rem; }
      .title { font-size: 1.5rem; }
      .kpi-value { font-size: 1.3rem; }
    }
  `]
})
export class DashboardComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('ventasCanvas') ventasCanvas!: ElementRef<HTMLCanvasElement>;
  @ViewChild('estadosCanvas') estadosCanvas!: ElementRef<HTMLCanvasElement>;

  private apiUrl = environment.apiUrl;

  userName = '';
  fechaActual = '';
  isAdmin = false;
  isSupervisor = false;
  isOperadorBodega = false;
  isOperadorPedidos = false;

  kpis: DashboardKpis | null = null;
  topProductos: TopProducto[] = [];
  totalPedidos = 0;
  cargando = false;

  diasVentas = 7;
  limiteTop = 5;

  iaAbierto = false;

  autoRefresh = false;
  private refreshTimer: any = null;
  private viewReady = false;

  private ventasChart: Chart | null = null;
  private estadosChart: Chart | null = null;

  private readonly estadoColores: { [k: string]: string } = {
    pendiente: '#FFC107',
    procesado: '#2196F3',
    enviado: '#FF9800',
    entregado: '#4CAF50',
    anulado: '#F44336'
  };

  constructor(private http: HttpClient, private authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    if (user) {
      this.userName = `${user.nombre} ${user.apellido}`;
    }
    this.fechaActual = new Date().toLocaleDateString('es-EC', {
      weekday: 'long', year: 'numeric', month: 'long', day: 'numeric'
    });
    this.isAdmin = this.authService.hasRol('Administrador');
    this.isSupervisor = this.authService.hasRol('Supervisor E-Commerce');
    this.isOperadorBodega = this.authService.hasRol('Operador de Bodega');
    this.isOperadorPedidos = this.authService.hasRol('Operador de Pedidos');
    this.cargarKpis();
    this.cargarTopProductos();
  }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.cargarVentas();
    this.cargarEstados();
  }

  ngOnDestroy(): void {
    this.detenerAutoRefresh();
    if (this.ventasChart) { this.ventasChart.destroy(); }
    if (this.estadosChart) { this.estadosChart.destroy(); }
  }

  cargarTodo(): void {
    this.cargando = true;
    this.cargarKpis();
    this.cargarTopProductos();
    this.cargarVentas();
    this.cargarEstados();
  }

  cargarKpis(): void {
    this.http.get<DashboardKpis>(`${this.apiUrl}/dashboard/kpis`).subscribe({
      next: res => { this.kpis = res; this.cargando = false; },
      error: () => { this.cargando = false; }
    });
  }

  cargarTopProductos(): void {
    this.http.get<TopProducto[]>(`${this.apiUrl}/dashboard/top-productos?limite=${this.limiteTop}`).subscribe({
      next: res => { this.topProductos = res; }
    });
  }

  cargarVentas(): void {
    if (!this.viewReady) { return; }
    this.http.get<VentaDia[]>(`${this.apiUrl}/dashboard/ventas-por-dia?dias=${this.diasVentas}`).subscribe({
      next: res => { this.renderVentas(res); }
    });
  }

  cargarEstados(): void {
    if (!this.viewReady) { return; }
    this.http.get<EstadoPedido[]>(`${this.apiUrl}/dashboard/pedidos-por-estado`).subscribe({
      next: res => { this.renderEstados(res); }
    });
  }

  private renderVentas(data: VentaDia[]): void {
    const labels = data.map(d => d.fecha);
    const valores = data.map(d => d.totalVentas);
    if (this.ventasChart) { this.ventasChart.destroy(); }
    this.ventasChart = new Chart(this.ventasCanvas.nativeElement.getContext('2d')!, {
      type: 'line',
      data: {
        labels,
        datasets: [{
          label: 'Ventas',
          data: valores,
          borderColor: '#C9A84C',
          backgroundColor: 'rgba(201,168,76,.08)',
          fill: true,
          tension: .4,
          pointBackgroundColor: '#C9A84C',
          pointBorderColor: '#C9A84C',
          pointRadius: 4,
          pointHoverRadius: 6,
          borderWidth: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          x: { grid: { color: 'rgba(255,255,255,0.03)' }, ticks: { color: 'rgba(255,255,255,0.4)', font: { size: 11 } } },
          y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.03)' }, ticks: { color: 'rgba(255,255,255,0.4)', font: { size: 11 } } }
        }
      }
    });
  }

  private renderEstados(data: EstadoPedido[]): void {
    this.totalPedidos = data.reduce((acc, d) => acc + d.cantidad, 0);
    const labels = data.map(d => d.estado);
    const valores = data.map(d => d.cantidad);
    const colores = data.map(d => this.estadoColores[d.estado] || '#9E9E9E');
    if (this.estadosChart) { this.estadosChart.destroy(); }
    this.estadosChart = new Chart(this.estadosCanvas.nativeElement.getContext('2d')!, {
      type: 'doughnut',
      data: {
        labels,
        datasets: [{ data: valores, backgroundColor: colores, borderWidth: 0 }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '68%',
        plugins: {
          legend: { position: 'bottom', labels: { color: 'rgba(255,255,255,0.6)', padding: 16, font: { size: 12 } } },
          tooltip: { enabled: true }
        }
      }
    });
  }

  porcentaje(valor: number): number {
    const max = Math.max(...this.topProductos.map(p => p.totalVendido), 1);
    return Math.round((valor / max) * 100);
  }

  cambiarDias(event: Event): void {
    this.diasVentas = Number((event.target as HTMLSelectElement).value);
    this.cargarVentas();
  }

  cambiarLimite(event: Event): void {
    this.limiteTop = Number((event.target as HTMLSelectElement).value);
    this.cargarTopProductos();
  }

  toggleAutoRefresh(): void {
    this.autoRefresh = !this.autoRefresh;
    if (this.autoRefresh) {
      this.refreshTimer = setInterval(() => this.cargarKpis(), 60000);
    } else {
      this.detenerAutoRefresh();
    }
  }

  private detenerAutoRefresh(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
      this.refreshTimer = null;
    }
  }

  irA(ruta: string): void {
    this.router.navigateByUrl(ruta);
  }
}
