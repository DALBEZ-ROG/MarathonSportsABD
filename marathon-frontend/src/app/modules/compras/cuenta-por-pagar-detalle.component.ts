import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';

interface Pago {
  idPago: number;
  monto: number;
  fechaPago: string;
  metodoPago: string;
  referencia: string;
  observaciones: string;
  usuarioNombre: string;
}

interface CuentaDetalle {
  idCuentaPagar: number;
  idFacturaCompra: number;
  numeroFacturaProveedor: string;
  proveedorNombre: string;
  montoTotal: number;
  montoPagado: number;
  saldoPendiente: number;
  fechaVencimiento: string;
  estado: string;
  pagos: Pago[];
}

@Component({
  selector: 'app-cuenta-por-pagar-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container" *ngIf="cuenta">
      <div class="toolbar">
        <h2>Cuenta por Pagar #{{cuenta.idCuentaPagar}}</h2>
        <button class="btn-cancel" routerLink="/cuentas-por-pagar">← Volver</button>
      </div>

      <div class="detail-grid">
        <div class="detail-card">
          <span class="label">Proveedor</span><span>{{cuenta.proveedorNombre}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Factura #</span><span>{{cuenta.numeroFacturaProveedor}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Monto total</span><span class="total">$ {{cuenta.montoTotal | number:'1.2-2'}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Pagado</span><span>$ {{cuenta.montoPagado | number:'1.2-2'}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Saldo pendiente</span>
          <span class="saldo">$ {{cuenta.saldoPendiente | number:'1.2-2'}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Vencimiento</span>
          <span [class.vencida]="cuenta.estado === 'vencida'">{{cuenta.fechaVencimiento}}</span>
        </div>
        <div class="detail-card">
          <span class="label">Estado</span>
          <span class="cxp-badge" [ngClass]="'cxp-' + cuenta.estado">{{cuenta.estado | uppercase}}</span>
        </div>
      </div>

      <!-- Historial de pagos -->
      <h3 *ngIf="cuenta.pagos && cuenta.pagos.length > 0">Historial de pagos</h3>
      <table class="data-table" *ngIf="cuenta.pagos && cuenta.pagos.length > 0">
        <thead>
          <tr><th>Fecha</th><th>Monto</th><th>Método</th><th>Referencia</th><th>Registrado por</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of cuenta.pagos">
            <td>{{p.fechaPago | date:'dd/MM/yyyy HH:mm'}}</td>
            <td>$ {{p.monto | number:'1.2-2'}}</td>
            <td>{{p.metodoPago}}</td>
            <td>{{p.referencia || '—'}}</td>
            <td>{{p.usuarioNombre}}</td>
          </tr>
        </tbody>
      </table>

      <!-- Formulario de pago -->
      <div class="pago-form" *ngIf="cuenta.estado !== 'pagada'">
        <h3>Registrar pago</h3>
        <p class="saldo-ref">Saldo pendiente: <strong>$ {{cuenta.saldoPendiente | number:'1.2-2'}}</strong></p>
        <form (ngSubmit)="registrarPago()" class="form-grid">
          <div class="form-group">
            <label>Monto *</label>
            <input type="number" step="0.01" min="0.01" [max]="cuenta.saldoPendiente"
                   [(ngModel)]="pago.monto" name="monto" required
                   [class.input-error]="pago.monto > cuenta.saldoPendiente">
            <span class="error-hint" *ngIf="pago.monto > cuenta.saldoPendiente">
              Excede el saldo pendiente
            </span>
          </div>
          <div class="form-group">
            <label>Método de pago *</label>
            <select [(ngModel)]="pago.metodoPago" name="metodoPago" required>
              <option value="">Seleccione...</option>
              <option value="transferencia">Transferencia</option>
              <option value="cheque">Cheque</option>
              <option value="efectivo">Efectivo</option>
              <option value="tarjeta">Tarjeta</option>
            </select>
          </div>
          <div class="form-group">
            <label>Referencia</label>
            <input [(ngModel)]="pago.referencia" name="referencia" placeholder="Nro. transferencia, cheque, etc.">
          </div>
          <div class="form-group">
            <label>Observaciones</label>
            <input [(ngModel)]="pago.observaciones" name="observaciones">
          </div>
          <div class="form-actions">
            <button type="submit" class="btn-save" [disabled]="guardando || pago.monto > cuenta.saldoPendiente">
              {{guardando ? 'Registrando...' : 'Registrar pago'}}
            </button>
          </div>
        </form>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .detail-card { display: flex; flex-direction: column; gap: .35rem; background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.05); border-radius: 10px; padding: .85rem 1rem; }
    .detail-card .label { font-size: .7rem; text-transform: uppercase; letter-spacing: 1px; color: rgba(255,255,255,0.4); }
    .detail-card .total { color: #C9A84C; font-size: 1.1rem; font-weight: 600; }
    .saldo { color: #C9A84C; font-weight: 600; font-size: 1.1rem; }
    .vencida { color: #E57373; font-weight: 600; }
    .cxp-badge { padding: .25rem .6rem; border-radius: 12px; font-size: .72rem; font-weight: 600; color: #fff; width: fit-content; }
    .cxp-vigente { background: #2563eb; }
    .cxp-vencida { background: #dc2626; }
    .cxp-pagada { background: #16a34a; }
    .pago-form { margin-top: 2rem; padding-top: 1.5rem; border-top: 1px solid rgba(255,255,255,0.06); }
    .saldo-ref { color: rgba(255,255,255,0.6); margin-bottom: 1rem; }
    .saldo-ref strong { color: #C9A84C; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.2rem; }
    .form-group { display: flex; flex-direction: column; gap: .4rem; }
    .form-group label { font-size: .75rem; text-transform: uppercase; letter-spacing: 1px; color: rgba(255,255,255,0.5); }
    .form-group input, .form-group select { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .7rem .9rem; border-radius: 8px; font-size: .9rem; }
    .form-group input:focus, .form-group select:focus { border-color: #C9A84C; outline: none; }
    .input-error { border-color: #E57373 !important; }
    .error-hint { font-size: .75rem; color: #E57373; }
    .form-actions { grid-column: 1 / -1; margin-top: 1rem; }
    @media(max-width: 600px) { .form-grid { grid-template-columns: 1fr; } }
  `]
})
export class CuentaPorPagarDetalleComponent implements OnInit {
  cuenta: CuentaDetalle | null = null;
  pago = { monto: 0, metodoPago: '', referencia: '', observaciones: '' };
  guardando = false;
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private api: ApiService) {}

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  cargar(id: number) {
    this.api.get<CuentaDetalle>(`cuentas-por-pagar/${id}`).subscribe({
      next: res => { this.cuenta = res; },
      error: () => { this.mostrarToast('Error al cargar la cuenta', true); }
    });
  }

  registrarPago() {
    if (!this.cuenta || !this.pago.monto || !this.pago.metodoPago) {
      this.mostrarToast('Complete los campos obligatorios', true);
      return;
    }
    if (this.pago.monto > this.cuenta.saldoPendiente) {
      this.mostrarToast('El monto excede el saldo pendiente', true);
      return;
    }
    this.guardando = true;
    const body = {
      idCuentaPagar: this.cuenta.idCuentaPagar,
      monto: this.pago.monto,
      metodoPago: this.pago.metodoPago,
      referencia: this.pago.referencia || null,
      observaciones: this.pago.observaciones || null
    };
    this.api.post<any>('pagos-proveedor', body).subscribe({
      next: res => {
        this.guardando = false;
        const saldo = res.saldoResultante;
        if (saldo === 0 || saldo === '0' || saldo === '0.00') {
          this.mostrarToast('Cuenta saldada completamente');
        } else {
          this.mostrarToast('Pago registrado. Saldo restante: $' + saldo);
        }
        this.pago = { monto: 0, metodoPago: '', referencia: '', observaciones: '' };
        this.cargar(this.cuenta!.idCuentaPagar);
      },
      error: err => {
        this.guardando = false;
        this.mostrarToast(err.error?.message || 'Error al registrar pago', true);
      }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 4000);
  }
}
