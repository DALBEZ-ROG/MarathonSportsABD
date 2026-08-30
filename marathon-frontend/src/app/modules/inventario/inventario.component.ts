import { Component, OnInit } from '@angular/core';
import { AuthService } from '../../core/services/auth.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AppIconComponent } from '../../shared/components/icon/icon.component';
import { SearchableSelectComponent } from '../../shared/components/searchable-select/searchable-select.component';
import { ModalSeguroDirective } from '../../shared/directives/modal-seguro.directive';
import { EstadoListaComponent } from '../../shared/components/estado-lista/estado-lista.component';

interface Bodega {
  idBodega: number;
  nombre: string;
}

interface Inventario {
  idInventario: number;
  productoId: number;
  productoNombre: string;
  bodegaId: number;
  bodegaNombre: string;
  cantidad: number;
  updatedAt: string;
}

interface Movimiento {
  idMovimiento: number;
  idProducto: number;
  productoNombre: string;
  idBodega: number;
  bodegaNombre: string;
  tipoMovimiento: string;
  cantidad: number;
  idUsuario: number;
  usuarioNombre: string;
  fecha: string;
}

interface Historial {
  idHistorial: number;
  cantidadAnterior: number;
  cantidadNueva: number;
  fechaCambio: string;
  tipoOperacion: string;
}

/**
 * Una reserva de stock (F47, D-02). El backend ya manda `diasRetenida`
 * calculado: la resta se hace en un solo sitio para que no haya dos respuestas
 * a "¿cuánto lleva esto parado?".
 */
