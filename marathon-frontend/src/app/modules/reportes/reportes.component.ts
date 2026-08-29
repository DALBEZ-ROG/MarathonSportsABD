import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';

interface Categoria { idCategoria: number; nombre: string; }
interface Bodega { idBodega: number; nombre: string; }

interface FiltroReporte {
  desde?: string | null;
  hasta?: string | null;
  estado?: string | null;
  idCategoria?: number | null;
  regionDestino?: string | null;
  idBodega?: number | null;
  idMateriaPrima?: number | null;
  idProducto?: number | null;
  limite?: number;
}

interface PedidoItem {
  idPedido: number; fechaPedido: string; estado: string; cliente: string; ciudad: string;
  regionDestino: string; transportista: string; total: number; descuento: number;
  esPedidoEspecial: boolean; tipoEspecial: string;
}
interface VentaProductoItem {
  idProducto: number; nombreProducto: string; categoria: string; unidadMedida: string;
  cantidadVendida: number; totalIngresos: number; precioPromedio: number; numeroPedidos: number;
}
interface MovimientoItem {
  idMovimiento: number; tipoMovimiento: string; cantidad: number; fecha: string;
  observacion: string; producto: string; bodega: string; bodegaDestino: string; usuario: string;
}
interface CostoProduccionItem {
  idOrdenProduccion: number; producto: string; cantidadProducida: number;
  costoMateriaPrima: number; costoManoObra: number; costoIndirecto: number;
  costoTotal: number; costoUnitario: number; fecha: string;
}
interface ConsumoMpItem {
  idMateriaPrima: number; nombreMateriaPrima: string; unidadMedida: string;
  cantidadConsumidaTotal: number; costoConsumidoTotal: number; numeroOrdenes: number;
}
interface EficienciaItem {
  idOrdenProduccion: number; producto: string; cantidadPlanificada: number; cantidadProducida: number;
  eficienciaProduccion: number; mermaTotalMateriaPrima: number;
  costoTotal: number; costoUnitario: number; fechaFin: string;
}
interface MateriaPrimaOpt { idMateriaPrima: number; nombre: string; }

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="reportes">
      <header class="rep-cab">
        <h1>Reportes</h1>
        <p class="rep-sub">
          Listados con filtros, para mirarlos aquí o llevárselos en Excel o PDF.
          Para gráficos y rankings está <a routerLink="/analitica">el análisis del negocio</a>.
        </p>
      </header>

      <div class="tabs">
        <button *ngFor="let t of informes" [class.active]="tab===t.clave"
                (click)="cambiarTab(t.clave)">{{ t.titulo }}</button>
      </div>

      <p class="rep-que" *ngIf="informeActual as inf">{{ inf.contesta }}</p>

      <!-- ===================== PANEL DE FILTROS ===================== -->
      <div class="card filtros">
        <div class="filtro-grid">
          <div class="campo">
            <label>Desde</label>
            <input type="date" [(ngModel)]="desde">
          </div>
          <div class="campo">
            <label>Hasta</label>
            <input type="date" [(ngModel)]="hasta">
          </div>

          <ng-container *ngIf="tab==='pedidos'">
            <div class="campo">
              <label>Estado</label>
              <select [(ngModel)]="estado">
                <option value="">Todos</option>
                <option value="pendiente">Pendiente</option>
                <option value="procesado">Procesado</option>
                <option value="enviado">Enviado</option>
                <option value="entregado">Entregado</option>
                <option value="anulado">Anulado</option>
              </select>
            </div>
            <div class="campo ancho">
              <label>Región de destino</label>
              <div class="opciones">
                <button type="button" class="op" [class.on]="!regionDestino"
                        (click)="regionDestino = ''">Todas</button>
                <button type="button" class="op" *ngFor="let r of regiones"
                        [class.on]="regionDestino === r" (click)="regionDestino = r">{{ r }}</button>
              </div>
            </div>
          </ng-container>

          <ng-container *ngIf="tab==='ventas'">
            <div class="campo">
              <label>Categoría</label>
              <select [(ngModel)]="idCategoria">
                <option [ngValue]="null">Todas</option>
                <option *ngFor="let c of categorias" [ngValue]="c.idCategoria">{{ c.nombre }}</option>
              </select>
            </div>
          </ng-container>

          <ng-container *ngIf="tab==='movimientos'">
            <div class="campo">
              <label>Bodega</label>
              <select [(ngModel)]="idBodega">
                <option [ngValue]="null">Todas</option>
                <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{ b.nombre }}</option>
              </select>
            </div>
            <div class="campo">
              <label>Tipo de Movimiento</label>
              <select [(ngModel)]="tipoMovimiento">
                <option value="">Todos</option>
                <option value="entrada">Entrada</option>
                <option value="salida">Salida</option>
                <option value="ajuste">Ajuste</option>
                <option value="traslado">Traslado</option>
              </select>
            </div>
          </ng-container>

          <ng-container *ngIf="tab==='costos'">
            <div class="campo">
              <label>Categoría</label>
              <select [(ngModel)]="idCategoria">
                <option [ngValue]="null">Todas</option>
                <option *ngFor="let c of categorias" [ngValue]="c.idCategoria">{{ c.nombre }}</option>
              </select>
            </div>
          </ng-container>

          <ng-container *ngIf="tab==='consumoMp'">
            <div class="campo">
              <label>Materia Prima</label>
              <select [(ngModel)]="idMateriaPrima">
                <option [ngValue]="null">Todas</option>
                <option *ngFor="let m of materiasPrimas" [ngValue]="m.idMateriaPrima">{{ m.nombre }}</option>
              </select>
            </div>
          </ng-container>

          <ng-container *ngIf="tab==='eficiencia'">
            <div class="campo">
              <label>Categoría</label>
              <select [(ngModel)]="idCategoria">
                <option [ngValue]="null">Todas</option>
                <option *ngFor="let c of categorias" [ngValue]="c.idCategoria">{{ c.nombre }}</option>
              </select>
            </div>
          </ng-container>

          <div class="campo">
            <label>Cuántas filas traer</label>
            <input type="number" [(ngModel)]="limite" min="1" max="1000">
            <small class="pista">
              Máximo 1000. <strong>También limita lo que se exporta</strong>: si pones
              100, el Excel trae 100 filas, no todas.
            </small>
          </div>
        </div>

        <div class="acciones">
          <button class="btn btn-primary" (click)="vistaPrevia()" [disabled]="cargando">
            {{ cargando ? 'Cargando…' : 'Vista previa' }}
          </button>
          <button class="btn btn-excel" (click)="exportar('excel')" [disabled]="generando">
            {{ generando ? 'Generando…' : 'Exportar Excel' }}
          </button>
          <button class="btn btn-pdf" (click)="exportar('pdf')" [disabled]="generando">
            {{ generando ? 'Generando…' : 'Exportar PDF' }}
          </button>
        </div>

        <div class="toast" *ngIf="mensaje" [class.error]="esError">{{ mensaje }}</div>
      </div>

      <!-- ===================== TABLA DE RESULTADOS ===================== -->
      <div class="card resultados" *ngIf="!cargando">
        <div class="aviso" *ngIf="aviso">{{ aviso }}</div>

        <div class="resultado-cab" *ngIf="filasTraidas > 0">
          <span class="r-cifra"><strong>{{ filasTraidas | number }}</strong>
            {{ filasTraidas === 1 ? 'fila' : 'filas' }}</span>
          <span class="r-nota" *ngIf="filasTraidas > 100">
            En pantalla se ven las 100 primeras; la exportación las trae todas
            (hasta el límite que hayas puesto).
          </span>
          <span class="r-nota" *ngIf="filasTraidas === limite">
            Justo el límite: es posible que haya más y estén quedando fuera.
          </span>
        </div>

        <p class="sin-nada" *ngIf="filasTraidas === 0 && consultado">
          No hay nada que enseñar con esos filtros entre
          {{ desde || '(sin fecha inicial)' }} y {{ hasta || '(sin fecha final)' }}.
          Prueba a ensanchar las fechas o a quitar algún filtro.
        </p>

        <!-- PEDIDOS -->
        <table class="tabla" *ngIf="tab==='pedidos' && pedidos.length > 0">
          <thead>
            <tr>
              <th># Pedido</th><th>Fecha</th><th>Cliente</th><th>Ciudad</th><th>Estado</th>
              <th>Región</th><th>Transportista</th><th class="num">Total</th><th class="num">Descuento</th>
              <th>Especial</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let p of pedidos.slice(0, 100)">
              <td>{{ p.idPedido }}</td>
              <td>{{ p.fechaPedido | date:'dd/MM/yyyy HH:mm' }}</td>
              <td>{{ p.cliente }}</td>
              <td>{{ p.ciudad }}</td>
              <td>{{ p.estado }}</td>
              <td>{{ p.regionDestino }}</td>
              <td>{{ p.transportista }}</td>
              <td class="num">{{ p.total | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ p.descuento | currency:'USD':'symbol':'1.2-2' }}</td>
              <td>{{ p.esPedidoEspecial ? (p.tipoEspecial || 'Sí') : 'No' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="7" class="num"><strong>TOTAL</strong></td>
              <td class="num"><strong>{{ totalPedidos | currency:'USD':'symbol':'1.2-2' }}</strong></td>
              <td colspan="2"></td>
            </tr>
          </tfoot>
        </table>

        <!-- VENTAS -->
        <table class="tabla" *ngIf="tab==='ventas' && ventas.length > 0">
          <thead>
            <tr>
              <th>#</th><th>Producto</th><th>Categoría</th><th>Unidad</th>
              <th class="num">Cant. Vendida</th><th class="num">Total Ingresos</th>
              <th class="num">Precio Promedio</th><th class="num"># Pedidos</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let v of ventas.slice(0, 100); let i = index">
              <td>{{ i + 1 }}</td>
              <td>{{ v.nombreProducto }}</td>
              <td>{{ v.categoria }}</td>
              <td>{{ v.unidadMedida }}</td>
              <td class="num">{{ v.cantidadVendida }}</td>
              <td class="num">{{ v.totalIngresos | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ v.precioPromedio | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ v.numeroPedidos }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="4" class="num"><strong>TOTAL</strong></td>
              <td class="num"><strong>{{ totalCantidadVendida }}</strong></td>
              <td class="num"><strong>{{ totalIngresos | currency:'USD':'symbol':'1.2-2' }}</strong></td>
              <td colspan="2"></td>
            </tr>
          </tfoot>
        </table>

        <!-- MOVIMIENTOS -->
        <table class="tabla" *ngIf="tab==='movimientos' && movimientos.length > 0">
          <thead>
            <tr>
              <th>#</th><th>Fecha</th><th>Tipo</th><th>Producto</th><th>Bodega</th>
              <th>Bodega Destino</th><th class="num">Cantidad</th><th>Usuario</th><th>Observación</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of movimientos.slice(0, 100); let i = index">
              <td>{{ i + 1 }}</td>
              <td>{{ m.fecha | date:'dd/MM/yyyy HH:mm' }}</td>
              <td>{{ m.tipoMovimiento }}</td>
              <td>{{ m.producto }}</td>
              <td>{{ m.bodega }}</td>
              <td>{{ m.bodegaDestino }}</td>
              <td class="num">{{ m.cantidad }}</td>
              <td>{{ m.usuario }}</td>
              <td>{{ m.observacion }}</td>
            </tr>
          </tbody>
        </table>

        <!-- COSTOS DE PRODUCCIÓN (F29) -->
        <table class="tabla" *ngIf="tab==='costos' && costos.length > 0">
          <thead>
            <tr>
              <th>OP #</th><th>Producto</th><th class="num">Cant. Producida</th>
              <th class="num">Costo MP</th><th class="num">Mano de Obra</th><th class="num">Indirectos</th>
              <th class="num">Costo Total</th><th class="num">Costo Unitario</th><th>Fecha</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let c of costos.slice(0, 100)">
              <td>{{ c.idOrdenProduccion }}</td>
              <td>{{ c.producto }}</td>
              <td class="num">{{ c.cantidadProducida }}</td>
              <td class="num">{{ c.costoMateriaPrima | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ c.costoManoObra | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ c.costoIndirecto | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ c.costoTotal | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ c.costoUnitario | currency:'USD':'symbol':'1.2-4' }}</td>
              <td>{{ c.fecha | date:'dd/MM/yyyy HH:mm' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="6" class="num"><strong>TOTAL</strong></td>
              <td class="num"><strong>{{ totalCostoProduccion | currency:'USD':'symbol':'1.2-2' }}</strong></td>
              <td colspan="2"></td>
            </tr>
          </tfoot>
        </table>

        <!-- CONSUMO DE MATERIA PRIMA (F30) -->
        <table class="tabla" *ngIf="tab==='consumoMp' && consumoMp.length > 0">
          <thead>
            <tr>
              <th>#</th><th>Materia Prima</th><th>Unidad</th>
              <th class="num">Cantidad Consumida</th><th class="num">Costo Consumido</th><th class="num"># Órdenes</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let m of consumoMp.slice(0, 100); let i = index">
              <td>{{ i + 1 }}</td>
              <td>{{ m.nombreMateriaPrima }}</td>
              <td>{{ m.unidadMedida }}</td>
              <td class="num">{{ m.cantidadConsumidaTotal | number:'1.0-3' }}</td>
              <td class="num">{{ m.costoConsumidoTotal | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ m.numeroOrdenes }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="4" class="num"><strong>TOTAL</strong></td>
              <td class="num"><strong>{{ totalCostoConsumido | currency:'USD':'symbol':'1.2-2' }}</strong></td>
              <td></td>
            </tr>
          </tfoot>
        </table>

        <!-- EFICIENCIA DE PRODUCCIÓN (F30) -->
        <table class="tabla" *ngIf="tab==='eficiencia' && eficiencia.length > 0">
          <thead>
            <tr>
              <th>OP #</th><th>Producto</th><th class="num">Planificada</th><th class="num">Producida</th>
              <th class="num">Eficiencia</th><th class="num">Merma MP</th>
              <th class="num">Costo Total</th><th class="num">Costo Unitario</th><th>Fecha Fin</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let e of eficiencia.slice(0, 100)">
              <td>{{ e.idOrdenProduccion }}</td>
              <td>{{ e.producto }}</td>
              <td class="num">{{ e.cantidadPlanificada }}</td>
              <td class="num">{{ e.cantidadProducida }}</td>
              <td class="num" [ngClass]="claseEficiencia(e.eficienciaProduccion)">{{ e.eficienciaProduccion | number:'1.2-2' }} %</td>
              <td class="num">{{ e.mermaTotalMateriaPrima | number:'1.0-3' }}</td>
              <td class="num">{{ e.costoTotal | currency:'USD':'symbol':'1.2-2' }}</td>
              <td class="num">{{ e.costoUnitario | currency:'USD':'symbol':'1.2-4' }}</td>
              <td>{{ e.fechaFin | date:'dd/MM/yyyy' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="6" class="num"><strong>TOTAL</strong></td>
              <td class="num"><strong>{{ totalCostoEficiencia | currency:'USD':'symbol':'1.2-2' }}</strong></td>
              <td colspan="2"></td>
            </tr>
          </tfoot>
        </table>

        <div class="empty" *ngIf="sinDatos">No hay datos para los filtros seleccionados.</div>
      </div>

      <div class="card cargando-card" *ngIf="cargando">
        <div class="spinner"></div>
        <span>Cargando vista previa…</span>
      </div>
    </div>
  `,
  styles: [`
    /* ── Cabecera y explicación (F81) ──────────────────────────── */
    .rep-cab { margin-bottom: 1.25rem; }
    .rep-cab h1 { margin: 0 0 .3rem; font-size: 1.7rem; color: var(--ms-text); }
    .rep-sub { margin: 0; color: var(--ms-text-muted); font-size: .92rem; line-height: 1.6; }
    .rep-sub a { color: var(--ms-gold); text-decoration: none; }
    .rep-sub a:hover { text-decoration: underline; }

    /* La pregunta que contesta el informe abierto. Sin esto, seis pestañas
       son seis nombres y hay que abrirlas para saber cuál sirve. */
    .rep-que { margin: .9rem 0 1.1rem; padding: .7rem 1rem; font-size: .85rem;
               line-height: 1.6; color: var(--ms-text-muted);
               background: rgba(255,255,255,0.02); border-left: 2px solid var(--ms-gold);
               border-radius: 0 var(--ms-radius-sm) var(--ms-radius-sm) 0; max-width: 90ch; }

    .campo.ancho { grid-column: span 2; min-width: 260px; }
    .opciones { display: flex; gap: .35rem; flex-wrap: wrap; }
    .op { background: rgba(255,255,255,0.03); border: 1px solid var(--ms-border);
          color: var(--ms-text-muted); padding: .5rem .85rem; border-radius: var(--ms-radius-sm);
          font-size: .82rem; cursor: pointer; font-family: inherit; }
    .op:hover { border-color: rgba(255,255,255,0.25); color: var(--ms-text); }
    .op.on { background: var(--ms-gold-dim); border-color: var(--ms-gold);
             color: var(--ms-gold-light); font-weight: 600; }

    .pista { display: block; margin-top: .35rem; font-size: .72rem; line-height: 1.5;
             color: var(--ms-text-muted); }
    .pista strong { color: rgba(255,255,255,0.75); }

    .resultado-cab { display: flex; align-items: baseline; gap: 1rem; flex-wrap: wrap;
                     margin-bottom: 1rem; padding-bottom: .8rem;
                     border-bottom: 1px solid var(--ms-border); }
    .r-cifra { font-size: .95rem; color: var(--ms-text-muted); }
    .r-cifra strong { color: var(--ms-gold-light); font-size: 1.2rem; }
    .r-nota { font-size: .78rem; color: var(--ms-text-muted); }

    .sin-nada { margin: 0; padding: 2.5rem 1rem; text-align: center; font-size: .9rem;
                line-height: 1.7; color: var(--ms-text-muted); }

    /* La cabecera no se parte: "TRANSPORTIST A" y "DESCUENT O" en dos renglones
       dejan de ser palabras. El texto de las celdas sí sigue partiendo, que es
       lo que mantiene la tabla estrecha. */
    .resultados { overflow-x: auto; }
    .resultados .tabla th { white-space: nowrap; }
    .resultados .tabla td.num { white-space: nowrap; }

    .ef-alta { color: #4ade80; font-weight: 600; }
    .ef-media { color: #fbbf24; font-weight: 600; }
    .ef-baja { color: #f87171; font-weight: 600; }
  `]
})
export class ReportesComponent implements OnInit {

  private apiUrl = environment.apiUrl;

  tab: 'pedidos' | 'ventas' | 'movimientos' | 'costos' | 'consumoMp' | 'eficiencia' = 'pedidos';

  /**
   * Qué contesta cada informe (F81).
   *
   * <p>Antes las seis pestañas eran seis nombres: para saber cuál servía había
   * que abrirlas una a una y pulsar «Vista previa». La frase de debajo dice qué
   * pregunta responde la que está abierta.
   */
  readonly informes = [
    { clave: 'pedidos' as const,     titulo: 'Pedidos',
      contesta: 'Un pedido por fila, con su cliente, su ciudad, su estado y lo que costó. Sirve para revisar un período concreto o sacar la lista de lo vendido a un cliente.' },
    { clave: 'ventas' as const,      titulo: 'Ventas por producto',
      contesta: 'Cuánto se vendió de cada producto, en unidades y en dinero, con el precio medio al que salió. Es el detalle que hay detrás de «lo que más sale».' },
    { clave: 'movimientos' as const, titulo: 'Movimientos de inventario',
      contesta: 'Cada entrada, salida, ajuste y traslado de stock, con quién lo hizo y por qué. Es de dónde salen y a dónde van las unidades.' },
    { clave: 'costos' as const,      titulo: 'Costos de producción',
      contesta: 'Lo que costó cada orden de producción: materia prima, mano de obra e indirectos, y el coste por unidad que sale de ahí.' },
    { clave: 'consumoMp' as const,   titulo: 'Consumo de materia prima',
      contesta: 'Cuánta materia prima se gastó y en cuántas órdenes. Sirve para saber qué hay que reponer antes de que pare la producción.' },
    { clave: 'eficiencia' as const,  titulo: 'Eficiencia de producción',
      contesta: 'Lo planificado frente a lo producido en cada orden, con la merma. Por debajo del 80 % sale en rojo.' }
  ];

  /** Las cuatro regiones del Ecuador; la de destino sale de la ciudad (F77). */
  readonly regiones = ['Costa', 'Sierra', 'Oriente', 'Insular'];

  /** Si ya se pidió algo: distingue «no hay nada» de «todavía no has buscado». */
  consultado = false;

  // filtros
  desde: string | null = null;
  hasta: string | null = null;
  estado = '';
  regionDestino = '';
  idCategoria: number | null = null;
  idBodega: number | null = null;
  tipoMovimiento = '';
  limite = 100;

  // catálogos
  categorias: Categoria[] = [];
  bodegas: Bodega[] = [];

  // resultados
  pedidos: PedidoItem[] = [];
  ventas: VentaProductoItem[] = [];
  movimientos: MovimientoItem[] = [];
  costos: CostoProduccionItem[] = [];
  consumoMp: ConsumoMpItem[] = [];
  eficiencia: EficienciaItem[] = [];
  materiasPrimas: MateriaPrimaOpt[] = [];
  idMateriaPrima: number | null = null;

  cargando = false;
  generando = false;
  mensaje = '';
  esError = false;
  aviso = '';
  sinDatos = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.cargarCategorias();
    this.cargarBodegas();
    this.cargarMateriasPrimas();
    // Un mes por defecto y se busca solo: la pantalla abría en blanco y había
    // que adivinar que hacía falta pulsar «Vista previa» para ver algo.
    const hoy = new Date();
    const hace30 = new Date(hoy.getTime() - 29 * 24 * 3600 * 1000);
    this.hasta = this.comoFecha(hoy);
    this.desde = this.comoFecha(hace30);
    this.vistaPrevia();
  }

  private comoFecha(d: Date): string {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
         + '-' + String(d.getDate()).padStart(2, '0');
  }

  get informeActual() {
    return this.informes.find(i => i.clave === this.tab);
  }

  /** Cuántas filas trajo la consulta, sea cual sea la pestaña. */
  get filasTraidas(): number {
    switch (this.tab) {
      case 'pedidos': return this.pedidos.length;
      case 'ventas': return this.ventas.length;
      case 'movimientos': return this.movimientos.length;
      case 'costos': return this.costos.length;
      case 'consumoMp': return this.consumoMp.length;
      case 'eficiencia': return this.eficiencia.length;
      default: return 0;
    }
  }

  cargarMateriasPrimas(): void {
    this.http.get<any>(`${this.apiUrl}/materia-prima?page=0&size=1000&estado=activo`).subscribe({
      next: res => { this.materiasPrimas = res?.content ?? []; },
      error: () => { /* puede no tener permiso de lectura de materia prima */ }
    });
  }

  claseEficiencia(v: number): string {
    if (v >= 95) { return 'ef-alta'; }
    if (v >= 80) { return 'ef-media'; }
    return 'ef-baja';
  }

  cargarCategorias(): void {
    this.http.get<any>(`${this.apiUrl}/categorias?page=0&size=1000`).subscribe({
      next: res => { this.categorias = res?.content ?? []; }
    });
  }

  cargarBodegas(): void {
    this.http.get<Bodega[]>(`${this.apiUrl}/bodegas/activas`).subscribe({
      next: res => { this.bodegas = res ?? []; }
    });
  }

  cambiarTab(t: 'pedidos' | 'ventas' | 'movimientos' | 'costos' | 'consumoMp' | 'eficiencia'): void {
    this.tab = t;
    this.pedidos = [];
    this.ventas = [];
    this.movimientos = [];
    this.costos = [];
    this.consumoMp = [];
    this.eficiencia = [];
    this.aviso = '';
    this.mensaje = '';
    this.sinDatos = false;
    this.consultado = false;
    // Cambiar de informe y quedarse mirando una tabla vacía no tiene sentido:
    // los filtros comunes -las fechas- siguen valiendo.
    this.vistaPrevia();
  }

  get totalPedidos(): number {
    return this.pedidos.reduce((acc, p) => acc + (p.total || 0), 0);
  }
  get totalCantidadVendida(): number {
    return this.ventas.reduce((acc, v) => acc + (v.cantidadVendida || 0), 0);
  }
  get totalIngresos(): number {
    return this.ventas.reduce((acc, v) => acc + (v.totalIngresos || 0), 0);
  }
  get totalCostoProduccion(): number {
    return this.costos.reduce((acc, c) => acc + (c.costoTotal || 0), 0);
  }
  get totalCostoConsumido(): number {
    return this.consumoMp.reduce((acc, m) => acc + (m.costoConsumidoTotal || 0), 0);
  }
  get totalCostoEficiencia(): number {
    return this.eficiencia.reduce((acc, e) => acc + (e.costoTotal || 0), 0);
  }

  private construirFiltro(): FiltroReporte {
    const filtro: FiltroReporte = { limite: this.acotarLimite() };
    if (this.desde) { filtro.desde = `${this.desde}T00:00:00`; }
    if (this.hasta) { filtro.hasta = `${this.hasta}T23:59:59`; }

    if (this.tab === 'pedidos') {
      filtro.estado = this.estado || null;
      filtro.regionDestino = this.regionDestino || null;
    } else if (this.tab === 'ventas') {
      filtro.idCategoria = this.idCategoria ?? null;
    } else if (this.tab === 'movimientos') {
      filtro.idBodega = this.idBodega ?? null;
      filtro.estado = this.tipoMovimiento || null;
    } else if (this.tab === 'costos') {
      filtro.idCategoria = this.idCategoria ?? null;
    } else if (this.tab === 'consumoMp') {
      filtro.idMateriaPrima = this.idMateriaPrima ?? null;
    } else if (this.tab === 'eficiencia') {
      filtro.idCategoria = this.idCategoria ?? null;
    }
    return filtro;
  }

  private acotarLimite(): number {
    let l = Number(this.limite) || 100;
    if (l < 1) { l = 1; }
    if (l > 1000) { l = 1000; }
    return l;
  }

  vistaPrevia(): void {
    this.cargando = true;
    this.mensaje = '';
    this.aviso = '';
    this.sinDatos = false;
    const filtro = this.construirFiltro();

    this.http.post<any[]>(`${this.apiUrl}/reportes/${this.endpointTipo()}/preview`, filtro).subscribe({
      next: res => {
        const datos = res ?? [];
        if (this.tab === 'pedidos') { this.pedidos = datos; }
        else if (this.tab === 'ventas') { this.ventas = datos; }
        else if (this.tab === 'costos') { this.costos = datos; }
        else if (this.tab === 'consumoMp') { this.consumoMp = datos; }
        else if (this.tab === 'eficiencia') { this.eficiencia = datos; }
        else { this.movimientos = datos; }
        this.cargando = false;
        this.consultado = true;
        this.sinDatos = datos.length === 0;
        // El aviso que habia aqui decia que el archivo exportado incluiria
        // TODOS los registros, y es falso: la exportacion manda el mismo
        // filtro, con el mismo limite. Quien exportaba con limite 100 se
        // llevaba un Excel de 100 filas creyendo que estaban todas. Lo que
        // hace falta decir esta ahora en la cabecera del resultado, y es lo
        // que de verdad pasa.
        this.aviso = "";
      },
      error: () => {
        this.cargando = false;
        this.mostrarMensaje('Error al cargar la vista previa.', true);
      }
    });
  }

  exportar(formato: 'excel' | 'pdf'): void {
    this.generando = true;
    this.mensaje = '';
    const filtro = this.construirFiltro();
    const url = `${this.apiUrl}/reportes/${this.endpointTipo()}/${formato}`;

    this.http.post(url, filtro, { responseType: 'blob' }).subscribe({
      next: (blob: Blob) => {
        this.descargar(blob, this.nombreArchivo(formato));
        this.generando = false;
        this.mostrarMensaje('Reporte generado correctamente.', false);
      },
      error: () => {
        this.generando = false;
        this.mostrarMensaje('Error al generar el reporte.', true);
      }
    });
  }

  private endpointTipo(): string {
    if (this.tab === 'pedidos') { return 'pedidos'; }
    if (this.tab === 'ventas') { return 'ventas-producto'; }
    if (this.tab === 'costos') { return 'costos-produccion'; }
    // F30 — viven bajo /api/reportes/manufactura/
    if (this.tab === 'consumoMp') { return 'manufactura/consumo-materia-prima'; }
    if (this.tab === 'eficiencia') { return 'manufactura/eficiencia-produccion'; }
    return 'movimientos';
  }

  private nombreArchivo(formato: 'excel' | 'pdf'): string {
    const ext = formato === 'excel' ? 'xlsx' : 'pdf';
    let base = `reporte-${this.tab}`;
    if (this.tab === 'ventas') { base = 'reporte-ventas-producto'; }
    else if (this.tab === 'costos') { base = 'reporte-costos-produccion'; }
    else if (this.tab === 'consumoMp') { base = 'reporte-consumo-materia-prima'; }
    else if (this.tab === 'eficiencia') { base = 'reporte-eficiencia-produccion'; }
    return `${base}.${ext}`;
  }

  private descargar(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

  private mostrarMensaje(texto: string, error: boolean): void {
    this.mensaje = texto;
    this.esError = error;
    setTimeout(() => { this.mensaje = ''; }, 4000);
  }
}
