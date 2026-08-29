import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { CrudService } from '../../core/services/crud.service';
import { SearchableSelectComponent } from '../../shared/components/searchable-select/searchable-select.component';

interface Detalle {
  idDetalleSd: number;
  idDetallePedido: number;
  productoNombre: string;
  cantidadOriginal: number;
  cantidadDevuelta: number;
  precioUnitario: number | null;
  resultadoInspeccion: string | null;
  observacionInspeccion: string | null;
}

interface Solicitud {
  idSolicitud: number;
  idPedido: number;
  clienteNombre: string;
  motivo: string;
  descripcion: string;
  estado: string;
  fechaSolicitud: string;
  fechaInspeccion: string;
  inspectorNombre: string;
  registradoPor: string;
  detalles: Detalle[];
  reembolso: any;
}

/**
 * Inspeccionar lo que devolvió un cliente, y pagarle lo que corresponda.
 *
 * <p><b>Qué estaba mal (F79).</b> La pantalla enseñaba los datos pero <b>no
 * decía qué hace cada cosa</b>, y las tres decisiones que se toman aquí no son
 * reversibles:
 *
 * <ul>
 *   <li><b>El resultado de cada línea mueve —o no— el stock.</b> Marcar «apto
 *       reventa» <i>devuelve la mercancía al inventario</i> de la bodega que se
 *       elija; «defectuoso» no la devuelve y la deja disponible para reclamársela
 *       al proveedor; «rechazado» no hace nada. Los tres se elegían en un
 *       desplegable idéntico, sin una palabra sobre la diferencia.
 *   <li><b>Una línea inspeccionada no se puede volver a inspeccionar</b> — el
 *       backend lo rechaza—, y eso tampoco se decía.
 *   <li><b>El reembolso tiene tope</b>: el valor de las líneas que la inspección
 *       no rechazó, al precio al que se vendieron. La pantalla dejaba escribir
 *       cualquier importe y el error salía al enviar. Ahora el tope se calcula y
 *       se enseña, y el importe viene propuesto.
 * </ul>
 */
