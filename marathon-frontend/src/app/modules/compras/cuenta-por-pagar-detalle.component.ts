import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';

interface Pago {
  idPago: number;
  monto: number;
  fechaPago: string;
  metodoPago: string;
  referencia: string;
  observaciones: string;
  usuarioNombre: string;
}

interface CuentaDetalle {
  idCuentaPagar: number;
  idFacturaCompra: number;
  numeroFacturaProveedor: string;
  proveedorNombre: string;
  montoTotal: number;
  montoPagado: number;
  saldoPendiente: number;
  fechaVencimiento: string;
  estado: string;
  pagos: Pago[];
}

/** Lo que se está pagando, traído de la orden que originó la factura. */
interface LineaOrden {
  itemNombre: string;
  cantidad: number;
  cantidadRecibida: number;
  precioUnitario: number;
}

/**
 * Pagar una cuenta a proveedor.
 *
 * **Por qué está partida en dos.** Pagar es decidir, y para decidir hay que ver
 * qué se está pagando. La pantalla anterior enseñaba seis cifras sueltas y un
 * formulario debajo: no había forma de saber a qué mercancía correspondía ese
 * saldo sin abrir la orden en otra pestaña.
 *
 * A la izquierda va lo que se revisa —la factura, **las líneas con lo pedido
 * frente a lo recibido**, y los pagos ya hechos—. A la derecha, lo que se hace.
 *
 * **El reparto es ~62/38, no 75/25.** Con 25 el panel de pago queda en unos
 * 300 px y los campos se apelotonan; el importe y el método necesitan aire
 * porque es donde uno se equivoca. Y va **fijo al hacer scroll**: el saldo tiene
 * que seguir a la vista mientras se repasa el detalle, que es justo cuando se
 * decide cuánto pagar.
 *
 * **El detalle de la orden solo se pide si el rol puede verlo.** El Supervisor
 * E-Commerce ve cuentas por pagar pero no órdenes de compra, así que pedirlo
 * sería un 403 garantizado en cada carga — la lección de la F65: una llamada que
 * siempre falla es ruido, y el ruido acaba tapando los fallos de verdad.
 */
