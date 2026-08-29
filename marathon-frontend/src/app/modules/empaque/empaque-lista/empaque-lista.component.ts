import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { ModalSeguroDirective } from '../../../shared/directives/modal-seguro.directive';
import { SearchableSelectComponent } from '../../../shared/components/searchable-select/searchable-select.component';

interface PickingPedido {
  idPedido: number;
  numeroPedido: string;
  clienteNombre: string;
  clienteApellido: string;
  fechaPedido: string;
  estado: string;
  totalLineas: number;
  lineasCompletadas: number;
  estadoPicking: string;
  /** F77: a donde va, sacado de la ciudad del cliente. */
  ciudadDestino?: string;
  regionDestino?: string;
}

interface Transportista {
  idTransportista: number;
  nombre: string;
  cobertura: string;
  etiqueta?: string;
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
  imports: [CommonModule, FormsModule, ModalSeguroDirective, SearchableSelectComponent],
  template: `
    <div class="empaque-container">
      <div class="toolbar">
        <h2>Empaque de Pedidos</h2>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <!-- F55: lista, no tarjetas — igual que picking, y por el mismo motivo:
           en tabla entran diez pedidos por pantalla en vez de tres. El más
           reciente primero, que es el que acabas de recoger (F52, D-42). -->
      <table class="data-table" *ngIf="!loading && pedidos.length > 0">
        <thead>
          <tr><th>Pedido</th><th>Cliente</th><th>Destino</th><th>Fecha</th><th>Líneas</th><th></th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of pedidos">
            <td class="nowrap"><strong>{{p.numeroPedido || ('# ' + p.idPedido)}}</strong></td>
            <td>{{p.clienteNombre}} {{p.clienteApellido}}</td>
            <td class="nowrap">
              <span *ngIf="p.ciudadDestino">{{p.ciudadDestino}}</span>
              <span class="reg" *ngIf="p.regionDestino">{{p.regionDestino}}</span>
              <span class="sin" *ngIf="!p.ciudadDestino">sin ciudad</span>
            </td>
            <td class="nowrap">{{p.fechaPedido | date:'dd/MM/yyyy HH:mm'}}</td>
            <td class="nowrap">{{p.lineasCompletadas}} / {{p.totalLineas}} recogidas</td>
            <td class="actions">
              <button class="btn-empacar" (click)="abrirModal(p)">Confirmar empaque</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="empty" *ngIf="!loading && pedidos.length === 0">No hay pedidos con picking completo</div>

      <!-- Modal -->
      <div class="modal-overlay" *ngIf="modalAbierto && seleccionado" appModalSeguro (cerrar)="cerrarModal()">
        <div class="modal emp" (click)="$event.stopPropagation()">

          <header class="emp-cab">
            <div>
              <h3>Empacar {{seleccionado.numeroPedido || ('pedido #' + seleccionado.idPedido)}}</h3>
              <p class="emp-sub">
                {{seleccionado.clienteNombre}} {{seleccionado.clienteApellido}}
                <span class="sep">·</span> {{seleccionado.totalLineas}}
                {{seleccionado.totalLineas === 1 ? 'línea' : 'líneas'}} recogidas
              </p>
            </div>
          </header>

          <!-- Lo que de verdad pasa al pulsar el botón. Antes no lo decía nada,
               y no es poco: descuenta el stock y da el pedido por enviado. -->
          <p class="emp-aviso">
            Al confirmar se <strong>descuenta el stock</strong> de las bodegas de
            donde se recogió, se sueltan las reservas y el pedido pasa a
            <strong>enviado</strong>. No se deshace desde aquí.
          </p>

          <div class="emp-campo">
            <label>Número HU</label>
            <p class="emp-ayuda">
              <strong>HU es «Handling Unit»: la etiqueta del bulto</strong> que sale
              del almacén — la caja, no el pedido. Es lo que se pega encima y por lo
              que se pregunta si algo se pierde. Va propuesto con el número del
              pedido y la fecha; cámbialo solo si el bulto ya trae otra etiqueta.
            </p>
            <div class="hu-fila">
              <input type="text" [(ngModel)]="form.numeroHu" maxlength="50" class="hu-caja"/>
              <button type="button" class="hu-otra" (click)="generarHu()">Proponer otro</button>
            </div>
          </div>

          <div class="emp-campo">
            <label>Transportista</label>
            <p class="emp-ayuda">Quién se lleva el bulto. Sale del catálogo, no se escribe a mano.</p>
            <app-searchable-select
              [items]="transportistas"
              labelKey="etiqueta"
              valueKey="nombre"
              placeholder="Escribe el nombre del transportista…"
              [(ngModel)]="form.transportista"
              [ngModelOptions]="{ standalone: true }"/>
          </div>

          <div class="emp-campo">
            <label>Región de destino</label>
            <p class="emp-ayuda">
              La zona del país a la que va, y <strong>sale sola de la ciudad del
              cliente</strong>: ya no se teclea. Cámbiala solo si el bulto va a
              otro sitio distinto de la dirección del cliente.
            </p>
            <div class="destino" *ngIf="seleccionado.ciudadDestino">
              Va a <strong>{{seleccionado.ciudadDestino}}</strong>, que está en la
              <strong>{{seleccionado.regionDestino || 'región sin clasificar'}}</strong>.
            </div>
            <div class="regiones">
              <button type="button" class="reg-btn" *ngFor="let r of regiones"
                      [class.on]="form.regionDestino === r"
                      (click)="form.regionDestino = r">{{r}}</button>
            </div>
          </div>

          <div class="emp-campo">
            <label>Observación <span class="opc">opcional</span></label>
            <textarea [(ngModel)]="form.observacion" rows="2"
                      placeholder="Algo que deba saber quien reciba el bulto…"></textarea>
          </div>

          <div class="modal-actions">
            <button class="btn-cancel" (click)="cerrarModal()">Cancelar</button>
            <button class="btn-confirm" (click)="confirmar()" [disabled]="enviando || !formValido()">
              {{ enviando ? 'Procesando…' : 'Confirmar empaque y enviar' }}
            </button>
          </div>
          <p class="falta-algo" *ngIf="!formValido()">{{ queFalta() }}</p>
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
    /* ── Destino en el listado (F77) ───────────────────────────── */
    .reg { display: inline-block; margin-left: .4rem; font-size: .68rem;
           text-transform: uppercase; letter-spacing: .04em;
           color: var(--ms-text-muted); background: rgba(255,255,255,0.06);
           padding: .1rem .35rem; border-radius: 4px; }
    .sin { color: rgba(255,255,255,0.3); font-style: italic; font-size: .84rem; }

    /* ── El modal ──────────────────────────────────────────────── */
    .emp { max-width: 620px; width: 100%; }
    .emp-cab { margin-bottom: 1rem; }
    .emp-cab h3 { margin: 0 0 .25rem; }
    .emp-sub { margin: 0; color: var(--ms-text-muted); font-size: .88rem; }
    .sep { margin: 0 .35rem; }

    .emp-aviso { margin: 0 0 1.4rem; padding: .8rem 1rem; font-size: .82rem;
                 line-height: 1.6; color: var(--ms-text-muted);
                 background: var(--ms-gold-dim);
                 border: 1px solid rgba(201,168,76,.3);
                 border-radius: var(--ms-radius-sm); }
    .emp-aviso strong { color: var(--ms-gold-light); }

    .emp-campo { margin-bottom: 1.4rem; }
    .emp-campo > label { display: block; font-size: .8rem; font-weight: 600;
                         color: var(--ms-text); margin-bottom: .3rem; }
    .opc { font-weight: 400; font-size: .72rem; color: var(--ms-text-muted); }
    .emp-ayuda { margin: 0 0 .6rem; font-size: .78rem; line-height: 1.6;
                 color: var(--ms-text-muted); }
    .emp-ayuda strong { color: rgba(255,255,255,0.85); }

    .hu-fila { display: flex; gap: .5rem; align-items: stretch; }
    .hu-caja { flex: 1; min-width: 0; font-family: var(--ms-font-mono, monospace);
               letter-spacing: .02em; }
    .hu-otra { flex-shrink: 0; background: transparent;
               border: 1px solid var(--ms-border); color: var(--ms-text-muted);
               padding: .5rem .9rem; border-radius: var(--ms-radius-sm);
               font-size: .8rem; cursor: pointer; font-family: inherit; }
    .hu-otra:hover { border-color: var(--ms-gold); color: var(--ms-gold); }

    /* La caja de observación se quedaba del tamaño por defecto del navegador
       —dos renglones estrechos— porque aquí no hay .form-group que la vista. */
    .emp-campo textarea { width: 100%; box-sizing: border-box; padding: .7rem 1rem;
                          font-family: inherit; font-size: .9rem; resize: vertical;
                          background: var(--ms-bg-input, #1e2430); color: var(--ms-text);
                          border: 1px solid var(--ms-border);
                          border-radius: var(--ms-radius-sm); }
    .emp-campo textarea::placeholder { color: var(--ms-text-muted); }

    .destino { font-size: .84rem; color: var(--ms-text-muted); line-height: 1.6;
               margin-bottom: .6rem; }
    .destino strong { color: var(--ms-text); }

    .regiones { display: flex; gap: .4rem; flex-wrap: wrap; }
    .reg-btn { flex: 1; min-width: 92px; background: rgba(255,255,255,0.03);
               border: 1px solid var(--ms-border); color: var(--ms-text-muted);
               padding: .55rem .5rem; border-radius: var(--ms-radius-sm);
               font-size: .84rem; cursor: pointer; font-family: inherit;
               transition: all .15s ease; }
    .reg-btn:hover { border-color: rgba(255,255,255,0.25); color: var(--ms-text); }
    .reg-btn.on { background: var(--ms-gold-dim); border-color: var(--ms-gold);
                  color: var(--ms-gold-light); font-weight: 600; }

    .falta-algo { margin: .7rem 0 0; font-size: .78rem; color: #fbbf24;
                  text-align: right; }

    /* La lista del buscador no puede quedar por detrás del campo siguiente. */
    .emp-campo:has(.search-select.open) { position: relative; z-index: 60; }
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

  /** El catálogo de la F77. Antes el transportista se escribía a mano. */
  transportistas: Transportista[] = [];

  /** Las cuatro regiones naturales del Ecuador; el CHECK de ciudad.region no admite otras. */
  readonly regiones = ['Costa', 'Sierra', 'Oriente', 'Insular'];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.cargar();
    this.cargarTransportistas();
  }

  cargarTransportistas() {
    this.http.get<Transportista[]>(`${environment.apiUrl}/transportistas/activos`).subscribe({
      next: res => {
        // La etiqueta lleva la cobertura porque es lo que decide la elección:
        // saber el nombre no dice si llega al Oriente.
        this.transportistas = (res ?? []).map(t => ({
          ...t,
          etiqueta: t.cobertura ? `${t.nombre} · ${t.cobertura}` : t.nombre
        }));
      },
      error: () => { this.transportistas = []; }
    });
  }

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
    // La HU y la región vienen puestas: las dos se pueden deducir, y pedirlas en
    // blanco solo conseguía que se tecleara cualquier cosa.
    this.generarHu();
    this.form.regionDestino = p.regionDestino || '';
    this.modalAbierto = true;
  }

  cerrarModal() {
    this.modalAbierto = false;
    this.seleccionado = null;
  }

  /**
   * Propone la etiqueta del bulto.
   *
   * <p>Antes era `HU-<fecha>-<3 dígitos al azar>`, y el azar de tres cifras
   * choca consigo mismo con muy pocos bultos el mismo día — sin decir nada,
   * porque la columna no es única. Ahora lleva <b>el número del pedido</b>, que
   * es lo que hace falta cuando alguien llama preguntando por una caja, y el
   * sufijo solo distingue un segundo bulto del mismo pedido.
   */
  generarHu() {
    if (!this.seleccionado) { return; }
    const d = new Date();
    const fecha = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`;
    const num = String(this.seleccionado.idPedido).padStart(6, '0');
    const previo = this.form.numeroHu;
    let sufijo = 1;
    // «Proponer otro» tiene que dar otro distinto, no el mismo.
    do {
      this.form.numeroHu = `HU-${num}-${fecha}` + (sufijo > 1 ? `-${sufijo}` : '');
      sufijo++;
    } while (this.form.numeroHu === previo && sufijo < 100);
  }

  /**
   * Ojo: los campos pueden ser NULOS, no solo cadenas vacias.
   *
   * <p>El buscador avisa con `null` a cada letra —lo escrito a medias todavia
   * no es una eleccion—, asi que `form.transportista` vale null mientras se
   * teclea. Con `.trim()` directo, esta funcion reventaba DENTRO de la
   * plantilla, y una excepcion ahi **aborta la deteccion de cambios entera**:
   * la lista del buscador se quedaba congelada enseñando los siete
   * transportistas por mucho que se filtrara. El sintoma no se parecia en nada
   * a la causa.
   */
  private lleno(v: string | null | undefined): boolean {
    return !!(v || '').trim();
  }

  formValido(): boolean {
    return this.lleno(this.form.numeroHu)
        && this.lleno(this.form.transportista)
        && this.lleno(this.form.regionDestino);
  }

  /** Decir qué falta, en vez de dejar el botón apagado sin explicación. */
  queFalta(): string {
    const faltan: string[] = [];
    if (!this.lleno(this.form.numeroHu)) { faltan.push('el número HU'); }
    if (!this.lleno(this.form.transportista)) { faltan.push('el transportista'); }
    if (!this.lleno(this.form.regionDestino)) { faltan.push('la región de destino'); }
    if (!faltan.length) { return ''; }
    return 'Falta ' + (faltan.length === 1 ? faltan[0]
        : faltan.slice(0, -1).join(', ') + ' y ' + faltan[faltan.length - 1]) + '.';
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
