import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface Permiso { idPermiso:number; modulo:string; accion:string; }
interface RolResp { idRol:number; nombre:string; descripcion:string; permisos:Permiso[]; }

@Component({
  selector: 'app-roles',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="toolbar"><h2>Gestión de Roles</h2><button class="btn-new" (click)="abrirCrear()">+ Nuevo rol</button></div>
      <div class="spinner" *ngIf="loading">Cargando...</div>
      <table class="data-table" *ngIf="!loading">
        <thead><tr><th>Nombre</th><th>Descripción</th><th>Permisos</th><th>Acciones</th></tr></thead>
        <tbody>
          <tr *ngFor="let r of roles">
            <td><strong>{{r.nombre}}</strong></td><td>{{r.descripcion||'-'}}</td>
            <td>{{r.permisos.length}} permisos</td>
            <td class="actions">
              <button class="btn-icon" (click)="abrirEditar(r)">✏️</button>
              <button class="btn-icon danger" (click)="confirmarEliminar(r)">🗑️</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="modal-overlay" *ngIf="showForm" (click)="showForm=false">
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

      <div class="modal-overlay" *ngIf="showConfirm" (click)="showConfirm=false">
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
    .page-container{max-width:1000px;padding:1.5rem}
    .toolbar{display:flex;align-items:center;gap:1rem;margin-bottom:1rem}
    .toolbar h2{color:#2d5a27;flex:1}
    .btn-new{background:#2d5a27;color:#fff;border:none;padding:.5rem 1rem;border-radius:4px;cursor:pointer;font-weight:600}
    .spinner{text-align:center;padding:2rem;color:#666}
    .data-table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.1)}
    .data-table th{background:#2d5a27;color:#fff;padding:.7rem;text-align:left;font-size:.85rem}
    .data-table td{padding:.6rem .7rem;border-bottom:1px solid #eee;font-size:.85rem}
    .data-table tr:hover td{background:#f0f7f0}
    .actions{display:flex;gap:.3rem}
    .btn-icon{background:none;border:none;cursor:pointer;font-size:1rem}
    .modal-overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;z-index:1000}
    .modal-card{background:#fff;border-radius:10px;padding:2rem;width:90%;max-width:420px}
    .modal-card.wide{max-width:600px;max-height:80vh;overflow-y:auto}
    .modal-card h3{color:#2d5a27;margin-bottom:1rem}
    .form-group{margin-bottom:.8rem;display:flex;flex-direction:column;gap:.3rem}
    .form-group label{font-size:.85rem;font-weight:600}
    .form-group input{padding:.6rem;border:1px solid #ddd;border-radius:4px}
    .permisos-grid{max-height:300px;overflow-y:auto;border:1px solid #eee;border-radius:4px;padding:.5rem}
    .modulo-group{margin-bottom:.8rem}
    .modulo-header{display:flex;align-items:center;justify-content:space-between;background:#f5f5f5;padding:.4rem .6rem;border-radius:4px;cursor:pointer}
    .modulo-header strong{font-size:.85rem;text-transform:capitalize}
    .btn-select-all{font-size:.7rem;background:#2d5a27;color:#fff;border:none;padding:.2rem .5rem;border-radius:3px;cursor:pointer}
    .modulo-permisos{display:flex;flex-wrap:wrap;gap:.4rem;padding:.4rem .6rem}
    .cb-item{font-size:.8rem;display:flex;align-items:center;gap:.2rem}
    .error{color:#c00;font-size:.8rem}
    .modal-actions{display:flex;gap:.5rem;justify-content:flex-end;margin-top:1.5rem}
    .btn-cancel{padding:.5rem 1rem;border:1px solid #ddd;border-radius:4px;background:#fff;cursor:pointer}
    .btn-save{padding:.5rem 1rem;border:none;border-radius:4px;background:#2d5a27;color:#fff;cursor:pointer}
    .btn-save:disabled{opacity:.6}
    .btn-delete{padding:.5rem 1rem;border:none;border-radius:4px;background:#c00;color:#fff;cursor:pointer}
    .toast{position:fixed;bottom:2rem;right:2rem;background:#2d5a27;color:#fff;padding:.8rem 1.5rem;border-radius:6px;z-index:9999}
    .toast.error{background:#c00}
  `]
})
export class RolesComponent implements OnInit {
  roles: RolResp[] = []; loading = false; saving = false;
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
      next: r => { this.roles = r; this.loading = false; },
      error: () => { this.loading = false; }
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
}