@Component({
  selector: 'app-devolucion-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SearchableSelectComponent],
  template: `
    <div class="crud-container" *ngIf="sol as s">

      <!-- ── Cabecera ────────────────────────────────────────────── -->
      <header class="cab">
        <button class="btn-volver" routerLink="/devoluciones">
          <span class="flecha" aria-hidden="true">←</span> Devoluciones
        </button>
        <h1>
          Devolución #{{ s.idSolicitud }}
          <span class="pill" [ngClass]="'d-' + s.estado">{{ etiquetaEstado(s.estado) }}</span>
        </h1>
        <p class="sub">
          {{ s.clienteNombre }}
          <span class="sep">·</span>
          <a class="enlace" [routerLink]="['/pedidos', s.idPedido]">pedido #{{ s.idPedido }}</a>
          <span class="sep">·</span> pedida el {{ s.fechaSolicitud | date:'dd/MM/yyyy HH:mm' }}
        </p>

        <ol class="ruta">
          <li *ngFor="let p of pasos" [class.hecho]="pasoHecho(p.clave)"
              [class.ahora]="s.estado === p.clave">
            <span class="punto"></span>{{ p.titulo }}
          </li>
        </ol>
      </header>

      <!-- ── Por qué la devuelven ────────────────────────────────── -->
      <section class="bloque">
        <h2>Por qué la devuelven</h2>
        <p class="motivo">{{ etiquetaMotivo(s.motivo) }}</p>
        <p class="desc" *ngIf="s.descripcion">«{{ s.descripcion }}»</p>
        <p class="quien">
          La pidió {{ s.registradoPor }}<span *ngIf="s.inspectorNombre">, la inspeccionó {{ s.inspectorNombre }}
          el {{ s.fechaInspeccion | date:'dd/MM/yyyy HH:mm' }}</span>.
        </p>
      </section>

      <!-- ── Qué llegó ───────────────────────────────────────────── -->
      <section class="bloque">
        <h2>Qué llegó</h2>
        <div class="tabla-scroll">
          <table class="data-table">
            <thead>
              <tr>
                <th>Producto</th>
                <th class="num">Devuelve</th>
                <th class="num">Precio</th>
                <th class="num">Valor</th>
                <th>Resultado</th>
                <th>Observación</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let d of s.detalles">
                <td>{{ d.productoNombre }}</td>
                <td class="num">{{ d.cantidadDevuelta }} de {{ d.cantidadOriginal }}</td>
                <td class="num">{{ d.precioUnitario != null ? ('$' + (d.precioUnitario | number:'1.2-2')) : '—' }}</td>
                <td class="num">{{ d.precioUnitario != null ? ('$' + (valorLinea(d) | number:'1.2-2')) : '—' }}</td>
                <td>
                  <span class="res" *ngIf="d.resultadoInspeccion"
                        [ngClass]="'r-' + d.resultadoInspeccion">{{ etiquetaResultado(d.resultadoInspeccion) }}</span>
                  <span class="pendiente" *ngIf="!d.resultadoInspeccion">Sin mirar</span>
                </td>
                <td class="obs">{{ d.observacionInspeccion || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- ── Aún no se ha mirado ─────────────────────────────────── -->
      <section class="bloque arranque" *ngIf="s.estado === 'solicitada'">
        <h2>Todavía nadie ha revisado la mercancía</h2>
        <p class="sub" *ngIf="esBodega">
          Al empezar la inspección la solicitud queda a tu nombre y pasa a
          <strong>en inspección</strong>. Después decides línea por línea qué se
          hace con cada prenda.
        </p>
        <p class="sub" *ngIf="!esBodega">
          La revisa <strong>Bodega</strong>: quien pide la devolución no es quien
          juzga el estado de la mercancía.
        </p>
        <button class="btn-save" *ngIf="esBodega" (click)="iniciarInspeccion()">
          Empezar la inspección
        </button>
      </section>

      <!-- ── Inspección ──────────────────────────────────────────── -->
      <section class="bloque" *ngIf="s.estado === 'en_inspeccion' && esBodega && hayPendientes()">
        <h2>Revisar lo que llegó</h2>
        <p class="sub">
          Una línea revisada <strong>no se puede volver a revisar</strong>. Puedes
          hacerlas de una en una: lo que dejes sin marcar se queda pendiente.
        </p>

        <div class="lineas">
          <ng-container *ngFor="let d of s.detalles">
          <article class="linea" *ngIf="!d.resultadoInspeccion">
            <div class="l-cab">
              <strong>{{ d.productoNombre }}</strong>
              <span class="l-cant">{{ d.cantidadDevuelta }}
                {{ d.cantidadDevuelta === 1 ? 'unidad' : 'unidades' }}</span>
            </div>

            <div class="opciones">
              <!-- Una sola fuente de clases: [ngClass] y [class.x] sobre el
                   mismo elemento se pisan segun el orden de evaluacion. -->
              <button type="button" class="op" *ngFor="let r of resultados"
                      [ngClass]="clasesOpcion(d, r.valor)"
                      (click)="elegirResultado(d, r.valor)">
                <strong>{{ r.titulo }}</strong>
                <span>{{ r.efecto }}</span>
              </button>
            </div>

            <input class="obs-caja" [(ngModel)]="inspObs[d.idDetalleSd]"
                   placeholder="Qué se le ve, si hace falta anotarlo…"/>
          </article>
          </ng-container>
        </div>

        <div class="bodega-destino" [class.hace-falta]="algoVuelveAlStock()">
          <label>Bodega a la que vuelve lo que esté apto</label>
          <app-searchable-select
            [items]="bodegas"
            labelKey="nombre"
            valueKey="idBodega"
            placeholder="Escribe el nombre de la bodega…"
            [(ngModel)]="idBodega"
            [ngModelOptions]="{ standalone: true }"/>
          <p class="pista" *ngIf="algoVuelveAlStock()">
            Vas a devolver <strong>{{ unidadesQueVuelven() }}</strong>
            {{ unidadesQueVuelven() === 1 ? 'unidad' : 'unidades' }} al stock de
            esa bodega. Eso sí mueve inventario.
          </p>
          <p class="pista" *ngIf="!algoVuelveAlStock()">
            Nada de lo marcado vuelve al stock, pero el dato se pide igual para
            dejar constancia de dónde se revisó.
          </p>
        </div>

        <div class="acciones-pie">
          <button class="btn-save" (click)="guardarInspeccion()" [disabled]="guardando || !inspeccionValida()">
            {{ guardando ? 'Guardando…' : 'Guardar la revisión' }}
          </button>
          <span class="falta" *ngIf="!inspeccionValida()">{{ queFaltaInspeccion() }}</span>
        </div>
      </section>

      <!-- ── Cerrada ─────────────────────────────────────────────── -->
      <section class="bloque cerrada" *ngIf="s.estado === 'completada' || s.estado === 'rechazada'">
        <h2>{{ s.estado === 'rechazada' ? 'No se aceptó nada' : 'Revisión terminada' }}</h2>
        <p class="sub" *ngIf="s.estado === 'rechazada'">
          Todas las líneas se rechazaron, así que <strong>no hay reembolso</strong>:
          la mercancía no se aceptó y el stock no se tocó.
        </p>
        <p class="sub" *ngIf="s.estado === 'completada'">
          <span *ngIf="unidadesDevueltasAlStock() > 0">
            Volvieron <strong>{{ unidadesDevueltasAlStock() }}</strong>
            {{ unidadesDevueltasAlStock() === 1 ? 'unidad' : 'unidades' }} al inventario.
          </span>
          <span *ngIf="unidadesDefectuosas() > 0">
            Hay <strong>{{ unidadesDefectuosas() }}</strong> defectuosas, que se le
            pueden reclamar al proveedor.
          </span>
        </p>
        <a class="ir" routerLink="/devoluciones-proveedor/items" *ngIf="unidadesDefectuosas() > 0 && esBodega">
          Ver los artículos defectuosos
        </a>
      </section>

      <!-- ── Reembolso ───────────────────────────────────────────── -->
      <section class="bloque" *ngIf="s.estado === 'completada' && !s.reembolso && esPedidos">
        <h2>Devolverle el dinero</h2>
        <p class="sub">
          Como máximo <strong>\${{ topeReembolso() | number:'1.2-2' }}</strong>: es
          lo que valen las líneas que no se rechazaron, al precio al que se
          vendieron. Por debajo se puede, por encima lo rechaza el servidor.
        </p>

        <div class="reembolso">
          <div class="campo">
            <label>Cuánto</label>
            <div class="monto">
              <span class="moneda">$</span>
              <input type="number" step="0.01" min="0.01" [max]="topeReembolso()"
                     [(ngModel)]="reembolso.monto" (change)="acotarMonto()"/>
              <button type="button" class="todo" (click)="reembolso.monto = topeReembolso()"
                      *ngIf="reembolso.monto !== topeReembolso()">Todo</button>
            </div>
          </div>

          <div class="campo">
            <label>Cómo</label>
            <div class="metodos">
              <button type="button" class="met" *ngFor="let m of metodos"
                      [class.on]="reembolso.metodo === m.valor"
                      (click)="reembolso.metodo = m.valor">{{ m.titulo }}</button>
            </div>
          </div>
        </div>

        <input class="obs-caja" [(ngModel)]="reembolso.observaciones"
               placeholder="Observaciones (opcional)"/>

        <div class="acciones-pie">
          <button class="btn-save" (click)="registrarReembolso()" [disabled]="guardando || !reembolsoValido()">
            {{ guardando ? 'Registrando…' : 'Registrar el reembolso' }}
          </button>
          <span class="falta" *ngIf="!reembolsoValido()">{{ queFaltaReembolso() }}</span>
        </div>
      </section>

      <section class="bloque hecho-ok" *ngIf="s.reembolso">
        <h2>Reembolso registrado</h2>
        <div class="datos">
          <div class="dato">
            <label>Monto</label>
            <span class="cifra">\${{ s.reembolso.monto | number:'1.2-2' }}</span>
          </div>
          <div class="dato">
            <label>Método</label>
            <span>{{ etiquetaMetodo(s.reembolso.metodo) }}</span>
          </div>
          <div class="dato">
            <label>Fecha</label>
            <span>{{ s.reembolso.fechaReembolso | date:'dd/MM/yyyy HH:mm' }}</span>
          </div>
        </div>
        <p class="desc" *ngIf="s.reembolso.observaciones">«{{ s.reembolso.observaciones }}»</p>
      </section>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{ toast }}</div>
    </div>

    <div class="spinner" *ngIf="!sol">Cargando devolución…</div>
  `,
  styles: [`
    .cab { margin-bottom: 1.5rem; }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.6rem; color: var(--ms-text);
              display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .9rem; line-height: 1.6; }
    .sep { margin: 0 .35rem; }
    .enlace { color: var(--ms-gold); text-decoration: none; }
    .enlace:hover { text-decoration: underline; }

    .pill { font-size: .68rem; font-weight: 700; letter-spacing: .06em;
            padding: .25rem .6rem; border-radius: 99px; color: #fff;
            text-transform: uppercase; }
    .d-solicitada    { background: #64748b; }
    .d-en_inspeccion { background: #d97706; }
    .d-completada    { background: #16a34a; }
    .d-rechazada     { background: #dc2626; }

    .ruta { list-style: none; display: flex; gap: .2rem; margin: .9rem 0 0; padding: 0;
            flex-wrap: wrap; align-items: center; }
    .ruta li { display: flex; align-items: center; gap: .35rem; font-size: .74rem;
               color: rgba(255,255,255,0.28); padding-right: .55rem; }
    .ruta li:not(:last-child)::after { content: '→'; margin-left: .35rem;
                                       color: rgba(255,255,255,0.15); }
    .ruta .punto { width: 7px; height: 7px; border-radius: 50%;
                   background: rgba(255,255,255,0.18); }
    .ruta li.hecho { color: rgba(255,255,255,0.55); }
    .ruta li.hecho .punto { background: #4ade80; }
    .ruta li.ahora { color: var(--ms-gold-light); font-weight: 600; }
    .ruta li.ahora .punto { background: var(--ms-gold); box-shadow: 0 0 0 3px var(--ms-gold-dim); }

    /* ── Bloques ───────────────────────────────────────────────── */
    .bloque { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
              border-radius: var(--ms-radius); padding: 1.35rem 1.5rem;
              margin-bottom: 1.25rem; }
    .bloque h2 { margin: 0 0 .7rem; font-size: 1.02rem; color: var(--ms-text); }
    .bloque .sub { margin: 0 0 1rem; max-width: 78ch; }
    .bloque .sub strong { color: rgba(255,255,255,0.85); }
    .motivo { margin: 0; font-size: 1rem; color: var(--ms-text); }
    .desc { margin: .5rem 0 0; font-size: .9rem; color: var(--ms-text-muted);
            font-style: italic; }
    .quien { margin: .7rem 0 0; font-size: .8rem; color: rgba(255,255,255,0.35); }

    .arranque { border-left: 3px solid #d97706; }
    .cerrada { border-left: 3px solid #16a34a; }
    .hecho-ok { border-left: 3px solid var(--ms-gold); }

    .tabla-scroll { overflow-x: auto; }
    .num { text-align: right; }
    .obs { color: var(--ms-text-muted); font-size: .85rem; }
    .pendiente { color: rgba(255,255,255,0.35); font-style: italic; font-size: .85rem; }
    .res { font-size: .7rem; font-weight: 700; letter-spacing: .04em;
           text-transform: uppercase; padding: .18rem .5rem; border-radius: 4px; }
    .r-apto_reventa { background: rgba(22,163,74,.18); color: #86efac; }
    .r-defectuoso   { background: rgba(217,119,6,.18); color: #fcd34d; }
    .r-rechazado    { background: rgba(220,38,38,.18); color: #fca5a5; }

    /* ── Revisión línea por línea ──────────────────────────────── */
    .lineas { display: flex; flex-direction: column; gap: 1rem; }
    .linea { border: 1px solid var(--ms-border); border-radius: var(--ms-radius-sm);
             padding: 1rem 1.1rem; background: rgba(255,255,255,0.02); }
    .l-cab { display: flex; justify-content: space-between; align-items: baseline;
             gap: 1rem; margin-bottom: .8rem; flex-wrap: wrap; }
    .l-cab strong { color: var(--ms-text); font-size: .95rem; }
    .l-cant { color: var(--ms-text-muted); font-size: .84rem; }

    .opciones { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
                gap: .5rem; margin-bottom: .7rem; }
    .op { text-align: left; background: rgba(255,255,255,0.03);
          border: 1px solid var(--ms-border); border-radius: var(--ms-radius-sm);
          padding: .6rem .8rem; cursor: pointer; display: flex; flex-direction: column;
          gap: .15rem; transition: all .15s ease; font-family: inherit; }
    .op:hover { border-color: rgba(255,255,255,0.25); }
    .op strong { font-size: .88rem; color: rgba(255,255,255,0.88); }
    .op span { font-size: .74rem; color: var(--ms-text-muted); line-height: 1.45; }
    .op.on.op-apto_reventa { border-color: #16a34a; background: rgba(22,163,74,.12); }
    .op.on.op-apto_reventa strong { color: #86efac; }
    .op.on.op-defectuoso { border-color: #d97706; background: rgba(217,119,6,.12); }
    .op.on.op-defectuoso strong { color: #fcd34d; }
    .op.on.op-rechazado { border-color: #dc2626; background: rgba(220,38,38,.12); }
    .op.on.op-rechazado strong { color: #fca5a5; }

    .obs-caja { width: 100%; box-sizing: border-box; padding: .6rem .9rem;
                font-family: inherit; font-size: .86rem;
                background: rgba(255,255,255,0.04); color: var(--ms-text);
                border: 1px solid var(--ms-border); border-radius: var(--ms-radius-sm); }
    .obs-caja::placeholder { color: var(--ms-text-muted); }

    .bodega-destino { margin-top: 1.2rem; padding-top: 1.2rem;
                      border-top: 1px solid var(--ms-border); }
    .bodega-destino label { display: block; font-size: .8rem; font-weight: 600;
                            color: var(--ms-text); margin-bottom: .4rem; }
    .bodega-destino.hace-falta label { color: var(--ms-gold-light); }
    .pista { margin: .5rem 0 0; font-size: .78rem; color: var(--ms-text-muted);
             line-height: 1.6; }
    .pista strong { color: var(--ms-gold-light); }
    .bodega-destino:has(.search-select.open) { position: relative; z-index: 60; }

    /* ── Reembolso ─────────────────────────────────────────────── */
    .reembolso { display: grid; grid-template-columns: minmax(220px, 1fr) 2fr;
                 gap: 1.25rem; margin-bottom: .9rem; align-items: start; }
    .campo label { display: block; font-size: .8rem; font-weight: 600;
                   color: var(--ms-text); margin-bottom: .4rem; }
    .monto { display: flex; align-items: center; gap: .4rem; }
    .moneda { color: var(--ms-text-muted); font-size: 1rem; }
    .monto input { width: 130px; padding: .55rem .7rem; font-size: 1rem; font-weight: 600;
                   font-family: inherit; background: rgba(255,255,255,0.04);
                   color: var(--ms-text); border: 1px solid var(--ms-border);
                   border-radius: var(--ms-radius-sm); }
    .todo { background: transparent; border: 1px solid rgba(201,168,76,.5);
            color: var(--ms-gold-light); padding: .35rem .7rem; border-radius: 99px;
            font-size: .78rem; cursor: pointer; font-family: inherit; }
    .todo:hover { background: var(--ms-gold-dim); }
    .metodos { display: flex; gap: .4rem; flex-wrap: wrap; }
    .met { flex: 1; min-width: 120px; background: rgba(255,255,255,0.03);
           border: 1px solid var(--ms-border); color: var(--ms-text-muted);
           padding: .55rem .5rem; border-radius: var(--ms-radius-sm);
           font-size: .84rem; cursor: pointer; font-family: inherit;
           transition: all .15s ease; }
    .met:hover { border-color: rgba(255,255,255,0.25); color: var(--ms-text); }
    .met.on { background: var(--ms-gold-dim); border-color: var(--ms-gold);
              color: var(--ms-gold-light); font-weight: 600; }

    .datos { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
             gap: 1rem; }
    .dato { display: flex; flex-direction: column; gap: .25rem; }
    .dato label { font-size: .7rem; text-transform: uppercase; letter-spacing: .05em;
                  color: var(--ms-text-muted); }
    .cifra { color: var(--ms-gold-light); font-size: 1.15rem; font-weight: 600; }

    .acciones-pie { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap;
                    margin-top: 1rem; }
    .falta { font-size: .8rem; color: #fbbf24; }
    .ir { display: inline-block; background: rgba(255,255,255,0.04);
          border: 1px solid var(--ms-border); color: var(--ms-text-muted);
          padding: .5rem 1rem; border-radius: var(--ms-radius-sm);
          font-size: .86rem; text-decoration: none; }
    .ir:hover { border-color: var(--ms-gold); color: var(--ms-gold); }

    @media (max-width: 760px) {
      .reembolso { grid-template-columns: 1fr; }
      .cab h1 { font-size: 1.3rem; }
    }
  `]
})
export class DevolucionDetalleComponent implements OnInit {
  sol: Solicitud | null = null;
  bodegas: any[] = [];
  idBodega: number | null = null;
  inspResults: Record<number, string> = {};
  inspObs: Record<number, string> = {};
  reembolso = { monto: 0, metodo: '', observaciones: '' };
  guardando = false;
  esBodega = false;
  esPedidos = false;
  toast = '';
  toastError = false;

