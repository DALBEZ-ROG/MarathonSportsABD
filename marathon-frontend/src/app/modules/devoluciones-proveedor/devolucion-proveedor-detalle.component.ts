import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';

/**
 * Una devolución a proveedor, explicada.
 *
 * **El problema que resuelve esta pantalla no era estético.** La anterior
 * enseñaba seis fichas y una tabla, y no contestaba ninguna de las tres
 * preguntas que trae quien la abre:
 *
 * <ol>
 *   <li><b>¿De dónde salió esto?</b> Una devolución a proveedor no se inventa:
 *       cada línea viene de mercancía que ya se marcó como defectuosa en otro
 *       sitio — al recibirla del proveedor, o al inspeccionar lo que devolvió un
 *       cliente. La pantalla decía «RMA» o «Recepción» y ahí lo dejaba.</li>
 *   <li><b>¿En qué punto está?</b> Había un estado suelto, sin decir qué va
 *       antes ni qué viene después.</li>
 *   <li><b>¿Qué me toca hacer ahora?</b> Los botones estaban ahí, sin decir qué
 *       significaban.</li>
 * </ol>
 *
 * **Y un botón que engañaba.** «Rechazar» parecía «yo rechazo esta devolución»,
 * cuando en la máquina de estados `rechazada` significa lo contrario: **el
 * proveedor no la aceptó**. Solo se puede desde `enviada` — es decir, después de
 * que salga— justamente porque es la respuesta de él, no una decisión nuestra.
 * Ahora el botón lo dice con todas las letras.
 */