interface Reserva {
  idReserva: number;
  idPedido: number;
  numeroPedido: string;
  estadoPedido: string;
  productoNombre: string;
  cantidad: number;
  estado: string;
  fechaReserva: string;
  diasRetenida: number;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent, SearchableSelectComponent, ModalSeguroDirective, EstadoListaComponent],
  template: `
    <div class="crud-container">
      <!-- Stock bajo alert -->
      <div class="alert-banner" *ngIf="stockBajoTotal > 0">
        <strong class="inline-icon-text"><app-icon name="warning" [size]="16"/> Alerta de Stock Bajo:</strong> {{stockBajoTotal | number}} referencia(s) en o por debajo de su stock mínimo
      </div>

      <!--
        Reservas vencidas (F47, D-02). Solo aparece si hay alguna: un aviso que
        sale siempre deja de leerse. Vencer NO libera nada — la decisión de
        negocio del 2026-08-27 es que lo suelta una persona, y este banner es
        precisamente cómo esa persona se entera.
      -->
      <div class="alert-banner" *ngIf="reservasVencidas.length > 0">
        <strong class="inline-icon-text"><app-icon name="warning" [size]="16"/> Reservas vencidas:</strong>
        {{reservasVencidas.length}} reserva(s) llevan más de {{DIAS_VIGENCIA}} días reteniendo mercancía
        de pedidos que siguen sin despacharse.
        <button class="btn-link" (click)="showReservas = true">Ver y decidir</button>
      </div>

      <div class="toolbar">
        <h2>Inventario</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="busqueda" (input)="onBuscar()"
                 placeholder="Buscar por producto o bodega..." class="input-search"/>
          <select [(ngModel)]="filtroBodega" (change)="cargar()" class="select-filter">
            <option [ngValue]="null">Todas las bodegas</option>
            <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{b.nombre}}</option>
          </select>
        </div>
        <button class="btn-new" (click)="abrirModalMovimiento()">+ Movimiento</button>
      </div>

      <app-estado-lista
        [cargando]="loading"
        [error]="cargaError"
        [vacio]="!loading && !cargaError && data.length === 0"
        [hayFiltro]="hayFiltroPuesto"
        nombrePlural="filas de inventario"
        pistaVacio="El inventario se crea al recibir mercancía."
        (reintentar)="cargar()"></app-estado-lista>

      <table class="data-table" *ngIf="!loading && !cargaError && data.length > 0">
        <thead>
          <tr><th>Producto</th><th>Bodega</th><th>Cantidad</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.productoNombre}}</td>
            <td>{{item.bodegaNombre}}</td>
            <td><strong>{{item.cantidad}}</strong></td>
            <td>
              <span class="badge" [class.active]="item.cantidad > 5" [class.warning]="item.cantidad > 0 && item.cantidad <= 5" [class.danger]="item.cantidad === 0">
                {{item.cantidad === 0 ? 'Sin stock' : item.cantidad <= 5 ? 'Bajo' : 'Normal'}}
              </span>
            </td>
            <td class="actions">
              <button class="btn-icon" (click)="verHistorial(item)" title="Historial"><app-icon name="clipboard" [size]="16"/></button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <!-- Modal Movimiento -->
      <div class="modal-overlay" *ngIf="showMovModal" appModalSeguro (cerrar)="cerrarModalMovimiento()">
        <div class="modal-card ancho" (click)="$event.stopPropagation()">
          <h3>Mover stock</h3>
          <p class="mov-sub">
            Para lo que entra o sale <strong>fuera del flujo normal</strong>. Lo que llega
            de una orden de compra se registra en su recepción, y lo que sale por un
            pedido, en el empaque.
          </p>

          <form (ngSubmit)="guardarMovimiento()">

            <!-- 1. Qué se hace -->
            <span class="paso-etq">1 · Qué quieres hacer</span>
            <div class="tipos">
              <button type="button" class="tipo" *ngFor="let t of tiposMovimiento"
                      [class.on]="movForm.tipoMovimiento === t.valor"
                      (click)="elegirTipo(t.valor)">
                <strong>{{ t.titulo }}</strong>
                <span>{{ t.explica }}</span>
              </button>
            </div>

            <ng-container *ngIf="movForm.tipoMovimiento">

              <!-- 2. Sobre qué -->
              <span class="paso-etq">2 · Sobre qué producto</span>
              <div class="form-group">
                <label>Producto *</label>
                <app-searchable-select [(ngModel)]="movForm.idProducto" name="idProducto"
                  [items]="productos" valueKey="idProducto"
                  placeholder="Escribe el nombre del producto…"
                  (ngModelChange)="alCambiarProductoOBodega()"/>
              </div>

              <div class="form-group">
                <label>{{ movForm.tipoMovimiento === 'traslado' ? 'Bodega de origen *' : 'Bodega *' }}</label>
                <app-searchable-select [(ngModel)]="movForm.idBodega" name="idBodega" [items]="bodegas"
                  valueKey="idBodega" placeholder="Escribe la bodega…"
                  (ngModelChange)="alCambiarProductoOBodega()"/>
              </div>

              <div class="form-group" *ngIf="movForm.tipoMovimiento === 'traslado'">
                <label>Bodega de destino *</label>
                <app-searchable-select [(ngModel)]="movForm.idBodegaDestino" name="idBodegaDestino"
                  [items]="bodegas" valueKey="idBodega" placeholder="Escribe la bodega…"/>
              </div>

              <!-- El stock que hay ahora -->
              <div class="stock-actual" *ngIf="stockConocido !== null">
                <span>Ahí hay ahora</span>
                <strong>{{ stockConocido }} unidad(es)</strong>
              </div>
              <p class="stock-nuevo-aviso" *ngIf="stockConocido === null && movForm.idProducto && movForm.idBodega">
                Ese producto todavía no tiene existencias en esa bodega. Una
                <strong>entrada</strong> creará la ficha; los demás movimientos no tienen
                de dónde sacar unidades.
              </p>

              <!-- 3. Cuánto -->
              <span class="paso-etq">3 · Cuánto</span>
              <div class="form-group">
                <label>{{ etiquetaCantidad() }} *</label>
                <input type="number" [(ngModel)]="movForm.cantidad" name="cantidad" required
                       [min]="movForm.tipoMovimiento === 'ajuste' ? 0 : 1"/>
                <small class="pista" *ngIf="movForm.tipoMovimiento === 'ajuste'">
                  <strong>No es la diferencia.</strong> Escribe el total que has contado en
                  la estantería; el sistema calcula solo cuánto sobra o falta.
                </small>
              </div>

              <!-- Cómo queda -->
              <div class="resultado" *ngIf="stockResultante() !== null">
                <span class="antes">{{ stockConocido || 0 }}</span>
                <span class="flecha" aria-hidden="true">→</span>
                <span class="despues" [class.sube]="delta() > 0" [class.baja]="delta() < 0">
                  {{ stockResultante() }}
                </span>
                <span class="delta" *ngIf="delta() !== 0">
                  ({{ delta() > 0 ? '+' : '' }}{{ delta() }})
                </span>
                <span class="destino" *ngIf="movForm.tipoMovimiento === 'traslado' && movForm.idBodegaDestino">
                  y {{ movForm.cantidad }} pasan a {{ nombreBodega(movForm.idBodegaDestino) }}
                </span>
              </div>

              <div class="form-group">
                <label>Motivo</label>
                <input type="text" [(ngModel)]="movForm.observacion" name="observacion"
                       [placeholder]="pistaMotivo()"/>
                <small class="pista">Queda en el kardex. Dentro de un mes, es lo único que explicará este movimiento.</small>
              </div>

              <p class="aviso-reserva" *ngIf="puedeChocarConReservas()">
                Si estas unidades están <strong>reservadas</strong> por un pedido ya
                procesado, el sistema lo impedirá: esa mercancía tiene dueño.
              </p>
            </ng-container>

            <small class="error" *ngIf="movError">{{movError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="cerrarModalMovimiento()">Cancelar</button>
              <button type="submit" class="btn-save" [disabled]="saving || !movForm.tipoMovimiento">
                {{ saving ? 'Registrando…' : 'Registrar movimiento' }}
              </button>
            </div>
          </form>
        </div>
      </div>

      <!-- Modal Historial -->
      <div class="modal-overlay" *ngIf="showHistorial" appModalSeguro (cerrar)="showHistorial=false">
        <div class="modal-card wide" (click)="$event.stopPropagation()">
          <h3>Historial de Inventario</h3>
          <p class="subtitle">{{historialProducto}}</p>
          <table class="data-table" *ngIf="historialData.length > 0">
            <thead>
              <tr><th>Fecha</th><th>Operación</th><th>Cant. Anterior</th><th>Cant. Nueva</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let h of historialData">
                <td>{{h.fechaCambio | date:'dd/MM/yyyy HH:mm'}}</td>
                <td><span class="badge-op">{{h.tipoOperacion}}</span></td>
                <td>{{h.cantidadAnterior}}</td>
                <td>{{h.cantidadNueva}}</td>
              </tr>
            </tbody>
          </table>
          <p *ngIf="historialData.length === 0" class="empty-text">No hay historial disponible</p>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="showHistorial=false">Cerrar</button>
          </div>
        </div>
      </div>

      <!-- Modal Reservas vencidas (F47, D-02) -->
      <div class="modal-overlay" *ngIf="showReservas" appModalSeguro (cerrar)="showReservas=false">
        <div class="modal-card wide" (click)="$event.stopPropagation()">
          <h3>Reservas vencidas</h3>
          <p class="subtitle">
            Un pedido retiene mercancía desde que pasa a «procesado» y hasta que se despacha
            o se anula. Estas llevan más de {{DIAS_VIGENCIA}} días. <strong>No se sueltan solas</strong>:
            liberar una devuelve esas unidades al disponible y deja el pedido sin respaldo,
            así que la decisión es tuya.
          </p>
          <!--
            Sin columna «Estado»: una reserva activa solo existe mientras el
            pedido está en «procesado» —el despacho la consume y la anulación la
            libera—, así que la columna repetía el mismo valor en cada fila y
            estrechaba a las que sí dicen algo. El nombre del producto es largo y
            necesita ese ancho.
          -->
          <div class="tabla-scroll">
            <table class="data-table tabla-reservas" *ngIf="reservasVencidas.length > 0">
              <thead>
                <tr><th>Pedido</th><th>Producto</th><th>Uds.</th><th>Reservada</th><th>Días</th><th></th></tr>
              </thead>
              <tbody>
                <tr *ngFor="let r of reservasVencidas">
                  <td class="nowrap">{{r.numeroPedido}}</td>
                  <td>{{r.productoNombre}}</td>
                  <td class="nowrap"><strong>{{r.cantidad}}</strong></td>
                  <td class="nowrap">{{r.fechaReserva | date:'dd/MM/yyyy'}}</td>
                  <td class="nowrap">{{r.diasRetenida}}</td>
                  <td class="actions">
                    <button class="btn-icon" (click)="pedirMotivo(r)" title="Liberar esta reserva">
                      <app-icon name="warning" [size]="16"/>
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p *ngIf="reservasVencidas.length === 0" class="empty-text">No hay reservas vencidas.</p>

          <div class="form-group" *ngIf="reservaALiberar">
            <label>Motivo para liberar {{reservaALiberar.cantidad}} unidades de
              «{{reservaALiberar.productoNombre}}» del pedido {{reservaALiberar.numeroPedido}} *</label>
            <input type="text" [(ngModel)]="motivoLiberacion" name="motivoLiberacion"
                   placeholder="Ej.: el cliente desistió, confirmado por teléfono"/>
            <small class="error" *ngIf="reservaError">{{reservaError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="reservaALiberar=null">Cancelar</button>
              <button type="button" class="btn-save" [disabled]="liberando" (click)="liberarReserva()">
                {{liberando ? 'Liberando...' : 'Liberar reserva'}}
              </button>
            </div>
          </div>

          <div class="modal-actions" *ngIf="!reservaALiberar">
            <button class="btn-cancel" (click)="showReservas=false">Cerrar</button>
          </div>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */

    /* Enlace de accion dentro de un banner de aviso (F47). No existe en
       styles.scss y solo lo usa esta pantalla, asi que vive aqui en vez de
       engordar la hoja global. */
    .btn-link {
      background: none;
      border: none;
      padding: 0 0 0 .5rem;
      color: inherit;
      font: inherit;
      font-weight: 600;
      text-decoration: underline;
      cursor: pointer;
    }
    .btn-link:hover { opacity: .8; }

    /* Tabla de reservas vencidas (F47).
       Las cabeceras se pintan en mayúsculas y con letra estrecha, y dentro del
       modal se partían por la mitad —«PEDID O», «UNID ADES»—. Se les prohíbe
       partir, y lo que cede es el nombre del producto, que es lo único que
       admite dos líneas sin quedar ilegible. Si aun así no cabe, la tabla
       desplaza en horizontal en vez de deformar el modal. */
    /* ── Mover stock (F72) ─────────────────────────────────────── */
    .modal-card.ancho { max-width: 620px; }
    .mov-sub { color: rgba(255,255,255,0.55); font-size: .85rem; line-height: 1.6;
               margin: -.3rem 0 1.2rem; }
    .mov-sub strong { color: rgba(255,255,255,0.85); }
    .paso-etq { display: block; font-size: .68rem; text-transform: uppercase;
                letter-spacing: .08em; color: rgba(255,255,255,0.4);
                margin: 1.2rem 0 .6rem; }
    .paso-etq:first-of-type { margin-top: 0; }

    .tipos { display: grid; grid-template-columns: 1fr 1fr; gap: .6rem; }
    .tipo { text-align: left; background: rgba(255,255,255,0.03);
            border: 1px solid rgba(255,255,255,0.1); border-radius: 10px;
            padding: .75rem .9rem; cursor: pointer; transition: all .15s ease;
            display: flex; flex-direction: column; gap: .25rem; }
    .tipo:hover { border-color: rgba(255,255,255,0.22); }
    .tipo strong { color: rgba(255,255,255,0.9); font-size: .92rem; }
    .tipo span { color: rgba(255,255,255,0.5); font-size: .76rem; line-height: 1.45; }
    .tipo.on { border-color: #C9A84C; background: rgba(201,168,76,0.1); }
    .tipo.on strong { color: #F4E28D; }

    .stock-actual { display: flex; justify-content: space-between; align-items: baseline;
                    background: rgba(255,255,255,0.04); border-radius: 8px;
                    padding: .65rem .9rem; margin: .2rem 0 .3rem;
                    font-size: .86rem; color: rgba(255,255,255,0.55); }
    .stock-actual strong { color: rgba(255,255,255,0.9); font-variant-numeric: tabular-nums; }
    .stock-nuevo-aviso { font-size: .8rem; color: rgba(255,255,255,0.5);
                         line-height: 1.55; margin: .2rem 0 .5rem; }
    .stock-nuevo-aviso strong { color: rgba(255,255,255,0.8); }

    .resultado { display: flex; align-items: baseline; gap: .6rem; flex-wrap: wrap;
                 border: 1px solid rgba(201,168,76,0.35); background: rgba(201,168,76,0.06);
                 border-radius: 8px; padding: .7rem .9rem; margin: .2rem 0 1rem;
                 font-variant-numeric: tabular-nums; }
    .resultado .antes { color: rgba(255,255,255,0.5); font-size: 1.05rem; }
    .resultado .flecha { color: rgba(255,255,255,0.35); }
    .resultado .despues { font-size: 1.35rem; font-weight: 600; color: #F4E28D; }
    .resultado .despues.sube { color: #4ade80; }
    .resultado .despues.baja { color: #f87171; }
    .resultado .delta { color: rgba(255,255,255,0.45); font-size: .85rem; }
    .resultado .destino { color: rgba(255,255,255,0.55); font-size: .8rem;
                          flex-basis: 100%; }

    .pista { display: block; font-size: .76rem; color: rgba(255,255,255,0.45);
             line-height: 1.5; margin-top: .3rem; }
    .pista strong { color: rgba(255,255,255,0.8); }
    .aviso-reserva { font-size: .8rem; color: rgba(255,255,255,0.5); line-height: 1.55;
                     border-left: 2px solid rgba(201,168,76,0.5); padding-left: .8rem;
                     margin: 0 0 1rem; }
    .aviso-reserva strong { color: rgba(255,255,255,0.85); }

    @media (max-width: 560px) { .tipos { grid-template-columns: 1fr; } }

    .tabla-scroll { overflow-x: auto; }
    .tabla-reservas th { white-space: nowrap; }
    .tabla-reservas td.nowrap { white-space: nowrap; }
  `]
})
export class InventarioComponent implements OnInit {
  data: Inventario[] = [];
  bodegas: Bodega[] = [];
  /** Cuantas referencias estan bajo minimo. Solo el numero (F94). */
  stockBajoTotal = 0;
  loading = false;
  /**
   * Motivo del fallo de carga, o null si la carga fue bien (D6).
   * Sin esto la pantalla no podia distinguir "no hay registros" de "no se
   * pudo preguntar", y enseñaba lo primero en los dos casos.
   */
  cargaError: string | null = null;

