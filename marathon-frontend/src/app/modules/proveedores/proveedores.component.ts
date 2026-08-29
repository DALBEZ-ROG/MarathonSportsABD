import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService, PageResponse } from '../../core/services/crud.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';
import { ModalSeguroDirective } from '../../shared/directives/modal-seguro.directive';
import { EstadoListaComponent } from '../../shared/components/estado-lista/estado-lista.component';

interface Proveedor {
  idProveedor: number;
  nombre: string;
  ruc: string;
  direccion: string;
  telefono: string;
  email: string;
  estado: string;
  createdAt: string;
}

@Component({
  selector: 'app-proveedores',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent, ModalSeguroDirective, EstadoListaComponent],
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Proveedores</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="filtroNombre" (input)="onSearch()" placeholder="Buscar por nombre..." class="input-search"/>
          <select [(ngModel)]="filtroEstado" (change)="cargar()" class="select-filter">
            <option value="">Todos</option>
            <option value="activo">Activo</option>
            <option value="inactivo">Inactivo</option>
          </select>
        </div>
        <button class="btn-new" (click)="abrirModal()">+ Nuevo</button>
      </div>

      <app-estado-lista
        [cargando]="loading"
        [error]="cargaError"
        [vacio]="!loading && !cargaError && data.length === 0"
        [hayFiltro]="hayFiltroPuesto"
        nombrePlural="proveedores"
        pistaVacio="Crea el primero con «+ Nuevo»."
        (reintentar)="cargar()"></app-estado-lista>

      <table class="data-table" *ngIf="!loading && !cargaError && data.length > 0">
        <thead>
          <tr><th>ID</th><th>Nombre</th><th>RUC</th><th>Teléfono</th><th>Correo</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.idProveedor}}</td>
            <td>{{item.nombre}}</td>
            <td>{{item.ruc}}</td>
            <td>{{item.telefono}}</td>
            <td>{{item.email}}</td>
            <td><span class="badge" [class.active]="item.estado==='activo'">{{item.estado}}</span></td>
            <td class="actions">
              <button class="btn-icon" (click)="editar(item)" title="Editar"><app-icon name="edit" [size]="16"/></button>
              <button class="btn-icon danger" (click)="confirmarEliminar(item)" title="Eliminar"><app-icon name="trash" [size]="16"/></button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <!-- Modal -->
      <div class="modal-overlay" *ngIf="showModal" appModalSeguro (cerrar)="cerrarModal()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>{{editando ? 'Editar' : 'Nuevo'}} Proveedor</h3>
          <form (ngSubmit)="guardar()">
            <div class="form-group">
              <label>Nombre *</label>
              <input type="text" [(ngModel)]="form.nombre" name="nombre" required/>
            </div>
            <div class="form-group">
              <label>RUC</label>
              <input type="text" [(ngModel)]="form.ruc" name="ruc" maxlength="13"/>
            </div>
            <div class="form-group">
              <label>Dirección</label>
              <input type="text" [(ngModel)]="form.direccion" name="direccion"/>
            </div>
            <div class="form-group">
              <label>Teléfono</label>
              <input type="text" [(ngModel)]="form.telefono" name="telefono"/>
            </div>
            <div class="form-group">
              <label>Email</label>
              <input type="email" [(ngModel)]="form.email" name="email"/>
            </div>
            <!-- F85: aquí había un desplegable de Ciudad. La tabla proveedor NO
                 tiene columna de ciudad: lo que se elegía se perdía al guardar y
                 la columna «Ciudad» de la lista salía siempre vacía. Ponerle
                 ciudad al proveedor es una decisión con su columna y su clave
                 ajena; mientras tanto no se pregunta lo que no se puede guardar. -->
            <div class="form-group" *ngIf="editando">
              <label>Estado</label>
              <select [(ngModel)]="form.estado" name="estado">
                <option value="activo">Activo</option>
                <option value="inactivo">Inactivo</option>
              </select>
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
      <div class="modal-overlay" *ngIf="showConfirm" appModalSeguro (cerrar)="showConfirm=false">
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
export class ProveedoresComponent implements OnInit {
  data: Proveedor[] = [];
  loading = false;
  /**
   * Motivo del fallo de carga, o null si la carga fue bien (D6).
   * Sin esto la pantalla no podia distinguir "no hay registros" de "no se
   * pudo preguntar", y enseñaba lo primero en los dos casos.
   */
  cargaError: string | null = null;

  /** ¿Hay busqueda o filtros puestos? Cambia el mensaje de lista vacia. */
  get hayFiltroPuesto(): boolean { return !!this.filtroNombre || !!this.filtroEstado; }

  saving = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroNombre = '';
  filtroEstado = '';
  showModal = false;
  showConfirm = false;
  editando = false;
  editId: number | null = null;
  form: any = { nombre: '', ruc: '', direccion: '', telefono: '', email: '', estado: 'activo' };
  formError = '';
  itemEliminar: Proveedor | null = null;
  toast = '';
  toastError = false;
  private searchTimeout: any;

  constructor(private crud: CrudService) {}

  ngOnInit() {
    this.cargar();
  }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroNombre) params['nombre'] = this.filtroNombre;
    if (this.filtroEstado) params['estado'] = this.filtroEstado;

    this.crud.listar<Proveedor>('proveedores', params).subscribe({
      next: res => { this.cargaError = null; this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: (err: any) => { this.loading = false; this.cargaError = this.motivoDelFallo(err); this.mostrarToast('Error al cargar datos', true); }
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
    this.form = { nombre: '', ruc: '', direccion: '', telefono: '', email: '', estado: 'activo' };
    this.formError = '';
    this.showModal = true;
  }

  editar(item: Proveedor) {
    this.editando = true;
    this.editId = item.idProveedor;
    this.form = {
      nombre: item.nombre, ruc: item.ruc || '', direccion: item.direccion || '',
      telefono: item.telefono || '', email: item.email || '',
      estado: item.estado
    };
    this.formError = '';
    this.showModal = true;
  }

  cerrarModal() { this.showModal = false; }

  guardar() {
    if (!this.form.nombre.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    this.saving = true;
    const body = { ...this.form };

    const obs = this.editando
      ? this.crud.actualizar<Proveedor>('proveedores', this.editId!, body)
      : this.crud.crear<Proveedor>('proveedores', body);

    obs.subscribe({
      next: () => { this.saving = false; this.cerrarModal(); this.cargar(); this.mostrarToast('Proveedor guardado correctamente'); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al guardar'; }
    });
  }

  confirmarEliminar(item: Proveedor) { this.itemEliminar = item; this.showConfirm = true; }

  eliminar() {
    if (!this.itemEliminar) return;
    this.crud.eliminar('proveedores', this.itemEliminar.idProveedor).subscribe({
      next: () => { this.showConfirm = false; this.cargar(); this.mostrarToast('Proveedor eliminado'); },
      error: (err) => { this.showConfirm = false; this.mostrarToast(err.error?.message || 'Error al eliminar', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
  /** Traduce el fallo a algo que se pueda leer y, si se puede, resolver. */
  private motivoDelFallo(err: any): string {
    if (err?.status === 0) return 'No hay conexión con el servidor.';
    if (err?.status === 403) return 'Tu rol no tiene permiso para ver esta información.';
    if (err?.status === 401) return 'Tu sesión ha caducado. Vuelve a entrar.';
    return err?.error?.message ?? 'El servidor no respondió correctamente.';
  }

}
