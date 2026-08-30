import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { SearchableSelectComponent } from '../../shared/components/searchable-select/searchable-select.component';
import { ESTILOS_AUDITORIA } from './auditoria.estilos';

export interface CambioDato {
  id: number; fecha: string; tabla: string; pkValor: string; operacion: string;
  campo: string | null; valorAnterior: string | null; valorNuevo: string | null;
  usuarioBd: string; idUsuario: number | null; usuarioNombre: string | null; txid: number;
}
interface PageResp<T> { content: T[]; totalElements: number; totalPages: number; currentPage: number; pageSize: number; }

/**
 * «Qué dato cambió, de qué valor a qué valor, y quién lo cambió.»
 *
 * Lee `auditoria_cambios`, que existe en la base desde la F40 pero hasta ahora
 * solo se consultaba por psql. Es la única de las tres bitácoras que guarda el
 * valor ANTERIOR: el historial de inventario solo sabe de stock, y el log de
 * acciones dice qué se hizo pero no sobre qué dato.
 */
@Component({
  selector: 'app-cambios-datos',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectComponent],
  template: `
    <p class="audit-que">
      El <strong>antes y el después de cada campo</strong>, escrito por un disparador de
      la base de datos. Un cambio que toca tres columnas deja tres filas: el
      <em>número de transacción</em> es lo que permite volver a juntarlas y ver que
      fueron un mismo acto.
    </p>

    <div class="audit-filters">
      <div class="filter-field ancho">
        <label>Quién</label>
        <app-searchable-select
          [items]="usuarios"
          labelKey="etiqueta"
          valueKey="idUsuario"
          placeholder="Cualquiera — escribe un nombre…"
          [(ngModel)]="fUsuario"
          [ngModelOptions]="{ standalone: true }"/>
      </div>
      <div class="filter-field">
        <label>Dónde (tabla)</label>
        <select [(ngModel)]="fTabla" (ngModelChange)="cargarCampos()">
          <option [ngValue]="null">Todas</option>
          <option *ngFor="let t of tablas" [ngValue]="t">{{ t }}</option>
        </select>
      </div>
      <div class="filter-field">
        <label>Qué campo</label>
        <select [(ngModel)]="fCampo" [disabled]="!fTabla">
          <option [ngValue]="null">{{ fTabla ? 'Todos' : 'Elige una tabla' }}</option>
          <option *ngFor="let c of campos" [ngValue]="c">{{ c }}</option>
        </select>
      </div>
      <div class="filter-field">
        <label>Qué pasó</label>
        <select [(ngModel)]="fOperacion">
          <option [ngValue]="null">Todo</option>
          <option value="INSERT">Se creó</option>
          <option value="UPDATE">Se modificó</option>
          <option value="DELETE">Se borró</option>
        </select>
      </div>
      <div class="filter-field ancho">
        <label>Buscar un valor</label>
        <input type="text" [(ngModel)]="fTexto" placeholder="Un precio, un correo, un nombre…"/>
      </div>
      <div class="filter-field">
        <label>Fila (clave)</label>
        <input type="text" [(ngModel)]="fPk" placeholder="id de la fila"/>
      </div>
      <div class="filter-field">
        <label>Desde</label>
        <input type="date" [(ngModel)]="fDesde"/>
      </div>
      <div class="filter-field">
        <label>Hasta</label>
        <input type="date" [(ngModel)]="fHasta"/>
      </div>
      <div class="filter-field filter-btn-wrap">
        <button class="btn-search" (click)="pagina = 0; cargar()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          Buscar
        </button>
      </div>
    </div>

    <div class="atajos">
      <span>Atajos:</span>
      <button type="button" *ngFor="let a of atajos" (click)="aplicarAtajo(a.dias)">{{ a.etiqueta }}</button>
      <button type="button" class="atajo-limpiar" (click)="limpiar()">Quitar todos los filtros</button>
    </div>

    <p class="cuantos" *ngIf="!cargando && total >= 0">
      <strong>{{ total | number }}</strong>
      {{ total === 1 ? 'cambio de dato' : 'cambios de dato' }} con esos filtros.
      <span class="pista" *ngIf="filtrandoPorTx">
        · Viendo una sola transacción — <a href="javascript:void(0)" (click)="quitarTx()">volver a todo</a>
      </span>
    </p>

    <div class="spinner" *ngIf="cargando">Cargando...</div>

    <div class="audit-table-wrap" *ngIf="!cargando">
      <table class="audit-table">
        <thead>
          <tr>
            <th>Fecha</th><th>Quién</th><th>Dónde</th><th>Fila</th>
            <th>Qué pasó</th><th>Campo</th><th>Antes → Después</th><th></th>
          </tr>
        </thead>
        <tbody>
          <ng-container *ngFor="let c of cambios">
            <tr [class.abierta]="abierta === c.id" (click)="alternar(c)">
              <td class="fecha-col">{{ c.fecha | date:'dd/MM/yyyy HH:mm:ss' }}</td>
              <td>
                <span *ngIf="c.usuarioNombre">{{ c.usuarioNombre }}</span>
                <!-- Usuario de aplicación vacío con usuario de base de datos
                     presente = el cambio NO pasó por la aplicación. Es el caso
                     que más le interesa a una auditoría, y por eso se marca en
                     vez de dejarlo en un guion. Ver AUDITORIA.md §2. -->
                <span *ngIf="!c.usuarioNombre" class="fuera-app"
                      title="No se cambió desde la aplicación: por psql, por un script, o por alguien con la credencial de {{ c.usuarioBd }}.">
                  fuera de la app
                </span>
              </td>
              <td><span class="modulo-badge" [attr.data-modulo]="c.tabla">{{ c.tabla }}</span></td>
              <td class="ip-col">{{ c.pkValor }}</td>
              <td><span class="op-chip" [attr.data-op]="c.operacion">{{ etiquetaOp(c.operacion) }}</span></td>
              <td>{{ c.campo || '—' }}</td>
              <td class="valores-col">
                <span class="antes" *ngIf="c.valorAnterior !== null">{{ corto(c.valorAnterior) }}</span>
                <span class="flecha" *ngIf="c.valorAnterior !== null && c.valorNuevo !== null">→</span>
                <span class="despues" *ngIf="c.valorNuevo !== null">{{ corto(c.valorNuevo) }}</span>
                <span class="nada" *ngIf="c.valorAnterior === null && c.valorNuevo === null">—</span>
              </td>
              <td class="chevron-col">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                     [style.transform]="abierta === c.id ? 'rotate(180deg)' : ''">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </td>
            </tr>
            <tr *ngIf="abierta === c.id" class="detalle-fila">
              <td colspan="8">
                <div class="detalle">
                  <div class="detalle-valores">
                    <div>
                      <span class="detalle-tit">Antes</span>
                      <pre>{{ c.valorAnterior === null ? '(no existía)' : c.valorAnterior }}</pre>
                    </div>
                    <div>
                      <span class="detalle-tit">Después</span>
                      <pre>{{ c.valorNuevo === null ? '(se borró)' : c.valorNuevo }}</pre>
                    </div>
                  </div>
                  <div class="detalle-meta">
                    <span><b>Cuenta de PostgreSQL:</b> {{ c.usuarioBd }}</span>
                    <span><b>Transacción:</b> {{ c.txid }}</span>
                    <button class="btn-tx" (click)="verTransaccion(c.txid); $event.stopPropagation()">
                      Ver todo lo que cambió en este mismo acto
                    </button>
                  </div>
                </div>
              </td>
            </tr>
          </ng-container>
          <tr *ngIf="cambios.length === 0">
            <td colspan="8" class="empty">Ningún cambio con esos filtros</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pag" *ngIf="totalPaginas > 1">
      <button (click)="pagina = pagina - 1; cargar()" [disabled]="pagina === 0">← Anterior</button>
      <span>Página {{ pagina + 1 }} de {{ totalPaginas | number }}</span>
      <button (click)="pagina = pagina + 1; cargar()" [disabled]="pagina >= totalPaginas - 1">Siguiente →</button>
    </div>
  `,
  styles: [ESTILOS_AUDITORIA, `
    .audit-table tbody tr:not(.detalle-fila) { cursor: pointer; }
    .audit-table tr.abierta td { background: rgba(201,168,76,.06); }
    .chevron-col { width: 24px; color: rgba(255,255,255,.3); }
    .chevron-col svg { transition: transform .2s; }

    .valores-col { max-width: 320px; }
    .antes   { color: #EF9A9A; text-decoration: line-through; opacity: .75; }
    .despues { color: #81C784; }
    .flecha  { color: rgba(255,255,255,.25); margin: 0 .4rem; }
    .nada    { color: rgba(255,255,255,.25); }

    /* El hallazgo que más importa de esta pantalla merece verse desde lejos. */
    .fuera-app { display: inline-block; padding: .15rem .55rem; border-radius: 6px;
                 font-size: .72rem; background: rgba(217,119,6,.16); color: #fcd34d;
                 border: 1px solid rgba(217,119,6,.4); cursor: help; }

    .op-chip { display: inline-block; padding: .2rem .6rem; border-radius: 6px;
               font-size: .74rem; border: 1px solid transparent; }
    .op-chip[data-op="INSERT"] { background: rgba(76,175,80,.12); color: #81C784;
                                 border-color: rgba(76,175,80,.25); }
    .op-chip[data-op="UPDATE"] { background: rgba(100,181,246,.12); color: #90CAF9;
                                 border-color: rgba(100,181,246,.25); }
    .op-chip[data-op="DELETE"] { background: rgba(229,115,115,.12); color: #EF9A9A;
                                 border-color: rgba(229,115,115,.25); }

    .detalle-fila td { padding: 0 !important; background: rgba(0,0,0,.2); }
    .detalle { padding: 1rem 1.25rem; }
    .detalle-valores { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
    .detalle-tit { font-size: .68rem; text-transform: uppercase; letter-spacing: .8px;
                   color: rgba(255,255,255,.35); }
    .detalle pre { margin: .35rem 0 0; padding: .6rem .75rem; max-height: 220px;
                   overflow: auto; white-space: pre-wrap; word-break: break-word;
                   background: rgba(255,255,255,.03); border: 1px solid rgba(255,255,255,.06);
                   border-radius: 8px; font-size: .78rem; color: rgba(255,255,255,.75);
                   font-family: 'Cascadia Code', 'Fira Code', monospace; }
    .detalle-meta { display: flex; gap: 1.5rem; align-items: center; flex-wrap: wrap;
                    margin-top: .9rem; font-size: .78rem; color: rgba(255,255,255,.45); }
    .detalle-meta b { color: rgba(255,255,255,.6); font-weight: 500; }
    .btn-tx { background: rgba(201,168,76,.1); border: 1px solid rgba(201,168,76,.25);
              color: #C9A84C; padding: .35rem .8rem; border-radius: 99px;
              font-size: .76rem; cursor: pointer; font-family: inherit; }
    .btn-tx:hover { background: rgba(201,168,76,.18); }

    .atajo-limpiar { margin-left: auto; }
    .pista { color: rgba(255,255,255,.35); }
    .pista a { color: #C9A84C; }

    @media (max-width: 900px) {
      .detalle-valores { grid-template-columns: 1fr; }
    }
  `]
})
export class CambiosDatosComponent implements OnInit {

