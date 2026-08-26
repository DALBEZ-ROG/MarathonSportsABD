import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService, PageResponse } from '../../core/services/crud.service';
import { AuthService } from '../../core/services/auth.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';
import { SearchableSelectComponent } from '../../shared/components/searchable-select/searchable-select.component';
import { ModalSeguroDirective } from '../../shared/directives/modal-seguro.directive';
import { EstadoListaComponent } from '../../shared/components/estado-lista/estado-lista.component';

interface Ciudad {
  idCiudad: number;
  nombre: string;
}

interface Cliente {
  idCliente: number;
  nombre: string;
  apellido: string;
  cedula: string;
  email: string;
  telefono: string;
  direccion: string;
  idCiudad: number;
  ciudadNombre: string;
  estado: string;
}

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent, SearchableSelectComponent, ModalSeguroDirective, EstadoListaComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Clientes</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="filtroNombre" (input)="onSearch()" placeholder="Buscar nombre/apellido..." class="input-search"/>
          <select [(ngModel)]="filtroEstado" (change)="cargar()" class="select-filter">
            <option value="">Todos</option>
            <option value="activo">Activo</option>
            <option value="inactivo">Inactivo</option>
          </select>
        </div>
        <button *ngIf="puedeEscribir" class="btn-new" (click)="abrirModal()">+ Nuevo Cliente</button>
      </div>

      <app-estado-lista
        [cargando]="loading"
        [error]="cargaError"
        [vacio]="!loading && !cargaError && data.length === 0"
        [hayFiltro]="hayFiltroPuesto"
        nombrePlural="clientes"
        pistaVacio="Los clientes se dan de alta con «+ Nuevo»."
        (reintentar)="cargar()"></app-estado-lista>

      <table class="data-table" *ngIf="!loading && !cargaError && data.length > 0">
        <thead>
          <tr><th>ID</th><th>Nombre</th><th>Cédula</th><th>Email</th><th>Teléfono</th><th>Ciudad</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.idCliente}}</td>
            <td>{{item.nombre}} {{item.apellido}}</td>
            <td>{{item.cedula}}</td>
            <td>{{item.email}}</td>
            <td>{{item.telefono}}</td>
            <td>{{item.ciudadNombre}}</td>
            <td><span class="badge" [class.active]="item.estado==='activo'">{{item.estado}}</span></td>
            <td class="actions">
              <button *ngIf="puedeEscribir" class="btn-icon" (click)="editar(item)" title="Editar"><app-icon name="edit" [size]="16"/></button>
              <button *ngIf="puedeEscribir" class="btn-icon danger" (click)="confirmarEliminar(item)" title="Eliminar"><app-icon name="trash" [size]="16"/></button>
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
          <h3>{{editando ? 'Editar' : 'Nuevo'}} Cliente</h3>
          <form (ngSubmit)="guardar()">
            <div class="form-row">
              <div class="form-group">
                <label>Nombre *</label>
                <input type="text" [(ngModel)]="form.nombre" name="nombre" required/>
              </div>
              <div class="form-group">
                <label>Apellido *</label>
                <input type="text" [(ngModel)]="form.apellido" name="apellido" required/>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>Cédula *</label>
                <input type="text" [(ngModel)]="form.cedula" name="cedula" maxlength="10" required/>
              </div>
              <div class="form-group">
                <label>Email</label>
                <input type="email" [(ngModel)]="form.email" name="email"/>
              </div>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>Teléfono</label>
                <input type="text" [(ngModel)]="form.telefono" name="telefono"/>
              </div>
              <div class="form-group">
                <label>Ciudad</label>
                <app-searchable-select [(ngModel)]="form.idCiudad" name="idCiudad" [items]="ciudades"
                  valueKey="idCiudad" placeholder="Escriba la ciudad..."/>
              </div>
            </div>
            <div class="form-group">
              <label>Dirección</label>
              <input type="text" [(ngModel)]="form.direccion" name="direccion"/>
            </div>
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
          <p>¿Estás seguro de que deseas eliminar a <strong>{{itemEliminar?.nombre}} {{itemEliminar?.apellido}}</strong>?</p>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="showConfirm=false">Cancelar</button>
            <button class="btn-delete" (click)="eliminar()">Confirmar</button>
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
export class ClientesComponent implements OnInit {
  data: Cliente[] = [];
  ciudades: Ciudad[] = [];
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
  form: any = { nombre: '', apellido: '', cedula: '', email: '', telefono: '', direccion: '', idCiudad: null, estado: 'activo' };
  formError = '';
  itemEliminar: Cliente | null = null;
  toast = '';
  toastError = false;
  private searchTimeout: any;

