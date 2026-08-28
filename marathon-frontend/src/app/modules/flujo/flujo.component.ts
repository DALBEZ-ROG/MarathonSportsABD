import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { FLUJO, OpcionFlujo, PasoFlujo } from './flujo.model';

/**
 * Inicio: el flujo completo del sistema, en orden.
 *
 * **Por qué esta pantalla.** El tablero de indicadores contesta «cómo va todo»,
 * que es una pregunta que solo se puede hacer cuando ya sabes cómo funciona el
 * sistema. Quien entra por primera vez tiene otra: «¿y ahora qué hago, y en qué
 * orden?». El menú lateral no la contesta —agrupa por módulo, no por
 * secuencia—, así que el orden de trabajo había que deducirlo.
 *
 * Aquí está escrito: ocho pasos, del catálogo a la auditoría, y dentro de cada
 * uno sus opciones en el orden en que se usan. El icono de información de cada
 * paso dice **quién es responsable y qué le corresponde**, que es la otra mitad
 * de la pregunta.
 *
 * **Se ve el flujo entero aunque no sea tuyo.** Las opciones que tu rol no
 * puede abrir salen con candado y con el nombre de quien sí, en vez de
 * desaparecer. Ocultarlas dejaría un flujo con agujeros y daría a entender que
 * el trabajo salta del paso 2 al 5. El candado no es una limitación de la
 * pantalla: es la misma regla que aplica `rolGuard`, dicha antes de chocar con
 * ella.
 *
 * **Sin superposiciones flotantes, a propósito.** El panel de información
 * empuja el contenido hacia abajo en vez de flotar sobre él. Es la lección de
 * los desplegables que se veían por detrás de las tarjetas de más abajo: lo que
 * no flota no puede quedar debajo de nada.
 */
