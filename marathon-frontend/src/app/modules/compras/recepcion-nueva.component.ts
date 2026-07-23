import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CrudService } from '../../core/services/crud.service';
import { ApiService } from '../../core/services/api.service';

interface Bodega { idBodega: number; nombre: string; }

interface DetalleOc {
  idDetalleOc: number;
  tipoItem: string;
  itemNombre: string;
  cantidad: number;
  cantidadRecibida: number;
}

interface OrdenCompra {
  idOrdenCompra: number;
  estado: string;
  proveedor: { nombre: string };
  detalles: DetalleOc[];
}

interface LineaRecepcion {
  idDetalleOc: number;
  tipoItem: string;
  itemNombre: string;
  pendiente: number;
  cantidadRecibidaAhora: number | null;
  cantidadDefectuosa: number;
  observacion: string;
}

@Component({
  selector: 'app-recepcion-nueva',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container" *ngIf="orden">
      <div class="toolbar">
        <h2>Registrar Recepción — OC #{{orden.idOrdenCompra}}</h2>
        <button class="btn-cancel" [routerLink]="['/compras', orden.idOrdenCompra]">← Volver</button>
      </div>

      <div class="form-card">
        <div class="form-row">
          <div class="form-group">
            <label>Proveedor</label>
            <input type="text" [value]="orden.proveedor?.nombre" disabled/>
          </div>
          <div class="form-group">
            <label>Bodega destino * <small>(aplica a líneas de producto)</small></label>
            <select [(ngModel)]="idBodega" name="idBodega">
              <option [ngValue]="null">-- Seleccione bodega --</option>
              <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{b.nombre}}</option>
            </select>
          </div>
          <div class="form-group">
            <label>N° guía de remisión</label>
            <input type="text" [(ngModel)]="numeroGuiaRemision" name="numeroGuiaRemision"/>
          </div>
        </div>

        <h3>Líneas pendientes por recibir</h3>
        <table class="data-table">
          <thead>
            <tr><th>Tipo</th><th>Ítem</th><th>Pendiente</th><th>Cantidad recibida ahora</th><th>Defectuosa</th><th>Observación</th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let l of lineas">
              <td>{{l.tipoItem === 'producto' ? 'Producto' : 'Materia Prima'}}</td>
              <td>{{l.itemNombre}}</td>
              <td>{{l.pendiente}}</td>
              <td>
                <input type="number" [(ngModel)]="l.cantidadRecibidaAhora" [name]="'rec'+l.idDetalleOc"
                       min="0" [max]="l.pendiente" style="width:90px"/>
              </td>
              <td>
                <input type="number" [(ngModel)]="l.cantidadDefectuosa" [name]="'def'+l.idDetalleOc"
                       min="0" [max]="l.cantidadRecibidaAhora || 0" style="width:80px"/>
                <div class="warn" *ngIf="l.cantidadDefectuosa > 0">Se registrará para revisión de devolución a proveedor (próximamente)</div>
              </td>
              <td><input type="text" [(ngModel)]="l.observacion" [name]="'obs'+l.idDetalleOc"/></td>
            </tr>
            <tr *ngIf="lineas.length === 0"><td colspan="6" class="empty">No hay líneas pendientes por recibir</td></tr>
          </tbody>
        </table>

        <div class="form-group">
          <label>Observaciones generales</label>
          <textarea [(ngModel)]="observaciones" name="observaciones" rows="2"></textarea>
        </div>

        <small class="error" *ngIf="formError">{{formError}}</small>
        <div class="modal-actions">
          <button type="button" class="btn-cancel" [routerLink]="['/compras', orden.idOrdenCompra]">Cancelar</button>
          <button type="button" class="btn-save" [disabled]="saving" (click)="confirmar()">{{saving ? 'Registrando...' : 'Confirmar recepción'}}</button>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .form-card { background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.05); border-radius: 12px; padding: 1.25rem; }
    .warn { color: #FFB74D; font-size: .7rem; margin-top: .25rem; max-width: 200px; }
  `]
})
export class RecepcionNuevaComponent implements OnInit {
  orden: OrdenCompra | null = null;
  bodegas: Bodega[] = [];
  lineas: LineaRecepcion[] = [];
  idOrden!: number;
  idBodega: number | null = null;
  numeroGuiaRemision = '';
  observaciones = '';
  formError = '';
  saving = false;
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private router: Router,
              private crud: CrudService, private api: ApiService) {}

  ngOnInit() {
    this.idOrden = Number(this.route.snapshot.paramMap.get('id'));
    this.crud.listar<Bodega>('bodegas', { page: 0, size: 1000 }).subscribe({ next: r => this.bodegas = r.content });
    this.crud.obtener<OrdenCompra>('ordenes-compra', this.idOrden).subscribe({
      next: (oc) => {
        this.orden = oc;
        if (!('aprobada' === oc.estado || 'recibida_parcial' === oc.estado)) {
          this.mostrarToast('La orden no está en un estado que permita recepción', true);
        }
        this.lineas = (oc.detalles || [])
          .filter(d => d.cantidadRecibida < d.cantidad)
          .map(d => ({
            idDetalleOc: d.idDetalleOc,
            tipoItem: d.tipoItem,
            itemNombre: d.itemNombre,
            pendiente: d.cantidad - d.cantidadRecibida,
            cantidadRecibidaAhora: null,
            cantidadDefectuosa: 0,
            observacion: ''
          }));
      },
      error: () => this.mostrarToast('Error al cargar la orden', true)
    });
  }

  confirmar() {
    this.formError = '';
    if (!this.idBodega) { this.formError = 'Debe seleccionar la bodega destino'; return; }

    const detalles = this.lineas
      .filter(l => l.cantidadRecibidaAhora && l.cantidadRecibidaAhora > 0)
      .map(l => ({
        idDetalleOc: l.idDetalleOc,
        cantidadRecibidaAhora: l.cantidadRecibidaAhora,
        cantidadDefectuosa: l.cantidadDefectuosa || 0,
        observacion: l.observacion || null
      }));

    if (detalles.length === 0) { this.formError = 'Debe registrar al menos una cantidad recibida'; return; }

    for (const l of this.lineas) {
      if (l.cantidadRecibidaAhora && l.cantidadRecibidaAhora > l.pendiente) {
        this.formError = `La cantidad recibida de "${l.itemNombre}" supera lo pendiente (${l.pendiente})`; return;
      }
      if ((l.cantidadDefectuosa || 0) > (l.cantidadRecibidaAhora || 0)) {
        this.formError = `La cantidad defectuosa de "${l.itemNombre}" supera lo recibido`; return;
      }
    }

    const body = {
      idOrdenCompra: this.idOrden,
      idBodega: this.idBodega,
      numeroGuiaRemision: this.numeroGuiaRemision || null,
      observaciones: this.observaciones || null,
      detalles
    };

    this.saving = true;
    this.api.post<any>('recepciones', body).subscribe({
      next: () => {
        this.saving = false;
        this.mostrarToast('Recepción registrada. Stock actualizado.');
        setTimeout(() => this.router.navigate(['/compras', this.idOrden]), 900);
      },
      error: (err) => { this.saving = false; this.formError = err.error?.message || 'Error al registrar la recepción'; }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