  /** Qué hace cada resultado. Es la información que faltaba (F79). */
  readonly resultados = [
    { valor: 'apto_reventa', titulo: 'Apto para reventa', efecto: 'Vuelve al stock de la bodega' },
    { valor: 'defectuoso',   titulo: 'Defectuoso',        efecto: 'No vuelve al stock; se le puede reclamar al proveedor' },
    { valor: 'rechazado',    titulo: 'Rechazado',         efecto: 'No se acepta la devolución de esta línea' }
  ];

  readonly metodos = [
    { valor: 'nota_credito',  titulo: 'Nota de crédito' },
    { valor: 'transferencia', titulo: 'Transferencia' },
    { valor: 'efectivo',      titulo: 'Efectivo' }
  ];

  /** El camino de una devolución. `rechazada` no está: es salirse de él. */
  readonly pasos = [
    { clave: 'solicitada',    titulo: 'Solicitada' },
    { clave: 'en_inspeccion', titulo: 'En inspección' },
    { clave: 'completada',    titulo: 'Completada' }
  ];

  constructor(private route: ActivatedRoute, private api: ApiService,
              private auth: AuthService, private crud: CrudService) {}

  ngOnInit() {
    this.esBodega = this.auth.hasRol('Administrador') || this.auth.hasRol('Operador de Bodega');
    this.esPedidos = this.auth.hasRol('Administrador') || this.auth.hasRol('Operador de Pedidos');
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
    this.crud.listar<any>('bodegas', { page: 0, size: 200, estado: 'activo' }).subscribe({
      next: (res: any) => { this.bodegas = res.content ?? []; }
    });
  }