@Component({
  selector: 'app-cuenta-por-pagar-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="cxp" *ngIf="cuenta as c">

      <!-- ── Cabecera ─────────────────────────────────────────── -->
      <header class="cab">
        <div class="cab-txt">
          <button class="volver" routerLink="/cuentas-por-pagar">← Cuentas por pagar</button>
          <h1>
            {{ c.numeroFacturaProveedor }}
            <span class="pill" [ngClass]="'e-' + c.estado">{{ c.estado | uppercase }}</span>
          </h1>
          <p class="prov">{{ c.proveedorNombre }} <span class="sep">·</span> cuenta #{{ c.idCuentaPagar }}</p>
        </div>
        <button class="pdf" (click)="abrirPdf()" [disabled]="descargando" *ngIf="puedeVerOrden">
          {{ descargando ? 'Abriendo…' : 'Ver documento PDF' }}
        </button>
      </header>

      <div class="reparto">

        <!-- ══ IZQUIERDA · lo que se revisa ══════════════════════ -->
        <section class="detalle">

          <!-- Cuánto va pagado -->
          <article class="bloque avance">
            <div class="cifras">
              <div class="cifra">
                <span class="etq">Total de la factura</span>
                <strong>{{ c.montoTotal | currency:'USD':'symbol':'1.2-2' }}</strong>
              </div>
              <div class="cifra">
                <span class="etq">Ya pagado</span>
                <strong class="ok">{{ c.montoPagado | currency:'USD':'symbol':'1.2-2' }}</strong>
              </div>
              <div class="cifra grande">
                <span class="etq">Saldo pendiente</span>
                <strong class="pend">{{ c.saldoPendiente | currency:'USD':'symbol':'1.2-2' }}</strong>
              </div>
            </div>

            <div class="barra" [attr.aria-label]="'Pagado el ' + porcentaje() + ' por ciento'">
              <div class="barra-llena" [style.width.%]="porcentaje()"></div>
            </div>
            <p class="barra-pie">
              {{ porcentaje() }} % pagado
              <span class="sep">·</span>
              <span [class.vencida]="estaVencida()">{{ textoVencimiento() }}</span>
            </p>
          </article>

          <!-- Qué se está pagando -->
          <article class="bloque" *ngIf="puedeVerOrden">
            <h2>Qué se está pagando</h2>
            <p class="sub" *ngIf="lineas.length">
              Mercancía recibida de la orden de compra #{{ idOrden }}. El importe de la
              factura sale de lo <strong>recibido</strong>, no de lo pedido.
            </p>

            <div class="tabla-caja" *ngIf="lineas.length; else sinLineas">
              <table>
                <thead>
                  <tr>
                    <th>Artículo</th>
                    <th class="num">Pedido</th>
                    <th class="num">Recibido</th>
                    <th class="num">P. unitario</th>
                    <th class="num">Importe</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let l of lineas" [class.parcial]="l.cantidadRecibida < l.cantidad">
                    <td>{{ l.itemNombre }}</td>
                    <td class="num">{{ l.cantidad }}</td>
                    <td class="num">
                      {{ l.cantidadRecibida }}
                      <span class="falta" *ngIf="l.cantidadRecibida < l.cantidad"
                            [title]="'Faltan ' + (l.cantidad - l.cantidadRecibida)">parcial</span>
                    </td>
                    <td class="num">{{ l.precioUnitario | currency:'USD':'symbol':'1.2-2' }}</td>
                    <td class="num fuerte">{{ importe(l) | currency:'USD':'symbol':'1.2-2' }}</td>
                  </tr>
                </tbody>
                <tfoot>
                  <tr>
                    <td colspan="4" class="num">Valor recibido de la orden</td>
                    <td class="num">{{ totalRecibido() | currency:'USD':'symbol':'1.2-2' }}</td>
                  </tr>
                  <tr *ngIf="cubreParteDeLaOrden()">
                    <td colspan="4" class="num sutil">Documentado en otra(s) factura(s)</td>
                    <td class="num sutil">− {{ (totalRecibido() - factura.subtotal) | currency:'USD':'symbol':'1.2-2' }}</td>
                  </tr>
                  <tr *ngIf="factura">
                    <td colspan="4" class="num">Subtotal de <strong>esta</strong> factura</td>
                    <td class="num">{{ factura.subtotal | currency:'USD':'symbol':'1.2-2' }}</td>
                  </tr>
                  <tr *ngIf="factura">
                    <td colspan="4" class="num sutil">IVA</td>
                    <td class="num sutil">{{ factura.impuesto | currency:'USD':'symbol':'1.2-2' }}</td>
                  </tr>
                  <tr class="cierre" *ngIf="factura">
                    <td colspan="4" class="num">Total a pagar por esta factura</td>
                    <td class="num fuerte">{{ factura.total | currency:'USD':'symbol':'1.2-2' }}</td>
                  </tr>
                </tfoot>
              </table>
            </div>

            <p class="aclara" *ngIf="cubreParteDeLaOrden()">
              Esta factura cubre <strong>parte</strong> de la orden: el resto se documentó
              cuando se recibió, en otra factura. Por eso el total a pagar es menor que el
              valor recibido de la orden completa.
            </p>

            <ng-template #sinLineas>
              <p class="vacio">No se pudo cargar el detalle de la orden.</p>
            </ng-template>
          </article>

          <!-- Lo ya pagado -->
          <article class="bloque">
            <h2>Pagos registrados</h2>
            <p class="vacio" *ngIf="!c.pagos?.length">
              Todavía no se ha pagado nada de esta cuenta.
            </p>

            <ol class="pagos" *ngIf="c.pagos?.length">
              <li *ngFor="let p of c.pagos">
                <div class="p-monto">{{ p.monto | currency:'USD':'symbol':'1.2-2' }}</div>
                <div class="p-datos">
                  <span class="p-metodo">{{ p.metodoPago | titlecase }}</span>
                  <span class="sep">·</span>
                  <span>{{ p.fechaPago | date:'dd/MM/yyyy HH:mm' }}</span>
                  <span class="sep">·</span>
                  <span>{{ p.usuarioNombre }}</span>
                  <div class="p-ref" *ngIf="p.referencia">Ref. {{ p.referencia }}</div>
                  <div class="p-obs" *ngIf="p.observaciones">{{ p.observaciones }}</div>
                </div>
              </li>
            </ol>
          </article>
        </section>

        <!-- ══ DERECHA · lo que se hace ══════════════════════════ -->
        <aside class="pago">
          <div class="pago-caja" *ngIf="c.estado !== 'pagada'; else saldada">
            <h2>Registrar pago</h2>

            <div class="saldo-grande">
              <span class="etq">Saldo pendiente</span>
              <strong>{{ c.saldoPendiente | currency:'USD':'symbol':'1.2-2' }}</strong>
            </div>

            <form (ngSubmit)="registrarPago()">
              <label class="campo">
                <span class="etq">Monto a pagar *</span>
                <input type="number" step="0.01" min="0.01" [max]="c.saldoPendiente"
                       [(ngModel)]="pago.monto" name="monto" required
                       [class.mal]="excede()" placeholder="0.00">
              </label>

              <div class="atajos">
                <button type="button" (click)="pago.monto = c.saldoPendiente">Todo el saldo</button>
                <button type="button" (click)="pago.monto = redondear(c.saldoPendiente / 2)">La mitad</button>
              </div>

              <p class="aviso-mal" *ngIf="excede()">
                Excede el saldo en {{ (montoNum - c.saldoPendiente) | currency:'USD':'symbol':'1.2-2' }}
              </p>
              <p class="restara" *ngIf="montoNum > 0 && !excede()">
                Quedará pendiente
                <strong>{{ (c.saldoPendiente - montoNum) | currency:'USD':'symbol':'1.2-2' }}</strong>
              </p>

              <span class="etq etq-suelta">Método de pago *</span>
              <div class="metodos">
                <button type="button" *ngFor="let m of metodos"
                        class="metodo" [class.on]="pago.metodoPago === m.valor"
                        [attr.aria-pressed]="pago.metodoPago === m.valor"
                        (click)="pago.metodoPago = m.valor">
                  {{ m.etiqueta }}
                </button>
              </div>

              <label class="campo">
                <span class="etq">Referencia</span>
                <input [(ngModel)]="pago.referencia" name="referencia"
                       placeholder="N.º de transferencia o cheque">
              </label>

              <label class="campo">
                <span class="etq">Observaciones</span>
                <input [(ngModel)]="pago.observaciones" name="observaciones" placeholder="Opcional">
              </label>

              <button type="submit" class="confirmar"
                      [disabled]="guardando || excede() || !pago.monto || !pago.metodoPago">
                {{ guardando ? 'Registrando…' : 'Registrar pago' }}
              </button>

              <p class="nota-asiento">
                Un pago registrado <strong>no se puede corregir</strong>: es un asiento
                contable. Revisa el importe antes de confirmar.
              </p>
            </form>
          </div>

          <ng-template #saldada>
            <div class="pago-caja saldada">
              <div class="tic" aria-hidden="true">✓</div>
              <h2>Cuenta saldada</h2>
              <p>Se pagaron {{ c.montoTotal | currency:'USD':'symbol':'1.2-2' }} en
                 {{ c.pagos?.length || 0 }} pago(s). No queda nada pendiente.</p>
            </div>
          </ng-template>
        </aside>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{ toast }}</div>
    </div>
  `,
  styles: [`
    .cxp { max-width: 1500px; margin: 0 auto; padding-bottom: 3rem; }

    /* ── Cabecera ─────────────────────────────────────────────── */
    .cab { display: flex; justify-content: space-between; align-items: flex-start;
           gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1.75rem; }
    .volver { background: transparent; border: none; color: var(--ms-text-muted);
              cursor: pointer; font-size: .85rem; padding: 0 0 .5rem; }
    .volver:hover { color: var(--ms-gold); }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.6rem; color: var(--ms-text);
              display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .prov { margin: 0; color: var(--ms-text-muted); font-size: .92rem; }
    .sep { color: var(--ms-text-muted); margin: 0 .35rem; }

    .pill { font-size: .68rem; font-weight: 700; letter-spacing: .06em;
            padding: .25rem .6rem; border-radius: 99px; color: #fff; }
    .e-vigente { background: #2563eb; }
    .e-vencida { background: #dc2626; }
    .e-pagada  { background: #16a34a; }

    .pdf { background: var(--ms-gold-dim); border: 1px solid var(--ms-gold);
           color: var(--ms-gold-light); padding: .6rem 1.1rem;
           border-radius: var(--ms-radius-sm); cursor: pointer; font-size: .88rem; }
    .pdf:hover:not(:disabled) { background: rgba(201,168,76,.22); }
    .pdf:disabled { opacity: .5; cursor: default; }

    /* ── El reparto: 62 / 38 ──────────────────────────────────── */
    .reparto { display: grid; grid-template-columns: 1.6fr 1fr; gap: 1.5rem; align-items: start; }
    .detalle { display: flex; flex-direction: column; gap: 1.25rem; min-width: 0; }

    .bloque { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
              border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; min-width: 0; }
    .bloque h2 { margin: 0 0 .3rem; font-size: 1rem; color: var(--ms-text); }
    .bloque .sub { margin: 0 0 1rem; font-size: .85rem; color: var(--ms-text-muted); line-height: 1.5; }
    .vacio { color: var(--ms-text-muted); font-size: .88rem; margin: .5rem 0 0; }

    /* ── Avance del pago ──────────────────────────────────────── */
    .cifras { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
              gap: 1rem; margin-bottom: 1.1rem; }
    .cifra { display: flex; flex-direction: column; gap: .25rem; }
    .etq { font-size: .68rem; text-transform: uppercase; letter-spacing: .08em;
           color: var(--ms-text-muted); }
    .cifra strong { font-size: 1.15rem; color: var(--ms-text); font-variant-numeric: tabular-nums; }
    .cifra.grande strong { font-size: 1.55rem; }
    .cifra .ok { color: var(--ms-green); }
    .cifra .pend { color: var(--ms-gold); }

    .barra { height: 8px; background: rgba(255,255,255,.06); border-radius: 99px; overflow: hidden; }
    .barra-llena { height: 100%; background: linear-gradient(90deg, var(--ms-green), #7cc47f);
                   border-radius: 99px; transition: width .35s ease; }
    .barra-pie { margin: .55rem 0 0; font-size: .82rem; color: var(--ms-text-muted); }
    .vencida { color: var(--ms-red); font-weight: 600; }

    /* ── Tabla del detalle ────────────────────────────────────── */
    .tabla-caja { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .88rem; }
    th, td { padding: .6rem .7rem; text-align: left; border-bottom: 1px solid var(--ms-border); }
    th { font-size: .68rem; text-transform: uppercase; letter-spacing: .07em;
         color: var(--ms-text-muted); font-weight: 600; white-space: nowrap; }
    td { color: var(--ms-text); }
    .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    th.num { text-align: right; }
    .fuerte { font-weight: 600; color: var(--ms-gold); }
    tfoot td { border-bottom: none; border-top: 1px solid var(--ms-gold);
               padding-top: .7rem; color: var(--ms-text-muted); }
    tfoot .fuerte { color: var(--ms-gold); font-size: .95rem; }
    tr.parcial td { background: rgba(201,168,76,.04); }
    tfoot tr td { border-top: none; padding-top: .35rem; padding-bottom: .35rem; }
    tfoot tr:first-child td { border-top: 1px solid var(--ms-border); padding-top: .7rem; }
    tfoot .sutil { color: var(--ms-text-muted); font-size: .82rem; }
    /* Las etiquetas del pie NO van en una sola linea: con nowrap ensanchaban la
       tabla, la tabla empujaba su columna, y la pagina acababa desplazandose en
       horizontal por debajo de la barra lateral. */
    tfoot td[colspan] { white-space: normal; }
    tfoot tr.cierre td { border-top: 1px solid var(--ms-gold); padding-top: .55rem; }
    .aclara { margin: .9rem 0 0; font-size: .82rem; color: var(--ms-text-muted); line-height: 1.55; }
    .aclara strong { color: var(--ms-text); }
    .falta { font-size: .64rem; text-transform: uppercase; letter-spacing: .05em;
             background: var(--ms-gold-dim); border: 1px solid var(--ms-gold);
             color: var(--ms-gold-light); padding: .05rem .35rem;
             border-radius: 99px; margin-left: .4rem; }

    /* ── Pagos hechos ─────────────────────────────────────────── */
    .pagos { list-style: none; margin: .75rem 0 0; padding: 0;
             display: flex; flex-direction: column; gap: .6rem; }
    .pagos li { display: flex; gap: 1rem; align-items: baseline;
                padding: .7rem .85rem; border: 1px solid var(--ms-border);
                border-left: 3px solid var(--ms-green);
                border-radius: var(--ms-radius-sm); background: rgba(76,175,80,.04); }
    .p-monto { font-weight: 600; color: var(--ms-green); font-size: 1rem;
               font-variant-numeric: tabular-nums; white-space: nowrap; }
    .p-datos { font-size: .82rem; color: var(--ms-text-muted); min-width: 0; }
    .p-metodo { color: var(--ms-text); }
    .p-ref, .p-obs { margin-top: .2rem; font-size: .78rem; }

    /* ── Panel de pago, fijo al hacer scroll ──────────────────── */
    .pago { position: sticky; top: 1rem; }
    .pago-caja { background: var(--ms-bg-card); border: 1px solid var(--ms-gold);
                 border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; }
    .pago-caja h2 { margin: 0 0 1rem; font-size: 1rem; color: var(--ms-text); }

    .saldo-grande { display: flex; flex-direction: column; gap: .2rem;
                    padding: .9rem 1rem; margin-bottom: 1.25rem;
                    background: var(--ms-gold-dim); border-radius: var(--ms-radius-sm); }
    .saldo-grande strong { font-size: 1.75rem; color: var(--ms-gold-light);
                           font-variant-numeric: tabular-nums; }

    .campo { display: flex; flex-direction: column; gap: .35rem; margin-bottom: .9rem; }
    .campo input { background: rgba(255,255,255,.04); border: 1px solid var(--ms-border);
                   color: var(--ms-text); padding: .7rem .85rem;
                   border-radius: var(--ms-radius-sm); font-size: .95rem; width: 100%; }
    .campo input:focus { border-color: var(--ms-gold); outline: none; }
    .campo input.mal { border-color: var(--ms-red); }
    .etq-suelta { display: block; margin-bottom: .45rem; }

    .atajos { display: flex; gap: .5rem; margin: -.4rem 0 .9rem; }
    .atajos button { flex: 1; background: transparent; border: 1px solid var(--ms-border);
                     color: var(--ms-text-muted); padding: .4rem; font-size: .78rem;
                     border-radius: var(--ms-radius-sm); cursor: pointer; }
    .atajos button:hover { border-color: var(--ms-gold); color: var(--ms-gold); }

    .aviso-mal { color: var(--ms-red); font-size: .8rem; margin: -.4rem 0 .8rem; }
    .restara { color: var(--ms-text-muted); font-size: .82rem; margin: -.4rem 0 .9rem; }
    .restara strong { color: var(--ms-text); }

    .metodos { display: grid; grid-template-columns: 1fr 1fr; gap: .5rem; margin-bottom: 1rem; }
    .metodo { background: transparent; border: 1px solid var(--ms-border);
              color: var(--ms-text-muted); padding: .55rem .4rem; font-size: .84rem;
              border-radius: var(--ms-radius-sm); cursor: pointer; transition: all .15s ease; }
    .metodo:hover { border-color: rgba(255,255,255,.2); color: var(--ms-text); }
    .metodo.on { background: var(--ms-gold-dim); border-color: var(--ms-gold);
                 color: var(--ms-gold-light); font-weight: 600; }

    .confirmar { width: 100%; background: var(--ms-gold); border: none; color: #1a1608;
                 padding: .85rem; font-size: .95rem; font-weight: 600;
                 border-radius: var(--ms-radius-sm); cursor: pointer; margin-top: .3rem; }
    .confirmar:hover:not(:disabled) { background: var(--ms-gold-light); }
    .confirmar:disabled { opacity: .4; cursor: not-allowed; }

    .nota-asiento { font-size: .76rem; color: var(--ms-text-muted);
                    line-height: 1.5; margin: .9rem 0 0; }
    .nota-asiento strong { color: var(--ms-text); }

    .saldada { text-align: center; border-color: var(--ms-green); }
    .saldada .tic { width: 48px; height: 48px; margin: 0 auto .75rem;
                    display: grid; place-items: center; border-radius: 50%;
                    background: rgba(76,175,80,.15); border: 1px solid var(--ms-green);
                    color: var(--ms-green); font-size: 1.5rem; }
    .saldada p { color: var(--ms-text-muted); font-size: .88rem; margin: 0; line-height: 1.55; }

    @media (max-width: 1024px) {
      .reparto { grid-template-columns: 1fr; }
      .pago { position: static; }
    }
    @media (max-width: 520px) {
      .metodos { grid-template-columns: 1fr; }
      .cab h1 { font-size: 1.3rem; }
    }
  `]
})
export class CuentaPorPagarDetalleComponent implements OnInit {

  cuenta: CuentaDetalle | null = null;
  lineas: LineaOrden[] = [];
  idOrden: number | null = null;
  /** La factura de la que cuelga la cuenta. Hace falta para cuadrar el detalle. */
  factura: any = null;
  puedeVerOrden = false;

  pago: { monto: number | null; metodoPago: string; referencia: string; observaciones: string } =
    { monto: null, metodoPago: '', referencia: '', observaciones: '' };
  guardando = false;
  descargando = false;
  toast = '';
  toastError = false;

  readonly metodos = [
    { valor: 'transferencia', etiqueta: 'Transferencia' },
    { valor: 'cheque',        etiqueta: 'Cheque' },
    { valor: 'efectivo',      etiqueta: 'Efectivo' },
    { valor: 'tarjeta',       etiqueta: 'Tarjeta' }
  ];

  constructor(private route: ActivatedRoute, private api: ApiService,
              private auth: AuthService) {}

  ngOnInit() {
    // Solo Compras y el Administrador leen órdenes de compra. El Supervisor ve
    // esta pantalla pero no la orden: pedirla sería un 403 en cada carga.
    this.puedeVerOrden = this.auth.hasRol('Encargado de Compras')
                      || this.auth.hasRol('Administrador');

    this.cargar(Number(this.route.snapshot.paramMap.get('id')));
  }

  cargar(id: number) {
    this.api.get<CuentaDetalle>(`cuentas-por-pagar/${id}`).subscribe({
      next: res => {
        this.cuenta = res;
        if (this.puedeVerOrden) { this.cargarDetalleDeLaOrden(res.idFacturaCompra); }
      },
      error: () => this.mostrarToast('No se pudo cargar la cuenta', true)
    });
  }

  /**
   * La cuenta cuelga de una factura, y la factura de una orden. Son dos saltos
   * porque el detalle de lo comprado vive en la orden, no en la cuenta.
   */
  private cargarDetalleDeLaOrden(idFactura: number) {
    this.api.get<any>(`facturas-compra/${idFactura}`).subscribe({
      next: factura => {
        this.factura = factura;
        this.idOrden = factura.idOrdenCompra;
        this.api.get<any>(`ordenes-compra/${factura.idOrdenCompra}`).subscribe({
          next: orden => { this.lineas = orden.detalles || []; },
          error: () => { /* sin detalle: el bloque queda con su aviso */ }
        });
      },
      error: () => { /* idem */ }
    });
  }

  importe(l: LineaOrden): number {
    return (l.cantidadRecibida || 0) * (l.precioUnitario || 0);
  }

  totalRecibido(): number {
    return this.lineas.reduce((suma, l) => suma + this.importe(l), 0);
  }

  /**
   * Una orden recibida en dos tandas produce DOS facturas. Sin decirlo, el
   * bloque enseñaba "valor recibido de la orden: 100" junto a una factura de
   * 69, y eso desconcierta a quien va a pagar: parece que le falta dinero.
   */
  cubreParteDeLaOrden(): boolean {
    if (!this.factura || !this.lineas.length) { return false; }
    return this.totalRecibido() - Number(this.factura.subtotal) > 0.005;
  }

  porcentaje(): number {
    const c = this.cuenta;
    if (!c || !c.montoTotal) { return 0; }
    return Math.round((c.montoPagado / c.montoTotal) * 100);
  }

  /** El importe como numero: el campo empieza vacio, no en cero. */
  get montoNum(): number { return this.pago.monto ?? 0; }

  excede(): boolean {
    return !!this.cuenta && (this.pago.monto ?? 0) > this.cuenta.saldoPendiente;
  }

  redondear(n: number): number {
    return Math.round(n * 100) / 100;
  }

  private diasParaVencer(): number | null {
    if (!this.cuenta?.fechaVencimiento) { return null; }
    const vence = new Date(this.cuenta.fechaVencimiento + 'T00:00:00');
    const hoy = new Date();
    hoy.setHours(0, 0, 0, 0);
    return Math.round((vence.getTime() - hoy.getTime()) / 86400000);
  }

  estaVencida(): boolean {
    const d = this.diasParaVencer();
    return this.cuenta?.estado !== 'pagada' && d !== null && d < 0;
  }

  textoVencimiento(): string {
    const d = this.diasParaVencer();
    if (d === null) { return 'sin fecha de vencimiento'; }
    if (this.cuenta?.estado === 'pagada') { return 'venció el ' + this.cuenta.fechaVencimiento; }
    if (d < 0)  { return `vencida hace ${Math.abs(d)} día(s)`; }
    if (d === 0) { return 'vence hoy'; }
    return `vence en ${d} día(s)`;
  }

  abrirPdf() {
    if (!this.cuenta || this.descargando) { return; }
    this.descargando = true;
    this.api.getBlob(`facturas-compra/${this.cuenta.idFacturaCompra}/pdf`).subscribe({
      next: (pdf: Blob) => {
        this.descargando = false;
        const url = URL.createObjectURL(pdf);
        window.open(url, '_blank');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: () => {
        this.descargando = false;
        this.mostrarToast('No se pudo abrir el documento', true);
      }
    });
  }

  registrarPago() {
    if (!this.cuenta || !this.pago.monto || !this.pago.metodoPago) {
      this.mostrarToast('Indica el monto y el método de pago', true);
      return;
    }
    if (this.excede()) {
      this.mostrarToast('El monto excede el saldo pendiente', true);
      return;
    }

    this.guardando = true;
    this.api.post<any>('pagos-proveedor', {
      idCuentaPagar: this.cuenta.idCuentaPagar,
      monto: this.pago.monto,
      metodoPago: this.pago.metodoPago,
      referencia: this.pago.referencia || null,
      observaciones: this.pago.observaciones || null
    }).subscribe({
      next: res => {
        this.guardando = false;
        const saldo = Number(res.saldoResultante);
        this.mostrarToast(saldo === 0
          ? 'Cuenta saldada completamente'
          : 'Pago registrado. Queda pendiente $' + saldo.toFixed(2));
        this.pago = { monto: null, metodoPago: '', referencia: '', observaciones: '' };
        this.cargar(this.cuenta!.idCuentaPagar);
      },
      error: err => {
        this.guardando = false;
        this.mostrarToast(err.error?.message || 'No se pudo registrar el pago', true);
      }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg;
    this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 4000);
  }
}
