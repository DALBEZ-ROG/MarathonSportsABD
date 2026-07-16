import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService, PageResponse } from '../../core/services/crud.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface Categoria {
  idCategoria: number;
  nombre: string;
}

interface UnidadMedida {
  idUnidad: number;
  nombre: string;
  abreviatura: string;
}

interface ProveedorSimple {
  idProveedor: number;
  nombre: string;
  ruc: string;
}

interface Producto {
  idProducto: number;
  codigo: string;
  nombre: string;
  descripcion: string;
  precioCompra: number;
  precioVenta: number;
  idCategoria: number;
  categoriaNombre: string;
  idUnidadMedida: number;
  unidadMedidaNombre: string;
  stockMinimo: number;
  estado: string;
  proveedores: ProveedorSimple[];
}

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Productos</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="filtroNombre" (input)="onSearch()" placeholder="Buscar por nombre..." class="input-search"/>
          <select [(ngModel)]="filtroEstado" (change)="cargar()" class="select-filter">
            <option value="">Todos</option>
            <option value="activo">Activo</option>
            <option value="inactivo">Inactivo</option>
          </select>
          <select [(ngModel)]="filtroCategoria" (change)="cargar()" class="select-filter">
            <option value="">Todas las categorías</option>
            <option *ngFor="let c of categorias" [value]="c.idCategoria">{{c.nombre}}</option>
          </select>
        </div>
        <button class="btn-new" (click)="abrirModal()">+ Nuevo</button>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>Código</th><th>Nombre</th><th>P. Compra</th><th>P. Venta</th><th>Categoría</th><th>Unidad</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.codigo}}</td>
            <td>{{item.nombre}}</td>
            <td>$ {{item.precioCompra | number:'1.2-2'}}</td>
            <td>$ {{item.precioVenta | number:'1.2-2'}}</td>
            <td>{{item.categoriaNombre}}</td>
            <td>{{item.unidadMedidaNombre}}</td>
            <td><span class="badge" [class.active]="item.estado==='activo'">{{item.estado}}</span></td>
            <td class="actions">
              <button class="btn-icon" (click)="editar(item)" title="Editar">✏️</button>
              <button class="btn-icon danger" (click)="confirmarEliminar(item)" title="Eliminar">🗑️</button>
            </td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="8" class="empty">No hay registros</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <!-- Modal -->
      <div class="modal-overlay" *ngIf="showModal" (click)="cerrarModal()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>{{editando ? 'Editar' : 'Nuevo'}} Producto</h3>
          <form (ngSubmit)="guardar()">
            <div class="form-row">
              <div class="form-group">
                <label>Código *</label>
                <input type="text" [(ngModel)]="form.codigo" name="codigo" required/>
              </div>
              <div class="form-group">
                <label>Nombre *</label>
                <input type="text" [(ngModel)]="form.nombre" name="nombre" required/>
              </div>
            </div>
            <div class="form-group">
              <label>Descripción</label>
              <input type="text" [(ngModel)]="form.descripcion" name="descripcion"/>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>Precio Compra *</label>
                <input type="number" [(ngModel)]="form.precioCompra" name="precioCompra" step="0.01" required/>
              </div>
              <div class="form-group">
                <label>Precio Venta *</label>
                <input type="number" [(ngModel)]="form.precioVenta" name="precioVenta" step="0.01" required/>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>Categoría *</label>
                <select [(ngModel)]="form.idCategoria" name="idCategoria" required>
                  <option [ngValue]="null">-- Seleccione --</option>
                  <option *ngFor="let c of categorias" [ngValue]="c.idCategoria">{{c.nombre}}</option>
                </select>
              </div>
              <div class="form-group">
                <label>Unidad Medida *</label>
                <select [(ngModel)]="form.idUnidadMedida" name="idUnidadMedida" required>
                  <option [ngValue]="null">-- Seleccione --</option>
                  <option *ngFor="let u of unidades" [ngValue]="u.idUnidad">{{u.nombre}} ({{u.abreviatura}})</option>
                </select>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>Stock Mínimo</label>
                <input type="number" [(ngModel)]="form.stockMinimo" name="stockMinimo"/>
              </div>
              <div class="form-group" *ngIf="editando">
                <label>Estado</label>
                <select [(ngModel)]="form.estado" name="estado">
                  <option value="activo">Activo</option>
                  <option value="inactivo">Inactivo</option>
                </select>
              </div>
            </div>
            <div class="form-group">
              <label>Proveedores</label>
              <div class="proveedor-list">
                <label *ngFor="let p of proveedores" class="checkbox-item">
                  <input type="checkbox" [checked]="form.proveedorIds.includes(p.idProveedor)" (change)="toggleProveedor(p.idProveedor)"/>
                  {{p.nombre}} <small *ngIf="p.ruc">({{p.ruc}})</small>
                </label>
                <span *ngIf="proveedores.length===0" class="no-items">No hay proveedores disponibles</span>
              </div>
            </div>
            <small class="error" *ngIf="formError">{{formError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="cerrarModal()">Cancelar</button>
              <button type="submit" class="btn-save" [disabled]="saving">{{saving ? 'Guardando...' : 'Guardar'}}</button>
            </div>
          </form>
        </div>
      </div>

      <!-- Confirm Delete -->
      <div class="modal-overlay" *ngIf="showConfirm" (click)="showConfirm=false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Confirmar eliminación</h3>
          <p>¿Estás seguro de que deseas eliminar <strong>{{itemEliminar?.nombre}}</strong>?</p>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="showConfirm=false">Cancelar</button>
            <button class="btn-delete" (click)="eliminar()">Confirmar eliminación</button>
          </div>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class ProductosComponent implements OnInit {
  data: Producto[] = [];
  categorias: Categoria[] = [];
  unidades: UnidadMedida[] = [];
  proveedores: ProveedorSimple[] = [];
  loading = false;
  saving = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroNombre = '';
  filtroEstado = '';
  filtroCategoria: string = '';
  showModal = false;
  showConfirm = false;
  editando = false;
  editId: number | null = null;
  form: any = {
    codigo: '', nombre: '', descripcion: '', precioCompra: null, precioVenta: null,
    idCategoria: null, idUnidadMedida: null, stockMinimo: 0, estado: 'activo', proveedorIds: [] as number[]
  };
  formError = '';
  itemEliminar: Producto | null = null;
  toast = '';
  toastError = false;
  private searchTimeout: any;

  constructor(private crud: CrudService, private http: HttpClient) {}

  ngOnInit() {
    this.cargar();
    this.cargarCatalogos();
  }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroNombre) params['nombre'] = this.filtroNombre;
    if (this.filtroEstado) params['estado'] = this.filtroEstado;
    if (this.filtroCategoria) params['idCategoria'] = this.filtroCategoria;

    this.crud.listar<Producto>('productos', params).subscribe({
      next: res => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; this.mostrarToast('Error al cargar datos', true); }
    });
  }

  cargarCatalogos() {
    this.crud.listar<Categoria>('categorias', { page: 0, size: 1000 }).subscribe({
      next: res => { this.categorias = res.content; }
    });
    this.crud.listar<UnidadMedida>('unidades-medida', { page: 0, size: 1000 }).subscribe({
      next: res => { this.unidades = res.content; }
    });
    this.crud.listar<any>('proveedores', { page: 0, size: 1000, estado: 'activo' }).subscribe({
      next: res => { this.proveedores = res.content; }
    });
  }

  onSearch() {
    clearTimeout(this.searchTimeout);
    this.searchTimeout = setTimeout(() => { this.page = 0; this.cargar(); }, 300);
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  abrirModal() {
    this.editando = false;
    this.editId = null;
    this.form = {
      codigo: '', nombre: '', descripcion: '', precioCompra: null, precioVenta: null,
      idCategoria: null, idUnidadMedida: null, stockMinimo: 0, estado: 'activo', proveedorIds: []
    };
    this.formError = '';
    this.showModal = true;
  }

  editar(item: Producto) {
    this.editando = true;
    this.editId = item.idProducto;
    this.formError = '';
    // Fetch full product details with proveedores
    this.crud.obtener<Producto>('productos', item.idProducto).subscribe({
      next: (prod) => {
        this.form = {
          codigo: prod.codigo, nombre: prod.nombre, descripcion: prod.descripcion || '',
          precioCompra: prod.precioCompra, precioVenta: prod.precioVenta,
          idCategoria: prod.idCategoria, idUnidadMedida: prod.idUnidadMedida,
          stockMinimo: prod.stockMinimo || 0, estado: prod.estado,
          proveedorIds: prod.proveedores ? prod.proveedores.map(p => p.idProveedor) : []
        };
        this.showModal = true;
      },
      error: () => { this.mostrarToast('Error al cargar producto', true); }
    });
  }

  cerrarModal() { this.showModal = false; }

  toggleProveedor(id: number) {
    const idx = this.form.proveedorIds.indexOf(id);
    if (idx > -1) {
      this.form.proveedorIds.splice(idx, 1);
    } else {
      this.form.proveedorIds.push(id);
    }
  }

  guardar() {
    if (!this.form.codigo?.trim()) { this.formError = 'El código es obligatorio'; return; }
    if (!this.form.nombre?.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    if (!this.form.precioCompra) { this.formError = 'El precio de compra es obligatorio'; return; }
    if (!this.form.precioVenta) { this.formError = 'El precio de venta es obligatorio'; return; }
    if (!this.form.idCategoria) { this.formError = 'La categoría es obligatoria'; return; }
    if (!this.form.idUnidadMedida) { this.formError = 'La unidad de medida es obligatoria'; return; }
    this.saving = true;

    const obs = this.editando
      ? this.crud.actualizar<Producto>('productos', this.editId!, this.form)
      : this.crud.crear<Producto>('productos', this.form);

    obs.subscribe({
      next: () => { this.saving = false; this.cerrarModal(); this.cargar(); this.mostrarToast('Producto guardado correctamente'); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al guardar'; }
    });
  }

  confirmarEliminar(item: Producto) { this.itemEliminar = item; this.showConfirm = true; }

  eliminar() {
    if (!this.itemEliminar) return;
    this.crud.eliminar('productos', this.itemEliminar.idProducto).subscribe({
      next: () => { this.showConfirm = false; this.cargar(); this.mostrarToast('Producto eliminado'); },
      error: (err) => { this.showConfirm = false; this.mostrarToast(err.error?.message || 'Error al eliminar', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
