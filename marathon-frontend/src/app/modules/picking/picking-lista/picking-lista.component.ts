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

      <!-- F55: lista, no tarjetas. Con las tarjetas cabian tres pedidos por
           pantalla y comparar dos exigia desplazarse; en tabla entran diez y se
           leen en columna. Ordenada del MAS RECIENTE primero. -->
      <table class="data-table" *ngIf="!loading && pedidosFiltrados.length > 0">
        <thead>
          <tr>
            <th>Pedido</th><th>Cliente</th><th>Fecha</th>
            <th>Avance</th><th>Estado</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of pedidosFiltrados">
            <td class="nowrap">
              <strong>{{p.numeroPedido}}</strong>
              <span class="especial-badge" *ngIf="p.esPedidoEspecial">{{tipoLabel(p.tipoEspecial)}}</span>
              <span class="urgente inline-icon-text" *ngIf="esUrgente(p)"><app-icon name="warning" [size]="14"/> &lt;24 h</span>
            </td>
            <td>{{p.clienteNombre}} {{p.clienteApellido}}</td>
            <td class="nowrap">{{p.fechaPedido | date:'dd/MM/yyyy HH:mm'}}</td>
            <td class="nowrap avance">
              <div class="progress-bar"><div class="progress-fill" [style.width.%]="porcentaje(p)"></div></div>
              <span>{{p.lineasCompletadas}}/{{p.totalLineas}}</span>
            </td>
            <td><span class="estado-badge" [ngClass]="'ep-'+p.estadoPicking">{{estadoLabel(p.estadoPicking)}}</span></td>
            <td class="actions">
              <button class="btn-picking" (click)="ejecutar(p.idPedido)">Ejecutar picking</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="empty" *ngIf="!loading && pedidosFiltrados.length === 0">No hay pedidos para picking</div>

      <div class="pagination" *ngIf="totalPages > 1">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>
    </div>
  `,
  styles: [`
    /* Hereda el tema global de styles.scss.
       Lo de aquí es solo lo que la tabla necesita y las tarjetas traían: la
       barra de avance dentro de su celda, y las dos etiquetas del pedido. */
    .avance { display: flex; align-items: center; gap: .5rem; min-width: 130px; }
    .avance .progress-bar {
      flex: 1; height: 6px; min-width: 70px; border-radius: 3px;
      background: rgba(255,255,255,.08); overflow: hidden;
    }
    .avance .progress-fill { height: 100%; background: #C9A84C; transition: width .2s; }
    .avance span { font-size: .78rem; color: rgba(255,255,255,.6); }

    .especial-badge {
      margin-left: .45rem; padding: .1rem .45rem; border-radius: 10px;
      font-size: .65rem; letter-spacing: .4px; text-transform: uppercase;
      background: rgba(201,168,76,.16); color: #C9A84C;
    }
    /* El aviso de urgencia va en rojo y en su propia línea: es lo único de la
       fila que cambia lo que el operario hace ahora mismo. */
    .urgente { display: block; margin-top: .2rem; font-size: .7rem; color: #e5736f; }
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
