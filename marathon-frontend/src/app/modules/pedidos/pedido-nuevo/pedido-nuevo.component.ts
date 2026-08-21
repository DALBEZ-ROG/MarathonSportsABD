import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import * as XLSX from 'xlsx';
import { environment } from '../../../../environments/environment';
import { CrudService } from '../../../core/services/crud.service';
import { AppIconComponent } from '../../../shared/components/icon/icon.component';

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
  imports: [CommonModule, FormsModule, AppIconComponent],
  template: `
    <div class="container">
      <h2>Nuevo Pedido</h2>

      <div class="form-section">
        <h3>Datos del Pedido</h3>
        <div class="form-row">
          <div class="form-group">
            <label>Cliente *</label>
            <select [(ngModel)]="idCliente" name="idCliente" class="full-width">
              <option [ngValue]="null">-- Seleccione un cliente --</option>
              <option *ngFor="let c of clientes" [ngValue]="c.idCliente">{{c.nombre}} {{c.apellido}}</option>
            </select>
          </div>
          <div class="form-group">
            <label>Observaciones</label>
            <input type="text" [(ngModel)]="observaciones" name="observaciones" placeholder="Opcional..." class="full-width"/>
          </div>
        </div>
      </div>

      <div class="form-section">
        <h3>Pedido Especial</h3>
        <label class="toggle-row">
          <input type="checkbox" [(ngModel)]="esPedidoEspecial" name="esPedidoEspecial" (change)="onToggleEspecial()"/>
          <span>¿Es un pedido especial?</span>
        </label>

        <div class="especial-fields" *ngIf="esPedidoEspecial">
          <div class="form-row">
            <div class="form-group">
              <label>Tipo de pedido especial *</label>
              <select [(ngModel)]="tipoEspecial" name="tipoEspecial" class="full-width">
                <option value="">-- Seleccione --</option>
                <option value="personalizado">Personalizado</option>
                <option value="regalo">Regalo</option>
                <option value="corporativo">Corporativo</option>
              </select>
            </div>
            <div class="form-group">
              <label>Fecha límite de entrega</label>
              <input type="datetime-local" [(ngModel)]="fechaLimiteEntrega" name="fechaLimiteEntrega" class="full-width"/>
            </div>
          </div>
          <div class="form-group">
            <label>Nota especial</label>
            <textarea [(ngModel)]="notaEspecial" name="notaEspecial" rows="3" placeholder="Detalles del pedido especial..." class="full-width"></textarea>
          </div>
        </div>
      </div>

      <div class="form-section">
        <h3>Agregar Productos</h3>
        <div class="product-search">
          <div class="form-group flex-2">
            <label>Producto</label>
            <select [(ngModel)]="selectedProducto" name="producto">
              <option [ngValue]="null">-- Seleccione --</option>
              <option *ngFor="let p of productos" [ngValue]="p">{{p.nombre}} (\${{p.precioVenta}})</option>
            </select>
          </div>
          <div class="form-group flex-1">
            <label>Cantidad</label>
            <input type="number" [(ngModel)]="cantidadAgregar" name="cantidad" min="1"/>
          </div>
          <div class="form-group flex-1">
            <label>Precio Unit.</label>
            <input type="number" [(ngModel)]="precioAgregar" name="precio" step="0.01" min="0.01"/>
          </div>
          <div class="form-group btn-group">
            <label>&nbsp;</label>
            <button type="button" class="btn-add" (click)="agregarLinea()">+ Agregar</button>
          </div>
        </div>

        <div class="excel-actions">
          <button type="button" class="btn-excel" (click)="descargarPlantilla()">
            <app-icon name="clipboard" [size]="16"/> Descargar plantilla Excel
          </button>
          <label class="btn-excel btn-excel-upload">
            <app-icon name="refresh" [size]="16"/> Importar Excel
            <input type="file" accept=".xlsx,.xls" (change)="importarExcel($event)" hidden/>
          </label>
        </div>
        <small class="excel-hint">Use nombres de cliente y producto en la plantilla. Revise en pantalla antes de confirmar.</small>
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
              <td>{{d.cantidad}}</td>
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
  tipoEspecial = '';
  notaEspecial = '';
  fechaLimiteEntrega = '';

  constructor(private http: HttpClient, private router: Router, private crud: CrudService) {}

  ngOnInit() {
    this.cargarClientes();
    this.cargarProductos();
  }

  cargarClientes() {
    this.http.get<Cliente[]>(`${environment.apiUrl}/clientes/activos`).subscribe({
      next: res => { this.clientes = res; }
    });
  }

  cargarProductos() {
    this.crud.listar<Producto>('productos', { page: 0, size: 500, estado: 'activo' }).subscribe({
      next: res => { this.productos = res.content; }
    });
  }

  get totalPreview(): number {
    return this.detalles.reduce((sum, d) => sum + d.subtotal, 0);
  }

  get totalImportPreview(): number {
    return this.importPendiente?.detalles.reduce((sum, d) => sum + d.subtotal, 0) ?? 0;
  }

  agregarLinea() {
    if (!this.selectedProducto) { this.formError = 'Seleccione un producto'; return; }
    if (this.cantidadAgregar < 1) { this.formError = 'La cantidad debe ser al menos 1'; return; }
    const precio = this.precioAgregar > 0 ? this.precioAgregar : this.selectedProducto.precioVenta;
    if (precio <= 0) { this.formError = 'El precio debe ser mayor a 0'; return; }

    this.formError = '';
    this.importOk = '';
    this.detalles.push({
      idProducto: this.selectedProducto.idProducto,
      productoNombre: this.selectedProducto.nombre,
      cantidad: this.cantidadAgregar,
      precioUnitario: precio,
      subtotal: this.cantidadAgregar * precio
    });

    this.selectedProducto = null;
    this.cantidadAgregar = 1;
    this.precioAgregar = 0;
  }

  quitarLinea(index: number) {
    this.detalles.splice(index, 1);
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

  descargarPlantilla(): void {
    const ejemploCliente = this.clientes[0]
      ? `${this.clientes[0].nombre} ${this.clientes[0].apellido}`
      : 'Nombre Apellido del cliente';
    const ejemploProducto = this.productos[0]?.nombre ?? 'Nombre del producto';

    const datos = [
      ['Campo', 'Valor'],
      ['cliente', ejemploCliente],
      ['observaciones', ''],
      ['es_pedido_especial', 'NO'],
      ['tipo_especial', ''],
      ['nota_especial', ''],
      ['fecha_limite_entrega', ''],
      [],
      ['producto', 'cantidad', 'precio_unitario'],
      [ejemploProducto, '1', ''],
    ];
    const instrucciones = [
      ['Instrucciones'],
      ['1. cliente: nombre completo tal como aparece en el sistema.'],
      ['2. es_pedido_especial: escriba SI o NO.'],
      ['3. tipo_especial: personalizado, regalo o corporativo (solo si es especial).'],
      ['4. fecha_limite_entrega: formato AAAA-MM-DDTHH:mm (ej. 2026-08-25T14:00).'],
      ['5. producto: nombre exacto del catálogo; cantidad obligatoria.'],
      ['6. precio_unitario es opcional; si queda vacío se usa el precio del catálogo.'],
    ];

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(datos), 'Pedido');
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(instrucciones), 'Instrucciones');
    XLSX.writeFile(wb, 'plantilla-pedido.xlsx');
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
        this.prepararImportacionExcel(rows);
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

        nuevosDetalles.push({
          idProducto: producto.idProducto,
          productoNombre: producto.nombre,
          cantidad,
          precioUnitario: precio,
          subtotal: cantidad * precio
        });
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

  private buscarClientePorNombre(texto: string): Cliente | undefined {
    const q = this.normalizarTexto(texto);
    if (!q) return undefined;

    const exacto = this.clientes.find(c =>
      this.normalizarTexto(`${c.nombre} ${c.apellido}`) === q ||
      this.normalizarTexto(`${c.apellido} ${c.nombre}`) === q
    );
    if (exacto) return exacto;

    return this.clientes.find(c => {
      const nombreCompleto = this.normalizarTexto(`${c.nombre} ${c.apellido}`);
      return nombreCompleto.includes(q) || q.includes(nombreCompleto);
    });
  }

  private buscarProductoPorNombre(texto: string): Producto | undefined {
    const q = this.normalizarTexto(texto);
    if (!q) return undefined;

    const exacto = this.productos.find(p => this.normalizarTexto(p.nombre) === q);
    if (exacto) return exacto;

    const parcial = this.productos.filter(p => {
      const nombre = this.normalizarTexto(p.nombre);
      return nombre.includes(q) || q.includes(nombre);
    });
    if (parcial.length === 1) return parcial[0];

    return this.productos.find(p => this.normalizarTexto(p.nombre).startsWith(q));
  }

  private celdaTexto(valor: string | number | null | undefined): string {
    if (valor === null || valor === undefined) return '';
    return String(valor).trim();
  }
}
