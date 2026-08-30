import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { SearchableSelectComponent } from '../../shared/components/searchable-select/searchable-select.component';
import { ESTILOS_AUDITORIA } from './auditoria.estilos';

interface Linea {
  clave: string; detalle: string; veces: number; primera: string; ultima: string;
}
interface Rastro {
  idUsuario: number; usuarioNombre: string | null;
  totalAcciones: number; totalCambios: number; totalMovimientos: number;
  primeraHuella: string | null; ultimaHuella: string | null;
  porModulo: Linea[]; porTabla: Linea[]; porBodega: Linea[];
}

/** A dónde saltar cuando se pincha una línea del desglose. */
export interface SaltoRastro {
  destino: 'cambios' | 'logs';
  idUsuario: number;
  tabla?: string;
  operacion?: string;
  modulo?: string;
}

/**
 * «¿En qué partes del sistema tocó algo esta persona, y qué tocó?»
 *
 * Es la pregunta con la que se entra a una auditoría, y hasta ahora no la
 * contestaba nadie: había que ir pestaña por pestaña filtrando por el mismo
 * nombre. Aquí se cruzan las tres bitácoras de una vez.
 *
 * No se fusionan en una sola lista cronológica a propósito. Un UNION de tres
 * tablas de más de un millón de filas hay que materializarlo y ordenarlo entero
 * para poder dar la primera página. Se enseñan RECUENTOS POR SITIO —que es lo
 * que contesta la pregunta— y cada línea salta al detalle ya filtrado, que sí
 * usa índice.
 */
