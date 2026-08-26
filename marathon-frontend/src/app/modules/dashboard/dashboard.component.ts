import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import Chart from 'chart.js/auto';

import { AuthService } from '../../core/services/auth.service';
import {
  ClavePeriodo, DashboardResumen, DashboardService
} from '../../core/services/dashboard.service';
import { IndicadorCardComponent } from '../../shared/components/indicador-card/indicador-card.component';

/**
 * Inicio (D2): el tablero del rol del usuario.
 *
 * Es la pantalla a la que lleva el login y la primera del menú. **No calcula
 * ninguna cifra**: pide `GET /api/dashboard/resumen`, que devuelve solo los
 * indicadores del rol del token, cada uno con su período, su base de cálculo y
 * su estado. Aquí solo se decide cómo se pinta cada estado.
 *
 * Los tres estados de la pantalla entera —cargando, error de red y tablero
 * vacío— son explícitos. Antes había cinco `error: () => {}` en este archivo:
 * si la red fallaba, las tarjetas se quedaban en su valor inicial, que era 0, y
 * el usuario leía «no hay nada» donde la verdad era «no se pudo cargar».
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, IndicadorCardComponent],
  template: `
    <div class="page">

      <!-- ── Llegada desde una sección sin permiso (F32) ─────────── -->
      <div class="aviso" *ngIf="accesoDenegado" role="alert">
        <strong>No tienes acceso a esa sección.</strong>
        Tu rol es {{ rolUsuario }}. Si necesitas entrar, pídeselo al administrador.
        <button type="button" class="cerrar-aviso" (click)="accesoDenegado = false"
                aria-label="Cerrar aviso">×</button>
      </div>

      <!-- ── Encabezado ─────────────────────────────────────────── -->
      <header class="page-head">
        <div class="head-txt">
          <p class="saludo">{{ saludo }}, {{ nombre }}</p>
          <h1>{{ resumen?.titulo || 'Inicio' }}</h1>
          <p class="sub" *ngIf="resumen">
            {{ resumen.periodoEtiqueta }}
            <span class="dot">·</span>
            calculado a las {{ horaCalculo }}
          </p>
        </div>

        <div class="head-tools">
          <div class="periodos" role="group" aria-label="Período">
            <button type="button"
                    *ngFor="let p of periodos"
                    class="per-btn"
                    [class.on]="p.clave === periodo"
                    [attr.aria-pressed]="p.clave === periodo"
                    (click)="cambiarPeriodo(p.clave)">
              {{ p.etiqueta }}
            </button>
          </div>
          <button type="button" class="refrescar" (click)="cargar()" [disabled]="cargando"
                  title="Volver a calcular">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/>
              <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
            </svg>
            <span>Actualizar</span>
          </button>
        </div>
      </header>

      <!-- ── Cargando ───────────────────────────────────────────── -->
      <section class="grid" *ngIf="cargando" aria-busy="true" aria-label="Cargando indicadores">
        <div class="skeleton" *ngFor="let s of esqueletos">
          <div class="sk-line w40"></div>
          <div class="sk-line big"></div>
          <div class="sk-line w60"></div>
          <div class="sk-line w80 thin"></div>
        </div>
      </section>

      <!-- ── Error de red ───────────────────────────────────────── -->
      <section class="estado-panel err" *ngIf="!cargando && error">
        <h2>No se pudo cargar el tablero</h2>
        <p>{{ error }}</p>
        <button type="button" class="btn-principal" (click)="cargar()">Reintentar</button>
      </section>

      <!-- ── Tablero ────────────────────────────────────────────── -->
      <ng-container *ngIf="!cargando && !error && resumen as r">

        <section class="grid">
          <app-indicador-card *ngFor="let i of r.indicadores"
                              [ind]="i"
                              (reintentar)="cargar()"></app-indicador-card>
        </section>

        <section class="paneles" *ngIf="r.serie.length || r.topProductos.length">

          <!-- Gráfico -->
          <article class="panel" *ngIf="r.serie.length">
            <header class="panel-head">
              <h2>Pedidos por día</h2>
              <p class="panel-base">
                Pedidos no anulados por <code>fecha_pedido</code>. Un día sin pedidos vale
                cero: el día existe y no hubo actividad.
              </p>
            </header>
            <div class="chart-box">
              <canvas #lienzo></canvas>
            </div>
            <p class="panel-vacio" *ngIf="serieVacia">
              Sin pedidos entre {{ r.desde }} y {{ r.hasta }}.
            </p>
          </article>

          <!-- Top productos -->
          <article class="panel" *ngIf="r.topProductos.length">
            <header class="panel-head">
              <h2>Más vendidos</h2>
              <p class="panel-base">
                Unidades del período, no del histórico. {{ r.periodoEtiqueta }}
              </p>
            </header>
            <ol class="rank">
              <li *ngFor="let p of r.topProductos; let i = index">
                <span class="pos">{{ i + 1 }}</span>
                <span class="nom" [title]="p.nombre">{{ p.nombre }}</span>
                <span class="uds">{{ formatear(p.unidades) }}</span>
                <span class="barra" [style.width.%]="anchoBarra(p.unidades)"></span>
              </li>
            </ol>
          </article>
        </section>

        <!-- Tablero sin indicadores -->
        <section class="estado-panel" *ngIf="!r.indicadores.length">
          <h2>Este rol todavía no tiene indicadores</h2>
          <p>Habla con el administrador para que le definan un tablero.</p>
        </section>

        <p class="pie">
          Todas las cifras se calculan en el servidor con SQL agregado.
          Cada tarjeta indica su período y de qué datos sale.
          <a routerLink="/perfil">Mi cuenta</a>
        </p>
      </ng-container>
    </div>
  `,
  styles: [`
    /* El contenedor es fluido: ocupa el ancho disponible con un tope alto para
       que en un monitor ultra-ancho las líneas de texto no se estiren. */
    .page {
      width: 100%;
      max-width: 1800px;
      margin: 0 auto;
      padding: clamp(1rem, 3vw, 2.5rem);
      color: #e9e9ee;
    }

    .aviso {
      position: relative; margin-bottom: 1.2rem; padding: .85rem 2.4rem .85rem 1rem;
      border-radius: 12px; font-size: .84rem; line-height: 1.55;
      border: 1px solid rgba(224,166,60,0.35); background: rgba(224,166,60,0.08);
      color: rgba(255,255,255,0.8);
    }
    .aviso strong { color: #e9be6a; }
    .cerrar-aviso {
      position: absolute; top: .45rem; right: .6rem; cursor: pointer;
      background: none; border: none; font-size: 1.15rem; line-height: 1;
      color: rgba(255,255,255,0.45);
    }
    .cerrar-aviso:hover { color: #fff; }

    /* ── Encabezado ── */
    .page-head {
      display: flex; flex-wrap: wrap; gap: 1.25rem;
      align-items: flex-end; justify-content: space-between;
      margin-bottom: 1.6rem;
    }
    .saludo { margin: 0 0 .2rem; font-size: .82rem; color: rgba(255,255,255,0.45); }
    .page-head h1 {
      margin: 0; font-size: clamp(1.5rem, 3vw, 2.1rem); font-weight: 700;
      letter-spacing: -0.02em;
      background: linear-gradient(100deg, #fff 20%, #C9A84C);
      -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent;
    }
    .sub { margin: .35rem 0 0; font-size: .82rem; color: rgba(255,255,255,0.5); }
    .dot { margin: 0 .3rem; opacity: .5; }

    .head-tools { display: flex; align-items: center; gap: .6rem; flex-wrap: wrap; }
    .periodos {
      display: inline-flex; padding: 3px; gap: 2px; border-radius: 10px;
      background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.08);
    }
    .per-btn {
      cursor: pointer; border: none; background: transparent; color: rgba(255,255,255,0.6);
      font-size: .78rem; font-weight: 600; padding: .38rem .75rem; border-radius: 8px;
      transition: background .15s, color .15s;
    }
    .per-btn:hover { color: #fff; }
    .per-btn.on { background: rgba(201,168,76,0.16); color: #F4E28D; }

    .refrescar {
      display: inline-flex; align-items: center; gap: .4rem; cursor: pointer;
      font-size: .78rem; font-weight: 600; padding: .45rem .8rem; border-radius: 10px;
      background: rgba(255,255,255,0.05); color: rgba(255,255,255,0.75);
      border: 1px solid rgba(255,255,255,0.1);
    }
    .refrescar:hover:not(:disabled) { background: rgba(255,255,255,0.1); color: #fff; }
    .refrescar:disabled { opacity: .5; cursor: default; }

    /* ── Rejilla adaptable: sin media queries por tamaño ── */
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 268px), 1fr));
      gap: clamp(.8rem, 1.4vw, 1.15rem);
      align-items: stretch;
    }

    /* ── Esqueleto de carga ── */
    .skeleton {
      border-radius: 16px; padding: 1.15rem 1.25rem; min-height: 178px;
      border: 1px solid rgba(255,255,255,0.06); background: rgba(255,255,255,0.025);
    }
    .sk-line {
      height: 10px; border-radius: 6px; margin-bottom: .7rem;
      background: linear-gradient(90deg, rgba(255,255,255,0.05), rgba(255,255,255,0.11), rgba(255,255,255,0.05));
      background-size: 220% 100%; animation: sk 1.4s ease-in-out infinite;
    }
    .sk-line.big { height: 30px; width: 55%; margin: 1.1rem 0; }
    .sk-line.thin { height: 7px; }
    .w40 { width: 40%; } .w60 { width: 60%; } .w80 { width: 80%; }
    @keyframes sk { 0% { background-position: 200% 0; } 100% { background-position: -40% 0; } }

    /* ── Paneles de estado ── */
    .estado-panel {
      margin-top: 1.2rem; padding: 2.2rem 1.5rem; border-radius: 16px; text-align: center;
      border: 1px dashed rgba(255,255,255,0.14); background: rgba(255,255,255,0.02);
    }
    .estado-panel h2 { margin: 0 0 .5rem; font-size: 1.05rem; font-weight: 600; }
    .estado-panel p { margin: 0 auto; max-width: 52ch; font-size: .86rem; color: rgba(255,255,255,0.55); line-height: 1.6; }
    .estado-panel.err { border-color: rgba(217,83,79,0.4); background: rgba(217,83,79,0.06); }
    .estado-panel.err h2 { color: #e79a95; }
    .btn-principal {
      margin-top: 1.1rem; cursor: pointer; font-size: .82rem; font-weight: 600;
      padding: .5rem 1.2rem; border-radius: 10px; color: #1a1a1f;
      background: linear-gradient(100deg, #C9A84C, #F4E28D); border: none;
    }

    /* ── Paneles de gráfico y ranking ── */
    .paneles {
      display: grid; gap: clamp(.8rem, 1.4vw, 1.15rem); margin-top: clamp(.8rem, 1.4vw, 1.15rem);
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 340px), 1fr));
    }
    .panel {
      padding: 1.25rem; border-radius: 16px;
      border: 1px solid rgba(255,255,255,0.07);
      background: linear-gradient(155deg, rgba(255,255,255,0.045), rgba(255,255,255,0.015));
    }
    .panel-head h2 { margin: 0; font-size: .95rem; font-weight: 600; }
    .panel-base { margin: .3rem 0 1rem; font-size: .72rem; line-height: 1.5; color: rgba(255,255,255,0.4); }
    .panel-base code { font-size: .7rem; color: rgba(201,168,76,0.85); }
    .chart-box { position: relative; height: 260px; }
    .panel-vacio { margin: .8rem 0 0; font-size: .8rem; color: rgba(255,255,255,0.5); text-align: center; }

    .rank { list-style: none; margin: 0; padding: 0; display: grid; gap: .55rem; }
    .rank li {
      position: relative; display: grid; align-items: center; gap: .6rem;
      grid-template-columns: 22px 1fr auto;
      padding: .55rem .65rem; border-radius: 10px; background: rgba(255,255,255,0.03);
      overflow: hidden;
    }
    .pos { font-size: .72rem; font-weight: 700; color: rgba(201,168,76,0.85); z-index: 1; }
    .nom {
      font-size: .8rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
      color: rgba(255,255,255,0.82); z-index: 1;
    }
    .uds { font-size: .78rem; font-weight: 600; font-variant-numeric: tabular-nums; z-index: 1; }
    .barra {
      position: absolute; left: 0; top: 0; bottom: 0; z-index: 0;
      background: linear-gradient(90deg, rgba(201,168,76,0.2), rgba(201,168,76,0.04));
    }

    .pie {
      margin: 1.6rem 0 0; font-size: .74rem; line-height: 1.6;
      color: rgba(255,255,255,0.33); text-align: center;
    }
    .pie a { color: rgba(201,168,76,0.8); }
  `]
})
export class DashboardComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('lienzo') lienzo?: ElementRef<HTMLCanvasElement>;

  readonly periodos: { clave: ClavePeriodo; etiqueta: string }[] = [
    { clave: '7d', etiqueta: '7 días' },
    { clave: '30d', etiqueta: '30 días' },
    { clave: '90d', etiqueta: '90 días' }
  ];

  /** Cuántas tarjetas fantasma pintar mientras carga. */
  readonly esqueletos = [1, 2, 3, 4, 5, 6];

  periodo: ClavePeriodo = '30d';
  resumen: DashboardResumen | null = null;
  cargando = true;
  error: string | null = null;
  nombre = '';
  rolUsuario = '';
  accesoDenegado = false;

  private grafico?: Chart;
  private readonly fmt = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 0 });

  constructor(
    private dashboardService: DashboardService,
    private authService: AuthService,
    private ruta: ActivatedRoute
  ) {
    const u = this.authService.getCurrentUser();
    this.nombre = u?.nombre ?? '';
    this.rolUsuario = u?.rol ?? 'desconocido';
  }

  ngOnInit(): void {
    this.accesoDenegado = this.ruta.snapshot.queryParamMap.get('acceso') === 'denegado';
    this.cargar();
  }

  ngAfterViewInit(): void {
    this.pintarGrafico();
  }

  ngOnDestroy(): void {
    this.grafico?.destroy();
  }

  get saludo(): string {
    const h = new Date().getHours();
    return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
  }

  get horaCalculo(): string {
    if (!this.resumen) return '';
    return new Date(this.resumen.generadoEn)
      .toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
  }

  get serieVacia(): boolean {
    return !!this.resumen && this.resumen.serie.every(d => d.pedidos === 0);
  }

  cambiarPeriodo(p: ClavePeriodo): void {
    if (p === this.periodo) return;
    this.periodo = p;
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = null;
    this.grafico?.destroy();
    this.grafico = undefined;

    this.dashboardService.getResumen(this.periodo).subscribe({
      next: r => {
        this.resumen = r;
        this.cargando = false;
        // El canvas aparece en el mismo ciclo en que se apaga `cargando`;
        // hay que esperar a que exista antes de dibujar sobre él.
        setTimeout(() => this.pintarGrafico());
      },
      error: err => {
        // Nunca se deja la pantalla en ceros: un cero significa «no hubo nada»
        // y esto es «no se pudo preguntar».
        this.resumen = null;
        this.cargando = false;
        this.error = this.mensajeDeError(err);
      }
    });
  }

  formatear(n: number): string {
    return this.fmt.format(n);
  }

  anchoBarra(unidades: number): number {
    const top = this.resumen?.topProductos[0]?.unidades ?? 0;
    return top > 0 ? Math.round((unidades / top) * 100) : 0;
  }

  // ------------------------------------------------------------------

  private mensajeDeError(err: unknown): string {
    const e = err as { status?: number; error?: { message?: string } };
    if (e?.status === 0) {
      return 'No hay conexión con el servidor. Comprueba tu red e inténtalo otra vez.';
    }
    if (e?.status === 401) {
      return 'Tu sesión ha caducado. Vuelve a entrar.';
    }
    if (e?.status === 403) {
      return 'Tu rol no tiene acceso al tablero.';
    }
    return e?.error?.message ?? 'El servidor no respondió correctamente.';
  }

  private pintarGrafico(): void {
    const canvas = this.lienzo?.nativeElement;
    const serie = this.resumen?.serie ?? [];
    if (!canvas || !serie.length) return;

    this.grafico?.destroy();

    // Con 90 días no caben 90 etiquetas: se muestran salteadas y el resto
    // sigue en el tooltip, que es donde se consulta el dato exacto.
    const paso = serie.length > 40 ? 10 : serie.length > 14 ? 3 : 1;

    this.grafico = new Chart(canvas, {
      type: 'line',
      data: {
        labels: serie.map(d => this.etiquetaDia(d.dia)),
        datasets: [{
          label: 'Pedidos',
          data: serie.map(d => d.pedidos),
          borderColor: '#C9A84C',
          backgroundColor: 'rgba(201,168,76,0.12)',
          borderWidth: 2,
          pointRadius: serie.length > 40 ? 0 : 2.5,
          pointHoverRadius: 5,
          tension: 0.3,
          fill: true
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: ctx => `${this.fmt.format(ctx.parsed.y ?? 0)} pedidos`
            }
          }
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: {
              color: 'rgba(255,255,255,0.4)',
              maxRotation: 0,
              autoSkip: false,
              callback: (_v, i) => (i % paso === 0 ? this.etiquetaDia(serie[i].dia) : '')
            }
          },
          y: {
            beginAtZero: true,
            grid: { color: 'rgba(255,255,255,0.05)' },
            ticks: { color: 'rgba(255,255,255,0.4)', precision: 0 }
          }
        }
      }
    });
  }

  /** `2026-08-26` → `26 ago`. Sin `new Date()`, que desplazaría el día por la zona horaria. */
  private etiquetaDia(iso: string): string {
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun',
                   'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    const [, mes, dia] = iso.split('-');
    return `${Number(dia)} ${meses[Number(mes) - 1]}`;
  }
}
