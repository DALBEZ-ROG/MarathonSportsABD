import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

interface Solicitud {
  idSolicitud: number;
  idPedido: number;
  clienteNombre: string;
  motivo: string;
  estado: string;
  fechaSolicitud: string;
}

@Component({
  selector: 'app-devoluciones-lista',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Devoluciones</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="busqueda" (input)="onBuscar()"
                 placeholder="Buscar por N.° de solicitud, pedido o cliente..." class="input-search"/>
          <select [(ngModel)]="filtroEstado" (change)="onFiltro()" class="select-filter">
            <option value="">Todos los estados</option>
            <option value="solicitada">Solicitada</option>
            <option value="en_inspeccion">En inspeccion</option>
            <option value="completada">Completada</option>
            <option value="rechazada">Rechazada</option>
          </select>
        </div>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>#</th><th>Pedido</th><th>Cliente</th><th>Motivo</th><th>Estado</th><th>Fecha</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let s of data">
            <td>{{s.idSolicitud}}</td>
            <td>#{{s.idPedido}}</td>
            <td>{{s.clienteNombre}}</td>
            <td>{{motivoLabel(s.motivo)}}</td>
            <td><span class="dev-badge" [ngClass]="'dev-' + s.estado">{{estadoLabel(s.estado)}}</span></td>
            <td>{{s.fechaSolicitud | date:'dd/MM/yyyy HH:mm'}}</td>
            <td><button class="btn-icon" [routerLink]="['/devoluciones', s.idSolicitud]" title="Ver detalle"><app-icon name="search" [size]="16"/></button></td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="7" class="empty">No hay solicitudes de devolucion</td></tr>
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
    .dev-badge { padding: .25rem .6rem; border-radius: 12px; font-size: .72rem; font-weight: 600; text-transform: uppercase; letter-spacing: .5px; color: #fff; }
    .dev-solicitada { background: #d97706; }
    .dev-en_inspeccion { background: #2563eb; }
    .dev-completada { background: #16a34a; }
    .dev-rechazada { background: #dc2626; }
  `]
})
export class DevolucionesListaComponent implements OnInit {
  data: Solicitud[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroEstado = '';

  constructor(private api: ApiService) {}

  ngOnInit() { this.cargar(); }

  motivoLabel(m: string): string {
    const map: Record<string, string> = {
      producto_defectuoso: 'Defectuoso', talla_incorrecta: 'Talla incorrecta',
      no_esperado: 'No esperado', cambio_opinion: 'Cambio opinion',
      producto_incompleto: 'Incompleto', otro: 'Otro'
    };
    return map[m] || m;
  }

  estadoLabel(e: string): string {
    const map: Record<string, string> = {
      solicitada: 'Solicitada', en_inspeccion: 'En inspeccion',
      completada: 'Completada', rechazada: 'Rechazada'
    };
    return map[e] || e;
  }

  /** F54: buscador por texto. Se espera 300 ms para no lanzar una consulta por tecla. */
  busqueda = '';
  private buscarTimeout: any;

  onBuscar() {
    clearTimeout(this.buscarTimeout);
    this.buscarTimeout = setTimeout(() => { this.page = 0; this.cargar(); }, 300);
  }
  cargar() {
    this.loading = true;
    let url = 'devoluciones?page=' + this.page + '&size=' + this.size;
    if (this.filtroEstado) url += '&estado=' + this.filtroEstado;
    if (this.busqueda) url += '&busqueda=' + encodeURIComponent(this.busqueda);
    this.api.get<any>(url).subscribe({
      next: (res: any) => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  onFiltro() { this.page = 0; this.cargar(); }
  cambiarPagina(p: number) { this.page = p; this.cargar(); }
}