  /** ¿Hay busqueda o filtros puestos? Cambia el mensaje de lista vacia. */
  get hayFiltroPuesto(): boolean { return this.filtroBodega !== null || !!this.busqueda; }

  saving = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroBodega: number | null = null;

  showMovModal = false;
  movForm: any = { tipoMovimiento: '', idProducto: null, idBodega: null, idBodegaDestino: null, cantidad: 1, observacion: '' };
  movError = '';

  /** El catálogo, para poder elegir por nombre en vez de por id (F72). */
  productos: any[] = [];

  /**
   * Stock que hay ahora del producto en la bodega elegida, o null si esa ficha
   * no existe todavía. Es lo que permite enseñar «62 → 68» antes de confirmar.
   */
  stockConocido: number | null = null;

  /**
   * Los cuatro movimientos, explicados.
   *
   * **El que más falta hacía es «ajuste».** Fija un valor ABSOLUTO: se escribe
   * el total contado y el sistema calcula la diferencia. Quien lo lea como
   * «sumar o restar» descuadrará el stock sin enterarse, y hay 10.757 ajustes
   * en la base.
   */
  readonly tiposMovimiento = [
    { valor: 'entrada',  titulo: 'Entrada',
      explica: 'Suma unidades. Para lo que llega sin ser una orden de compra.' },
    { valor: 'salida',   titulo: 'Salida',
      explica: 'Resta unidades. Para roturas, muestras o mermas.' },
    { valor: 'ajuste',   titulo: 'Ajuste por conteo',
      explica: 'Fija el stock a lo que has contado. No suma ni resta.' },
    { valor: 'traslado', titulo: 'Traslado',
      explica: 'Mueve unidades a otra bodega. El total no cambia.' }
  ];

