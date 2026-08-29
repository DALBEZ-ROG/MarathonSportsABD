import { AfterViewChecked, Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AsistenteService } from '../../../core/services/asistente.service';
import { AuthService } from '../../../core/services/auth.service';

/**
 * El asistente, a mano desde cualquier pantalla (F88).
 *
 * <p><b>Cómo se comporta, y por qué así.</b> Plegado es una burbuja en la
 * esquina. Desplegado es un panel que <b>no se cierra</b>: no tiene aspa, no se
 * cierra al pulsar fuera y no se cierra al cambiar de pantalla. La única salida
 * es <b>minimizar</b>, que lo devuelve a burbuja — y la conversación sigue
 * entera, porque vive en {@link AsistenteService} y no aquí.
 *
 * <p>Eso es deliberado: la gracia de tener el asistente al lado es poder
 * preguntarle algo, ir a mirar la pantalla de la que hablas y volver sin haber
 * perdido el hilo. Un panel que se cierra al hacer clic fuera no sirve para eso.
 *
 * <p>El botón de la esquina abre la <b>pantalla completa</b> (<code>/ia</code>),
 * que es la misma conversación con sitio para leer tablas grandes. Por eso el
 * panel se esconde cuando ya estás en <code>/ia</code>: sería el asistente
 * encima del asistente.
 */
