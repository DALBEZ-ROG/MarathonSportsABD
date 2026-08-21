import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CrudService, PageResponse } from '../../core/services/crud.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

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
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent],
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
              <a [routerLink]="['/pedidos', item.idPedido]" class="btn-icon" title="Ver detalle"><app-icon name="eye" [size]="16"/></a>
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
    /* Inherits global dark theme from styles.scss */
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
