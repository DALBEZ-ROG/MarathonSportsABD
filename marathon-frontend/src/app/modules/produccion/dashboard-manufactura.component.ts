import { AfterViewInit, Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Chart, registerables } from 'chart.js';
import { ApiService } from '../../core/services/api.service';

Chart.register(...registerables);

interface TopProductoFabricado { idProducto: number; producto: string; unidades: number; }
interface EstadoOp { estado: string; cantidad: number; }

interface ResumenManufactura {
  ordenesPlanificadas: number;
  ordenesEnProceso: number;
  ordenesCompletadasMes: number;
  unidadesProducidasMes: number;
  costoProduccionMes: number;
  mermaPromedioMes: number;
  materiaPrimaBajoMinimo: number;
  top3ProductosFabricados: TopProductoFabricado[];
  ordenesPorEstado: EstadoOp[];
}

@Component({
  selector: 'app-dashboard-manufactura',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Dashboard de Producción</h2>
        <div>
          <button class="btn-cancel" routerLink="/produccion">Órdenes</button>
          <button class="btn-cancel" routerLink="/produccion/costos">Análisis de costos</button>
        </div>
      </div>

      <div class="spinner" *ngIf="cargando">Cargando…</div>

      <ng-container *ngIf="!cargando && resumen">
        <section class="kpi-grid">
          <div class="kpi-card">
            <span class="kpi-label">OP planificadas</span>
            <span class="kpi-value">{{ resumen.ordenesPlanificadas }}</span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">OP en proceso</span>
            <span class="kpi-value amber">{{ resumen.ordenesEnProceso }}</span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">Completadas (mes)</span>
            <span class="kpi-value green">{{ resumen.ordenesCompletadasMes }}</span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">Unidades producidas (mes)</span>
            <span class="kpi-value">{{ resumen.unidadesProducidasMes }}</span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">Costo producción (mes)</span>
            <span class="kpi-value gold">{{ resumen.costoProduccionMes | currency:'USD':'symbol':'1.2-2' }}</span>
          </div>
          <div class="kpi-card">
            <span class="kpi-label">Merma promedio (mes)</span>
            <span class="kpi-value" [ngClass]="claseMerma()">{{ resumen.mermaPromedioMes | number:'1.2-2' }} %</span>
          </div>
          <div class="kpi-card clickable" routerLink="/materia-prima">
            <span class="kpi-label">Materia prima bajo mínimo</span>
            <span class="kpi-value" [class.red]="resumen.materiaPrimaBajoMinimo > 0">{{ resumen.materiaPrimaBajoMinimo }}</span>
          </div>
        </section>

        <section class="charts">
          <div class="chart-card">
            <h3>Top 3 productos fabricados (mes)</h3>
            <canvas id="chartTopFab"></canvas>
            <p class="empty" *ngIf="resumen.top3ProductosFabricados.length === 0">Sin producción registrada este mes.</p>
          </div>
          <div class="chart-card">
            <h3>Órdenes de producción por estado</h3>
            <canvas id="chartEstados"></canvas>
            <p class="empty" *ngIf="resumen.ordenesPorEstado.length === 0">Sin órdenes registradas.</p>
          </div>
        </section>
      </ng-container>
    </div>
  `,
  styles: [`
    .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .kpi-card { display: flex; flex-direction: column; gap: .4rem; background: rgba(255,255,255,0.03);
      border: 1px solid rgba(255,255,255,0.06); border-radius: 12px; padding: 1rem 1.1rem; }
    .kpi-card.clickable { cursor: pointer; }
    .kpi-label { font-size: .72rem; text-transform: uppercase; letter-spacing: 1px; color: rgba(255,255,255,0.45); }
    .kpi-value { font-size: 1.5rem; font-weight: 700; }
    .kpi-value.green { color: #4ade80; }
    .kpi-value.amber { color: #fbbf24; }
    .kpi-value.red { color: #f87171; }
    .kpi-value.gold { color: #C9A84C; }
    .merma-ok { color: #4ade80; }
    .merma-media { color: #fbbf24; }
    .merma-alta { color: #f87171; }
    .charts { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    @media(max-width: 900px) { .charts { grid-template-columns: 1fr; } }
    .chart-card { background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.06);
      border-radius: 12px; padding: 1rem; }
    .chart-card h3 { margin: 0 0 .75rem; font-size: .95rem; }
    .chart-card canvas { max-height: 260px; }
  `]
})
export class DashboardManufacturaComponent implements OnInit, AfterViewInit, OnDestroy {

  resumen: ResumenManufactura | null = null;
  cargando = false;
  private chartTop: Chart | null = null;
  private chartEstados: Chart | null = null;
  private viewReady = false;

  private readonly estadoColores: { [k: string]: string } = {
    planificada: '#6b7280',
    en_proceso: '#d97706',
    completada: '#16a34a',
    cancelada: '#dc2626'
  };

  constructor(private api: ApiService) {}

  ngOnInit(): void { this.cargar(); }

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.dibujar();
  }

  ngOnDestroy(): void {
    this.chartTop?.destroy();
    this.chartEstados?.destroy();
  }

  cargar(): void {
    this.cargando = true;
    this.api.get<ResumenManufactura>('dashboard/manufactura').subscribe({
      next: (res) => {
        this.resumen = res;
        this.cargando = false;
        setTimeout(() => this.dibujar(), 0);
      },
      error: () => { this.cargando = false; }
    });
  }

  claseMerma(): string {
    const m = this.resumen?.mermaPromedioMes ?? 0;
    if (m < 5) { return 'merma-ok'; }
    if (m <= 15) { return 'merma-media'; }
    return 'merma-alta';
  }

  private dibujar(): void {
    if (!this.viewReady || !this.resumen) { return; }

    const top = this.resumen.top3ProductosFabricados ?? [];
    const canvasTop = document.getElementById('chartTopFab') as HTMLCanvasElement | null;
    if (canvasTop && top.length > 0) {
      this.chartTop?.destroy();
      this.chartTop = new Chart(canvasTop, {
        type: 'bar',
        data: {
          labels: top.map(t => t.producto),
          datasets: [{
            label: 'Unidades producidas',
            data: top.map(t => t.unidades),
            backgroundColor: 'rgba(201,168,76,0.65)',
            borderColor: '#C9A84C',
            borderWidth: 1
          }]
        },
        options: {
          responsive: true,
          plugins: { legend: { display: false } },
          scales: { y: { beginAtZero: true, ticks: { precision: 0 } } }
        }
      });
    }

    const estados = this.resumen.ordenesPorEstado ?? [];
    const canvasEst = document.getElementById('chartEstados') as HTMLCanvasElement | null;
    if (canvasEst && estados.length > 0) {
      this.chartEstados?.destroy();
      this.chartEstados = new Chart(canvasEst, {
        type: 'doughnut',
        data: {
          labels: estados.map(e => e.estado),
          datasets: [{
            data: estados.map(e => e.cantidad),
            backgroundColor: estados.map(e => this.estadoColores[e.estado] ?? '#64748b')
          }]
        },
        options: { responsive: true, plugins: { legend: { position: 'bottom' } } }
      });
    }
  }
}