  cargar(id: number) {
    this.api.get<Solicitud>('devoluciones/' + id).subscribe({
      next: (res: any) => {
        this.sol = res;
        // El importe viene propuesto al tope: es el caso normal —se devuelve lo
        // que valía— y evita teclear una cifra que el servidor va a rechazar.
        if (res?.estado === 'completada' && !res?.reembolso && !this.reembolso.monto) {
          this.reembolso.monto = this.topeReembolso();
        }
      },
      error: () => { this.mostrarToast('Error al cargar', true); }
    });
  }

  // ── Etiquetas ───────────────────────────────────────────────────────────
  etiquetaEstado(e: string): string {
    switch (e) {
      case 'solicitada': return 'Solicitada';
      case 'en_inspeccion': return 'En inspección';
      case 'completada': return 'Completada';
      case 'rechazada': return 'Rechazada';
      default: return e || '';
    }
  }

  etiquetaMotivo(m: string): string {
    switch (m) {
      case 'producto_defectuoso': return 'Producto defectuoso';
      case 'talla_incorrecta': return 'Talla incorrecta';
      case 'no_esperado': return 'No es lo esperado';
      case 'cambio_opinion': return 'Cambio de opinión';
      case 'producto_incompleto': return 'Producto incompleto';
      case 'otro': return 'Otro';
      default: return m || '';
    }
  }

