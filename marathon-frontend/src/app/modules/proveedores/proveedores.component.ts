import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService, PageResponse } from '../../core/services/crud.service';

interface Ciudad {
  idCiudad: number;
  nombre: string;
  estado: string;
}

interface Proveedor {
  idProveedor: number;
  nombre: string;
  ruc: string;
  direccion: string;
  telefono: string;
  email: string;
  idCiudad: number;
  ciudadNombre: string;
  estado: string;
  createdAt: string;
}

@Component({
  selector: 'app-proveedores',
  standalone: true,
  imports: [CommonModule, FormsModule],
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

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>ID</th><th>Nombre</th><th>RUC</th><th>Teléfono</th><th>Ciudad</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.idProveedor}}</td>
            <td>{{item.nombre}}</td>
            <td>{{item.ruc}}</td>
            <td>{{item.telefono}}</td>
            <td>{{item.ciudadNombre}}</td>
            <td><span class="badge" [class.active]="item.estado==='activo'">{{item.estado}}</span></td>
            <td class="actions">
              <button class="btn-icon" (click)="editar(item)" title="Editar">✏️</button>
              <button class="btn-icon danger" (click)="confirmarEliminar(item)" title="Eliminar">🗑️</button>
            </td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="7" class="empty">No hay registros</td></tr>
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
            <div class="form-group">
              <label>Ciudad</label>
              <select [(ngModel)]="form.idCiudad" name="idCiudad">
                <option [ngValue]="null">-- Seleccione --</option>
                <option *ngFor="let c of ciudades" [ngValue]="c.idCiudad">{{c.nombre}}</option>
              </select>
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
    .crud-container { max-width: 1100px; }
    .toolbar { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .toolbar h2 { color: #2d5a27; flex: 1; }
    .filters { display: flex; gap: 0.5rem; }
    .input-search, .select-filter { padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; font-size: 0.85rem; }
    .input-search { width: 180px; }
    .btn-new { background: #2d5a27; color: #fff; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; font-weight: 600; }
    .btn-new:hover { background: #1e3d1a; }
    .spinner { text-align: center; padding: 2rem; color: #666; }
    .data-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,0.1); }
    .data-table th { background: #2d5a27; color: #fff; padding: 0.7rem; text-align: left; font-size: 0.85rem; }
    .data-table td { padding: 0.6rem 0.7rem; border-bottom: 1px solid #eee; font-size: 0.85rem; }
    .data-table tr:hover td { background: #f0f7f0; }
    .badge { padding: 0.2rem 0.6rem; border-radius: 12px; font-size: 0.75rem; background: #eee; }
    .badge.active { background: #c8e6c9; color: #2d5a27; }
    .actions { display: flex; gap: 0.3rem; }
    .btn-icon { background: none; border: none; cursor: pointer; font-size: 1rem; padding: 0.2rem; }
    .empty { text-align: center; color: #999; padding: 2rem !important; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1rem; }
    .pagination button { padding: 0.4rem 0.8rem; border: 1px solid #ddd; border-radius: 4px; background: #fff; cursor: pointer; }
    .pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .modal-card { background: #fff; border-radius: 10px; padding: 2rem; width: 90%; max-width: 500px; max-height: 90vh; overflow-y: auto; }
    .modal-card h3 { color: #2d5a27; margin-bottom: 1rem; }
    .form-group { margin-bottom: 1rem; display: flex; flex-direction: column; gap: 0.3rem; }
    .form-group label { font-size: 0.85rem; font-weight: 600; }
    .form-group input, .form-group select { padding: 0.6rem; border: 1px solid #ddd; border-radius: 4px; }
    .error { color: #c00; font-size: 0.8rem; }
    .modal-actions { display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 1.5rem; }
    .btn-cancel { padding: 0.5rem 1rem; border: 1px solid #ddd; border-radius: 4px; background: #fff; cursor: pointer; }
    .btn-save { padding: 0.5rem 1rem; border: none; border-radius: 4px; background: #2d5a27; color: #fff; cursor: pointer; }
    .btn-save:disabled { opacity: 0.6; }
    .btn-delete { padding: 0.5rem 1rem; border: none; border-radius: 4px; background: #c00; color: #fff; cursor: pointer; }
    .toast { position: fixed; bottom: 2rem; right: 2rem; background: #2d5a27; color: #fff; padding: 0.8rem 1.5rem; border-radius: 6px; z-index: 9999; animation: fadeIn 0.3s; }
    .toast.error { background: #c00; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
  `]
})
export class ProveedoresComponent implements OnInit {
  data: Proveedor[] = [];
  ciudades: Ciudad[] = [];
  loading = false;
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
  form: any = { nombre: '', ruc: '', direccion: '', telefono: '', email: '', idCiudad: null, estado: 'activo' };
  formError = '';
  itemEliminar: Proveedor | null = null;
  toast = '';
  toastError = false;
  private searchTimeout: any;

  constructor(private crud: CrudService) {}

  ngOnInit() {
    this.cargar();
    this.cargarCiudades();
  }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroNombre) params['nombre'] = this.filtroNombre;
    if (this.filtroEstado) params['estado'] = this.filtroEstado;

    this.crud.listar<Proveedor>('proveedores', params).subscribe({
      next: res => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; this.mostrarToast('Error al cargar datos', true); }
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
    this.form = { nombre: '', ruc: '', direccion: '', telefono: '', email: '', idCiudad: null, estado: 'activo' };
    this.formError = '';
    this.showModal = true;
  }

  editar(item: Proveedor) {
    this.editando = true;
    this.editId = item.idProveedor;
    this.form = {
      nombre: item.nombre, ruc: item.ruc || '', direccion: item.direccion || '',
      telefono: item.telefono || '', email: item.email || '',
      idCiudad: item.idCiudad || null, estado: item.estado
    };
    this.formError = '';
    this.showModal = true;
  }

  cerrarModal() { this.showModal = false; }

  guardar() {
    if (!this.form.nombre.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    this.saving = true;
    const body = { ...this.form };
    if (!body.idCiudad) body.idCiudad = null;

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
}
