import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { ModalSeguroDirective } from '../../../shared/directives/modal-seguro.directive';

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
  imports: [CommonModule, FormsModule, ModalSeguroDirective],
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
      <div class="modal-overlay" *ngIf="modalAbierto && seleccionado" appModalSeguro (cerrar)="cerrarModal()">
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

      <!-- F52 (D-42): sin esta paginacion la pantalla solo enseñaba los
           primeros pedidos y el resto era inalcanzable. -->
      <div class="pagination" *ngIf="totalPages > 1">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}} · {{totalElements}} pedidos listos para empacar</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class EmpaqueListaComponent implements OnInit {
  pedidos: PickingPedido[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  totalElements = 0;
  modalAbierto = false;
  seleccionado: PickingPedido | null = null;
  enviando = false;
  toast = '';
  toastError = false;
  form = { numeroHu: '', transportista: '', regionDestino: '', observacion: '' };

  constructor(private http: HttpClient) {}

  ngOnInit() { this.cargar(); }

  /**
   * Cola de empaque (F52, D-42).
   *
   * Antes esto pedía los 100 primeros pedidos *procesados* a
   * `/api/picking/pedidos` y filtraba aquí los que tenían el picking completo.
   * Con 19.059 pedidos en «procesado» ordenados del más antiguo, un pedido
   * recién recogido quedaba el último de la cola y **no aparecía nunca**: quien
   * lo recogía no podía empacarlo.
   *
   * Ahora el filtro lo hace la base (`/api/empaque/pedidos/listos`), la lista
   * llega del más reciente al más antiguo —que es lo que busca quien acaba de
   * recoger— y hay paginación de verdad.
   */
  cargar() {
    this.loading = true;
    const params = new HttpParams().set('page', this.page).set('size', this.size);
    this.http.get<PageResponse<PickingPedido>>(`${environment.apiUrl}/empaque/pedidos/listos`, { params }).subscribe({
      next: res => {
        this.pedidos = res.content;
        this.totalPages = res.totalPages;
        this.totalElements = res.totalElements;
        this.loading = false;
      },
      error: () => { this.loading = false; }
    });
  }

  cambiarPagina(p: number) {
    if (p < 0 || p >= this.totalPages) { return; }
    this.page = p;
    this.cargar();
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
