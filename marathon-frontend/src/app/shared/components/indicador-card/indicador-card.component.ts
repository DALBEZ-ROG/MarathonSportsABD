import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Indicador } from '../../../core/services/dashboard.service';

/**
 * Una tarjeta de indicador (D2).
 *
 * Es la única pieza que sabe cómo se pinta una cifra del tablero, y no calcula
 * ninguna: recibe el `Indicador` ya resuelto por el servidor y elige plantilla
 * según `estado`. Los cinco estados se ven **distintos a propósito**, porque un
 * cero, un «no hubo nada», un «la base no lo guarda» y un «no se pudo cargar»
 * significan cosas opuestas y antes se leían todos igual: un 0 grande.
 *
 * La variación no se colorea de verde ni de rojo. Que una cifra suba no es
 * bueno ni malo por sí mismo — la tasa de anulación subiendo es malo, y los
 * pedidos subiendo es bueno. Se muestra la dirección y el porcentaje, y quien
 * lee decide.
 */
@Component({
  selector: 'app-indicador-card',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <article class="ind" [attr.data-estado]="ind.estado">
      <header class="ind-head">
        <h3 class="ind-title">{{ ind.titulo }}</h3>
        <span class="badge" *ngIf="ind.estado === 'parcial'" title="{{ ind.nota }}">parcial</span>
        <span class="badge sd" *ngIf="ind.estado === 'sin_dato'">sin dato</span>
      </header>

      <!-- ── Hay cifra: ok o parcial ───────────────────────────── -->
      <div class="ind-body" *ngIf="ind.estado === 'ok' || ind.estado === 'parcial'">
        <div class="value-row">
          <span class="prefix" *ngIf="ind.unidad === '$'">$</span>
          <span class="value">{{ valorFormateado }}</span>
          <span class="suffix" *ngIf="ind.unidad === '%'">%</span>
          <span class="unit" *ngIf="ind.unidad && ind.unidad !== '$' && ind.unidad !== '%'">
            {{ ind.unidad }}
          </span>
        </div>

        <p class="denom" *ngIf="ind.denominador !== null">
          sobre <strong>{{ formatear(ind.denominador) }}</strong>
        </p>

        <p class="cmp" *ngIf="ind.comparacion as c">
          <span class="chip" [class.up]="(c.variacion ?? 0) > 0" [class.down]="(c.variacion ?? 0) < 0">
            {{ flecha(c.variacion) }} {{ variacionTexto(c.variacion) }}
          </span>
          <span class="cmp-txt">
            <ng-container *ngIf="c.variacion !== null">
              frente a {{ formatear(c.valor) }} · {{ c.etiqueta }}
            </ng-container>
            <ng-container *ngIf="c.variacion === null">
              no hubo nada en {{ c.etiqueta }}
            </ng-container>
          </span>
        </p>
      </div>

      <!-- ── No hay cifra: vacío, sin dato o error ─────────────── -->
      <div class="ind-body empty" *ngIf="ind.estado !== 'ok' && ind.estado !== 'parcial'">
        <p class="nota">{{ ind.nota }}</p>
        <button type="button" class="retry" *ngIf="ind.estado === 'error'" (click)="reintentar.emit()">
          Reintentar
        </button>
      </div>

      <footer class="ind-foot">
        <p class="periodo">{{ ind.periodo }}</p>
        <p class="base" *ngIf="ind.base" [title]="ind.base">{{ ind.base }}</p>
        <p class="cobertura" *ngIf="ind.estado === 'parcial'">{{ ind.nota }}</p>
        <a class="ir" *ngIf="ind.enlace" [routerLink]="ind.enlace">
          Ver detalle
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </a>
      </footer>
    </article>
  `,
  styles: [`
    :host { display: block; height: 100%; }

    .ind {
      display: flex; flex-direction: column; height: 100%;
      padding: 1.15rem 1.25rem 1rem;
      border-radius: 16px;
      border: 1px solid rgba(255,255,255,0.07);
      background: linear-gradient(155deg, rgba(255,255,255,0.045), rgba(255,255,255,0.015));
      backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
      position: relative; overflow: hidden;
      transition: transform .18s ease, border-color .18s ease, box-shadow .18s ease;
    }
    .ind::before {
      content: ''; position: absolute; inset: 0 0 auto 0; height: 2px;
      background: linear-gradient(90deg, #C9A84C, rgba(201,168,76,0));
    }
    .ind:hover { transform: translateY(-3px); border-color: rgba(201,168,76,0.28); box-shadow: 0 14px 34px rgba(0,0,0,0.34); }

    .ind[data-estado="vacio"]::before,
    .ind[data-estado="sin_dato"]::before { background: linear-gradient(90deg, rgba(255,255,255,0.22), transparent); }
    .ind[data-estado="sin_dato"] { border-style: dashed; }
    .ind[data-estado="error"]::before { background: linear-gradient(90deg, #d9534f, rgba(217,83,79,0)); }
    .ind[data-estado="error"] { border-color: rgba(217,83,79,0.35); }
    .ind[data-estado="parcial"]::before { background: linear-gradient(90deg, #e0a63c, rgba(224,166,60,0)); }

    .ind-head { display: flex; align-items: flex-start; gap: .5rem; margin-bottom: .6rem; }
    .ind-title {
      margin: 0; flex: 1; font-size: .8rem; font-weight: 600; letter-spacing: .04em;
      text-transform: uppercase; color: rgba(255,255,255,0.62); line-height: 1.35;
    }
    .badge {
      flex-shrink: 0; font-size: .62rem; letter-spacing: .06em; text-transform: uppercase;
      padding: .18rem .45rem; border-radius: 999px;
      background: rgba(224,166,60,0.16); color: #e9be6a; border: 1px solid rgba(224,166,60,0.3);
    }
    .badge.sd { background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.55); border-color: rgba(255,255,255,0.14); }

    .ind-body { flex: 1; }
    .value-row { display: flex; align-items: baseline; gap: .22rem; flex-wrap: wrap; }
    .value {
      font-size: clamp(1.7rem, 2.4vw, 2.3rem); font-weight: 700; line-height: 1.05;
      color: #f4f4f6; font-variant-numeric: tabular-nums; letter-spacing: -0.02em;
    }
    .prefix, .suffix { font-size: 1.2rem; font-weight: 600; color: #C9A84C; }
    .unit { font-size: .78rem; color: rgba(255,255,255,0.42); margin-left: .18rem; }

    .denom { margin: .25rem 0 0; font-size: .78rem; color: rgba(255,255,255,0.5); }
    .denom strong { color: rgba(255,255,255,0.78); font-weight: 600; font-variant-numeric: tabular-nums; }

    .cmp { margin: .6rem 0 0; display: flex; align-items: center; gap: .45rem; flex-wrap: wrap; }
    .chip {
      font-size: .72rem; font-weight: 600; padding: .16rem .45rem; border-radius: 6px;
      background: rgba(255,255,255,0.07); color: rgba(255,255,255,0.72);
      border: 1px solid rgba(255,255,255,0.1); font-variant-numeric: tabular-nums;
    }
    .chip.up { color: #8fd6a8; border-color: rgba(143,214,168,0.28); background: rgba(143,214,168,0.1); }
    .chip.down { color: #e79a95; border-color: rgba(231,154,149,0.28); background: rgba(231,154,149,0.1); }
    .cmp-txt { font-size: .72rem; color: rgba(255,255,255,0.42); }

    .empty { display: flex; flex-direction: column; justify-content: center; gap: .7rem; min-height: 68px; }
    /* text-align y font-style explícitos: styles.scss tiene una .nota global
       centrada y en cursiva que aquí no encaja. */
    .nota {
      margin: 0; font-size: .84rem; line-height: 1.5; color: rgba(255,255,255,0.55);
      text-align: left; font-style: normal;
    }
    .ind[data-estado="error"] .nota { color: #e79a95; }
    .retry {
      align-self: flex-start; cursor: pointer;
      font-size: .74rem; padding: .3rem .7rem; border-radius: 8px;
      background: rgba(255,255,255,0.06); color: rgba(255,255,255,0.8);
      border: 1px solid rgba(255,255,255,0.14);
    }
    .retry:hover { background: rgba(255,255,255,0.12); }

    .ind-foot { margin-top: .9rem; padding-top: .7rem; border-top: 1px solid rgba(255,255,255,0.06); }
    .periodo { margin: 0; font-size: .72rem; font-weight: 600; color: rgba(201,168,76,0.85); }
    .base {
      margin: .22rem 0 0; font-size: .7rem; line-height: 1.45; color: rgba(255,255,255,0.34);
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
    }
    .cobertura { margin: .3rem 0 0; font-size: .7rem; line-height: 1.45; color: rgba(224,166,60,0.8); }
    .ir {
      display: inline-flex; align-items: center; gap: .22rem; margin-top: .5rem;
      font-size: .74rem; font-weight: 600; color: rgba(201,168,76,0.9); text-decoration: none;
    }
    .ir:hover { color: #F4E28D; }
  `]
})
export class IndicadorCardComponent {

  @Input({ required: true }) ind!: Indicador;

  /** Lo emite el botón de reintento de una tarjeta en error. */
  @Output() reintentar = new EventEmitter<void>();

  private readonly fmt = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 2 });

  get valorFormateado(): string {
    return this.ind.valor === null ? '—' : this.fmt.format(this.ind.valor);
  }

  formatear(n: number): string {
    return this.fmt.format(n);
  }

  flecha(variacion: number | null): string {
    if (variacion === null) return '·';
    return variacion > 0 ? '▲' : variacion < 0 ? '▼' : '=';
  }

  /** `null` no es 0%: es «el período anterior fue cero, no hay con qué comparar». */
  variacionTexto(variacion: number | null): string {
    return variacion === null
      ? 'sin comparación'
      : `${this.fmt.format(Math.abs(variacion))}%`;
  }
}
