import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AppIconComponent } from '../../../shared/components/icon/icon.component';
import { SearchableSelectComponent } from '../../../shared/components/searchable-select/searchable-select.component';

interface PickingLinea {
  idDetalle: number;
  idProducto: number;
  productoNombre: string;
  productoDescripcion: string;
  unidadMedidaNombre: string;
  cantidad: number;
  cantidadRecogida: number;
  pickingCompletado: boolean;
  idBodegaPicking?: number | null;
  bodegaPickingNombre?: string | null;
  pendiente: number;
  guardando?: boolean;
  /** F74: dónde está la mercancía. Se resuelve con el inventario que ya existía. */
  ubicaciones?: Ubicacion[];
  buscandoUbicaciones?: boolean;
  /** Lo que ve el buscador: todas las bodegas, las que tienen stock primero. */
  opciones?: OpcionBodega[];
}

/** Una entrada del buscador de bodega. La etiqueta es por lo que se filtra. */
interface OpcionBodega {
  idBodega: number;
  etiqueta: string;
}

/** Una bodega que de verdad tiene existencias de este producto. */
interface Ubicacion {
  idBodega: number;
  nombre: string;
  cantidad: number;
}

interface PickingPedido {
  idPedido: number;
  numeroPedido: string;
  clienteNombre: string;
  clienteApellido: string;
  fechaPedido: string;
  estado: string;
  esPedidoEspecial: boolean;
  tipoEspecial: string;
  notaEspecial: string;
  fechaLimiteEntrega: string;
  lineas: PickingLinea[];
  totalLineas: number;
  lineasCompletadas: number;
  estadoPicking: string;
}

/**
 * Recoger un pedido de las estanterías.
 *
 * <p><b>Qué estaba mal (F74).</b> Dos cosas, y la segunda pesaba más que la
 * primera.
 *
 * <p>La visible: el componente <b>no tenía ni una regla de estilo</b> —el bloque
 * decía «hereda el tema global» y no heredaba nada, porque las clases que usa
 * (<code>.linea-controles</code>, <code>.campo</code>) no existen en
 * <code>styles.scss</code>. El resultado eran recuadros encimados: la cantidad
 * total caída al fondo de su columna y el desplegable de bodega montado sobre la
 * casilla de al lado.
 *
 * <p>La de fondo: <b>el desplegable listaba las 200 bodegas sin decir cuál tiene
 * la mercancía</b>. Quien recoge tenía que adivinar el almacén, y equivocarse
 * significa un movimiento de stock contra una bodega que no lo tenía.
 *
 * <p>Ahora cada línea pregunta al inventario dónde está el producto y lo dice en
 * el propio buscador: se escribe el nombre —como en el resto de la aplicación—
 * y cada opción lleva al lado las unidades que hay en esa bodega, con las que
 * tienen existencias delante. Siguen estando todas, porque recoger de un almacén
 * sin registro es raro pero pasa; lo que cambia es cuál se encuentra primero.
 */
