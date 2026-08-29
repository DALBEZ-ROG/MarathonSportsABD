import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../../core/services/auth.service';
import { environment } from '../../../../environments/environment';

/**
 * Mi cuenta (F90).
 *
 * <p><b>Qué tenía.</b> Una columna de 500 px en una pantalla de 1.300, con tres
 * datos —nombre, correo, rol— y un formulario de contraseña que no decía
 * ninguna de las reglas que el servidor iba a aplicar: ni que la actual tiene
 * que ser correcta, ni que la nueva son entre 8 y 50 caracteres. Se escribía,
 * se enviaba, y el servidor contestaba que no.
 *
 * <p><b>Qué le faltaba, y estaba a mano.</b> En un sistema donde todo depende
 * del rol, la pregunta que trae a alguien a su perfil no es «¿cómo me llamo?»,
 * es <b>«¿qué puedo hacer yo?»</b>. Esa lista ya viajaba en la sesión —el login
 * devuelve los permisos y se guardan en {@code localStorage}— y no se enseñaba
 * en ninguna parte. Ahora es el bloque principal.
 */
@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="page">

      <header class="p-head">
        <div class="avatar" aria-hidden="true">{{ iniciales }}</div>
        <div class="p-quien">
          <h1>{{ user?.nombre }} {{ user?.apellido }}</h1>
          <p>{{ user?.correo }}</p>
        </div>
        <span class="rol-chip">{{ user?.rol }}</span>
      </header>

      <div class="p-cols">

        <!-- ── Cambiar la contraseña ──────────────────────────────── -->
        <section class="bloque">
          <header class="b-head"><h2>Cambiar mi contraseña</h2></header>

          <!-- Las reglas, ANTES de escribir. Son las que aplica el servidor. -->
          <ul class="reglas">
            <li>Tienes que saber la contraseña <strong>actual</strong>.</li>
            <li [class.ok]="largoOk" [class.mal]="form.passwordNuevo.length > 0 && !largoOk">
              La nueva: entre <strong>8 y 50</strong> caracteres.
              <span *ngIf="form.passwordNuevo.length">({{ form.passwordNuevo.length }})</span>
            </li>
            <li [class.ok]="coinciden" [class.mal]="form.confirmarPassword.length > 0 && !coinciden">
              Escribirla <strong>dos veces</strong> igual.
            </li>
          </ul>

          <form (ngSubmit)="cambiarPassword()">
            <div class="form-group">
              <label for="pa">Contraseña actual</label>
              <input id="pa" [type]="verClaves ? 'text' : 'password'"
                     [(ngModel)]="form.passwordActual" name="pa"
                     autocomplete="current-password" required/>
            </div>
            <div class="form-group">
              <label for="pn">Nueva contraseña</label>
              <input id="pn" [type]="verClaves ? 'text' : 'password'"
                     [(ngModel)]="form.passwordNuevo" name="pn"
                     autocomplete="new-password" minlength="8" maxlength="50" required/>
            </div>
            <div class="form-group">
              <label for="pc">Repetir la nueva</label>
              <input id="pc" [type]="verClaves ? 'text' : 'password'"
                     [(ngModel)]="form.confirmarPassword" name="pc"
                     autocomplete="new-password" maxlength="50" required/>
            </div>

            <label class="ver-claves">
              <input type="checkbox" [(ngModel)]="verClaves" name="ver"/>
              <span>Ver lo que escribo</span>
            </label>

            <small class="error" *ngIf="error">{{ error }}</small>
            <small class="success" *ngIf="success">{{ success }}</small>

            <button type="submit" class="btn-save" [disabled]="saving || !formValido">
              {{ saving ? 'Guardando…' : 'Cambiar contraseña' }}
            </button>
            <p class="falta" *ngIf="!formValido && !saving">{{ queFalta }}</p>
          </form>

          <!-- Lo que NO hace, dicho: es lo que la gente da por hecho. -->
          <p class="b-pie">
            Cambiarla <strong>no cierra</strong> las sesiones que tengas abiertas
            en otro sitio. Para eso, cierra sesión ahí — al cerrar, ese acceso
            queda revocado en el servidor de inmediato.
          </p>
        </section>

        <!-- ── La sesión ──────────────────────────────────────────── -->
        <section class="bloque">
          <header class="b-head"><h2>Mi sesión</h2></header>
          <dl class="datos">
            <dt>Identificador</dt><dd>#{{ user?.idUsuario }}</dd>
            <dt>Rol</dt><dd>{{ user?.rol }}</dd>
            <dt>Caduca</dt>
            <dd [class.pronto]="minutosRestantes !== null && minutosRestantes < 10">
              {{ textoCaducidad }}
            </dd>
          </dl>
          <p class="b-pie">
            La sesión se renueva sola mientras trabajas. Si caduca, la aplicación
            te devuelve al inicio de sesión sin perder lo que hubieras guardado.
          </p>
        </section>
        <!-- ── Lo que puedes hacer ────────────────────────────────── -->
        <section class="bloque ancho">
          <header class="b-head">
            <h2>Lo que puedes hacer</h2>
            <span class="b-cuenta">{{ permisos.length }}
              {{ permisos.length === 1 ? 'permiso' : 'permisos' }}</span>
          </header>
          <p class="b-nota">
            Sale de tu rol, no de tu persona: si cambia el rol, cambia esto. Lo
            decide el administrador desde <strong>Roles</strong>.
          </p>

          <div class="perm-grid" *ngIf="modulos.length; else sinPermisos">
            <div class="perm-mod" *ngFor="let m of modulos">
              <strong>{{ nombreModulo(m) }}</strong>
              <span class="acc" *ngFor="let a of accionesDe(m)">{{ a }}</span>
            </div>
          </div>

          <ng-template #sinPermisos>
            <p class="vacio">
              Tu rol no tiene ningún permiso asignado, así que puedes entrar pero
              no operar. Habla con el administrador.
            </p>
          </ng-template>
        </section>

      </div>
    </div>
  `,
  styles: [`
    .page { padding: clamp(1rem, 2vw, 1.6rem); }

    .p-head { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;
              margin-bottom: 1.4rem; }
    .avatar { width: 56px; height: 56px; border-radius: 50%; flex-shrink: 0;
              display: flex; align-items: center; justify-content: center;
              font-size: 1.15rem; font-weight: 700; color: #1a1a1f;
              background: linear-gradient(135deg, #C9A84C, #F4E28D); }
    .p-quien { flex: 1; min-width: 0; }
    .p-quien h1 { margin: 0; font-size: 1.5rem; }
    .p-quien p { margin: .15rem 0 0; font-size: .86rem; color: var(--ms-text-muted); }
    .rol-chip { padding: .3rem .8rem; border-radius: 999px; font-size: .8rem;
                font-weight: 600; color: var(--ms-gold-light);
                background: var(--ms-gold-dim); border: 1px solid rgba(201,168,76,.3); }

    /* Antes era UNA columna de 500 px en una pantalla de 1.300.
       Dos columnas FIJAS y no auto-fit: el bloque de permisos ocupa todo el
       ancho, y eso impide que auto-fit colapse las pistas vacías — salían
       cuatro columnas con dos llenas y 800 px de nada a la derecha. */
    .p-cols { display: grid; gap: clamp(.8rem, 1.4vw, 1.15rem);
              grid-template-columns: repeat(2, minmax(0, 1fr));
              align-items: start; }
    @media (max-width: 820px) { .p-cols { grid-template-columns: 1fr; } }
    .bloque { padding: 1.15rem 1.25rem; border-radius: 16px;
              border: 1px solid rgba(255,255,255,0.07);
              background: linear-gradient(155deg, rgba(255,255,255,0.045), rgba(255,255,255,0.015)); }
    .bloque.ancho { grid-column: 1 / -1; }

    .b-head { display: flex; align-items: baseline; gap: .6rem; margin-bottom: .3rem; }
    .b-head h2 { margin: 0; font-size: .95rem; font-weight: 600; }
    .b-cuenta { font-size: .76rem; color: var(--ms-gold-light); }
    .b-nota { margin: 0 0 .9rem; font-size: .78rem; line-height: 1.55;
              color: var(--ms-text-muted); }
    .b-nota strong, .b-pie strong { color: rgba(255,255,255,0.8); }
    .b-pie { margin: 1rem 0 0; padding-top: .8rem; font-size: .76rem; line-height: 1.6;
             color: var(--ms-text-muted); border-top: 1px solid rgba(255,255,255,0.06); }

    .perm-grid { display: grid; gap: .55rem;
                 grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr)); }
    .perm-mod { padding: .55rem .7rem; border-radius: 11px;
                background: rgba(255,255,255,0.03); border: 1px solid var(--ms-border); }
    .perm-mod strong { display: block; margin-bottom: .35rem; font-size: .78rem;
                       color: var(--ms-text); }
    .acc { display: inline-block; margin: 0 .25rem .25rem 0; padding: .1rem .45rem;
           border-radius: 6px; font-size: .7rem; color: var(--ms-text-muted);
           background: rgba(255,255,255,0.04); border: 1px solid var(--ms-border); }

    .vacio { margin: 0; font-size: .82rem; line-height: 1.6; color: #fbbf24; }

    .reglas { margin: 0 0 1rem; padding: 0 0 0 1.1rem; }
    .reglas li { font-size: .78rem; line-height: 1.7; color: var(--ms-text-muted); }
    .reglas li strong { color: rgba(255,255,255,0.8); }
    .reglas li.ok { color: #6ee7a0; }
    .reglas li.ok strong { color: #6ee7a0; }
    .reglas li.mal { color: #e79a95; }
    .reglas li.mal strong { color: #e79a95; }

    .ver-claves { display: flex; align-items: center; gap: .45rem; cursor: pointer;
                  margin: .2rem 0 .9rem; font-size: .78rem; color: var(--ms-text-muted); }

    .falta { margin: .6rem 0 0; font-size: .76rem; color: #fbbf24; }

    .datos { display: grid; grid-template-columns: auto 1fr; gap: .4rem .9rem;
             margin: .6rem 0 0; }
    .datos dt { font-size: .76rem; color: var(--ms-text-muted); }
    .datos dd { margin: 0; font-size: .82rem; color: var(--ms-text);
                font-variant-numeric: tabular-nums; }
    .datos dd.pronto { color: #fbbf24; }
  `]
})
export class PerfilComponent {
  user: any;
  form = { passwordActual: '', passwordNuevo: '', confirmarPassword: '' };
  error = ''; success = ''; saving = false;
  verClaves = false;

  constructor(private authService: AuthService, private http: HttpClient) {
    this.user = this.authService.getCurrentUser();
  }

  get iniciales(): string {
    const n = (this.user?.nombre ?? '').charAt(0);
    const a = (this.user?.apellido ?? '').charAt(0);
    return (n + a).toUpperCase() || '?';
  }

  // -----------------------------------------------------------------------
  // Permisos. Vienen como "modulo:accion" en la sesión.
  // -----------------------------------------------------------------------

  get permisos(): string[] { return this.user?.permisos ?? []; }

  get modulos(): string[] {
    return [...new Set(this.permisos.map(p => p.split(':')[0]))].sort();
  }

  accionesDe(modulo: string): string[] {
    return this.permisos
      .filter(p => p.startsWith(modulo + ':'))
      .map(p => p.split(':')[1])
      .sort();
  }

  /** Siglas que quedan mal con solo la primera en mayuscula: «Ia», «Bom». */
  private static readonly SIGLAS: Record<string, string> = { ia: 'IA', bom: 'BOM' };

  /** `analisis_costos` se lee peor que `Analisis costos`. */
  nombreModulo(m: string): string {
    const sigla = PerfilComponent.SIGLAS[m];
    if (sigla) { return sigla; }
    const limpio = m.replace(/_/g, ' ');
    return limpio.charAt(0).toUpperCase() + limpio.slice(1);
  }

  // -----------------------------------------------------------------------
  // La sesión
  // -----------------------------------------------------------------------

  /** Minutos que faltan, o null si no se sabe. */
  get minutosRestantes(): number | null {
    const expira = Number(localStorage.getItem('marathon_expira'));
    if (!expira) { return null; }
    return Math.max(0, Math.round((expira - Date.now()) / 60000));
  }

  get textoCaducidad(): string {
    const m = this.minutosRestantes;
    if (m === null) { return 'no se sabe'; }
    if (m === 0) { return 'ya ha caducado'; }
    if (m < 60) { return 'en ' + m + (m === 1 ? ' minuto' : ' minutos'); }
    const h = Math.floor(m / 60);
    return 'en ' + h + (h === 1 ? ' hora' : ' horas');
  }

  // -----------------------------------------------------------------------
  // El formulario. Las mismas reglas que el servidor, dichas antes.
  // -----------------------------------------------------------------------

  get largoOk(): boolean {
    const n = this.form.passwordNuevo.length;
    return n >= 8 && n <= 50;
  }

  get coinciden(): boolean {
    return this.form.passwordNuevo.length > 0
        && this.form.passwordNuevo === this.form.confirmarPassword;
  }

  get formValido(): boolean {
    return !!this.form.passwordActual && this.largoOk && this.coinciden;
  }

  /** Decir qué falta, en vez de dejar el botón apagado sin explicación. */
  get queFalta(): string {
    if (!this.form.passwordActual) { return 'Falta la contraseña actual.'; }
    if (!this.largoOk) { return 'La nueva tiene que medir entre 8 y 50 caracteres.'; }
    if (!this.coinciden) { return 'Las dos nuevas no coinciden.'; }
    return '';
  }

  cambiarPassword() {
    this.error = ''; this.success = '';
    if (!this.formValido) { this.error = this.queFalta; return; }
    this.saving = true;
    this.http.put(`${environment.apiUrl}/usuarios/${this.user.idUsuario}/password`, this.form).subscribe({
      next: () => {
        this.saving = false;
        this.success = 'Contraseña actualizada. La próxima vez entra con la nueva.';
        this.form = { passwordActual: '', passwordNuevo: '', confirmarPassword: '' };
        this.verClaves = false;
      },
      error: e => { this.saving = false; this.error = e.error?.message || 'No se pudo cambiar la contraseña.'; }
    });
  }
}
