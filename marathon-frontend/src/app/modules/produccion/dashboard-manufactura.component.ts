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
            <div class="kpi-icon kpi-icon-gray"><span class="kpi-dot"></span></div>
            <div class="kpi-info">
              <span class="kpi-value">{{ resumen.ordenesPlanificadas }}</span>
              <span class="kpi-label">OP planificadas</span>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon kpi-icon-amber"><span class="kpi-dot"></span></div>
            <div class="kpi-info">
              <span class="kpi-value">{{ resumen.ordenesEnProceso }}</span>
              <span class="kpi-label">OP en proceso</span>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon kpi-icon-green"><span class="kpi-dot"></span></div>
            <div class="kpi-info">
              <span class="kpi-value">{{ resumen.ordenesCompletadasMes }}</span>
              <span class="kpi-label">Completadas (mes)</span>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon kpi-icon-blue"><span class="kpi-dot"></span></div>
            <div class="kpi-info">
              <span class="kpi-value">{{ resumen.unidadesProducidasMes }}</span>
              <span class="kpi-label">Unidades producidas (mes)</span>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon kpi-icon-gold"><span class="kpi-dot"></span></div>
            <div class="kpi-info">
              <span class="kpi-value gold-text">{{ resumen.costoProduccionMes | currency:'USD':'symbol':'1.2-2' }}</span>
              <span class="kpi-label">Costo producción (mes)</span>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon kpi-icon-orange"><span class="kpi-dot"></span></div>
            <div class="kpi-info">
              <span class="kpi-value" [ngClass]="claseMerma()">{{ resumen.mermaPromedioMes | number:'1.2-2' }} %</span>
              <span class="kpi-label">Merma promedio (mes)</span>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-icon kpi-icon-red"><span class="kpi-dot"></span></div>
            <div class="kpi-info">
              <span class="kpi-value" [class.merma-alta]="resumen.materiaPrimaBajoMinimo > 0">{{ resumen.materiaPrimaBajoMinimo }}</span>
              <span class="kpi-label">Materia prima bajo mínimo</span>
            </div>
          </div>
        </section>

        <section class="charts-row">
          <div class="glass-card chart-card">
            <h3>Top 3 productos fabricados (mes)</h3>
            <canvas id="chartTopFab"></canvas>
            <p class="empty" *ngIf="resumen.top3ProductosFabricados.length === 0">Sin producción registrada este mes.</p>
          </div>
          <div class="glass-card chart-card">
            <h3>Órdenes de producción por estado</h3>
            <canvas id="chartEstados"></canvas>
            <p class="empty" *ngIf="resumen.ordenesPorEstado.length === 0">Sin órdenes registradas.</p>
          </div>
        </section>
      </ng-container>
    </div>
  `,
  styles: [`
    .kpi-icon-gray { background: rgba(255,255,255,0.06); color: rgba(255,255,255,0.5); }
    .kpi-dot { width: 10px; height: 10px; border-radius: 50%; background: currentColor; }
    .merma-ok { color: #81C784; }
    .merma-media { color: #FFD54F; }
    .merma-alta { color: #E57373; }
    .gold-text {
      background: linear-gradient(135deg, #C9A84C, #F4E28D);
      -webkit-background-clip: text; -webkit-text-fill-color: transparent; background-clip: text;
    }
    .charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem; }
    @media(max-width: 900px) { .charts-row { grid-template-columns: 1fr; } }
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
    planificada: 'rgba(255,255,255,0.35)',
    en_proceso: '#FFB74D',
    completada: '#81C784',
    cancelada: '#E57373'
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

    const axis = {
      ticks: { color: 'rgba(255,255,255,0.4)', font: { size: 11 } },
      grid: { color: 'rgba(255,255,255,0.03)' }
    };

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
          scales: {
            x: axis,
            y: { beginAtZero: true, ticks: { ...axis.ticks, precision: 0 }, grid: axis.grid }
          }
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
            backgroundColor: estados.map(e => this.estadoColores[e.estado] ?? '#64748b'),
            borderWidth: 0
          }]
        },
        options: {
          responsive: true,
          plugins: { legend: { position: 'bottom', labels: { color: 'rgba(255,255,255,0.6)' } } }
        }
      });
    }
  }
}
