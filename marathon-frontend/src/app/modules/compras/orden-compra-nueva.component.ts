import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CrudService } from '../../core/services/crud.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';
import { SearchableSelectComponent } from '../../shared/components/searchable-select/searchable-select.component';

interface Proveedor { idProveedor: number; nombre: string; }
interface Producto { idProducto: number; nombre: string; }
interface MateriaPrima { idMateriaPrima: number; nombre: string; }

interface LineaNueva {
  tipoItem: 'producto' | 'materia_prima';
  idProducto: number | null;
  idMateriaPrima: number | null;
  itemNombre: string;
  cantidad: number;
  precioUnitario: number;
}

@Component({
  selector: 'app-orden-compra-nueva',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent, SearchableSelectComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Nueva Orden de Compra</h2>
        <button class="btn-cancel" routerLink="/compras">← Volver</button>
      </div>

      <div class="form-card">
        <div class="form-group">
          <label>Proveedor *</label>
          <app-searchable-select [(ngModel)]="idProveedor" name="idProveedor"
            [items]="proveedores" labelKey="nombre" valueKey="idProveedor"
            placeholder="Escriba el nombre del proveedor..."/>
        </div>

        <h3>Agregar línea</h3>
        <div class="form-row">
          <div class="form-group">
            <label>Tipo de línea</label>
            <div class="radio-group">
              <label><input type="radio" name="tipo" [value]="'producto'" [(ngModel)]="lineaTipo" (change)="onTipoChange()"/> Producto (reventa)</label>
              <label><input type="radio" name="tipo" [value]="'materia_prima'" [(ngModel)]="lineaTipo" (change)="onTipoChange()"/> Materia Prima (fabricación)</label>
            </div>
          </div>
        </div>

        <div class="form-row">
          <!-- F54: se ESCRIBE, no se despliega. Con cientos de productos, un
               <select> obliga a recorrer la lista entera para encontrar uno.
               app-searchable-select ya existia y lo usaba la pantalla de
               pedidos; aqui solo faltaba usarlo. -->
          <div class="form-group flex-2" *ngIf="lineaTipo === 'producto'">
            <label>Producto</label>
            <app-searchable-select [(ngModel)]="lineaIdProducto" name="lineaIdProducto"
              [items]="productos" labelKey="nombre" valueKey="idProducto"
              placeholder="Escriba el nombre o código del producto..."/>
          </div>
          <div class="form-group flex-2" *ngIf="lineaTipo === 'materia_prima'">
            <label>Materia Prima</label>
            <app-searchable-select [(ngModel)]="lineaIdMateria" name="lineaIdMateria"
              [items]="materiasPrimas" labelKey="nombre" valueKey="idMateriaPrima"
              placeholder="Escriba el nombre de la materia prima..."/>
          </div>
          <div class="form-group">
            <label>Cantidad</label>
            <input type="number" [(ngModel)]="lineaCantidad" name="lineaCantidad" min="1"/>
          </div>
          <div class="form-group">
            <label>Precio unitario</label>
            <input type="number" [(ngModel)]="lineaPrecio" name="lineaPrecio" step="0.01" min="0.01"/>
          </div>
          <div class="form-group" style="align-self:flex-end">
            <button type="button" class="btn-save" (click)="agregarLinea()">Agregar</button>
          </div>
        </div>
        <small class="error" *ngIf="lineaError">{{lineaError}}</small>

        <h3>Líneas de la orden</h3>
        <table class="data-table">
          <thead>
            <tr><th>Tipo</th><th>Ítem</th><th>Cantidad</th><th>P. Unit.</th><th>Subtotal</th><th></th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of lineas; let i = index">
              <td>{{l.tipoItem === 'producto' ? 'Producto' : 'Materia Prima'}}</td>
              <td>{{l.itemNombre}}</td>
              <td>{{l.cantidad}}</td>
              <td>$ {{l.precioUnitario | number:'1.2-2'}}</td>
              <td>$ {{(l.cantidad * l.precioUnitario) | number:'1.2-2'}}</td>
              <td><button class="btn-icon danger" (click)="eliminarLinea(i)" title="Quitar"><app-icon name="trash" [size]="16"/></button></td>
            </tr>
            <tr *ngIf="lineas.length === 0"><td colspan="6" class="empty">Sin líneas todavía</td></tr>
          </tbody>
        </table>

        <div class="total-preview">
          <span>Total estimado (lo confirma la BD):</span>
          <strong>$ {{totalEstimado() | number:'1.2-2'}}</strong>
        </div>

        <div class="form-group">
          <label>Observaciones</label>
          <textarea [(ngModel)]="observaciones" name="observaciones" rows="2"></textarea>
        </div>

        <small class="error" *ngIf="formError">{{formError}}</small>
        <div class="modal-actions">
          <button type="button" class="btn-cancel" routerLink="/compras">Cancelar</button>
          <button type="button" class="btn-save" [disabled]="saving" (click)="crear()">{{saving ? 'Creando...' : 'Crear orden (borrador)'}}</button>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .form-card { background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.05); border-radius: 12px; padding: 1.25rem; }
    .radio-group { display: flex; gap: 1.5rem; padding-top: .4rem; }
    .radio-group label { display: flex; align-items: center; gap: .4rem; font-weight: 400; }
    .total-preview { display: flex; justify-content: flex-end; gap: 1rem; align-items: center; padding: .75rem 0; font-size: 1.05rem; }
    .total-preview strong { color: #C9A84C; }
  `]
})
export class OrdenCompraNuevaComponent implements OnInit {
  proveedores: Proveedor[] = [];
  productos: Producto[] = [];
  materiasPrimas: MateriaPrima[] = [];

  idProveedor: number | null = null;
  observaciones = '';
  lineas: LineaNueva[] = [];

  lineaTipo: 'producto' | 'materia_prima' = 'producto';
  lineaIdProducto: number | null = null;
  lineaIdMateria: number | null = null;
  lineaCantidad: number | null = null;
  lineaPrecio: number | null = null;
  lineaError = '';

  formError = '';
  saving = false;
  toast = '';
  toastError = false;

  constructor(private crud: CrudService, private router: Router) {}

  ngOnInit() {
    this.crud.listar<Proveedor>('proveedores', { page: 0, size: 1000, estado: 'activo' }).subscribe({ next: r => this.proveedores = r.content });
    this.crud.listar<Producto>('productos', { page: 0, size: 1000, estado: 'activo' }).subscribe({ next: r => this.productos = r.content });
    this.crud.listar<MateriaPrima>('materia-prima', { page: 0, size: 1000, estado: 'activo' }).subscribe({ next: r => this.materiasPrimas = r.content });
  }

  onTipoChange() {
    this.lineaIdProducto = null;
    this.lineaIdMateria = null;
  }

  agregarLinea() {
    this.lineaError = '';
    if (!this.lineaCantidad || this.lineaCantidad < 1) { this.lineaError = 'La cantidad debe ser mayor a 0'; return; }
    if (!this.lineaPrecio || this.lineaPrecio <= 0) { this.lineaError = 'El precio unitario debe ser mayor a 0'; return; }

    if (this.lineaTipo === 'producto') {
      if (!this.lineaIdProducto) { this.lineaError = 'Seleccione un producto'; return; }
      const prod = this.productos.find(p => p.idProducto === this.lineaIdProducto);
      this.lineas.push({
        tipoItem: 'producto', idProducto: this.lineaIdProducto, idMateriaPrima: null,
        itemNombre: prod?.nombre || '', cantidad: this.lineaCantidad, precioUnitario: this.lineaPrecio
      });
    } else {
      if (!this.lineaIdMateria) { this.lineaError = 'Seleccione una materia prima'; return; }
      const mp = this.materiasPrimas.find(m => m.idMateriaPrima === this.lineaIdMateria);
      this.lineas.push({
        tipoItem: 'materia_prima', idProducto: null, idMateriaPrima: this.lineaIdMateria,
        itemNombre: mp?.nombre || '', cantidad: this.lineaCantidad, precioUnitario: this.lineaPrecio
      });
    }
    // limpiar
    this.lineaIdProducto = null;
    this.lineaIdMateria = null;
    this.lineaCantidad = null;
    this.lineaPrecio = null;
  }

  eliminarLinea(i: number) { this.lineas.splice(i, 1); }

  totalEstimado(): number {
    return this.lineas.reduce((acc, l) => acc + (l.cantidad * l.precioUnitario), 0);
  }

  crear() {
    this.formError = '';
    if (!this.idProveedor) { this.formError = 'Debe seleccionar un proveedor'; return; }
    if (this.lineas.length === 0) { this.formError = 'Debe agregar al menos una línea'; return; }

    const body = {
      idProveedor: this.idProveedor,
      observaciones: this.observaciones || null,
      detalles: this.lineas.map(l => ({
        tipoItem: l.tipoItem,
        idProducto: l.idProducto,
        idMateriaPrima: l.idMateriaPrima,
        cantidad: l.cantidad,
        precioUnitario: l.precioUnitario
      }))
    };

    this.saving = true;
    this.crud.crear<any>('ordenes-compra', body).subscribe({
      next: (oc) => { this.saving = false; this.router.navigate(['/compras', oc.idOrdenCompra]); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al crear la orden'; }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
