import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

/**
 * Lanzar una fabricación.
 *
 * **La pregunta que la pantalla no contestaba.** Tenía la tabla de
 * disponibilidad —que está bien y se conserva— pero no decía lo único que hace
 * falta para decidir: **cuánto va a costar y si sale a cuenta**. Se lanzaba a
 * ciegas y la respuesta llegaba después, en el análisis de costes.
 *
 * Ahora el panel de la derecha trae el coste estimado de materia prima, el
 * precio al que se vende y el **margen** — antes de crear nada. Sale del mismo
 * cálculo de la F29 que usa la pantalla de costes, así que no es una cuenta
 * paralela que pueda decir otra cosa.
 *
 * **Lo que no cambia**: la verificación de materia prima en vivo, que es la que
 * impide lanzar una orden que no se puede terminar.
 */
@Component({
  selector: 'app-orden-produccion-nueva',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent],
  template: `
    <div class="opn">

      <header class="cab">
        <button class="btn-volver" routerLink="/produccion">
          <span class="flecha" aria-hidden="true">←</span> Órdenes de producción
        </button>
        <h1>Nueva orden de producción</h1>
        <p class="sub">
          Solo se fabrica lo de marca propia, y solo si tiene lista de materiales.
        </p>
      </header>

      <p class="vacio-total" *ngIf="productos.length === 0">
        <strong>No hay ningún producto que se pueda fabricar.</strong>
        Un producto entra aquí si su origen es <em>fabricado</em> y tiene definida su
        lista de materiales. Lo de marca ajena se compra, no se produce.
      </p>

      <div class="reparto" *ngIf="productos.length > 0">

        <!-- ══ IZQUIERDA · qué se va a fabricar ══════════════════ -->
        <section class="cuerpo">

          <article class="bloque">
            <h2>Qué se fabrica</h2>

            <div class="campos">
              <label class="campo ancho">
                <span class="etq">Producto *</span>
                <select [(ngModel)]="form.idProducto" (change)="onProductoElegido()">
                  <option [ngValue]="null">Elige el producto…</option>
                  <option *ngFor="let p of productos" [ngValue]="p.idProducto">{{ p.nombre }}</option>
                </select>
              </label>

              <label class="campo">
                <span class="etq">Cantidad a producir *</span>
                <input type="number" min="1" [(ngModel)]="form.cantidadPlanificada" (input)="onCambio()">
                <small class="pista" *ngIf="verif?.cantidadMaximaProducible != null">
                  Con el stock de hoy caben {{ verif.cantidadMaximaProducible }}.
                  <button type="button" class="enlace"
                          *ngIf="verif.cantidadMaximaProducible > 0 && form.cantidadPlanificada !== verif.cantidadMaximaProducible"
                          (click)="ponerMaximo()">poner el máximo</button>
                </small>
              </label>

              <label class="campo">
                <span class="etq">Bodega destino *</span>
                <select [(ngModel)]="form.idBodegaDestino">
                  <option [ngValue]="null">Elige la bodega…</option>
                  <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{ b.nombre }}</option>
                </select>
                <small class="pista">Donde entrará el producto terminado.</small>
              </label>

              <label class="campo ancho">
                <span class="etq">Observaciones</span>
                <input [(ngModel)]="form.observaciones" placeholder="Opcional">
              </label>
            </div>
          </article>

          <!-- Disponibilidad de materia prima -->
          <article class="bloque" *ngIf="verif as v">
            <h2>
              Materia prima
              <span class="sello" [class.ok]="v.puedeProducir" [class.mal]="!v.puedeProducir">
                {{ v.puedeProducir ? 'alcanza' : 'no alcanza' }}
              </span>
            </h2>
            <p class="sub2">
              Al <strong>iniciar</strong> la orden se descontará esto del stock de insumos.
              Todavía no: crear la orden no consume nada.
            </p>

            <div class="tabla-caja">
              <table>
                <thead>
                  <tr>
                    <th>Material</th>
                    <th class="num">Necesario</th>
                    <th class="num">Disponible</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let m of v.materiales" [class.falta]="!m.suficiente">
                    <td>{{ m.nombreMateriaPrima }}</td>
                    <td class="num">{{ m.cantidadNecesaria | number:'1.0-3' }} {{ m.unidadMedida }}</td>
                    <td class="num">{{ m.stockDisponible | number:'1.0-3' }} {{ m.unidadMedida }}</td>
                    <td>
                      <span *ngIf="m.suficiente" class="ok"><app-icon name="check" [size]="13"/> suficiente</span>
                      <span *ngIf="!m.suficiente" class="mal">
                        <app-icon name="x" [size]="13"/>
                        faltan {{ m.faltante | number:'1.0-3' }} {{ m.unidadMedida }}
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </article>

          <article class="bloque sin-bom" *ngIf="sinBom">
            <h2>Este producto todavía no se puede fabricar</h2>
            <p class="sub2">
              No tiene <strong>lista de materiales</strong> definida, y sin ella el sistema
              no sabe qué insumos consume ni cuánto cuesta. Es el estado en el que quedaron
              los productos de marca propia cuando se corrigió su origen: la marca está
              bien, la receta falta.
            </p>
            <p class="sub2">
              Se define en <strong>Datos maestros → Productos → el producto → Lista de
              materiales</strong>. Es cosa del Encargado de Producción o del Administrador.
            </p>
          </article>

          <p class="aviso-error" *ngIf="verifError && !sinBom">{{ verifError }}</p>
        </section>

        <!-- ══ DERECHA · cuánto cuesta y confirmar ══════════════ -->
        <aside class="panel">
          <div class="caja">
            <h2>Lo que va a costar</h2>

            <p class="sin-datos" *ngIf="!costo && !sinBom">
              Elige un producto para ver el coste estimado.
            </p>
            <p class="sin-datos" *ngIf="sinBom">
              No se puede estimar: <strong>a este producto le falta la lista de
              materiales</strong>.
            </p>

            <ng-container *ngIf="costo as c">
              <div class="resumen">
                <div class="fila">
                  <span>Materia prima por unidad</span>
                  <strong>{{ c.costoMateriaPrimaUnitario | currency:'USD':'symbol':'1.2-2' }}</strong>
                </div>
                <div class="fila">
                  <span>Unidades</span>
                  <strong>{{ form.cantidadPlanificada || 0 }}</strong>
                </div>
                <div class="fila total">
                  <span>Materia prima total</span>
                  <strong>{{ materiaPrimaTotal() | currency:'USD':'symbol':'1.2-2' }}</strong>
                </div>
              </div>

              <div class="margen" *ngIf="c.precioVenta">
                <div class="fila">
                  <span>Se vende a</span>
                  <strong>{{ c.precioVenta | currency:'USD':'symbol':'1.2-2' }}</strong>
                </div>
                <div class="fila">
                  <span>Margen bruto</span>
                  <strong [class.bueno]="c.margenPorcentaje >= 30" [class.pobre]="c.margenPorcentaje < 15">
                    {{ c.margenBruto | currency:'USD':'symbol':'1.2-2' }}
                    <small>({{ c.margenPorcentaje | number:'1.0-1' }} %)</small>
                  </strong>
                </div>
              </div>

              <p class="nota" *ngIf="c.advertencia">{{ c.advertencia }}</p>

              <p class="nota">
                Es <strong>solo la materia prima</strong>. La mano de obra y los costes
                indirectos se añaden al completar la orden, cuando ya se sabe cuánto
                salió de verdad.
              </p>
            </ng-container>

            <p class="aviso-bloqueo" *ngIf="verif && !verif.puedeProducir">
              No hay materia prima suficiente para {{ form.cantidadPlanificada }} unidad(es).
            </p>

            <button type="button" class="confirmar" [disabled]="!puedeCrear() || guardando"
                    (click)="crear()">
              {{ guardando ? 'Creando…' : 'Crear orden' }}
            </button>

            <p class="nota">
              La orden nace <strong>planificada</strong>: no consume nada todavía. La
              materia prima se descuenta cuando la inicies.
            </p>
          </div>
        </aside>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{ toast }}</div>
    </div>
  `,
  styles: [`
    .opn { width: 100%; max-width: 1500px; margin: 0 auto;
           padding: clamp(1rem, 3vw, 2.5rem); padding-bottom: 3rem; }

    .cab { margin-bottom: 1.5rem; }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.6rem; color: var(--ms-text); }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .92rem; }

    .vacio-total { border: 1px dashed var(--ms-border); border-radius: var(--ms-radius);
                   padding: 1.5rem; color: var(--ms-text-muted); line-height: 1.65; }
    .vacio-total strong { color: var(--ms-text); display: block; margin-bottom: .3rem; }

    .reparto { display: grid; grid-template-columns: 1.7fr 1fr; gap: 1.5rem; align-items: start; }
    .cuerpo { display: flex; flex-direction: column; gap: 1.25rem; min-width: 0; }

    .bloque { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
              border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; min-width: 0; }
    .bloque h2 { margin: 0 0 .9rem; font-size: 1rem; color: var(--ms-text);
                 display: flex; align-items: center; gap: .6rem; }
    .sub2 { margin: -.5rem 0 1rem; font-size: .85rem; color: var(--ms-text-muted); line-height: 1.55; }
    .sub2 strong { color: var(--ms-text); }

    .sello { font-size: .66rem; text-transform: uppercase; letter-spacing: .05em;
             padding: .18rem .5rem; border-radius: 99px; border: 1px solid currentColor; }
    .sello.ok  { color: var(--ms-green); background: rgba(76,175,80,.1); }
    .sello.mal { color: var(--ms-red); background: rgba(229,115,115,.1); }

    .campos { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .campo { display: flex; flex-direction: column; gap: .35rem; }
    .campo.ancho { grid-column: 1 / -1; }
    .etq { font-size: .68rem; text-transform: uppercase; letter-spacing: .08em;
           color: var(--ms-text-muted); }
    .campo input, .campo select {
      background: rgba(255,255,255,.04); border: 1px solid var(--ms-border);
      color: var(--ms-text); padding: .65rem .8rem;
      border-radius: var(--ms-radius-sm); font-size: .92rem; width: 100%;
    }
    .campo input:focus, .campo select:focus { border-color: var(--ms-gold); outline: none; }
    .pista { font-size: .76rem; color: var(--ms-text-muted); }
    .enlace { background: none; border: none; color: var(--ms-gold);
              font-size: .76rem; cursor: pointer; padding: 0; text-decoration: underline; }

    .tabla-caja { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .87rem; }
    th, td { padding: .55rem .65rem; text-align: left; border-bottom: 1px solid var(--ms-border); }
    th { font-size: .66rem; text-transform: uppercase; letter-spacing: .06em;
         color: var(--ms-text-muted); font-weight: 600; white-space: nowrap; }
    td { color: var(--ms-text); }
    .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    th.num { text-align: right; }
    tr.falta td { background: rgba(229,115,115,.05); }
    .ok  { color: var(--ms-green); display: inline-flex; align-items: center; gap: .3rem; }
    .mal { color: var(--ms-red); display: inline-flex; align-items: center; gap: .3rem; }

    .aviso-error { color: var(--ms-red); font-size: .86rem; }
    .sin-bom { border-color: var(--ms-gold); background: var(--ms-gold-dim); }
    .sin-bom h2 { color: var(--ms-gold-light); }

    /* ── Panel ────────────────────────────────────────────────── */
    .panel { position: sticky; top: 1rem; }
    .caja { background: var(--ms-bg-card); border: 1px solid var(--ms-gold);
            border-radius: var(--ms-radius); padding: 1.35rem 1.5rem; }
    .caja h2 { margin: 0 0 1rem; font-size: 1rem; color: var(--ms-text); }
    .sin-datos { color: var(--ms-text-muted); font-size: .88rem; margin: 0 0 1rem; }

    .resumen, .margen { border-top: 1px solid var(--ms-border); padding-top: .8rem; margin-top: .8rem; }
    .resumen:first-of-type { border-top: none; margin-top: 0; padding-top: 0; }
    .fila { display: flex; justify-content: space-between; align-items: baseline;
            gap: 1rem; font-size: .86rem; color: var(--ms-text-muted); margin-bottom: .45rem; }
    .fila strong { color: var(--ms-text); font-variant-numeric: tabular-nums; }
    .fila.total { border-top: 1px solid var(--ms-border); padding-top: .6rem; margin-top: .55rem; }
    .fila.total strong { color: var(--ms-gold); font-size: 1.1rem; }
    .fila .bueno { color: var(--ms-green); }
    .fila .pobre { color: var(--ms-red); }
    .fila small { font-weight: 400; opacity: .8; }

    .aviso-bloqueo { color: var(--ms-red); font-size: .84rem; margin: 1rem 0 0; line-height: 1.5; }

    .confirmar { width: 100%; background: var(--ms-gold); border: none; color: #1a1608;
                 padding: .85rem; font-size: .95rem; font-weight: 600; margin-top: 1rem;
                 border-radius: var(--ms-radius-sm); cursor: pointer; }
    .confirmar:hover:not(:disabled) { background: var(--ms-gold-light); }
    .confirmar:disabled { opacity: .4; cursor: not-allowed; }

    .nota { font-size: .77rem; color: var(--ms-text-muted); line-height: 1.55; margin: .85rem 0 0; }
    .nota strong { color: var(--ms-text); }

    @media (max-width: 1024px) {
      .reparto { grid-template-columns: 1fr; }
      .panel { position: static; }
      .campos { grid-template-columns: 1fr; }
    }
  `]
})
export class OrdenProduccionNuevaComponent implements OnInit {