  /** Filtros que puede fijar la pestaña de rastro al saltar aquí. */
  @Input() set filtroInicial(f: { idUsuario?: number; tabla?: string; operacion?: string } | null) {
    if (!f) { return; }
    this.fUsuario = f.idUsuario ?? null;
    this.fTabla = f.tabla ?? null;
    this.fOperacion = f.operacion ?? null;
    this.pagina = 0;
    if (this.fTabla) { this.cargarCampos(); }
    this.cargar();
  }

  @Output() cargado = new EventEmitter<number>();

  cambios: CambioDato[] = [];
  cargando = false;
  pagina = 0;
  totalPaginas = 0;
  total = -1;
  abierta: number | null = null;

  tablas: string[] = [];
  campos: string[] = [];
  usuarios: Array<{ idUsuario: number; etiqueta: string }> = [];

  fUsuario: number | null = null;
  fTabla: string | null = null;
  fCampo: string | null = null;
  fOperacion: string | null = null;
  fTexto = '';
  fPk = '';
  fDesde = '';
  fHasta = '';
  fTxid: number | null = null;

  readonly atajos = [
    { etiqueta: 'Hoy', dias: 0 },
    { etiqueta: 'Últimos 7 días', dias: 6 },
    { etiqueta: 'Últimos 30 días', dias: 29 },
    { etiqueta: 'Todo', dias: -1 }
  ];

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<string[]>(`${environment.apiUrl}/auditoria/cambios/tablas`)
      .subscribe({ next: t => this.tablas = t, error: () => this.tablas = [] });

