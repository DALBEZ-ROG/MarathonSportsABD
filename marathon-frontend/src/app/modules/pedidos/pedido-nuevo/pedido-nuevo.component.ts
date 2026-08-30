import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import * as XLSX from 'xlsx';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { CrudService } from '../../../core/services/crud.service';
import { AppIconComponent } from '../../../shared/components/icon/icon.component';
import { SearchableSelectComponent } from '../../../shared/components/searchable-select/searchable-select.component';

interface Cliente {
  idCliente: number;
  nombre: string;
  apellido: string;
  cedula: string;
}

interface Producto {
  idProducto: number;
  nombre: string;
  codigo: string;
  precioVenta: number;
}

interface DetalleLinea {
  idProducto: number;
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

interface ExcelImportPendiente {
  idCliente: number | null;
  observaciones: string;
  esPedidoEspecial: boolean;
  tipoEspecial: string;
  notaEspecial: string;
  fechaLimiteEntrega: string;
  detalles: DetalleLinea[];
  advertencias: string[];
  sinDatos: boolean;
}

@Component({
  selector: 'app-pedido-nuevo',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent, SearchableSelectComponent],
  template: `
    <div class="container">
      <h2>Nuevo Pedido</h2>

      <div class="form-section">
        <div class="sec-cab">
          <h3>Datos del pedido</h3>
          <!-- El pedido especial era una SECCIÓN entera para una casilla. Ahora
               es un interruptor en la misma banda, y solo ocupa sitio cuando de
               verdad hay algo que rellenar. -->
          <button type="button" class="esp-toggle" [class.on]="esPedidoEspecial"
                  [attr.aria-pressed]="esPedidoEspecial"
                  (click)="alternarEspecial()">
            {{ esPedidoEspecial ? '✓ Pedido especial' : 'Marcar como especial' }}
          </button>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Cliente *</label>
            <!-- F93: [remoto] — filtra la base, no el navegador. Ver cargarClientes(). -->
            <app-searchable-select [(ngModel)]="idCliente" name="idCliente" [items]="clientes"
              labelKey="nombre,apellido" valueKey="idCliente" placeholder="Escriba el nombre del cliente..."
              [remoto]="true" [buscando]="buscandoClientes" (buscar)="cargarClientes($event)"/>
          </div>
          <div class="form-group">
            <label>Observaciones</label>
            <input type="text" [(ngModel)]="observaciones" name="observaciones" placeholder="Opcional..." class="full-width"/>
          </div>
        </div>

        <div class="especial" *ngIf="esPedidoEspecial">
          <p class="esp-expli">
            Un pedido especial <strong>se crea aunque no haya stock</strong>: existe
            precisamente para prepararse o fabricarse. El déficit queda anotado en la
            bitácora, no callado.
          </p>
          <div class="form-row">
            <div class="form-group">
              <label>Tipo *</label>
              <div class="esp-tipos">
                <button type="button" *ngFor="let t of tiposEspeciales"
                        class="esp-tipo" [class.on]="tipoEspecial === t.valor"
                        (click)="tipoEspecial = t.valor">{{ t.titulo }}</button>
              </div>
            </div>
            <div class="form-group">
              <label>Fecha límite de entrega</label>
              <input type="datetime-local" [(ngModel)]="fechaLimiteEntrega"
                     name="fechaLimiteEntrega" class="full-width"/>
            </div>
          </div>
          <div class="form-group">
            <label>Nota</label>
            <input type="text" [(ngModel)]="notaEspecial" name="notaEspecial"
                   placeholder="Qué lo hace especial: talla a medida, envoltorio, plazo…"
                   class="full-width"/>
          </div>
        </div>
      </div>

      <div class="form-section">
        <h3>Agregar Productos</h3>
        <div class="product-search">
          <div class="form-group flex-2">
            <label>Producto</label>
            <app-searchable-select [(ngModel)]="selectedProducto" name="producto" [items]="productos"
              labelKey="nombre" placeholder="Escriba el nombre del producto..."
              [remoto]="true" [buscando]="buscandoProductos" (buscar)="cargarProductos($event)"
              (ngModelChange)="onProductoElegido()"/>
          </div>
          <div class="form-group flex-1">
            <label>Cantidad</label>
            <input type="number" [(ngModel)]="cantidadAgregar" name="cantidad" min="1"/>
          </div>
          <!-- F55: el precio se carga solo al elegir el producto, y NO se
               escribe. Era un campo editable cuyo valor el backend descarta
               desde la L3 (D-34): el precio de la linea lo pone el catalogo,
               precisamente para que un POST con "precioUnitario": 0.01 no cree
               un pedido de 0,01. Dejarlo escribible invitaba a teclear un
               importe que no se iba a usar. -->
          <div class="form-group flex-1">
            <label>Precio Unit.</label>
            <input type="number" [ngModel]="precioAgregar" name="precio" readonly
                   title="Es el precio del catálogo. Para cambiarlo, edita el producto."/>
          </div>
          <div class="form-group btn-group">
            <label>&nbsp;</label>
            <button type="button" class="btn-add" (click)="agregarLinea()">+ Agregar</button>
          </div>
        </div>

        <div class="excel-actions">
          <label class="btn-excel btn-excel-upload">
            <app-icon name="refresh" [size]="16"/> Importar Excel
            <input type="file" accept=".xlsx,.xls" (change)="importarExcel($event)" hidden/>
          </label>
        </div>
        <small class="excel-hint">El archivo debe usar nombres de cliente y producto existentes. Revise los datos antes de confirmar.</small>
      </div>

      <div class="form-section">
        <h3>Detalle del Pedido</h3>
        <table class="detail-table">
          <thead>
            <tr><th>Producto</th><th>Cantidad</th><th>P. Unitario</th><th>Subtotal</th><th></th></tr>
          </thead>
          <tbody>
            <tr *ngIf="detalles.length === 0" class="empty-row">
              <td colspan="5">Sin productos agregados. Use el formulario de arriba o importe un Excel.</td>
            </tr>
            <tr *ngFor="let d of detalles; let i = index">
              <td>{{d.productoNombre}}</td>
              <td>
                <div class="quantity-control">
                  <button type="button" (click)="reducirCantidad(i)" [disabled]="d.cantidad <= 1" title="Reducir cantidad">−</button>
                  <span>{{d.cantidad}}</span>
                  <button type="button" (click)="aumentarCantidad(i)" title="Aumentar cantidad">+</button>
                </div>
              </td>
              <td>\${{d.precioUnitario | number:'1.2-2'}}</td>
              <td class="total">\${{d.subtotal | number:'1.2-2'}}</td>
              <td><button class="btn-remove" (click)="quitarLinea(i)" title="Quitar línea"><app-icon name="x" [size]="14"/></button></td>
            </tr>
          </tbody>
          <tfoot>
            <tr><td colspan="3" class="total-label">TOTAL ESTIMADO:</td><td class="total-value">\${{totalPreview | number:'1.2-2'}}</td><td></td></tr>
          </tfoot>
        </table>
      </div>

      <small class="error" *ngIf="formError">{{formError}}</small>
      <small class="excel-hint import-ok" *ngIf="importOk">{{importOk}}</small>

      <div class="form-actions">
        <button type="button" class="btn-cancel" (click)="cancelar()">Cancelar</button>
        <button type="button" class="btn-save" (click)="crearPedido()" [disabled]="saving">
          {{saving ? 'Creando pedido...' : 'Crear Pedido'}}
        </button>
      </div>
    </div>

    <div class="import-overlay" *ngIf="showImportModal" (click)="cancelarImportacion()">
      <div class="import-panel" (click)="$event.stopPropagation()">
        <ng-container *ngIf="importPendiente?.sinDatos; else previewConProductos">
          <p class="import-message">Excel sin datos</p>
          <div class="import-actions">
            <button type="button" class="btn-save" (click)="confirmarImportacion()">Confirmar</button>
          </div>
        </ng-container>

        <ng-template #previewConProductos>
          <p class="import-lead">Productos importados</p>
          <ul class="import-list">
            <li *ngFor="let d of importPendiente?.detalles">
              <span class="name">{{ d.productoNombre }}</span>
              <span class="qty">{{ d.cantidad }} × \${{ d.precioUnitario | number:'1.2-2' }}</span>
              <span class="sub">\${{ d.subtotal | number:'1.2-2' }}</span>
            </li>
          </ul>
          <p class="import-total">Total: <strong>\${{ totalImportPreview | number:'1.2-2' }}</strong></p>
          <p class="import-note" *ngIf="importPendiente?.advertencias?.length">
            {{ importPendiente!.advertencias.slice(0, 2).join(' · ') }}
          </p>
          <div class="import-actions">
            <button type="button" class="btn-cancel" (click)="cancelarImportacion()">Cancelar</button>
            <button type="button" class="btn-save" (click)="confirmarImportacion()">Confirmar</button>
          </div>
        </ng-template>
      </div>
    </div>
  `,
  styles: [`
    /* ── Cabecera de sección con el interruptor de especial (F73) ─ */
    .sec-cab { display: flex; justify-content: space-between; align-items: center;
               gap: 1rem; flex-wrap: wrap; margin-bottom: .9rem; }
    .sec-cab h3 { margin: 0; }
    .esp-toggle { background: transparent; border: 1px solid rgba(255,255,255,0.14);
                  color: rgba(255,255,255,0.55); padding: .38rem .8rem;
                  border-radius: 99px; font-size: .78rem; cursor: pointer;
                  transition: all .15s ease; white-space: nowrap; }
    .esp-toggle:hover { border-color: rgba(201,168,76,0.6); color: #C9A84C; }
    .esp-toggle.on { background: rgba(201,168,76,0.14); border-color: #C9A84C; color: #F4E28D; }

    .especial { border-top: 1px solid rgba(255,255,255,0.07); margin-top: 1rem; padding-top: 1rem; }
    .esp-expli { font-size: .82rem; color: rgba(255,255,255,0.5); line-height: 1.6;
                 margin: 0 0 1rem; }
    .esp-expli strong { color: rgba(255,255,255,0.85); }
    .esp-tipos { display: flex; gap: .4rem; flex-wrap: wrap; }
    .esp-tipo { flex: 1; min-width: 96px; background: rgba(255,255,255,0.03);
                border: 1px solid rgba(255,255,255,0.1); color: rgba(255,255,255,0.6);
                padding: .58rem .5rem; border-radius: 8px; font-size: .84rem;
                cursor: pointer; transition: all .15s ease; }
    .esp-tipo:hover { border-color: rgba(255,255,255,0.22); color: rgba(255,255,255,0.85); }
    .esp-tipo.on { background: rgba(201,168,76,0.12); border-color: #C9A84C;
                   color: #F4E28D; font-weight: 600; }

    .excel-actions {
      display: flex;
      flex-wrap: wrap;
      gap: .75rem;
      margin-top: 1rem;
    }

    .btn-excel {
      display: inline-flex;
      align-items: center;
      gap: .45rem;
      padding: .55rem 1rem;
      border-radius: var(--ms-radius-sm);
      border: 1px solid rgba(201, 168, 76, 0.35);
      background: rgba(201, 168, 76, 0.08);
      color: var(--ms-gold);
      font-size: .82rem;
      cursor: pointer;
      transition: background .2s, border-color .2s;
    }
    .btn-excel:hover {
      background: rgba(201, 168, 76, 0.14);
      border-color: rgba(201, 168, 76, 0.55);
    }
    .btn-excel-upload { margin: 0; }

    .excel-hint {
      display: block;
      margin-top: .65rem;
      color: var(--ms-text-muted);
      font-size: .78rem;
    }
    .import-ok { color: #81C784; font-style: normal; }

    .empty-row td {
      text-align: center;
      color: var(--ms-text-muted);
      font-style: italic;
      padding: 1.25rem .75rem;
    }

    .quantity-control {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: .65rem;
    }
    .quantity-control button {
      width: 28px;
      height: 28px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0;
      border: 1px solid rgba(201, 168, 76, .35);
      border-radius: 7px;
      background: rgba(201, 168, 76, .08);
      color: var(--ms-gold);
      font-size: 1rem;
      line-height: 1;
      cursor: pointer;
    }
    .quantity-control button:hover:not(:disabled) { background: rgba(201, 168, 76, .18); }
    .quantity-control button:disabled { opacity: .3; cursor: not-allowed; }
    .quantity-control span { min-width: 2ch; text-align: center; }

    .import-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.78);
      backdrop-filter: blur(8px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      animation: fadeIn .2s ease;
    }

    .import-panel {
      width: min(560px, 92vw);
      padding: .5rem 1rem;
      text-align: center;
    }

    .import-message {
      font-size: 1.45rem;
      font-weight: 400;
      color: rgba(255, 255, 255, 0.88);
      letter-spacing: 0.4px;
      margin: 0 0 1.75rem;
    }

    .import-lead {
      font-size: .78rem;
      text-transform: uppercase;
      letter-spacing: 1.4px;
      color: rgba(255, 255, 255, 0.35);
      margin: 0 0 1rem;
    }

    .import-list {
      list-style: none;
      margin: 0 0 1rem;
      padding: 0;
      text-align: left;
    }

    .import-list li {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto auto;
      gap: .85rem;
      align-items: baseline;
      padding: .5rem 0;
      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
      font-size: .86rem;
    }

    .import-list .name {
      color: rgba(255, 255, 255, 0.9);
      line-height: 1.35;
    }

    .import-list .qty {
      color: rgba(255, 255, 255, 0.45);
      white-space: nowrap;
    }

    .import-list .sub {
      color: var(--ms-gold);
      white-space: nowrap;
      text-align: right;
    }

    .import-total {
      margin: 0 0 .75rem;
      text-align: right;
      color: rgba(255, 255, 255, 0.75);
      font-size: .92rem;
    }

    .import-total strong { color: #fff; font-weight: 600; }

    .import-note {
      margin: 0 0 1.25rem;
      text-align: left;
      color: #FFB74D;
      font-size: .76rem;
      line-height: 1.4;
    }

    .import-actions {
      display: flex;
      justify-content: center;
      gap: .75rem;
      margin-top: .25rem;
    }
  `]
})
export class PedidoNuevoComponent implements OnInit {
  clientes: Cliente[] = [];
  productos: Producto[] = [];
  detalles: DetalleLinea[] = [];
  idCliente: number | null = null;
  observaciones = '';
  selectedProducto: Producto | null = null;
  cantidadAgregar = 1;
  precioAgregar = 0;
  formError = '';
  importOk = '';
  saving = false;
  showImportModal = false;
  importPendiente: ExcelImportPendiente | null = null;