@Component({
  selector: 'app-picking-ejecucion',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent, RouterLink, SearchableSelectComponent],
  template: `
    <div class="container" *ngIf="pedido as p">

      <!-- ── Cabecera ────────────────────────────────────────────── -->
      <header class="cab">
        <div class="cab-txt">
          <button class="btn-volver" (click)="volver()">
            <span class="flecha" aria-hidden="true">←</span> Picking
          </button>
          <h1>
            Recoger {{ p.numeroPedido }}
            <span class="pill" [ngClass]="'ep-' + estadoPicking">{{ estadoLabel(estadoPicking) }}</span>
          </h1>
          <p class="sub">
            {{ p.clienteNombre }} {{ p.clienteApellido }}
            <span class="sep">·</span> {{ totalUnidades }} unidades en {{ totalLineas }}
            {{ totalLineas === 1 ? 'línea' : 'líneas' }}
          </p>
        </div>

        <div class="avance">
          <div class="avance-cab">
            <span class="avance-txt">{{ lineasCompletadas }} de {{ totalLineas }} líneas listas</span>
            <span class="avance-pct">{{ porcentaje }}%</span>
          </div>
          <div class="barra"><div class="relleno" [style.width.%]="porcentaje"></div></div>
        </div>
      </header>

      <!-- ── Pedido especial ─────────────────────────────────────── -->
      <div class="especial" *ngIf="p.esPedidoEspecial" [class.urge]="esUrgente()">
        <div class="esp-cab">
          <span class="esp-chip">Pedido especial · {{ tipoLabel(p.tipoEspecial) }}</span>
          <span class="esp-urge inline-icon-text" *ngIf="esUrgente()">
            <app-icon name="warning" [size]="16"/>
            Entrega urgente — límite {{ p.fechaLimiteEntrega | date:'dd/MM/yyyy HH:mm' }}
          </span>
        </div>
        <p class="esp-nota" *ngIf="p.notaEspecial">«{{ p.notaEspecial }}»</p>
      </div>

      <!-- ── Las líneas ──────────────────────────────────────────── -->
      <div class="lineas">
        <article class="linea" *ngFor="let l of p.lineas; let i = index" [ngClass]="claseLinea(l)">

          <div class="l-cab">
            <div class="l-titulo">
              <span class="l-num">{{ i + 1 }}</span>
              <div>
                <h2>{{ l.productoNombre }}</h2>
                <p class="l-desc">
                  <span *ngIf="l.productoDescripcion">{{ l.productoDescripcion }}</span>
                  <span class="sep" *ngIf="l.productoDescripcion && l.unidadMedidaNombre">·</span>
                  <span *ngIf="l.unidadMedidaNombre">{{ l.unidadMedidaNombre }}</span>
                </p>
              </div>
            </div>
            <span class="l-estado">{{ textoEstadoLinea(l) }}</span>
          </div>

          <div class="l-cuerpo">

            <!-- Cuánto se recoge -->
            <div class="bloque">
              <span class="rotulo">Cuántas unidades has recogido</span>
              <div class="contador">
                <button type="button" class="paso" (click)="sumar(l, -1)"
                        [disabled]="l.cantidadRecogida <= 0" aria-label="Una menos">−</button>
                <input type="number" [(ngModel)]="l.cantidadRecogida" [min]="0" [max]="l.cantidad"
                       class="cifra" (change)="acotar(l)"/>
                <button type="button" class="paso" (click)="sumar(l, 1)"
                        [disabled]="l.cantidadRecogida >= l.cantidad" aria-label="Una más">+</button>
                <span class="de">de {{ l.cantidad }}</span>
                <button type="button" class="todo" (click)="recogerTodo(l)"
                        *ngIf="l.cantidadRecogida !== l.cantidad">Todas</button>
              </div>
              <!-- La explicación solo cuando de verdad se va a guardar a medias.
                   Puesta siempre, en un pedido de siete líneas es un muro que se
                   deja de leer a la segunda. -->
              <p class="falta" *ngIf="l.cantidadRecogida > 0 && l.cantidadRecogida < l.cantidad">
                Faltan <strong>{{ l.cantidad - l.cantidadRecogida }}</strong>.
                Guardar así deja la línea a medias, y eso está bien: se puede
                terminar después.
              </p>
            </div>

            <!-- De dónde se recoge -->
            <div class="bloque">
              <span class="rotulo">De qué bodega la sacas</span>

              <p class="cargando" *ngIf="l.buscandoUbicaciones">Buscando dónde está…</p>

              <ng-container *ngIf="!l.buscandoUbicaciones">
                <!-- Se escribe, no se busca a ojo entre veinte. Cada opción lleva
                     al lado lo que hay en esa bodega, y van ordenadas de más a
                     menos: la primera suele ser la buena. -->
                <app-searchable-select
                  [items]="l.opciones || []"
                  labelKey="etiqueta"
                  valueKey="idBodega"
                  placeholder="Escribe el nombre de la bodega…"
                  [(ngModel)]="l.idBodegaPicking"
                  [ngModelOptions]="{ standalone: true }"/>

                <p class="pista-bod" *ngIf="l.ubicaciones?.length">
                  {{ l.ubicaciones!.length }}
                  {{ l.ubicaciones!.length === 1 ? 'bodega tiene' : 'bodegas tienen' }}
                  este producto, y salen primero. Las demás también se pueden elegir.
                </p>

                <p class="sin-stock" *ngIf="!l.ubicaciones?.length">
                  Ninguna bodega registra existencias de este producto. Puedes
                  elegir una igualmente, pero conviene revisar el inventario antes.
                </p>
              </ng-container>

              <p class="aviso-corta" *ngIf="bodegaElegidaCorta(l)">
                Esa bodega tiene {{ existenciasElegidas(l) }} y estás recogiendo
                {{ l.cantidadRecogida }}.
              </p>
            </div>

            <!-- Guardar -->
            <div class="bloque accion">
              <button class="guardar" (click)="guardarLinea(l)" [disabled]="l.guardando">
                {{ l.guardando ? 'Guardando…' : 'Guardar línea' }}
              </button>
              <span class="guardado" *ngIf="l.pickingCompletado">
                Línea completa
              </span>
            </div>
          </div>
        </article>
      </div>

      <!-- ── Qué toca después ────────────────────────────────────── -->
      <div class="cierre" *ngIf="totalLineas > 0 && lineasCompletadas === totalLineas">
        <h3>Todo recogido</h3>
        <p>
          Las {{ totalLineas }} líneas están completas. El siguiente paso del
          flujo es <strong>empacar</strong> el pedido y darle su HU.
        </p>
        <a class="ir" routerLink="/empaque">Ir a Empaque</a>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{ toast }}</div>
    </div>

    <div class="spinner" *ngIf="!pedido">Cargando picking…</div>
  `,
  styles: [`
    /* ── Cabecera ──────────────────────────────────────────────── */
    .cab { display: flex; justify-content: space-between; align-items: flex-start;
           gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1.5rem; }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.6rem; color: var(--ms-text);
              display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .92rem; }
    .sep { color: var(--ms-text-muted); margin: 0 .35rem; }

    .pill { font-size: .68rem; font-weight: 700; letter-spacing: .06em;
            padding: .25rem .6rem; border-radius: 99px; color: #fff;
            text-transform: uppercase; }
    .ep-pendiente   { background: #64748b; }
    .ep-en_progreso { background: #d97706; }
    .ep-completo    { background: #16a34a; }

    /* El avance como barra: en una pantalla de almacén se mira de lejos. */
    .avance { min-width: 230px; display: flex; flex-direction: column; gap: .45rem; }
    .avance-cab { display: flex; justify-content: space-between; align-items: baseline;
                  gap: 1rem; }
    .avance-txt { color: var(--ms-text-muted); font-size: .84rem; }
    .avance-pct { color: var(--ms-gold-light); font-size: 1.1rem; font-weight: 700; }
    .barra { height: 8px; border-radius: 99px; background: rgba(255,255,255,0.07);
             overflow: hidden; }
    .relleno { height: 100%; background: var(--ms-gold); border-radius: 99px;
               transition: width .25s ease; }

    /* ── Pedido especial ───────────────────────────────────────── */
    .especial { background: var(--ms-gold-dim); border: 1px solid rgba(201,168,76,.35);
                border-radius: var(--ms-radius); padding: .9rem 1.2rem; margin-bottom: 1.5rem; }
    .especial.urge { border-color: rgba(220,38,38,.5); background: rgba(220,38,38,.08); }
    .esp-cab { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .esp-chip { font-size: .72rem; font-weight: 700; letter-spacing: .05em;
                text-transform: uppercase; color: var(--ms-gold-light); }
    .especial.urge .esp-chip { color: #fca5a5; }
    .esp-urge { font-size: .82rem; color: #fca5a5; display: inline-flex;
                align-items: center; gap: .35rem; }
    .esp-nota { margin: .5rem 0 0; font-size: .88rem; color: var(--ms-text-muted);
                font-style: italic; }

    /* ── Las líneas ────────────────────────────────────────────── */
    .lineas { display: flex; flex-direction: column; gap: 1rem; }

    .linea { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
             border-left: 3px solid var(--ms-border);
             border-radius: var(--ms-radius); overflow: hidden; }
    /* La lista del buscador se sale de la tarjeta, y la tarjeta la recortaba:
       mientras hay una lista abierta deja de recortar y se levanta sobre las de
       abajo. Es el mismo remedio que styles.scss aplica a .form-section, y por
       la misma razón: el z-index 1200 de la lista solo compite dentro de su
       caja. */
    .linea:has(.search-select.open) { overflow: visible; position: relative; z-index: 60; }

    .linea.pendiente { border-left-color: #64748b; }
    .linea.parcial   { border-left-color: #d97706; }
    .linea.completa  { border-left-color: #16a34a; }

    .l-cab { display: flex; justify-content: space-between; align-items: flex-start;
             gap: 1rem; padding: 1.1rem 1.4rem; border-bottom: 1px solid var(--ms-border); }
    .l-titulo { display: flex; gap: .9rem; align-items: flex-start; min-width: 0; }
    .l-num { flex-shrink: 0; width: 26px; height: 26px; border-radius: 50%;
             background: rgba(255,255,255,0.06); color: var(--ms-text-muted);
             display: grid; place-items: center; font-size: .78rem; font-weight: 700; }
    .l-cab h2 { margin: 0; font-size: 1.02rem; color: var(--ms-text); }
    .l-desc { margin: .2rem 0 0; font-size: .84rem; color: var(--ms-text-muted); }
    .l-estado { flex-shrink: 0; font-size: .8rem; color: var(--ms-text-muted);
                white-space: nowrap; }
    .linea.completa .l-estado { color: #4ade80; }
    .linea.parcial  .l-estado { color: #fbbf24; }

    /* Tres columnas que NO se pisan: cantidad, bodega, acción. */
    .l-cuerpo { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(280px, 1.3fr) auto;
                gap: 1.5rem; padding: 1.25rem 1.4rem; align-items: start; }
    .bloque { min-width: 0; display: flex; flex-direction: column; gap: .55rem; }
    .bloque.accion { align-items: stretch; justify-content: flex-start; gap: .5rem; }
    .rotulo { font-size: .72rem; text-transform: uppercase; letter-spacing: .05em;
              color: var(--ms-text-muted); }

    /* ── El contador ───────────────────────────────────────────── */
    .contador { display: flex; align-items: center; gap: .45rem; flex-wrap: wrap; }
    /* 44 px, el alto de cualquier campo de la aplicacion: el contador y el
       buscador de bodega tienen que leerse como un solo renglon. */
    .paso { width: 38px; height: 44px; flex-shrink: 0; border-radius: var(--ms-radius-sm);
            background: rgba(255,255,255,0.04); border: 1px solid var(--ms-border);
            color: var(--ms-text); font-size: 1.1rem; line-height: 1; cursor: pointer;
            transition: all .15s ease; }
    .paso:hover:not(:disabled) { border-color: var(--ms-gold); color: var(--ms-gold); }
    .paso:disabled { opacity: .35; cursor: default; }
    .cifra { width: 74px; height: 44px; text-align: center; font-size: 1.05rem;
             font-weight: 600; padding: .45rem .3rem; border-radius: var(--ms-radius-sm);
             background: rgba(255,255,255,0.04); border: 1px solid var(--ms-border);
             color: var(--ms-text); font-family: inherit; }
    .de { color: var(--ms-text-muted); font-size: .88rem; }
    .todo { background: transparent; border: 1px solid rgba(201,168,76,.5);
            color: var(--ms-gold-light); padding: .35rem .7rem; border-radius: 99px;
            font-size: .78rem; cursor: pointer; }
    .todo:hover { background: var(--ms-gold-dim); }
    .falta { margin: 0; font-size: .78rem; color: var(--ms-text-muted); line-height: 1.5; }
    .falta strong { color: #fbbf24; }

    /* ── La bodega ─────────────────────────────────────────────── */
    .cargando, .sin-stock, .pista-bod { margin: 0; font-size: .78rem;
                                        color: var(--ms-text-muted); line-height: 1.5; }
    .aviso-corta { margin: 0; font-size: .78rem; color: #fbbf24; }

    /* ── Guardar ───────────────────────────────────────────────── */
    .guardar { background: var(--ms-gold); border: none; color: #1a1a1a;
               padding: .65rem 1.3rem; border-radius: var(--ms-radius-sm);
               font-size: .88rem; font-weight: 600; cursor: pointer; white-space: nowrap;
               font-family: inherit; }
    .guardar:hover:not(:disabled) { filter: brightness(1.08); }
    .guardar:disabled { opacity: .55; cursor: default; }
    .guardado { font-size: .78rem; color: #4ade80; text-align: center; }

    /* ── El cierre ─────────────────────────────────────────────── */
    .cierre { margin-top: 1.5rem; background: rgba(22,163,74,.08);
              border: 1px solid rgba(22,163,74,.35); border-radius: var(--ms-radius);
              padding: 1.3rem 1.5rem; }
    .cierre h3 { margin: 0 0 .35rem; font-size: 1.05rem; color: #4ade80; }
    .cierre p { margin: 0 0 .9rem; font-size: .88rem; color: var(--ms-text-muted);
                line-height: 1.6; }
    .ir { display: inline-block; background: rgba(22,163,74,.18);
          border: 1px solid rgba(22,163,74,.5); color: #86efac; padding: .55rem 1.1rem;
          border-radius: var(--ms-radius-sm); font-size: .86rem; text-decoration: none; }
    .ir:hover { background: rgba(22,163,74,.28); }

    /* ── Estrecho: las tres columnas se apilan en vez de encimarse ── */
    @media (max-width: 1100px) {
      .l-cuerpo { grid-template-columns: 1fr; gap: 1.2rem; }
      .bloque.accion { align-items: flex-start; }
    }
    @media (max-width: 620px) {
      .cab h1 { font-size: 1.3rem; }
      .avance { width: 100%; }
    }
  `]
})
export class PickingEjecucionComponent implements OnInit {
  pedido: PickingPedido | null = null;
  totalLineas = 0;
  lineasCompletadas = 0;
  estadoPicking = 'pendiente';
  toast = '';
  toastError = false;
  /** L4: bodegas activas, para elegir de cuál se recoge cada línea. */
  bodegas: Array<{ idBodega: number; nombre: string }> = [];

