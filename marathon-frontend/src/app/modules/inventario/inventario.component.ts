import { Component, OnInit } from '@angular/core';
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
      <div class="alert-banner" *ngIf="stockBajo.length > 0">
        <strong class="inline-icon-text"><app-icon name="warning" [size]="16"/> Alerta de Stock Bajo:</strong> {{stockBajo.length}} referencia(s) en o por debajo de su stock mínimo
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
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Registrar Movimiento</h3>
          <form (ngSubmit)="guardarMovimiento()">
            <div class="form-group">
              <label>Tipo de Movimiento *</label>
              <select [(ngModel)]="movForm.tipoMovimiento" name="tipoMovimiento" required>
                <option value="">-- Seleccione --</option>
                <option value="entrada">Entrada</option>
                <option value="salida">Salida</option>
                <option value="ajuste">Ajuste</option>
                <option value="traslado">Traslado</option>
              </select>
            </div>
            <div class="form-group">
              <label>Producto (ID) *</label>
              <input type="number" [(ngModel)]="movForm.idProducto" name="idProducto" required min="1"/>
            </div>
            <div class="form-group">
              <label>Bodega Origen *</label>
              <app-searchable-select [(ngModel)]="movForm.idBodega" name="idBodega" [items]="bodegas"
                valueKey="idBodega" placeholder="Escriba la bodega de origen..."/>
            </div>
            <div class="form-group" *ngIf="movForm.tipoMovimiento === 'traslado'">
              <label>Bodega Destino *</label>
              <app-searchable-select [(ngModel)]="movForm.idBodegaDestino" name="idBodegaDestino" [items]="bodegas"
                valueKey="idBodega" placeholder="Escriba la bodega de destino..."/>
            </div>
            <div class="form-group">
              <label>Cantidad *</label>
              <input type="number" [(ngModel)]="movForm.cantidad" name="cantidad" required min="1"/>
            </div>
            <div class="form-group">
              <label>Observación</label>
              <input type="text" [(ngModel)]="movForm.observacion" name="observacion"/>
            </div>
            <small class="error" *ngIf="movError">{{movError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="cerrarModalMovimiento()">Cancelar</button>
              <button type="submit" class="btn-save" [disabled]="saving">{{saving ? 'Registrando...' : 'Registrar'}}</button>
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
    .tabla-scroll { overflow-x: auto; }
    .tabla-reservas th { white-space: nowrap; }
    .tabla-reservas td.nowrap { white-space: nowrap; }
  `]
})
export class InventarioComponent implements OnInit {
  data: Inventario[] = [];
  bodegas: Bodega[] = [];
  stockBajo: Inventario[] = [];
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

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.cargar();
    this.cargarBodegas();
    this.cargarStockBajo();
    this.cargarReservasVencidas();
  }

  /**
   * Reservas que llevan demasiado tiempo reteniendo stock (F47, D-02).
   *
   * <p>Un 403 aquí no es un fallo que enseñar: significa que este rol no tiene
   * 'inventario:ver' y sencillamente no le toca ver el informe. Se traga en
   * silencio y el banner no aparece, en vez de pintar un error rojo por algo
   * que está funcionando como debe.
   */
  cargarReservasVencidas() {
    this.http.get<Reserva[]>(`${this.apiUrl}/inventario/reservas/vencidas`).subscribe({
      next: res => { this.reservasVencidas = res; },
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

  cargarBodegas() {
    this.http.get<Bodega[]>(`${this.apiUrl}/bodegas/activas`).subscribe({
      next: res => { this.bodegas = res; }
    });
  }

  cargarStockBajo() {
    this.http.get<Inventario[]>(`${this.apiUrl}/inventario/stock-bajo`).subscribe({
      next: res => { this.stockBajo = res; }
    });
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  abrirModalMovimiento() {
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
