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
  fechaOrden: string;
  fechaAprobacion: string;
  estado: string;
  total: number;
  observaciones: string;
  proveedor: { idProveedor: number; nombre: string };
  usuarioSolicitante: { nombre: string; apellido: string };
  usuarioAprobador: { nombre: string; apellido: string };
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
        <h2>Orden de Compra #{{oc.idOrdenCompra}}</h2>
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
          <span>{{oc.usuarioAprobador?.nombre}} {{oc.usuarioAprobador?.apellido}} ({{oc.fechaAprobacion | date:'dd/MM/yyyy HH:mm'}})</span>
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

      <div class="acciones-estado">
        <button *ngIf="(oc.estado === 'aprobada' || oc.estado === 'recibida_parcial') && esCompras"
                class="btn-save" [routerLink]="['/compras', oc.idOrdenCompra, 'recepcion']">Registrar recepción</button>

        <button *ngIf="(oc.estado === 'recibida_parcial' || oc.estado === 'recibida_completa') && esCompras"
                class="btn-save factura-btn" [routerLink]="['/compras', oc.idOrdenCompra, 'factura']">Registrar factura de compra</button>

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
    .factura-btn { background: rgba(201,168,76,0.15) !important; border-color: rgba(201,168,76,0.4) !important; color: #C9A84C !important; }
    .pendiente-cero { color: #16a34a; font-weight: 600; }
    .oc-badge { padding: .25rem .6rem; border-radius: 12px; font-size: .72rem; font-weight: 600; text-transform: uppercase; letter-spacing: .5px; color: #fff; width: fit-content; }
    .oc-borrador { background: #6b7280; }
    .oc-pendiente_aprobacion { background: #d97706; }
    .oc-aprobada { background: #16a34a; }
    .oc-rechazada { background: #dc2626; }
    .oc-recibida_parcial { background: #2563eb; }
    .oc-recibida_completa { background: #14532d; }
    .oc-cancelada { background: #7f1d1d; }
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

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
