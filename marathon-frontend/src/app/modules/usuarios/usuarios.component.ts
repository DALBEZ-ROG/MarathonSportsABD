import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CrudService, PageResponse } from '../../core/services/crud.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface Rol { idRol: number; nombre: string; }
interface UsuarioResp { idUsuario: number; nombre: string; apellido: string; correo: string; estado: string; roles: Rol[]; }

@Component({
  selector: 'app-usuarios',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page-container">
      <div class="toolbar">
        <h2>Gestión de Usuarios</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="filtroNombre" (input)="onSearch()" placeholder="Buscar por nombre..." class="input-search"/>
          <select [(ngModel)]="filtroEstado" (change)="cargar()" class="select-filter">
            <option value="">Todos</option><option value="activo">Activo</option><option value="inactivo">Inactivo</option>
          </select>
        </div>
        <button class="btn-new" (click)="abrirCrear()">+ Nuevo usuario</button>
      </div>
      <div class="spinner" *ngIf="loading">Cargando...</div>
      <table class="data-table" *ngIf="!loading">
        <thead><tr><th>Nombre</th><th>Correo</th><th>Roles</th><th>Estado</th><th>Acciones</th></tr></thead>
        <tbody>
          <tr *ngFor="let u of data">
            <td>{{u.nombre}} {{u.apellido}}</td>
            <td>{{u.correo}}</td>
            <td><span class="role-badge" *ngFor="let r of u.roles">{{r.nombre}}</span></td>
            <td><span class="badge" [class.active]="u.estado==='activo'">{{u.estado}}</span></td>
            <td class="actions">
              <button class="btn-icon" (click)="abrirEditar(u)" title="Editar">✏️</button>
              <button class="btn-icon" (click)="abrirCambiarPass(u)" title="Contraseña">🔑</button>
              <button class="btn-icon danger" (click)="confirmarDesactivar(u)" title="Desactivar" *ngIf="u.estado==='activo'">🚫</button>
            </td>
          </tr>
          <tr *ngIf="data.length===0"><td colspan="5" class="empty">No hay registros</td></tr>
        </tbody>
      </table>
      <div class="pagination" *ngIf="totalPages > 1">
        <button (click)="page=page-1;cargar()" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="page=page+1;cargar()" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <!-- Modal Crear/Editar -->
      <div class="modal-overlay" *ngIf="showForm" (click)="showForm=false">
        <div class="modal-card wide" (click)="$event.stopPropagation()">
          <h3>{{editId ? 'Editar' : 'Nuevo'}} Usuario</h3>
          <form (ngSubmit)="guardar()">
            <div class="form-row">
              <div class="form-group"><label>Nombre *</label><input [(ngModel)]="form.nombre" name="nombre" required/></div>
              <div class="form-group"><label>Apellido *</label><input [(ngModel)]="form.apellido" name="apellido" required/></div>
            </div>
            <div class="form-group"><label>Correo *</label><input type="email" [(ngModel)]="form.correo" name="correo" required/></div>
            <div class="form-group" *ngIf="!editId"><label>Contraseña *</label>
              <div class="pass-wrap"><input [type]="showPass?'text':'password'" [(ngModel)]="form.password" name="password" minlength="8"/>
              <button type="button" class="btn-eye" (click)="showPass=!showPass">{{showPass?'🙈':'👁️'}}</button></div>
            </div>
            <div class="form-group"><label>Estado</label>
              <select [(ngModel)]="form.estado" name="estado"><option value="activo">Activo</option><option value="inactivo">Inactivo</option></select>
            </div>
            <div class="form-group"><label>Roles *</label>
              <div class="checkbox-list">
                <label *ngFor="let r of rolesDisponibles" class="cb-item">
                  <input type="checkbox" [checked]="form.idRoles.includes(r.idRol)" (change)="toggleRol(r.idRol)"/> {{r.nombre}}
                </label>
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

      <!-- Modal Cambiar Password -->
      <div class="modal-overlay" *ngIf="showPassModal" (click)="showPassModal=false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Cambiar contraseña</h3>
          <form (ngSubmit)="guardarPassword()">
            <div class="form-group"><label>Contraseña actual</label><input type="password" [(ngModel)]="passForm.passwordActual" name="pa" required/></div>
            <div class="form-group"><label>Nueva contraseña</label><input type="password" [(ngModel)]="passForm.passwordNuevo" name="pn" required minlength="8"/></div>
            <div class="form-group"><label>Confirmar</label><input type="password" [(ngModel)]="passForm.confirmarPassword" name="pc" required/></div>
            <small class="error" *ngIf="passError">{{passError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="showPassModal=false">Cancelar</button>
              <button type="submit" class="btn-save" [disabled]="saving">Cambiar</button>
            </div>
          </form>
        </div>
      </div>

      <!-- Confirm Desactivar -->
      <div class="modal-overlay" *ngIf="showConfirm" (click)="showConfirm=false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Confirmar desactivación</h3>
          <p>¿Desactivar a <strong>{{itemDesactivar?.nombre}} {{itemDesactivar?.apellido}}</strong>? El usuario no podrá iniciar sesión.</p>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="showConfirm=false">Cancelar</button>
            <button class="btn-delete" (click)="desactivar()">Confirmar</button>
          </div>
        </div>
      </div>
      <div class="toast" *ngIf="toast" [class.error]="toastErr">{{toast}}</div>
    </div>
  `,
  styles: [`
    .page-container{max-width:1000px;padding:1.5rem}
    .toolbar{display:flex;align-items:center;gap:1rem;flex-wrap:wrap;margin-bottom:1rem}
    .toolbar h2{color:#2d5a27;flex:1}
    .filters{display:flex;gap:.5rem}
    .input-search,.select-filter{padding:.5rem;border:1px solid #ddd;border-radius:4px;font-size:.85rem}
    .input-search{width:180px}
    .btn-new{background:#2d5a27;color:#fff;border:none;padding:.5rem 1rem;border-radius:4px;cursor:pointer;font-weight:600}
    .spinner{text-align:center;padding:2rem;color:#666}
    .data-table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.1)}
    .data-table th{background:#2d5a27;color:#fff;padding:.7rem;text-align:left;font-size:.85rem}
    .data-table td{padding:.6rem .7rem;border-bottom:1px solid #eee;font-size:.85rem}
    .data-table tr:hover td{background:#f0f7f0}
    .role-badge{background:#e8f5e9;color:#2d5a27;padding:.15rem .5rem;border-radius:10px;font-size:.75rem;margin-right:.3rem}
    .badge{padding:.2rem .6rem;border-radius:12px;font-size:.75rem;background:#eee}
    .badge.active{background:#c8e6c9;color:#2d5a27}
    .actions{display:flex;gap:.3rem}
    .btn-icon{background:none;border:none;cursor:pointer;font-size:1rem}
    .empty{text-align:center;color:#999;padding:2rem!important}
    .pagination{display:flex;align-items:center;justify-content:center;gap:1rem;margin-top:1rem}
    .pagination button{padding:.4rem .8rem;border:1px solid #ddd;border-radius:4px;background:#fff;cursor:pointer}
    .pagination button:disabled{opacity:.5;cursor:not-allowed}
    .modal-overlay{position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,.5);display:flex;align-items:center;justify-content:center;z-index:1000}
    .modal-card{background:#fff;border-radius:10px;padding:2rem;width:90%;max-width:420px}
    .modal-card.wide{max-width:520px}
    .modal-card h3{color:#2d5a27;margin-bottom:1rem}
    .form-row{display:flex;gap:1rem}
    .form-row .form-group{flex:1}
    .form-group{margin-bottom:.8rem;display:flex;flex-direction:column;gap:.3rem}
    .form-group label{font-size:.85rem;font-weight:600}
    .form-group input,.form-group select{padding:.6rem;border:1px solid #ddd;border-radius:4px}
    .pass-wrap{display:flex;gap:.3rem}
    .pass-wrap input{flex:1}
    .btn-eye{background:none;border:1px solid #ddd;border-radius:4px;cursor:pointer;padding:.3rem .5rem}
    .checkbox-list{display:flex;flex-wrap:wrap;gap:.5rem}
    .cb-item{font-size:.85rem;display:flex;align-items:center;gap:.3rem}
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
export class UsuariosComponent implements OnInit {
  data: UsuarioResp[] = []; loading = false; saving = false;
  page = 0; size = 10; totalPages = 0;
  filtroNombre = ''; filtroEstado = '';
  showForm = false; editId: number|null = null; showPass = false;
  form = {nombre:'',apellido:'',correo:'',password:'',estado:'activo',idRoles:[] as number[]};
  formError = '';
  rolesDisponibles: Rol[] = [];
  showPassModal = false; passUsuarioId: number|null = null;
  passForm = {passwordActual:'',passwordNuevo:'',confirmarPassword:''};
  passError = '';
  showConfirm = false; itemDesactivar: UsuarioResp|null = null;
  toast = ''; toastErr = false;
  private searchTimeout: any;

  constructor(private crud: CrudService, private http: HttpClient) {}

  ngOnInit() { this.cargar(); this.cargarRoles(); }

  cargar() {
    this.loading = true;
    const p: Record<string,string|number> = {page:this.page,size:this.size};
    if(this.filtroNombre) p['nombre']=this.filtroNombre;
    if(this.filtroEstado) p['estado']=this.filtroEstado;
    this.crud.listar<UsuarioResp>('usuarios',p).subscribe({
      next:r=>{this.data=r.content;this.totalPages=r.totalPages;this.loading=false},
      error:()=>{this.loading=false}
    });
  }

  cargarRoles() {
    this.http.get<any[]>(`${environment.apiUrl}/roles`).subscribe(r => this.rolesDisponibles = r);
  }

  onSearch(){clearTimeout(this.searchTimeout);this.searchTimeout=setTimeout(()=>{this.page=0;this.cargar()},300)}

  abrirCrear(){this.editId=null;this.form={nombre:'',apellido:'',correo:'',password:'',estado:'activo',idRoles:[]};this.formError='';this.showForm=true}
  abrirEditar(u:UsuarioResp){this.editId=u.idUsuario;this.form={nombre:u.nombre,apellido:u.apellido,correo:u.correo,password:'',estado:u.estado,idRoles:u.roles.map(r=>r.idRol)};this.formError='';this.showForm=true}

  toggleRol(id:number){const i=this.form.idRoles.indexOf(id);if(i>=0)this.form.idRoles.splice(i,1);else this.form.idRoles.push(id)}

  guardar(){
    if(!this.form.nombre||!this.form.apellido||!this.form.correo){this.formError='Complete todos los campos obligatorios';return}
    if(!this.editId&&(!this.form.password||this.form.password.length<8)){this.formError='La contraseña debe tener al menos 8 caracteres';return}
    if(this.form.idRoles.length===0){this.formError='Seleccione al menos un rol';return}
    this.saving=true;
    const body:any={nombre:this.form.nombre,apellido:this.form.apellido,correo:this.form.correo,estado:this.form.estado,idRoles:this.form.idRoles};
    if(!this.editId) body.password=this.form.password;
    const obs=this.editId?this.crud.actualizar<any>('usuarios',this.editId,body):this.crud.crear<any>('usuarios',body);
    obs.subscribe({
      next:()=>{this.saving=false;this.showForm=false;this.cargar();this.mostrarToast('Usuario guardado correctamente')},
      error:e=>{this.saving=false;this.formError=e.error?.message||'Error al guardar'}
    });
  }

  abrirCambiarPass(u:UsuarioResp){this.passUsuarioId=u.idUsuario;this.passForm={passwordActual:'',passwordNuevo:'',confirmarPassword:''};this.passError='';this.showPassModal=true}
  guardarPassword(){
    if(this.passForm.passwordNuevo!==this.passForm.confirmarPassword){this.passError='Las contraseñas no coinciden';return}
    this.saving=true;
    this.http.put(`${environment.apiUrl}/usuarios/${this.passUsuarioId}/password`,this.passForm).subscribe({
      next:()=>{this.saving=false;this.showPassModal=false;this.mostrarToast('Contraseña actualizada')},
      error:e=>{this.saving=false;this.passError=e.error?.message||'Error'}
    });
  }

  confirmarDesactivar(u:UsuarioResp){this.itemDesactivar=u;this.showConfirm=true}
  desactivar(){
    if(!this.itemDesactivar)return;
    this.crud.eliminar('usuarios',this.itemDesactivar.idUsuario).subscribe({
      next:()=>{this.showConfirm=false;this.cargar();this.mostrarToast('Usuario desactivado')},
      error:e=>{this.showConfirm=false;this.mostrarToast(e.error?.message||'Error',true)}
    });
  }

  mostrarToast(m:string,err=false){this.toast=m;this.toastErr=err;setTimeout(()=>{this.toast=''},3000)}
}
