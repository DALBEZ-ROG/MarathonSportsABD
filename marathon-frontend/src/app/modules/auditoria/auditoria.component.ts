import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { SearchableSelectComponent } from '../../shared/components/searchable-select/searchable-select.component';
import { ESTILOS_AUDITORIA } from './auditoria.estilos';
import { CambiosDatosComponent } from './cambios-datos.component';
import { RastroUsuarioComponent, SaltoRastro } from './rastro-usuario.component';

interface HistorialItem {
  idHistorial: number; fecha: string; producto: string; bodega: string;
  stockAnterior: number; stockNuevo: number; diferencia: number; motivo: string; usuario: string;
}
interface LogItem {
  idLog: number; modulo: string; accion: string; descripcion: string; ipAddress: string;
  fecha: string; idUsuario: number; usuarioNombre: string; usuarioApellido: string;
}
interface PageResp<T> { content: T[]; totalElements: number; totalPages: number; currentPage: number; pageSize: number; }

@Component({
  selector: 'app-auditoria',
  standalone: true,
  imports: [CommonModule, FormsModule, SearchableSelectComponent,
            CambiosDatosComponent, RastroUsuarioComponent],
  template: `
    <div class="audit-page">
      <header class="audit-header">
        <h1>Auditoría</h1>
        <p class="audit-subtitle">
          La traza de quién tocó qué, y cuándo. No se puede editar ni borrar desde
          aquí: para eso sirve — si se pudiera arreglar, no probaría nada.
        </p>
      </header>

      <!-- F92 — el orden de las pestañas es el de las preguntas, no el de las
           tablas: primero «por dónde anduvo esta persona», que es con lo que se
           entra, y luego los tres detalles a los que esa pantalla salta. -->
      <div class="tabs">
        <button [class.active]="tab==='rastro'" (click)="irA('rastro')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
          Rastro por usuario
        </button>
        <button [class.active]="tab==='cambios'" (click)="irA('cambios')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 013 3L7 19l-4 1 1-4z"/></svg>
          Cambios en datos
        </button>
        <button [class.active]="tab==='logs'" (click)="irA('logs')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
          Log de Acciones
        </button>
        <button [class.active]="tab==='historial'" (click)="irA('historial')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>
          Historial de Inventario
        </button>
      </div>

      <!-- TAB RASTRO (F92) -->
      <app-rastro-usuario *ngIf="tab==='rastro'" (salto)="atender($event)"/>

      <!-- TAB CAMBIOS EN DATOS (F92) -->
      <app-cambios-datos *ngIf="tab==='cambios'" [filtroInicial]="filtroCambios"/>

      <p class="audit-que" *ngIf="tab==='historial'">
        Cada cambio de stock, con el <strong>antes y el después</strong>. Lo escribe
        un disparador de la base de datos, no la aplicación: aunque alguien tocara el
        stock por fuera del sistema, la fila aparecería igual.
      </p>
      <p class="audit-que" *ngIf="tab==='logs'">
        Qué hizo cada persona en cada módulo. Aquí no se ven los datos que cambió,
        se ve <strong>la acción</strong>: quién aprobó, quién anuló, quién reembolsó.
      </p>

      <!-- TAB HISTORIAL -->
      <div *ngIf="tab==='historial'">
        <div class="audit-filters">
          <div class="filter-field ancho">
            <label>Producto</label>
            <app-searchable-select
              [items]="productos"
              labelKey="nombre"
              valueKey="idProducto"
              placeholder="Todos — escribe para filtrar por uno…"
              [(ngModel)]="hProductoBusq"
              [ngModelOptions]="{ standalone: true }"/>
          </div>
          <div class="filter-field">
            <label>Bodega</label>
            <select [(ngModel)]="hBodega">
              <option [ngValue]="null">Todas</option>
              <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{b.nombre}}</option>
            </select>
          </div>
          <div class="filter-field">
            <label>Desde</label>
            <input type="date" [(ngModel)]="hDesde"/>
          </div>
          <div class="filter-field">
            <label>Hasta</label>
            <input type="date" [(ngModel)]="hHasta"/>
          </div>
          <div class="filter-field filter-btn-wrap">
            <button class="btn-search" (click)="hPage = 0; cargarHistorial()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              Buscar
            </button>
          </div>
        </div>

        <div class="atajos">
          <span>Atajos:</span>
          <button type="button" *ngFor="let a of atajos"
                  (click)="aplicarAtajo(a.dias, 'historial')">{{ a.etiqueta }}</button>
        </div>

        <p class="cuantos" *ngIf="!loadingH && hTotal >= 0">
          <strong>{{ hTotal | number }}</strong>
          {{ hTotal === 1 ? 'cambio de stock' : 'cambios de stock' }} con esos filtros.
        </p>

        <div class="spinner" *ngIf="loadingH">Cargando...</div>

        <div class="audit-table-wrap" *ngIf="!loadingH">
          <table class="audit-table">
            <thead>
              <tr>
                <th>Fecha</th><th>Producto</th><th>Bodega</th>
                <th class="num">Anterior</th><th class="num">Nuevo</th><th class="num">Cambio</th>
                <th>Motivo</th><th>Usuario</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let h of historial">
                <td class="fecha-col">{{h.fecha | date:'dd/MM/yyyy HH:mm'}}</td>
                <td>{{h.producto || '—'}}</td>
                <td>{{h.bodega || '—'}}</td>
                <td class="num">{{h.stockAnterior}}</td>
                <td class="num">{{h.stockNuevo}}</td>
                <td class="num">
                  <span class="diff-badge" [class.diff-up]="h.diferencia>0" [class.diff-down]="h.diferencia<0" [class.diff-zero]="h.diferencia===0">
                    {{h.diferencia>0 ? '+'+h.diferencia : h.diferencia}}
                  </span>
                </td>
                <td><span class="motivo-chip">{{h.motivo}}</span></td>
                <td>{{h.usuario || '—'}}</td>
              </tr>
              <tr *ngIf="historial.length===0"><td colspan="8" class="empty">Sin registros de inventario</td></tr>
            </tbody>
          </table>
        </div>

        <div class="pag" *ngIf="hTotalPages>1">
          <button (click)="hPage=hPage-1;cargarHistorial()" [disabled]="hPage===0">← Anterior</button>
          <span>Página {{hPage+1}} de {{hTotalPages}}</span>
          <button (click)="hPage=hPage+1;cargarHistorial()" [disabled]="hPage>=hTotalPages-1">Siguiente →</button>
        </div>
      </div>

      <!-- TAB LOGS -->
      <div *ngIf="tab==='logs'">
        <div class="audit-filters">
          <div class="filter-field">
            <label>Usuario</label>
            <select [(ngModel)]="lUsuario">
              <option [ngValue]="null">Todos</option>
              <option *ngFor="let u of usuarios" [ngValue]="u.idUsuario">{{u.nombre}} {{u.apellido}}</option>
            </select>
          </div>
          <div class="filter-field">
            <label>Módulo</label>
            <select [(ngModel)]="lModulo">
              <option [ngValue]="null">Todos</option>
              <option *ngFor="let m of modulos" [ngValue]="m">{{m}}</option>
            </select>
          </div>
          <div class="filter-field">
            <label>Desde</label>
            <input type="date" [(ngModel)]="lDesde"/>
          </div>
          <div class="filter-field">
            <label>Hasta</label>
            <input type="date" [(ngModel)]="lHasta"/>
          </div>
          <div class="filter-field filter-btn-wrap">
            <button class="btn-search" (click)="lPage = 0; cargarLogs()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              Buscar
            </button>
          </div>
        </div>

        <div class="atajos">
          <span>Atajos:</span>
          <button type="button" *ngFor="let a of atajos"
                  (click)="aplicarAtajo(a.dias, 'logs')">{{ a.etiqueta }}</button>
        </div>

        <p class="cuantos" *ngIf="!loadingL && lTotal >= 0">
          <strong>{{ lTotal | number }}</strong>
          {{ lTotal === 1 ? 'acción registrada' : 'acciones registradas' }} con esos filtros.
        </p>

        <div class="spinner" *ngIf="loadingL">Cargando...</div>

        <div class="audit-table-wrap" *ngIf="!loadingL">
          <table class="audit-table">
            <thead>
              <tr><th>Fecha</th><th>Usuario</th><th>Módulo</th><th>Acción</th><th>Descripción</th><th>IP</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let l of logs">
                <td class="fecha-col">{{l.fecha | date:'dd/MM/yyyy HH:mm'}}</td>
                <td>{{l.usuarioNombre ? (l.usuarioNombre+' '+l.usuarioApellido) : '—'}}</td>
                <td><span class="modulo-badge" [attr.data-modulo]="l.modulo">{{l.modulo}}</span></td>
                <td><span class="accion-chip" [class.sensible]="esSensible(l.accion)">{{l.accion}}</span></td>
                <td class="desc-col">{{l.descripcion}}</td>
                <td class="ip-col">{{l.ipAddress || '—'}}</td>
              </tr>
              <tr *ngIf="logs.length===0"><td colspan="6" class="empty">Sin registros de actividad</td></tr>
            </tbody>
          </table>
        </div>

        <div class="pag" *ngIf="lTotalPages>1">
          <button (click)="lPage=lPage-1;cargarLogs()" [disabled]="lPage===0">← Anterior</button>
          <span>Página {{lPage+1}} de {{lTotalPages}}</span>
          <button (click)="lPage=lPage+1;cargarLogs()" [disabled]="lPage>=lTotalPages-1">Siguiente →</button>
        </div>
      </div>
    </div>
  `,
  styles: [ESTILOS_AUDITORIA]
})
export class AuditoriaComponent implements OnInit {
  // F92: se abre por «Rastro por usuario». Es la pregunta con la que se entra a
  // una auditoría; las otras tres pestañas son el detalle al que lleva.
  tab: 'rastro' | 'cambios' | 'logs' | 'historial' = 'rastro';

