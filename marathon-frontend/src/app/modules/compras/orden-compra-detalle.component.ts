import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CrudService } from '../../core/services/crud.service';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';

interface Detalle {
  idDetalleOc: number;
  tipoItem: string;
  itemNombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
  cantidadRecibida: number;
}

interface OrdenCompra {
  idOrdenCompra: number;
  esReposicion?: boolean;
  idDevolucionProv?: number;
  fechaOrden: string;
  fechaAprobacion: string;
  estado: string;
  total: number;
  observaciones: string;
  /** El backend solo lo rellena si la orden tiene proveedor cargado. */
  proveedor?: { idProveedor: number; nombre: string };
  usuarioSolicitante?: { nombre: string; apellido: string };
  /** Solo existe cuando la orden ya se aprobó: id_usuario_aprobador es NULLABLE. */
  usuarioAprobador?: { nombre: string; apellido: string };
  detalles: Detalle[];
}

interface RecepcionDetalle { cantidadRecibidaAhora: number; }
interface Recepcion {
  idRecepcion: number;
  fechaRecepcion: string;
  numeroGuiaRemision: string;
  receptorNombre: string;
  detalles: RecepcionDetalle[];
}

@Component({
  selector: 'app-orden-compra-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container" *ngIf="oc">
      <div class="toolbar">
        <h2>
          Orden de Compra #{{oc.idOrdenCompra}}
          <span class="rep-badge" *ngIf="oc.esReposicion">REPOSICIÓN · NO SE PAGA</span>
        </h2>
        <button class="btn-cancel" routerLink="/compras">← Volver</button>
      </div>

      <div class="detail-grid">
        <div class="detail-card">
          <span class="label">Proveedor</span><span>{{oc.proveedor?.nombre}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Estado</span>
          <span class="oc-badge" [ngClass]="'oc-' + oc.estado">{{etiqueta(oc.estado)}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Fecha orden</span><span>{{oc.fechaOrden | date:'dd/MM/yyyy HH:mm'}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Total</span><span class="total">$ {{oc.total | number:'1.2-2'}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Solicitante</span>
          <span>{{oc.usuarioSolicitante?.nombre}} {{oc.usuarioSolicitante?.apellido}}</span>
        </div>
        <div class="detail-card" *ngIf="oc.usuarioAprobador">
          <span class="label">Aprobador</span>
           <span>{{oc.usuarioAprobador.nombre}} {{oc.usuarioAprobador.apellido}} ({{oc.fechaAprobacion | date:'dd/MM/yyyy HH:mm'}})</span>
        </div>
        <div class="detail-card wide" *ngIf="oc.observaciones">
          <span class="label">Observaciones</span><span>{{oc.observaciones}}</span>
        </div>
      </div>

      <h3>Líneas</h3>
      <table class="data-table">
        <thead>
          <tr><th>Tipo</th><th>Ítem</th><th>Cantidad</th><th>P. Unit.</th><th>Subtotal</th><th>Recibido</th><th>Pendiente por recibir</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let d of oc.detalles">
            <td>{{d.tipoItem === 'producto' ? 'Producto' : 'Materia Prima'}}</td>
            <td>{{d.itemNombre}}</td>
            <td>{{d.cantidad}}</td>
            <td>$ {{d.precioUnitario | number:'1.2-2'}}</td>
            <td>$ {{d.subtotal | number:'1.2-2'}}</td>
            <td>{{d.cantidadRecibida}}</td>
            <td [class.pendiente-cero]="(d.cantidad - d.cantidadRecibida) === 0">{{d.cantidad - d.cantidadRecibida}}</td>
          </tr>
        </tbody>
      </table>

      <h3 *ngIf="recepciones.length > 0">Historial de recepciones</h3>
      <table class="data-table" *ngIf="recepciones.length > 0">
        <thead>
          <tr><th>#</th><th>Fecha</th><th>Guía</th><th>Receptor</th><th>Cantidad total recibida</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of recepciones">
            <td>{{r.idRecepcion}}</td>
            <td>{{r.fechaRecepcion | date:'dd/MM/yyyy HH:mm'}}</td>
            <td>{{r.numeroGuiaRemision || '—'}}</td>
            <td>{{r.receptorNombre}}</td>
            <td>{{totalRecibido(r)}}</td>
          </tr>
        </tbody>
      </table>

      <div class="rep-aviso" *ngIf="oc.esReposicion">
        <strong>Esta orden es una reposición del proveedor.</strong>
        La generó la devolución
        <a [routerLink]="['/devoluciones-proveedor', oc.idDevolucionProv]" *ngIf="oc.idDevolucionProv">#{{oc.idDevolucionProv}}</a>
        <span *ngIf="!oc.idDevolucionProv">a proveedor</span>.
        Recíbela como cualquier otra entrada —el stock sube igual— pero
        <strong>no se factura ni genera cuenta por pagar</strong>: esta mercancía ya se
        pagó cuando se compró la que salió defectuosa. El precio de las líneas está para
        que el costo de bodega siga siendo correcto, no para cobrarlo.
      </div>

      <div class="acciones-estado">
        <button *ngIf="(oc.estado === 'aprobada' || oc.estado === 'recibida_parcial') && esCompras"
                class="btn-save" [routerLink]="['/compras', oc.idOrdenCompra, 'recepcion']">Registrar recepción</button>

        <button *ngIf="puedeDocumentar()"
                class="btn-save factura-btn" [disabled]="documentando"
                (click)="documentarCompra()">
          {{ documentando ? 'Generando documento…' : (documentos.length ? 'Documentar lo que falta' : 'Documentar compra') }}
        </button>

        <button *ngFor="let d of documentos" class="btn-save ver-pdf-btn"
                [disabled]="abriendo === d.idFacturaCompra"
                (click)="abrirPdf(d.idFacturaCompra)">
          {{ abriendo === d.idFacturaCompra ? 'Abriendo…' : 'Ver PDF · ' + d.numeroFacturaProveedor }}
        </button>

        <button *ngIf="oc.estado === 'borrador' && esCompras"
                class="btn-save" (click)="cambiar('pendiente_aprobacion')">Enviar a aprobación</button>

        <button *ngIf="oc.estado === 'pendiente_aprobacion' && esAdmin"
                class="btn-save" (click)="cambiar('aprobada')">Aprobar</button>
        <button *ngIf="oc.estado === 'pendiente_aprobacion' && esAdmin"
                class="btn-delete" (click)="cambiar('rechazada')">Rechazar</button>

        <button *ngIf="puedeCancelar()"
                class="btn-delete" (click)="cambiar('cancelada')">Cancelar</button>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .detail-card { display: flex; flex-direction: column; gap: .35rem; background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.05); border-radius: 10px; padding: .85rem 1rem; }
    .detail-card.wide { grid-column: 1 / -1; }
    .detail-card .label { font-size: .7rem; text-transform: uppercase; letter-spacing: 1px; color: rgba(255,255,255,0.4); }
    .detail-card .total { color: #C9A84C; font-size: 1.1rem; font-weight: 600; }
    .acciones-estado { display: flex; gap: 1rem; margin-top: 1.5rem; flex-wrap: wrap; }
    .rep-badge { font-size: .62rem; font-weight: 700; letter-spacing: .06em;
                 background: rgba(121,196,210,.15); border: 1px solid #79C4D2;
                 color: #79C4D2; padding: .2rem .5rem; border-radius: 99px;
                 margin-left: .6rem; vertical-align: middle; }
    .rep-aviso { border: 1px solid #79C4D2; border-left-width: 3px;
                 background: rgba(121,196,210,.07); border-radius: 8px;
                 padding: 1rem 1.2rem; margin: 1rem 0; font-size: .87rem;
                 line-height: 1.6; color: rgba(255,255,255,0.7); }
    .rep-aviso strong { color: rgba(255,255,255,0.92); }
    .rep-aviso a { color: #79C4D2; }
    .ver-pdf-btn { background: rgba(255,255,255,0.04) !important;
                   border-color: rgba(255,255,255,0.14) !important;
                   color: rgba(255,255,255,0.75) !important; }
    .ver-pdf-btn:hover:not(:disabled) { border-color: #C9A84C !important; color: #C9A84C !important; }
    .factura-btn { background: rgba(201,168,76,0.15) !important; border-color: rgba(201,168,76,0.4) !important; color: #C9A84C !important; }
    .pendiente-cero { color: #81C784; font-weight: 600; }
  `]
})
export class OrdenCompraDetalleComponent implements OnInit {
  oc: OrdenCompra | null = null;
  recepciones: Recepcion[] = [];
  id!: number;
  esAdmin = false;
  esCompras = false;
  toast = '';
  toastError = false;
  documentando = false;

  /** Los documentos ya emitidos de esta orden. */
  documentos: any[] = [];
  /** El id del documento que se está abriendo, para no dejar dos clics sueltos. */
  abriendo: number | null = null;

  private etiquetas: Record<string, string> = {
    borrador: 'Borrador',
    pendiente_aprobacion: 'Pendiente aprob.',
    aprobada: 'Aprobada',
    rechazada: 'Rechazada',
    recibida_parcial: 'Recibida parcial',
    recibida_completa: 'Recibida completa',
    cancelada: 'Cancelada'
  };

  constructor(private route: ActivatedRoute, private crud: CrudService,
              private api: ApiService, private auth: AuthService) {}

  ngOnInit() {
    this.esAdmin = this.auth.hasRol('Administrador');
    this.esCompras = this.auth.hasRol('Encargado de Compras') || this.esAdmin;
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar();
  }

  etiqueta(estado: string): string { return this.etiquetas[estado] || estado; }

  cargar() {
    this.crud.obtener<OrdenCompra>('ordenes-compra', this.id).subscribe({
      next: res => { this.oc = res; },
      error: () => { this.mostrarToast('Error al cargar la orden', true); }
    });
    this.api.get<Recepcion[]>(`recepciones/orden/${this.id}`).subscribe({
      next: res => { this.recepciones = res; },
      error: () => { /* silencioso: puede no haber recepciones */ }
    });
    this.cargarDocumentos();
  }

  totalRecibido(r: Recepcion): number {
    return (r.detalles || []).reduce((acc, d) => acc + (d.cantidadRecibidaAhora || 0), 0);
  }

  puedeCancelar(): boolean {
    if (!this.oc) return false;
    const cancelables = ['borrador', 'pendiente_aprobacion', 'aprobada'];
    return cancelables.includes(this.oc.estado) && this.esCompras;
  }

  cambiar(estado: string) {
    this.api.put<OrdenCompra>(`ordenes-compra/${this.id}/estado`, { estado }).subscribe({
      next: res => { this.oc = res; this.mostrarToast('Estado actualizado a ' + this.etiqueta(estado)); },
      error: (err) => { this.mostrarToast(err.error?.message || 'Error al cambiar estado', true); }
    });
  }

  private cargarDocumentos() {
    this.api.get<any[]>(`facturas-compra/orden/${this.id}`).subscribe({
      next: res => { this.documentos = (res || []).filter(d => d.estado !== 'anulada'); },
      error: () => { this.documentos = []; }
    });
  }

  /**
   * ¿Queda algo por documentar? (F70)
   *
   * Antes el botón hacía dos cosas —documentar y abrir el PDF— y por eso, una
   * vez documentada la orden, **no había forma de volver a abrir el PDF solo
   * para mirarlo**: el botón seguía ahí, pero al pulsarlo intentaba documentar
   * otra vez y el servidor lo rechazaba, con razón.
   *
   * Ahora son dos botones. Este aparece solo si de verdad queda algo pendiente:
   * lo recibido menos lo ya documentado. Con recepciones parciales eso importa,
   * porque una orden puede documentarse dos veces —una por tanda— y el botón
   * tiene que seguir estando entre una y otra.
   */
  puedeDocumentar(): boolean {
    if (!this.oc || !this.esCompras || this.oc.esReposicion) { return false; }
    if (this.oc.estado !== 'recibida_parcial' && this.oc.estado !== 'recibida_completa') { return false; }
    return this.pendientePorDocumentar() > 0.005;
  }

  /** Lo recibido menos lo ya documentado. Es la misma resta que hace el servidor. */
  private pendientePorDocumentar(): number {
    const recibido = (this.oc?.detalles || [])
      .reduce((s, d) => s + (d.cantidadRecibida || 0) * (d.precioUnitario || 0), 0);
    const documentado = this.documentos
      .reduce((s, d) => s + Number(d.subtotal || 0), 0);
    return recibido - documentado;
  }

  /** Abre un documento ya emitido, sin volver a crear nada. */
  abrirPdf(idFactura: number) {
    if (this.abriendo) { return; }
    this.abriendo = idFactura;
    this.api.getBlob(`facturas-compra/${idFactura}/pdf`).subscribe({
      next: (pdf: Blob) => {
        this.abriendo = null;
        const url = URL.createObjectURL(pdf);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: () => {
        this.abriendo = null;
        this.mostrarToast('No se pudo abrir el documento', true);
      }
    });
  }

  /**
   * Documenta lo recibido y abre el PDF, en un solo clic (F66).
   *
   * Antes esto llevaba a un formulario donde había que teclear número, fechas,
   * subtotal e impuesto. Los cuatro se deducen de la orden y de sus
   * recepciones, así que pedirlos era pedirle al usuario datos que el sistema
   * ya tiene — y además abría la puerta a teclear un importe que no cuadrase
   * con lo recibido.
   *
   * **El importe es lo recibido menos lo ya documentado.** Si la orden se
   * recibió en dos veces, el segundo documento cubre solo la diferencia.
   */
  documentarCompra() {
    if (this.documentando) { return; }
    this.documentando = true;

    this.api.post<any>(`facturas-compra/orden/${this.id}/desde-recepcion`, {}).subscribe({
      next: factura => {
        this.documentando = false;
        this.mostrarToast('Documento ' + factura.numeroFacturaProveedor + ' registrado');
        this.abrirPdf(factura.idFacturaCompra);
        this.cargar();
        this.cargarDocumentos();
      },
      error: err => {
        this.documentando = false;
        this.mostrarToast(err.error?.message || 'No se pudo documentar la compra', true);
      }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