  showHistorial = false;
  historialData: Historial[] = [];
  historialProducto = '';

  // --- Reservas de stock (F47, D-02) ---------------------------------------
  /** Días que aguanta una reserva antes de salir en el informe. Igual que
   *  ReservaStockService.DIAS_VIGENCIA en el backend, que es quien decide. */
  readonly DIAS_VIGENCIA = 7;
  reservasVencidas: Reserva[] = [];
  showReservas = false;
  reservaALiberar: Reserva | null = null;
  motivoLiberacion = '';
  reservaError = '';
  liberando = false;

  toast = '';
  toastError = false;

  private apiUrl = environment.apiUrl;

  /**
   * Roles a los que les toca ver las reservas vencidas (F63).
   *
   * <p>Son los que pueden leer `pedido`, porque el informe une cada reserva con
   * el pedido que la retiene. **Esta lista tiene que decir lo mismo que la regla
   * de `SecurityConfig` para GET /api/inventario/reservas/**.** Si allí cambia,
   * aquí también, o volveremos a pedir algo que siempre va a fallar.
   */
  private static readonly ROLES_CON_RESERVAS = [
    'Administrador', 'Operador de Bodega', 'Operador de Pedidos', 'Supervisor E-Commerce'
  ];

  constructor(private http: HttpClient, private auth: AuthService) {}

