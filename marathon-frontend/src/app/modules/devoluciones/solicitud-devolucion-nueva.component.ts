import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ApiService } from '../../core/services/api.service';

interface LineaPedido {
  idDetalle: number;
  productoNombre: string;
  cantidad: number;
  /** Lo que ya se llevó otra solicitud de este mismo pedido. */
  yaDevuelto: number;
  /** Lo que queda: comprado − ya devuelto. Es el tope real. */
  disponible: number;
  selected: boolean;
  cantidadDevolver: number;
}

interface Pedido {
  numeroPedido: string;
  clienteNombre: string;
  estado: string;
  fechaPedido: string;
  total: number;
}

/**
 * Pedir la devolución de un pedido entregado.
 *
 * <p><b>Qué estaba mal (F78).</b> La pantalla dejaba rellenarlo todo y fallaba
 * al enviar, en dos casos distintos y por la misma razón: <b>enseñaba menos de
 * lo que la base exige</b>.
 *
 * <ol>
 *   <li><b>El pedido tiene que estar entregado.</b> No lo decía en ninguna
 *       parte. Sobre un pedido en {@code procesado} se podía elegir motivo,
 *       marcar líneas y pulsar «Registrar», para recibir un error al final.
 *   <li><b>Lo devuelto antes gasta cupo.</b> La tabla solo enseñaba «cantidad
 *       comprada» y dejaba escribir hasta ahí, aunque una solicitud anterior ya
 *       se hubiera llevado media línea. El backend lo rechaza —compara contra el
 *       acumulado—, pero la pantalla lo descubría después de escribirlo.
 * </ol>
 *
 * <p>Ahora las dos cosas se ven antes de empezar: si el pedido no se puede
 * devolver, el formulario no aparece; y cada línea dice cuánto se compró, cuánto
 * se devolvió ya y cuánto queda, con el tope puesto en lo que queda.
 */
