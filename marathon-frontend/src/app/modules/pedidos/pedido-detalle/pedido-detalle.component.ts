import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface DetallePedido {
  idDetalle: number;
  productoId: number;
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

interface PedidoDetalle {
  idPedido: number;
  numeroPedido: string;
  fechaPedido: string;
  total: number;
  estado: string;
  observaciones: string;
  clienteNombre: string;
  usuarioNombre: string;
  detalles: DetallePedido[];
}

@Component({
  selector: 'app-pedido-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container" *ngIf="pedido">
      <div class="header">
        <div>
          <h2>Pedido {{pedido.numeroPedido}}</h2>
          <span class="status-badge" [ngClass]="'status-'+pedido.estado">{{pedido.estado}}</span>
        </div>
        <button class="btn-back" (click)="volver()">← Volver</button>
      </div>

      <div class="info-grid">
        <div class="info-card">
          <label>Cliente</label>
          <span>{{pedido.clienteNombre}}</span>
        </div>
        <div class="info-card">
          <label>Fecha</label>
          <span>{{pedido.fechaPedido | date:'dd/MM/yyyy HH:mm'}}</span>
        </div>
        <div class="info-card">
          <label>Registrado por</label>
          <span>{{pedido.usuarioNombre}}</span>
        </div>
        <div class="info-card">
          <label>Total</label>
          <span class="total">\${{pedido.total | number:'1.2-2'}}</span>
        </div>
      </div>

      <div class="info-card full" *ngIf="pedido.observaciones">
        <label>Observaciones</label>
        <span>{{pedido.observaciones}}</span>
      </div>

      <div class="section">
        <h3>Detalle de Productos</h3>
        <table class="detail-table">
          <thead>
            <tr><th>Producto</th><th>Cantidad</th><th>P. Unitario</th><th>Subtotal</th></tr>
          </thead>
          <tbody>
            <tr *ngFor="let d of pedido.detalles">
              <td>{{d.productoNombre}}</td>
              <td>{{d.cantidad}}</td>
              <td>\${{d.precioUnitario | number:'1.2-2'}}</td>
              <td class="total">\${{d.subtotal | number:'1.2-2'}}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Cambiar Estado -->
      <div class="section" *ngIf="puedesCambiarEstado()">
        <h3>Cambiar Estado</h3>
        <div class="estado-actions">
          <button *ngIf="pedido.estado==='pendiente'" class="btn-estado procesado" (click)="cambiarEstado('procesado')">Marcar como Procesado</button>
          <button *ngIf="pedido.estado==='procesado'" class="btn-estado enviado" (click)="cambiarEstado('enviado')">Marcar como Enviado</button>
          <button *ngIf="pedido.estado==='enviado'" class="btn-estado entregado" (click)="cambiarEstado('entregado')">Marcar como Entregado</button>
          <button *ngIf="pedido.estado==='pendiente' || pedido.estado==='procesado'" class="btn-estado anulado" (click)="cambiarEstado('anulado')">Anular Pedido</button>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>

    <div class="spinner" *ngIf="!pedido">Cargando pedido...</div>
  `,
  styles: [`
    .container{max-width:900px;margin:0 auto}
    .header{display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem}
    .header h2{color:#2d5a27;margin:0 .8rem 0 0;display:inline}
    .btn-back{background:none;border:1px solid #ddd;padding:.4rem .8rem;border-radius:4px;cursor:pointer}
    .btn-back:hover{background:#f5f5f5}
    .status-badge{padding:.3rem .8rem;border-radius:12px;font-size:.8rem;font-weight:600;text-transform:capitalize}
    .status-pendiente{background:#fff8e1;color:#f57f17}
    .status-procesado{background:#e3f2fd;color:#1565c0}
    .status-enviado{background:#fff3e0;color:#e65100}
    .status-entregado{background:#e8f5e9;color:#2e7d32}
    .status-anulado{background:#ffebee;color:#c62828}
    .info-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:1rem;margin-bottom:1.5rem}
    .info-card{background:#fff;border-radius:8px;padding:1rem;box-shadow:0 1px 4px rgba(0,0,0,.06)}
    .info-card.full{margin-bottom:1.5rem}
    .info-card label{display:block;font-size:.75rem;color:#666;margin-bottom:.3rem;text-transform:uppercase}
    .info-card span{font-size:.95rem;font-weight:500}
    .total{color:#2d5a27;font-weight:700;font-size:1.1rem}
    .section{background:#fff;border-radius:10px;padding:1.5rem;margin-bottom:1.5rem;box-shadow:0 1px 4px rgba(0,0,0,.08)}
    .section h3{color:#2d5a27;font-size:1rem;margin-bottom:1rem;border-bottom:1px solid #eee;padding-bottom:.5rem}
    .detail-table{width:100%;border-collapse:collapse}
    .detail-table th{background:#f5f5f5;padding:.6rem;text-align:left;font-size:.85rem;border-bottom:2px solid #ddd}
    .detail-table td{padding:.6rem;border-bottom:1px solid #eee;font-size:.85rem}
    .estado-actions{display:flex;gap:.8rem;flex-wrap:wrap}
    .btn-estado{padding:.5rem 1.2rem;border:none;border-radius:4px;cursor:pointer;font-weight:600;font-size:.85rem;color:#fff}
    .btn-estado.procesado{background:#1565c0}
    .btn-estado.enviado{background:#e65100}
    .btn-estado.entregado{background:#2e7d32}
    .btn-estado.anulado{background:#c62828}
    .btn-estado:hover{opacity:.85}
    .spinner{text-align:center;padding:3rem;color:#666}
    .toast{position:fixed;bottom:2rem;right:2rem;background:#2d5a27;color:#fff;padding:.8rem 1.5rem;border-radius:6px;z-index:9999;animation:fadeIn .3s}
    .toast.error{background:#c00}
    @keyframes fadeIn{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
  `]
})
export class PedidoDetalleComponent implements OnInit {
  pedido: PedidoDetalle | null = null;
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private router: Router, private http: HttpClient) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.cargarPedido(+id);
    }
  }

  cargarPedido(id: number) {
    this.http.get<PedidoDetalle>(`${environment.apiUrl}/pedidos/${id}`).subscribe({
      next: res => { this.pedido = res; },
      error: () => { this.mostrarToast('Error al cargar el pedido', true); }
    });
  }

  puedesCambiarEstado(): boolean {
    if (!this.pedido) return false;
    return this.pedido.estado !== 'entregado' && this.pedido.estado !== 'anulado';
  }

  cambiarEstado(nuevoEstado: string) {
    if (!this.pedido) return;
    this.http.put<PedidoDetalle>(`${environment.apiUrl}/pedidos/${this.pedido.idPedido}/estado`, { estado: nuevoEstado }).subscribe({
      next: res => {
        this.pedido!.estado = res.estado;
        this.mostrarToast('Estado actualizado a: ' + res.estado);
      },
      error: (err) => { this.mostrarToast(err.error?.message || 'Error al cambiar estado', true); }
    });
  }

  volver() { this.router.navigate(['/pedidos']); }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