  /** Lo que el rastro deja puesto al saltar a la pestaña de cambios. */
  filtroCambios: { idUsuario?: number; tabla?: string; operacion?: string } | null = null;

  // Historial
  historial: HistorialItem[] = []; loadingH = false; hPage = 0; hTotalPages = 0;
  /** Los atajos de fecha, que es como se mira una traza: hacia atrás. */
  readonly atajos = [
    { etiqueta: 'Hoy', dias: 0 },
    { etiqueta: 'Últimos 7 días', dias: 6 },
    { etiqueta: 'Últimos 30 días', dias: 29 },
    { etiqueta: 'Todo', dias: -1 }
  ];

  /** El catálogo para el buscador de producto: antes se pedía el id a mano. */
  productos: Array<{ idProducto: number; nombre: string }> = [];

  hTotal = -1;
  lTotal = -1;

  hProductoBusq: number | null = null; hBodega: number | null = null; hDesde = ''; hHasta = '';
  bodegas: any[] = [];

  // Logs
  logs: LogItem[] = []; loadingL = false; lPage = 0; lTotalPages = 0;
  lUsuario: number | null = null; lModulo: string | null = null; lDesde = ''; lHasta = '';
  usuarios: any[] = []; modulos: string[] = [];

  constructor(private http: HttpClient) {}

