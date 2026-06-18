import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CrudService, PageResponse } from '../../core/services/crud.service';

interface Pedido {
  idPedido: number;
  numeroPedido: string;
  fechaPedido: string;
  total: number;
  estado: string;
  observaciones: string;
  clienteNombre: string;
  usuarioNombre: string;
}

@Component({
  selector: 'app-pedidos',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Pedidos</h2>
        <div class="filters">
          <select [(ngModel)]="filtroEstado" (change)="cargar()" class="select-filter">
            <option value="">Todos los estados</option>
            <option value="pendiente">Pendiente</option>
            <option value="procesado">Procesado</option>
            <option value="enviado">Enviado</option>
            <option value="entregado">Entregado</option>
            <option value="anulado">Anulado</option>
          </select>
          <input type="date" [(ngModel)]="fechaDesde" (change)="cargar()" class="input-date" placeholder="Desde"/>
          <input type="date" [(ngModel)]="fechaHasta" (change)="cargar()" class="input-date" placeholder="Hasta"/>
        </div>
        <a routerLink="/pedidos/nuevo" class="btn-new">+ Nuevo Pedido</a>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>N° Pedido</th><th>Fecha</th><th>Cliente</th><th>Total</th><th>Estado</th><th>Registrado por</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td><strong>{{item.numeroPedido}}</strong></td>
            <td>{{item.fechaPedido | date:'dd/MM/yyyy HH:mm'}}</td>
            <td>{{item.clienteNombre}}</td>
            <td class="total">\${{item.total | number:'1.2-2'}}</td>
            <td><span class="status-badge" [ngClass]="'status-'+item.estado">{{item.estado}}</span></td>
            <td>{{item.usuarioNombre}}</td>
            <td class="actions">
              <a [routerLink]="['/pedidos', item.idPedido]" class="btn-icon" title="Ver detalle">👁️</a>
            </td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="7" class="empty">No hay pedidos registrados</td></tr>
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
    .select-filter,.input-date{padding:.5rem;border:1px solid #ddd;border-radius:4px;font-size:.85rem}
    .btn-new{background:#2d5a27;color:#fff;border:none;padding:.5rem 1rem;border-radius:4px;cursor:pointer;font-weight:600;text-decoration:none}
    .btn-new:hover{background:#1e3d1a}
    .spinner{text-align:center;padding:2rem;color:#666}
    .data-table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.1)}
    .data-table th{background:#2d5a27;color:#fff;padding:.7rem;text-align:left;font-size:.85rem}
    .data-table td{padding:.6rem .7rem;border-bottom:1px solid #eee;font-size:.85rem}
    .data-table tr:hover td{background:#f0f7f0}
    .total{font-weight:600;color:#2d5a27}
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
export class PedidosComponent implements OnInit {
  data: Pedido[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroEstado = '';
  fechaDesde = '';
  fechaHasta = '';

  constructor(private crud: CrudService) {}

  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    const params: Record<string, string | number> = { page: this.page, size: this.size };
    if (this.filtroEstado) params['estado'] = this.filtroEstado;
    if (this.fechaDesde) params['fechaDesde'] = this.fechaDesde;
    if (this.fechaHasta) params['fechaHasta'] = this.fechaHasta;

    this.crud.listar<Pedido>('pedidos', params).subscribe({
      next: res => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }
}