  constructor(private route: ActivatedRoute, private router: Router, private http: HttpClient) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('idPedido');
    if (id) { this.cargar(+id); }
    this.cargarBodegas();
  }

  cargarBodegas() {
    this.http.get<Array<{ idBodega: number; nombre: string }>>(`${environment.apiUrl}/bodegas/activas`)
      .subscribe({
        next: res => { this.bodegas = res ?? []; },
        error: () => { this.bodegas = []; }
      });
  }

  cargar(id: number) {
    this.http.get<PickingPedido>(`${environment.apiUrl}/picking/pedidos/${id}`).subscribe({
      next: res => {
        this.pedido = res;
        this.recalcular();
        res.lineas.forEach(l => this.cargarUbicaciones(l));
      },
      error: (err) => { this.mostrarToast(err.error?.message || 'Error al cargar el picking', true); }
    });
  }

  /**
   * Dónde está la mercancía de esta línea (F74).
   *
   * <p>Usa el listado de inventario que ya existía —no un endpoint nuevo, que
   * habría que concederle a los seis roles— y se queda con las filas de ESTE
   * producto que tienen existencias. Sin esto, quien recoge elige el almacén de
   * una lista de 200 nombres sin ninguna pista, y equivocarse significa un
   * movimiento de stock contra una bodega que no tenía la prenda.
   */
  cargarUbicaciones(l: PickingLinea) {
    l.buscandoUbicaciones = true;
    const url = `${environment.apiUrl}/inventario?page=0&size=300`
              + `&busqueda=${encodeURIComponent(l.productoNombre)}`;
    this.http.get<any>(url).subscribe({
      next: res => {
        const filas: any[] = res?.content ?? res?.data ?? [];
        l.ubicaciones = filas
          .filter(f => f.productoId === l.idProducto && f.cantidad > 0)
          .map(f => ({ idBodega: f.bodegaId, nombre: f.bodegaNombre, cantidad: f.cantidad }))
          .sort((a, b) => b.cantidad - a.cantidad);
        this.armarOpciones(l);
        l.buscandoUbicaciones = false;
        // La preseleccion va DESPUES de armar las opciones y en otro ciclo: el
        // buscador rellena su caja de texto buscando el valor entre sus items,
        // y si el valor llega antes que la lista se queda en blanco aunque la
        // linea si tenga bodega.
        if (l.ubicaciones.length === 1 && !l.idBodegaPicking) {
          setTimeout(() => { l.idBodegaPicking = l.ubicaciones![0].idBodega; });
        }
      },
      error: () => { l.ubicaciones = []; l.buscandoUbicaciones = false; }
    });
  }

  /**
   * Lo que ofrece el buscador de bodega.
   *
   * <p>Van <b>todas</b> las bodegas, no solo las que tienen stock: hace falta
   * poder recoger de un almacén sin registro, que es el caso raro pero real.
   * Lo que cambia es el orden y la etiqueta — las que tienen existencias salen
   * primero y dicen cuántas—, porque es lo que decide la elección.
   */
  private armarOpciones(l: PickingLinea) {
    const conStock = (l.ubicaciones ?? []).map(u => ({
      idBodega: u.idBodega,
      etiqueta: u.nombre + ' · ' + u.cantidad + ' u.'
    }));
    const yaEstan = new Set(conStock.map(o => o.idBodega));
    const resto = this.bodegas
      .filter(b => !yaEstan.has(b.idBodega))
      .map(b => ({ idBodega: b.idBodega, etiqueta: b.nombre + ' · sin existencias' }));
    l.opciones = [...conStock, ...resto];
  }

  get porcentaje(): number {
    if (!this.totalLineas) return 0;
    return Math.round((this.lineasCompletadas / this.totalLineas) * 100);
  }

  get totalUnidades(): number {
    return this.pedido ? this.pedido.lineas.reduce((s, l) => s + l.cantidad, 0) : 0;
  }

  recalcular() {
    if (!this.pedido) return;
    this.totalLineas = this.pedido.lineas.length;
    this.lineasCompletadas = this.pedido.lineas.filter(l => l.pickingCompletado).length;
    if (this.totalLineas > 0 && this.lineasCompletadas === this.totalLineas) {
      this.estadoPicking = 'completo';
    } else if (this.lineasCompletadas === 0) {
      this.estadoPicking = 'pendiente';
    } else {
      this.estadoPicking = 'en_progreso';
    }
  }

  // ── Contador ────────────────────────────────────────────────────────────
  sumar(l: PickingLinea, delta: number) {
    const v = (l.cantidadRecogida || 0) + delta;
    l.cantidadRecogida = Math.max(0, Math.min(l.cantidad, v));
  }

  recogerTodo(l: PickingLinea) { l.cantidadRecogida = l.cantidad; }

  /** Teclear a mano puede pasarse; se acota al escribir, no al guardar. */
  acotar(l: PickingLinea) {
    if (l.cantidadRecogida == null || isNaN(l.cantidadRecogida)) { l.cantidadRecogida = 0; return; }
    l.cantidadRecogida = Math.max(0, Math.min(l.cantidad, Math.trunc(l.cantidadRecogida)));
  }

  /** Aviso, no bloqueo: el stock puede haberse movido desde que se cargó. */
  bodegaElegidaCorta(l: PickingLinea): boolean {
    if (!l.idBodegaPicking || !(l.cantidadRecogida > 0)) return false;
    const u = (l.ubicaciones ?? []).find(x => x.idBodega === l.idBodegaPicking);
    return (u?.cantidad ?? 0) < l.cantidadRecogida;
  }

  /** Lo que dice el aviso: «tiene 3 u.» o «no tiene existencias». */
  existenciasElegidas(l: PickingLinea): string {
    const u = (l.ubicaciones ?? []).find(x => x.idBodega === l.idBodegaPicking);
    return u ? u.cantidad + ' unidades' : 'sin existencias registradas';
  }

  guardarLinea(l: PickingLinea) {
    if (!this.pedido) return;
    if (l.cantidadRecogida == null || l.cantidadRecogida < 0) {
      this.mostrarToast('La cantidad recogida no puede ser negativa', true);
      return;
    }
    if (l.cantidadRecogida > 0 && !l.idBodegaPicking) {
      this.mostrarToast('Indica de qué bodega se recogió la línea', true);
      return;
    }
    if (l.cantidadRecogida > l.cantidad) {
      this.mostrarToast('Cantidad recogida no puede superar la cantidad del pedido (máximo: ' + l.cantidad + ')', true);
      return;
    }
    // El estado se deriva de la cantidad; el usuario no debe mantener dos
    // controles que pueden contradecirse entre si.
    l.pickingCompletado = l.cantidad > 0 && l.cantidadRecogida === l.cantidad;
    l.guardando = true;
    const body = { idDetalle: l.idDetalle, cantidadRecogida: l.cantidadRecogida,
                   pickingCompletado: l.pickingCompletado, idBodega: l.idBodegaPicking ?? null };
    this.http.put<PickingLinea>(`${environment.apiUrl}/picking/pedidos/${this.pedido.idPedido}/lineas`, body).subscribe({
      next: res => {
        l.cantidadRecogida = res.cantidadRecogida;
        l.pickingCompletado = res.pickingCompletado;
        l.pendiente = res.pendiente;
        l.guardando = false;
        this.recalcular();
        this.mostrarToast('Línea guardada correctamente');
      },
      error: (err) => { l.guardando = false; this.mostrarToast(err.error?.message || 'Error al guardar la línea', true); }
    });
  }

  claseLinea(l: PickingLinea): string {
    if (l.pickingCompletado && l.cantidadRecogida >= l.cantidad) return 'completa';
    if (l.cantidadRecogida > 0 && l.cantidadRecogida < l.cantidad) return 'parcial';
    if (l.pickingCompletado) return 'completa';
    return 'pendiente';
  }

  textoEstadoLinea(l: PickingLinea): string {
    if (l.pickingCompletado) return 'Recogida entera';
    if (l.cantidadRecogida > 0) return 'A medias · faltan ' + (l.cantidad - l.cantidadRecogida);
    return 'Sin empezar';
  }

  volver() { this.router.navigate(['/picking']); }

  estadoLabel(estado: string): string {
    switch (estado) {
      case 'pendiente': return 'Pendiente';
      case 'en_progreso': return 'En progreso';
      case 'completo': return 'Completo';
      default: return estado || '';
    }
  }

  tipoLabel(tipo: string): string {
    switch (tipo) {
      case 'personalizado': return 'Personalizado';
      case 'regalo': return 'Regalo';
      case 'corporativo': return 'Corporativo';
      default: return tipo || '';
    }
  }

  esUrgente(): boolean {
    if (!this.pedido || !this.pedido.fechaLimiteEntrega) return false;
    const diff = new Date(this.pedido.fechaLimiteEntrega).getTime() - Date.now();
    return diff >= 0 && diff <= 24 * 60 * 60 * 1000;
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