  productos: any[] = [];
  bodegas: any[] = [];
  form: any = { idProducto: null, cantidadPlanificada: 1, idBodegaDestino: null, observaciones: '' };
  verif: any = null;
  costo: any = null;
  verifError = '';
  /** El producto elegido no tiene BOM: es el caso de 11 de los 14 fabricados. */
  sinBom = false;
  guardando = false;
  toast = '';
  toastError = false;
  private timer: any;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit() {
    this.api.get<any>('productos?origen=fabricado&size=1000').subscribe({
      next: res => { this.productos = res.content || []; },
      error: () => {}
    });
    this.api.get<any>('bodegas?size=1000').subscribe({
      next: res => { this.bodegas = res.content || []; },
      error: () => {}
    });
  }

  onProductoElegido() {
    this.costo = null;
    this.sinBom = false;
    if (this.form.idProducto) {
      // El mismo cálculo de la F29 que usa la pantalla de costes: no es una
      // cuenta paralela que pueda acabar diciendo otra cosa.
      this.api.get<any>(`productos/${this.form.idProducto}/costo-estimado`).subscribe({
        next: res => { this.costo = res; },
        // Un 400 aqui casi siempre significa lo mismo: falta el BOM. Se
        // distingue por el mensaje del servidor y no por el codigo, para no
        // llamar "sin lista de materiales" a cualquier otro fallo.
        error: err => {
          this.costo = null;
          this.sinBom = /lista de materiales/i.test(err.error?.message || "");
        }
      });
    }
    this.onCambio();
  }