    this.http.get<any>(`${environment.apiUrl}/usuarios?page=0&size=200`).subscribe({
      next: r => this.usuarios = (r?.content ?? []).map((u: any) => ({
        idUsuario: u.idUsuario, etiqueta: `${u.nombre} ${u.apellido}`
      })),
      error: () => this.usuarios = []
    });

    this.cargar();
  }

  get filtrandoPorTx(): boolean { return this.fTxid !== null; }

  cargarCampos(): void {
    this.fCampo = null;
    if (!this.fTabla) { this.campos = []; return; }
    this.http.get<string[]>(`${environment.apiUrl}/auditoria/cambios/campos`,
                            { params: new HttpParams().set('tabla', this.fTabla) })
      .subscribe({ next: c => this.campos = c, error: () => this.campos = [] });
  }

  cargar(): void {
    this.cargando = true;
    this.abierta = null;
    let p = new HttpParams().set('page', this.pagina).set('size', 20);
    if (this.fUsuario) { p = p.set('idUsuario', this.fUsuario); }
    if (this.fTabla) { p = p.set('tabla', this.fTabla); }
    if (this.fCampo) { p = p.set('campo', this.fCampo); }
    if (this.fOperacion) { p = p.set('operacion', this.fOperacion); }
    if (this.fTexto.trim()) { p = p.set('texto', this.fTexto.trim()); }
    if (this.fPk.trim()) { p = p.set('pkValor', this.fPk.trim()); }
    if (this.fTxid !== null) { p = p.set('txid', this.fTxid); }
    if (this.fDesde) { p = p.set('desde', this.fDesde + 'T00:00:00'); }
    if (this.fHasta) { p = p.set('hasta', this.fHasta + 'T23:59:59'); }

    this.http.get<PageResp<CambioDato>>(`${environment.apiUrl}/auditoria/cambios`, { params: p })
      .subscribe({
        next: r => {
          this.cambios = r.content;
          this.totalPaginas = r.totalPages;
          this.total = r.totalElements;
          this.cargando = false;
          this.cargado.emit(r.totalElements);
        },
        error: () => { this.cargando = false; this.cambios = []; this.total = 0; }
      });
  }

  alternar(c: CambioDato): void {
    this.abierta = this.abierta === c.id ? null : c.id;
  }

  /** Reagrupa las filas sueltas de un mismo acto. */
  verTransaccion(txid: number): void {
    this.fTxid = txid;
    this.pagina = 0;
    this.cargar();
  }

  quitarTx(): void {
    this.fTxid = null;
    this.pagina = 0;
    this.cargar();
  }

  limpiar(): void {
    this.fUsuario = null; this.fTabla = null; this.fCampo = null;
    this.fOperacion = null; this.fTexto = ''; this.fPk = '';
    this.fDesde = ''; this.fHasta = ''; this.fTxid = null;
    this.campos = [];
    this.pagina = 0;
    this.cargar();
  }

  aplicarAtajo(dias: number): void {
    const hoy = new Date();
    this.fHasta = dias < 0 ? '' : this.comoFecha(hoy);
    this.fDesde = dias < 0 ? '' : this.comoFecha(new Date(hoy.getTime() - dias * 86400000));
    this.pagina = 0;
    this.cargar();
  }

  etiquetaOp(op: string): string {
    return op === 'INSERT' ? 'se creó' : op === 'DELETE' ? 'se borró' : 'se modificó';
  }

  /**
   * Los valores de un INSERT vienen como la fila entera en JSON y pueden ocupar
   * cientos de caracteres. En la lista se recorta; al desplegar la fila se ve
   * completo.
   */
  corto(valor: string | null): string {
    if (valor === null) { return ''; }
    return valor.length > 40 ? valor.slice(0, 40) + '…' : valor;
  }

  private comoFecha(d: Date): string {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
         + '-' + String(d.getDate()).padStart(2, '0');
  }
}
