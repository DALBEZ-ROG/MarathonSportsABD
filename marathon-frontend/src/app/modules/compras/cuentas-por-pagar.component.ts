import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

interface CuentaPorPagar {
  idCuentaPagar: number;
  idFacturaCompra: number;
  numeroFacturaProveedor: string;
  proveedorNombre: string;
  montoTotal: number;
  montoPagado: number;
  saldoPendiente: number;
  fechaVencimiento: string;
  estado: string;
}

@Component({
  selector: 'app-cuentas-por-pagar',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Cuentas por Pagar</h2>
        <div class="filters">
          <select [(ngModel)]="filtroEstado" (change)="onFiltro()" class="select-filter">
            <option value="">Todos los estados</option>
            <option value="vigente">Vigente</option>
            <option value="vencida">Vencida</option>
            <option value="pagada">Pagada</option>
          </select>
        </div>
      </div>

      <div class="banner-vencidas" *ngIf="cuentasVencidas > 0">
        {{cuentasVencidas}} cuenta(s) vencida(s) por un total de \${{totalVencido | number:'1.2-2'}}
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr>
            <th>Proveedor</th><th>Factura #</th><th>Monto total</th>
            <th>Pagado</th><th>Saldo</th><th>Vencimiento</th><th>Estado</th><th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let c of data">
            <td>{{c.proveedorNombre}}</td>
            <td>{{c.numeroFacturaProveedor}}</td>
            <td>$ {{c.montoTotal | number:'1.2-2'}}</td>
            <td>$ {{c.montoPagado | number:'1.2-2'}}</td>
            <td class="saldo">$ {{c.saldoPendiente | number:'1.2-2'}}</td>
            <td [class.vencida]="esVencida(c)">{{c.fechaVencimiento}}</td>
            <td><span class="cxp-badge" [ngClass]="'cxp-' + c.estado">{{etiqueta(c.estado)}}</span></td>
            <td><button class="btn-icon" [routerLink]="['/cuentas-por-pagar', c.idCuentaPagar]" title="Ver detalle / Pagar"><app-icon name="credit-card" [size]="16"/></button></td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="8" class="empty">No hay cuentas por pagar</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">Anterior</button>
        <span>Pagina {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente</button>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .banner-vencidas { background: rgba(220,38,38,0.1); border: 1px solid rgba(220,38,38,0.3); border-radius: 10px; padding: .8rem 1.2rem; margin-bottom: 1rem; color: #E57373; font-weight: 500; }
    .saldo { color: #C9A84C; font-weight: 600; }
    .vencida { color: #E57373; font-weight: 600; }
    .cxp-badge { padding: .25rem .6rem; border-radius: 12px; font-size: .72rem; font-weight: 600; text-transform: uppercase; letter-spacing: .5px; color: #fff; }
    .cxp-vigente { background: #2563eb; }
    .cxp-vencida { background: #dc2626; }
    .cxp-pagada { background: #16a34a; }
  `]
})
export class CuentasPorPagarComponent implements OnInit {
  data: CuentaPorPagar[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroEstado = '';
  cuentasVencidas = 0;
  totalVencido = 0;
  toast = '';
  toastError = false;

  private etiquetas: Record<string, string> = { vigente: 'Vigente', vencida: 'Vencida', pagada: 'Pagada' };

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit() { this.cargar(); this.cargarResumenVencidas(); }

  etiqueta(estado: string): string { return this.etiquetas[estado] || estado; }

  esVencida(c: CuentaPorPagar): boolean {
    return c.estado === 'vencida' || (c.estado === 'vigente' && new Date(c.fechaVencimiento) < new Date());
  }

  cargar() {
    this.loading = true;
    let url = 'cuentas-por-pagar?page=' + this.page + '&size=' + this.size;
    if (this.filtroEstado) { url += '&estado=' + this.filtroEstado; }

    this.api.get<any>(url).subscribe({
      next: (res: any) => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; this.mostrarToast('Error al cargar cuentas por pagar', true); }
    });
  }

  cargarResumenVencidas() {
    this.api.get<any>('cuentas-por-pagar?estado=vencida&size=1000').subscribe({
      next: (res: any) => {
        this.cuentasVencidas = res.totalElements || 0;
        this.totalVencido = (res.content || []).reduce((acc: number, c: any) => acc + (c.saldoPendiente || 0), 0);
      }
    });
  }

  onFiltro() { this.page = 0; this.cargar(); }
  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