@Component({
  selector: 'app-devolucion-proveedor-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="dvp" *ngIf="dev">

      <header class="cab">
        <div>
          <button class="volver" routerLink="/devoluciones-proveedor">← Devoluciones a proveedor</button>
          <h1>
            Devolución #{{ dev.idDevolucionProv }}
            <span class="pill" [ngClass]="'e-' + dev.estado">{{ etiquetaEstado(dev.estado) }}</span>
          </h1>
          <p class="sub">
            {{ dev.proveedorNombre }}
            <span class="sep">·</span> abierta el {{ dev.fechaDevolucion | date:'dd/MM/yyyy' }}
            <span class="sep">·</span> {{ dev.registradoPor || 'sin registrar' }}
          </p>
        </div>
      </header>

      <!-- ── En qué punto está ────────────────────────────────── -->
      <section class="pasos" aria-label="Estado de la devolución">
        <div class="paso" *ngFor="let p of pasos(); let i = index"
             [class.hecho]="p.estado === 'hecho'"
             [class.ahora]="p.estado === 'ahora'"
             [class.malo]="p.malo">
          <span class="marca" aria-hidden="true">{{ p.estado === 'hecho' ? '✓' : (i + 1) }}</span>
          <div class="paso-txt">
            <strong>{{ p.titulo }}</strong>
            <span>{{ p.detalle }}</span>
          </div>
        </div>
      </section>

      <div class="reparto">

        <!-- ══ IZQUIERDA · qué se devuelve y de dónde salió ══════ -->
        <section class="cuerpo">

          <article class="bloque">
            <h2>Qué se le devuelve</h2>
            <p class="sub2">
              Cada línea es mercancía que <strong>ya se marcó como defectuosa</strong> en
              otro momento. Aquí no se decide que algo está mal: aquí se agrupa lo que ya
              lo estaba para mandárselo al proveedor.
            </p>

            <p class="vacio" *ngIf="!dev.detalles?.length">
              Esta devolución no tiene líneas registradas. Las devoluciones que vienen
              de la carga masiva de datos de prueba son así: se creó la cabecera pero no
              el detalle. <strong>No es un fallo de la pantalla</strong> — una devolución
              creada desde «Ítems defectuosos» sí las trae.
            </p>

            <div class="tabla-caja" *ngIf="dev.detalles?.length">
              <table>
                <thead>
                  <tr>
                    <th>Artículo</th>
                    <th class="num">Cantidad</th>
                    <th>Motivo</th>
                    <th>De dónde salió</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let d of dev.detalles">
                    <td>{{ d.productoNombre }}</td>
                    <td class="num">{{ d.cantidad }}</td>
                    <td>{{ d.motivo || '—' }}</td>
                    <td>
                      <span class="origen" [ngClass]="'o-' + d.origen">
                        {{ d.origen === 'rma_cliente' ? 'Devolución de cliente' : 'Recepción de compra' }}
                      </span>
                      <div class="ref">{{ d.referenciaOrigen }}</div>
                    </td>
                  </tr>
                </tbody>
                <tfoot>
                  <tr>
                    <td>Total</td>
                    <td class="num fuerte">{{ totalUnidades() }} unidad(es)</td>
                    <td colspan="2"></td>
                  </tr>
                </tfoot>
              </table>
            </div>
          </article>

          <!-- De dónde nace todo esto -->
          <article class="bloque ayuda">
            <h2>De dónde nace una devolución a proveedor</h2>
            <p class="sub2">
              Nunca empieza en esta pantalla. Empieza <strong>antes</strong>, en uno de
              estos dos sitios, y de ahí cae a la bandeja de
              <a routerLink="/devoluciones-proveedor/pendientes">Ítems defectuosos</a>:
            </p>
            <ol class="origenes">
              <li>
                <span class="origen o-recepcion_compra">Recepción de compra</span>
                <p>Al recibir del proveedor, quien recibe anota una
                <strong>cantidad defectuosa</strong>. Esa cantidad queda apartada
                esperando a que se le devuelva.</p>
              </li>
              <li>
                <span class="origen o-rma_cliente">Devolución de cliente</span>
                <p>Un cliente devuelve algo, el Operador de Bodega lo inspecciona y marca
                la línea como <strong>defectuosa</strong>. Si el fallo es de fábrica, se
                le devuelve al proveedor en vez de reponerlo al inventario.</p>
              </li>
            </ol>
            <p class="sub2">
              Desde esa bandeja se agrupan los ítems <strong>de un mismo proveedor</strong>
              y se crea la devolución. Un ítem solo puede entrar en una devolución: el
              sistema no deja reclamarlo dos veces.
            </p>
          </article>
        </section>

        <!-- ══ DERECHA · qué toca hacer ═════════════════════════ -->
        <aside class="accion">

          <!-- pendiente -->
          <div class="caja" *ngIf="dev.estado === 'pendiente'">
            <h2>Todavía no ha salido</h2>
            <p>La devolución está preparada pero la mercancía sigue aquí. Cuando la
            mandes físicamente al proveedor, márcalo:</p>
            <button class="principal" (click)="marcarEnviada()" [disabled]="guardando" *ngIf="esCompras">
              {{ guardando ? 'Guardando…' : 'Ya se la envié al proveedor' }}
            </button>
            <p class="nota">Después de esto la devolución queda a la espera de su respuesta.</p>
          </div>

          <!-- enviada -->
          <div class="caja" *ngIf="dev.estado === 'enviada'">
            <h2>En manos del proveedor</h2>
            <p>Ya se la enviaste. Cuando conteste, registra <strong>qué respondió</strong>.</p>

            <ng-container *ngIf="esCompras">
              <h3>Si la aceptó</h3>
              <div class="tipos">
                <button type="button" class="tipo" [class.on]="resolucion.tipoResolucion === 'reembolso'"
                        (click)="resolucion.tipoResolucion = 'reembolso'">
                  Devuelve el dinero
                </button>
                <button type="button" class="tipo" [class.on]="resolucion.tipoResolucion === 'reposicion'"
                        (click)="resolucion.tipoResolucion = 'reposicion'">
                  Manda otra igual
                </button>
              </div>

              <label class="campo" *ngIf="resolucion.tipoResolucion === 'reembolso'">
                <span class="etq">Monto que devuelve *</span>
                <input type="number" step="0.01" min="0.01"
                       [(ngModel)]="resolucion.montoReembolso" name="monto" placeholder="0.00">
              </label>

              <label class="campo">
                <span class="etq">Observaciones</span>
                <input [(ngModel)]="resolucion.observaciones" name="obs"
                       placeholder="N.º de nota de crédito, guía…">
              </label>

              <button class="principal" (click)="resolverDev()"
                      [disabled]="guardando || !resolucion.tipoResolucion || faltaMonto()">
                {{ guardando ? 'Guardando…' : 'Registrar la respuesta' }}
              </button>

              <p class="nota" *ngIf="resolucion.tipoResolucion === 'reposicion'">
                Al guardar se creará <strong>una orden de compra de reposición</strong> con
                esta mercancía, ya aprobada y esperando llegar. <strong>No se factura ni se
                paga</strong>: ya se pagó al comprar la que salió defectuosa. Aparecerá en
                el tablero bajo «Aprobadas sin recibir», que es tu aviso de que viene.
              </p>

              <div class="separador"><span>o bien</span></div>

              <button class="secundario" (click)="rechazar()" [disabled]="guardando">
                El proveedor la rechazó
              </button>
              <p class="nota">
                Esto no es «yo la rechazo»: deja constancia de que <strong>él</strong> no
                aceptó la reclamación. La devolución se cierra sin compensación.
              </p>
            </ng-container>
          </div>

          <!-- resuelta -->
          <div class="caja cerrada ok" *ngIf="dev.estado === 'resuelta'">
            <div class="tic" aria-hidden="true">✓</div>
            <h2>Resuelta</h2>
            <p *ngIf="dev.tipoResolucion === 'reembolso'">
              El proveedor devolvió
              <strong>{{ dev.montoReembolso | currency:'USD':'symbol':'1.2-2' }}</strong>.
            </p>
            <p *ngIf="dev.tipoResolucion === 'reposicion'">
              El proveedor repone la mercancía. Se creó una <strong>orden de compra de
              reposición</strong> esperando su llegada: recíbela como cualquier otra
              entrada, pero <strong>no se factura ni se paga</strong>.
            </p>
            <a class="ir-oc" *ngIf="dev.tipoResolucion === 'reposicion'" routerLink="/compras">
              Ver órdenes de compra →
            </a>
            <p class="obs" *ngIf="dev.observaciones">«{{ dev.observaciones }}»</p>
          </div>

          <!-- rechazada -->
          <div class="caja cerrada mal" *ngIf="dev.estado === 'rechazada'">
            <div class="tic" aria-hidden="true">×</div>
            <h2>El proveedor la rechazó</h2>
            <p>No aceptó la reclamación, así que se cierra sin compensación. La pérdida
            se queda en casa.</p>
            <p class="obs" *ngIf="dev.observaciones">«{{ dev.observaciones }}»</p>
          </div>

          <div class="caja aviso-rol" *ngIf="!esCompras && (dev.estado === 'pendiente' || dev.estado === 'enviada')">
            <p>Esto lo gestiona el <strong>Encargado de Compras</strong>. Aquí solo puedes
            consultarlo.</p>
          </div>
        </aside>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{ toast }}</div>
    </div>
  `,
  styles: [`
    .dvp { max-width: 1500px; margin: 0 auto; padding-bottom: 3rem; }

    .cab { margin-bottom: 1.5rem; }
    .volver { background: transparent; border: none; color: var(--ms-text-muted);
              cursor: pointer; font-size: .85rem; padding: 0 0 .5rem; }
    .volver:hover { color: var(--ms-gold); }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.55rem; color: var(--ms-text);
              display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .9rem; }
    .sep { margin: 0 .35rem; }

    .pill { font-size: .68rem; font-weight: 700; letter-spacing: .05em;
            padding: .25rem .65rem; border-radius: 99px; color: #fff; }
    .e-pendiente { background: #6b7280; }
    .e-enviada   { background: #2563eb; }
    .e-resuelta  { background: #16a34a; }
    .e-rechazada { background: #dc2626; }

    /* ── Los pasos ────────────────────────────────────────────── */
    .pasos { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
             gap: .75rem; margin-bottom: 1.5rem; }
    .paso { display: flex; gap: .75rem; align-items: flex-start;
            border: 1px solid var(--ms-border); border-radius: var(--ms-radius);
            padding: .85rem 1rem; background: var(--ms-bg-card); opacity: .5; }
    .paso.hecho { opacity: 1; border-color: rgba(76,175,80,.4); }
    .paso.ahora { opacity: 1; border-color: var(--ms-gold); background: var(--ms-gold-dim); }
    .paso.malo.hecho { border-color: rgba(229,115,115,.5); }
    .marca { flex: none; width: 26px; height: 26px; border-radius: 50%;
             display: grid; place-items: center; font-size: .8rem; font-weight: 600;
             border: 1px solid var(--ms-border); color: var(--ms-text-muted); }
    .paso.hecho .marca { background: rgba(76,175,80,.15); border-color: var(--ms-green); color: var(--ms-green); }
    .paso.ahora .marca { background: var(--ms-gold); border-color: var(--ms-gold); color: #1a1608; }
    .paso.malo.hecho .marca { background: rgba(229,115,115,.15); border-color: var(--ms-red); color: var(--ms-red); }
    .paso-txt { display: flex; flex-direction: column; gap: .15rem; min-width: 0; }
    .paso-txt strong { font-size: .9rem; color: var(--ms-text); }
    .paso-txt span { font-size: .78rem; color: var(--ms-text-muted); line-height: 1.4; }

    /* ── Reparto ──────────────────────────────────────────────── */
    .reparto { display: grid; grid-template-columns: 1.7fr 1fr; gap: 1.5rem; align-items: start; }
    .cuerpo { display: flex; flex-direction: column; gap: 1.25rem; min-width: 0; }

    .bloque { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
              border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; min-width: 0; }
    .bloque h2 { margin: 0 0 .4rem; font-size: 1rem; color: var(--ms-text); }
    .sub2 { margin: 0 0 1rem; font-size: .85rem; color: var(--ms-text-muted); line-height: 1.55; }
    .sub2 strong { color: var(--ms-text); }
    .sub2 a { color: var(--ms-gold); }

    .tabla-caja { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .88rem; }
    th, td { padding: .65rem .7rem; text-align: left; border-bottom: 1px solid var(--ms-border);
             vertical-align: top; }
    th { font-size: .68rem; text-transform: uppercase; letter-spacing: .07em;
         color: var(--ms-text-muted); font-weight: 600; }
    td { color: var(--ms-text); }
    .num { text-align: right; font-variant-numeric: tabular-nums; }
    th.num { text-align: right; }
    .fuerte { color: var(--ms-gold); font-weight: 600; }
    tfoot td { border-bottom: none; border-top: 1px solid var(--ms-gold);
               color: var(--ms-text-muted); padding-top: .7rem; }
    .ref { font-size: .76rem; color: var(--ms-text-muted); margin-top: .25rem; }
    .vacio { margin: 0; font-size: .85rem; color: var(--ms-text-muted); line-height: 1.6;
             border: 1px dashed var(--ms-border); border-radius: var(--ms-radius-sm);
             padding: .9rem 1.1rem; }
    .vacio strong { color: var(--ms-text); }

    .origen { display: inline-block; font-size: .7rem; padding: .18rem .5rem;
              border-radius: 99px; border: 1px solid currentColor; white-space: nowrap; }
    .o-rma_cliente      { color: #E38B85; background: rgba(227,139,133,.1); }
    .o-recepcion_compra { color: #79C4D2; background: rgba(121,196,210,.1); }

    .ayuda .origenes { list-style: none; margin: 0; padding: 0;
                       display: flex; flex-direction: column; gap: 1rem; }
    .ayuda .origenes li { border-left: 2px solid var(--ms-border); padding-left: 1rem; }
    .ayuda .origenes p { margin: .4rem 0 0; font-size: .85rem;
                         color: var(--ms-text-muted); line-height: 1.55; }
    .ayuda .origenes strong { color: var(--ms-text); }

    /* ── Acción ───────────────────────────────────────────────── */
    .accion { position: sticky; top: 1rem; }
    .caja { background: var(--ms-bg-card); border: 1px solid var(--ms-gold);
            border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; }
    .caja h2 { margin: 0 0 .5rem; font-size: 1.05rem; color: var(--ms-text); }
    .caja h3 { margin: 1.1rem 0 .6rem; font-size: .8rem; text-transform: uppercase;
               letter-spacing: .07em; color: var(--ms-text-muted); font-weight: 600; }
    .caja > p { margin: 0 0 1rem; font-size: .88rem; color: var(--ms-text-muted); line-height: 1.6; }
    .caja > p strong { color: var(--ms-text); }

    .tipos { display: grid; gap: .5rem; margin-bottom: 1rem; }
    .tipo { background: transparent; border: 1px solid var(--ms-border);
            color: var(--ms-text-muted); padding: .65rem; font-size: .88rem;
            border-radius: var(--ms-radius-sm); cursor: pointer; transition: all .15s ease; }
    .tipo:hover { border-color: rgba(255,255,255,.2); color: var(--ms-text); }
    .tipo.on { background: var(--ms-gold-dim); border-color: var(--ms-gold);
               color: var(--ms-gold-light); font-weight: 600; }

    .campo { display: flex; flex-direction: column; gap: .35rem; margin-bottom: .9rem; }
    .etq { font-size: .68rem; text-transform: uppercase; letter-spacing: .08em;
           color: var(--ms-text-muted); }
    .campo input { background: rgba(255,255,255,.04); border: 1px solid var(--ms-border);
                   color: var(--ms-text); padding: .7rem .85rem;
                   border-radius: var(--ms-radius-sm); font-size: .93rem; width: 100%; }
    .campo input:focus { border-color: var(--ms-gold); outline: none; }

    .principal { width: 100%; background: var(--ms-gold); border: none; color: #1a1608;
                 padding: .8rem; font-size: .92rem; font-weight: 600;
                 border-radius: var(--ms-radius-sm); cursor: pointer; }
    .principal:hover:not(:disabled) { background: var(--ms-gold-light); }
    .principal:disabled { opacity: .4; cursor: not-allowed; }

    .secundario { width: 100%; background: transparent; border: 1px solid var(--ms-red);
                  color: var(--ms-red); padding: .7rem; font-size: .88rem;
                  border-radius: var(--ms-radius-sm); cursor: pointer; }
    .secundario:hover:not(:disabled) { background: rgba(229,115,115,.1); }

    .separador { display: flex; align-items: center; gap: .75rem;
                 margin: 1.25rem 0 1rem; color: var(--ms-text-muted); font-size: .78rem; }
    .separador::before, .separador::after { content: ""; flex: 1; height: 1px; background: var(--ms-border); }

    .nota { font-size: .78rem; color: var(--ms-text-muted); line-height: 1.55; margin: .8rem 0 0; }
    .nota strong { color: var(--ms-text); }

    .cerrada { text-align: center; }
    .cerrada.ok  { border-color: var(--ms-green); }
    .cerrada.mal { border-color: var(--ms-red); }
    .tic { width: 46px; height: 46px; margin: 0 auto .75rem; display: grid; place-items: center;
           border-radius: 50%; font-size: 1.4rem; }
    .cerrada.ok .tic  { background: rgba(76,175,80,.15); border: 1px solid var(--ms-green); color: var(--ms-green); }
    .cerrada.mal .tic { background: rgba(229,115,115,.15); border: 1px solid var(--ms-red); color: var(--ms-red); }
    .obs { font-style: italic; font-size: .84rem; color: var(--ms-text-muted); margin-top: .75rem; }
    .ir-oc { display: inline-block; margin-top: .6rem; color: var(--ms-gold); font-size: .85rem; }

    .aviso-rol { border-color: var(--ms-border); }
    .aviso-rol p { margin: 0; }

    @media (max-width: 1024px) {
      .reparto { grid-template-columns: 1fr; }
      .accion { position: static; }
    }
  `]
})
export class DevolucionProveedorDetalleComponent implements OnInit {

  dev: any = null;
  esCompras = false;
  guardando = false;
  resolucion = { tipoResolucion: '', montoReembolso: 0, observaciones: '' };
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private router: Router,
              private api: ApiService, private auth: AuthService) {}

  ngOnInit() {
    this.esCompras = this.auth.hasRol('Administrador') || this.auth.hasRol('Encargado de Compras');
    this.cargar(Number(this.route.snapshot.paramMap.get('id')));
  }

  cargar(id: number) {
    this.api.get<any>('devoluciones-proveedor/' + id).subscribe({
      next: res => { this.dev = res; },
      error: () => this.mostrarToast('No se pudo cargar la devolución', true)
    });
  }

  etiquetaEstado(e: string): string {
    const m: Record<string, string> = {
      pendiente: 'SIN ENVIAR',
      enviada: 'ESPERANDO AL PROVEEDOR',
      resuelta: 'RESUELTA',
      rechazada: 'RECHAZADA POR EL PROVEEDOR'
    };
    return m[e] || e.toUpperCase();
  }

  /**
   * Los tres pasos del recorrido, con el punto donde está.
   *
   * Existe porque un estado suelto no dice nada: «enviada» no explica ni qué se
   * hizo antes ni qué se espera después, que es justo lo que necesita saber
   * quien abre la pantalla.
   */
  pasos(): { titulo: string; detalle: string; estado: string; malo?: boolean }[] {
    const e = this.dev?.estado;
    const hecho = 'hecho', ahora = 'ahora', luego = 'luego';

    const tercero = e === 'rechazada'
      ? { titulo: 'La rechazó', detalle: 'No aceptó la reclamación. Se cierra sin compensación.',
          estado: hecho, malo: true }
      : { titulo: 'Responde el proveedor',
          detalle: e === 'resuelta'
            ? (this.dev?.tipoResolucion === 'reembolso' ? 'Devolvió el dinero.' : 'Repuso la mercancía.')
            : 'Devuelve el dinero o repone la mercancía.',
          estado: e === 'resuelta' ? hecho : luego };

    return [
      { titulo: 'Se agrupa lo defectuoso',
        detalle: 'De recepciones o de devoluciones de clientes.',
        estado: hecho },
      { titulo: 'Se le envía',
        detalle: e === 'pendiente' ? 'Todavía no ha salido de la bodega.' : 'Ya salió hacia el proveedor.',
        estado: e === 'pendiente' ? ahora : hecho },
      tercero
    ];
  }

  totalUnidades(): number {
    return (this.dev?.detalles || []).reduce((s: number, d: any) => s + (d.cantidad || 0), 0);
  }

  faltaMonto(): boolean {
    return this.resolucion.tipoResolucion === 'reembolso'
        && !(this.resolucion.montoReembolso > 0);
  }

  marcarEnviada() {
    this.guardando = true;
    this.api.put<any>(`devoluciones-proveedor/${this.dev.idDevolucionProv}/estado`, { estado: 'enviada' })
      .subscribe({
        next: res => { this.guardando = false; this.dev = res; this.mostrarToast('Marcada como enviada al proveedor'); },
        error: err => { this.guardando = false; this.mostrarToast(err.error?.message || 'No se pudo actualizar', true); }
      });
  }

  resolverDev() {
    if (!this.resolucion.tipoResolucion) {
      this.mostrarToast('Indica qué respondió el proveedor', true);
      return;
    }
    if (this.faltaMonto()) {
      this.mostrarToast('Indica el monto que devuelve', true);
      return;
    }

    this.guardando = true;
    const body: any = {
      tipoResolucion: this.resolucion.tipoResolucion,
      observaciones: this.resolucion.observaciones || null
    };
    if (this.resolucion.tipoResolucion === 'reembolso') {
      body.montoReembolso = this.resolucion.montoReembolso;
    }

    this.api.post<any>(`devoluciones-proveedor/${this.dev.idDevolucionProv}/resolver`, body).subscribe({
      next: res => {
        this.guardando = false;
        this.dev = res;
        this.mostrarToast('Respuesta registrada. Devolución resuelta.');
      },
      error: err => { this.guardando = false; this.mostrarToast(err.error?.message || 'No se pudo resolver', true); }
    });
  }

  rechazar() {
    this.guardando = true;
    this.api.put<any>(`devoluciones-proveedor/${this.dev.idDevolucionProv}/estado`, { estado: 'rechazada' })
      .subscribe({
        next: res => { this.guardando = false; this.dev = res; this.mostrarToast('Registrado: el proveedor la rechazó'); },
        error: err => { this.guardando = false; this.mostrarToast(err.error?.message || 'No se pudo actualizar', true); }
      });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg;
    this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 4000);
  }
}
