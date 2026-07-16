import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface Despacho {
  idPedido: number;
  clienteNombre: string;
  numeroHu: string;
  transportista: string;
  regionDestino: string;
  fechaEmpaque: string;
  estado: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

@Component({
  selector: 'app-despacho-lista',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="despacho-container">
      <div class="toolbar">
        <h2>Despachos</h2>
      </div>

      <div class="filters">
        <div class="filter-item">
          <label>Región destino</label>
          <input type="text" [(ngModel)]="regionDestino" placeholder="Región destino">
        </div>
        <div class="filter-item">
          <label>Desde</label>
          <input type="datetime-local" [(ngModel)]="desde">
        </div>
        <div class="filter-item">
          <label>Hasta</label>
          <input type="datetime-local" [(ngModel)]="hasta">
        </div>
        <div class="filter-actions">
          <button class="btn-filter" (click)="aplicarFiltros()">Filtrar</button>
          <button class="btn-clear" (click)="limpiar()">Limpiar</button>
        </div>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <div class="table-wrap" *ngIf="!loading">
        <table class="despacho-table">
          <thead>
            <tr>
              <th># Pedido</th><th>Cliente</th><th>HU</th><th>Transportista</th>
              <th>Región destino</th><th>Fecha empaque</th><th>Estado</th><th>Acciones</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let d of despachos">
              <td>{{d.idPedido}}</td>
              <td>{{d.clienteNombre}}</td>
              <td>{{d.numeroHu}}</td>
              <td>{{d.transportista}}</td>
              <td>{{d.regionDestino}}</td>
              <td>{{d.fechaEmpaque | date:'dd/MM/yyyy HH:mm'}}</td>
              <td><span class="estado-badge" [ngClass]="'est-'+d.estado">{{d.estado}}</span></td>
              <td><button class="btn-ver" (click)="verDetalle(d.idPedido)">Ver detalle</button></td>
            </tr>
            <tr *ngIf="despachos.length === 0">
              <td colspan="8" class="empty">No hay despachos registrados</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination" *ngIf="totalPages > 1">
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
export class DespachoListaComponent implements OnInit {
  despachos: Despacho[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  regionDestino = '';
  desde = '';
  hasta = '';

  constructor(private http: HttpClient, private router: Router) {}

  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    let params = new HttpParams().set('page', this.page).set('size', this.size);
    if (this.regionDestino.trim()) { params = params.set('regionDestino', this.regionDestino.trim()); }
    if (this.desde) { params = params.set('desde', this.desde); }
    if (this.hasta) { params = params.set('hasta', this.hasta); }
    this.http.get<PageResponse<Despacho>>(`${environment.apiUrl}/empaque/pedidos`, { params }).subscribe({
      next: res => {
        this.despachos = res.content;
        this.totalPages = res.totalPages;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  aplicarFiltros() { this.page = 0; this.cargar(); }

  limpiar() {
    this.regionDestino = ''; this.desde = ''; this.hasta = '';
    this.page = 0; this.cargar();
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  verDetalle(idPedido: number) { this.router.navigate(['/pedidos', idPedido]); }
}