@Component({
  selector: 'app-rastro-usuario',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectComponent],
  template: `
    <p class="audit-que">
      Elige una persona y un periodo: sale <strong>todo lo que tocó</strong>, agrupado por
      dónde lo tocó. Las tres columnas vienen de las tres bitácoras del sistema, que
      no dicen lo mismo — una guarda la acción, otra el dato, otra el stock.
      <strong>Pincha cualquier línea</strong> para ver esos registros uno a uno.
    </p>

    <div class="audit-filters">
      <div class="filter-field ancho">
        <label>Quién</label>
        <app-searchable-select
          [items]="usuarios"
          labelKey="etiqueta"
          valueKey="idUsuario"
          placeholder="Escribe un nombre…"
          [(ngModel)]="idUsuario"
          [ngModelOptions]="{ standalone: true }"/>
      </div>
      <div class="filter-field">
        <label>Desde</label>
        <input type="date" [(ngModel)]="desde"/>
      </div>
      <div class="filter-field">
        <label>Hasta</label>
        <input type="date" [(ngModel)]="hasta"/>
      </div>
      <div class="filter-field filter-btn-wrap">
        <button class="btn-search" (click)="cargar()" [disabled]="!idUsuario">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          Ver su rastro
        </button>
      </div>
    </div>

    <div class="atajos">
      <span>Atajos:</span>
      <button type="button" *ngFor="let a of atajos" (click)="aplicarAtajo(a.dias)">{{ a.etiqueta }}</button>
    </div>

    <p class="sin-nadie" *ngIf="!idUsuario && !cargando">
      Elige una persona arriba para ver por dónde ha pasado.
    </p>

    <div class="spinner" *ngIf="cargando">Cargando...</div>

    <div *ngIf="rastro && !cargando">
      <div class="kpis">
        <div class="kpi">
          <span class="kpi-num">{{ rastro.totalAcciones | number }}</span>
          <span class="kpi-lab">acciones registradas</span>
          <span class="kpi-sub">qué hizo</span>
        </div>
        <div class="kpi">
          <span class="kpi-num">{{ rastro.totalCambios | number }}</span>
          <span class="kpi-lab">campos cambiados</span>
          <span class="kpi-sub">qué dato tocó</span>
        </div>
        <div class="kpi">
          <span class="kpi-num">{{ rastro.totalMovimientos | number }}</span>
          <span class="kpi-lab">movimientos de stock</span>
          <span class="kpi-sub">qué mercancía movió</span>
        </div>
        <div class="kpi kpi-ancho">
          <span class="kpi-lab">Su rastro va de</span>
          <span class="kpi-fechas">
            {{ rastro.primeraHuella ? (rastro.primeraHuella | date:'dd/MM/yyyy HH:mm') : '—' }}
            <em>a</em>
            {{ rastro.ultimaHuella ? (rastro.ultimaHuella | date:'dd/MM/yyyy HH:mm') : '—' }}
          </span>
        </div>
      </div>

      <p class="nada-de-nada"
         *ngIf="!rastro.totalAcciones && !rastro.totalCambios && !rastro.totalMovimientos">
        Esta persona no dejó ningún rastro en el periodo elegido. No significa que no
        entrara: significa que no cambió nada.
      </p>

      <div class="rastro-grid">
        <section class="rastro-bloque" *ngIf="rastro.porModulo.length">
          <h3>Qué hizo, por módulo</h3>
          <p class="bloque-que">Del log de acciones. Es el lenguaje del negocio: aprobó, anuló, reembolsó.</p>
          <table class="audit-table">
            <thead><tr><th>Módulo</th><th>Acción</th><th class="num">Veces</th><th>Última vez</th></tr></thead>
            <tbody>
              <tr *ngFor="let l of rastro.porModulo" class="clicable"
                  (click)="saltar({ destino: 'logs', idUsuario: rastro!.idUsuario, modulo: l.clave })">
                <td><span class="modulo-badge" [attr.data-modulo]="l.clave">{{ l.clave }}</span></td>
                <td><span class="accion-chip">{{ l.detalle }}</span></td>
                <td class="num">{{ l.veces | number }}</td>
                <td class="fecha-col">{{ l.ultima | date:'dd/MM/yy HH:mm' }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section class="rastro-bloque" *ngIf="rastro.porTabla.length">
          <h3>Qué dato cambió, por tabla</h3>
          <p class="bloque-que">De la auditoría campo a campo. Aquí está el antes y el después.</p>
          <table class="audit-table">
            <thead><tr><th>Tabla</th><th>Qué pasó</th><th class="num">Veces</th><th>Última vez</th></tr></thead>
            <tbody>
              <tr *ngFor="let l of rastro.porTabla" class="clicable"
                  (click)="saltar({ destino: 'cambios', idUsuario: rastro!.idUsuario, tabla: l.clave, operacion: l.detalle })">
                <td><span class="modulo-badge" [attr.data-modulo]="l.clave">{{ l.clave }}</span></td>
                <td><span class="accion-chip">{{ etiquetaOp(l.detalle) }}</span></td>
                <td class="num">{{ l.veces | number }}</td>
                <td class="fecha-col">{{ l.ultima | date:'dd/MM/yy HH:mm' }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <section class="rastro-bloque" *ngIf="rastro.porBodega.length">
          <h3>Qué stock movió, por bodega</h3>
          <p class="bloque-que">Del historial de inventario. Lo escribe un disparador de la base.</p>
          <table class="audit-table">
            <thead><tr><th>Bodega</th><th>Motivo</th><th class="num">Veces</th><th>Última vez</th></tr></thead>
            <tbody>
              <tr *ngFor="let l of rastro.porBodega">
                <td>{{ l.clave }}</td>
                <td><span class="motivo-chip">{{ l.detalle }}</span></td>
                <td class="num">{{ l.veces | number }}</td>
                <td class="fecha-col">{{ l.ultima | date:'dd/MM/yy HH:mm' }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </div>
  `,
  styles: [ESTILOS_AUDITORIA, `
    .sin-nadie { margin: 2.5rem 0; text-align: center; font-size: .9rem;
                 color: rgba(255,255,255,.3); }
    .nada-de-nada { margin: 1.2rem 0; padding: .9rem 1.1rem; font-size: .85rem;
                    line-height: 1.6; color: rgba(255,255,255,.5);
                    background: rgba(255,255,255,.02);
                    border-left: 2px solid rgba(255,255,255,.15);
                    border-radius: 0 8px 8px 0; }

    .kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
            gap: 1rem; margin: 1.5rem 0; }
    .kpi { display: flex; flex-direction: column; gap: .2rem; padding: 1.1rem 1.25rem;
           background: rgba(255,255,255,.02); border: 1px solid rgba(255,255,255,.06);
           border-radius: 14px; }
    .kpi-num { font-size: 1.8rem; font-weight: 300; color: #F4E28D;
               font-variant-numeric: tabular-nums; line-height: 1.1; }
    .kpi-lab { font-size: .78rem; color: rgba(255,255,255,.6); }
    .kpi-sub { font-size: .68rem; color: rgba(255,255,255,.28);
               text-transform: uppercase; letter-spacing: .8px; }
    .kpi-ancho { grid-column: span 2; justify-content: center; }
    .kpi-fechas { font-size: .95rem; color: rgba(255,255,255,.75); margin-top: .25rem; }
    .kpi-fechas em { color: rgba(255,255,255,.3); font-style: normal; margin: 0 .4rem; }

    .rastro-grid { display: grid; gap: 1.5rem; }
    .rastro-bloque h3 { margin: 0 0 .2rem; font-size: 1rem; font-weight: 400;
                        color: rgba(255,255,255,.85); letter-spacing: .3px; }
    .bloque-que { margin: 0 0 .7rem; font-size: .78rem; color: rgba(255,255,255,.32); }
    .rastro-bloque .audit-table { border: 1px solid rgba(255,255,255,.06); border-radius: 14px; }

    .clicable { cursor: pointer; }
    .clicable:hover td { background: rgba(201,168,76,.07); }

    @media (max-width: 700px) { .kpi-ancho { grid-column: span 1; } }
  `]
})
export class RastroUsuarioComponent implements OnInit {