  etiquetaResultado(r: string): string {
    return this.resultados.find(x => x.valor === r)?.titulo || r;
  }

  etiquetaMetodo(m: string): string {
    return this.metodos.find(x => x.valor === m)?.titulo || m;
  }

  pasoHecho(clave: string): boolean {
    if (!this.sol) { return false; }
    if (this.sol.estado === 'rechazada') { return clave !== 'completada'; }
    const iActual = this.pasos.findIndex(p => p.clave === this.sol!.estado);
    const iPaso = this.pasos.findIndex(p => p.clave === clave);
    return iActual >= 0 && iPaso <= iActual;
  }

  // ── Cuentas ─────────────────────────────────────────────────────────────
  valorLinea(d: Detalle): number {
    return (d.precioUnitario ?? 0) * (d.cantidadDevuelta ?? 0);
  }

  /**
   * El tope del reembolso, con la MISMA regla que el servidor: la suma de las
   * líneas que la inspección no rechazó, al precio al que se vendieron.
   *
   * <p>Si esta cuenta y la del backend se separan, la pantalla vuelve a ofrecer
   * un importe que el servidor rechaza — que es el defecto que se arregló.
   */
  topeReembolso(): number {
    if (!this.sol) { return 0; }
    return this.sol.detalles
      .filter(d => d.resultadoInspeccion !== 'rechazado')
      .reduce((s, d) => s + this.valorLinea(d), 0);
  }