  esPedidoEspecial = false;

  readonly tiposEspeciales = [
    { valor: 'personalizado', titulo: 'Personalizado' },
    { valor: 'regalo',        titulo: 'Regalo' },
    { valor: 'corporativo',   titulo: 'Corporativo' }
  ];
  tipoEspecial = '';
  notaEspecial = '';
  fechaLimiteEntrega = '';

  constructor(private http: HttpClient, private router: Router, private crud: CrudService) {}

  buscandoClientes = false;
  buscandoProductos = false;

  ngOnInit() {
    // F93: ya NO se precargan las dos listas al abrir la pantalla.
    //
    // Se hacía, y era lo que colgaba el equipo. `/clientes/activos` devolvía la
    // lista COMPLETA para que filtrara el navegador: con las 4.620 filas de
    // cuando se escribió era razonable, pero con el millón y medio de la F91
    // son 299 MB de JSON que hay que descargar, parsear y convertir en objetos
    // antes de que la pantalla responda a nada.
    //
    // Los dos buscadores piden ahora lo que se escribe y reciben 20 filas. El
    // primer `(buscar)` lo lanza el propio componente al abrir el desplegable,
    // así que abrir la pantalla no pide NADA.
    //
    // Efecto secundario que no es menor: el buscador de producto se traía las
    // primeras 500 filas de un catálogo de 1.500.000. El 99,97 % de los
    // productos era imposible de elegir desde aquí, y no había forma de darse
    // cuenta — la lista simplemente no los contenía.
  }

