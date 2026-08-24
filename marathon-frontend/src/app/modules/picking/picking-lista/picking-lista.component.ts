import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AppIconComponent } from '../../../shared/components/icon/icon.component';

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
  numeroPedido: string;
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
  imports: [CommonModule, FormsModule, AppIconComponent],
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
            <span class="pedido-num">Pedido {{p.numeroPedido}}</span>
            <span class="estado-badge" [ngClass]="'ep-'+p.estadoPicking">{{estadoLabel(p.estadoPicking)}}</span>
          </div>

          <div class="card-body">
            <p class="cliente">{{p.clienteNombre}} {{p.clienteApellido}}</p>
            <p class="fecha">{{p.fechaPedido | date:'dd/MM/yyyy HH:mm'}}</p>

            <div class="especial" *ngIf="p.esPedidoEspecial">
              <span class="especial-badge">ESPECIAL · {{tipoLabel(p.tipoEspecial)}}</span>
              <span class="urgente inline-icon-text" *ngIf="esUrgente(p)"><app-icon name="warning" [size]="16"/> Entrega urgente (&lt;24h)</span>
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
    /* Inherits global dark theme from styles.scss */
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
