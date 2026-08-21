import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CrudService } from '../../core/services/crud.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

interface ProveedorSimple { idProveedor: number; nombre: string; }

interface OrdenCompra {
  idOrdenCompra: number;
  fechaOrden: string;
  estado: string;
  total: number;
  proveedor: ProveedorSimple;
}

@Component({
  selector: 'app-ordenes-compra',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Órdenes de Compra</h2>
        <div class="filters">
          <select [(ngModel)]="filtroEstado" (change)="onFiltro()" class="select-filter">
            <option value="">Todos los estados</option>
            <option value="borrador">Borrador</option>
            <option value="pendiente_aprobacion">Pendiente aprobación</option>
            <option value="aprobada">Aprobada</option>
            <option value="rechazada">Rechazada</option>
            <option value="recibida_parcial">Recibida parcial</option>
            <option value="recibida_completa">Recibida completa</option>
            <option value="cancelada">Cancelada</option>
          </select>
          <select [(ngModel)]="filtroProveedor" (change)="onFiltro()" class="select-filter">
            <option value="">Todos los proveedores</option>
            <option *ngFor="let p of proveedores" [value]="p.idProveedor">{{p.nombre}}</option>
          </select>
        </div>
        <button class="btn-new" routerLink="/compras/nueva">+ Nueva orden de compra</button>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>#</th><th>Proveedor</th><th>Fecha</th><th>Total</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let oc of data">
            <td>{{oc.idOrdenCompra}}</td>
            <td>{{oc.proveedor?.nombre}}</td>
            <td>{{oc.fechaOrden | date:'dd/MM/yyyy HH:mm'}}</td>
            <td>$ {{oc.total | number:'1.2-2'}}</td>
            <td><span class="oc-badge" [ngClass]="'oc-' + oc.estado">{{etiqueta(oc.estado)}}</span></td>
            <td class="actions">
              <button class="btn-icon" [routerLink]="['/compras', oc.idOrdenCompra]" title="Ver detalle"><app-icon name="eye" [size]="16"/></button>
            </td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="6" class="empty">No hay órdenes de compra</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class OrdenesCompraComponent implements OnInit {
  data: OrdenCompra[] = [];
  proveedores: ProveedorSimple[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroEstado = '';
  filtroProveedor = '';
  toast = '';
  toastError = false;

  private etiquetas: Record<string, string> = {
    borrador: 'Borrador',
    pendiente_aprobacion: 'Pendiente aprob.',
    aprobada: 'Aprobada',
    rechazada: 'Rechazada',
    recibida_parcial: 'Recibida parcial',
    recibida_completa: 'Recibida completa',
    cancelada: 'Cancelada'
  };

  constructor(private crud: CrudService) {}

  ngOnInit() {
    this.cargar();
    this.crud.listar<ProveedorSimple>('proveedores', { page: 0, size: 1000, estado: 'activo' }).subscribe({
      next: res => { this.proveedores = res.content; }
    });
  }

  etiqueta(estado: string): string { return this.etiquetas[estado] || estado; }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroEstado) params['estado'] = this.filtroEstado;
    if (this.filtroProveedor) params['idProveedor'] = this.filtroProveedor;

    this.crud.listar<OrdenCompra>('ordenes-compra', params).subscribe({
      next: res => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; this.mostrarToast('Error al cargar órdenes de compra', true); }
    });
  }

  onFiltro() { this.page = 0; this.cargar(); }
  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
