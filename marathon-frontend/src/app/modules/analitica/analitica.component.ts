import { AfterViewChecked, Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import Chart from 'chart.js/auto';

import { environment } from '../../../environments/environment';

interface Fila { [k: string]: any; }

interface Analitica {
  desde: string;
  hasta: string;
  periodoEtiqueta: string;
  pedidos: number;
  importe: number;
  clientes: number;
  ticketMedio: number;
  productosMasVendidos: Fila[];
  productosMasComprados: Fila[];
  mejoresClientes: Fila[];
  ventasPorRegion: Fila[];
  ventasPorCiudad: Fila[];
  ventasPorCategoria: Fila[];
  devolucionesPorMotivo: Fila[];
  serie: Fila[];
  granularidad: string;
}

/**
 * Análisis del negocio (F80): qué se vende, quién compra y dónde.
 *
 * <p><b>Por qué una pantalla aparte de /indicadores.</b> Aquélla contesta «¿cómo
 * va todo <i>ahora</i>?» y se mira de pie, en diez segundos. Ésta contesta «¿qué
 * está pasando?» y se mira sentado: son rankings sobre una ventana de tiempo que
 * se cambia y se compara. Meterlas juntas habría hecho la primera más lenta de
 * leer sin hacer la segunda mejor.
 *
 * <p><b>Decisiones de dibujo, y por qué.</b>
 * <ul>
 *   <li><b>Barras horizontales para todo ranking.</b> Los nombres de producto y
 *       cliente son largos; en columnas verticales se giran y dejan de leerse.
 *   <li><b>Un solo color por gráfico.</b> Las barras son categorías nominales
 *       —productos, ciudades— y su magnitud ya la dice la longitud: pintar cada
 *       barra de un color gastaría el canal de identidad en repetir lo que la
 *       barra ya dice. El oro de la marca da 8,5:1 sobre el fondo del panel.
 *   <li><b>Ningún hueco se rellena con cero.</b> Si un bloque viene vacío se dice
 *       con palabras y con el período: «no hubo compras recibidas entre estas dos
 *       fechas». Un gráfico de ceros parece un dato.
 *   <li><b>Cada gráfico lleva su tabla.</b> Plegada, pero ahí: es la lectura que
 *       funciona sin ver color, y la que se copia a un informe.
 * </ul>
 */
@Component({
  selector: 'app-analitica',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="page">

      <header class="cab">
        <div>
          <h1>Análisis del negocio</h1>
          <p class="sub" *ngIf="datos as d">
            {{ d.periodoEtiqueta }}
            <span class="sep">·</span> del {{ d.desde | date:'dd/MM/yyyy' }}
            al {{ d.hasta | date:'dd/MM/yyyy' }}
          </p>
          <p class="sub" *ngIf="!datos">Qué se vende, quién compra y dónde</p>
        </div>

        <div class="herramientas">
          <div class="periodos" role="group" aria-label="Período">
            <button type="button" *ngFor="let p of periodos" class="per-btn"
                    [class.on]="p.clave === periodo" [attr.aria-pressed]="p.clave === periodo"
                    (click)="cambiarPeriodo(p.clave)">{{ p.etiqueta }}</button>
          </div>
          <a class="ir-ind" routerLink="/indicadores">Ver indicadores</a>
        </div>
      </header>

      <p class="cargando" *ngIf="cargando">Calculando sobre los pedidos del período…</p>

      <section class="panel err" *ngIf="!cargando && error">
        <h2>No se pudo calcular</h2>
        <p>{{ error }}</p>
        <button type="button" class="btn-principal" (click)="cargar()">Reintentar</button>
      </section>

      <ng-container *ngIf="!cargando && !error && datos as d">

        <!-- ── Cifras de cabecera ──────────────────────────────────── -->
        <section class="cifras">
          <article class="cifra">
            <span class="c-rot">Pedidos</span>
            <span class="c-val">{{ d.pedidos | number }}</span>
            <span class="c-pie">sin contar anulados</span>
          </article>
          <article class="cifra">
            <span class="c-rot">Facturado</span>
            <span class="c-val oro">\${{ d.importe | number:'1.0-0' }}</span>
            <span class="c-pie">suma del total de cada pedido</span>
          </article>
          <article class="cifra">
            <span class="c-rot">Clientes que compraron</span>
            <span class="c-val">{{ d.clientes | number }}</span>
            <span class="c-pie">distintos, no visitas</span>
          </article>
          <article class="cifra">
            <span class="c-rot">Ticket medio</span>
            <span class="c-val">\${{ d.ticketMedio | number:'1.2-2' }}</span>
            <span class="c-pie">facturado ÷ pedidos</span>
          </article>
        </section>

        <!-- ── Cómo evoluciona ─────────────────────────────────────── -->
        <section class="panel ancho">
          <header class="p-cab">
            <h2>Cómo evoluciona</h2>
            <p class="p-base">
              Facturado por {{ d.granularidad === 'dia' ? 'día' : 'mes' }} de <code>fecha_pedido</code>.
              La granularidad la elige la ventana: en 30 días una serie mensual serían dos
              puntos, y dos puntos unidos no son una tendencia.
              <span *ngIf="d.granularidad === 'dia'">Un día sin ventas vale cero: el día
              existió y no hubo actividad.</span>
              <span *ngIf="d.granularidad !== 'dia'">Un mes sin ventas no sale: la serie
              es la de los pedidos que hay.</span>
            </p>
          </header>
          <div class="lienzo alto" *ngIf="d.serie.length"><canvas #cMes></canvas></div>
          <p class="vacio" *ngIf="!d.serie.length">No hubo ventas entre {{ d.desde | date:'dd/MM/yyyy' }} y {{ d.hasta | date:'dd/MM/yyyy' }}.</p>
          <details class="tabla" *ngIf="d.serie.length">
            <summary>Ver los números</summary>
            <table>
              <thead><tr><th>{{ d.granularidad === 'dia' ? 'Día' : 'Mes' }}</th><th class="num">Pedidos</th><th class="num">Facturado</th></tr></thead>
              <tbody><tr *ngFor="let f of d.serie">
                <td>{{ f['periodo'] }}</td><td class="num">{{ f['pedidos'] | number }}</td>
                <td class="num">\${{ f['importe'] | number:'1.2-2' }}</td></tr></tbody>
            </table>
          </details>
        </section>

        <div class="rejilla">

          <!-- ── Lo más vendido ────────────────────────────────────── -->
          <section class="panel">
            <header class="p-cab">
              <h2>Lo que más sale</h2>
              <p class="p-base">Unidades vendidas por producto. Ordenado por rotación, no por dinero: al lado va lo que facturó cada uno.</p>
            </header>
            <div class="lienzo" *ngIf="d.productosMasVendidos.length"><canvas #cVendidos></canvas></div>
            <p class="vacio" *ngIf="!d.productosMasVendidos.length">No se vendió nada en el período.</p>
            <details class="tabla" *ngIf="d.productosMasVendidos.length">
              <summary>Ver los números</summary>
              <table>
                <thead><tr><th>Producto</th><th class="num">Unidades</th><th class="num">Facturado</th></tr></thead>
                <tbody><tr *ngFor="let f of d.productosMasVendidos">
                  <td>{{ f['nombre'] }}</td><td class="num">{{ f['unidades'] | number }}</td>
                  <td class="num">\${{ f['importe'] | number:'1.2-2' }}</td></tr></tbody>
              </table>
            </details>
          </section>

          <!-- ── Lo más comprado ───────────────────────────────────── -->
          <section class="panel">
            <header class="p-cab">
              <h2>Lo que más se compra</h2>
              <p class="p-base">Unidades <strong>recibidas</strong> de proveedores. Una orden aprobada todavía no es mercancía, así que no cuenta.</p>
            </header>
            <div class="lienzo" *ngIf="d.productosMasComprados.length"><canvas #cComprados></canvas></div>
            <p class="vacio" *ngIf="!d.productosMasComprados.length">No se recibió ninguna compra en el período.</p>
            <details class="tabla" *ngIf="d.productosMasComprados.length">
              <summary>Ver los números</summary>
              <table>
                <thead><tr><th>Producto</th><th class="num">Unidades</th><th class="num">Coste</th></tr></thead>
                <tbody><tr *ngFor="let f of d.productosMasComprados">
                  <td>{{ f['nombre'] }}</td><td class="num">{{ f['unidades'] | number }}</td>
                  <td class="num">\${{ f['importe'] | number:'1.2-2' }}</td></tr></tbody>
              </table>
            </details>
          </section>

          <!-- ── Mejores clientes ──────────────────────────────────── -->
          <section class="panel">
            <header class="p-cab">
              <h2>Quién deja más</h2>
              <p class="p-base">Facturado por cliente. El que más deja no siempre es el que más veces viene: al lado va cuántos pedidos hizo.</p>
            </header>
            <div class="lienzo" *ngIf="d.mejoresClientes.length"><canvas #cClientes></canvas></div>
            <p class="vacio" *ngIf="!d.mejoresClientes.length">Ningún cliente compró en el período.</p>
            <details class="tabla" *ngIf="d.mejoresClientes.length">
              <summary>Ver los números</summary>
              <table>
                <thead><tr><th>Cliente</th><th>Ciudad</th><th class="num">Pedidos</th><th class="num">Facturado</th></tr></thead>
                <tbody><tr *ngFor="let f of d.mejoresClientes">
                  <td>{{ f['nombre'] }}</td><td>{{ f['ciudad'] }}</td>
                  <td class="num">{{ f['pedidos'] | number }}</td>
                  <td class="num">\${{ f['importe'] | number:'1.2-2' }}</td></tr></tbody>
              </table>
            </details>
          </section>

          <!-- ── Regiones ──────────────────────────────────────────── -->
          <section class="panel">
            <header class="p-cab">
              <h2>Dónde se vende</h2>
              <p class="p-base">Facturado por región natural. Sale de la ciudad del cliente, no de un dato tecleado en el pedido.</p>
            </header>
            <div class="lienzo bajo" *ngIf="d.ventasPorRegion.length"><canvas #cRegion></canvas></div>
            <p class="vacio" *ngIf="!d.ventasPorRegion.length">Sin ventas en el período.</p>
            <details class="tabla" *ngIf="d.ventasPorRegion.length">
              <summary>Ver los números</summary>
              <table>
                <thead><tr><th>Región</th><th class="num">Pedidos</th><th class="num">Facturado</th></tr></thead>
                <tbody><tr *ngFor="let f of d.ventasPorRegion">
                  <td>{{ f['nombre'] }}</td><td class="num">{{ f['pedidos'] | number }}</td>
                  <td class="num">\${{ f['importe'] | number:'1.2-2' }}</td></tr></tbody>
              </table>
            </details>
          </section>

          <!-- ── Ciudades ──────────────────────────────────────────── -->
          <section class="panel">
            <header class="p-cab">
              <h2>Las ciudades que más facturan</h2>
              <p class="p-base">Las diez primeras por importe. La región de cada una va en la tabla.</p>
            </header>
            <div class="lienzo" *ngIf="d.ventasPorCiudad.length"><canvas #cCiudad></canvas></div>
            <p class="vacio" *ngIf="!d.ventasPorCiudad.length">Sin ventas en el período.</p>
            <details class="tabla" *ngIf="d.ventasPorCiudad.length">
              <summary>Ver los números</summary>
              <table>
                <thead><tr><th>Ciudad</th><th>Región</th><th class="num">Pedidos</th><th class="num">Facturado</th></tr></thead>
                <tbody><tr *ngFor="let f of d.ventasPorCiudad">
                  <td>{{ f['nombre'] }}</td><td>{{ f['region'] }}</td>
                  <td class="num">{{ f['pedidos'] | number }}</td>
                  <td class="num">\${{ f['importe'] | number:'1.2-2' }}</td></tr></tbody>
              </table>
            </details>
          </section>

          <!-- ── Categorías ────────────────────────────────────────── -->
          <section class="panel">
            <header class="p-cab">
              <h2>De qué se vive</h2>
              <p class="p-base">Facturado por categoría de producto. Es categoría y no marca porque la marca no es una tabla: vive dentro de la descripción.</p>
            </header>
            <div class="lienzo bajo" *ngIf="d.ventasPorCategoria.length"><canvas #cCategoria></canvas></div>
            <p class="vacio" *ngIf="!d.ventasPorCategoria.length">Sin ventas en el período.</p>
            <details class="tabla" *ngIf="d.ventasPorCategoria.length">
              <summary>Ver los números</summary>
              <table>
                <thead><tr><th>Categoría</th><th class="num">Unidades</th><th class="num">Facturado</th></tr></thead>
                <tbody><tr *ngFor="let f of d.ventasPorCategoria">
                  <td>{{ f['nombre'] }}</td><td class="num">{{ f['unidades'] | number }}</td>
                  <td class="num">\${{ f['importe'] | number:'1.2-2' }}</td></tr></tbody>
              </table>
            </details>
          </section>

          <!-- ── Devoluciones ──────────────────────────────────────── -->
          <section class="panel">
            <header class="p-cab">
              <h2>Por qué devuelven</h2>
              <p class="p-base">Unidades devueltas por motivo, sin contar las solicitudes rechazadas. Se cuentan unidades: veinte prendas y una no son el mismo problema.</p>
            </header>
            <div class="lienzo bajo" *ngIf="d.devolucionesPorMotivo.length"><canvas #cDevoluciones></canvas></div>
            <p class="vacio" *ngIf="!d.devolucionesPorMotivo.length">No hubo devoluciones en el período.</p>
            <details class="tabla" *ngIf="d.devolucionesPorMotivo.length">
              <summary>Ver los números</summary>
              <table>
                <thead><tr><th>Motivo</th><th class="num">Solicitudes</th><th class="num">Unidades</th></tr></thead>
                <tbody><tr *ngFor="let f of d.devolucionesPorMotivo">
                  <td>{{ etiquetaMotivo(f['nombre']) }}</td>
                  <td class="num">{{ f['solicitudes'] | number }}</td>
                  <td class="num">{{ f['unidades'] | number }}</td></tr></tbody>
              </table>
            </details>
          </section>
        </div>
      </ng-container>
    </div>
  `,
  styles: [`
    .page { width: 100%; max-width: 1800px; padding: clamp(1rem, 3vw, 2.5rem); margin: 0 auto; }

    .cab { display: flex; justify-content: space-between; align-items: flex-start;
           gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1.75rem; }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.7rem; color: var(--ms-text); }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .92rem; }
    .sep { margin: 0 .35rem; }

    .herramientas { display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .periodos { display: flex; gap: .25rem; background: rgba(255,255,255,0.03);
                border: 1px solid var(--ms-border); border-radius: 99px; padding: .2rem; }
    .per-btn { background: transparent; border: 0; color: var(--ms-text-muted);
               padding: .4rem .85rem; border-radius: 99px; font-size: .82rem;
               cursor: pointer; font-family: inherit; }
    .per-btn:hover { color: var(--ms-text); }
    .per-btn.on { background: var(--ms-gold-dim); color: var(--ms-gold-light); font-weight: 600; }
    .ir-ind { font-size: .84rem; color: var(--ms-text-muted); text-decoration: none;
              border: 1px solid var(--ms-border); padding: .45rem .9rem; border-radius: var(--ms-radius-sm); }
    .ir-ind:hover { border-color: var(--ms-gold); color: var(--ms-gold); }

    .cargando { color: var(--ms-text-muted); font-size: .9rem; }

    /* ── Cifras ────────────────────────────────────────────────── */
    .cifras { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
              gap: 1rem; margin-bottom: 1.25rem; }
    .cifra { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
             border-radius: var(--ms-radius); padding: 1.1rem 1.3rem;
             display: flex; flex-direction: column; gap: .25rem; }
    .c-rot { font-size: .72rem; text-transform: uppercase; letter-spacing: .05em;
             color: var(--ms-text-muted); }
    .c-val { font-size: 1.9rem; font-weight: 700; color: var(--ms-text);
             font-variant-numeric: tabular-nums; line-height: 1.1; }
    .c-val.oro { color: var(--ms-gold-light); }
    .c-pie { font-size: .74rem; color: rgba(255,255,255,0.32); }

    /* ── Paneles ───────────────────────────────────────────────── */
    .rejilla { display: grid; grid-template-columns: repeat(auto-fit, minmax(430px, 1fr)); gap: 1.25rem; }
    .panel { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
             border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; min-width: 0; }
    .panel.ancho { margin-bottom: 1.25rem; }
    .panel.err { border-left: 3px solid #dc2626; }
    .p-cab { margin-bottom: 1rem; }
    .p-cab h2 { margin: 0 0 .3rem; font-size: 1.02rem; color: var(--ms-text); }
    .p-base { margin: 0; font-size: .78rem; line-height: 1.6; color: var(--ms-text-muted);
              max-width: 70ch; }
    .p-base strong { color: rgba(255,255,255,0.8); }
    .p-base code { font-size: .74rem; color: rgba(255,255,255,0.55); }

    .lienzo { position: relative; height: 340px; }
    .lienzo.alto { height: 300px; }
    .lienzo.bajo { height: 210px; }

    .vacio { margin: 0; padding: 2rem 0; text-align: center; font-size: .88rem;
             color: var(--ms-text-muted); line-height: 1.6; }

    .tabla { margin-top: 1rem; border-top: 1px solid var(--ms-border); padding-top: .8rem; }
    .tabla summary { cursor: pointer; font-size: .8rem; color: var(--ms-text-muted);
                     list-style: none; }
    .tabla summary::before { content: '▸ '; }
    .tabla[open] summary::before { content: '▾ '; }
    .tabla summary:hover { color: var(--ms-gold); }
    .tabla table { width: 100%; border-collapse: collapse; margin-top: .7rem; font-size: .82rem; }
    .tabla th { text-align: left; font-size: .68rem; text-transform: uppercase;
                letter-spacing: .05em; color: var(--ms-text-muted); font-weight: 600;
                padding: .35rem .5rem; border-bottom: 1px solid var(--ms-border); }
    .tabla td { padding: .35rem .5rem; color: var(--ms-text);
                border-bottom: 1px solid rgba(255,255,255,0.03); }
    .tabla .num { text-align: right; font-variant-numeric: tabular-nums; }

    @media (max-width: 900px) { .rejilla { grid-template-columns: 1fr; } }
  `]
})
export class AnaliticaComponent implements OnInit, AfterViewChecked, OnDestroy {

  @ViewChild('cMes') refMes?: ElementRef<HTMLCanvasElement>;
  @ViewChild('cVendidos') refVendidos?: ElementRef<HTMLCanvasElement>;
  @ViewChild('cComprados') refComprados?: ElementRef<HTMLCanvasElement>;
  @ViewChild('cClientes') refClientes?: ElementRef<HTMLCanvasElement>;
  @ViewChild('cRegion') refRegion?: ElementRef<HTMLCanvasElement>;
  @ViewChild('cCiudad') refCiudad?: ElementRef<HTMLCanvasElement>;
  @ViewChild('cCategoria') refCategoria?: ElementRef<HTMLCanvasElement>;
  @ViewChild('cDevoluciones') refDevoluciones?: ElementRef<HTMLCanvasElement>;

  datos: Analitica | null = null;
  cargando = false;
  error = '';
  periodo = '30d';
  private pintado = false;
  private graficos: Chart[] = [];

  /** El oro de la marca: 8,5:1 sobre el fondo del panel, muy por encima del 3:1. */
  private readonly ORO = '#C9A84C';
  private readonly ORO_SUAVE = 'rgba(201,168,76,0.75)';
  private readonly TINTA_TENUE = 'rgba(255,255,255,0.4)';
  private readonly REJILLA = 'rgba(255,255,255,0.06)';

  readonly periodos = [
    { clave: '30d',  etiqueta: '30 días' },
    { clave: '90d',  etiqueta: '90 días' },
    { clave: '12m',  etiqueta: '12 meses' },
    { clave: 'todo', etiqueta: 'Todo' }
  ];

  private readonly dinero = new Intl.NumberFormat('es-EC', { maximumFractionDigits: 0 });

  constructor(private http: HttpClient) {}

  ngOnInit() { this.cargar(); }

  ngAfterViewChecked() {
    // Los lienzos solo existen cuando hay datos; se pintan una vez por carga.
    if (this.datos && !this.pintado && this.refVendidos) {
      this.pintado = true;
      this.pintarTodo();
    }
  }

  ngOnDestroy() { this.destruirGraficos(); }

  cambiarPeriodo(clave: string) {
    if (clave === this.periodo) { return; }
    this.periodo = clave;
    this.cargar();
  }

  cargar() {
    this.cargando = true;
    this.error = '';
    this.destruirGraficos();
    this.pintado = false;
    this.http.get<Analitica>(`${environment.apiUrl}/dashboard/analitica?periodo=${this.periodo}`)
      .subscribe({
        next: res => { this.datos = res; this.cargando = false; },
        error: err => {
          this.datos = null;
          this.cargando = false;
          this.error = err?.error?.message
            || 'No se pudo hablar con el servidor. Vuelve a intentarlo.';
        }
      });
  }

  etiquetaMotivo(m: string): string {
    switch (m) {
      case 'producto_defectuoso': return 'Producto defectuoso';
      case 'talla_incorrecta': return 'Talla incorrecta';
      case 'no_esperado': return 'No es lo esperado';
      case 'cambio_opinion': return 'Cambio de opinión';
      case 'producto_incompleto': return 'Producto incompleto';
      case 'otro': return 'Otro';
      default: return m || '';
    }
  }

  // ── Dibujo ──────────────────────────────────────────────────────────────
  private destruirGraficos() {
    this.graficos.forEach(g => g.destroy());
    this.graficos = [];
  }

  private pintarTodo() {
    const d = this.datos;
    if (!d) { return; }

    this.linea(this.refMes, d.serie, 'periodo', 'importe', v => '$' + this.dinero.format(v));
    this.barras(this.refVendidos, d.productosMasVendidos, 'unidades', v => this.dinero.format(v) + ' u.');
    this.barras(this.refComprados, d.productosMasComprados, 'unidades', v => this.dinero.format(v) + ' u.');
    this.barras(this.refClientes, d.mejoresClientes, 'importe', v => '$' + this.dinero.format(v));
    this.barras(this.refRegion, d.ventasPorRegion, 'importe', v => '$' + this.dinero.format(v));
    this.barras(this.refCiudad, d.ventasPorCiudad, 'importe', v => '$' + this.dinero.format(v));
    this.barras(this.refCategoria, d.ventasPorCategoria, 'importe', v => '$' + this.dinero.format(v));
    this.barras(this.refDevoluciones, d.devolucionesPorMotivo, 'unidades',
                v => this.dinero.format(v) + ' u.', n => this.etiquetaMotivo(n));
  }

  /**
   * Un ranking: barras horizontales, un solo color, sin leyenda.
   *
   * <p>Horizontales porque los nombres son largos —un producto se llama «ZAP NIK
   * DM0113-100 W NIKE COURT V 5»— y en vertical se giran y dejan de leerse. Un
   * solo color porque la magnitud ya la dice la longitud de la barra: darle un
   * color a cada una gastaría el canal de identidad en repetir lo mismo.
   */
  private barras(ref: ElementRef<HTMLCanvasElement> | undefined, filas: Fila[],
                 campo: string, formato: (v: number) => string,
                 etiqueta: (n: string) => string = n => n) {
    if (!ref || !filas?.length) { return; }
    const g = new Chart(ref.nativeElement, {
      type: 'bar',
      data: {
        labels: filas.map(f => this.recortar(etiqueta(String(f['nombre'] ?? '')))),
        datasets: [{
          data: filas.map(f => Number(f[campo] ?? 0)),
          backgroundColor: this.ORO_SUAVE,
          hoverBackgroundColor: this.ORO,
          borderRadius: 4,
          borderSkipped: false,
          barPercentage: 0.78,
          categoryPercentage: 0.82
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: items => String(filas[items[0].dataIndex]?.['nombre'] ?? ''),
              label: ctx => formato(Number(ctx.parsed.x ?? 0))
            }
          }
        },
        scales: {
          x: {
            grid: { color: this.REJILLA },
            border: { display: false },
            ticks: { color: this.TINTA_TENUE, font: { size: 11 },
                     callback: v => formato(Number(v)) }
          },
          y: {
            grid: { display: false },
            border: { display: false },
            ticks: { color: this.TINTA_TENUE, font: { size: 11 }, autoSkip: false }
          }
        }
      }
    });
    this.graficos.push(g);
  }

  /** La evolución: una línea, una serie, sin leyenda — el título ya la nombra. */
  private linea(ref: ElementRef<HTMLCanvasElement> | undefined, filas: Fila[],
                campoX: string, campoY: string, formato: (v: number) => string) {
    if (!ref || !filas?.length) { return; }
    const g = new Chart(ref.nativeElement, {
      type: 'line',
      data: {
        labels: filas.map(f => String(f[campoX] ?? '')),
        datasets: [{
          data: filas.map(f => Number(f[campoY] ?? 0)),
          borderColor: this.ORO,
          backgroundColor: 'rgba(201,168,76,0.10)',
          borderWidth: 2,
          pointRadius: filas.length > 24 ? 0 : 3,
          pointHoverRadius: 6,
          tension: 0.25,
          fill: true
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: ctx => formato(Number(ctx.parsed.y ?? 0)) } }
        },
        scales: {
          x: { grid: { display: false }, border: { display: false },
               ticks: { color: this.TINTA_TENUE, font: { size: 11 } } },
          y: { grid: { color: this.REJILLA }, border: { display: false },
               ticks: { color: this.TINTA_TENUE, font: { size: 11 },
                        callback: v => formato(Number(v)) } }
        }
      }
    });
    this.graficos.push(g);
  }

  /** Un nombre de producto entero no cabe en el eje; el completo va en el tooltip. */
  private recortar(txt: string): string {
    return txt.length > 30 ? txt.slice(0, 29) + '…' : txt;
  }
}