  /** Las 20 primeras coincidencias en la base. El componente aplica el respiro. */
  cargarClientes(q: string = '') {
    this.buscandoClientes = true;
    const params = new HttpParams().set('q', q).set('limite', 20);
    this.http.get<Cliente[]>(`${environment.apiUrl}/clientes/buscar`, { params }).subscribe({
      next: res => { this.clientes = res; this.buscandoClientes = false; },
      error: () => { this.clientes = []; this.buscandoClientes = false; }
    });
  }

  cargarProductos(q: string = '') {
    this.buscandoProductos = true;
    const filtros: Record<string, string | number> = { page: 0, size: 20, estado: 'activo' };
    if (q) { filtros['nombre'] = q; }
    this.crud.listar<Producto>('productos', filtros).subscribe({
      next: res => { this.productos = res.content; this.buscandoProductos = false; },
      error: () => { this.productos = []; this.buscandoProductos = false; }
    });
  }

  get totalPreview(): number {
    return this.detalles.reduce((sum, d) => sum + d.subtotal, 0);
  }

  get totalImportPreview(): number {
    return this.importPendiente?.detalles.reduce((sum, d) => sum + d.subtotal, 0) ?? 0;
  }

  /**
   * F55: al elegir un producto se trae su precio de catalogo.
   *
   * Antes la casilla quedaba vacia y habia que teclearlo — y lo tecleado se
   * descartaba, porque desde la L3 (D-34) el precio de la linea lo pone el
   * catalogo. Ahora se ve el precio que de verdad se va a aplicar.
   */
  onProductoElegido() {
    this.precioAgregar = this.selectedProducto ? this.selectedProducto.precioVenta : 0;
  }

