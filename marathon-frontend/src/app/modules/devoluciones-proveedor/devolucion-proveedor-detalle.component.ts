import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-devolucion-proveedor-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container" *ngIf="dev">
      <div class="toolbar">
        <h2>Devolucion a Proveedor #{{dev.idDevolucionProv}}</h2>
        <button class="btn-cancel" routerLink="/devoluciones-proveedor">Volver</button>
      </div>

      <div class="detail-grid">
        <div class="detail-card"><span class="label">Proveedor</span><span>{{dev.proveedorNombre}}</span></div>
        <div class="detail-card"><span class="label">Estado</span><span class="dp-badge" [ngClass]="'dp-' + dev.estado">{{dev.estado}}</span></div>
        <div class="detail-card"><span class="label">Fecha</span><span>{{dev.fechaDevolucion | date:'dd/MM/yyyy HH:mm'}}</span></div>
        <div class="detail-card" *ngIf="dev.tipoResolucion"><span class="label">Resolucion</span><span>{{dev.tipoResolucion}}</span></div>
        <div class="detail-card" *ngIf="dev.montoReembolso"><span class="label">Monto reembolso</span><span class="total">$ {{dev.montoReembolso | number:'1.2-2'}}</span></div>
        <div class="detail-card wide" *ngIf="dev.observaciones"><span class="label">Observaciones</span><span>{{dev.observaciones}}</span></div>
      </div>

      <h3>Lineas</h3>
      <table class="data-table">
        <thead><tr><th>Origen</th><th>Producto</th><th>Cantidad</th><th>Motivo</th><th>Referencia</th></tr></thead>
        <tbody>
          <tr *ngFor="let d of dev.detalles">
            <td><span class="orig-badge" [ngClass]="'orig-' + d.origen">{{d.origen === 'rma_cliente' ? 'RMA' : 'Recepcion'}}</span></td>
            <td>{{d.productoNombre}}</td>
            <td>{{d.cantidad}}</td>
            <td>{{d.motivo || '-'}}</td>
            <td>{{d.referenciaOrigen}}</td>
          </tr>
        </tbody>
      </table>

      <div class="acciones">
        <button *ngIf="dev.estado === 'pendiente' && esCompras" class="btn-save" (click)="marcarEnviada()">Marcar como enviada</button>

        <div *ngIf="dev.estado === 'enviada' && esCompras" class="resolver-form">
          <h3>Resolver devolucion</h3>
          <div class="form-grid">
            <div class="form-group"><label>Tipo resolucion *</label>
              <select [(ngModel)]="resolucion.tipoResolucion"><option value="">Seleccione...</option><option value="reembolso">Reembolso</option><option value="reposicion">Reposicion</option></select>
            </div>
            <div class="form-group" *ngIf="resolucion.tipoResolucion === 'reembolso'"><label>Monto *</label>
              <input type="number" step="0.01" min="0.01" [(ngModel)]="resolucion.montoReembolso">
            </div>
            <div class="form-group wide"><label>Observaciones</label><input [(ngModel)]="resolucion.observaciones"></div>
          </div>
          <button class="btn-save" (click)="resolverDev()" [disabled]="guardando">Resolver</button>
          <button class="btn-delete" (click)="rechazar()" [disabled]="guardando">Rechazar</button>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .detail-card { display: flex; flex-direction: column; gap: .35rem; background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.05); border-radius: 10px; padding: .85rem 1rem; }
    .detail-card.wide { grid-column: 1 / -1; }
    .detail-card .label { font-size: .7rem; text-transform: uppercase; letter-spacing: 1px; color: rgba(255,255,255,0.4); }
    .detail-card .total { color: #C9A84C; font-size: 1.1rem; font-weight: 600; }
    .acciones { margin-top: 1.5rem; }
    .resolver-form { margin-top: 1.5rem; padding-top: 1rem; border-top: 1px solid rgba(255,255,255,0.06); }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1rem; }
    .form-group { display: flex; flex-direction: column; gap: .4rem; }
    .form-group.wide { grid-column: 1 / -1; }
    .form-group label { font-size: .75rem; text-transform: uppercase; color: rgba(255,255,255,0.5); }
    .form-group input, .form-group select { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .7rem; border-radius: 8px; }
  `]
})
export class DevolucionProveedorDetalleComponent implements OnInit {
  dev: any = null;
  esCompras = false;
  guardando = false;
  resolucion = { tipoResolucion: '', montoReembolso: 0, observaciones: '' };
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private router: Router,
              private api: ApiService, private auth: AuthService) {}

  ngOnInit() {
    this.esCompras = this.auth.hasRol('Administrador') || this.auth.hasRol('Encargado de Compras');
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  cargar(id: number) {
    this.api.get<any>('devoluciones-proveedor/' + id).subscribe({
      next: (res: any) => { this.dev = res; },
      error: () => { this.mostrarToast('Error al cargar', true); }
    });
  }

  marcarEnviada() {
    this.api.put<any>('devoluciones-proveedor/' + this.dev.idDevolucionProv + '/estado', { estado: 'enviada' }).subscribe({
      next: (res: any) => { this.dev = res; this.mostrarToast('Marcada como enviada'); },
      error: (err: any) => { this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  resolverDev() {
    if (!this.resolucion.tipoResolucion) { this.mostrarToast('Seleccione tipo de resolucion', true); return; }
    this.guardando = true;
    const body: any = { tipoResolucion: this.resolucion.tipoResolucion, observaciones: this.resolucion.observaciones || null };
    if (this.resolucion.tipoResolucion === 'reembolso') body.montoReembolso = this.resolucion.montoReembolso;
    this.api.post<any>('devoluciones-proveedor/' + this.dev.idDevolucionProv + '/resolver', body).subscribe({
      next: () => { this.guardando = false; this.router.navigate(['/devoluciones-proveedor']); },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  rechazar() {
    this.guardando = true;
    this.api.put<any>('devoluciones-proveedor/' + this.dev.idDevolucionProv + '/estado', { estado: 'rechazada' }).subscribe({
      next: () => { this.guardando = false; this.router.navigate(['/devoluciones-proveedor']); },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
