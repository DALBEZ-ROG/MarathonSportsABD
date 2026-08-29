import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AuthService } from '../../../core/services/auth.service';
import { AppIconComponent } from '../../../shared/components/icon/icon.component';

interface Comprobante {
  idComprobante: number;
  numeroComprobante: string;
  fechaEmision: string;
  total: number;
  estado: string;
  idPedido: number;
}

interface DetallePedido {
  idDetalle: number;
  productoId: number;
  productoNombre: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

interface PedidoDetalle {
  idPedido: number;
  numeroPedido: string;
  fechaPedido: string;
  total: number;
  estado: string;
  observaciones: string;
  clienteNombre: string;
  usuarioNombre: string;
  detalles: DetallePedido[];
  esPedidoEspecial: boolean;
  tipoEspecial: string;
  notaEspecial: string;
  fechaLimiteEntrega: string;
  numeroHu: string;
  transportista: string;
  regionDestino: string;
  fechaEmpaque: string;
}

@Component({
  selector: 'app-pedido-detalle',
  standalone: true,
  imports: [CommonModule, AppIconComponent],
  template: `
    <div class="container" *ngIf="pedido">
      <header class="cab">
        <div class="cab-txt">
          <button class="btn-volver" (click)="volver()">
            <span class="flecha" aria-hidden="true">←</span> Pedidos
          </button>
          <h1>
            Pedido {{pedido.numeroPedido}}
            <span class="pill" [ngClass]="'e-'+pedido.estado">{{estadoLabel(pedido.estado)}}</span>
          </h1>
          <p class="sub">
            {{pedido.clienteNombre}}
            <span class="sep">·</span> {{pedido.fechaPedido | date:'dd/MM/yyyy HH:mm'}}
          </p>
        </div>

        <!-- Dónde va el pedido dentro del flujo. Antes había que deducirlo del
             color de una etiqueta. -->
        <ol class="ruta" *ngIf="pedido.estado !== 'anulado'">
          <li *ngFor="let p of pasos" [class.hecho]="pasoHecho(p.clave)"
              [class.ahora]="pedido.estado === p.clave">
            <span class="punto"></span>{{p.titulo}}
          </li>
        </ol>
        <p class="anulado" *ngIf="pedido.estado === 'anulado'">Pedido anulado</p>
      </header>

      <div class="datos">
        <div class="dato">
          <label>Cliente</label>
          <span>{{pedido.clienteNombre}}</span>
        </div>
        <div class="dato">
          <label>Registrado por</label>
          <span>{{pedido.usuarioNombre}}</span>
        </div>
        <div class="dato">
          <label>Líneas</label>
          <span>{{pedido.detalles.length}} · {{unidadesTotales}} unidades</span>
        </div>
        <div class="dato">
          <label>Total</label>
          <span class="cifra-total">\${{pedido.total | number:'1.2-2'}}</span>
        </div>
      </div>

      <div class="obs" *ngIf="pedido.observaciones">
        <label>Observaciones</label>
        <span>{{pedido.observaciones}}</span>
      </div>

      <!-- Pedido especial. Antes eran tres recuadros anidados que decían
           "Corporativo" tres veces; ahora es una banda con lo que no se repite. -->
      <div class="especial" *ngIf="pedido.esPedidoEspecial" [class.urge]="esUrgente()">
        <div class="esp-cab">
          <span class="esp-chip">Pedido especial · {{tipoLabel(pedido.tipoEspecial)}}</span>
          <span class="esp-fecha" *ngIf="pedido.fechaLimiteEntrega">
            Entrega antes del {{pedido.fechaLimiteEntrega | date:'dd/MM/yyyy HH:mm'}}
          </span>
          <span class="esp-urge inline-icon-text" *ngIf="esUrgente()">
            <app-icon name="warning" [size]="16"/> Urgente
          </span>
        </div>
        <p class="esp-nota" *ngIf="pedido.notaEspecial">«{{pedido.notaEspecial}}»</p>
        <p class="esp-expli">
          Un pedido especial <strong>se crea aunque no haya stock</strong>: existe
          para prepararse o fabricarse.
        </p>
      </div>

      <section class="bloque">
        <h3>Qué lleva el pedido</h3>
        <div class="tabla-scroll">
          <table class="detail-table">
            <thead>
              <tr><th>Producto</th><th class="num">Cantidad</th><th class="num">P. unitario</th><th class="num">Subtotal</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let d of pedido.detalles">
                <td>{{d.productoNombre}}</td>
                <td class="num">{{d.cantidad}}</td>
                <td class="num">\${{d.precioUnitario | number:'1.2-2'}}</td>
                <td class="num total">\${{d.subtotal | number:'1.2-2'}}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td>Total</td>
                <td class="num">{{unidadesTotales}}</td>
                <td></td>
                <td class="num total">\${{pedido.total | number:'1.2-2'}}</td>
              </tr>
            </tfoot>
          </table>
        </div>
        <p class="nota-precio">
          El precio unitario es el del catálogo en el momento de crear el pedido:
          no se negocia en la pantalla.
        </p>
      </section>

      <!-- Información de despacho -->
      <section class="bloque" *ngIf="pedido.numeroHu">
        <h3>Cómo se despachó</h3>
        <div class="datos">
          <div class="dato">
            <label>HU</label>
            <span>{{pedido.numeroHu}}</span>
          </div>
          <div class="dato">
            <label>Transportista</label>
            <span>{{pedido.transportista}}</span>
          </div>
          <div class="dato">
            <label>Región destino</label>
            <span>{{pedido.regionDestino}}</span>
          </div>
          <div class="dato">
            <label>Fecha de empaque</label>
            <span>{{pedido.fechaEmpaque | date:'dd/MM/yyyy HH:mm'}}</span>
          </div>
        </div>
        <button class="btn-entregado" *ngIf="pedido.estado === 'enviado' && puedeDespachar" (click)="marcarEntregado()">
          Marcar como entregado
        </button>
      </section>

      <!-- Comprobante interno -->
      <section class="bloque">
        <h3>Comprobante interno</h3>
        <div *ngIf="comprobante" class="comprobante">
          <div class="datos">
            <div class="dato">
              <label>Número</label>
              <span>{{comprobante.numeroComprobante}}</span>
            </div>
            <div class="dato">
              <label>Emitido</label>
              <span>{{comprobante.fechaEmision | date:'dd/MM/yyyy HH:mm'}}</span>
            </div>
            <div class="dato">
              <label>Total</label>
              <span class="cifra-total">\${{comprobante.total | number:'1.2-2'}}</span>
            </div>
          </div>
          <button class="btn-pdf" (click)="descargarPDF(comprobante.idComprobante, comprobante.numeroComprobante)" [disabled]="descargando">
            {{ descargando ? 'Generando…' : 'Descargar PDF' }}
          </button>
        </div>

        <div *ngIf="!comprobante">
          <p class="vacio" *ngIf="pedido.estado !== 'procesado'">
            Todavía no hay comprobante. Se genera cuando el pedido está
            <strong>procesado</strong>, que es cuando las unidades quedan reservadas.
          </p>
          <ng-container *ngIf="pedido.estado === 'procesado'">
            <p class="vacio">Este pedido aún no tiene comprobante generado.</p>
            <button class="btn-generar" *ngIf="isAdmin" (click)="generarComprobante()" [disabled]="generando">
              {{ generando ? 'Generando…' : 'Generar comprobante' }}
            </button>
            <p class="vacio" *ngIf="!isAdmin">Lo genera el Administrador.</p>
          </ng-container>
        </div>
      </section>

      <!-- Qué se puede hacer ahora -->
      <section class="bloque que-toca" *ngIf="puedesCambiarEstado()">
        <h3>Qué toca ahora</h3>
        <p class="sub">{{ explicacionEstado() }}</p>
        <div class="botones">
          <button *ngIf="pedido.estado==='pendiente'" class="btn-estado procesado" (click)="cambiarEstado('procesado')">Marcar como procesado</button>
          <button *ngIf="pedido.estado==='procesado'" class="btn-estado enviado" (click)="cambiarEstado('enviado')">Marcar como enviado</button>
          <button *ngIf="pedido.estado==='enviado'" class="btn-estado entregado" (click)="cambiarEstado('entregado')">Marcar como entregado</button>
          <button *ngIf="pedido.estado==='pendiente' || pedido.estado==='procesado'" class="btn-estado anulado" (click)="cambiarEstado('anulado')">Anular pedido</button>
        </div>
      </section>

      <!-- Solicitar devolucion -->
      <section class="bloque" *ngIf="pedido.estado === 'entregado'">
        <h3>Después de entregar</h3>
        <p class="sub">Si el cliente devuelve algo, la solicitud arranca aquí.</p>
        <button class="btn-estado procesado" (click)="irDevolucion()">Solicitar devolución</button>
      </section>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>

    <div class="spinner" *ngIf="!pedido">Cargando pedido...</div>
  `,
  styles: [`
    /* ── Cabecera (F74) ────────────────────────────────────────── */
    .cab { display: flex; justify-content: space-between; align-items: flex-start;
           gap: 1.5rem; flex-wrap: wrap; margin-bottom: 1.5rem; }
    .cab h1 { margin: 0 0 .3rem; font-size: 1.6rem; color: var(--ms-text);
              display: flex; align-items: center; gap: .75rem; flex-wrap: wrap; }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .92rem; line-height: 1.6; }
    .sep { color: var(--ms-text-muted); margin: 0 .35rem; }

    .pill { font-size: .68rem; font-weight: 700; letter-spacing: .06em;
            padding: .25rem .6rem; border-radius: 99px; color: #fff;
            text-transform: uppercase; }
    .e-pendiente { background: #64748b; }
    .e-procesado { background: #2563eb; }
    .e-enviado   { background: #7c3aed; }
    .e-entregado { background: #16a34a; }
    .e-anulado   { background: #dc2626; }

    /* La ruta del pedido: cinco puntos en vez de deducirlo del color. */
    .ruta { list-style: none; display: flex; gap: .2rem; margin: 0; padding: 0;
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
    .anulado { margin: 0; font-size: .8rem; color: #fca5a5; }

    /* ── Los datos de cabecera ─────────────────────────────────── */
    .datos { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
             gap: 1rem; }
    .dato { display: flex; flex-direction: column; gap: .25rem; min-width: 0; }
    .dato label { font-size: .7rem; text-transform: uppercase; letter-spacing: .05em;
                  color: var(--ms-text-muted); }
    .dato span { color: var(--ms-text); font-size: .95rem; }
    .cifra-total { color: var(--ms-gold-light); font-weight: 600; }

    .obs { margin-top: 1.1rem; padding-top: 1.1rem; border-top: 1px solid var(--ms-border);
           display: flex; flex-direction: column; gap: .25rem; }
    .obs label { font-size: .7rem; text-transform: uppercase; letter-spacing: .05em;
                 color: var(--ms-text-muted); }

    /* Las tarjetas sueltas de arriba pasan a un solo bloque. */
    .container > .datos { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
                          border-radius: var(--ms-radius); padding: 1.25rem 1.5rem; }
    .container > .obs { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
                        border-top: 1px solid var(--ms-border);
                        border-radius: var(--ms-radius); padding: 1rem 1.5rem;
                        margin-top: 1rem; }

    /* ── Pedido especial ───────────────────────────────────────── */
    .especial { margin-top: 1rem; background: var(--ms-gold-dim);
                border: 1px solid rgba(201,168,76,.35); border-radius: var(--ms-radius);
                padding: 1rem 1.3rem; }
    .especial.urge { border-color: rgba(220,38,38,.5); background: rgba(220,38,38,.08); }
    .esp-cab { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; }
    .esp-chip { font-size: .72rem; font-weight: 700; letter-spacing: .05em;
                text-transform: uppercase; color: var(--ms-gold-light); }
    .especial.urge .esp-chip { color: #fca5a5; }
    .esp-fecha { font-size: .84rem; color: var(--ms-text); }
    .esp-urge { font-size: .82rem; color: #fca5a5; display: inline-flex;
                align-items: center; gap: .35rem; }
    .esp-nota { margin: .6rem 0 0; font-size: .9rem; color: var(--ms-text);
                font-style: italic; }
    .esp-expli { margin: .5rem 0 0; font-size: .8rem; color: var(--ms-text-muted);
                 line-height: 1.6; }
    .esp-expli strong { color: rgba(255,255,255,0.8); }

    /* ── Los bloques ───────────────────────────────────────────── */
    .bloque { margin-top: 1.5rem; background: var(--ms-bg-card);
              border: 1px solid var(--ms-border); border-radius: var(--ms-radius);
              padding: 1.35rem 1.5rem; }
    .bloque h3 { display: block; margin: 0 0 .5rem; font-size: 1.02rem; color: var(--ms-text); }
    .bloque .sub { display: block; margin: 0 0 1.1rem; font-size: .85rem; max-width: 70ch; }
    .vacio { margin: 0 0 .8rem; font-size: .86rem; color: var(--ms-text-muted);
             line-height: 1.6; }
    .vacio strong { color: rgba(255,255,255,0.8); }

    .tabla-scroll { overflow-x: auto; }
    .detail-table { width: 100%; border-collapse: collapse; }
    .detail-table .num { text-align: right; }
    .detail-table tfoot td { border-top: 1px solid var(--ms-border); font-weight: 600;
                             padding-top: .7rem; color: var(--ms-text); }
    .nota-precio { margin: .9rem 0 0; font-size: .78rem; color: var(--ms-text-muted);
                   line-height: 1.6; }

    .comprobante { display: flex; flex-direction: column; gap: 1.1rem;
                   align-items: flex-start; }
    .comprobante .datos { width: 100%; }

    /* Ojo: .acciones ya existe en styles.scss con display:flex; usar ese
       nombre aqui aplanaba titulo, explicacion y botones en un renglon. */
    .que-toca { display: block; }
    .botones { display: flex; gap: .6rem; flex-wrap: wrap; }

    @media (max-width: 620px) {
      .cab h1 { font-size: 1.3rem; }
      .ruta { font-size: .7rem; }
    }
  `]
})
export class PedidoDetalleComponent implements OnInit {
  pedido: PedidoDetalle | null = null;
  comprobante: Comprobante | null = null;
  descargando = false;
  generando = false;
  isAdmin = false;
  puedeDespachar = false;
  toast = '';
  toastError = false;