  agregarLinea() {
    if (!this.selectedProducto) { this.formError = 'Seleccione un producto'; return; }
    if (this.cantidadAgregar < 1) { this.formError = 'La cantidad debe ser al menos 1'; return; }
    const precio = this.precioAgregar > 0 ? this.precioAgregar : this.selectedProducto.precioVenta;
    if (precio <= 0) { this.formError = 'El precio debe ser mayor a 0'; return; }

    this.formError = '';
    this.importOk = '';
    const existente = this.detalles.find(d => d.idProducto === this.selectedProducto!.idProducto);
    if (existente) {
      existente.cantidad += this.cantidadAgregar;
      existente.subtotal = existente.cantidad * existente.precioUnitario;
    } else {
      this.detalles.push({
        idProducto: this.selectedProducto.idProducto,
        productoNombre: this.selectedProducto.nombre,
        cantidad: this.cantidadAgregar,
        precioUnitario: precio,
        subtotal: this.cantidadAgregar * precio
      });
    }

    this.selectedProducto = null;
    this.cantidadAgregar = 1;
    this.precioAgregar = 0;
  }

  quitarLinea(index: number) {
    this.detalles.splice(index, 1);
  }

  aumentarCantidad(index: number): void {
    const detalle = this.detalles[index];
    detalle.cantidad++;
    detalle.subtotal = detalle.cantidad * detalle.precioUnitario;
  }

