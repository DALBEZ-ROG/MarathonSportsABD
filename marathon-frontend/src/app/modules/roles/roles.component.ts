import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AppIconComponent } from '../../shared/components/icon/icon.component';
import { ModalSeguroDirective } from '../../shared/directives/modal-seguro.directive';
import { EstadoListaComponent } from '../../shared/components/estado-lista/estado-lista.component';

interface Permiso { idPermiso:number; modulo:string; accion:string; }
interface RolResp { idRol:number; nombre:string; descripcion:string; permisos:Permiso[]; }

@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent, ModalSeguroDirective, EstadoListaComponent],
  template: `
    <div class="page-container">
      <div class="toolbar"><h2>Gestión de Roles</h2><button class="btn-new" (click)="abrirCrear()">+ Nuevo rol</button></div>
      <app-estado-lista
        [cargando]="loading"
        [error]="cargaError"
        [vacio]="!loading && !cargaError && roles.length === 0"
        [hayFiltro]="hayFiltroPuesto"
        nombrePlural="roles"
        pistaVacio="Crea el primero con «+ Nuevo»."
        (reintentar)="cargar()"></app-estado-lista>
      <table class="data-table" *ngIf="!loading && !cargaError && roles.length > 0">
        <thead><tr><th>Nombre</th><th>Descripción</th><th>Permisos</th><th>Acciones</th></tr></thead>
        <tbody>
          <tr *ngFor="let r of roles">
            <td><strong>{{r.nombre}}</strong></td><td>{{r.descripcion||'-'}}</td>
            <td>{{r.permisos.length}} permisos</td>
            <td class="actions">
              <button class="btn-icon" (click)="abrirEditar(r)"><app-icon name="edit" [size]="16"/></button>
              <button class="btn-icon danger" (click)="confirmarEliminar(r)"><app-icon name="trash" [size]="16"/></button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="modal-overlay" *ngIf="showForm" appModalSeguro (cerrar)="showForm=false">
        <div class="modal-card wide" (click)="$event.stopPropagation()">
          <h3>{{editId?'Editar':'Nuevo'}} Rol</h3>
          <form (ngSubmit)="guardar()">
            <div class="form-group"><label>Nombre *</label><input [(ngModel)]="form.nombre" name="nombre" required maxlength="50"/></div>
            <div class="form-group"><label>Descripción</label><input [(ngModel)]="form.descripcion" name="desc" maxlength="255"/></div>
            <div class="form-group"><label>Permisos</label>
              <div class="permisos-grid">
                <div *ngFor="let modulo of modulos" class="modulo-group">
                  <div class="modulo-header" (click)="toggleModulo(modulo)">
                    <strong>{{modulo}}</strong>
                    <button type="button" class="btn-select-all" (click)="selectAllModulo(modulo);$event.stopPropagation()">Todos</button>
                  </div>
                  <div class="modulo-permisos">
                    <label *ngFor="let p of permisosPorModulo(modulo)" class="cb-item">
                      <input type="checkbox" [checked]="form.idPermisos.includes(p.idPermiso)" (change)="togglePermiso(p.idPermiso)"/> {{p.accion}}
                    </label>
                  </div>
                </div>
              </div>
            </div>
            <small class="error" *ngIf="formError">{{formError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="showForm=false">Cancelar</button>
              <button type="submit" class="btn-save" [disabled]="saving">{{saving?'Guardando...':'Guardar'}}</button>
            </div>
          </form>
        </div>
      </div>

      <div class="modal-overlay" *ngIf="showConfirm" appModalSeguro (cerrar)="showConfirm=false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Confirmar eliminación</h3>
          <p>¿Eliminar el rol <strong>{{itemEliminar?.nombre}}</strong>?</p>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="showConfirm=false">Cancelar</button>
            <button class="btn-delete" (click)="eliminar()">Confirmar</button>
          </div>
        </div>
      </div>
      <div class="toast" *ngIf="toast" [class.error]="toastErr">{{toast}}</div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class RolesComponent implements OnInit {
  roles: RolResp[] = []; loading = false; saving = false;
  /**
   * Motivo del fallo de carga, o null si la carga fue bien (D6).
   * Sin esto la pantalla no podia distinguir "no hay registros" de "no se
   * pudo preguntar", y enseñaba lo primero en los dos casos.
   */
  cargaError: string | null = null;

  /** ¿Hay busqueda o filtros puestos? Cambia el mensaje de lista vacia. */
  get hayFiltroPuesto(): boolean { return false; }

  todosPermisos: Permiso[] = []; modulos: string[] = [];
  showForm = false; editId: number|null = null;
  form = {nombre:'',descripcion:'',idPermisos:[] as number[]};
  formError = '';
  showConfirm = false; itemEliminar: RolResp|null = null;
  toast = ''; toastErr = false;

  constructor(private http: HttpClient) {}

  ngOnInit() { this.cargar(); this.cargarPermisos(); }

  cargar() {
    this.loading = true;
    this.http.get<RolResp[]>(`${environment.apiUrl}/roles`).subscribe({
      next: r => { this.cargaError = null; this.roles = r; this.loading = false; },
      error: (err: any) => { this.loading = false; this.cargaError = this.motivoDelFallo(err); }
    });
  }

  cargarPermisos() {
    this.http.get<Permiso[]>(`${environment.apiUrl}/permisos`).subscribe(p => {
      this.todosPermisos = p;
      this.modulos = [...new Set(p.map(x => x.modulo))];
    });
  }

  permisosPorModulo(modulo: string) { return this.todosPermisos.filter(p => p.modulo === modulo); }
  toggleModulo(_m: string) {}
  selectAllModulo(modulo: string) {
    const ids = this.permisosPorModulo(modulo).map(p => p.idPermiso);
    const allSelected = ids.every(id => this.form.idPermisos.includes(id));
    if (allSelected) { this.form.idPermisos = this.form.idPermisos.filter(id => !ids.includes(id)); }
    else { ids.forEach(id => { if (!this.form.idPermisos.includes(id)) this.form.idPermisos.push(id); }); }
  }
  togglePermiso(id: number) { const i = this.form.idPermisos.indexOf(id); if (i >= 0) this.form.idPermisos.splice(i, 1); else this.form.idPermisos.push(id); }

  abrirCrear() { this.editId = null; this.form = {nombre:'',descripcion:'',idPermisos:[]}; this.formError = ''; this.showForm = true; }
  abrirEditar(r: RolResp) { this.editId = r.idRol; this.form = {nombre:r.nombre,descripcion:r.descripcion||'',idPermisos:r.permisos.map(p=>p.idPermiso)}; this.formError = ''; this.showForm = true; }

  guardar() {
    if (!this.form.nombre.trim()) { this.formError = 'El nombre es obligatorio'; return; }
    this.saving = true;
    const obs = this.editId
      ? this.http.put<RolResp>(`${environment.apiUrl}/roles/${this.editId}`, this.form)
      : this.http.post<RolResp>(`${environment.apiUrl}/roles`, this.form);
    obs.subscribe({
      next: () => { this.saving = false; this.showForm = false; this.cargar(); this.mostrarToast('Rol guardado correctamente'); },
      error: e => { this.saving = false; this.formError = e.error?.message || 'Error al guardar'; }
    });
  }

  confirmarEliminar(r: RolResp) { this.itemEliminar = r; this.showConfirm = true; }
  eliminar() {
    if (!this.itemEliminar) return;
    this.http.delete(`${environment.apiUrl}/roles/${this.itemEliminar.idRol}`).subscribe({
      next: () => { this.showConfirm = false; this.cargar(); this.mostrarToast('Rol eliminado'); },
      error: e => { this.showConfirm = false; this.mostrarToast(e.error?.message || 'Error', true); }
    });
  }

  mostrarToast(m: string, err = false) { this.toast = m; this.toastErr = err; setTimeout(() => { this.toast = ''; }, 3000); }
  /** Traduce el fallo a algo que se pueda leer y, si se puede, resolver. */
  private motivoDelFallo(err: any): string {
    if (err?.status === 0) return 'No hay conexión con el servidor.';
    if (err?.status === 403) return 'Tu rol no tiene permiso para ver esta información.';
    if (err?.status === 401) return 'Tu sesión ha caducado. Vuelve a entrar.';
    return err?.error?.message ?? 'El servidor no respondió correctamente.';
  }

}
