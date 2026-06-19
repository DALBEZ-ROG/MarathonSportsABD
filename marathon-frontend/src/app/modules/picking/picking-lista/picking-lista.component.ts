import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface PickingLinea {
  idDetalle: number;
  idProducto: number;
  productoNombre: string;
  productoDescripcion: string;
  unidadMedidaNombre: string;
  cantidad: number;
  cantidadRecogida: number;
  pickingCompletado: boolean;
  pendiente: number;
}

interface PickingPedido {
  idPedido: number;
  clienteNombre: string;
  clienteApellido: string;
  fechaPedido: string;
  estado: string;
  esPedidoEspecial: boolean;
  tipoEspecial: string;
  notaEspecial: string;
  fechaLimiteEntrega: string;
  lineas: PickingLinea[];
  totalLineas: number;
  lineasCompletadas: number;
  estadoPicking: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

@Component({
  selector: 'app-picking-lista',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="picking-container">
      <div class="toolbar">
        <h2>Picking de Pedidos</h2>
        <div class="filters">
          <label>Estado:</label>
          <select [(ngModel)]="filtro" (ngModelChange)="aplicarFiltro()">
            <option value="todos">Todos</option>
            <option value="pendiente">Pendiente</option>
            <option value="en_progreso">En progreso</option>
            <option value="completo">Completo</option>
          </select>
        </div>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <div class="cards" *ngIf="!loading">
        <div class="card" *ngFor="let p of pedidosFiltrados">
          <div class="card-header">
            <span class="pedido-num"># Pedido {{p.idPedido}}</span>
            <span class="estado-badge" [ngClass]="'ep-'+p.estadoPicking">{{estadoLabel(p.estadoPicking)}}</span>
          </div>

          <div class="card-body">
            <p class="cliente">{{p.clienteNombre}} {{p.clienteApellido}}</p>
            <p class="fecha">{{p.fechaPedido | date:'dd/MM/yyyy HH:mm'}}</p>

            <div class="especial" *ngIf="p.esPedidoEspecial">
              <span class="especial-badge">ESPECIAL · {{tipoLabel(p.tipoEspecial)}}</span>
              <span class="urgente" *ngIf="esUrgente(p)">⚠️ Entrega urgente (&lt;24h)</span>
            </div>

            <div class="progress-wrap">
              <div class="progress-info">
                <span>{{p.lineasCompletadas}} / {{p.totalLineas}} líneas</span>
                <span>{{porcentaje(p)}}%</span>
              </div>
              <div class="progress-bar">
                <div class="progress-fill" [style.width.%]="porcentaje(p)"></div>
              </div>
            </div>
          </div>

          <div class="card-footer">
            <button class="btn-picking" (click)="ejecutar(p.idPedido)">Ejecutar picking</button>
          </div>
        </div>

        <div class="empty" *ngIf="pedidosFiltrados.length === 0">No hay pedidos para picking</div>
      </div>

      <div class="pagination" *ngIf="totalPages > 1">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>
    </div>
  `,
  styles: [`
    .picking-container{max-width:1100px;margin:0 auto}
    .toolbar{display:flex;align-items:center;gap:1rem;flex-wrap:wrap;margin-bottom:1.5rem}
    .toolbar h2{color:#2d5a27;flex:1;margin:0}
    .filters{display:flex;gap:.5rem;align-items:center}
    .filters label{font-size:.85rem;color:#666}
    .filters select{padding:.5rem;border:1px solid #ddd;border-radius:4px;font-size:.85rem}
    .spinner{text-align:center;padding:2rem;color:#666}
    .cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:1rem}
    .card{background:#fff;border-radius:10px;box-shadow:0 1px 6px rgba(0,0,0,.08);display:flex;flex-direction:column;border-left:4px solid #2d5a27}
    .card-header{display:flex;justify-content:space-between;align-items:center;padding:1rem 1rem .5rem}
    .pedido-num{font-weight:700;color:#2d5a27}
    .estado-badge{padding:.25rem .7rem;border-radius:12px;font-size:.7rem;font-weight:700;text-transform:uppercase}
    .ep-pendiente{background:#f5f5f5;color:#666}
    .ep-en_progreso{background:#fff8e1;color:#f57f17}
    .ep-completo{background:#e8f5e9;color:#2e7d32}
    .card-body{padding:0 1rem 1rem}
    .cliente{font-weight:600;margin:.3rem 0}
    .fecha{color:#666;font-size:.8rem;margin:0 0 .6rem}
    .especial{display:flex;flex-direction:column;gap:.4rem;margin-bottom:.6rem}
    .especial-badge{background:#9c27b0;color:#fff;padding:.2rem .6rem;border-radius:10px;font-size:.7rem;font-weight:700;align-self:flex-start}
    .urgente{background:#ffebee;color:#c62828;border:1px solid #c62828;padding:.3rem .6rem;border-radius:6px;font-size:.75rem;font-weight:700}
    .progress-wrap{margin-top:.5rem}
    .progress-info{display:flex;justify-content:space-between;font-size:.75rem;color:#666;margin-bottom:.3rem}
    .progress-bar{height:8px;background:#f5f5f5;border-radius:6px;overflow:hidden}
    .progress-fill{height:100%;background:#2d5a27;transition:width .3s}
    .card-footer{padding:.8rem 1rem;border-top:1px solid #eee}
    .btn-picking{width:100%;background:#2d5a27;color:#fff;border:none;padding:.6rem;border-radius:6px;cursor:pointer;font-weight:600;font-size:.85rem}
    .btn-picking:hover{background:#1e3d1a}
    .empty{grid-column:1/-1;text-align:center;color:#999;padding:2rem}
    .pagination{display:flex;align-items:center;justify-content:center;gap:1rem;margin-top:1.5rem}
    .pagination button{padding:.4rem .8rem;border:1px solid #ddd;border-radius:4px;background:#fff;cursor:pointer}
    .pagination button:disabled{opacity:.5;cursor:not-allowed}
  `]
})
export class PickingListaComponent implements OnInit {
  pedidos: PickingPedido[] = [];
  pedidosFiltrados: PickingPedido[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtro = 'todos';

  constructor(private http: HttpClient, private router: Router) {}

  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    const params = new HttpParams().set('page', this.page).set('size', this.size);
    this.http.get<PageResponse<PickingPedido>>(`${environment.apiUrl}/picking/pedidos`, { params }).subscribe({
      next: res => {
        this.pedidos = res.content;
        this.totalPages = res.totalPages;
        this.aplicarFiltro();
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  aplicarFiltro() {
    if (this.filtro === 'todos') {
      this.pedidosFiltrados = this.pedidos;
    } else {
      this.pedidosFiltrados = this.pedidos.filter(p => p.estadoPicking === this.filtro);
    }
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  ejecutar(idPedido: number) { this.router.navigate(['/picking', idPedido]); }

  porcentaje(p: PickingPedido): number {
    if (!p.totalLineas) return 0;
    return Math.round((p.lineasCompletadas / p.totalLineas) * 100);
  }

  estadoLabel(estado: string): string {
    switch (estado) {
      case 'pendiente': return 'Pendiente';
      case 'en_progreso': return 'En progreso';
      case 'completo': return 'Completo';
      default: return estado || '';
    }
  }

  tipoLabel(tipo: string): string {
    switch (tipo) {
      case 'personalizado': return 'Personalizado';
      case 'regalo': return 'Regalo';
      case 'corporativo': return 'Corporativo';
      default: return tipo || '';
    }
  }

  esUrgente(p: PickingPedido): boolean {
    if (!p.fechaLimiteEntrega) return false;
    const diff = new Date(p.fechaLimiteEntrega).getTime() - Date.now();
    return diff >= 0 && diff <= 24 * 60 * 60 * 1000;
  }
}