  reducirCantidad(index: number): void {
    const detalle = this.detalles[index];
    if (detalle.cantidad <= 1) return;
    detalle.cantidad--;
    detalle.subtotal = detalle.cantidad * detalle.precioUnitario;
  }

  /** El interruptor sustituye a la casilla; reusa la limpieza que ya había. */
  alternarEspecial() {
    this.esPedidoEspecial = !this.esPedidoEspecial;
    this.onToggleEspecial();
  }

  onToggleEspecial() {
    if (!this.esPedidoEspecial) {
      this.tipoEspecial = '';
      this.notaEspecial = '';
      this.fechaLimiteEntrega = '';
    }
  }

  crearPedido() {
    if (!this.idCliente) { this.formError = 'Seleccione un cliente'; return; }
    if (this.detalles.length === 0) { this.formError = 'Agregue al menos un producto'; return; }
    if (this.esPedidoEspecial && !this.tipoEspecial) { this.formError = 'Seleccione el tipo de pedido especial'; return; }
    this.formError = '';
    this.saving = true;

    const body = {
      idCliente: this.idCliente,
      observaciones: this.observaciones || null,
      esPedidoEspecial: this.esPedidoEspecial,
      tipoEspecial: this.esPedidoEspecial ? this.tipoEspecial : null,
      notaEspecial: this.esPedidoEspecial ? (this.notaEspecial || null) : null,
      fechaLimiteEntrega: this.esPedidoEspecial && this.fechaLimiteEntrega ? this.fechaLimiteEntrega : null,
      detalles: this.detalles.map(d => ({
        idProducto: d.idProducto,
        cantidad: d.cantidad,
        precioUnitario: d.precioUnitario
      }))
    };

    this.http.post(`${environment.apiUrl}/pedidos`, body).subscribe({
      next: () => { this.router.navigate(['/pedidos']); },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al crear el pedido'; }
    });
  }