  onCambio() {
    clearTimeout(this.timer);
    this.verif = null;
    this.verifError = '';
    if (!this.form.idProducto || !this.form.cantidadPlanificada || this.form.cantidadPlanificada < 1) {
      return;
    }
    this.timer = setTimeout(() => this.verificar(), 300);
  }

  ponerMaximo() {
    this.form.cantidadPlanificada = this.verif.cantidadMaximaProducible;
    this.onCambio();
  }

  materiaPrimaTotal(): number {
    const unitario = Number(this.costo?.costoMateriaPrimaUnitario || 0);
    return unitario * (this.form.cantidadPlanificada || 0);
  }

  verificar() {
    this.api.get<any>('ordenes-produccion/verificar-disponibilidad?idProducto=' + this.form.idProducto
        + '&cantidad=' + this.form.cantidadPlanificada).subscribe({
      next: res => { this.verif = res; this.verifError = ''; },
      error: err => {
        this.verif = null;
        const msg = err.error?.message || "";
        this.sinBom = this.sinBom || /lista de materiales/i.test(msg);
        this.verifError = msg || "No se pudo comprobar la materia prima";
      }
    });
  }

  puedeCrear(): boolean {
    return !!this.form.idProducto && !!this.form.idBodegaDestino
        && this.form.cantidadPlanificada >= 1
        && !!this.verif && this.verif.puedeProducir === true;
  }

  crear() {
    if (!this.puedeCrear()) { return; }
    this.guardando = true;
    this.api.post<any>('ordenes-produccion', this.form).subscribe({
      next: res => {
        this.guardando = false;
        this.router.navigate(['/produccion', res.idOrdenProduccion]);
      },
      error: err => {
        this.guardando = false;
        this.mostrarToast(err.error?.message || 'No se pudo crear la orden', true);
      }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg;
    this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 4000);
  }
}