  @Output() salto = new EventEmitter<SaltoRastro>();

  usuarios: Array<{ idUsuario: number; etiqueta: string }> = [];
  idUsuario: number | null = null;
  desde = '';
  hasta = '';
  rastro: Rastro | null = null;
  cargando = false;

  readonly atajos = [
    { etiqueta: 'Hoy', dias: 0 },
    { etiqueta: 'Últimos 7 días', dias: 6 },
    { etiqueta: 'Últimos 30 días', dias: 29 },
    { etiqueta: 'Todo', dias: -1 }
  ];

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<any>(`${environment.apiUrl}/usuarios?page=0&size=200`).subscribe({
      next: r => this.usuarios = (r?.content ?? []).map((u: any) => ({
        idUsuario: u.idUsuario, etiqueta: `${u.nombre} ${u.apellido}`
      })),
      error: () => this.usuarios = []
    });
  }

  cargar(): void {
    if (!this.idUsuario) { return; }
    this.cargando = true;
    let p = new HttpParams().set('idUsuario', this.idUsuario);
    if (this.desde) { p = p.set('desde', this.desde + 'T00:00:00'); }
    if (this.hasta) { p = p.set('hasta', this.hasta + 'T23:59:59'); }

    this.http.get<Rastro>(`${environment.apiUrl}/auditoria/rastro`, { params: p })
      .subscribe({
        next: r => { this.rastro = r; this.cargando = false; },
        error: () => { this.cargando = false; this.rastro = null; }
      });
  }

  aplicarAtajo(dias: number): void {
    const hoy = new Date();
    this.hasta = dias < 0 ? '' : this.comoFecha(hoy);
    this.desde = dias < 0 ? '' : this.comoFecha(new Date(hoy.getTime() - dias * 86400000));
    if (this.idUsuario) { this.cargar(); }
  }

  saltar(s: SaltoRastro): void {
    this.salto.emit(s);
  }

  etiquetaOp(op: string): string {
    return op === 'INSERT' ? 'se creó' : op === 'DELETE' ? 'se borró' : 'se modificó';
  }

  private comoFecha(d: Date): string {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
         + '-' + String(d.getDate()).padStart(2, '0');
  }
}
