import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CrudService } from '../../../core/services/crud.service';

interface PedidoEspecial {
  idPedido: number;
  numeroPedido: string;
  estado: string;
  clienteNombre: string;
  esPedidoEspecial: boolean;
  tipoEspecial: string;
  notaEspecial: string;
  fechaLimiteEntrega: string;
}

@Component({
  selector: 'app-pedidos-especiales',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Pedidos Especiales</h2>
        <div class="filters">
          <select [(ngModel)]="filtroTipo" (change)="cargar()" class="select-filter">
            <option value="">Todos los tipos</option>
            <option value="personalizado">Personalizado</option>
            <option value="regalo">Regalo</option>
            <option value="corporativo">Corporativo</option>
          </select>
        </div>
      </div>

      <div class="banner">{{totalElements}} pedidos especiales activos</div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr>
            <th># Pedido</th><th>Cliente</th><th>Tipo</th><th>Nota especial</th>
            <th>Fecha límite</th><th>Estado</th><th>Acciones</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td><strong>{{item.numeroPedido}}</strong></td>
            <td>{{item.clienteNombre}}</td>
            <td><span class="tipo-badge" [ngStyle]="{'background': badgeColor(item.tipoEspecial)}">{{tipoLabel(item.tipoEspecial)}}</span></td>
            <td>{{truncar(item.notaEspecial)}}</td>
            <td [class.fecha-vencida]="esVencidaOHoy(item.fechaLimiteEntrega)">
              {{item.fechaLimiteEntrega ? (item.fechaLimiteEntrega | date:'dd/MM/yyyy HH:mm') : '—'}}
            </td>
            <td><span class="status-badge" [ngClass]="'status-'+item.estado">{{item.estado}}</span></td>
            <td class="actions">
              <a [routerLink]="['/pedidos', item.idPedido]" class="btn-icon" title="Ver detalle">👁️</a>
            </td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="7" class="empty">No hay pedidos especiales</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>
    </div>
  `,
  styles: [`
    .crud-container{max-width:1100px}
    .toolbar{display:flex;align-items:center;gap:1rem;flex-wrap:wrap;margin-bottom:1rem}
    .toolbar h2{color:#2d5a27;flex:1}
    .filters{display:flex;gap:.5rem;align-items:center}
    .select-filter{padding:.5rem;border:1px solid #ddd;border-radius:4px;font-size:.85rem}
    .banner{background:#e8f5e9;color:#2d5a27;padding:.7rem 1rem;border-radius:6px;font-weight:600;margin-bottom:1rem}
    .spinner{text-align:center;padding:2rem;color:#666}
    .data-table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.1)}
    .data-table th{background:#2d5a27;color:#fff;padding:.7rem;text-align:left;font-size:.85rem}
    .data-table td{padding:.6rem .7rem;border-bottom:1px solid #eee;font-size:.85rem}
    .data-table tr:hover td{background:#f0f7f0}
    .tipo-badge{color:#fff;padding:.25rem .7rem;border-radius:12px;font-size:.72rem;font-weight:700;text-transform:capitalize}
    .fecha-vencida{color:#c62828;font-weight:700}
    .status-badge{padding:.25rem .7rem;border-radius:12px;font-size:.75rem;font-weight:600;text-transform:capitalize}
    .status-pendiente{background:#fff8e1;color:#f57f17}
    .status-procesado{background:#e3f2fd;color:#1565c0}
    .status-enviado{background:#fff3e0;color:#e65100}
    .status-entregado{background:#e8f5e9;color:#2e7d32}
    .status-anulado{background:#ffebee;color:#c62828}
    .actions{display:flex;gap:.3rem}
    .btn-icon{background:none;border:none;cursor:pointer;font-size:1rem;padding:.2rem;text-decoration:none}
    .empty{text-align:center;color:#999;padding:2rem !important}
    .pagination{display:flex;align-items:center;justify-content:center;gap:1rem;margin-top:1rem}
    .pagination button{padding:.4rem .8rem;border:1px solid #ddd;border-radius:4px;background:#fff;cursor:pointer}
    .pagination button:disabled{opacity:.5;cursor:not-allowed}
  `]
})
export class PedidosEspecialesComponent implements OnInit {
  data: PedidoEspecial[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  totalElements = 0;
  filtroTipo = '';

  constructor(private crud: CrudService) {}

  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroTipo) params['tipoEspecial'] = this.filtroTipo;

    this.crud.listar<PedidoEspecial>('pedidos/especiales', params).subscribe({
      next: res => {
        this.data = res.content;
        this.totalPages = res.totalPages;
        this.totalElements = res.totalElements;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  truncar(texto: string): string {
    if (!texto) return '—';
    return texto.length > 50 ? texto.substring(0, 50) + '…' : texto;
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

  esVencidaOHoy(fecha: string): boolean {
    if (!fecha) return false;
    const limite = new Date(fecha);
    const hoy = new Date();
    limite.setHours(0, 0, 0, 0);
    hoy.setHours(0, 0, 0, 0);
    return limite.getTime() <= hoy.getTime();
  }
}
