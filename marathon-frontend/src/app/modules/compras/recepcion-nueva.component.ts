import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CrudService } from '../../core/services/crud.service';
import { ApiService } from '../../core/services/api.service';

interface Bodega { idBodega: number; nombre: string; }

interface DetalleOc {
  idDetalleOc: number;
  tipoItem: string;
  itemNombre: string;
  cantidad: number;
  cantidadRecibida: number;
  precioUnitario: number;
}

interface OrdenCompra {
  idOrdenCompra: number;
  estado: string;
  esReposicion?: boolean;
  proveedor?: { nombre: string };
  detalles: DetalleOc[];
}

interface LineaRecepcion {
  idDetalleOc: number;
  tipoItem: string;
  itemNombre: string;
  pedido: number;
  yaRecibido: number;
  pendiente: number;
  precioUnitario: number;
  cantidadRecibidaAhora: number | null;
  cantidadDefectuosa: number;
  observacion: string;
}

/**
 * Registrar lo que llegó del proveedor.
 *
 * **Es el momento en que la mercancía se hace real**, y la pantalla anterior no
 * lo contaba: una tabla de campos sueltos, sin decir a dónde iba a parar el
 * stock, ni cuánto se estaba recibiendo en total, ni qué pasaba con lo
 * defectuoso. De hecho *mentía* sobre eso último — ponía «devolución a proveedor
 * (próximamente)» cuando ese circuito lleva funcionando desde la F68.
 *
 * Ahora dice las tres cosas que hay que saber antes de confirmar:
 *
 * <ol>
 *   <li><b>Dónde entra</b> — la bodega, arriba y obligatoria, no un campo más.</li>
 *   <li><b>Cuánto entra</b> — un resumen en vivo de unidades e importe, que se
 *       actualiza al teclear.</li>
 *   <li><b>Qué pasa con lo defectuoso</b> — que no entra al stock aprovechable y
 *       queda esperando en la bandeja de «Ítems defectuosos» para reclamárselo
 *       al proveedor.</li>
 * </ol>
 *
 * **Confirmar no se puede deshacer**, y también se dice: la recepción mueve
 * stock y deja su asiento en el kardex. Es la clase de aviso que solo sirve
 * *antes* del clic.
 */
