import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService } from '../../../core/services/crud.service';

interface Categoria { idCategoria: number; nombre: string; descripcion: string; }

@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Categorías</h2>
        <input type="text" [(ngModel)]="filtroNombre" (input)="onSearch()" placeholder="Buscar..." class="input-search"/>
        <button class="btn-new" (click)="abrirModal()">+ Nuevo</button>
      </div>
      <div class="spinner" *ngIf="loading">Cargando...</div>
      <table class="data-table" *ngIf="!loading">
        <thead><tr><th>ID</th><th>Nombre</th><th>Descripción</th><th>Acciones</th></tr></thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.idCategoria}}</td><td>{{item.nombre}}</td><td>{{item.descripcion || '-'}}</td>
            <td class="actions">
              <button class="btn-icon" (click)="editar(item)">✏️</button>
              <button class="btn-icon danger" (click)="confirmarEliminar(item)">🗑️</button>
            </td>
          </tr>
          <tr *ngIf="data.length===0"><td colspan="4" class="empty">No hay registros</td></tr>
        </tbody>
      </table>
      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>
      <div class="modal-overlay" *ngIf="showModal" (click)="cerrarModal()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>{{editando ? 'Editar' : 'Nueva'}} Categoría</h3>
          <form (ngSubmit)="guardar()">
            <div class="form-group"><label>Nombre *</label><input type="text" [(ngModel)]="form.nombre" name="nombre" required maxlength="100"/></div>
            <div class="form-group"><label>Descripción</label><input type="text" [(ngModel)]="form.descripcion" name="descripcion" maxlength="255"/></div>
            <small class="error" *ngIf="formError">{{formError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="cerrarModal()">Cancelar</button>
              <button type="submit" class="btn-save" [disabled]="saving">{{saving ? 'Guardando...' : 'Guardar'}}</button>
            </div>
          </form>
        </div>
      </div>
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
    .crud-container { max-width: 900px; }
    .toolbar { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .toolbar h2 { color: #2d5a27; flex: 1; }
    .input-search { padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; font-size: 0.85rem; width: 180px; }
    .btn-new { background: #2d5a27; color: #fff; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; font-weight: 600; }
    .spinner { text-align: center; padding: 2rem; color: #666; }
    .data-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,0.1); }
    .data-table th { background: #2d5a27; color: #fff; padding: 0.7rem; text-align: left; font-size: 0.85rem; }
    .data-table td { padding: 0.6rem 0.7rem; border-bottom: 1px solid #eee; font-size: 0.85rem; }
    .data-table tr:hover td { background: #f0f7f0; }
    .actions { display: flex; gap: 0.3rem; }
    .btn-icon { background: none; border: none; cursor: pointer; font-size: 1rem; }
    .empty { text-align: center; color: #999; padding: 2rem !important; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1rem; }
    .pagination button { padding: 0.4rem 0.8rem; border: 1px solid #ddd; border-radius: 4px; background: #fff; cursor: pointer; }
    .pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .modal-card { background: #fff; border-radius: 10px; padding: 2rem; width: 90%; max-width: 400px; }
    .modal-card h3 { color: #2d5a27; margin-bottom: 1rem; }
    .form-group { margin-bottom: 1rem; display: flex; flex-direction: column; gap: 0.3rem; }
    .form-group label { font-size: 0.85rem; font-weight: 600; }
    .form-group input { padding: 0.6rem; border: 1px solid #ddd; border-radius: 4px; }
    .error { color: #c00; font-size: 0.8rem; }
    .modal-actions { display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 1.5rem; }
    .btn-cancel { padding: 0.5rem 1rem; border: 1px solid #ddd; border-radius: 4px; background: #fff; cursor: pointer; }
    .btn-save { padding: 0.5rem 1rem; border: none; border-radius: 4px; background: #2d5a27; color: #fff; cursor: pointer; }
    .btn-save:disabled { opacity: 0.6; }
    .btn-delete { padding: 0.5rem 1rem; border: none; border-radius: 4px; background: #c00; color: #fff; cursor: pointer; }
    .toast { position: fixed; bottom: 2rem; right: 2rem; background: #2d5a27; color: #fff; padding: 0.8rem 1.5rem; border-radius: 6px; z-index: 9999; }
    .toast.error { background: #c00; }
  `]
})
export class CategoriasComponent implements OnInit {
  data: Categoria[] = []; loading = false; saving = false; page = 0; size = 10; totalPages = 0;
  filtroNombre = ''; showModal = false; showConfirm = false; editando = false; editId: number | null = null;
  form = { nombre: '', descripcion: '' }; formError = ''; itemEliminar: Categoria | null = null;
  toast = ''; toastError = false; private searchTimeout: any;

  constructor(private crud: CrudService) {}
  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroNombre) params['nombre'] = this.filtroNombre;
    this.crud.listar<Categoria>('categorias', params).subscribe({
      next: res => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  onSearch() { clearTimeout(this.searchTimeout); this.searchTimeout = setTimeout(() => { this.page = 0; this.cargar(); }, 300); }
  cambiarPagina(p: number) { this.page = p; this.cargar(); }
  abrirModal() { this.editando = false; this.editId = null; this.form = { nombre: '', descripcion: '' }; this.formError = ''; this.showModal = true; }
  editar(item: Categoria) { this.editando = true; this.editId = item.idCategoria; this.form = { nombre: item.nombre, descripcion: item.descripcion || '' }; this.formError = ''; this.showModal = true; }
  cerrarModal() { this.showModal = false; }

  guardar() {
    if (!this.form.nombre.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    this.saving = true;
    const obs = this.editando ? this.crud.actualizar<Categoria>('categorias', this.editId!, this.form) : this.crud.crear<Categoria>('categorias', this.form);
    obs.subscribe({
      next: () => { this.saving = false; this.cerrarModal(); this.cargar(); this.mostrarToast('Registro guardado correctamente'); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al guardar'; }
    });
  }

  confirmarEliminar(item: Categoria) { this.itemEliminar = item; this.showConfirm = true; }
  eliminar() {
    if (!this.itemEliminar) return;
    this.crud.eliminar('categorias', this.itemEliminar.idCategoria).subscribe({
      next: () => { this.showConfirm = false; this.cargar(); this.mostrarToast('Registro eliminado'); },
      error: (err) => { this.showConfirm = false; this.mostrarToast(err.error?.message || 'Error al eliminar', true); }
    });
  }

  mostrarToast(msg: string, error = false) { this.toast = msg; this.toastError = error; setTimeout(() => { this.toast = ''; }, 3000); }
}
