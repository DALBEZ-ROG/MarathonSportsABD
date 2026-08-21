import { Component, OnInit, OnDestroy, AfterViewInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Chart, registerables } from 'chart.js';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
import { IAChatComponent } from '../ia/ia-chat.component';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

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
  productosFabricados: number;
  ordenesProduccionEnProceso: number;
  costoPromedioProduccionMes: number;
}

interface VentaDia { fecha: string; totalVentas: number; cantidadPedidos: number; }
interface EstadoPedido { estado: string; cantidad: number; }
interface TopProducto { idProducto: number; nombreProducto: string; categoria: string; totalVendido: number; totalIngresos: number; }

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, IAChatComponent, AppIconComponent],
  template: `
    <div class="dashboard">
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
        <div class="kpi-card" *ngIf="verKpisPedidos">
          <div class="kpi-icon kpi-icon-blue">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 2L3 6v14a2 2 0 002 2h14a2 2 0 002-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 01-8 0"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosHoy }}</span>
            <span class="kpi-label">Pedidos hoy</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisPedidos">
          <div class="kpi-icon kpi-icon-green">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 11-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosEntregados }}</span>
            <span class="kpi-label">Entregados</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisPedidos">
          <div class="kpi-icon kpi-icon-orange">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="1" y="3" width="15" height="13"/><polygon points="16 8 20 8 23 11 23 16 16 16 16 8"/><circle cx="5.5" cy="18.5" r="2.5"/><circle cx="18.5" cy="18.5" r="2.5"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosEnviados }}</span>
            <span class="kpi-label">Enviados</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisPedidos">
          <div class="kpi-icon kpi-icon-amber">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosPendientes }}</span>
            <span class="kpi-label">Pendientes</span>
          </div>
        </div>
        <div class="kpi-card kpi-wide" *ngIf="verKpisVentas">
          <div class="kpi-icon kpi-icon-gold">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value gold-text">{{ kpis.totalVentasHoy | currency:'USD':'symbol':'1.2-2' }}</span>
            <span class="kpi-label">Ventas hoy</span>
          </div>
        </div>
        <div class="kpi-card kpi-wide" *ngIf="verKpisVentas">
          <div class="kpi-icon kpi-icon-gold">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value gold-text">{{ kpis.totalVentasMes | currency:'USD':'symbol':'1.2-2' }}</span>
            <span class="kpi-label">Ventas del mes</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisPedidos || isCompras">
          <div class="kpi-icon kpi-icon-red">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.productosStockBajo }}</span>
            <span class="kpi-label">Stock bajo</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisPedidos">
          <div class="kpi-icon kpi-icon-purple">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.pedidosEspecialesActivos }}</span>
            <span class="kpi-label">Especiales</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisCompras">
          <div class="kpi-icon kpi-icon-orange">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 002 1.61h9.72a2 2 0 002-1.61L23 6H6"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ ocPendientesAprobacion }}</span>
            <span class="kpi-label">OC por aprobar</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisCompras">
          <div class="kpi-icon kpi-icon-red">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ cxpVencidas }}</span>
            <span class="kpi-label">CxP vencidas</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="isAdmin || isOperadorBodega">
          <div class="kpi-icon kpi-icon-purple">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 102.13-9.36L1 10"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ devPendientesInspeccion }}</span>
            <span class="kpi-label">Dev. pendientes insp.</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisProduccion">
          <div class="kpi-icon kpi-icon-green">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.productosFabricados }}</span>
            <span class="kpi-label">Productos fabricados</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisProduccion">
          <div class="kpi-icon kpi-icon-red">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ mpStockBajo }}</span>
            <span class="kpi-label">Materia prima bajo mínimo</span>
          </div>
        </div>
        <div class="kpi-card" *ngIf="verKpisProduccion">
          <div class="kpi-icon kpi-icon-orange">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 20h20"/><path d="M4 20V8l6 4V8l6 4V8l4 3v9"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value">{{ kpis.ordenesProduccionEnProceso }}</span>
            <span class="kpi-label">OP en proceso</span>
          </div>
        </div>
        <div class="kpi-card kpi-wide" *ngIf="isAdmin || isSupervisor || isProduccion">
          <div class="kpi-icon kpi-icon-gold">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="1" x2="12" y2="23"/><path d="M17 5H9.5a3.5 3.5 0 000 7h5a3.5 3.5 0 010 7H6"/></svg>
          </div>
          <div class="kpi-info">
            <span class="kpi-value gold-text">{{ (kpis.costoPromedioProduccionMes || 0) | currency:'USD':'symbol':'1.2-2' }}</span>
            <span class="kpi-label">Costo prom. producción (mes)</span>
          </div>
        </div>
      </section>

      <!-- F32 — aviso cuando el rolGuard bloquea una ruta -->
      <div class="acceso-denegado" *ngIf="accesoDenegado">
        <span class="inline-icon-text"><app-icon name="lock" [size]="16"/> No tienes acceso a esa sección con tu rol actual.</span>
        <button (click)="accesoDenegado = false">Entendido</button>
      </div>

      <!-- Resumen compacto de Manufactura (F30) -->
      <section class="glass-card manuf-strip" *ngIf="(isAdmin || isSupervisor || isProduccion) && resumenManuf">
        <div class="manuf-head">
          <h3>Producción</h3>
          <button class="manuf-link" (click)="irA('/produccion/dashboard')">Ver dashboard de producción →</button>
        </div>
        <div class="manuf-kpis">
          <div class="manuf-kpi">
            <span class="manuf-label">OP en proceso</span>
            <span class="manuf-value">{{ resumenManuf.ordenesEnProceso }}</span>
          </div>
          <div class="manuf-kpi">
            <span class="manuf-label">Unidades producidas (mes)</span>
            <span class="manuf-value">{{ resumenManuf.unidadesProducidasMes }}</span>
          </div>
          <div class="manuf-kpi">
            <span class="manuf-label">Merma promedio (mes)</span>
            <span class="manuf-value" [ngClass]="claseMermaManuf()">{{ resumenManuf.mermaPromedioMes | number:'1.2-2' }} %</span>
          </div>
        </div>
      </section>

      <!-- Charts Row (solo roles comerciales — F31) -->
      <section class="charts-row" *ngIf="verKpisPedidos">
        <div class="glass-card chart-card">
          <div class="card-head">
            <h3>Ventas por día</h3>
            <div class="period-toggle" role="group" aria-label="Periodo de ventas">
              <button type="button" [class.active]="diasVentas === 7" (click)="cambiarDias(7)">7 días</button>
              <button type="button" [class.active]="diasVentas === 15" (click)="cambiarDias(15)">15 días</button>
              <button type="button" [class.active]="diasVentas === 30" (click)="cambiarDias(30)">30 días</button>
            </div>
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

      <!-- Top Products (solo roles comerciales — F31) -->
      <section class="glass-card" *ngIf="verKpisPedidos">
        <div class="card-head">
          <h3>Top productos vendidos</h3>
          <div class="period-toggle" role="group" aria-label="Cantidad de productos">
            <button type="button" [class.active]="limiteTop === 5" (click)="cambiarLimite(5)">Top 5</button>
            <button type="button" [class.active]="limiteTop === 10" (click)="cambiarLimite(10)">Top 10</button>
          </div>
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

      <!-- IA Chat -->
      <section class="glass-card ia-card" *ngIf="(isAdmin || isSupervisor) && iaAbierto">
        <div class="card-head">
          <h3 class="inline-icon-text"><app-icon name="bot" [size]="20"/> Asistente IA</h3>
          <div class="ia-actions">
            <button class="btn-toggle-ia" (click)="iaAbierto = false"><app-icon name="x" [size]="14"/> Cerrar</button>
            <a class="btn-fullscreen" routerLink="/ia">Pantalla completa</a>
          </div>
        </div>
        <div class="ia-embed">
          <app-ia-chat></app-ia-chat>
        </div>
      </section>

      <button class="fab-ia" *ngIf="(isAdmin || isSupervisor) && !iaAbierto" (click)="iaAbierto = true">
        <app-icon name="bot" [size]="24"/>
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
      background: transparent;
      overflow: hidden;
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
      font-size: 2rem; font-weight: 400; letter-spacing: 1px;
      color: #fff;
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

    /* ── Aviso de acceso denegado (F32) ── */
    .acceso-denegado {
      position: relative; z-index: 2;
      display: flex; justify-content: space-between; align-items: center; gap: 1rem;
      background: rgba(220,38,38,0.12); border: 1px solid rgba(220,38,38,0.35);
      color: #fca5a5; border-radius: 10px; padding: .85rem 1.1rem; margin-bottom: 1.5rem;
    }
    .acceso-denegado button {
      background: none; border: 1px solid rgba(252,165,165,0.4); color: #fca5a5;
      padding: .35rem .8rem; border-radius: 6px; cursor: pointer;
    }

    /* ── Resumen Manufactura (F30) ── */
    .manuf-strip { position: relative; z-index: 1; margin-bottom: 2rem; }
    .manuf-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: .75rem; }
    .manuf-head h3 { margin: 0; }
    .manuf-link { background: none; border: none; color: #C9A84C; cursor: pointer; font-size: .85rem; }
    .manuf-kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 1rem; }
    .manuf-kpi { display: flex; flex-direction: column; gap: .3rem; }
    .manuf-label { font-size: .7rem; text-transform: uppercase; letter-spacing: 1px; color: rgba(255,255,255,0.45); }
    .manuf-value { font-size: 1.35rem; font-weight: 700; }
    .manuf-ok { color: #4ade80; }
    .manuf-media { color: #fbbf24; }
    .manuf-alta { color: #f87171; }

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

    .card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1rem; gap: 1rem; }
    .card-head h3 { margin: 0; }

    .period-toggle {
      display: flex;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 8px;
      overflow: hidden;
    }
    .period-toggle button {
      background: transparent;
      border: none;
      color: rgba(255,255,255,0.45);
      padding: .4rem .75rem;
      font-size: .78rem;
      cursor: pointer;
      transition: all .2s;
    }
    .period-toggle button:hover { color: rgba(255,255,255,0.8); }
    .period-toggle button.active {
      background: rgba(201,168,76,0.15);
      color: #C9A84C;
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
  isProduccion = false;
  isCompras = false;

  /**
   * F31 — Visibilidad por rol en el dashboard.
   * Los roles de Compras y Producción NO ven KPIs comerciales (pedidos/ventas),
   * porque no son de su competencia.
   */
  get verKpisPedidos(): boolean {
    return this.isAdmin || this.isSupervisor || this.isOperadorPedidos || this.isOperadorBodega;
  }
  /** Los importes de ventas son información sensible: solo Admin y Supervisor. */
  get verKpisVentas(): boolean {
    return this.isAdmin || this.isSupervisor;
  }
  get verKpisCompras(): boolean {
    return this.isAdmin || this.isCompras || this.isSupervisor;
  }
  get verKpisProduccion(): boolean {
    return this.isAdmin || this.isProduccion;
  }

  kpis: DashboardKpis | null = null;
  resumenManuf: any = null;
  accesoDenegado = false;
  topProductos: TopProducto[] = [];
  totalPedidos = 0;
  cargando = false;
  ocPendientesAprobacion = 0;
  cxpVencidas = 0;
  devPendientesInspeccion = 0;
  mpStockBajo = 0;

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
    // F32 — el rolGuard redirige aquí con ?acceso=denegado al bloquear una ruta
    this.accesoDenegado = new URLSearchParams(window.location.search).get('acceso') === 'denegado';
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
    this.isProduccion = this.authService.hasRol('Encargado de Producción');
    this.isCompras = this.authService.hasRol('Encargado de Compras');
    if (this.isAdmin || this.isSupervisor || this.isProduccion) { this.cargarResumenManufactura(); }
    this.cargarKpis();
    this.cargarTopProductos();
    // F31 — cada rol carga solo lo que le compete
    if (this.verKpisCompras) { this.cargarOcPendientes(); this.cargarCxpVencidas(); }
    if (this.verKpisProduccion) { this.cargarMpStockBajo(); }
  }

  cargarOcPendientes(): void {
    this.http.get<any>(`${this.apiUrl}/ordenes-compra?estado=pendiente_aprobacion&size=1`).subscribe({
      next: res => { this.ocPendientesAprobacion = res.totalElements || 0; },
      error: () => { }
    });
  }

  cargarCxpVencidas(): void {
    this.http.get<any>(`${this.apiUrl}/cuentas-por-pagar?estado=vencida&size=1`).subscribe({
      next: res => { this.cxpVencidas = res.totalElements || 0; },
      error: () => { }
    });
  }

  cargarDevPendientes(): void {
    this.http.get<any>(`${this.apiUrl}/devoluciones?estado=solicitada&size=1`).subscribe({
      next: res => { this.devPendientesInspeccion = res.totalElements || 0; },
      error: () => { }
    });
  }

  // F30 — resumen compacto de manufactura
  cargarResumenManufactura(): void {
    this.http.get<any>(`${this.apiUrl}/dashboard/manufactura`).subscribe({
      next: res => { this.resumenManuf = res; },
      error: () => { }
    });
  }

  claseMermaManuf(): string {
    const m = this.resumenManuf?.mermaPromedioMes ?? 0;
    if (m < 5) { return 'manuf-ok'; }
    if (m <= 15) { return 'manuf-media'; }
    return 'manuf-alta';
  }

  cargarMpStockBajo(): void {
    this.http.get<any[]>(`${this.apiUrl}/materia-prima/stock-bajo`).subscribe({
      next: res => { this.mpStockBajo = res.length || 0; },
      error: () => { }
    });
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
    if (this.isAdmin) { this.cargarOcPendientes(); }
    if (this.isAdmin || this.isSupervisor) { this.cargarCxpVencidas(); }
    if (this.isAdmin || this.isOperadorBodega) { this.cargarDevPendientes(); }
    if (this.isAdmin) { this.cargarMpStockBajo(); }
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
    // F31 — los gráficos comerciales no se renderizan para Compras/Producción
    if (!this.viewReady || !this.verKpisPedidos) { return; }
    this.http.get<VentaDia[]>(`${this.apiUrl}/dashboard/ventas-por-dia?dias=${this.diasVentas}`).subscribe({
      next: res => { this.renderVentas(res); }
    });
  }

  cargarEstados(): void {
    if (!this.viewReady || !this.verKpisPedidos) { return; }
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

  cambiarDias(dias: number): void {
    if (this.diasVentas === dias) return;
    this.diasVentas = dias;
    this.cargarVentas();
  }

  cambiarLimite(limite: number): void {
    if (this.limiteTop === limite) return;
    this.limiteTop = limite;
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