@Component({
  selector: 'app-recepcion-nueva',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="rec" *ngIf="orden as oc">

      <header class="cab">
        <button class="btn-volver" [routerLink]="['/compras', oc.idOrdenCompra]">
          <span class="flecha" aria-hidden="true">←</span> Orden de compra #{{ oc.idOrdenCompra }}
        </button>
        <h1>Registrar recepción</h1>
        <p class="sub">
          {{ oc.proveedor?.nombre }}
          <span class="sep">·</span> orden #{{ oc.idOrdenCompra }}
          <span class="sep">·</span> {{ etiquetaEstado(oc.estado) }}
          <span class="pill-rep" *ngIf="oc.esReposicion">reposición · no se paga</span>
        </p>
      </header>

      <p class="bloqueo" *ngIf="!recepcionPermitida()">
        Esta orden está en <strong>{{ etiquetaEstado(oc.estado) }}</strong> y no admite
        recepciones. Solo se puede recibir una orden <strong>aprobada</strong> o
        <strong>recibida parcialmente</strong>.
      </p>

      <div class="reparto" *ngIf="recepcionPermitida()">

        <!-- ══ IZQUIERDA · lo que llega ══════════════════════════ -->
        <section class="cuerpo">

          <article class="bloque">
            <h2>Qué está llegando</h2>
            <p class="sub2">
              Escribe lo que entró de cada línea. Lo que dejes en blanco no se recibe:
              seguirá pendiente para una recepción posterior.
            </p>

            <div class="tabla-caja">
              <table>
                <thead>
                  <tr>
                    <th>Artículo</th>
                    <th class="num">Pedido</th>
                    <th class="num">Ya recibido</th>
                    <th class="num">Pendiente</th>
                    <th class="num campo-col">Llega ahora</th>
                    <th class="num campo-col">Defectuosas</th>
                    <th>Observación</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let l of lineas" [class.tocada]="(l.cantidadRecibidaAhora || 0) > 0">
                    <td>
                      {{ l.itemNombre }}
                      <span class="tipo" *ngIf="l.tipoItem !== 'producto'">materia prima</span>
                    </td>
                    <td class="num apagado">{{ l.pedido }}</td>
                    <td class="num apagado">{{ l.yaRecibido }}</td>
                    <td class="num">{{ l.pendiente }}</td>
                    <td class="num">
                      <input type="number" min="0" [max]="l.pendiente"
                             [(ngModel)]="l.cantidadRecibidaAhora" [name]="'rec' + l.idDetalleOc"
                             [class.mal]="(l.cantidadRecibidaAhora || 0) > l.pendiente"
                             placeholder="0">
                      <button type="button" class="todo" *ngIf="l.cantidadRecibidaAhora !== l.pendiente"
                              (click)="l.cantidadRecibidaAhora = l.pendiente">todo</button>
                    </td>
                    <td class="num">
                      <input type="number" min="0" [max]="l.cantidadRecibidaAhora || 0"
                             [(ngModel)]="l.cantidadDefectuosa" [name]="'def' + l.idDetalleOc"
                             [class.mal]="l.cantidadDefectuosa > (l.cantidadRecibidaAhora || 0)"
                             [disabled]="!l.cantidadRecibidaAhora">
                    </td>
                    <td>
                      <input type="text" [(ngModel)]="l.observacion" [name]="'obs' + l.idDetalleOc"
                             placeholder="Opcional">
                    </td>
                  </tr>
                  <tr *ngIf="lineas.length === 0">
                    <td colspan="7" class="vacio">
                      No queda nada pendiente por recibir en esta orden.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>

          <article class="bloque" *ngIf="totalDefectuosas() > 0">
            <h2>Lo defectuoso no se pierde</h2>
            <p class="sub2">
              Las <strong>{{ totalDefectuosas() }}</strong> unidad(es) que marques como
              defectuosas <strong>entran al stock igual</strong> —llegaron— pero quedan
              apuntadas en la bandeja de
              <a routerLink="/devoluciones-proveedor/pendientes">Ítems defectuosos</a>,
              esperando a que se le reclamen al proveedor. Desde ahí se agrupan y se crea
              la devolución.
            </p>
          </article>
        </section>

        <!-- ══ DERECHA · confirmar ══════════════════════════════ -->
        <aside class="panel">
          <div class="caja">
            <h2>Confirmar la entrada</h2>

            <label class="campo">
              <span class="etq">Bodega destino *</span>
              <select [(ngModel)]="idBodega" name="idBodega" [class.mal]="intentado && !idBodega">
                <option [ngValue]="null">Elige la bodega…</option>
                <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{ b.nombre }}</option>
              </select>
              <small class="pista">Ahí es donde sube el stock.</small>
            </label>

            <label class="campo">
              <span class="etq">N.º de guía de remisión</span>
              <input type="text" [(ngModel)]="numeroGuiaRemision" name="guia"
                     placeholder="El del transportista">
            </label>

            <label class="campo">
              <span class="etq">Observaciones</span>
              <input type="text" [(ngModel)]="observaciones" name="observaciones"
                     placeholder="Opcional">
            </label>

            <div class="resumen">
              <div class="fila">
                <span>Líneas con entrada</span>
                <strong>{{ lineasConEntrada() }} de {{ lineas.length }}</strong>
              </div>
              <div class="fila">
                <span>Unidades que entran</span>
                <strong>{{ totalUnidades() }}</strong>
              </div>
              <div class="fila" *ngIf="totalDefectuosas() > 0">
                <span>De ellas, defectuosas</span>
                <strong class="ojo">{{ totalDefectuosas() }}</strong>
              </div>
              <div class="fila total">
                <span>Valor de lo que entra</span>
                <strong>{{ valorEntrante() | currency:'USD':'symbol':'1.2-2' }}</strong>
              </div>
            </div>

            <p class="quedara" *ngIf="lineasConEntrada() > 0">
              La orden quedará <strong>{{ estadoResultante() }}</strong>.
            </p>

            <p class="error" *ngIf="formError">{{ formError }}</p>

            <button type="button" class="confirmar"
                    [disabled]="saving || lineasConEntrada() === 0"
                    (click)="confirmar()">
              {{ saving ? 'Registrando…' : 'Confirmar recepción' }}
            </button>

            <p class="nota">
              Confirmar <strong>mueve el stock</strong> y deja su asiento en el kardex.
              No se puede deshacer: revísalo antes.
            </p>
          </div>
        </aside>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{ toast }}</div>
    </div>
  `,
  styles: [`
    .rec { width: 100%; max-width: 1600px; margin: 0 auto;
           padding: clamp(1rem, 3vw, 2.5rem); padding-bottom: 3rem; }

    .cab { margin-bottom: 1.5rem; }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.6rem; color: var(--ms-text); }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .92rem;
           display: flex; align-items: center; gap: .4rem; flex-wrap: wrap; }
    .sep { color: var(--ms-text-muted); }
    .pill-rep { font-size: .66rem; text-transform: uppercase; letter-spacing: .05em;
                background: rgba(121,196,210,.12); border: 1px solid #79C4D2;
                color: #79C4D2; padding: .15rem .5rem; border-radius: 99px; }

    .bloqueo { border: 1px solid var(--ms-red); border-left-width: 3px;
               background: rgba(229,115,115,.08); border-radius: var(--ms-radius);
               padding: 1rem 1.2rem; color: var(--ms-text-muted); line-height: 1.6; }
    .bloqueo strong { color: var(--ms-text); }

    .reparto { display: grid; grid-template-columns: 1.9fr 1fr; gap: 1.5rem; align-items: start; }
    .cuerpo { display: flex; flex-direction: column; gap: 1.25rem; min-width: 0; }

    .bloque { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
              border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; min-width: 0; }
    .bloque h2 { margin: 0 0 .4rem; font-size: 1rem; color: var(--ms-text); }
    .sub2 { margin: 0 0 1rem; font-size: .85rem; color: var(--ms-text-muted); line-height: 1.55; }
    .sub2 strong { color: var(--ms-text); }
    .sub2 a { color: var(--ms-gold); }

    .tabla-caja { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .87rem; }
    th, td { padding: .55rem .6rem; text-align: left; border-bottom: 1px solid var(--ms-border);
             vertical-align: middle; }
    th { font-size: .66rem; text-transform: uppercase; letter-spacing: .06em;
         color: var(--ms-text-muted); font-weight: 600; white-space: nowrap; }
    td { color: var(--ms-text); }
    .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    th.num { text-align: right; }
    .apagado { color: var(--ms-text-muted); }
    .campo-col { width: 118px; }
    tr.tocada td { background: rgba(201,168,76,.05); }
    .tipo { display: block; font-size: .68rem; color: var(--ms-text-muted); }
    .vacio { text-align: center; color: var(--ms-text-muted); padding: 1.5rem; }

    td input { background: rgba(255,255,255,.04); border: 1px solid var(--ms-border);
               color: var(--ms-text); padding: .4rem .5rem; border-radius: var(--ms-radius-sm);
               font-size: .87rem; width: 100%; text-align: right; }
    td input[type=text] { text-align: left; }
    td input:focus { border-color: var(--ms-gold); outline: none; }
    td input:disabled { opacity: .35; }
    td input.mal { border-color: var(--ms-red); }
    .todo { display: block; width: 100%; margin-top: .25rem; background: transparent;
            border: none; color: var(--ms-text-muted); font-size: .68rem;
            cursor: pointer; text-align: right; padding: 0; }
    .todo:hover { color: var(--ms-gold); }

    /* ── Panel de confirmación ────────────────────────────────── */
    .panel { position: sticky; top: 1rem; }
    .caja { background: var(--ms-bg-card); border: 1px solid var(--ms-gold);
            border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; }
    .caja h2 { margin: 0 0 1.1rem; font-size: 1rem; color: var(--ms-text); }

    .campo { display: flex; flex-direction: column; gap: .35rem; margin-bottom: .9rem; }
    .etq { font-size: .68rem; text-transform: uppercase; letter-spacing: .08em;
           color: var(--ms-text-muted); }
    .campo input, .campo select {
      background: rgba(255,255,255,.04); border: 1px solid var(--ms-border);
      color: var(--ms-text); padding: .65rem .8rem;
      border-radius: var(--ms-radius-sm); font-size: .92rem; width: 100%;
    }
    .campo input:focus, .campo select:focus { border-color: var(--ms-gold); outline: none; }
    .campo .mal { border-color: var(--ms-red); }
    .pista { font-size: .74rem; color: var(--ms-text-muted); }

    .resumen { border-top: 1px solid var(--ms-border); margin-top: 1rem; padding-top: .9rem; }
    .fila { display: flex; justify-content: space-between; align-items: baseline;
            gap: 1rem; font-size: .86rem; color: var(--ms-text-muted); margin-bottom: .45rem; }
    .fila strong { color: var(--ms-text); font-variant-numeric: tabular-nums; }
    .fila .ojo { color: var(--ms-gold); }
    .fila.total { border-top: 1px solid var(--ms-border); padding-top: .6rem; margin-top: .6rem; }
    .fila.total strong { color: var(--ms-gold); font-size: 1.1rem; }

    .quedara { font-size: .84rem; color: var(--ms-text-muted); margin: .9rem 0 0; }
    .quedara strong { color: var(--ms-text); }
    .error { color: var(--ms-red); font-size: .84rem; margin: .8rem 0 0; line-height: 1.5; }

    .confirmar { width: 100%; background: var(--ms-gold); border: none; color: #1a1608;
                 padding: .85rem; font-size: .95rem; font-weight: 600; margin-top: 1rem;
                 border-radius: var(--ms-radius-sm); cursor: pointer; }
    .confirmar:hover:not(:disabled) { background: var(--ms-gold-light); }
    .confirmar:disabled { opacity: .4; cursor: not-allowed; }

    .nota { font-size: .77rem; color: var(--ms-text-muted); line-height: 1.55; margin: .85rem 0 0; }
    .nota strong { color: var(--ms-text); }

    @media (max-width: 1100px) {
      .reparto { grid-template-columns: 1fr; }
      .panel { position: static; }
    }
  `]
})
export class RecepcionNuevaComponent implements OnInit {

  orden: OrdenCompra | null = null;
  bodegas: Bodega[] = [];
  lineas: LineaRecepcion[] = [];
  idOrden!: number;
  idBodega: number | null = null;
  numeroGuiaRemision = '';
  observaciones = '';
  formError = '';
  saving = false;
  intentado = false;
  toast = '';
  toastError = false;

  private readonly etiquetas: Record<string, string> = {
    borrador: 'borrador',
    pendiente_aprobacion: 'pendiente de aprobación',
    aprobada: 'aprobada',
    rechazada: 'rechazada',
    recibida_parcial: 'recibida parcialmente',
    recibida_completa: 'recibida por completo',
    cancelada: 'cancelada'
  };

  constructor(private route: ActivatedRoute, private router: Router,
              private crud: CrudService, private api: ApiService) {}

  ngOnInit() {
    this.idOrden = Number(this.route.snapshot.paramMap.get('id'));

    this.crud.listar<Bodega>('bodegas', { page: 0, size: 1000 })
      .subscribe({ next: r => this.bodegas = r.content });

    this.crud.obtener<OrdenCompra>('ordenes-compra', this.idOrden).subscribe({
      next: oc => {
        this.orden = oc;
        this.lineas = (oc.detalles || [])
          .filter(d => d.cantidadRecibida < d.cantidad)
          .map(d => ({
            idDetalleOc: d.idDetalleOc,
            tipoItem: d.tipoItem,
            itemNombre: d.itemNombre,
            pedido: d.cantidad,
            yaRecibido: d.cantidadRecibida,
            pendiente: d.cantidad - d.cantidadRecibida,
            precioUnitario: d.precioUnitario,
            cantidadRecibidaAhora: null,
            cantidadDefectuosa: 0,
            observacion: ''
          }));
      },
      error: () => this.mostrarToast('No se pudo cargar la orden', true)
    });
  }

  etiquetaEstado(e: string): string { return this.etiquetas[e] || e; }

  recepcionPermitida(): boolean {
    return this.orden?.estado === 'aprobada' || this.orden?.estado === 'recibida_parcial';
  }

  lineasConEntrada(): number {
    return this.lineas.filter(l => (l.cantidadRecibidaAhora || 0) > 0).length;
  }

  totalUnidades(): number {
    return this.lineas.reduce((s, l) => s + (l.cantidadRecibidaAhora || 0), 0);
  }

  totalDefectuosas(): number {
    return this.lineas.reduce((s, l) => s + (l.cantidadRecibidaAhora ? (l.cantidadDefectuosa || 0) : 0), 0);
  }

  valorEntrante(): number {
    return this.lineas.reduce((s, l) => s + (l.cantidadRecibidaAhora || 0) * (l.precioUnitario || 0), 0);
  }

  /**
   * Qué estado le quedará a la orden, dicho antes de confirmar.
   *
   * Es la misma cuenta que hace el servidor: si después de esta entrada no queda
   * nada pendiente en ninguna línea, la orden pasa a recibida por completo.
   */
  estadoResultante(): string {
    const quedaAlgo = this.lineas.some(l => l.pendiente - (l.cantidadRecibidaAhora || 0) > 0);
    return quedaAlgo ? 'recibida parcialmente' : 'recibida por completo';
  }

  confirmar() {
    this.intentado = true;
    this.formError = '';

    if (!this.idBodega) {
      this.formError = 'Elige la bodega donde entra la mercancía.';
      return;
    }

    for (const l of this.lineas) {
      if ((l.cantidadRecibidaAhora || 0) > l.pendiente) {
        this.formError = `De «${l.itemNombre}» solo quedan ${l.pendiente} pendiente(s), `
          + `y estás recibiendo ${l.cantidadRecibidaAhora}.`;
        return;
      }
      if ((l.cantidadDefectuosa || 0) > (l.cantidadRecibidaAhora || 0)) {
        this.formError = `No puedes marcar ${l.cantidadDefectuosa} defectuosa(s) de «${l.itemNombre}» `
          + `si solo llegaron ${l.cantidadRecibidaAhora || 0}.`;
        return;
      }
    }

    const detalles = this.lineas
      .filter(l => (l.cantidadRecibidaAhora || 0) > 0)
      .map(l => ({
        idDetalleOc: l.idDetalleOc,
        cantidadRecibidaAhora: l.cantidadRecibidaAhora,
        cantidadDefectuosa: l.cantidadDefectuosa || 0,
        observacion: l.observacion || null
      }));

    if (detalles.length === 0) {
      this.formError = 'Escribe al menos una cantidad recibida.';
      return;
    }

    this.saving = true;
    this.api.post<any>('recepciones', {
      idOrdenCompra: this.idOrden,
      idBodega: this.idBodega,
      numeroGuiaRemision: this.numeroGuiaRemision || null,
      observaciones: this.observaciones || null,
      detalles
    }).subscribe({
      next: () => {
        this.saving = false;
        this.mostrarToast('Recepción registrada. El stock ya está actualizado.');
        setTimeout(() => this.router.navigate(['/compras', this.idOrden]), 900);
      },
      error: err => {
        this.saving = false;
        this.formError = err.error?.message || 'No se pudo registrar la recepción.';
      }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg;
    this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