  /**
   * L15 (D-33): quien puede escribir clientes.
   *
   * <p>La ruta /clientes admite Supervisor E-Commerce, pero el backend reserva
   * POST/PUT/DELETE a Administrador y Operador de Pedidos. Un Supervisor entraba,
   * veia los botones de crear y editar, y se llevaba un 403 al pulsarlos. La
   * lista de aqui es la misma que la de SecurityConfig; si una cambia, la otra
   * tambien.
   */
  get puedeEscribir(): boolean {
    const rol = this.auth.getCurrentUser()?.rol;
    return rol === 'Administrador' || rol === 'Operador de Pedidos';
  }

  constructor(private crud: CrudService, private auth: AuthService) {}

  ngOnInit() {
    this.cargar();
    this.cargarCiudades();
  }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroNombre) params['nombre'] = this.filtroNombre;
    if (this.filtroEstado) params['estado'] = this.filtroEstado;

    this.crud.listar<Cliente>('clientes', params).subscribe({
      next: res => { this.cargaError = null; this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: (err: any) => { this.loading = false; this.cargaError = this.motivoDelFallo(err); this.mostrarToast('Error al cargar clientes', true); }
    });
  }

  cargarCiudades() {
    this.crud.listar<Ciudad>('ciudades', { page: 0, size: 1000, estado: 'activo' }).subscribe({
      next: res => { this.ciudades = res.content; }
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
    this.form = { nombre: '', apellido: '', cedula: '', email: '', telefono: '', direccion: '', idCiudad: null, estado: 'activo' };
    this.formError = '';
    this.showModal = true;
  }

  editar(item: Cliente) {
    this.editando = true;
    this.editId = item.idCliente;
    this.form = {
      nombre: item.nombre, apellido: item.apellido, cedula: item.cedula || '',
      email: item.email || '', telefono: item.telefono || '',
      direccion: item.direccion || '', idCiudad: item.idCiudad || null, estado: item.estado
    };
    this.formError = '';
    this.showModal = true;
  }

  cerrarModal() { this.showModal = false; }

  guardar() {
    if (!this.form.nombre.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    if (!this.form.apellido.trim()) { this.formError = 'El apellido es obligatorio'; return; }
    if (!this.form.cedula.trim()) { this.formError = 'La cédula es obligatoria'; return; }
    this.saving = true;
    const body = { ...this.form };
    if (!body.idCiudad) body.idCiudad = null;

    const obs = this.editando
      ? this.crud.actualizar<Cliente>('clientes', this.editId!, body)
      : this.crud.crear<Cliente>('clientes', body);

    obs.subscribe({
      next: () => { this.saving = false; this.cerrarModal(); this.cargar(); this.mostrarToast('Cliente guardado correctamente'); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al guardar'; }
    });
  }

  confirmarEliminar(item: Cliente) { this.itemEliminar = item; this.showConfirm = true; }

  eliminar() {
    if (!this.itemEliminar) return;
    this.crud.eliminar('clientes', this.itemEliminar.idCliente).subscribe({
      next: () => { this.showConfirm = false; this.cargar(); this.mostrarToast('Cliente eliminado'); },
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
