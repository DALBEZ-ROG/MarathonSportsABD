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
    .crud-container{max-width:1100px}
    .toolbar{display:flex;align-items:center;gap:1rem;flex-wrap:wrap;margin-bottom:1rem}
    .toolbar h2{color:#2d5a27;flex:1}
    .filters{display:flex;gap:.5rem;align-items:center}
    .input-search{padding:.5rem;border:1px solid #ddd;border-radius:4px;font-size:.85rem;min-width:240px}
    .spinner{text-align:center;padding:2rem;color:#666}
    .data-table{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.1)}
    .data-table th{background:#2d5a27;color:#fff;padding:.7rem;text-align:left;font-size:.85rem}
    .data-table td{padding:.6rem .7rem;border-bottom:1px solid #eee;font-size:.85rem}
    .data-table tr:hover td{background:#f0f7f0}
    .total{font-weight:600;color:#2d5a27}
    .status-badge{padding:.25rem .7rem;border-radius:12px;font-size:.75rem;font-weight:600;text-transform:capitalize}
    .status-emitido{background:#e8f5e9;color:#2e7d32}
    .status-anulado{background:#ffebee;color:#c62828}
    .actions{display:flex;gap:.4rem;flex-wrap:wrap}
    .btn-pdf{background:#2d5a27;color:#fff;border:none;padding:.35rem .8rem;border-radius:4px;cursor:pointer;font-size:.8rem;font-weight:600}
    .btn-pdf:hover{background:#1e3d1a}
    .btn-pdf:disabled{opacity:.6;cursor:not-allowed}
    .btn-anular{background:#c62828;color:#fff;border:none;padding:.35rem .8rem;border-radius:4px;cursor:pointer;font-size:.8rem;font-weight:600}
    .btn-anular:hover{background:#a01b1b}
    .btn-anular:disabled{opacity:.6;cursor:not-allowed}
    .empty{text-align:center;color:#999;padding:2rem !important}
    .pagination{display:flex;align-items:center;justify-content:center;gap:1rem;margin-top:1rem}
    .pagination button{padding:.4rem .8rem;border:1px solid #ddd;border-radius:4px;background:#fff;cursor:pointer}
    .pagination button:disabled{opacity:.5;cursor:not-allowed}
    .toast{position:fixed;bottom:2rem;right:2rem;background:#2d5a27;color:#fff;padding:.8rem 1.5rem;border-radius:6px;z-index:9999;animation:fadeIn .3s}
    .toast.error{background:#c00}
    @keyframes fadeIn{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
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