  hayPendientes(): boolean {
    return !!this.sol && this.sol.detalles.some(d => !d.resultadoInspeccion);
  }

  /** Las clases de una opcion: su color y si esta elegida. */
  clasesOpcion(d: Detalle, valor: string): string {
    return 'op-' + valor + (this.inspResults[d.idDetalleSd] === valor ? ' on' : '');
  }

  elegirResultado(d: Detalle, valor: string) {
    this.inspResults[d.idDetalleSd] = this.inspResults[d.idDetalleSd] === valor ? '' : valor;
  }

  private marcadas(): Detalle[] {
    if (!this.sol) { return []; }
    return this.sol.detalles.filter(d => !d.resultadoInspeccion && this.inspResults[d.idDetalleSd]);
  }

  algoVuelveAlStock(): boolean {
    return this.marcadas().some(d => this.inspResults[d.idDetalleSd] === 'apto_reventa');
  }

  unidadesQueVuelven(): number {
    return this.marcadas()
      .filter(d => this.inspResults[d.idDetalleSd] === 'apto_reventa')
      .reduce((s, d) => s + d.cantidadDevuelta, 0);
  }

  unidadesDevueltasAlStock(): number {
    if (!this.sol) { return 0; }
    return this.sol.detalles.filter(d => d.resultadoInspeccion === 'apto_reventa')
      .reduce((s, d) => s + d.cantidadDevuelta, 0);
  }

