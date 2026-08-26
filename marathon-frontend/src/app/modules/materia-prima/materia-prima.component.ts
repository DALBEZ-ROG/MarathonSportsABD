import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { CrudService } from '../../core/services/crud.service';
import { AuthService } from '../../core/services/auth.service';
import { ApiService } from '../../core/services/api.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';
import { ModalSeguroDirective } from '../../shared/directives/modal-seguro.directive';
import { EstadoListaComponent } from '../../shared/components/estado-lista/estado-lista.component';

interface UnidadMedida {
  idUnidad: number;
  nombre: string;
  abreviatura: string;
}

interface MateriaPrima {
  idMateriaPrima: number;
  nombre: string;
  descripcion: string;
  idUnidadMedida: number;
  unidadMedidaNombre: string;
  estado: string;
  stockActual: number;
  stockMinimo: number;
  stockBajo: boolean;
  costoUnitarioPromedio: number;
}

@Component({
  selector: 'app-materia-prima',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent, ModalSeguroDirective, EstadoListaComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Materia Prima</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="filtroNombre" (input)="onSearch()" placeholder="Buscar por nombre..." class="input-search"/>
          <select [(ngModel)]="filtroEstado" (change)="cargar()" class="select-filter">
            <option value="">Todos</option>
            <option value="activo">Activo</option>
            <option value="inactivo">Inactivo</option>
          </select>
        </div>
        <button class="btn-new" *ngIf="puedeEscribir" (click)="abrirModal()">+ Nueva</button>
      </div>

      <app-estado-lista
        [cargando]="loading"
        [error]="cargaError"
        [vacio]="!loading && !cargaError && data.length === 0"
        [hayFiltro]="hayFiltroPuesto"
        nombrePlural="materias primas"
        pistaVacio="Crea la primera con «+ Nueva»."
        (reintentar)="cargar()"></app-estado-lista>

      <table class="data-table" *ngIf="!loading && !cargaError && data.length > 0">
        <thead>
          <tr><th>#</th><th>Nombre</th><th>Unidad</th><th>Stock Actual</th><th>Stock Min.</th><th title="Costo promedio ponderado, actualizado automáticamente con cada recepción de compra.">Costo promedio</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data" [class.stock-bajo]="item.stockBajo">
            <td>{{item.idMateriaPrima}}</td>
            <td>{{item.nombre}}</td>
            <td>{{item.unidadMedidaNombre}}</td>
            <td>{{item.stockActual | number:'1.3-3'}}</td>
            <td>{{item.stockMinimo | number:'1.3-3'}}</td>
            <td title="Costo promedio ponderado, actualizado automáticamente con cada recepción de compra.">$ {{item.costoUnitarioPromedio | number:'1.4-4'}}</td>
            <td><span class="badge" [class.active]="item.estado==='activo'">{{item.estado}}</span></td>
            <td class="actions">
              <button class="btn-icon" *ngIf="puedeEscribir" (click)="editar(item)" title="Editar"><app-icon name="edit" [size]="16"/></button>
              <button class="btn-icon" (click)="verKardex(item)" title="Kardex"><app-icon name="clipboard" [size]="16"/></button>
              <button class="btn-icon" *ngIf="puedeEscribir" (click)="abrirMovimiento(item)" title="Movimiento"><app-icon name="refresh" [size]="16"/></button>
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
          <h3>{{editando ? 'Editar' : 'Nueva'}} Materia Prima</h3>
          <form (ngSubmit)="guardar()">
            <div class="form-group">
              <label>Nombre *</label>
              <input type="text" [(ngModel)]="form.nombre" name="nombre" required/>
            </div>
            <div class="form-group">
              <label>Descripción</label>
              <input type="text" [(ngModel)]="form.descripcion" name="descripcion"/>
            </div>
            <div class="form-row">
              <div class="form-group">
                <label>Unidad de Medida *</label>
                <select [(ngModel)]="form.idUnidadMedida" name="idUnidadMedida" required>
                  <option [ngValue]="null">-- Seleccione --</option>
                  <option *ngFor="let u of unidades" [ngValue]="u.idUnidad">{{u.nombre}} ({{u.abreviatura}})</option>
                </select>
              </div>
              <div class="form-group" *ngIf="editando">
                <label>Estado</label>
                <select [(ngModel)]="form.estado" name="estado">
                  <option value="activo">Activo</option>
                  <option value="inactivo">Inactivo</option>
                </select>
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

      <!-- Confirm -->
      <div class="modal-overlay" *ngIf="showConfirm" appModalSeguro (cerrar)="showConfirm=false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Confirmar desactivación</h3>
          <p>¿Deseas desactivar <strong>{{itemEliminar?.nombre}}</strong>?</p>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="showConfirm=false">Cancelar</button>
            <button class="btn-delete" (click)="eliminar()">Confirmar</button>
          </div>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [``]
})
export class MateriaPrimaComponent implements OnInit {
  data: MateriaPrima[] = [];
  unidades: UnidadMedida[] = [];
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
  form: any = { nombre: '', descripcion: '', idUnidadMedida: null, estado: 'activo' };
  formError = '';
  itemEliminar: MateriaPrima | null = null;
  toast = '';
  toastError = false;
  puedeEscribir = false;
  private searchTimeout: any;

