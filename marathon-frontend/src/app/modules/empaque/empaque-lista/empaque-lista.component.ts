import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface PickingPedido {
  idPedido: number;
  clienteNombre: string;
  clienteApellido: string;
  fechaPedido: string;
  estado: string;
  totalLineas: number;
  lineasCompletadas: number;
  estadoPicking: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
}

@Component({
  selector: 'app-empaque-lista',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="empaque-container">
      <div class="toolbar">
        <h2>Empaque de Pedidos</h2>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <div class="cards" *ngIf="!loading">
        <div class="card" *ngFor="let p of pedidos">
          <div class="card-header">
            <span class="pedido-num"># Pedido {{p.idPedido}}</span>
            <span class="estado-badge">PICKING COMPLETO</span>
          </div>
          <div class="card-body">
            <p class="cliente">{{p.clienteNombre}} {{p.clienteApellido}}</p>
            <p class="lineas">{{p.lineasCompletadas}} / {{p.totalLineas}} líneas recogidas</p>
          </div>
          <div class="card-footer">
            <button class="btn-empacar" (click)="abrirModal(p)">Confirmar empaque</button>
          </div>
        </div>
        <div class="empty" *ngIf="pedidos.length === 0">No hay pedidos con picking completo</div>
      </div>

      <!-- Modal -->
      <div class="modal-overlay" *ngIf="modalAbierto && seleccionado" (click)="cerrarModal()">
        <div class="modal" (click)="$event.stopPropagation()">
          <h3>Confirmar empaque — Pedido #{{seleccionado.idPedido}}</h3>

          <div class="form-group">
            <label>Número HU</label>
            <div class="hu-row">
              <input type="text" [(ngModel)]="form.numeroHu" placeholder="HU-AAAAMMDD-001" maxlength="50">
              <button type="button" class="btn-gen" (click)="generarHu()">Generar HU automático</button>
            </div>
          </div>

          <div class="form-group">
            <label>Transportista</label>
            <input type="text" [(ngModel)]="form.transportista" placeholder="Transportista" maxlength="100">
          </div>

          <div class="form-group">
            <label>Región destino</label>
            <input type="text" [(ngModel)]="form.regionDestino" placeholder="Región destino" maxlength="100">
          </div>

          <div class="form-group">
            <label>Observación</label>
            <textarea [(ngModel)]="form.observacion" rows="2" placeholder="Observación (opcional)"></textarea>
          </div>

          <div class="resumen">
            <h4>Resumen del pedido</h4>
            <p><strong>Cliente:</strong> {{seleccionado.clienteNombre}} {{seleccionado.clienteApellido}}</p>
            <p><strong>Líneas:</strong> {{seleccionado.totalLineas}}</p>
          </div>

          <div class="modal-actions">
            <button class="btn-cancel" (click)="cerrarModal()">Cancelar</button>
            <button class="btn-confirm" (click)="confirmar()" [disabled]="enviando || !formValido()">
              {{ enviando ? 'Procesando...' : 'Confirmar empaque' }}
            </button>
          </div>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .empaque-container{max-width:1100px;margin:0 auto}
    .toolbar{margin-bottom:1.5rem}
    .toolbar h2{color:#2d5a27;margin:0}
    .spinner{text-align:center;padding:2rem;color:#666}
    .cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:1rem}
    .card{background:#fff;border-radius:10px;box-shadow:0 1px 6px rgba(0,0,0,.08);display:flex;flex-direction:column;border-left:4px solid #2d5a27}
    .card-header{display:flex;justify-content:space-between;align-items:center;padding:1rem 1rem .5rem}
    .pedido-num{font-weight:700;color:#2d5a27}
    .estado-badge{padding:.25rem .7rem;border-radius:12px;font-size:.7rem;font-weight:700;background:#e8f5e9;color:#2e7d32}
    .card-body{padding:0 1rem 1rem}
    .cliente{font-weight:600;margin:.3rem 0}
    .lineas{color:#666;font-size:.8rem;margin:0}
    .card-footer{padding:.8rem 1rem;border-top:1px solid #eee}
    .btn-empacar{width:100%;background:#2d5a27;color:#fff;border:none;padding:.6rem;border-radius:6px;cursor:pointer;font-weight:600;font-size:.85rem}
    .btn-empacar:hover{background:#1e3d1a}
    .empty{grid-column:1/-1;text-align:center;color:#999;padding:2rem}
    .modal-overlay{position:fixed;inset:0;background:rgba(0,0,0,.45);display:flex;align-items:center;justify-content:center;z-index:1000;padding:1rem}
    .modal{background:#fff;border-radius:10px;padding:1.5rem;width:100%;max-width:480px;max-height:90vh;overflow:auto}
    .modal h3{color:#2d5a27;margin:0 0 1rem}
    .form-group{margin-bottom:1rem}
    .form-group label{display:block;font-size:.8rem;color:#555;margin-bottom:.3rem;font-weight:600}
    .form-group input,.form-group textarea{width:100%;padding:.5rem;border:1px solid #ddd;border-radius:4px;font-size:.85rem;box-sizing:border-box}
    .hu-row{display:flex;gap:.5rem}
    .hu-row input{flex:1}
    .btn-gen{background:#f5f5f5;border:1px solid #ddd;border-radius:4px;padding:.4rem .7rem;cursor:pointer;font-size:.75rem;white-space:nowrap}
    .btn-gen:hover{background:#eee}
    .resumen{background:#f5f5f5;border-radius:6px;padding:.8rem;margin-bottom:1rem}
    .resumen h4{margin:0 0 .5rem;color:#2d5a27;font-size:.85rem}
    .resumen p{margin:.2rem 0;font-size:.85rem;color:#444}
    .modal-actions{display:flex;justify-content:flex-end;gap:.8rem}
    .btn-cancel{background:#fff;border:1px solid #ddd;padding:.5rem 1.2rem;border-radius:4px;cursor:pointer}
    .btn-confirm{background:#2d5a27;color:#fff;border:none;padding:.5rem 1.2rem;border-radius:4px;cursor:pointer;font-weight:600}
    .btn-confirm:disabled{opacity:.6;cursor:not-allowed}
    .toast{position:fixed;bottom:2rem;right:2rem;background:#2d5a27;color:#fff;padding:.8rem 1.5rem;border-radius:6px;z-index:9999}
    .toast.error{background:#c00}
  `]
})
export class EmpaqueListaComponent implements OnInit {
  pedidos: PickingPedido[] = [];
  loading = false;
  modalAbierto = false;
  seleccionado: PickingPedido | null = null;
  enviando = false;
  toast = '';
  toastError = false;
  form = { numeroHu: '', transportista: '', regionDestino: '', observacion: '' };

  constructor(private http: HttpClient) {}

  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    const params = new HttpParams().set('page', 0).set('size', 100);
    this.http.get<PageResponse<PickingPedido>>(`${environment.apiUrl}/picking/pedidos`, { params }).subscribe({
      next: res => {
        this.pedidos = res.content.filter(p => p.estadoPicking === 'completo');
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  abrirModal(p: PickingPedido) {
    this.seleccionado = p;
    this.form = { numeroHu: '', transportista: '', regionDestino: '', observacion: '' };
    this.modalAbierto = true;
  }

  cerrarModal() {
    this.modalAbierto = false;
    this.seleccionado = null;
  }

  generarHu() {
    const d = new Date();
    const fecha = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
    const sec = String(Math.floor(Math.random() * 999) + 1).padStart(3, '0');
    this.form.numeroHu = `HU-${fecha}-${sec}`;
  }

  formValido(): boolean {
    return !!this.form.numeroHu.trim() && !!this.form.transportista.trim() && !!this.form.regionDestino.trim();
  }

  confirmar() {
    if (!this.seleccionado || !this.formValido()) return;
    this.enviando = true;
    const id = this.seleccionado.idPedido;
    this.http.post(`${environment.apiUrl}/empaque/pedidos/${id}/confirmar`, this.form).subscribe({
      next: () => {
        this.enviando = false;
        this.mostrarToast(`Pedido #${id} empacado y enviado correctamente. HU: ${this.form.numeroHu}`);
        this.cerrarModal();
        this.cargar();
      },
      error: (err) => {
        this.enviando = false;
        this.mostrarToast(err.error?.message || 'Error al confirmar el empaque', true);
      }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