  unidadesDefectuosas(): number {
    if (!this.sol) { return 0; }
    return this.sol.detalles.filter(d => d.resultadoInspeccion === 'defectuoso')
      .reduce((s, d) => s + d.cantidadDevuelta, 0);
  }

  // ── Validaciones ────────────────────────────────────────────────────────
  inspeccionValida(): boolean {
    return this.marcadas().length > 0 && !!this.idBodega;
  }

  queFaltaInspeccion(): string {
    if (this.marcadas().length === 0 && !this.idBodega) {
      return 'Falta decidir alguna línea y elegir la bodega.';
    }
    if (this.marcadas().length === 0) { return 'Decide al menos una línea.'; }
    return 'Falta la bodega.';
  }

  acotarMonto() {
    const tope = this.topeReembolso();
    const v = Number(this.reembolso.monto);
    this.reembolso.monto = isNaN(v) ? 0 : Math.max(0, Math.min(tope, v));
  }

  reembolsoValido(): boolean {
    const v = Number(this.reembolso.monto);
    return !!this.reembolso.metodo && v > 0 && v <= this.topeReembolso();
  }

  queFaltaReembolso(): string {
    const v = Number(this.reembolso.monto);
    if (!v || v <= 0) { return 'Pon cuánto se le devuelve.'; }
    if (v > this.topeReembolso()) { return 'Pasa del máximo.'; }
    return 'Falta el método.';
  }