  /**
   * La ruta del pedido, para pintar dónde va (F74).
   *
   * <p>Antes el estado era una etiqueta de color y había que saberse el orden de
   * memoria. `anulado` no está aquí a propósito: no es un paso del camino, es
   * salirse de él, y se enseña aparte.
   */
  readonly pasos = [
    { clave: 'pendiente', titulo: 'Pendiente' },
    { clave: 'procesado', titulo: 'Procesado' },
    { clave: 'enviado',   titulo: 'Enviado' },
    { clave: 'entregado', titulo: 'Entregado' }
  ];

  constructor(private route: ActivatedRoute, private router: Router, private http: HttpClient, private authService: AuthService) {
    this.isAdmin = this.authService.hasRol('Administrador');
    this.puedeDespachar = this.authService.hasRol('Administrador') || this.authService.hasRol('Operador de Bodega');
  }

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.cargarPedido(+id);
      this.cargarComprobante(+id);
    }
  }

  cargarPedido(id: number) {
    this.http.get<PedidoDetalle>(`${environment.apiUrl}/pedidos/${id}`).subscribe({
      next: res => { this.pedido = res; },
      error: () => { this.mostrarToast('Error al cargar el pedido', true); }
    });
  }

  cargarComprobante(idPedido: number) {
    this.http.get<Comprobante>(`${environment.apiUrl}/comprobantes/pedido/${idPedido}`).subscribe({
      next: res => { this.comprobante = res; },
      error: () => { this.comprobante = null; }
    });
  }

  descargarPDF(id: number, numero: string) {
    this.descargando = true;
    this.http.get(`${environment.apiUrl}/comprobantes/${id}/pdf`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `comprobante-${numero}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.descargando = false;
      },
      error: () => { this.descargando = false; this.mostrarToast('Error al descargar el PDF', true); }
    });
  }

  generarComprobante() {
    if (!this.pedido) return;
    this.generando = true;
    this.http.post<Comprobante>(`${environment.apiUrl}/comprobantes/pedido/${this.pedido.idPedido}/generar`, {}).subscribe({
      next: res => { this.comprobante = res; this.generando = false; this.mostrarToast('Comprobante generado correctamente'); },
      error: (err) => { this.generando = false; this.mostrarToast(err.error?.message || 'Error al generar el comprobante', true); }
    });
  }

  get unidadesTotales(): number {
    return this.pedido ? this.pedido.detalles.reduce((s, d) => s + d.cantidad, 0) : 0;
  }

  estadoLabel(estado: string): string {
    const p = this.pasos.find(x => x.clave === estado);
    return p ? p.titulo : (estado === 'anulado' ? 'Anulado' : estado || '');
  }

  /** Un paso ya pasado se pinta hecho; el actual va aparte. */
  pasoHecho(clave: string): boolean {
    if (!this.pedido) return false;
    const iActual = this.pasos.findIndex(p => p.clave === this.pedido!.estado);
    const iPaso = this.pasos.findIndex(p => p.clave === clave);
    return iActual >= 0 && iPaso <= iActual;
  }

  /**
   * Qué significa el botón que se va a pulsar.
   *
   * <p>«Marcar como procesado» no dice que ahí es donde se <b>reservan</b> las
   * unidades, que es lo único importante del paso y lo que no se puede deshacer
   * sin soltar la reserva a mano.
   */
  explicacionEstado(): string {
    switch (this.pedido?.estado) {
      case 'pendiente':
        return 'Procesarlo reserva las unidades en bodega. Es todo o nada: si una '
             + 'línea no cabe, no se reserva ninguna.';
      case 'procesado':
        return 'Las unidades están reservadas. Recogerlo y empacarlo se hace desde '
             + 'Picking y Empaque; marcarlo como enviado cierra la salida.';
      case 'enviado':
        return 'Va de camino. Se marca como entregado cuando el cliente lo recibe.';
      default:
        return '';
    }
  }

  puedesCambiarEstado(): boolean {
    if (!this.pedido) return false;
    return this.pedido.estado !== 'entregado' && this.pedido.estado !== 'anulado';
  }

  cambiarEstado(nuevoEstado: string) {
    if (!this.pedido) return;
    this.http.put<PedidoDetalle>(`${environment.apiUrl}/pedidos/${this.pedido.idPedido}/estado`, { estado: nuevoEstado }).subscribe({
      next: res => {
        this.pedido!.estado = res.estado;
        this.mostrarToast('Estado actualizado a: ' + res.estado);
      },
      error: (err) => { this.mostrarToast(err.error?.message || 'Error al cambiar estado', true); }
    });
  }

  volver() { this.router.navigate(['/pedidos']); }

  marcarEntregado() {
    if (!this.pedido) return;
    this.http.put<PedidoDetalle>(`${environment.apiUrl}/pedidos/${this.pedido.idPedido}/estado`, { estado: 'entregado' }).subscribe({
      next: res => {
        this.pedido!.estado = res.estado;
        this.mostrarToast('Pedido marcado como entregado');
      },
      error: (err) => { this.mostrarToast(err.error?.message || 'Error al marcar como entregado', true); }
    });
  }

  irDevolucion() {
    if (!this.pedido) return;
    this.router.navigate(['/devoluciones/nueva', this.pedido.idPedido]);
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
    const limite = new Date(this.pedido.fechaLimiteEntrega).getTime();
    const ahora = Date.now();
    const diff = limite - ahora;
    return diff >= 0 && diff <= 24 * 60 * 60 * 1000;
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
