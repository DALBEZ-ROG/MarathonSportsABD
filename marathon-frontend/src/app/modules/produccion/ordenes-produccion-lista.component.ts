import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

@Component({
  selector: 'app-ordenes-produccion-lista',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Órdenes de Producción</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="busqueda" (input)="onBuscar()"
                 placeholder="Buscar por N.° de orden o producto..." class="input-search"/>
          <select [(ngModel)]="filtroEstado" (change)="onFiltro()" class="select-filter">
            <option value="">Todos los estados</option>
            <option value="planificada">Planificada</option>
            <option value="en_proceso">En proceso</option>
            <option value="completada">Completada</option>
            <option value="cancelada">Cancelada</option>
          </select>
          <select [(ngModel)]="filtroProducto" (change)="onFiltro()" class="select-filter">
            <option [ngValue]="''">Todos los productos</option>
            <option *ngFor="let p of productos" [ngValue]="p.idProducto">{{p.nombre}}</option>
          </select>
        </div>
        <button class="btn-cancel" routerLink="/produccion/costos">Análisis de costos</button>
        <button class="btn-new" routerLink="/produccion/nueva">+ Nueva orden de producción</button>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>#</th><th>Producto</th><th>Planificada</th><th>Producida</th><th>Estado</th><th>Bodega destino</th><th>Fecha</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let o of data">
            <td>{{o.idOrdenProduccion}}</td>
            <td>{{o.productoNombre}}</td>
            <td>{{o.cantidadPlanificada}}</td>
            <td>{{o.cantidadProducida != null ? o.cantidadProducida : '-'}}</td>
            <td><span class="op-badge" [ngClass]="'op-' + o.estado">{{o.estado}}</span></td>
            <td>{{o.bodegaNombre}}</td>
            <td>{{o.fechaCreacion | date:'dd/MM/yyyy HH:mm'}}</td>
            <td><button class="btn-icon" [routerLink]="['/produccion', o.idOrdenProduccion]" title="Ver"><app-icon name="search" [size]="16"/></button></td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="8" class="empty">No hay órdenes de producción</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>
    </div>
  `,
  styles: [`
    .op-badge { padding: .25rem .6rem; border-radius: 12px; font-size: .72rem; font-weight: 600; text-transform: uppercase; color: #fff; }
    .op-planificada { background: #6b7280; }
    .op-en_proceso { background: #d97706; }
    .op-completada { background: #16a34a; }
    .op-cancelada { background: #dc2626; }
  `]
})
export class OrdenesProduccionListaComponent implements OnInit {
  data: any[] = [];
  productos: any[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroEstado = '';
  filtroProducto: any = '';

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.cargar();
    this.api.get<any>('productos?origen=fabricado&size=1000').subscribe({
      next: (res: any) => { this.productos = res.content || []; },
      error: () => {}
    });
  }

  /** F54: buscador por texto. Se espera 300 ms para no lanzar una consulta por tecla. */
  busqueda = '';
  private buscarTimeout: any;

  onBuscar() {
    clearTimeout(this.buscarTimeout);
    this.buscarTimeout = setTimeout(() => { this.page = 0; this.cargar(); }, 300);
  }
  cargar() {
    this.loading = true;
    let url = 'ordenes-produccion?page=' + this.page + '&size=' + this.size;
    if (this.filtroEstado) url += '&estado=' + this.filtroEstado;
    if (this.busqueda) url += '&busqueda=' + encodeURIComponent(this.busqueda);
    if (this.filtroProducto) url += '&idProducto=' + this.filtroProducto;
    this.api.get<any>(url).subscribe({
      next: (res: any) => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  onFiltro() { this.page = 0; this.cargar(); }
  cambiarPagina(p: number) { this.page = p; this.cargar(); }
}