@Component({
  selector: 'app-solicitud-devolucion-nueva',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container" *ngIf="cargado">

      <!-- ── Cabecera ────────────────────────────────────────────── -->
      <header class="cab">
        <div>
          <button class="btn-volver" [routerLink]="['/pedidos', idPedido]">
            <span class="flecha" aria-hidden="true">←</span> Pedido
          </button>
          <h1>
            Devolver del {{ pedido?.numeroPedido || ('pedido #' + idPedido) }}
            <span class="pill" [ngClass]="'e-' + (pedido?.estado || '')">{{ pedido?.estado }}</span>
          </h1>
          <p class="sub" *ngIf="pedido">
            {{ pedido.clienteNombre }}
            <span class="sep">·</span> pedido del {{ pedido.fechaPedido | date:'dd/MM/yyyy' }}
            <span class="sep">·</span> \${{ pedido.total | number:'1.2-2' }}
          </p>
        </div>
      </header>

      <!-- ── No se puede: se dice antes, no al enviar ─────────────── -->
      <div class="bloqueo" *ngIf="!sePuedeDevolver()">
        <h2>Este pedido todavía no se puede devolver</h2>
        <p>
          Una devolución solo se pide sobre un pedido <strong>entregado</strong>,
          porque hasta que el cliente no lo tiene no hay nada que devolver. Este
          está en <strong>{{ pedido?.estado }}</strong>.
        </p>
        <p class="camino" *ngIf="pedido?.estado === 'procesado'">
          Le faltan el picking, el empaque y marcarlo como entregado.
        </p>
        <p class="camino" *ngIf="pedido?.estado === 'enviado'">
          Ya salió del almacén: cuando el cliente lo reciba, márcalo como
          entregado en la ficha del pedido y vuelve aquí.
        </p>
        <p class="camino" *ngIf="pedido?.estado === 'pendiente'">
          Ni siquiera se ha procesado: no hay unidades reservadas todavía.
        </p>
        <p class="camino" *ngIf="pedido?.estado === 'anulado'">
          Un pedido anulado no llegó a entregarse, así que no hay devolución que
          pedir.
        </p>
        <a class="ir" [routerLink]="['/pedidos', idPedido]">Ver el pedido</a>
      </div>

      <ng-container *ngIf="sePuedeDevolver()">

        <!-- ── Nada que devolver ─────────────────────────────────── -->
        <div class="bloqueo" *ngIf="!quedaAlgo()">
          <h2>De este pedido ya se devolvió todo</h2>
          <p>
            Las {{ lineas.length }} líneas están cubiertas por solicitudes
            anteriores. No queda ninguna unidad por devolver.
          </p>
          <a class="ir" routerLink="/devoluciones">Ver las devoluciones</a>
        </div>

        <ng-container *ngIf="quedaAlgo()">

          <!-- ── Motivo ──────────────────────────────────────────── -->
          <section class="bloque">
            <h2>Por qué se devuelve</h2>
            <div class="motivos">
              <button type="button" class="mot" *ngFor="let m of motivos"
                      [class.on]="motivo === m.valor" (click)="motivo = m.valor">
                <strong>{{ m.titulo }}</strong>
                <span>{{ m.pista }}</span>
              </button>
            </div>
            <p class="nota-defecto" *ngIf="motivo === 'producto_defectuoso'">
              Al inspeccionarla en bodega, una prenda marcada como defectuosa
              puede pasar a <strong>devolución al proveedor</strong>. Es la única
              causa que sigue ese camino.
            </p>

            <label class="etq" for="desc">Descripción <span class="opc">opcional</span></label>
            <textarea id="desc" [(ngModel)]="descripcion" rows="2"
                      placeholder="Lo que contó el cliente, o lo que se ve en la prenda…"></textarea>
          </section>

          <!-- ── Qué se devuelve ─────────────────────────────────── -->
          <section class="bloque">
            <h2>Qué se devuelve</h2>
            <p class="sub" *ngIf="hayDevueltasAntes()">
              Este pedido ya tiene devoluciones. Cada línea dice lo que queda por
              devolver, que es el tope.
            </p>

            <div class="tabla-scroll">
              <table class="data-table">
                <thead>
                  <tr>
                    <th class="col-check"></th>
                    <th>Producto</th>
                    <th class="num">Comprado</th>
                    <th class="num" *ngIf="hayDevueltasAntes()">Ya devuelto</th>
                    <th class="num">Queda</th>
                    <th class="num">A devolver</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let l of lineas" [class.agotada]="l.disponible === 0"
                      [class.marcada]="l.selected">
                    <td class="col-check">
                      <input type="checkbox" [(ngModel)]="l.selected"
                             [disabled]="l.disponible === 0" (change)="alMarcar(l)"/>
                    </td>
                    <td>{{ l.productoNombre }}</td>
                    <td class="num">{{ l.cantidad }}</td>
                    <td class="num" *ngIf="hayDevueltasAntes()">
                      <span *ngIf="l.yaDevuelto">{{ l.yaDevuelto }}</span>
                      <span class="cero" *ngIf="!l.yaDevuelto">—</span>
                    </td>
                    <td class="num">
                      <span *ngIf="l.disponible > 0">{{ l.disponible }}</span>
                      <span class="cero" *ngIf="l.disponible === 0">nada</span>
                    </td>
                    <td class="num">
                      <div class="contador" *ngIf="l.disponible > 0">
                        <button type="button" class="paso" (click)="sumar(l, -1)"
                                [disabled]="!l.selected || l.cantidadDevolver <= 1">−</button>
                        <input type="number" [min]="1" [max]="l.disponible"
                               [(ngModel)]="l.cantidadDevolver" [disabled]="!l.selected"
                               (change)="acotar(l)" class="cifra"/>
                        <button type="button" class="paso" (click)="sumar(l, 1)"
                                [disabled]="!l.selected || l.cantidadDevolver >= l.disponible">+</button>
                      </div>
                      <span class="cero" *ngIf="l.disponible === 0">—</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <!-- ── Cierre ──────────────────────────────────────────── -->
          <section class="bloque cierre">
            <div class="resumen">
              <strong>{{ unidadesADevolver() }}</strong>
              {{ unidadesADevolver() === 1 ? 'unidad' : 'unidades' }}
              de <strong>{{ lineasMarcadas() }}</strong>
              {{ lineasMarcadas() === 1 ? 'línea' : 'líneas' }}
            </div>
            <p class="que-pasa">
              Esto registra una <strong>solicitud</strong>: no devuelve dinero ni
              mueve stock todavía. Bodega inspecciona lo que llegue y, según el
              resultado, se emite el reembolso.
            </p>
            <div class="acciones-pie">
              <button class="btn-save" (click)="enviar()" [disabled]="guardando || !formValido()">
                {{ guardando ? 'Registrando…' : 'Registrar solicitud' }}
              </button>
              <span class="falta" *ngIf="!formValido()">{{ queFalta() }}</span>
            </div>
          </section>
        </ng-container>
      </ng-container>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{ toast }}</div>
    </div>

    <div class="spinner" *ngIf="!cargado">Cargando pedido…</div>
  `,
  styles: [`
    .cab { margin-bottom: 1.5rem; }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.6rem; color: var(--ms-text);
              display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .92rem; }
    .sep { margin: 0 .35rem; }

    .pill { font-size: .68rem; font-weight: 700; letter-spacing: .06em;
            padding: .25rem .6rem; border-radius: 99px; color: #fff;
            text-transform: uppercase; }
    .e-pendiente { background: #64748b; }
    .e-procesado { background: #2563eb; }
    .e-enviado   { background: #7c3aed; }
    .e-entregado { background: #16a34a; }
    .e-anulado   { background: #dc2626; }

    /* ── Cuando no se puede ────────────────────────────────────── */
    .bloqueo { background: rgba(255,255,255,0.03); border: 1px solid var(--ms-border);
               border-left: 3px solid #d97706; border-radius: var(--ms-radius);
               padding: 1.5rem 1.6rem; max-width: 70ch; }
    .bloqueo h2 { margin: 0 0 .6rem; font-size: 1.1rem; color: var(--ms-text); }
    .bloqueo p { margin: 0 0 .6rem; font-size: .9rem; line-height: 1.65;
                 color: var(--ms-text-muted); }
    .bloqueo strong { color: rgba(255,255,255,0.88); }
    .camino { font-size: .85rem !important; }
    .ir { display: inline-block; margin-top: .5rem; background: rgba(255,255,255,0.04);
          border: 1px solid var(--ms-border); color: var(--ms-text-muted);
          padding: .5rem 1rem; border-radius: var(--ms-radius-sm);
          font-size: .86rem; text-decoration: none; }
    .ir:hover { border-color: var(--ms-gold); color: var(--ms-gold); }

    /* ── Bloques ───────────────────────────────────────────────── */
    .bloque { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
              border-radius: var(--ms-radius); padding: 1.35rem 1.5rem;
              margin-bottom: 1.25rem; }
    .bloque h2 { margin: 0 0 1rem; font-size: 1.02rem; color: var(--ms-text); }
    .bloque .sub { margin: -.6rem 0 1rem; font-size: .84rem; }

    .motivos { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
               gap: .5rem; }
    .mot { text-align: left; background: rgba(255,255,255,0.03);
           border: 1px solid var(--ms-border); border-radius: var(--ms-radius-sm);
           padding: .6rem .8rem; cursor: pointer; display: flex; flex-direction: column;
           gap: .15rem; transition: all .15s ease; font-family: inherit; }
    .mot:hover { border-color: rgba(255,255,255,0.25); }
    .mot strong { font-size: .88rem; color: rgba(255,255,255,0.88); }
    .mot span { font-size: .74rem; color: var(--ms-text-muted); line-height: 1.4; }
    .mot.on { border-color: var(--ms-gold); background: var(--ms-gold-dim); }
    .mot.on strong { color: var(--ms-gold-light); }

    .nota-defecto { margin: .9rem 0 0; padding: .7rem .9rem; font-size: .8rem;
                    line-height: 1.6; color: var(--ms-text-muted);
                    background: rgba(217,119,6,.09);
                    border: 1px solid rgba(217,119,6,.3);
                    border-radius: var(--ms-radius-sm); }
    .nota-defecto strong { color: #fbbf24; }

    .etq { display: block; margin: 1.2rem 0 .35rem; font-size: .8rem;
           font-weight: 600; color: var(--ms-text); }
    .opc { font-weight: 400; font-size: .72rem; color: var(--ms-text-muted); }
    textarea { width: 100%; box-sizing: border-box; padding: .7rem 1rem;
               font-family: inherit; font-size: .9rem; resize: vertical;
               background: rgba(255,255,255,0.04); color: var(--ms-text);
               border: 1px solid var(--ms-border); border-radius: var(--ms-radius-sm); }
    textarea::placeholder { color: var(--ms-text-muted); }

    /* ── La tabla ──────────────────────────────────────────────── */
    .tabla-scroll { overflow-x: auto; }
    .col-check { width: 42px; text-align: center; }
    .num { text-align: right; }
    .cero { color: rgba(255,255,255,0.3); }
    tr.agotada { opacity: .45; }
    tr.marcada { background: rgba(201,168,76,.06); }

    .contador { display: inline-flex; align-items: center; gap: .3rem; }
    .paso { width: 30px; height: 34px; border-radius: var(--ms-radius-sm);
            background: rgba(255,255,255,0.04); border: 1px solid var(--ms-border);
            color: var(--ms-text); font-size: 1rem; line-height: 1; cursor: pointer; }
    .paso:hover:not(:disabled) { border-color: var(--ms-gold); color: var(--ms-gold); }
    .paso:disabled { opacity: .3; cursor: default; }
    .cifra { width: 58px; height: 34px; text-align: center; font-size: .95rem;
             font-weight: 600; padding: 0 .2rem; border-radius: var(--ms-radius-sm);
             background: rgba(255,255,255,0.04); border: 1px solid var(--ms-border);
             color: var(--ms-text); font-family: inherit; }
    .cifra:disabled { opacity: .4; }

    /* ── Cierre ────────────────────────────────────────────────── */
    .cierre { display: flex; flex-direction: column; gap: .8rem; }
    .resumen { font-size: 1rem; color: var(--ms-text-muted); }
    .resumen strong { color: var(--ms-gold-light); font-size: 1.15rem; }
    .que-pasa { margin: 0; font-size: .82rem; line-height: 1.65;
                color: var(--ms-text-muted); max-width: 75ch; }
    .que-pasa strong { color: rgba(255,255,255,0.85); }
    .acciones-pie { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .falta { font-size: .8rem; color: #fbbf24; }

    @media (max-width: 620px) { .cab h1 { font-size: 1.3rem; } }
  `]
})
export class SolicitudDevolucionNuevaComponent implements OnInit {
  idPedido!: number;
  pedido: Pedido | null = null;
  lineas: LineaPedido[] = [];
  motivo = '';
  descripcion = '';
  guardando = false;
  cargado = false;
  toast = '';
  toastError = false;

  readonly motivos = [
    { valor: 'producto_defectuoso', titulo: 'Producto defectuoso', pista: 'Llegó roto o con tara' },
    { valor: 'talla_incorrecta',    titulo: 'Talla incorrecta',    pista: 'No es la talla que quería' },
    { valor: 'no_esperado',         titulo: 'No es lo esperado',   pista: 'Distinto de lo que vio' },
    { valor: 'cambio_opinion',      titulo: 'Cambio de opinión',   pista: 'Ya no lo quiere' },
    { valor: 'producto_incompleto', titulo: 'Producto incompleto', pista: 'Faltan piezas del conjunto' },
    { valor: 'otro',                titulo: 'Otro',                pista: 'Explícalo abajo' }
  ];

  constructor(private route: ActivatedRoute, private router: Router,
              private http: HttpClient, private api: ApiService) {}

  ngOnInit() {
    this.idPedido = Number(this.route.snapshot.paramMap.get('idPedido'));
    this.http.get<any>(environment.apiUrl + '/pedidos/' + this.idPedido).subscribe({
      next: (res: any) => {
        this.pedido = {
          numeroPedido: res.numeroPedido,
          clienteNombre: res.clienteNombre,
          estado: res.estado,
          fechaPedido: res.fechaPedido,
          total: res.total
        };
        this.lineas = (res.detalles || []).map((d: any) => ({
          idDetalle: d.idDetalle, productoNombre: d.productoNombre,
          cantidad: d.cantidad, yaDevuelto: 0, disponible: d.cantidad,
          selected: false, cantidadDevolver: d.cantidad
        }));
        this.cargarDevolucionesPrevias();
      },
      error: () => { this.cargado = true; this.mostrarToast('Error al cargar el pedido', true); }
    });
  }

  /**
   * Lo que ya se llevaron otras solicitudes de este pedido.
   *
   * <p>Se calcula con la MISMA regla que el backend: cuentan todas menos las
   * {@code rechazada}, porque en una rechazada no se llevó mercancía y no debe
   * gastar cupo. Si estas dos cuentas se separan, la pantalla ofrece un tope que
   * el servidor rechaza — que es justo lo que pasaba antes de mirarlo.
   */
  private cargarDevolucionesPrevias() {
    this.http.get<any>(`${environment.apiUrl}/devoluciones?idPedido=${this.idPedido}&page=0&size=100`)
      .subscribe({
        next: (res: any) => {
          const previas = (res?.content ?? []).filter((s: any) => s.estado !== 'rechazada');
          for (const sol of previas) {
            for (const det of (sol.detalles ?? [])) {
              const linea = this.lineas.find(l => l.idDetalle === det.idDetallePedido);
              if (linea) { linea.yaDevuelto += det.cantidadDevuelta ?? 0; }
            }
          }
          this.recalcularTopes();
          this.cargado = true;
        },
        // Sin el dato es mejor no ofrecer un tope que puede ser falso: se deja
        // lo comprado y el backend seguira siendo la ultima palabra.
        error: () => { this.recalcularTopes(); this.cargado = true; }
      });
  }

  private recalcularTopes() {
    for (const l of this.lineas) {
      l.disponible = Math.max(0, l.cantidad - l.yaDevuelto);
      l.cantidadDevolver = Math.max(1, Math.min(l.cantidadDevolver, l.disponible || 1));
      if (l.disponible === 0) { l.selected = false; }
    }
  }

  sePuedeDevolver(): boolean { return this.pedido?.estado === 'entregado'; }
  quedaAlgo(): boolean { return this.lineas.some(l => l.disponible > 0); }
  hayDevueltasAntes(): boolean { return this.lineas.some(l => l.yaDevuelto > 0); }

  alMarcar(l: LineaPedido) {
    if (l.selected && (l.cantidadDevolver < 1 || l.cantidadDevolver > l.disponible)) {
      l.cantidadDevolver = l.disponible;
    }
  }

  sumar(l: LineaPedido, delta: number) {
    l.cantidadDevolver = Math.max(1, Math.min(l.disponible, (l.cantidadDevolver || 0) + delta));
  }

  acotar(l: LineaPedido) {
    const v = Math.trunc(l.cantidadDevolver);
    l.cantidadDevolver = isNaN(v) ? 1 : Math.max(1, Math.min(l.disponible, v));
  }

  lineasMarcadas(): number { return this.lineas.filter(l => l.selected).length; }

  unidadesADevolver(): number {
    return this.lineas.filter(l => l.selected).reduce((s, l) => s + (l.cantidadDevolver || 0), 0);
  }

  formValido(): boolean { return !!this.motivo && this.lineasMarcadas() > 0; }

  queFalta(): string {
    if (!this.motivo && this.lineasMarcadas() === 0) { return 'Falta el motivo y marcar qué se devuelve.'; }
    if (!this.motivo) { return 'Falta el motivo.'; }
    return 'Marca al menos un producto.';
  }

  enviar() {
    if (!this.formValido()) { return; }
    const detalles = this.lineas.filter(l => l.selected).map(l => ({
      idDetallePedido: l.idDetalle, cantidadDevuelta: l.cantidadDevolver
    }));
    this.guardando = true;
    this.api.post<any>('devoluciones', {
      idPedido: this.idPedido, motivo: this.motivo,
      descripcion: this.descripcion || null, detalles
    }).subscribe({
      next: (res: any) => {
        this.guardando = false;
        this.mostrarToast('Solicitud registrada');
        setTimeout(() => this.router.navigate(['/devoluciones', res.idSolicitud]), 1500);
      },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