  // ── Acciones ────────────────────────────────────────────────────────────
  iniciarInspeccion() {
    if (!this.sol) { return; }
    this.api.put<any>('devoluciones/' + this.sol.idSolicitud + '/iniciar-inspeccion', {}).subscribe({
      next: (res: any) => { this.sol = res; this.mostrarToast('Inspección iniciada'); },
      error: (err: any) => { this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  guardarInspeccion() {
    if (!this.sol || !this.inspeccionValida()) { return; }
    const items = this.marcadas().map(d => ({
      idDetalleSd: d.idDetalleSd,
      resultadoInspeccion: this.inspResults[d.idDetalleSd],
      observacionInspeccion: this.inspObs[d.idDetalleSd] || null
    }));
    this.guardando = true;
    this.api.put<any>('devoluciones/' + this.sol.idSolicitud + '/inspeccionar',
                      { idBodega: Number(this.idBodega), items }).subscribe({
      next: (res: any) => {
        this.sol = res;
        this.guardando = false;
        this.inspResults = {};
        this.inspObs = {};
        if (res?.estado === 'completada' && !res?.reembolso) {
          this.reembolso.monto = this.topeReembolso();
        }
        this.mostrarToast('Revisión guardada');
      },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  registrarReembolso() {
    if (!this.sol || !this.reembolsoValido()) { return; }
    this.guardando = true;
    this.api.post<any>('devoluciones/' + this.sol.idSolicitud + '/reembolso', {
      monto: Number(this.reembolso.monto),
      metodo: this.reembolso.metodo,
      observaciones: this.reembolso.observaciones || null
    }).subscribe({
      next: (res: any) => { this.sol = res; this.guardando = false; this.mostrarToast('Reembolso registrado'); },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