  constructor(private crud: CrudService, private auth: AuthService, private router: Router, private api: ApiService) {}

  ngOnInit() {
    this.puedeEscribir = this.auth.hasRol('Administrador') || this.auth.hasRol('Encargado de Producción');
    this.cargar();
    this.cargarCatalogos();
  }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroNombre) params['nombre'] = this.filtroNombre;
    if (this.filtroEstado) params['estado'] = this.filtroEstado;

    this.crud.listar<MateriaPrima>('materia-prima', params).subscribe({
      next: res => { this.cargaError = null; this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: (err: any) => { this.loading = false; this.cargaError = this.motivoDelFallo(err); this.mostrarToast('Error al cargar datos', true); }
    });
  }

  cargarCatalogos() {
    this.crud.listar<UnidadMedida>('unidades-medida', { page: 0, size: 1000 }).subscribe({
      next: res => { this.unidades = res.content; }
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
    this.form = { nombre: '', descripcion: '', idUnidadMedida: null, estado: 'activo' };
    this.formError = '';
    this.showModal = true;
  }

  editar(item: MateriaPrima) {
    this.editando = true;
    this.editId = item.idMateriaPrima;
    this.formError = '';
    this.form = {
      nombre: item.nombre, descripcion: item.descripcion || '',
      idUnidadMedida: item.idUnidadMedida, estado: item.estado
    };
    this.showModal = true;
  }

  cerrarModal() { this.showModal = false; }

  guardar() {
    if (!this.form.nombre?.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    if (!this.form.idUnidadMedida) { this.formError = 'La unidad de medida es obligatoria'; return; }
    this.saving = true;

    const obs = this.editando
      ? this.crud.actualizar<MateriaPrima>('materia-prima', this.editId!, this.form)
      : this.crud.crear<MateriaPrima>('materia-prima', this.form);

    obs.subscribe({
      next: () => { this.saving = false; this.cerrarModal(); this.cargar(); this.mostrarToast('Materia prima guardada'); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al guardar'; }
    });
  }

  confirmarEliminar(item: MateriaPrima) { this.itemEliminar = item; this.showConfirm = true; }

  eliminar() {
    if (!this.itemEliminar) return;
    this.crud.eliminar('materia-prima', this.itemEliminar.idMateriaPrima).subscribe({
      next: () => { this.showConfirm = false; this.cargar(); this.mostrarToast('Materia prima desactivada'); },
      error: (err) => { this.showConfirm = false; this.mostrarToast(err.error?.message || 'Error al desactivar', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }

  // ---- F26: Kardex ----
  verKardex(item: MateriaPrima) {
    this.router.navigate(['/materia-prima', item.idMateriaPrima, 'kardex']);
  }

  movItem: MateriaPrima | null = null;
  showMovModal = false;
  movForm = { tipoMovimiento: 'ajuste', esIncremento: true, cantidad: 0, observacion: '' };

  abrirMovimiento(item: MateriaPrima) {
    this.movItem = item;
    this.movForm = { tipoMovimiento: 'ajuste', esIncremento: true, cantidad: 0, observacion: '' };
    this.showMovModal = true;
  }

  guardarMovimiento() {
    if (!this.movItem || !this.movForm.cantidad) return;
    const body = {
      idMateriaPrima: this.movItem.idMateriaPrima,
      tipoMovimiento: this.movForm.tipoMovimiento,
      esIncremento: this.movForm.tipoMovimiento === 'ajuste' ? this.movForm.esIncremento : false,
      cantidad: this.movForm.cantidad,
      observacion: this.movForm.observacion || null
    };
    this.api.post<any>('materia-prima/movimiento', body).subscribe({
      next: () => { this.showMovModal = false; this.cargar(); this.mostrarToast('Movimiento registrado'); },
      error: (err: any) => { this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }
  /** Traduce el fallo a algo que se pueda leer y, si se puede, resolver. */
  private motivoDelFallo(err: any): string {
    if (err?.status === 0) return 'No hay conexión con el servidor.';
    if (err?.status === 403) return 'Tu rol no tiene permiso para ver esta información.';
    if (err?.status === 401) return 'Tu sesión ha caducado. Vuelve a entrar.';
    return err?.error?.message ?? 'El servidor no respondió correctamente.';
  }

}
