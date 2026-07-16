import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { CrudService } from '../../../core/services/crud.service';

interface Cliente {
  idCliente: number;
  nombre: string;
  apellido: string;
  cedula: string;
}

interface Producto {
  idProducto: number;
  nombre: string;
  codigo: string;
  precioVenta: number;
}

interface DetalleLinea {
  idProducto: number;
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

@Component({
  selector: 'app-pedido-nuevo',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container">
      <h2>Nuevo Pedido</h2>

      <div class="form-section">
        <h3>Datos del Pedido</h3>
        <div class="form-row">
          <div class="form-group">
            <label>Cliente *</label>
            <select [(ngModel)]="idCliente" name="idCliente" class="full-width">
              <option [ngValue]="null">-- Seleccione un cliente --</option>
              <option *ngFor="let c of clientes" [ngValue]="c.idCliente">{{c.nombre}} {{c.apellido}} ({{c.cedula}})</option>
            </select>
          </div>
          <div class="form-group">
            <label>Observaciones</label>
            <input type="text" [(ngModel)]="observaciones" name="observaciones" placeholder="Opcional..." class="full-width"/>
          </div>
        </div>
      </div>

      <div class="form-section">
        <h3>Pedido Especial</h3>
        <label class="toggle-row">
          <input type="checkbox" [(ngModel)]="esPedidoEspecial" name="esPedidoEspecial" (change)="onToggleEspecial()"/>
          <span>¿Es un pedido especial?</span>
        </label>

        <div class="especial-fields" *ngIf="esPedidoEspecial">
          <div class="form-row">
            <div class="form-group">
              <label>Tipo de pedido especial *</label>
              <select [(ngModel)]="tipoEspecial" name="tipoEspecial" class="full-width">
                <option value="">-- Seleccione --</option>
                <option value="personalizado">Personalizado</option>
                <option value="regalo">Regalo</option>
                <option value="corporativo">Corporativo</option>
              </select>
            </div>
            <div class="form-group">
              <label>Fecha límite de entrega</label>
              <input type="datetime-local" [(ngModel)]="fechaLimiteEntrega" name="fechaLimiteEntrega" class="full-width"/>
            </div>
          </div>
          <div class="form-group">
            <label>Nota especial</label>
            <textarea [(ngModel)]="notaEspecial" name="notaEspecial" rows="3" placeholder="Detalles del pedido especial..." class="full-width"></textarea>
          </div>
        </div>
      </div>

      <div class="form-section">
        <h3>Agregar Productos</h3>
        <div class="product-search">
          <div class="form-group flex-2">
            <label>Producto</label>
            <select [(ngModel)]="selectedProducto" name="producto">
              <option [ngValue]="null">-- Seleccione --</option>
              <option *ngFor="let p of productos" [ngValue]="p">{{p.codigo}} - {{p.nombre}} (\${{p.precioVenta}})</option>
            </select>
          </div>
          <div class="form-group flex-1">
            <label>Cantidad</label>
            <input type="number" [(ngModel)]="cantidadAgregar" name="cantidad" min="1"/>
          </div>
          <div class="form-group flex-1">
            <label>Precio Unit.</label>
            <input type="number" [(ngModel)]="precioAgregar" name="precio" step="0.01" min="0.01"/>
          </div>
          <div class="form-group btn-group">
            <label>&nbsp;</label>
            <button type="button" class="btn-add" (click)="agregarLinea()">+ Agregar</button>
          </div>
        </div>
      </div>

      <div class="form-section" *ngIf="detalles.length > 0">
        <h3>Detalle del Pedido</h3>
        <table class="detail-table">
          <thead>
            <tr><th>Producto</th><th>Cantidad</th><th>P. Unitario</th><th>Subtotal</th><th></th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let d of detalles; let i = index">
              <td>{{d.productoNombre}}</td>
              <td>{{d.cantidad}}</td>
              <td>\${{d.precioUnitario | number:'1.2-2'}}</td>
              <td class="total">\${{d.subtotal | number:'1.2-2'}}</td>
              <td><button class="btn-remove" (click)="quitarLinea(i)">✕</button></td>
            </tr>
          </tbody>
          <tfoot>
            <tr><td colspan="3" class="total-label">TOTAL ESTIMADO:</td><td class="total-value">\${{totalPreview | number:'1.2-2'}}</td><td></td></tr>
          </tfoot>
        </table>
      </div>

      <small class="error" *ngIf="formError">{{formError}}</small>

      <div class="form-actions">
        <button type="button" class="btn-cancel" (click)="cancelar()">Cancelar</button>
        <button type="button" class="btn-save" (click)="crearPedido()" [disabled]="saving">
          {{saving ? 'Creando pedido...' : 'Crear Pedido'}}
        </button>
      </div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class PedidoNuevoComponent implements OnInit {
  clientes: Cliente[] = [];
  productos: Producto[] = [];
  detalles: DetalleLinea[] = [];
  idCliente: number | null = null;
  observaciones = '';
  selectedProducto: Producto | null = null;
  cantidadAgregar = 1;
  precioAgregar = 0;
  formError = '';
  saving = false;

  esPedidoEspecial = false;
  tipoEspecial = '';
  notaEspecial = '';
  fechaLimiteEntrega = '';

  constructor(private http: HttpClient, private router: Router, private crud: CrudService) {}

  ngOnInit() {
    this.cargarClientes();
    this.cargarProductos();
  }

  cargarClientes() {
    this.http.get<Cliente[]>(`${environment.apiUrl}/clientes/activos`).subscribe({
      next: res => { this.clientes = res; }
    });
  }

  cargarProductos() {
    this.crud.listar<Producto>('productos', { page: 0, size: 500, estado: 'activo' }).subscribe({
      next: res => { this.productos = res.content; }
    });
  }

  get totalPreview(): number {
    return this.detalles.reduce((sum, d) => sum + d.subtotal, 0);
  }

  agregarLinea() {
    if (!this.selectedProducto) { this.formError = 'Seleccione un producto'; return; }
    if (this.cantidadAgregar < 1) { this.formError = 'La cantidad debe ser al menos 1'; return; }
    const precio = this.precioAgregar > 0 ? this.precioAgregar : this.selectedProducto.precioVenta;
    if (precio <= 0) { this.formError = 'El precio debe ser mayor a 0'; return; }

    this.formError = '';
    this.detalles.push({
      idProducto: this.selectedProducto.idProducto,
      productoNombre: this.selectedProducto.nombre,
      cantidad: this.cantidadAgregar,
      precioUnitario: precio,
      subtotal: this.cantidadAgregar * precio
    });

    this.selectedProducto = null;
    this.cantidadAgregar = 1;
    this.precioAgregar = 0;
  }

  quitarLinea(index: number) {
    this.detalles.splice(index, 1);
  }

  onToggleEspecial() {
    if (!this.esPedidoEspecial) {
      this.tipoEspecial = '';
      this.notaEspecial = '';
      this.fechaLimiteEntrega = '';
    }
  }

  crearPedido() {
    if (!this.idCliente) { this.formError = 'Seleccione un cliente'; return; }
    if (this.detalles.length === 0) { this.formError = 'Agregue al menos un producto'; return; }
    if (this.esPedidoEspecial && !this.tipoEspecial) { this.formError = 'Seleccione el tipo de pedido especial'; return; }
    this.formError = '';
    this.saving = true;

    const body = {
      idCliente: this.idCliente,
      observaciones: this.observaciones || null,
      esPedidoEspecial: this.esPedidoEspecial,
      tipoEspecial: this.esPedidoEspecial ? this.tipoEspecial : null,
      notaEspecial: this.esPedidoEspecial ? (this.notaEspecial || null) : null,
      fechaLimiteEntrega: this.esPedidoEspecial && this.fechaLimiteEntrega ? this.fechaLimiteEntrega : null,
      detalles: this.detalles.map(d => ({
        idProducto: d.idProducto,
        cantidad: d.cantidad,
        precioUnitario: d.precioUnitario
      }))
    };

    this.http.post(`${environment.apiUrl}/pedidos`, body).subscribe({
      next: () => { this.router.navigate(['/pedidos']); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al crear el pedido'; }
    });
  }

  cancelar() {
    this.router.navigate(['/pedidos']);
  }
}
