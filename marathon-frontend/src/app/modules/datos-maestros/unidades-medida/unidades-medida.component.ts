import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService } from '../../../core/services/crud.service';

interface UnidadMedida { idUnidad: number; nombre: string; abreviatura: string; }

@Component({
  selector: 'app-unidades-medida',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Unidades de Medida</h2>
        <input type="text" [(ngModel)]="filtroNombre" (input)="onSearch()" placeholder="Buscar..." class="input-search"/>
        <button class="btn-new" (click)="abrirModal()">+ Nuevo</button>
      </div>
      <div class="spinner" *ngIf="loading">Cargando...</div>
      <table class="data-table" *ngIf="!loading">
        <thead><tr><th>ID</th><th>Nombre</th><th>Abreviatura</th><th>Acciones</th></tr></thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.idUnidad}}</td><td>{{item.nombre}}</td><td>{{item.abreviatura}}</td>
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
          <h3>{{editando ? 'Editar' : 'Nueva'}} Unidad de Medida</h3>
          <form (ngSubmit)="guardar()">
            <div class="form-group"><label>Nombre *</label><input type="text" [(ngModel)]="form.nombre" name="nombre" required maxlength="50"/></div>
            <div class="form-group"><label>Abreviatura *</label><input type="text" [(ngModel)]="form.abreviatura" name="abreviatura" required maxlength="10"/></div>
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
    /* Inherits global dark theme from styles.scss */
  `]
})
export class UnidadesMedidaComponent implements OnInit {
  data: UnidadMedida[] = []; loading = false; saving = false; page = 0; size = 10; totalPages = 0;
  filtroNombre = ''; showModal = false; showConfirm = false; editando = false; editId: number | null = null;
  form = { nombre: '', abreviatura: '' }; formError = ''; itemEliminar: UnidadMedida | null = null;
  toast = ''; toastError = false; private searchTimeout: any;

  constructor(private crud: CrudService) {}
  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroNombre) params['nombre'] = this.filtroNombre;
    this.crud.listar<UnidadMedida>('unidades-medida', params).subscribe({
      next: res => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  onSearch() { clearTimeout(this.searchTimeout); this.searchTimeout = setTimeout(() => { this.page = 0; this.cargar(); }, 300); }
  cambiarPagina(p: number) { this.page = p; this.cargar(); }
  abrirModal() { this.editando = false; this.editId = null; this.form = { nombre: '', abreviatura: '' }; this.formError = ''; this.showModal = true; }
  editar(item: UnidadMedida) { this.editando = true; this.editId = item.idUnidad; this.form = { nombre: item.nombre, abreviatura: item.abreviatura }; this.formError = ''; this.showModal = true; }
  cerrarModal() { this.showModal = false; }

  guardar() {
    if (!this.form.nombre.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    if (!this.form.abreviatura.trim()) { this.formError = 'La abreviatura es obligatoria'; return; }
    this.saving = true;
    const obs = this.editando ? this.crud.actualizar<UnidadMedida>('unidades-medida', this.editId!, this.form) : this.crud.crear<UnidadMedida>('unidades-medida', this.form);
    obs.subscribe({
      next: () => { this.saving = false; this.cerrarModal(); this.cargar(); this.mostrarToast('Registro guardado correctamente'); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al guardar'; }
    });
  }

  confirmarEliminar(item: UnidadMedida) { this.itemEliminar = item; this.showConfirm = true; }
  eliminar() {
    if (!this.itemEliminar) return;
    this.crud.eliminar('unidades-medida', this.itemEliminar.idUnidad).subscribe({
      next: () => { this.showConfirm = false; this.cargar(); this.mostrarToast('Registro eliminado'); },
      error: (err) => { this.showConfirm = false; this.mostrarToast(err.error?.message || 'Error al eliminar', true); }
    });
  }

  mostrarToast(msg: string, error = false) { this.toast = msg; this.toastError = error; setTimeout(() => { this.toast = ''; }, 3000); }
}
