import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';

interface Comprobante {
  idComprobante: number;
  numeroComprobante: string;
  fechaEmision: string;
  total: number;
  estado: string;
  idPedido: number;
}

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
  esPedidoEspecial: boolean;
  tipoEspecial: string;
  notaEspecial: string;
  fechaLimiteEntrega: string;
  numeroHu: string;
  transportista: string;
  regionDestino: string;
  fechaEmpaque: string;
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

      <!-- Pedido Especial -->
      <div class="section especial-section" *ngIf="pedido.esPedidoEspecial">
        <div class="especial-header">
          <span class="especial-badge" [ngStyle]="{'background': badgeColor(pedido.tipoEspecial)}">PEDIDO ESPECIAL</span>
          <span class="tipo-text">{{tipoLabel(pedido.tipoEspecial)}}</span>
        </div>

        <div class="alerta-urgente" *ngIf="esUrgente()">⚠️ Entrega urgente</div>

        <div class="info-grid">
          <div class="info-card">
            <label>Tipo</label>
            <span>{{tipoLabel(pedido.tipoEspecial)}}</span>
          </div>
          <div class="info-card" *ngIf="pedido.fechaLimiteEntrega">
            <label>Fecha límite de entrega</label>
            <span>{{pedido.fechaLimiteEntrega | date:'dd/MM/yyyy HH:mm'}}</span>
          </div>
        </div>
        <div class="info-card full" *ngIf="pedido.notaEspecial">
          <label>Nota especial</label>
          <span>{{pedido.notaEspecial}}</span>
        </div>
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

      <!-- Información de despacho -->
      <div class="section" *ngIf="pedido.numeroHu">
        <h3>Información de despacho</h3>
        <div class="info-grid">
          <div class="info-card">
            <label>HU</label>
            <span>{{pedido.numeroHu}}</span>
          </div>
          <div class="info-card">
            <label>Transportista</label>
            <span>{{pedido.transportista}}</span>
          </div>
          <div class="info-card">
            <label>Región destino</label>
            <span>{{pedido.regionDestino}}</span>
          </div>
          <div class="info-card">
            <label>Fecha empaque</label>
            <span>{{pedido.fechaEmpaque | date:'dd/MM/yyyy HH:mm'}}</span>
          </div>
        </div>
        <button class="btn-entregado" *ngIf="pedido.estado === 'enviado' && puedeDespachar" (click)="marcarEntregado()">
          Marcar como entregado
        </button>
      </div>

      <!-- Comprobante Interno -->
      <div class="section">
        <h3>Comprobante Interno</h3>
        <div *ngIf="comprobante" class="comprobante-info">
          <div class="info-grid">
            <div class="info-card">
              <label>Número de comprobante</label>
              <span>{{comprobante.numeroComprobante}}</span>
            </div>
            <div class="info-card">
              <label>Total</label>
              <span class="total">\${{comprobante.total | number:'1.2-2'}}</span>
            </div>
          </div>
          <button class="btn-pdf" (click)="descargarPDF(comprobante.idComprobante, comprobante.numeroComprobante)" [disabled]="descargando">
            {{ descargando ? 'Generando...' : 'Descargar PDF' }}
          </button>
        </div>

        <div *ngIf="!comprobante">
          <p class="sin-comprobante" *ngIf="pedido.estado !== 'procesado'">Este pedido aún no tiene comprobante.</p>
          <ng-container *ngIf="pedido.estado === 'procesado'">
            <p class="sin-comprobante">Este pedido aún no tiene comprobante generado.</p>
            <button class="btn-generar" *ngIf="isAdmin" (click)="generarComprobante()" [disabled]="generando">
              {{ generando ? 'Generando...' : 'Generar comprobante' }}
            </button>
          </ng-container>
        </div>
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

      <!-- Solicitar devolucion -->
      <div class="section" *ngIf="pedido.estado === 'entregado'">
        <button class="btn-estado procesado" (click)="irDevolucion()">Solicitar devolucion</button>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>

    <div class="spinner" *ngIf="!pedido">Cargando pedido...</div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class PedidoDetalleComponent implements OnInit {
  pedido: PedidoDetalle | null = null;
  comprobante: Comprobante | null = null;
  descargando = false;
  generando = false;
  isAdmin = false;
  puedeDespachar = false;
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private router: Router, private http: HttpClient, private authService: AuthService) {
    this.isAdmin = this.authService.hasRol('Administrador');
    this.puedeDespachar = this.authService.hasRol('Administrador') || this.authService.hasRol('Operador de Bodega');
  }

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.cargarPedido(+id);
      this.cargarComprobante(+id);
    }
  }

  cargarPedido(id: number) {
    this.http.get<PedidoDetalle>(`${environment.apiUrl}/pedidos/${id}`).subscribe({
      next: res => { this.pedido = res; },
      error: () => { this.mostrarToast('Error al cargar el pedido', true); }
    });
  }

  cargarComprobante(idPedido: number) {
    this.http.get<Comprobante>(`${environment.apiUrl}/comprobantes/pedido/${idPedido}`).subscribe({
      next: res => { this.comprobante = res; },
      error: () => { this.comprobante = null; }
    });
  }

  descargarPDF(id: number, numero: string) {
    this.descargando = true;
    this.http.get(`${environment.apiUrl}/comprobantes/${id}/pdf`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `comprobante-${numero}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.descargando = false;
      },
      error: () => { this.descargando = false; this.mostrarToast('Error al descargar el PDF', true); }
    });
  }

  generarComprobante() {
    if (!this.pedido) return;
    this.generando = true;
    this.http.post<Comprobante>(`${environment.apiUrl}/comprobantes/pedido/${this.pedido.idPedido}/generar`, {}).subscribe({
      next: res => { this.comprobante = res; this.generando = false; this.mostrarToast('Comprobante generado correctamente'); },
      error: (err) => { this.generando = false; this.mostrarToast(err.error?.message || 'Error al generar el comprobante', true); }
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

  marcarEntregado() {
    if (!this.pedido) return;
    this.http.put<PedidoDetalle>(`${environment.apiUrl}/pedidos/${this.pedido.idPedido}/estado`, { estado: 'entregado' }).subscribe({
      next: res => {
        this.pedido!.estado = res.estado;
        this.mostrarToast('Pedido marcado como entregado');
      },
      error: (err) => { this.mostrarToast(err.error?.message || 'Error al marcar como entregado', true); }
    });
  }

  irDevolucion() {
    if (!this.pedido) return;
    this.router.navigate(['/devoluciones/nueva', this.pedido.idPedido]);
  }

  badgeColor(tipo: string): string {
    switch (tipo) {
      case 'personalizado': return '#9c27b0';
      case 'regalo': return '#e91e63';
      case 'corporativo': return '#1a237e';
      default: return '#607d8b';
    }
  }

  tipoLabel(tipo: string): string {
    switch (tipo) {
      case 'personalizado': return 'Personalizado';
      case 'regalo': return 'Regalo';
      case 'corporativo': return 'Corporativo';
      default: return tipo || '';
    }
  }

  esUrgente(): boolean {
    if (!this.pedido || !this.pedido.fechaLimiteEntrega) return false;
    const limite = new Date(this.pedido.fechaLimiteEntrega).getTime();
    const ahora = Date.now();
    const diff = limite - ahora;
    return diff >= 0 && diff <= 24 * 60 * 60 * 1000;
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
