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
        <div class="modal-card rol-modal" (click)="$event.stopPropagation()">

          <header class="rm-head">
            <h3>{{editId ? 'Editar rol' : 'Nuevo rol'}}</h3>
            <p>Un rol es <strong>lo que alguien puede hacer</strong>. Se le asigna a
               personas desde Usuarios; aquí solo se define.</p>
          </header>

          <form (ngSubmit)="guardar()">

            <div class="rm-datos">
              <div class="form-group">
                <label>Nombre *</label>
                <input [(ngModel)]="form.nombre" name="nombre" required maxlength="50"
                       placeholder="Jefe de tienda"/>
              </div>
              <div class="form-group">
                <label>Descripción</label>
                <input [(ngModel)]="form.descripcion" name="desc" maxlength="255"
                       placeholder="Para qué sirve este rol"/>
              </div>
            </div>

            <!-- ── Lo que el servidor va a aplicar, dicho ANTES ──────────
                 51 reglas de SecurityConfig van por NOMBRE de rol, no por
                 permiso. Un rol nuevo no llega a esas pantallas por muchos
                 permisos que se le marquen aquí, y eso no se veía por
                 ninguna parte: se descubría al usarlo. -->
            <div class="rm-aviso" *ngIf="!editId">
              <strong>Un rol nuevo no lo abre todo.</strong>
              Los permisos de aquí abren las operaciones, pero <strong>51 pantallas
              están atadas a los seis roles que ya existen</strong> por su nombre.
              Un rol nuevo no entrará en esas aunque le marques todo: hace falta
              tocar la configuración del servidor.
            </div>

            <div class="rm-perm-head">
              <label class="rm-tit">Permisos</label>
              <span class="rm-cuenta" [class.cero]="!form.idPermisos.length">
                {{ form.idPermisos.length }} de {{ todosPermisos.length }}
              </span>
              <input type="text" class="rm-buscar" [(ngModel)]="busqueda" name="busqueda"
                     placeholder="Filtrar por módulo o acción…" autocomplete="off"/>
              <button type="button" class="rm-mini" (click)="marcarTodo()">Marcar todo</button>
              <button type="button" class="rm-mini" (click)="quitarTodo()"
                      [disabled]="!form.idPermisos.length">Quitar todo</button>
            </div>

            <!-- Partir de un rol que ya funciona ahorra 95 casillas. -->
            <div class="rm-copiar" *ngIf="roles.length">
              <span>Copiar los permisos de:</span>
              <button type="button" *ngFor="let r of roles" (click)="copiarDe(r)"
                      [title]="r.permisos.length + ' permisos'">{{ r.nombre }}</button>
            </div>

            <div class="rm-grid">
              <div *ngFor="let modulo of modulosVisibles" class="rm-modulo"
                   [class.algo]="marcadosDe(modulo) > 0">
                <div class="rm-mod-head">
                  <strong>{{ nombreModulo(modulo) }}</strong>
                  <span class="rm-mod-n">{{ marcadosDe(modulo) }}/{{ permisosPorModulo(modulo).length }}</span>
                  <button type="button" class="rm-mini" (click)="selectAllModulo(modulo)">
                    {{ todosMarcados(modulo) ? 'Ninguno' : 'Todos' }}
                  </button>
                </div>
                <label *ngFor="let p of permisosVisiblesDe(modulo)" class="rm-cb">
                  <input type="checkbox" [checked]="form.idPermisos.includes(p.idPermiso)"
                         (change)="togglePermiso(p.idPermiso)"/>
                  <span>{{ p.accion }}</span>
                </label>
              </div>

              <p class="rm-nada" *ngIf="!modulosVisibles.length">
                Ningún permiso coincide con «{{ busqueda }}».
              </p>
            </div>

            <p class="rm-sin" *ngIf="!form.idPermisos.length">
              Sin ningún permiso marcado, quien tenga este rol podrá entrar y no
              podrá hacer nada. Se puede guardar así, pero conviene saberlo.
            </p>

            <small class="error" *ngIf="formError">{{formError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="showForm=false">Cancelar</button>
              <button type="submit" class="btn-save" [disabled]="saving">
                {{ saving ? 'Guardando…' : (editId ? 'Guardar cambios' : 'Crear rol') }}
              </button>
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
    /* ── F89: la ventana de rol ──────────────────────────────────────────
       Antes: 31 módulos y 95 casillas en una caja de dos columnas y 320 px
       de alto, con los nombres en crudo (ANALISIS_COSTOS), sin buscador y
       sin decir cuántos llevabas marcados. Encontrar uno era recorrerlos.
       Ahora la ventana usa el ancho de la pantalla, se filtra escribiendo,
       lleva la cuenta, y se puede partir de un rol que ya funciona. */
    .rol-modal { width: min(1040px, 94vw); max-height: 92vh;
                 display: flex; flex-direction: column; }
    .rol-modal form { display: flex; flex-direction: column; min-height: 0; }

    .rm-head h3 { margin: 0 0 .3rem; }
    .rm-head p { margin: 0 0 1.1rem; font-size: .84rem; line-height: 1.6;
                 color: var(--ms-text-muted); }
    .rm-head strong { color: var(--ms-text); }

    .rm-datos { display: grid; grid-template-columns: minmax(0,1fr) minmax(0,1.6fr);
                gap: .9rem; }
    @media (max-width: 720px) { .rm-datos { grid-template-columns: 1fr; } }

    .rm-aviso { margin: .3rem 0 1rem; padding: .7rem .9rem; border-radius: 10px;
                font-size: .8rem; line-height: 1.6; color: rgba(255,255,255,0.62);
                background: rgba(224,166,60,0.08);
                border: 1px solid rgba(224,166,60,0.28); }
    .rm-aviso strong { color: #e9be6a; }

    .rm-perm-head { display: flex; align-items: center; gap: .5rem; flex-wrap: wrap;
                    margin-bottom: .6rem; }
    .rm-tit { font-size: .78rem; letter-spacing: .05em; text-transform: uppercase;
              color: var(--ms-text-muted); }
    .rm-cuenta { font-size: .78rem; font-weight: 600; color: var(--ms-gold-light);
                 padding: .12rem .5rem; border-radius: 999px;
                 background: var(--ms-gold-dim); border: 1px solid rgba(201,168,76,.3); }
    .rm-cuenta.cero { color: var(--ms-text-muted); background: rgba(255,255,255,0.04);
                      border-color: var(--ms-border); }
    .rm-buscar { flex: 1 1 220px; min-width: 160px; min-height: 34px;
                 padding: 0 .7rem; border-radius: 9px; font-size: .8rem;
                 font-family: inherit; color: var(--ms-text);
                 background: rgba(255,255,255,0.04); border: 1px solid var(--ms-border); }
    .rm-buscar:focus { outline: none; border-color: var(--ms-gold); }

    .rm-mini { cursor: pointer; font-family: inherit; font-size: .72rem;
               padding: .28rem .6rem; border-radius: 7px;
               color: var(--ms-text-muted); background: rgba(255,255,255,0.04);
               border: 1px solid var(--ms-border); }
    .rm-mini:hover:not(:disabled) { border-color: var(--ms-gold); color: var(--ms-gold); }
    .rm-mini:disabled { opacity: .4; cursor: default; }

    .rm-copiar { display: flex; align-items: center; gap: .4rem; flex-wrap: wrap;
                 margin-bottom: .7rem; font-size: .76rem; color: var(--ms-text-muted); }
    .rm-copiar button { cursor: pointer; font-family: inherit; font-size: .74rem;
                        padding: .25rem .6rem; border-radius: 999px;
                        color: var(--ms-text-muted); background: rgba(255,255,255,0.03);
                        border: 1px solid var(--ms-border); }
    .rm-copiar button:hover { border-color: var(--ms-gold); color: var(--ms-gold); }

    /* Cuatro columnas en pantalla ancha: los 31 módulos caben en 8 filas en
       vez de 16, y se ve el conjunto sin arrastrar. */
    .rm-grid { flex: 1; min-height: 0; overflow-y: auto; padding: .2rem;
               display: grid; gap: .6rem; align-items: start;
               grid-template-columns: repeat(auto-fill, minmax(min(100%, 210px), 1fr)); }

    .rm-modulo { padding: .6rem .7rem; border-radius: 11px;
                 background: rgba(255,255,255,0.02);
                 border: 1px solid var(--ms-border); }
    .rm-modulo.algo { border-color: rgba(201,168,76,.35);
                      background: rgba(201,168,76,.05); }
    .rm-mod-head { display: flex; align-items: center; gap: .4rem; margin-bottom: .4rem; }
    .rm-mod-head strong { flex: 1; min-width: 0; font-size: .78rem;
                          color: var(--ms-text); overflow: hidden;
                          text-overflow: ellipsis; white-space: nowrap; }
    .rm-mod-n { font-size: .68rem; color: var(--ms-text-muted);
                font-variant-numeric: tabular-nums; }

    .rm-cb { display: flex; align-items: center; gap: .45rem; cursor: pointer;
             padding: .16rem 0; font-size: .78rem; color: var(--ms-text-muted); }
    .rm-cb:hover { color: var(--ms-text); }
    .rm-cb input { cursor: pointer; }

    .rm-nada { grid-column: 1 / -1; margin: 1.4rem 0; text-align: center;
               font-size: .84rem; color: var(--ms-text-muted); }

    .rm-sin { margin: .8rem 0 0; font-size: .78rem; line-height: 1.55;
              color: #fbbf24; }
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
  /** Filtro escrito en la ventana de permisos (F89). */
  busqueda = '';
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

  // ---------------------------------------------------------------------
  // F89 — 95 permisos en 31 módulos no se recorren a ojo.
  // ---------------------------------------------------------------------

  /** Acentos fuera y a minúsculas: buscar «análisis» debe encontrar «analisis». */
  private plano(t: string): string {
    return (t ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  }

  /** `analisis_costos` se lee peor que `Análisis costos`. */
  nombreModulo(m: string): string {
    const limpio = m.replace(/_/g, ' ');
    return limpio.charAt(0).toUpperCase() + limpio.slice(1);
  }

  /** Los permisos de un módulo que pasan el filtro escrito. */
  permisosVisiblesDe(modulo: string): Permiso[] {
    const q = this.plano(this.busqueda).trim();
    if (!q) { return this.permisosPorModulo(modulo); }
    // Si el módulo entero coincide, se enseñan todas sus acciones: buscar
    // «pedidos» debe dar el bloque completo, no solo la acción llamada así.
    if (this.plano(modulo).includes(q)) { return this.permisosPorModulo(modulo); }
    return this.permisosPorModulo(modulo).filter(p => this.plano(p.accion).includes(q));
  }

  get modulosVisibles(): string[] {
    return this.modulos.filter(m => this.permisosVisiblesDe(m).length > 0);
  }

  marcadosDe(modulo: string): number {
    return this.permisosPorModulo(modulo).filter(p => this.form.idPermisos.includes(p.idPermiso)).length;
  }

  todosMarcados(modulo: string): boolean {
    const ps = this.permisosPorModulo(modulo);
    return ps.length > 0 && ps.every(p => this.form.idPermisos.includes(p.idPermiso));
  }

  marcarTodo() { this.form.idPermisos = this.todosPermisos.map(p => p.idPermiso); }
  quitarTodo() { this.form.idPermisos = []; }

  /**
   * Parte de un rol que ya funciona.
   *
   * <p>Es lo que se hace de verdad al crear un rol: casi nunca se empieza de
   * cero, se quiere «como el de bodega pero sin anular». Marcar 40 casillas a
   * mano para eso es trabajo que no aporta nada.
   */
  copiarDe(r: RolResp) {
    this.form.idPermisos = r.permisos.map(p => p.idPermiso);
  }

  selectAllModulo(modulo: string) {
    const ids = this.permisosPorModulo(modulo).map(p => p.idPermiso);
    const allSelected = ids.every(id => this.form.idPermisos.includes(id));
    if (allSelected) { this.form.idPermisos = this.form.idPermisos.filter(id => !ids.includes(id)); }
    else { ids.forEach(id => { if (!this.form.idPermisos.includes(id)) this.form.idPermisos.push(id); }); }
  }
  togglePermiso(id: number) { const i = this.form.idPermisos.indexOf(id); if (i >= 0) this.form.idPermisos.splice(i, 1); else this.form.idPermisos.push(id); }

  abrirCrear() { this.editId = null; this.form = {nombre:'',descripcion:'',idPermisos:[]}; this.formError = ''; this.busqueda = ''; this.showForm = true; }
  abrirEditar(r: RolResp) { this.editId = r.idRol; this.form = {nombre:r.nombre,descripcion:r.descripcion||'',idPermisos:r.permisos.map(p=>p.idPermiso)}; this.formError = ''; this.busqueda = ''; this.showForm = true; }

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
