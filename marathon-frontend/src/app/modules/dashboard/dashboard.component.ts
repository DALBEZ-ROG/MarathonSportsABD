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
      <div class="dash-header">
        <div>
          <h1>Dashboard — Marathon Sports</h1>
          <p class="subhead">{{ fechaActual }} · {{ userName }}</p>
        </div>
        <div class="header-actions">
          <button class="btn-refresh" (click)="cargarTodo()" [disabled]="cargando">
            {{ cargando ? 'Actualizando…' : '↻ Actualizar' }}
          </button>
          <label class="toggle">
            <input type="checkbox" [checked]="autoRefresh" (change)="toggleAutoRefresh()">
            <span>Auto-actualizar cada 60s</span>
          </label>
        </div>
      </div>

      <div class="kpi-grid" *ngIf="kpis">
        <div class="kpi-card kpi-blue">
          <span class="kpi-label">Pedidos hoy</span>
          <span class="kpi-value">{{ kpis.pedidosHoy }}</span>
        </div>
        <div class="kpi-card kpi-green">
          <span class="kpi-label">Entregados</span>
          <span class="kpi-value">{{ kpis.pedidosEntregados }}</span>
        </div>
        <div class="kpi-card kpi-orange">
          <span class="kpi-label">Enviados</span>
          <span class="kpi-value">{{ kpis.pedidosEnviados }}</span>
        </div>
        <div class="kpi-card kpi-amber">
          <span class="kpi-label">Pendientes</span>
          <span class="kpi-value">{{ kpis.pedidosPendientes }}</span>
        </div>
        <div class="kpi-card kpi-darkgreen">
          <span class="kpi-label">Ventas hoy</span>
          <span class="kpi-value">{{ kpis.totalVentasHoy | currency:'USD':'symbol':'1.2-2' }}</span>
        </div>
        <div class="kpi-card kpi-greenlight">
          <span class="kpi-label">Ventas del mes</span>
          <span class="kpi-value">{{ kpis.totalVentasMes | currency:'USD':'symbol':'1.2-2' }}</span>
        </div>
        <div class="kpi-card kpi-red clickable" (click)="irA('/inventario')">
          <span class="kpi-label">Stock bajo</span>
          <span class="kpi-value">{{ kpis.productosStockBajo }}</span>
        </div>
        <div class="kpi-card kpi-purple clickable" (click)="irA('/pedidos/especiales')">
          <span class="kpi-label">Pedidos especiales</span>
          <span class="kpi-value">{{ kpis.pedidosEspecialesActivos }}</span>
        </div>
      </div>

      <div class="charts-row">
        <div class="card chart-card">
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
        <div class="card chart-card">
          <div class="card-head">
            <h3>Pedidos por estado</h3>
            <span class="total-badge">Total: {{ totalPedidos }}</span>
          </div>
          <div class="canvas-wrap"><canvas #estadosCanvas></canvas></div>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <h3>Top productos vendidos</h3>
          <select [value]="limiteTop" (change)="cambiarLimite($event)">
            <option [value]="5">Top 5</option>
            <option [value]="10">Top 10</option>
          </select>
        </div>
        <table class="top-table">
          <thead>
            <tr>
              <th>#</th><th>Producto</th><th>Categoría</th>
              <th class="num">Unidades</th><th class="num">Ingresos</th><th>Volumen</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of topProductos; let i = index">
              <td>{{ i + 1 }}</td>
              <td>{{ p.nombreProducto }}</td>
              <td>{{ p.categoria }}</td>
              <td class="num">{{ p.totalVendido }}</td>
              <td class="num">{{ p.totalIngresos | currency:'USD':'symbol':'1.2-2' }}</td>
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

      <div class="card">
        <h3>Accesos rápidos</h3>
        <div class="quick-grid">
          <ng-container *ngIf="isAdmin">
            <button (click)="irA('/usuarios')">Usuarios</button>
            <button (click)="irA('/productos')">Productos</button>
            <button (click)="irA('/bodegas')">Bodegas</button>
            <button (click)="irA('/reportes')">Reportes</button>
            <button (click)="irA('/auditoria')">Auditoría</button>
          </ng-container>
          <ng-container *ngIf="isSupervisor">
            <button (click)="irA('/pedidos')">Pedidos</button>
            <button (click)="irA('/despachos')">Despachos</button>
            <button (click)="irA('/reportes')">Reportes</button>
          </ng-container>
          <ng-container *ngIf="isOperadorBodega">
            <button (click)="irA('/inventario')">Inventario</button>
            <button (click)="irA('/picking')">Picking</button>
            <button (click)="irA('/empaque')">Empaque</button>
          </ng-container>
          <ng-container *ngIf="isOperadorPedidos">
            <button (click)="irA('/pedidos/nuevo')">Nuevo Pedido</button>
            <button (click)="irA('/clientes')">Clientes</button>
            <button (click)="irA('/pedidos/especiales')">Pedidos Especiales</button>
          </ng-container>
          <button class="btn-ia" *ngIf="isAdmin || isSupervisor" (click)="irA('/ia')">🤖 Asistente IA</button>
        </div>
      </div>

      <div class="card ia-card" *ngIf="isAdmin || isSupervisor">
        <div class="card-head">
          <h3>🤖 Asistente IA</h3>
          <div class="ia-actions">
            <button class="btn-toggle-ia" (click)="iaAbierto = !iaAbierto">
              {{ iaAbierto ? '▲ Contraer' : '▼ Expandir' }}
            </button>
            <a class="btn-fullscreen" routerLink="/ia">Abrir en pantalla completa</a>
          </div>
        </div>
        <div class="ia-embed" *ngIf="iaAbierto">
          <app-ia-chat></app-ia-chat>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .dashboard{padding:1.5rem;max-width:1200px;margin:0 auto;background:#f5f5f5}
    .dash-header{display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:1rem;margin-bottom:1.5rem}
    .dash-header h1{color:#2d5a27;margin:0;font-size:1.6rem}
    .subhead{color:#666;margin:.3rem 0 0;font-size:.9rem}
    .header-actions{display:flex;align-items:center;gap:1rem;flex-wrap:wrap}
    .btn-refresh{background:#2d5a27;color:#fff;border:none;padding:.6rem 1.1rem;border-radius:8px;cursor:pointer;font-weight:600}
    .btn-refresh:disabled{opacity:.6;cursor:default}
    .toggle{display:flex;align-items:center;gap:.4rem;color:#444;font-size:.85rem;cursor:pointer}
    .kpi-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:1rem;margin-bottom:1.5rem}
    @media(max-width:900px){.kpi-grid{grid-template-columns:repeat(2,1fr)}}
    @media(max-width:520px){.kpi-grid{grid-template-columns:1fr}}
    .kpi-card{background:#fff;border-radius:12px;padding:1.2rem;box-shadow:0 2px 10px rgba(0,0,0,.06);display:flex;flex-direction:column;gap:.5rem;border-left:5px solid #ccc}
    .kpi-label{color:#666;font-size:.8rem;text-transform:uppercase;letter-spacing:.5px}
    .kpi-value{font-size:1.8rem;font-weight:700;color:#222}
    .clickable{cursor:pointer;transition:transform .15s}
    .clickable:hover{transform:translateY(-2px)}
    .kpi-blue{border-left-color:#2196F3}
    .kpi-green{border-left-color:#4CAF50}
    .kpi-orange{border-left-color:#FF9800}
    .kpi-amber{border-left-color:#FFC107}
    .kpi-darkgreen{border-left-color:#2d5a27}
    .kpi-darkgreen .kpi-value{color:#2d5a27}
    .kpi-greenlight{border-left-color:#66bb6a}
    .kpi-red{border-left-color:#F44336}
    .kpi-purple{border-left-color:#9C27B0}
    .charts-row{display:grid;grid-template-columns:1fr 1fr;gap:1rem;margin-bottom:1.5rem}
    @media(max-width:900px){.charts-row{grid-template-columns:1fr}}
    .card{background:#fff;border-radius:12px;padding:1.2rem;box-shadow:0 2px 10px rgba(0,0,0,.06);margin-bottom:1.5rem}
    .card h3{color:#2d5a27;margin:0 0 1rem}
    .card-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem}
    .card-head h3{margin:0}
    .card-head select{padding:.4rem .6rem;border:1px solid #ddd;border-radius:6px}
    .total-badge{background:#e8f5e9;color:#2d5a27;padding:.3rem .8rem;border-radius:16px;font-weight:600;font-size:.85rem}
    .canvas-wrap{position:relative;height:280px}
    .top-table{width:100%;border-collapse:collapse}
    .top-table th,.top-table td{padding:.6rem .5rem;text-align:left;border-bottom:1px solid #eee;font-size:.9rem}
    .top-table th{color:#666;font-size:.75rem;text-transform:uppercase}
    .top-table .num{text-align:right}
    .top-table .empty{text-align:center;color:#999;padding:1.5rem}
    .bar-bg{background:#eee;border-radius:6px;height:10px;width:100%;overflow:hidden}
    .bar-fill{background:#2d5a27;height:100%;border-radius:6px}
    .quick-grid{display:flex;flex-wrap:wrap;gap:.8rem}
    .quick-grid button{background:#f5f5f5;border:1px solid #2d5a27;color:#2d5a27;padding:.6rem 1.2rem;border-radius:8px;cursor:pointer;font-weight:600;transition:background .15s}
    .quick-grid button:hover{background:#2d5a27;color:#fff}
    .btn-ia{background:#2d5a27 !important;color:#fff !important;border-color:#2d5a27 !important}
    .ia-actions{display:flex;align-items:center;gap:.6rem}
    .btn-toggle-ia{background:#f5f5f5;border:1px solid #2d5a27;color:#2d5a27;padding:.4rem .9rem;border-radius:8px;cursor:pointer;font-weight:600;font-size:.85rem}
    .btn-toggle-ia:hover{background:#2d5a27;color:#fff}
    .btn-fullscreen{background:#2d5a27;color:#fff;text-decoration:none;padding:.45rem 1rem;border-radius:8px;font-weight:600;font-size:.85rem}
    .btn-fullscreen:hover{background:#234a1f}
    .ia-embed{height:400px;overflow:auto;border:1px solid #e0e0e0;border-radius:8px;margin-top:1rem}
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
          borderColor: '#2d5a27',
          backgroundColor: 'rgba(45,90,39,.12)',
          fill: true,
          tension: .3,
          pointBackgroundColor: '#2d5a27'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true } }
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
        datasets: [{ data: valores, backgroundColor: colores, borderWidth: 2, borderColor: '#fff' }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '62%',
        plugins: {
          legend: { position: 'bottom' },
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
