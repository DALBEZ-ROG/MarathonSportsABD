import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService, PageResponse } from '../../core/services/crud.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

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

interface MateriaPrima {
  idMateriaPrima: number;
  nombre: string;
  unidadMedidaNombre?: string;
  unidadMedida?: string;
}

interface BomLinea {
  idMateriaPrima: number | null;
  cantidadNecesaria: number | null;
}

interface BomItemResponse {
  idBom: number;
  cantidadNecesaria: number;
  estado: string;
  materiaPrima: { idMateriaPrima: number; nombre: string; unidadMedida: string };
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
  origen: string;
  tieneBom?: boolean;
  proveedores: ProveedorSimple[];
}

@Component({
  selector: 'app-productos',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent],
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
          <select [(ngModel)]="filtroOrigen" (change)="cargar()" class="select-filter">
            <option value="">Todo origen</option>
            <option value="comprado">Comprado</option>
            <option value="fabricado">Fabricado</option>
          </select>
        </div>
        <button class="btn-new" (click)="abrirModal()">+ Nuevo</button>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>Código</th><th>Nombre</th><th>P. Compra</th><th>P. Venta</th><th>Categoría</th><th>Unidad</th><th>Origen</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.codigo}}</td>
            <td>{{item.nombre}}</td>
            <td>$ {{item.precioCompra | number:'1.2-2'}}</td>
            <td>$ {{item.precioVenta | number:'1.2-2'}}</td>
            <td>{{item.categoriaNombre}}</td>
            <td>{{item.unidadMedidaNombre}}</td>
            <td>
              <span class="badge-origen" [class.fabricado]="item.origen==='fabricado'" [class.comprado]="item.origen==='comprado'">
                {{item.origen==='fabricado' ? 'Fabricado' : 'Comprado'}}
              </span>
            </td>
            <td><span class="badge" [class.active]="item.estado==='activo'">{{item.estado}}</span></td>
            <td class="actions">
              <button class="btn-icon" (click)="editar(item)" title="Editar"><app-icon name="edit" [size]="16"/></button>
              <button class="btn-icon danger" (click)="confirmarEliminar(item)" title="Eliminar"><app-icon name="trash" [size]="16"/></button>
            </td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="9" class="empty">No hay registros</td></tr>
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
            <div class="form-row">
              <div class="form-group">
                <label>Origen *</label>
                <select [(ngModel)]="form.origen" name="origen" (change)="onOrigenChange()">
                  <option value="comprado">Comprado (a una marca)</option>
                  <option value="fabricado">Fabricado (producción interna)</option>
                </select>
              </div>
            </div>

            <!-- Lista de Materiales (BOM) — solo para productos fabricados -->
            <div class="bom-section" *ngIf="form.origen==='fabricado'">
              <h4>Lista de Materiales (BOM)</h4>
              <p class="bom-hint">Debe definir al menos 1 material antes de guardar. Cantidad necesaria para producir 1 unidad.</p>
              <div class="bom-linea" *ngFor="let linea of bomLineas; let i = index">
                <select [(ngModel)]="linea.idMateriaPrima" [name]="'mp'+i" class="bom-mp">
                  <option [ngValue]="null">-- Materia prima --</option>
                  <option *ngFor="let m of materiasPrimas" [ngValue]="m.idMateriaPrima">{{m.nombre}}</option>
                </select>
                <input type="number" [(ngModel)]="linea.cantidadNecesaria" [name]="'cant'+i" step="0.001" min="0.001"
                       class="bom-cant" placeholder="Cantidad"/>
                <span class="bom-unidad">{{unidadDeMateria(linea.idMateriaPrima)}}</span>
                <button type="button" class="btn-icon danger" (click)="quitarLineaBom(i)" title="Eliminar"><app-icon name="trash" [size]="16"/></button>
              </div>
              <button type="button" class="btn-add-mat" (click)="agregarLineaBom()">+ Agregar material</button>
            </div>

            <!-- Costo estimado de producción (F29) -->
            <div class="costo-est" *ngIf="form.origen==='fabricado' && costoEst">
              <h4>Costo estimado de producción</h4>
              <table class="mini-table">
                <thead><tr><th>Material</th><th>Cantidad</th><th>Costo unit.</th><th>Costo línea</th></tr></thead>
                <tbody>
                  <tr *ngFor="let m of costoEst.materiales">
                    <td>{{m.nombre}}</td>
                    <td>{{m.cantidadNecesaria | number:'1.0-3'}}</td>
                    <td>$ {{m.costoUnitarioPromedio | number:'1.4-4'}}</td>
                    <td>$ {{m.costoLinea | number:'1.2-4'}}</td>
                  </tr>
                </tbody>
              </table>
              <div class="costo-est-row"><span>Costo materia prima unitario</span><span>$ {{costoEst.costoMateriaPrimaUnitario | number:'1.2-4'}}</span></div>
              <div class="costo-est-row"><span>Precio de venta</span><span>$ {{costoEst.precioVenta | number:'1.2-2'}}</span></div>
              <div class="costo-est-row"><span>Margen bruto</span><span [ngClass]="{ 'margen-pos': costoEst.margenBruto >= 0, 'margen-neg': costoEst.margenBruto < 0 }">$ {{costoEst.margenBruto | number:'1.2-2'}} ({{costoEst.margenPorcentaje | number:'1.1-1'}}%)</span></div>
              <p class="costo-adv" *ngIf="costoEst.advertencia">{{costoEst.advertencia}}</p>
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
    .bom-section, .costo-est {
      border: 1px solid var(--ms-border);
      border-radius: var(--ms-radius-sm);
      padding: 12px;
      margin: 10px 0;
      background: rgba(255,255,255,0.02);
    }
    .bom-section h4, .costo-est h4 { margin: 0 0 8px; color: rgba(255,255,255,0.8); font-weight: 400; }
    .bom-hint { font-size: 0.8rem; color: var(--ms-text-muted); margin: 0 0 10px; }
    .bom-linea { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
    .bom-mp { flex: 2; }
    .bom-cant { flex: 1; }
    .bom-unidad { min-width: 60px; font-size: 0.8rem; color: var(--ms-text-muted); }
    .btn-add-mat {
      background: var(--ms-gold-dim); color: var(--ms-gold);
      border: 1px solid rgba(201,168,76,0.3); padding: 6px 12px; border-radius: 6px; cursor: pointer;
    }
    .mini-table { width: 100%; font-size: 0.85rem; margin-bottom: 8px; }
    .mini-table th, .mini-table td { text-align: left; padding: 3px 6px; }
    .costo-est-row { display: flex; justify-content: space-between; padding: 2px 0; }
    .margen-pos { color: #81C784; font-weight: 600; }
    .margen-neg { color: #E57373; font-weight: 600; }
    .costo-adv { font-size: 0.75rem; color: #FFD54F; margin-top: 6px; }
  `]
})
export class ProductosComponent implements OnInit {
  data: Producto[] = [];
  categorias: Categoria[] = [];
  unidades: UnidadMedida[] = [];
  proveedores: ProveedorSimple[] = [];
  materiasPrimas: MateriaPrima[] = [];
  bomLineas: BomLinea[] = [];
  costoEst: any = null;
  loading = false;
  saving = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroNombre = '';
  filtroEstado = '';
  filtroCategoria: string = '';
  filtroOrigen: string = '';
  showModal = false;
  showConfirm = false;
  editando = false;
  editId: number | null = null;
  form: any = {
    codigo: '', nombre: '', descripcion: '', precioCompra: null, precioVenta: null,
    idCategoria: null, idUnidadMedida: null, stockMinimo: 0, estado: 'activo',
    origen: 'comprado', proveedorIds: [] as number[]
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
    if (this.filtroOrigen) params['origen'] = this.filtroOrigen;

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
    this.crud.listar<any>('materia-prima', { page: 0, size: 1000, estado: 'activo' }).subscribe({
      next: res => {
        this.materiasPrimas = res.content.map((m: any) => ({
          idMateriaPrima: m.idMateriaPrima,
          nombre: m.nombre,
          unidadMedida: m.unidadMedidaNombre || m.unidadMedida || m.abreviatura || ''
        }));
      },
      error: () => { /* el usuario puede no tener permiso de lectura de materia prima */ }
    });
  }

  onOrigenChange() {
    if (this.form.origen === 'fabricado' && this.bomLineas.length === 0) {
      this.agregarLineaBom();
    }
    if (this.form.origen !== 'fabricado') {
      this.costoEst = null;
    }
  }

  agregarLineaBom() {
    this.bomLineas.push({ idMateriaPrima: null, cantidadNecesaria: null });
  }

  quitarLineaBom(i: number) {
    this.bomLineas.splice(i, 1);
  }

  unidadDeMateria(idMateriaPrima: number | null): string {
    if (!idMateriaPrima) return '';
    const m = this.materiasPrimas.find(x => x.idMateriaPrima === idMateriaPrima);
    return m && m.unidadMedida ? '(' + m.unidadMedida + ')' : '';
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
      idCategoria: null, idUnidadMedida: null, stockMinimo: 0, estado: 'activo',
      origen: 'comprado', proveedorIds: []
    };
    this.bomLineas = [];
    this.costoEst = null;
    this.formError = '';
    this.showModal = true;
  }

  editar(item: Producto) {
    this.editando = true;
    this.editId = item.idProducto;
    this.formError = '';
    this.bomLineas = [];
    // Fetch full product details with proveedores
    this.crud.obtener<Producto>('productos', item.idProducto).subscribe({
      next: (prod) => {
        this.form = {
          codigo: prod.codigo, nombre: prod.nombre, descripcion: prod.descripcion || '',
          precioCompra: prod.precioCompra, precioVenta: prod.precioVenta,
          idCategoria: prod.idCategoria, idUnidadMedida: prod.idUnidadMedida,
          stockMinimo: prod.stockMinimo || 0, estado: prod.estado,
          origen: prod.origen || 'comprado',
          proveedorIds: prod.proveedores ? prod.proveedores.map(p => p.idProveedor) : []
        };
        this.showModal = true;
        if (this.form.origen === 'fabricado') {
          this.cargarBom(prod.idProducto);
        }
      },
      error: () => { this.mostrarToast('Error al cargar producto', true); }
    });
  }

  cargarBom(idProducto: number) {
    this.http.get<BomItemResponse[]>(`${environment.apiUrl}/productos/${idProducto}/bom`).subscribe({
      next: (items) => {
        this.bomLineas = (items || []).map(it => ({
          idMateriaPrima: it.materiaPrima.idMateriaPrima,
          cantidadNecesaria: it.cantidadNecesaria
        }));
        if (this.bomLineas.length === 0) { this.agregarLineaBom(); }
        this.cargarCostoEstimado(idProducto);
      },
      error: () => { /* sin BOM o sin permiso */ }
    });
  }

  cargarCostoEstimado(idProducto: number) {
    this.costoEst = null;
    this.http.get<any>(`${environment.apiUrl}/productos/${idProducto}/costo-estimado`).subscribe({
      next: (res) => { this.costoEst = res; },
      error: () => { /* sin BOM o sin permiso */ }
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
    if (!this.form.nombre?.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    if (!this.form.precioCompra) { this.formError = 'El precio de compra es obligatorio'; return; }
    if (!this.form.precioVenta) { this.formError = 'El precio de venta es obligatorio'; return; }
    if (!this.form.idCategoria) { this.formError = 'La categoría es obligatoria'; return; }
    if (!this.form.idUnidadMedida) { this.formError = 'La unidad de medida es obligatoria'; return; }

    let bomItems: { idMateriaPrima: number; cantidadNecesaria: number }[] = [];
    if (this.form.origen === 'fabricado') {
      bomItems = this.bomLineas
        .filter(l => l.idMateriaPrima && l.cantidadNecesaria && l.cantidadNecesaria > 0)
        .map(l => ({ idMateriaPrima: l.idMateriaPrima as number, cantidadNecesaria: l.cantidadNecesaria as number }));
      if (bomItems.length === 0) {
        this.formError = 'Un producto fabricado debe tener al menos 1 material en la lista de materiales';
        return;
      }
      const ids = bomItems.map(b => b.idMateriaPrima);
      if (new Set(ids).size !== ids.length) {
        this.formError = 'No repita la misma materia prima en la lista de materiales';
        return;
      }
    }
    this.saving = true;

    const obs = this.editando
      ? this.crud.actualizar<Producto>('productos', this.editId!, this.form)
      : this.crud.crear<Producto>('productos', this.form);

    obs.subscribe({
      next: (prod) => {
        if (this.form.origen === 'fabricado' && bomItems.length > 0) {
          this.http.put(`${environment.apiUrl}/productos/${prod.idProducto}/bom`, { items: bomItems }).subscribe({
            next: () => { this.saving = false; this.cerrarModal(); this.cargar(); this.mostrarToast('Producto y lista de materiales guardados'); },
            error: (err) => { this.saving = false; this.formError = err.error?.message || 'Producto guardado, pero fallo el BOM'; }
          });
        } else {
          this.saving = false; this.cerrarModal(); this.cargar(); this.mostrarToast('Producto guardado correctamente');
        }
      },
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