  cancelar() {
    this.router.navigate(['/pedidos']);
  }

  importarExcel(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
      try {
        const buffer = e.target?.result;
        if (!(buffer instanceof ArrayBuffer)) {
          this.formError = 'No se pudo leer el archivo Excel';
          return;
        }
        const wb = XLSX.read(new Uint8Array(buffer), { type: 'array' });
        const sheet = wb.Sheets[wb.SheetNames[0]];
        const rows = XLSX.utils.sheet_to_json<(string | number | null)[]>(sheet, { header: 1, defval: '' });
        // F93: antes de resolver nombres hay que traerse los candidatos.
        // Hasta ahora se buscaban contra las listas que la pantalla precargaba;
        // ya no se precargan, y ademas el producto solo tenia las primeras 500
        // filas de 1.500.000 — cualquier producto fuera de esas 500 se
        // importaba como «no encontrado» sin que nada lo explicara.
        this.resolverCatalogoDelExcel(rows, () => this.prepararImportacionExcel(rows));
      } catch {
        this.formError = 'El archivo Excel no tiene un formato válido';
      }
    };
    reader.readAsArrayBuffer(file);
  }

  confirmarImportacion(): void {
    if (!this.importPendiente) return;

    if (this.importPendiente.idCliente) {
      this.idCliente = this.importPendiente.idCliente;
    }
    this.observaciones = this.importPendiente.observaciones;
    this.esPedidoEspecial = this.importPendiente.esPedidoEspecial;
    this.tipoEspecial = this.importPendiente.tipoEspecial;
    this.notaEspecial = this.importPendiente.notaEspecial;
    this.fechaLimiteEntrega = this.importPendiente.fechaLimiteEntrega;

    if (!this.importPendiente.sinDatos) {
      this.detalles = [...this.importPendiente.detalles];
    }

    this.formError = this.importPendiente.advertencias.length
      ? this.importPendiente.advertencias.slice(0, 3).join('; ')
      : '';
    this.importOk = this.importPendiente.sinDatos
      ? ''
      : (this.importPendiente.advertencias.length
        ? ''
        : 'Pedido cargado desde Excel. Revise y modifique si es necesario.');
    this.cancelarImportacion();
  }

  cancelarImportacion(): void {
    this.showImportModal = false;
    this.importPendiente = null;
  }

  private prepararImportacionExcel(rows: (string | number | null)[][]): void {
    const campos = new Map<string, string>();
    let filaProductos = -1;

    for (let i = 0; i < rows.length; i++) {
      const fila = rows[i] ?? [];
      const col0 = this.normalizarCampo(this.celdaTexto(fila[0]));
      const col1 = this.celdaTexto(fila[1]);

      if (this.esEncabezadoProductos(col0)) {
        filaProductos = i + 1;
        break;
      }
      if (col0 && col0 !== 'campo' && col0 !== 'valor') {
        campos.set(this.resolverCampo(col0), col1);
      }
    }

    const advertencias: string[] = [];
    let idCliente: number | null = null;

    const nombreCliente = campos.get('cliente') ?? campos.get('nombre_cliente') ?? '';
    if (nombreCliente) {
      const cliente = this.buscarClientePorNombre(nombreCliente);
      if (cliente) {
        idCliente = cliente.idCliente;
      } else {
        advertencias.push(`Cliente "${nombreCliente}" no encontrado`);
      }
    }

    const observaciones = campos.get('observaciones') ?? '';
    const esEspecial = (campos.get('es_pedido_especial') ?? 'NO').toUpperCase();
    const esPedidoEspecial = esEspecial === 'SI' || esEspecial === 'S' || esEspecial === 'TRUE';
    const tipoEspecial = campos.get('tipo_especial') ?? '';
    const notaEspecial = campos.get('nota_especial') ?? '';
    const fechaLimiteEntrega = campos.get('fecha_limite_entrega') ?? '';

    const nuevosDetalles: DetalleLinea[] = [];

    if (filaProductos >= 0) {
      for (let i = filaProductos; i < rows.length; i++) {
        const fila = rows[i] ?? [];
        const nombreProducto = this.celdaTexto(fila[0]);
        const cantidad = Number(this.celdaTexto(fila[1]));
        const precioExcel = this.celdaTexto(fila[2]);

        if (!nombreProducto && !cantidad && !precioExcel) continue;
        if (!nombreProducto) {
          advertencias.push(`Fila ${i + 1}: falta nombre de producto`);
          continue;
        }
        if (!cantidad || cantidad < 1) {
          advertencias.push(`Fila ${i + 1}: cantidad inválida`);
          continue;
        }

        const producto = this.buscarProductoPorNombre(nombreProducto);
        if (!producto) {
          advertencias.push(`Fila ${i + 1}: producto "${nombreProducto}" no encontrado`);
          continue;
        }

        const precio = precioExcel ? Number(precioExcel) : producto.precioVenta;
        if (!precio || precio <= 0) {
          advertencias.push(`Fila ${i + 1}: precio inválido`);
          continue;
        }

        const existente = nuevosDetalles.find(d => d.idProducto === producto.idProducto);
        if (existente) {
          existente.cantidad += cantidad;
          existente.subtotal = existente.cantidad * existente.precioUnitario;
        } else {
          nuevosDetalles.push({
            idProducto: producto.idProducto,
            productoNombre: producto.nombre,
            cantidad,
            precioUnitario: precio,
            subtotal: cantidad * precio
          });
        }
      }
    } else {
      advertencias.push('No se encontró la sección de productos en el Excel');
    }

    this.formError = '';
    this.importOk = '';
    this.importPendiente = {
      idCliente,
      observaciones,
      esPedidoEspecial,
      tipoEspecial,
      notaEspecial,
      fechaLimiteEntrega,
      detalles: nuevosDetalles,
      advertencias,
      sinDatos: nuevosDetalles.length === 0
    };
    this.showImportModal = true;
  }

  private resolverCampo(col0: string): string {
    if (col0.startsWith('cliente') || col0.startsWith('nombre_cliente') || col0 === 'id_cliente') return 'cliente';
    if (col0.startsWith('observac')) return 'observaciones';
    if (col0.startsWith('es_pedido')) return 'es_pedido_especial';
    if (col0.startsWith('tipo_especial')) return 'tipo_especial';
    if (col0.startsWith('nota_especial') || col0.startsWith('nota_especia')) return 'nota_especial';
    if (col0.startsWith('fecha_limite')) return 'fecha_limite_entrega';
    return col0;
  }

  private esEncabezadoProductos(col0: string): boolean {
    return ['producto', 'nombre_producto', 'id_producto'].includes(col0);
  }

  private normalizarCampo(valor: string): string {
    return valor
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
  }

  private normalizarTexto(valor: string): string {
    return this.normalizarCampo(valor).replace(/\s+/g, ' ');
  }

  /**
   * Candidatos traídos de la base para resolver los nombres de un Excel (F93).
   *
   * <p>Son listas pequeñas y de usar y tirar: una búsqueda por cada nombre
   * distinto que aparece en el archivo. Antes esto se resolvía contra las
   * listas que la pantalla precargaba enteras, que es justo lo que había que
   * dejar de hacer.
   */
  private candidatosCliente: Cliente[] = [];
  private candidatosProducto: Producto[] = [];

  /** Pide a la base los candidatos de cada nombre del Excel y luego sigue. */
  private resolverCatalogoDelExcel(rows: (string | number | null)[][], seguir: () => void): void {
    const { nombreCliente, nombresProducto } = this.nombresDelExcel(rows);

    const peticiones: Observable<unknown>[] = [];
    this.candidatosCliente = [];
    this.candidatosProducto = [];

    if (nombreCliente) {
      peticiones.push(this.http
        .get<Cliente[]>(`${environment.apiUrl}/clientes/buscar`,
                        { params: new HttpParams().set('q', nombreCliente).set('limite', 25) })
        .pipe(tap(res => this.candidatosCliente.push(...res)),
              catchError(() => of([]))));
    }

    // Un tope, por si alguien suelta un Excel de mil líneas: son mil consultas
    // y el navegador solo abre seis a la vez. Se avisa en lugar de colgarse,
    // que es de lo que iba todo esto.
    const nombres = nombresProducto.slice(0, 100);
    for (const nombre of nombres) {
      peticiones.push(this.crud
        .listar<Producto>('productos', { page: 0, size: 10, estado: 'activo', nombre })
        .pipe(tap(res => this.candidatosProducto.push(...res.content)),
              catchError(() => of({ content: [] as Producto[] } as any))));
    }

    if (peticiones.length === 0) { seguir(); return; }

    this.formError = '';
    forkJoin(peticiones).subscribe({
      next: () => seguir(),
      error: () => { this.formError = 'No se pudo consultar el catálogo para importar el Excel.'; }
    });
  }

  /** Los nombres distintos que hay que resolver: el cliente y cada producto. */
  private nombresDelExcel(rows: (string | number | null)[][]): { nombreCliente: string; nombresProducto: string[] } {
    let nombreCliente = '';
    const productos = new Set<string>();
    let filaProductos = -1;

    for (let i = 0; i < rows.length; i++) {
      const fila = rows[i] ?? [];
      const col0 = this.normalizarCampo(this.celdaTexto(fila[0]));
      if (this.esEncabezadoProductos(col0)) { filaProductos = i + 1; break; }
      if (this.resolverCampo(col0) === 'cliente' || this.resolverCampo(col0) === 'nombre_cliente') {
        nombreCliente = this.celdaTexto(fila[1]);
      }
    }

    if (filaProductos >= 0) {
      for (let i = filaProductos; i < rows.length; i++) {
        const nombre = this.celdaTexto((rows[i] ?? [])[0]);
        if (nombre) { productos.add(nombre); }
      }
    }
    return { nombreCliente, nombresProducto: [...productos] };
  }

  private buscarClientePorNombre(texto: string): Cliente | undefined {
    const q = this.normalizarTexto(texto);
    if (!q) return undefined;

    const exacto = this.candidatosCliente.find(c =>
      this.normalizarTexto(`${c.nombre} ${c.apellido}`) === q ||
      this.normalizarTexto(`${c.apellido} ${c.nombre}`) === q
    );
    if (exacto) return exacto;

    return this.candidatosCliente.find(c => {
      const nombreCompleto = this.normalizarTexto(`${c.nombre} ${c.apellido}`);
      return nombreCompleto.includes(q) || q.includes(nombreCompleto);
    });
  }

  private buscarProductoPorNombre(texto: string): Producto | undefined {
    const q = this.normalizarTexto(texto);
    if (!q) return undefined;

    const exacto = this.candidatosProducto.find(p => this.normalizarTexto(p.nombre) === q);
    if (exacto) return exacto;

    const parcial = this.candidatosProducto.filter(p => {
      const nombre = this.normalizarTexto(p.nombre);
      return nombre.includes(q) || q.includes(nombre);
    });
    if (parcial.length === 1) return parcial[0];

    return this.candidatosProducto.find(p => this.normalizarTexto(p.nombre).startsWith(q));
  }

  private celdaTexto(valor: string | number | null | undefined): string {
    if (valor === null || valor === undefined) return '';
    return String(valor).trim();
  }
}
