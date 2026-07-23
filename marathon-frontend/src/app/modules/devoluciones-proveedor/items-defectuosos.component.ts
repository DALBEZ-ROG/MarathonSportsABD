import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../core/services/api.service';

interface Item { origen: string; idOrigenDetalle: number; idProducto: number; nombreProducto: string; cantidad: number; idProveedorSugerido: number; nombreProveedorSugerido: string; fechaOrigen: string; referenciaOrigen: string; selected: boolean; }

@Component({
  selector: 'app-items-defectuosos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Items defectuosos pendientes</h2>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead><tr><th></th><th>Origen</th><th>Producto</th><th>Cant.</th><th>Proveedor sugerido</th><th>Referencia</th><th>Fecha</th></tr></thead>
        <tbody>
          <tr *ngFor="let i of items">
            <td><input type="checkbox" [(ngModel)]="i.selected"></td>
            <td><span class="orig-badge" [ngClass]="'orig-' + i.origen">{{i.origen === 'rma_cliente' ? 'RMA Cliente' : 'Recepcion'}}</span></td>
            <td>{{i.nombreProducto}}</td>
            <td>{{i.cantidad}}</td>
            <td>{{i.nombreProveedorSugerido || '-'}}</td>
            <td>{{i.referenciaOrigen}}</td>
            <td>{{i.fechaOrigen | date:'dd/MM/yyyy'}}</td>
          </tr>
          <tr *ngIf="items.length === 0"><td colspan="7" class="empty">No hay items defectuosos pendientes</td></tr>
        </tbody>
      </table>

      <div class="acciones" *ngIf="items.length > 0">
        <button class="btn-save" (click)="crearDevolucion()" [disabled]="!haySeleccionados()">Crear devolucion con seleccionados</button>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .orig-badge { padding: .2rem .5rem; border-radius: 8px; font-size: .7rem; font-weight: 600; color: #fff; }
    .orig-rma_cliente { background: #d97706; }
    .orig-recepcion_compra { background: #2563eb; }
    .acciones { margin-top: 1rem; }
  `]
})
export class ItemsDefectuososComponent implements OnInit {
  items: Item[] = [];
  loading = false;
  toast = '';
  toastError = false;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    this.api.get<any[]>('devoluciones-proveedor/items-disponibles').subscribe({
      next: (res: any[]) => { this.items = res.map(i => ({ ...i, selected: false })); this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  haySeleccionados(): boolean { return this.items.some(i => i.selected); }

  crearDevolucion() {
    const seleccionados = this.items.filter(i => i.selected);
    if (seleccionados.length === 0) return;

    // Validar mismo proveedor
    const proveedores = new Set(seleccionados.map(i => i.idProveedorSugerido));
    if (proveedores.size > 1) {
      this.mostrarToast('Todos los items seleccionados deben ser del mismo proveedor', true);
      return;
    }

    const idProveedor = seleccionados[0].idProveedorSugerido;
    if (!idProveedor) {
      this.mostrarToast('No se pudo determinar el proveedor', true);
      return;
    }

    const body = {
      idProveedor: idProveedor,
      observaciones: null,
      items: seleccionados.map(i => ({
        origen: i.origen,
        idOrigenDetalle: i.idOrigenDetalle,
        cantidad: i.cantidad,
        motivo: null
      }))
    };

    this.api.post<any>('devoluciones-proveedor', body).subscribe({
      next: (res: any) => {
        this.mostrarToast('Devolucion #' + res.idDevolucionProv + ' creada');
        setTimeout(() => this.router.navigate(['/devoluciones-proveedor', res.idDevolucionProv]), 1500);
      },
      error: (err: any) => { this.mostrarToast(err.error?.message || 'Error al crear', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