  /**
   * Un salto desde el rastro: cambia de pestaña con los filtros ya puestos.
   *
   * <p>Es lo que convierte el resumen en algo útil. Sin esto, ver que alguien
   * hizo «412 cambios en producto» obligaría a cambiar de pestaña y volver a
   * escribir a mano el nombre, la tabla y la operación — y a equivocarse.
   */
  atender(s: SaltoRastro): void {
    if (s.destino === 'cambios') {
      this.filtroCambios = { idUsuario: s.idUsuario, tabla: s.tabla, operacion: s.operacion };
      this.tab = 'cambios';
    } else {
      this.lUsuario = s.idUsuario;
      this.lModulo = s.modulo ?? null;
      this.lPage = 0;
      this.tab = 'logs';
      this.cargarLogs();
    }
  }

  /** Cambia las fechas y vuelve a buscar. {@code dias < 0} las quita del todo. */
  aplicarAtajo(dias: number, cual: 'historial' | 'logs') {
    const hoy = new Date();
    const hasta = this.comoFecha(hoy);
    const desde = dias < 0 ? '' : this.comoFecha(new Date(hoy.getTime() - dias * 86400000));
    if (cual === 'historial') {
      this.hDesde = desde; this.hHasta = dias < 0 ? '' : hasta; this.hPage = 0;
      this.cargarHistorial();
    } else {
      this.lDesde = desde; this.lHasta = dias < 0 ? '' : hasta; this.lPage = 0;
      this.cargarLogs();
    }
  }