@Component({
  selector: 'app-flujo',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page flujo">

      <header class="cabecera">
        <div class="cab-txt">
          <p class="saludo">{{ saludo }}, {{ nombre }}</p>
          <h1>El flujo de Marathon Sports</h1>
          <p class="sub">
            Ocho pasos, del catálogo a la auditoría. Cada uno depende del anterior:
            este es el orden en que el sistema espera que se trabaje.
          </p>
        </div>

        <div class="cab-rol">
          <span class="etq">Tu rol</span>
          <strong>{{ rol }}</strong>
          <span class="cuenta">{{ abiertas }} de {{ total }} opciones abiertas</span>
        </div>
      </header>

      <!-- Filtro: el flujo entero, o solo lo que puedo hacer -->
      <div class="filtro" role="group" aria-label="Qué mostrar">
        <button type="button" class="f-btn" [class.on]="!soloLoMio"
                [attr.aria-pressed]="!soloLoMio" (click)="soloLoMio = false">
          Todo el flujo
        </button>
        <button type="button" class="f-btn" [class.on]="soloLoMio"
                [attr.aria-pressed]="soloLoMio" (click)="soloLoMio = true">
          Solo lo que puedo hacer
        </button>
      </div>

      <p class="vacio" *ngIf="soloLoMio && !hayAlgoMio()">
        Tu rol no tiene ninguna opción en este flujo. Habla con el administrador.
      </p>

      <!-- ── Los pasos ──────────────────────────────────────────── -->
      <section class="paso" *ngFor="let p of pasosVisibles(); let ultimo = last"
               [class.ajeno]="!tieneAlgo(p)">

        <div class="paso-cab">
          <span class="num" aria-hidden="true">{{ p.numero }}</span>

          <div class="paso-txt">
            <h2>
              {{ p.titulo }}
              <button type="button" class="info"
                      [class.on]="abierto === p.numero"
                      [attr.aria-expanded]="abierto === p.numero"
                      [attr.aria-label]="'Quién hace el paso ' + p.numero + ': ' + p.titulo"
                      [title]="'Responsable: ' + p.responsable"
                      (click)="alternar(p.numero)">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="12" y1="16" x2="12" y2="12"/>
                  <line x1="12" y1="8" x2="12.01" y2="8"/>
                </svg>
              </button>
            </h2>
            <p class="resumen">{{ p.resumen }}</p>
          </div>
        </div>

        <!-- Panel de información: empuja, no flota -->
        <div class="ficha" *ngIf="abierto === p.numero">
          <p class="ficha-rol">
            <span class="etq">Responsable</span>
            <strong>{{ p.responsable }}</strong>
            <span class="tuyo" *ngIf="p.responsable === rol">eres tú</span>
          </p>
          <p class="ficha-txt">{{ p.incumbencia }}</p>
          <p class="ficha-nota" *ngIf="p.nota">
            <strong>Ojo:</strong> {{ p.nota }}
          </p>
        </div>

        <!-- Las opciones del paso, en orden -->
        <div class="tarjetas">
          <ng-container *ngFor="let o of p.opciones">

            <a class="tarjeta" [class.principal]="o.principal"
               *ngIf="puede(o); else bloqueada"
               [routerLink]="o.ruta">
              <span class="t-nom">
                {{ o.nombre }}
                <span class="t-empieza" *ngIf="o.principal">empieza aquí</span>
              </span>
              <span class="t-desc">{{ o.descripcion }}</span>
              <span class="t-ir" aria-hidden="true">→</span>
            </a>

            <ng-template #bloqueada>
              <div class="tarjeta cerrada" *ngIf="!soloLoMio"
                   [title]="'Solo para: ' + o.roles.join(', ')">
                <span class="t-nom">
                  {{ o.nombre }}
                  <svg width="13" height="13" viewBox="0 0 24 24" fill="none"
                       stroke="currentColor" stroke-width="2" aria-hidden="true">
                    <rect x="3" y="11" width="18" height="11" rx="2"/>
                    <path d="M7 11V7a5 5 0 0110 0v4"/>
                  </svg>
                </span>
                <span class="t-desc">{{ o.descripcion }}</span>
                <span class="t-quien">Lo hace {{ quienLoHace(o) }}</span>
              </div>
            </ng-template>

          </ng-container>
        </div>

        <div class="union" *ngIf="!ultimo" aria-hidden="true"></div>
      </section>
    </div>
  `,
  styles: [`
    .flujo { max-width: 1400px; margin: 0 auto; padding-bottom: 3rem; }

    /* ── Cabecera ─────────────────────────────────────────────── */
    .cabecera {
      display: flex; justify-content: space-between; align-items: flex-start;
      gap: 2rem; flex-wrap: wrap; margin-bottom: 1.5rem;
    }
    .saludo { color: var(--ms-text-muted); font-size: .9rem; margin: 0 0 .25rem; }
    .cabecera h1 { margin: 0 0 .5rem; font-size: 1.75rem; color: var(--ms-text); }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .95rem; max-width: 62ch; line-height: 1.5; }

    .cab-rol {
      display: flex; flex-direction: column; gap: .15rem;
      border: 1px solid var(--ms-border); border-radius: var(--ms-radius);
      padding: .75rem 1rem; background: var(--ms-bg-card); min-width: 190px;
    }
    .cab-rol strong { color: var(--ms-gold); font-size: 1rem; }
    .etq { color: var(--ms-text-muted); font-size: .7rem; text-transform: uppercase; letter-spacing: .06em; }
    .cuenta { color: var(--ms-text-muted); font-size: .8rem; margin-top: .2rem; }

    /* ── Filtro ───────────────────────────────────────────────── */
    .filtro { display: flex; gap: .5rem; margin-bottom: 2rem; flex-wrap: wrap; }
    .f-btn {
      background: transparent; border: 1px solid var(--ms-border);
      color: var(--ms-text-muted); padding: .45rem .9rem;
      border-radius: var(--ms-radius-sm); cursor: pointer; font-size: .85rem;
      transition: all .15s ease;
    }
    .f-btn:hover { color: var(--ms-text); border-color: rgba(255,255,255,.15); }
    .f-btn.on { background: var(--ms-gold-dim); border-color: var(--ms-gold); color: var(--ms-gold-light); }

    .vacio { color: var(--ms-text-muted); padding: 2rem; text-align: center; }

    /* ── Paso ─────────────────────────────────────────────────── */
    .paso { position: relative; margin-bottom: 2.25rem; }
    .paso.ajeno { opacity: .72; }

    .paso-cab { display: flex; gap: 1rem; align-items: flex-start; margin-bottom: 1rem; }

    .num {
      flex: 0 0 auto; width: 38px; height: 38px; border-radius: 50%;
      display: grid; place-items: center;
      background: var(--ms-gold-dim); border: 1px solid var(--ms-gold);
      color: var(--ms-gold-light); font-weight: 600; font-size: 1rem;
    }
    .paso-txt { min-width: 0; }
    .paso-txt h2 {
      margin: .35rem 0 .3rem; font-size: 1.15rem; color: var(--ms-text);
      display: flex; align-items: center; gap: .5rem; flex-wrap: wrap;
    }
    .resumen { margin: 0; color: var(--ms-text-muted); font-size: .9rem; max-width: 80ch; line-height: 1.5; }

    .info {
      background: transparent; border: 1px solid var(--ms-border);
      color: var(--ms-text-muted); width: 24px; height: 24px;
      border-radius: 50%; display: grid; place-items: center;
      cursor: pointer; padding: 0; transition: all .15s ease;
    }
    .info:hover, .info.on { color: var(--ms-gold); border-color: var(--ms-gold); background: var(--ms-gold-dim); }

    /* ── Ficha de responsabilidad ─────────────────────────────── */
    /* Empuja el contenido en vez de flotar sobre el: asi no puede
       quedar por detras de las tarjetas de mas abajo. */
    .ficha {
      margin: 0 0 1rem 54px;
      border: 1px solid var(--ms-gold); border-left-width: 3px;
      border-radius: var(--ms-radius-sm);
      background: var(--ms-gold-dim); padding: .9rem 1.1rem;
    }
    .ficha-rol { margin: 0 0 .5rem; display: flex; align-items: baseline; gap: .5rem; flex-wrap: wrap; }
    .ficha-rol strong { color: var(--ms-gold-light); font-size: .95rem; }
    .tuyo {
      background: var(--ms-gold); color: #1a1608; font-size: .68rem;
      padding: .12rem .45rem; border-radius: 99px; font-weight: 600;
      text-transform: uppercase; letter-spacing: .04em;
    }
    .ficha-txt { margin: 0; color: var(--ms-text); font-size: .88rem; line-height: 1.6; max-width: 90ch; }
    .ficha-nota { margin: .6rem 0 0; color: var(--ms-text-muted); font-size: .85rem; line-height: 1.55; max-width: 90ch; }
    .ficha-nota strong { color: var(--ms-text); }

    /* ── Tarjetas ─────────────────────────────────────────────── */
    .tarjetas {
      margin-left: 54px;
      display: grid; gap: .75rem;
      grid-template-columns: repeat(auto-fill, minmax(255px, 1fr));
    }

    .tarjeta {
      position: relative; display: flex; flex-direction: column; gap: .3rem;
      border: 1px solid var(--ms-border); border-radius: var(--ms-radius);
      background: var(--ms-bg-card); padding: .9rem 1rem;
      text-decoration: none; color: inherit; transition: all .15s ease;
    }
    a.tarjeta:hover { border-color: var(--ms-gold); background: rgba(201,168,76,.06); transform: translateY(-1px); }
    a.tarjeta:hover .t-ir { opacity: 1; transform: translateX(0); }

    .tarjeta.principal { border-color: rgba(201,168,76,.45); }

    .t-nom {
      color: var(--ms-text); font-weight: 600; font-size: .95rem;
      display: flex; align-items: center; gap: .45rem; flex-wrap: wrap;
    }
    .t-empieza {
      background: var(--ms-gold-dim); border: 1px solid var(--ms-gold);
      color: var(--ms-gold-light); font-size: .62rem; padding: .1rem .4rem;
      border-radius: 99px; text-transform: uppercase; letter-spacing: .05em; font-weight: 600;
    }
    .t-desc { color: var(--ms-text-muted); font-size: .82rem; line-height: 1.45; }
    .t-ir {
      position: absolute; right: 1rem; top: .9rem; color: var(--ms-gold);
      opacity: 0; transform: translateX(-4px); transition: all .15s ease;
    }

    .tarjeta.cerrada { cursor: not-allowed; background: transparent; border-style: dashed; }
    .tarjeta.cerrada .t-nom { color: var(--ms-text-muted); }
    .t-quien { color: var(--ms-text-muted); font-size: .74rem; margin-top: .2rem; font-style: italic; }

    /* ── Union entre pasos ────────────────────────────────────── */
    .union {
      position: absolute; left: 19px; bottom: -2.25rem; width: 1px; height: 2.25rem;
      background: linear-gradient(to bottom, var(--ms-border), transparent);
    }

    @media (max-width: 640px) {
      .ficha, .tarjetas { margin-left: 0; }
      .union { display: none; }
      .cabecera { flex-direction: column; gap: 1rem; }
    }
  `]
})
export class FlujoComponent implements OnInit {

  readonly pasos: PasoFlujo[] = FLUJO;

  nombre = '';
  rol = '';
  saludo = 'Hola';
  soloLoMio = false;

  /** Número del paso cuya ficha está abierta, o null. Solo una a la vez. */
  abierto: number | null = null;

  constructor(private auth: AuthService) {}

  ngOnInit(): void {
    const u = this.auth.getCurrentUser();
    this.nombre = u?.nombre ?? '';
    this.rol = u?.rol ?? '';
    this.saludo = this.saludoSegunHora();
  }

  puede(o: OpcionFlujo): boolean {
    return this.rol !== '' && o.roles.includes(this.rol);
  }

  /** ¿Este paso tiene alguna opción que yo pueda abrir? */
  tieneAlgo(p: PasoFlujo): boolean {
    return p.opciones.some(o => this.puede(o));
  }

  hayAlgoMio(): boolean {
    return this.pasos.some(p => this.tieneAlgo(p));
  }

  /**
   * Con «solo lo mío» se ocultan los pasos en los que no pinto nada. Sin el
   * filtro se ven los ocho, aunque no sean míos: es lo que hace que esto sea un
   * flujo y no un menú.
   */
  pasosVisibles(): PasoFlujo[] {
    return this.soloLoMio ? this.pasos.filter(p => this.tieneAlgo(p)) : this.pasos;
  }

  /** El primer rol que no sea Administrador: el que de verdad hace el trabajo. */
  quienLoHace(o: OpcionFlujo): string {
    return o.roles.find(r => r !== 'Administrador') ?? o.roles[0];
  }

  get total(): number {
    return this.pasos.reduce((n, p) => n + p.opciones.length, 0);
  }

  get abiertas(): number {
    return this.pasos.reduce((n, p) => n + p.opciones.filter(o => this.puede(o)).length, 0);
  }

  alternar(numero: number): void {
    this.abierto = this.abierto === numero ? null : numero;
  }

  private saludoSegunHora(): string {
    const h = new Date().getHours();
    if (h < 12) return 'Buenos días';
    if (h < 19) return 'Buenas tardes';
    return 'Buenas noches';
  }
}