  ngOnInit() {
    this.cargar();
    this.cargarBodegas();
    this.cargarStockBajo();
    this.cargarReservasVencidas();
  }

  /**
   * Reservas que llevan demasiado tiempo reteniendo stock (F47, D-02).
   *
   * <p>Un 403 aquí no es un fallo que enseñar: significa que a este rol no le
   * toca ver el informe. Se traga en silencio y el banner no aparece, en vez de
   * pintar un error rojo por algo que funciona como debe.
   *
   * <p><b>Ojo con la causa, porque este comentario la tenía mal.</b> Decía que
   * el 403 venía de no tener `inventario:ver`, y no es eso: el Encargado de
   * Compras **sí** lo tiene. Lo que no tiene es SELECT sobre `pedido` —
   * deliberado desde la F34, quien compra no lee los pedidos de los clientes—
   * y este informe une las reservas con el pedido que las retiene. Desde la
   * F63 la regla está escrita en `SecurityConfig` en vez de depender de que
   * PostgreSQL diga que no.
   */
  cargarReservasVencidas() {
    // No se pide lo que se sabe que van a denegar. El Encargado de Compras
    // entra a esta pantalla con todo el derecho -necesita ver existencias para
    // saber que comprar- pero no le toca ver reservas de pedidos de clientes,
    // asi que pedirlas era un 403 garantizado en cada carga.
    //
    // Se quito porque una llamada que SIEMPRE falla es ruido, y el ruido acaba
    // tapando los fallos de verdad: este mismo se encontro barriendo el flujo
    // rol por rol, entre 84 comprobaciones.
    const rol = this.auth.getCurrentUser()?.rol;
    if (!InventarioComponent.ROLES_CON_RESERVAS.includes(rol)) {
      this.reservasVencidas = [];
      return;
    }

    this.http.get<Reserva[]>(`${this.apiUrl}/inventario/reservas/vencidas`).subscribe({
      next: res => { this.reservasVencidas = res; },
      // Se conserva la red de seguridad: si la regla del servidor cambia y esta
      // lista se queda atras, la pantalla sigue funcionando sin pintar un error.
      error: () => { this.reservasVencidas = []; }
    });
  }

