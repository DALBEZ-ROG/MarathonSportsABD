import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';

interface Comprobante {
  idComprobante: number;
  numeroComprobante: string;
  fechaEmision: string;
  total: number;
  estado: string;
  idPedido: number;
  clienteNombre: string;
  clienteApellido: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

@Component({
  selector: 'app-comprobantes-lista',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Comprobantes Internos</h2>
        <div class="filters">
          <input type="text" [(ngModel)]="filtroNumero" (ngModelChange)="onBuscar()" class="input-search" placeholder="Buscar por número..."/>
        </div>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>Número</th><th>Pedido #</th><th>Cliente</th><th>Fecha emisión</th><th>Total</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td><strong>{{item.numeroComprobante}}</strong></td>
            <td>{{item.idPedido}}</td>
            <td>{{item.clienteNombre}} {{item.clienteApellido}}</td>
            <td>{{item.fechaEmision | date:'dd/MM/yyyy HH:mm'}}</td>
            <td class="total">\${{item.total | number:'1.2-2'}}</td>
            <td><span class="status-badge" [ngClass]="'status-'+item.estado">{{item.estado}}</span></td>
            <td class="actions">
              <button class="btn-pdf" (click)="descargarPDF(item.idComprobante, item.numeroComprobante)" [disabled]="descargandoId===item.idComprobante">
                {{ descargandoId===item.idComprobante ? 'Generando...' : 'Descargar PDF' }}
              </button>
              <button class="btn-anular" *ngIf="isAdmin && item.estado==='emitido'" (click)="anular(item.idComprobante)" [disabled]="anulandoId===item.idComprobante">
                {{ anulandoId===item.idComprobante ? 'Anulando...' : 'Anular' }}
              </button>
            </td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="7" class="empty">No hay comprobantes registrados</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class ComprobantesListaComponent implements OnInit, OnDestroy {
  data: Comprobante[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroNumero = '';
  descargandoId: number | null = null;
  anulandoId: number | null = null;
  isAdmin = false;
  toast = '';
  toastError = false;

  private buscar$ = new Subject<void>();

  constructor(private http: HttpClient, private authService: AuthService) {
    this.isAdmin = this.authService.hasRol('Administrador');
  }

  ngOnInit() {
    this.buscar$.pipe(debounceTime(300)).subscribe(() => { this.page = 0; this.cargar(); });
    this.cargar();
  }

  ngOnDestroy() { this.buscar$.complete(); }

  onBuscar() { this.buscar$.next(); }

  cargar() {
    this.loading = true;
    let params = new HttpParams().set('page', this.page).set('size', this.size);
    if (this.filtroNumero) params = params.set('numero', this.filtroNumero);

    this.http.get<PageResponse<Comprobante>>(`${environment.apiUrl}/comprobantes`, { params }).subscribe({
      next: res => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; this.mostrarToast('Error al cargar los comprobantes', true); }
    });
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  descargarPDF(id: number, numero: string) {
    this.descargandoId = id;
    this.http.get(`${environment.apiUrl}/comprobantes/${id}/pdf`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `comprobante-${numero}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.descargandoId = null;
      },
      error: () => { this.descargandoId = null; this.mostrarToast('Error al descargar el PDF', true); }
    });
  }

  anular(id: number) {
    this.anulandoId = id;
    this.http.post(`${environment.apiUrl}/comprobantes/${id}/anular`, {}).subscribe({
      next: () => { this.anulandoId = null; this.mostrarToast('Comprobante anulado correctamente'); this.cargar(); },
      error: (err) => { this.anulandoId = null; this.mostrarToast(err.error?.message || 'Error al anular el comprobante', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
