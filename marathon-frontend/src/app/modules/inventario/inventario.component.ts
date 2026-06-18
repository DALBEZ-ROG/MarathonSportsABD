import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

interface Bodega {
  idBodega: number;
  nombre: string;
}

interface Inventario {
  idInventario: number;
  productoId: number;
  productoNombre: string;
  bodegaId: number;
  bodegaNombre: string;
  cantidad: number;
  updatedAt: string;
}

interface Movimiento {
  idMovimiento: number;
  idProducto: number;
  productoNombre: string;
  idBodega: number;
  bodegaNombre: string;
  tipoMovimiento: string;
  cantidad: number;
  idUsuario: number;
  usuarioNombre: string;
  fecha: string;
}

interface Historial {
  idHistorial: number;
  cantidadAnterior: number;
  cantidadNueva: number;
  fechaCambio: string;
  tipoOperacion: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

@Component({
  selector: 'app-inventario',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="crud-container">
      <!-- Stock bajo alert -->
      <div class="alert-banner" *ngIf="stockBajo.length > 0">
        <strong>⚠️ Alerta de Stock Bajo:</strong> {{stockBajo.length}} producto(s) con stock bajo (≤ 5 unidades)
      </div>

      <div class="toolbar">
        <h2>Inventario</h2>
        <div class="filters">
          <select [(ngModel)]="filtroBodega" (change)="cargar()" class="select-filter">
            <option [ngValue]="null">Todas las bodegas</option>
            <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{b.nombre}}</option>
          </select>
        </div>
        <button class="btn-new" (click)="abrirModalMovimiento()">+ Movimiento</button>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>Producto</th><th>Bodega</th><th>Cantidad</th><th>Estado</th><th>Acciones</th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let item of data">
            <td>{{item.productoNombre}}</td>
            <td>{{item.bodegaNombre}}</td>
            <td><strong>{{item.cantidad}}</strong></td>
            <td>
              <span class="badge" [class.active]="item.cantidad > 5" [class.warning]="item.cantidad > 0 && item.cantidad <= 5" [class.danger]="item.cantidad === 0">
                {{item.cantidad === 0 ? 'Sin stock' : item.cantidad <= 5 ? 'Bajo' : 'Normal'}}
              </span>
            </td>
            <td class="actions">
              <button class="btn-icon" (click)="verHistorial(item)" title="Historial">📋</button>
            </td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="5" class="empty">No hay registros de inventario</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <!-- Modal Movimiento -->
      <div class="modal-overlay" *ngIf="showMovModal" (click)="cerrarModalMovimiento()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Registrar Movimiento</h3>
          <form (ngSubmit)="guardarMovimiento()">
            <div class="form-group">
              <label>Tipo de Movimiento *</label>
              <select [(ngModel)]="movForm.tipoMovimiento" name="tipoMovimiento" required>
                <option value="">-- Seleccione --</option>
                <option value="entrada">Entrada</option>
                <option value="salida">Salida</option>
                <option value="ajuste">Ajuste</option>
                <option value="traslado">Traslado</option>
              </select>
            </div>
            <div class="form-group">
              <label>Producto (ID) *</label>
              <input type="number" [(ngModel)]="movForm.idProducto" name="idProducto" required min="1"/>
            </div>
            <div class="form-group">
              <label>Bodega Origen *</label>
              <select [(ngModel)]="movForm.idBodega" name="idBodega" required>
                <option [ngValue]="null">-- Seleccione --</option>
                <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{b.nombre}}</option>
              </select>
            </div>
            <div class="form-group" *ngIf="movForm.tipoMovimiento === 'traslado'">
              <label>Bodega Destino *</label>
              <select [(ngModel)]="movForm.idBodegaDestino" name="idBodegaDestino">
                <option [ngValue]="null">-- Seleccione --</option>
                <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{b.nombre}}</option>
              </select>
            </div>
            <div class="form-group">
              <label>Cantidad *</label>
              <input type="number" [(ngModel)]="movForm.cantidad" name="cantidad" required min="1"/>
            </div>
            <div class="form-group">
              <label>Observación</label>
              <input type="text" [(ngModel)]="movForm.observacion" name="observacion"/>
            </div>
            <small class="error" *ngIf="movError">{{movError}}</small>
            <div class="modal-actions">
              <button type="button" class="btn-cancel" (click)="cerrarModalMovimiento()">Cancelar</button>
              <button type="submit" class="btn-save" [disabled]="saving">{{saving ? 'Registrando...' : 'Registrar'}}</button>
            </div>
          </form>
        </div>
      </div>

      <!-- Modal Historial -->
      <div class="modal-overlay" *ngIf="showHistorial" (click)="showHistorial=false">
        <div class="modal-card wide" (click)="$event.stopPropagation()">
          <h3>Historial de Inventario</h3>
          <p class="subtitle">{{historialProducto}}</p>
          <table class="data-table" *ngIf="historialData.length > 0">
            <thead>
              <tr><th>Fecha</th><th>Operación</th><th>Cant. Anterior</th><th>Cant. Nueva</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let h of historialData">
                <td>{{h.fechaCambio | date:'dd/MM/yyyy HH:mm'}}</td>
                <td><span class="badge-op">{{h.tipoOperacion}}</span></td>
                <td>{{h.cantidadAnterior}}</td>
                <td>{{h.cantidadNueva}}</td>
              </tr>
            </tbody>
          </table>
          <p *ngIf="historialData.length === 0" class="empty-text">No hay historial disponible</p>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="showHistorial=false">Cerrar</button>
          </div>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .crud-container { max-width: 1100px; }
    .alert-banner { background: #fff3cd; border: 1px solid #ffc107; border-radius: 6px; padding: 0.8rem 1rem; margin-bottom: 1rem; color: #856404; font-size: 0.9rem; }
    .toolbar { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; margin-bottom: 1rem; }
    .toolbar h2 { color: #2d5a27; flex: 1; }
    .filters { display: flex; gap: 0.5rem; }
    .select-filter { padding: 0.5rem; border: 1px solid #ddd; border-radius: 4px; font-size: 0.85rem; }
    .btn-new { background: #2d5a27; color: #fff; border: none; padding: 0.5rem 1rem; border-radius: 4px; cursor: pointer; font-weight: 600; }
    .btn-new:hover { background: #1e3d1a; }
    .spinner { text-align: center; padding: 2rem; color: #666; }
    .data-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,0.1); }
    .data-table th { background: #2d5a27; color: #fff; padding: 0.7rem; text-align: left; font-size: 0.85rem; }
    .data-table td { padding: 0.6rem 0.7rem; border-bottom: 1px solid #eee; font-size: 0.85rem; }
    .data-table tr:hover td { background: #f0f7f0; }
    .badge { padding: 0.2rem 0.6rem; border-radius: 12px; font-size: 0.75rem; background: #c8e6c9; color: #2d5a27; }
    .badge.active { background: #c8e6c9; color: #2d5a27; }
    .badge.warning { background: #fff3cd; color: #856404; }
    .badge.danger { background: #f8d7da; color: #721c24; }
    .badge-op { padding: 0.2rem 0.5rem; border-radius: 4px; font-size: 0.75rem; background: #e3f2fd; color: #1565c0; }
    .actions { display: flex; gap: 0.3rem; }
    .btn-icon { background: none; border: none; cursor: pointer; font-size: 1rem; padding: 0.2rem; }
    .empty { text-align: center; color: #999; padding: 2rem !important; }
    .empty-text { text-align: center; color: #999; padding: 1rem; }
    .subtitle { color: #666; font-size: 0.85rem; margin-bottom: 1rem; }
    .pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; margin-top: 1rem; }
    .pagination button { padding: 0.4rem 0.8rem; border: 1px solid #ddd; border-radius: 4px; background: #fff; cursor: pointer; }
    .pagination button:disabled { opacity: 0.5; cursor: not-allowed; }
    .modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
    .modal-card { background: #fff; border-radius: 10px; padding: 2rem; width: 90%; max-width: 500px; max-height: 90vh; overflow-y: auto; }
    .modal-card.wide { max-width: 650px; }
    .modal-card h3 { color: #2d5a27; margin-bottom: 1rem; }
    .form-group { margin-bottom: 1rem; display: flex; flex-direction: column; gap: 0.3rem; }
    .form-group label { font-size: 0.85rem; font-weight: 600; }
    .form-group input, .form-group select { padding: 0.6rem; border: 1px solid #ddd; border-radius: 4px; }
    .error { color: #c00; font-size: 0.8rem; }
    .modal-actions { display: flex; gap: 0.5rem; justify-content: flex-end; margin-top: 1.5rem; }
    .btn-cancel { padding: 0.5rem 1rem; border: 1px solid #ddd; border-radius: 4px; background: #fff; cursor: pointer; }
    .btn-save { padding: 0.5rem 1rem; border: none; border-radius: 4px; background: #2d5a27; color: #fff; cursor: pointer; }
    .btn-save:disabled { opacity: 0.6; }
    .toast { position: fixed; bottom: 2rem; right: 2rem; background: #2d5a27; color: #fff; padding: 0.8rem 1.5rem; border-radius: 6px; z-index: 9999; animation: fadeIn 0.3s; }
    .toast.error { background: #c00; }
    @keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
  `]
})
export class InventarioComponent implements OnInit {
  data: Inventario[] = [];
  bodegas: Bodega[] = [];
  stockBajo: Inventario[] = [];
  loading = false;
  saving = false;
  page = 0;
  size = 10;
  totalPages = 0;
  filtroBodega: number | null = null;

  showMovModal = false;
  movForm: any = { tipoMovimiento: '', idProducto: null, idBodega: null, idBodegaDestino: null, cantidad: 1, observacion: '' };
  movError = '';

  showHistorial = false;
  historialData: Historial[] = [];
  historialProducto = '';

  toast = '';
  toastError = false;

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.cargar();
    this.cargarBodegas();
    this.cargarStockBajo();
  }

  cargar() {
    this.loading = true;
    let params = new HttpParams().set('page', this.page).set('size', this.size);
    if (this.filtroBodega) params = params.set('idBodega', this.filtroBodega);

    this.http.get<PageResponse<Inventario>>(`${this.apiUrl}/inventario`, { params }).subscribe({
      next: res => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; this.mostrarToast('Error al cargar inventario', true); }
    });
  }

  cargarBodegas() {
    this.http.get<Bodega[]>(`${this.apiUrl}/bodegas/activas`).subscribe({
      next: res => { this.bodegas = res; }
    });
  }

  cargarStockBajo() {
    this.http.get<Inventario[]>(`${this.apiUrl}/inventario/stock-bajo`).subscribe({
      next: res => { this.stockBajo = res; }
    });
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  abrirModalMovimiento() {
    this.movForm = { tipoMovimiento: '', idProducto: null, idBodega: null, idBodegaDestino: null, cantidad: 1, observacion: '' };
    this.movError = '';
    this.showMovModal = true;
  }

  cerrarModalMovimiento() { this.showMovModal = false; }

  guardarMovimiento() {
    if (!this.movForm.tipoMovimiento) { this.movError = 'Seleccione un tipo de movimiento'; return; }
    if (!this.movForm.idProducto) { this.movError = 'Ingrese el ID del producto'; return; }
    if (!this.movForm.idBodega) { this.movError = 'Seleccione la bodega'; return; }
    if (!this.movForm.cantidad || this.movForm.cantidad < 1) { this.movError = 'La cantidad debe ser al menos 1'; return; }
    if (this.movForm.tipoMovimiento === 'traslado' && !this.movForm.idBodegaDestino) {
      this.movError = 'Seleccione la bodega destino para el traslado'; return;
    }

    this.saving = true;
    this.http.post(`${this.apiUrl}/inventario/movimiento`, this.movForm).subscribe({
      next: () => {
        this.saving = false;
        this.cerrarModalMovimiento();
        this.cargar();
        this.cargarStockBajo();
        this.mostrarToast('Movimiento registrado correctamente');
      },
      error: (err) => {
        this.saving = false;
        this.movError = err.error?.message || 'Error al registrar movimiento';
      }
    });
  }

  verHistorial(item: Inventario) {
    this.historialProducto = `${item.productoNombre} — ${item.bodegaNombre}`;
    this.http.get<Historial[]>(`${this.apiUrl}/inventario/${item.idInventario}/historial`).subscribe({
      next: res => { this.historialData = res; this.showHistorial = true; },
      error: () => { this.mostrarToast('Error al cargar historial', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
