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
    /* Inherits global dark theme from styles.scss */
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
