import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

@Component({
  selector: 'app-devoluciones-proveedor-lista',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Devoluciones a Proveedor</h2>
        <div class="filters">
          <select [(ngModel)]="filtroEstado" (change)="onFiltro()" class="select-filter">
            <option value="">Todos</option>
            <option value="pendiente">Pendiente</option>
            <option value="enviada">Enviada</option>
            <option value="resuelta">Resuelta</option>
            <option value="rechazada">Rechazada</option>
          </select>
        </div>
        <button class="btn-new" routerLink="/devoluciones-proveedor/pendientes">Bandeja de pendientes</button>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead><tr><th>#</th><th>Proveedor</th><th>Fecha</th><th>Estado</th><th>Resolucion</th><th>Acciones</th></tr></thead>
        <tbody>
          <tr *ngFor="let d of data">
            <td>{{d.idDevolucionProv}}</td>
            <td>{{d.proveedorNombre}}</td>
            <td>{{d.fechaDevolucion | date:'dd/MM/yyyy'}}</td>
            <td><span class="dp-badge" [ngClass]="'dp-' + d.estado">{{d.estado}}</span></td>
            <td>{{d.tipoResolucion || '-'}}</td>
            <td><button class="btn-icon" [routerLink]="['/devoluciones-proveedor', d.idDevolucionProv]"><app-icon name="search" [size]="16"/></button></td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="6" class="empty">No hay devoluciones</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">Anterior</button>
        <span>Pagina {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente</button>
      </div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class DevolucionesProveedorListaComponent implements OnInit {
  data: any[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroEstado = '';

  constructor(private api: ApiService) {}
  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    let url = 'devoluciones-proveedor?page=' + this.page + '&size=' + this.size;
    if (this.filtroEstado) url += '&estado=' + this.filtroEstado;
    this.api.get<any>(url).subscribe({
      next: (res: any) => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  onFiltro() { this.page = 0; this.cargar(); }
  cambiarPagina(p: number) { this.page = p; this.cargar(); }
}