  private comoFecha(d: Date): string {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0')
         + '-' + String(d.getDate()).padStart(2, '0');
  }

  /** Las acciones que mueven dinero o stock. El resto son consultas y altas. */
  esSensible(accion: string): boolean {
    return ['anular', 'aprobar', 'rechazar', 'reembolso', 'liberar_reserva',
            'eliminar', 'ajuste', 'cambio_estado'].includes(accion);
  }

  private cargarProductos() {
    this.http.get<any>(`${environment.apiUrl}/productos?page=0&size=1000&estado=activo`)
      .subscribe({
        next: r => { this.productos = r?.content ?? []; },
        error: () => { this.productos = []; }
      });
  }

  ngOnInit() {
    this.http.get<any[]>(`${environment.apiUrl}/bodegas/activas`).subscribe(b => this.bodegas = b);
    this.http.get<PageResp<any>>(`${environment.apiUrl}/usuarios?page=0&size=100`).subscribe(r => this.usuarios = r.content);
    this.http.get<string[]>(`${environment.apiUrl}/logs/modulos`).subscribe(m => this.modulos = m);
    this.cargarProductos();
    // F92: ya NO se cargan aquí las dos listas. Con cuatro pestañas y la de
    // rastro abierta por defecto, esto lanzaba dos consultas paginadas sobre
    // tablas de millón y medio de filas para pintar algo que nadie estaba
    // mirando. Cada pestaña pide sus datos la primera vez que se abre.
  }

  /**
   * Carga perezosa al cambiar de pestaña.
   *
   * <p>`cargadas` recuerda cuáles ya se pidieron para no repetir la consulta
   * cada vez que se va y se vuelve; los botones «Buscar» de cada pestaña siguen
   * refrescando a demanda.
   */
  private cargadas = new Set<string>();

  irA(destino: 'rastro' | 'cambios' | 'logs' | 'historial'): void {
    this.tab = destino;
    if (this.cargadas.has(destino)) { return; }
    this.cargadas.add(destino);
    if (destino === 'historial') { this.cargarHistorial(); }
    if (destino === 'logs') { this.cargarLogs(); }
  }

  cargarHistorial() {
    this.loadingH = true;
    let p = new HttpParams().set('page', this.hPage).set('size', 20);
    if (this.hProductoBusq) { p = p.set('idProducto', this.hProductoBusq); }
    if (this.hBodega) p = p.set('idBodega', this.hBodega);
    if (this.hDesde) p = p.set('desde', this.hDesde + 'T00:00:00');
    if (this.hHasta) p = p.set('hasta', this.hHasta + 'T23:59:59');
    this.http.get<PageResp<HistorialItem>>(`${environment.apiUrl}/auditoria/inventario`, { params: p }).subscribe({
      next: r => { this.historial = r.content; this.hTotalPages = r.totalPages;
                   this.hTotal = r.totalElements; this.loadingH = false; },
      error: () => { this.loadingH = false; }
    });
  }

  cargarLogs() {
    this.loadingL = true;
    let p = new HttpParams().set('page', this.lPage).set('size', 20);
    if (this.lUsuario) p = p.set('idUsuario', this.lUsuario);
    if (this.lModulo) p = p.set('modulo', this.lModulo);
    if (this.lDesde) p = p.set('desde', this.lDesde + 'T00:00:00');
    if (this.lHasta) p = p.set('hasta', this.lHasta + 'T23:59:59');
    this.http.get<PageResp<LogItem>>(`${environment.apiUrl}/logs`, { params: p }).subscribe({
      next: r => { this.logs = r.content; this.lTotalPages = r.totalPages;
                   this.lTotal = r.totalElements; this.loadingL = false; },
      error: () => { this.loadingL = false; }
    });
  }

  colorModulo(m: string): string {
    switch (m) {
      case 'auth': return '#2196F3';
      case 'pedidos': return '#4CAF50';
      case 'empaque': return '#FF9800';
      case 'usuarios': return '#9c27b0';
      case 'comprobantes': return '#607d8b';
      case 'inventario': return '#FFC107';
      default: return '#555';
    }
  }
}
