import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-factura-compra-nueva',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Registrar Factura de Compra — OC #{{idOrden}}</h2>
        <button class="btn-cancel" [routerLink]="['/compras', idOrden]">← Volver</button>
      </div>

      <form (ngSubmit)="registrar()" class="form-grid">
        <div class="form-group">
          <label>Número de factura (proveedor) *</label>
          <input [(ngModel)]="form.numeroFacturaProveedor" name="numero" required placeholder="Ej: FAC-001-2024">
        </div>
        <div class="form-group">
          <label>Fecha factura *</label>
          <input type="date" [(ngModel)]="form.fechaFactura" name="fechaFactura" required>
        </div>
        <div class="form-group">
          <label>Fecha vencimiento *</label>
          <input type="date" [(ngModel)]="form.fechaVencimiento" name="fechaVencimiento" required>
        </div>
        <div class="form-group">
          <label>Subtotal *</label>
          <input type="number" step="0.01" min="0.01" [(ngModel)]="form.subtotal" name="subtotal" required>
        </div>
        <div class="form-group">
          <label>Impuesto (IVA)</label>
          <input type="number" step="0.01" min="0" [(ngModel)]="form.impuesto" name="impuesto">
        </div>
        <div class="form-group total-preview">
          <label>Total (preview)</label>
          <span class="total-value">$ {{totalPreview | number:'1.2-2'}}</span>
        </div>

        <div class="form-actions">
          <button type="submit" class="btn-save" [disabled]="guardando">
            {{guardando ? 'Registrando...' : 'Registrar factura'}}
          </button>
        </div>
      </form>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1.2rem; }
    .form-group { display: flex; flex-direction: column; gap: .4rem; }
    .form-group label { font-size: .75rem; text-transform: uppercase; letter-spacing: 1px; color: rgba(255,255,255,0.5); }
    .form-group input { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .7rem .9rem; border-radius: 8px; font-size: .9rem; }
    .form-group input:focus { border-color: #C9A84C; outline: none; }
    .form-actions { grid-column: 1 / -1; margin-top: 1rem; }
    .total-preview .total-value { font-size: 1.3rem; font-weight: 600; color: #C9A84C; }
    @media(max-width: 600px) { .form-grid { grid-template-columns: 1fr; } }
  `]
})
export class FacturaCompraNuevaComponent implements OnInit {
  idOrden!: number;
  form = {
    numeroFacturaProveedor: '',
    fechaFactura: '',
    fechaVencimiento: '',
    subtotal: 0,
    impuesto: 0
  };
  guardando = false;
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private router: Router, private api: ApiService) {}

  ngOnInit() {
    this.idOrden = Number(this.route.snapshot.paramMap.get('id'));
    const hoy = new Date().toISOString().split('T')[0];
    this.form.fechaFactura = hoy;
    // Vencimiento por defecto: +30 días
    const venc = new Date();
    venc.setDate(venc.getDate() + 30);
    this.form.fechaVencimiento = venc.toISOString().split('T')[0];
  }

  get totalPreview(): number {
    return (this.form.subtotal || 0) + (this.form.impuesto || 0);
  }

  registrar() {
    if (!this.form.numeroFacturaProveedor || !this.form.fechaFactura || !this.form.fechaVencimiento || !this.form.subtotal) {
      this.mostrarToast('Complete todos los campos obligatorios', true);
      return;
    }
    this.guardando = true;
    const body = {
      idOrdenCompra: this.idOrden,
      numeroFacturaProveedor: this.form.numeroFacturaProveedor,
      fechaFactura: this.form.fechaFactura,
      fechaVencimiento: this.form.fechaVencimiento,
      subtotal: this.form.subtotal,
      impuesto: this.form.impuesto || 0
    };
    this.api.post<any>('facturas-compra', body).subscribe({
      next: res => {
        this.guardando = false;
        const total = res.cuentaPorPagar?.montoTotal || res.total;
        this.mostrarToast('Factura registrada. Cuenta por pagar generada: $' + total);
        setTimeout(() => this.router.navigate(['/compras', this.idOrden]), 2000);
      },
      error: err => {
        this.guardando = false;
        this.mostrarToast(err.error?.message || 'Error al registrar factura', true);
      }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 4000);
  }
}