@Component({
  selector: 'app-asistente-burbuja',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <ng-container *ngIf="visible">

      <!-- ── Plegado: la burbuja ─────────────────────────────────── -->
      <button type="button" class="burbuja" *ngIf="!a.abierto" (click)="a.abrir()"
              title="Preguntar al asistente" aria-label="Abrir el asistente">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 01-.9-3.8 8.5 8.5 0 014.7-7.6 8.38 8.38 0 013.8-.9h.5a8.48 8.48 0 018 8v.5z"/>
        </svg>
        <span class="burbuja-punto" *ngIf="a.mensajes.length" [attr.aria-label]="a.mensajes.length + ' mensajes'"></span>
      </button>

      <!-- ── Desplegado: el panel ────────────────────────────────── -->
      <section class="panel" *ngIf="a.abierto" role="dialog" aria-label="Asistente">

        <header class="p-head">
          <span class="p-icono" aria-hidden="true">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 11.5a8.38 8.38 0 01-.9 3.8 8.5 8.5 0 01-7.6 4.7 8.38 8.38 0 01-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 01-.9-3.8 8.5 8.5 0 014.7-7.6 8.38 8.38 0 013.8-.9h.5a8.48 8.48 0 018 8v.5z"/>
            </svg>
          </span>
          <div class="p-titulo">
            <strong>Asistente</strong>
            <span class="p-est" [class.on]="a.estado === 'encendido'">
              {{ a.estado === 'encendido' ? 'listo' : a.estado === 'comprobando' ? 'comprobando…' : 'apagado' }}
            </span>
          </div>

          <button type="button" class="p-btn" *ngIf="a.mensajes.length" (click)="a.limpiar()"
                  title="Vaciar la conversación" aria-label="Vaciar la conversación">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6"/>
              <path d="M19 6l-1 14a2 2 0 01-2 2H8a2 2 0 01-2-2L5 6M10 11v6M14 11v6"/>
            </svg>
          </button>

          <!-- El botoncito que pidió el dueño: abre la pantalla completa. -->
          <a class="p-btn" routerLink="/ia" (click)="a.minimizar()"
             title="Abrir en pantalla completa" aria-label="Abrir el asistente en pantalla completa">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 3 21 3 21 9"/><polyline points="9 21 3 21 3 15"/>
              <line x1="21" y1="3" x2="14" y2="10"/><line x1="3" y1="21" x2="10" y2="14"/>
            </svg>
          </a>

          <!-- Minimizar es la UNICA salida: no hay aspa a proposito. -->
          <button type="button" class="p-btn" (click)="a.minimizar()"
                  title="Minimizar" aria-label="Minimizar el asistente">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
          </button>
        </header>

        <div class="p-cuerpo" #cuerpo>

          <!-- Apagado -->
          <div class="p-aviso" *ngIf="a.estado === 'apagado'">
            <strong>El asistente está apagado</strong>
            <span>Se enciende en el servidor con <code>app.ia.enabled=true</code>.</span>
          </div>

          <!-- Sin conversación todavía -->
          <div class="p-vacio" *ngIf="a.estado === 'encendido' && !a.mensajes.length">
            <p>Pregúntale en castellano por los datos del sistema. Solo lee; nunca escribe.</p>
            <button type="button" class="p-ejemplo" *ngFor="let e of a.ejemplos.slice(0, 3)"
                    (click)="enviarTexto(e)">{{ e }}</button>
          </div>

          <!-- Conversación -->
          <div class="msg" *ngFor="let m of a.mensajes" [class.mio]="m.tipo === 'usuario'">
            <p class="m-txt" *ngIf="m.tipo === 'usuario'">{{ m.texto }}</p>

            <div class="m-ia" *ngIf="m.tipo === 'ia' && m.respuesta as r">
              <p class="m-err" *ngIf="r.error">{{ r.error }}</p>

              <ng-container *ngIf="!r.error">
                <p class="m-exp" *ngIf="r.explicacion">{{ r.explicacion }}</p>

                <div class="m-tabla" *ngIf="r.resultados?.length">
                  <table>
                    <thead>
                      <tr><th *ngFor="let c of a.columnas(r.resultados!)">{{ a.titulo(c) }}</th></tr>
                    </thead>
                    <tbody>
                      <tr *ngFor="let f of r.resultados!.slice(0, 8)">
                        <td *ngFor="let c of a.columnas(r.resultados!)"
                            [class.num]="a.esNumerica(r.resultados!, c)">{{ a.celda(f[c]) }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <p class="m-mas" *ngIf="(r.totalResultados ?? 0) > 8">
                    {{ r.totalResultados }} filas · <a routerLink="/ia" (click)="a.minimizar()">verlas todas</a>
                  </p>
                </div>

                <p class="m-vacio" *ngIf="r.resultados && !r.resultados.length">
                  La consulta no devolvió ninguna fila.
                </p>
              </ng-container>
            </div>
          </div>

          <div class="m-pensando" *ngIf="a.cargando"><span></span><span></span><span></span></div>
        </div>

        <form class="p-pie" (ngSubmit)="enviar()">
          <input type="text" [(ngModel)]="pregunta" name="pregunta"
                 [disabled]="a.estado !== 'encendido' || a.cargando"
                 [placeholder]="a.estado === 'encendido' ? 'Pregunta algo…' : 'No disponible'"
                 aria-label="Pregunta para el asistente"/>
          <button type="submit" [disabled]="a.estado !== 'encendido' || a.cargando || !pregunta.trim()"
                  aria-label="Enviar">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </button>
        </form>
      </section>
    </ng-container>
  `,
  styles: [`
    /* z-index 1200: por encima de la barra lateral (300) y de los modales
       (1000). El asistente tiene que seguir accesible con un modal abierto,
       porque justo entonces es cuando surge la duda. */
    .burbuja, .panel { position: fixed; right: 22px; bottom: 22px; z-index: 1200; }

    .burbuja {
      width: 56px; height: 56px; border-radius: 50%; cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      color: #1a1a1f; border: none;
      background: linear-gradient(135deg, #C9A84C, #F4E28D);
      box-shadow: 0 8px 26px rgba(0,0,0,0.45), 0 0 0 1px rgba(255,255,255,0.08);
      transition: transform .18s ease, box-shadow .18s ease;
    }
    .burbuja:hover { transform: translateY(-3px) scale(1.04); box-shadow: 0 14px 34px rgba(0,0,0,0.5); }
    .burbuja-punto {
      position: absolute; top: 4px; right: 4px; width: 12px; height: 12px;
      border-radius: 50%; background: #3ddc84; border: 2px solid #12121a;
    }

    .panel {
      width: min(390px, calc(100vw - 32px));
      height: min(560px, calc(100vh - 110px));
      display: flex; flex-direction: column; overflow: hidden;
      border-radius: 18px; border: 1px solid rgba(201,168,76,0.28);
      background: #12121a;
      box-shadow: 0 22px 60px rgba(0,0,0,0.6);
      animation: subir .18s ease-out;
    }
    @keyframes subir { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: none; } }

    .p-head {
      display: flex; align-items: center; gap: .5rem; padding: .7rem .8rem;
      border-bottom: 1px solid rgba(255,255,255,0.08);
      background: linear-gradient(120deg, rgba(201,168,76,0.14), transparent);
    }
    .p-icono { color: var(--ms-gold); display: flex; }
    .p-titulo { flex: 1; min-width: 0; display: flex; align-items: baseline; gap: .45rem; }
    .p-titulo strong { font-size: .92rem; color: var(--ms-text); }
    .p-est { font-size: .68rem; color: var(--ms-text-muted); }
    .p-est.on { color: #6ee7a0; }

    .p-btn {
      display: flex; align-items: center; justify-content: center;
      width: 28px; height: 28px; border-radius: 8px; cursor: pointer;
      background: transparent; border: 1px solid transparent;
      color: var(--ms-text-muted); text-decoration: none;
    }
    .p-btn:hover { background: rgba(255,255,255,0.07); color: var(--ms-text); }

    .p-cuerpo { flex: 1; overflow-y: auto; padding: .85rem; display: flex; flex-direction: column; gap: .7rem; }

    .p-aviso, .p-vacio { font-size: .8rem; line-height: 1.55; color: var(--ms-text-muted); }
    .p-aviso { padding: .8rem; border-radius: 10px; background: rgba(255,255,255,0.04); }
    .p-aviso strong { display: block; color: var(--ms-text); margin-bottom: .2rem; }
    .p-vacio p { margin: 0 0 .6rem; }
    .p-ejemplo {
      display: block; width: 100%; text-align: left; margin-bottom: .4rem; cursor: pointer;
      padding: .5rem .6rem; border-radius: 9px; font-size: .78rem; font-family: inherit;
      color: var(--ms-text); background: rgba(255,255,255,0.04);
      border: 1px solid var(--ms-border);
    }
    .p-ejemplo:hover { border-color: var(--ms-gold); }

    .msg { display: flex; }
    .msg.mio { justify-content: flex-end; }
    .m-txt {
      margin: 0; max-width: 85%; padding: .5rem .7rem; border-radius: 12px 12px 3px 12px;
      font-size: .82rem; line-height: 1.5; color: #1a1a1f;
      background: linear-gradient(135deg, #C9A84C, #E8CF7E);
    }
    .m-ia {
      max-width: 100%; padding: .6rem .7rem; border-radius: 12px 12px 12px 3px;
      background: rgba(255,255,255,0.045); border: 1px solid rgba(255,255,255,0.07);
    }
    .m-exp { margin: 0; font-size: .82rem; line-height: 1.55; color: var(--ms-text); }
    .m-err { margin: 0; font-size: .8rem; line-height: 1.55; color: #e79a95; }
    .m-vacio { margin: .5rem 0 0; font-size: .78rem; color: var(--ms-text-muted); }

    .m-tabla { margin-top: .55rem; overflow-x: auto; }
    .m-tabla table { width: 100%; border-collapse: collapse; font-size: .74rem; }
    .m-tabla th, .m-tabla td { padding: .3rem .45rem; text-align: left; white-space: nowrap; }
    .m-tabla th { color: var(--ms-text-muted); font-weight: 600; border-bottom: 1px solid var(--ms-border); }
    .m-tabla td { border-bottom: 1px solid rgba(255,255,255,0.04); color: var(--ms-text); }
    .m-tabla td.num { text-align: right; font-variant-numeric: tabular-nums; }
    .m-mas { margin: .4rem 0 0; font-size: .72rem; color: var(--ms-text-muted); }
    .m-mas a { color: var(--ms-gold); }

    .m-pensando { display: flex; gap: .25rem; padding: .3rem .2rem; }
    .m-pensando span {
      width: 6px; height: 6px; border-radius: 50%; background: var(--ms-gold);
      animation: latir 1.1s ease-in-out infinite;
    }
    .m-pensando span:nth-child(2) { animation-delay: .15s; }
    .m-pensando span:nth-child(3) { animation-delay: .3s; }
    @keyframes latir { 0%, 60%, 100% { opacity: .3; } 30% { opacity: 1; } }

    .p-pie { display: flex; gap: .45rem; padding: .7rem; border-top: 1px solid rgba(255,255,255,0.08); }
    .p-pie input {
      flex: 1; min-width: 0; min-height: 38px; padding: 0 .7rem;
      border-radius: 10px; border: 1px solid var(--ms-border);
      background: rgba(255,255,255,0.04); color: var(--ms-text);
      font-family: inherit; font-size: .82rem;
    }
    .p-pie input:focus { outline: none; border-color: var(--ms-gold); }
    .p-pie button {
      width: 38px; height: 38px; flex-shrink: 0; border-radius: 10px; cursor: pointer;
      display: flex; align-items: center; justify-content: center; border: none;
      color: #1a1a1f; background: linear-gradient(135deg, #C9A84C, #F4E28D);
    }
    .p-pie button:disabled { opacity: .4; cursor: default; }

    @media (max-width: 480px) {
      .burbuja, .panel { right: 14px; bottom: 14px; }
      .panel { height: min(70vh, calc(100vh - 90px)); }
    }
  `]
})
export class AsistenteBurbujaComponent implements OnInit, AfterViewChecked {

  @ViewChild('cuerpo') cuerpo?: ElementRef<HTMLDivElement>;

  pregunta = '';

  constructor(public a: AsistenteService,
              private auth: AuthService,
              private router: Router) {}

  ngOnInit(): void {
    // No se llama a iniciar() aqui: se pregunta por el estado del modulo la
    // primera vez que alguien ABRE la burbuja, no en cada carga de la
    // aplicacion. Quien no use el asistente no paga la peticion.
  }

  /**
   * Se ve si hay sesión, si el rol puede consultar y si no estamos ya en /ia.
   *
   * <p>El permiso se mira aquí además de en el servidor por una razón sencilla:
   * a quien no puede usarlo, una burbuja que siempre responde «no tienes
   * permiso» solo le estorba.
   */
  get visible(): boolean {
    if (!this.auth.isAuthenticated()) { return false; }
    if (this.router.url.startsWith('/ia')) { return false; }
    const permisos: string[] = this.auth.getCurrentUser()?.permisos ?? [];
    return permisos.includes('ia:consultar');
  }

  ngAfterViewChecked(): void {
    if (this.a.hayQueBajar && this.cuerpo) {
      this.cuerpo.nativeElement.scrollTop = this.cuerpo.nativeElement.scrollHeight;
      this.a.hayQueBajar = false;
    }
  }

  enviar(): void {
    this.a.enviar(this.pregunta);
    this.pregunta = '';
  }

  enviarTexto(texto: string): void {
    this.a.enviar(texto);
    this.pregunta = '';
  }
}
