import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
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

@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="reportes">
      <h1>Reportes — Marathon Sports</h1>

      <div class="tabs">
        <button [class.active]="tab==='pedidos'" (click)="cambiarTab('pedidos')">Reporte de Pedidos</button>
        <button [class.active]="tab==='ventas'" (click)="cambiarTab('ventas')">Ventas por Producto</button>
        <button [class.active]="tab==='movimientos'" (click)="cambiarTab('movimientos')">Movimientos de Inventario</button>
      </div>

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
            <div class="campo">
              <label>Región Destino</label>
              <input type="text" [(ngModel)]="regionDestino" placeholder="Ej: Sierra">
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

          <div class="campo">
            <label>Límite (máx 1000)</label>
            <input type="number" [(ngModel)]="limite" min="1" max="1000">
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
      <div class="card" *ngIf="!cargando">
        <div class="aviso" *ngIf="aviso">{{ aviso }}</div>

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

        <div class="empty" *ngIf="sinDatos">No hay datos para los filtros seleccionados.</div>
      </div>

      <div class="card cargando-card" *ngIf="cargando">
        <div class="spinner"></div>
        <span>Cargando vista previa…</span>
      </div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class ReportesComponent implements OnInit {

  private apiUrl = environment.apiUrl;

  tab: 'pedidos' | 'ventas' | 'movimientos' = 'pedidos';

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

  cambiarTab(t: 'pedidos' | 'ventas' | 'movimientos'): void {
    this.tab = t;
    this.pedidos = [];
    this.ventas = [];
    this.movimientos = [];
    this.aviso = '';
    this.mensaje = '';
    this.sinDatos = false;
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
        else { this.movimientos = datos; }
        this.cargando = false;
        this.sinDatos = datos.length === 0;
        if (datos.length >= 100) {
          this.aviso = `Mostrando ${Math.min(datos.length, 100)} de ${datos.length} resultados. ` +
            `El archivo exportado incluirá todos los registros.`;
        }
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
    return 'movimientos';
  }

  private nombreArchivo(formato: 'excel' | 'pdf'): string {
    const ext = formato === 'excel' ? 'xlsx' : 'pdf';
    const base = this.tab === 'ventas' ? 'reporte-ventas-producto' : `reporte-${this.tab}`;
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