  pedirMotivo(r: Reserva) {
    this.reservaALiberar = r;
    this.motivoLiberacion = '';
    this.reservaError = '';
  }

  liberarReserva() {
    if (!this.reservaALiberar) { return; }
    if (!this.motivoLiberacion.trim()) {
      this.reservaError = 'Escribe por qué se libera: sin motivo no se puede auditar después.';
      return;
    }
    this.liberando = true;
    this.http.post(`${this.apiUrl}/inventario/reservas/${this.reservaALiberar.idReserva}/liberar`,
                   { motivo: this.motivoLiberacion.trim() }).subscribe({
      next: () => {
        this.liberando = false;
        this.reservaALiberar = null;
        this.cargarReservasVencidas();
        this.mostrarToast('Reserva liberada. Las unidades vuelven al disponible.');
      },
      error: (err) => {
        this.liberando = false;
        this.reservaError = err.error?.message || 'No se pudo liberar la reserva.';
      }
    });
  }

  /** F54: buscador por texto. 300 ms de espera para no consultar por tecla. */
  busqueda = '';
  private buscarTimeout: any;

  onBuscar() {
    clearTimeout(this.buscarTimeout);
    this.buscarTimeout = setTimeout(() => { this.page = 0; this.cargar(); }, 300);
  }
  cargar() {
    this.loading = true;
    let params = new HttpParams().set('page', this.page).set('size', this.size);
    if (this.filtroBodega) params = params.set('idBodega', this.filtroBodega);
    if (this.busqueda) params = params.set('busqueda', this.busqueda);

    this.http.get<PageResponse<Inventario>>(`${this.apiUrl}/inventario`, { params }).subscribe({
      next: res => { this.cargaError = null; this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: (err: any) => { this.loading = false; this.cargaError = this.motivoDelFallo(err); this.mostrarToast('Error al cargar inventario', true); }
    });
  }

  cargarProductos() {
    this.http.get<any>(`${this.apiUrl}/productos?size=1000&estado=activo`).subscribe({
      next: res => { this.productos = res.content || []; },
      error: () => { /* sin catalogo el selector queda vacio y se ve */ }
    });
  }

  cargarBodegas() {
    this.http.get<Bodega[]>(`${this.apiUrl}/bodegas/activas`).subscribe({
      next: res => { this.bodegas = res; }
    });
  }

  /**
   * F94: pide el NÚMERO, no las filas.
   *
   * El aviso de arriba solo enseña «N referencias bajo mínimo», y para eso se
   * estaban descargando las 50.153 filas completas — nueve segundos y medio de
   * espera para pintar una cifra. `/stock-bajo/conteo` devuelve solo el total,
   * resuelto contra un índice parcial.
   */
  cargarStockBajo() {
    this.http.get<number>(`${this.apiUrl}/inventario/stock-bajo/conteo`).subscribe({
      next: n => { this.stockBajoTotal = n ?? 0; },
      error: () => { this.stockBajoTotal = 0; }
    });
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  elegirTipo(valor: string) {
    this.movForm.tipoMovimiento = valor;
    this.movError = '';
  }

  /** La cantidad significa algo distinto en cada movimiento; el rótulo lo dice. */
  etiquetaCantidad(): string {
    switch (this.movForm.tipoMovimiento) {
      case 'entrada':  return 'Unidades que entran';
      case 'salida':   return 'Unidades que salen';
      case 'ajuste':   return 'Stock real que has contado';
      case 'traslado': return 'Unidades que se mueven';
      default:         return 'Cantidad';
    }
  }

  pistaMotivo(): string {
    switch (this.movForm.tipoMovimiento) {
      case 'entrada':  return 'Ej.: sobrante de una devolución';
      case 'salida':   return 'Ej.: dos unidades rotas en bodega';
      case 'ajuste':   return 'Ej.: conteo físico del 28/08';
      case 'traslado': return 'Ej.: reposición de tienda';
      default:         return '';
    }
  }

  /** Cuánto cambia el stock de la bodega de origen. */
  delta(): number {
    const cant = Number(this.movForm.cantidad) || 0;
    const actual = this.stockConocido ?? 0;
    switch (this.movForm.tipoMovimiento) {
      case 'entrada':  return cant;
      case 'salida':
      case 'traslado': return -cant;
      case 'ajuste':   return cant - actual;
      default:         return 0;
    }
  }

  stockResultante(): number | null {
    if (!this.movForm.tipoMovimiento || !this.movForm.idProducto || !this.movForm.idBodega) {
      return null;
    }
    if (this.movForm.cantidad === null || this.movForm.cantidad === '') { return null; }
    return (this.stockConocido ?? 0) + this.delta();
  }

  puedeChocarConReservas(): boolean {
    if (this.movForm.tipoMovimiento === 'salida') { return true; }
    return this.movForm.tipoMovimiento === 'ajuste' && this.delta() < 0;
  }

  nombreBodega(id: number): string {
    return this.bodegas.find(b => b.idBodega === id)?.nombre || '';
  }

  /**
   * Busca el stock que hay ahora de ese producto en esa bodega.
   *
   * Se resuelve con el listado que ya existe —filtrando por bodega y buscando
   * por nombre— en vez de añadir un endpoint: la pantalla ya tiene todo lo que
   * necesita, y una consulta nueva habría que concedérsela a seis roles.
   */
  alCambiarProductoOBodega() {
    this.stockConocido = null;
    const idP = this.movForm.idProducto;
    const idB = this.movForm.idBodega;
    if (!idP || !idB) { return; }

    const prod = this.productos.find(p => p.idProducto === idP);
    if (!prod) { return; }

    const params: any = { page: 0, size: 50, idBodega: idB, busqueda: prod.nombre };
    this.http.get<PageResponse<Inventario>>(`${this.apiUrl}/inventario`, { params }).subscribe({
      next: res => {
        const fila = (res.content || []).find(i => i.productoId === idP && i.bodegaId === idB);
        this.stockConocido = fila ? fila.cantidad : null;
      },
      error: () => { this.stockConocido = null; }
    });
  }

  abrirModalMovimiento() {
    this.stockConocido = null;
    if (this.productos.length === 0) { this.cargarProductos(); }
    this.movForm = { tipoMovimiento: '', idProducto: null, idBodega: null, idBodegaDestino: null, cantidad: 1, observacion: '' };
    this.movError = '';
    this.showMovModal = true;
  }

  cerrarModalMovimiento() { this.showMovModal = false; }

  guardarMovimiento() {
    if (!this.movForm.tipoMovimiento) { this.movError = 'Seleccione un tipo de movimiento'; return; }
    if (!this.movForm.idProducto) { this.movError = 'Ingrese el ID del producto'; return; }
    if (!this.movForm.idBodega) { this.movError = 'Seleccione la bodega'; return; }
    if (!this.movForm.cantidad || this.movForm.cantidad < 1) { this.movError = 'La cantidad debe ser al menos 1'; return; }
    if (this.movForm.tipoMovimiento === 'traslado' && !this.movForm.idBodegaDestino) {
      this.movError = 'Seleccione la bodega destino para el traslado'; return;
    }

    this.saving = true;
    this.http.post(`${this.apiUrl}/inventario/movimiento`, this.movForm).subscribe({
      next: () => {
        this.saving = false;
        this.cerrarModalMovimiento();
        this.cargar();
        this.cargarStockBajo();
        this.mostrarToast('Movimiento registrado correctamente');
      },
      error: (err) => {
        this.saving = false;
        this.movError = err.error?.message || 'Error al registrar movimiento';
      }
    });
  }

  verHistorial(item: Inventario) {
    this.historialProducto = `${item.productoNombre} — ${item.bodegaNombre}`;
    this.http.get<Historial[]>(`${this.apiUrl}/inventario/${item.idInventario}/historial`).subscribe({
      next: res => { this.historialData = res; this.showHistorial = true; },
      error: () => { this.mostrarToast('Error al cargar historial', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
  /** Traduce el fallo a algo que se pueda leer y, si se puede, resolver. */
  private motivoDelFallo(err: any): string {
    if (err?.status === 0) return 'No hay conexión con el servidor.';
    if (err?.status === 403) return 'Tu rol no tiene permiso para ver esta información.';
    if (err?.status === 401) return 'Tu sesión ha caducado. Vuelve a entrar.';
    return err?.error?.message ?? 'El servidor no respondió correctamente.';
  }

}
