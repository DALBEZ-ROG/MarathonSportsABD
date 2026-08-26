import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService } from '../../../core/services/crud.service';
import { AppIconComponent } from '../../../shared/components/icon/icon.component';
import { ModalSeguroDirective } from '../../../shared/directives/modal-seguro.directive';
import { EstadoListaComponent } from '../../../shared/components/estado-lista/estado-lista.component';

interface Categoria { idCategoria: number; nombre: string; descripcion: string; }

@Component({
  selector: 'app-categorias',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent, ModalSeguroDirective, EstadoListaComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Categorías</h2>
        <input type="text" [(ngModel)]="filtroNombre" (input)="onSearch()" placeholder="Buscar..." class="input-search"/>
        <button class="btn-new" (click)="abrirModal()">+ Nuevo</button>
      </div>
      <app-estado-lista
        [cargando]="loading"
        [error]="cargaError"
        [vacio]="!loading && !cargaError && data.length === 0"
        [hayFiltro]="hayFiltroPuesto"
        nombrePlural="categorías"
        pistaVacio="Crea la primera con «+ Nueva»."
        (reintentar)="cargar()"></app-estado-lista>
      <table class="data-table" *ngIf="!loading && !cargaError && data.length > 0">
        <thead><tr><th>ID</th><th>Nombre</th><th>Descripción</th><th>Acciones</th></tr></thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.idCategoria}}</td><td>{{item.nombre}}</td><td>{{item.descripcion || '-'}}</td>
            <td class="actions">
              <button class="btn-icon" (click)="editar(item)"><app-icon name="edit" [size]="16"/></button>
              <button class="btn-icon danger" (click)="confirmarEliminar(item)"><app-icon name="trash" [size]="16"/></button>
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
      <div class="modal-overlay" *ngIf="showModal" appModalSeguro (cerrar)="cerrarModal()">
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
export class CategoriasComponent implements OnInit {
  data: Categoria[] = []; loading = false; saving = false; page = 0; size = 10; totalPages = 0;
  /**
   * Motivo del fallo de carga, o null si la carga fue bien (D6).
   * Sin esto la pantalla no podia distinguir "no hay registros" de "no se
   * pudo preguntar", y enseñaba lo primero en los dos casos.
   */
  cargaError: string | null = null;

  /** ¿Hay busqueda o filtros puestos? Cambia el mensaje de lista vacia. */
  get hayFiltroPuesto(): boolean { return !!this.filtroNombre; }

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
      next: res => { this.cargaError = null; this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: (err: any) => { this.loading = false; this.cargaError = this.motivoDelFallo(err); }
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
  /** Traduce el fallo a algo que se pueda leer y, si se puede, resolver. */
  private motivoDelFallo(err: any): string {
    if (err?.status === 0) return 'No hay conexión con el servidor.';
    if (err?.status === 403) return 'Tu rol no tiene permiso para ver esta información.';
    if (err?.status === 401) return 'Tu sesión ha caducado. Vuelve a entrar.';
    return err?.error?.message ?? 'El servidor no respondió correctamente.';
  }

}
