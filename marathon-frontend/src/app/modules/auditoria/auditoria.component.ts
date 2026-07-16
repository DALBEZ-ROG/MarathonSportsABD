import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

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
  imports: [CommonModule, FormsModule],
  template: `
    <div class="audit-page">
      <header class="audit-header">
        <h1>Auditoría</h1>
        <p class="audit-subtitle">Control de cambios y actividad del sistema</p>
      </header>

      <div class="tabs">
        <button [class.active]="tab==='historial'" (click)="tab='historial'">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z"/></svg>
          Historial de Inventario
        </button>
        <button [class.active]="tab==='logs'" (click)="tab='logs'">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
          Log de Acciones
        </button>
      </div>

      <!-- TAB HISTORIAL -->
      <div *ngIf="tab==='historial'">
        <div class="audit-filters">
          <div class="filter-field">
            <label>Producto ID</label>
            <input type="text" [(ngModel)]="hProductoBusq" placeholder="Opcional..."/>
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
            <button class="btn-search" (click)="cargarHistorial()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              Buscar
            </button>
          </div>
        </div>

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
            <button class="btn-search" (click)="cargarLogs()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
              Buscar
            </button>
          </div>
        </div>

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
                <td><span class="accion-chip">{{l.accion}}</span></td>
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
  styles: [`
    .audit-page {
      max-width: 1300px;
      padding: 2rem;
      margin: 0 auto;
    }

    .audit-header {
      margin-bottom: 2rem;
    }

    .audit-header h1 {
      font-size: 1.8rem;
      font-weight: 300;
      letter-spacing: 1px;
      background: linear-gradient(135deg, #fff 0%, #C9A84C 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      margin-bottom: 0.3rem;
    }

    .audit-subtitle {
      color: rgba(255,255,255,0.35);
      font-size: 0.85rem;
      font-weight: 300;
    }

    /* ── Filters ── */
    .audit-filters {
      display: flex;
      gap: 1rem;
      align-items: flex-end;
      flex-wrap: wrap;
      padding: 1.25rem;
      background: rgba(255,255,255,0.02);
      border: 1px solid rgba(255,255,255,0.06);
      border-radius: 14px;
      margin-bottom: 1.5rem;
      backdrop-filter: blur(8px);
    }

    .filter-field {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
      min-width: 140px;
    }

    .filter-field label {
      font-size: 0.68rem;
      font-weight: 500;
      color: rgba(255,255,255,0.35);
      text-transform: uppercase;
      letter-spacing: 0.8px;
    }

    .filter-field input,
    .filter-field select {
      padding: 0.6rem 0.9rem;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.08);
      border-radius: 8px;
      color: rgba(255,255,255,0.85);
      font-size: 0.84rem;
      outline: none;
      transition: all 0.3s;
    }

    .filter-field input:focus,
    .filter-field select:focus {
      border-color: rgba(201,168,76,0.4);
      background: rgba(255,255,255,0.06);
      box-shadow: 0 0 0 3px rgba(201,168,76,0.08);
    }

    .filter-field select option {
      background: #1a1a2e;
      color: #fff;
    }

    .filter-field input::placeholder {
      color: rgba(255,255,255,0.2);
    }

    .filter-btn-wrap {
      justify-content: flex-end;
    }

    .btn-search {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.6rem 1.2rem;
      background: linear-gradient(135deg, #C9A84C, #a08339);
      color: #0a0a0f;
      border: none;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 600;
      font-size: 0.82rem;
      transition: all 0.3s;
      box-shadow: 0 4px 12px rgba(201,168,76,0.2);
    }

    .btn-search:hover {
      box-shadow: 0 6px 20px rgba(201,168,76,0.35);
      transform: translateY(-1px);
    }

    /* ── Table ── */
    .audit-table-wrap {
      overflow-x: auto;
      border-radius: 14px;
      border: 1px solid rgba(255,255,255,0.06);
      box-shadow: 0 4px 24px rgba(0,0,0,0.2);
    }

    .audit-table {
      width: 100%;
      border-collapse: collapse;
      background: rgba(255,255,255,0.02);
      backdrop-filter: blur(12px);
    }

    .audit-table th {
      background: rgba(201,168,76,0.06);
      color: #C9A84C;
      padding: 0.85rem 1rem;
      text-align: left;
      font-size: 0.7rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.8px;
      border-bottom: 1px solid rgba(255,255,255,0.06);
    }

    .audit-table td {
      padding: 0.75rem 1rem;
      border-bottom: 1px solid rgba(255,255,255,0.03);
      font-size: 0.84rem;
      color: rgba(255,255,255,0.7);
    }

    .audit-table tr:hover td {
      background: rgba(255,255,255,0.03);
    }

    .audit-table .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }

    .fecha-col {
      color: rgba(255,255,255,0.4) !important;
      font-size: 0.8rem !important;
      white-space: nowrap;
    }

    .desc-col {
      max-width: 280px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .ip-col {
      font-family: 'Cascadia Code', 'Fira Code', monospace;
      font-size: 0.78rem !important;
      color: rgba(255,255,255,0.4) !important;
    }

    /* ── Diff Badge ── */
    .diff-badge {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 6px;
      font-size: 0.78rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }

    .diff-up {
      background: rgba(76,175,80,0.12);
      color: #81C784;
    }

    .diff-down {
      background: rgba(229,115,115,0.12);
      color: #EF9A9A;
    }

    .diff-zero {
      background: rgba(255,255,255,0.04);
      color: rgba(255,255,255,0.3);
    }

    /* ── Motivo chip ── */
    .motivo-chip {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 6px;
      font-size: 0.75rem;
      background: rgba(255,255,255,0.04);
      border: 1px solid rgba(255,255,255,0.08);
      color: rgba(255,255,255,0.6);
      text-transform: capitalize;
    }

    /* ── Modulo Badge ── */
    .modulo-badge {
      display: inline-block;
      padding: 0.22rem 0.65rem;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.4px;
      background: rgba(100,181,246,0.12);
      color: #90CAF9;
      border: 1px solid rgba(100,181,246,0.2);
    }

    .modulo-badge[data-modulo="auth"] {
      background: rgba(33,150,243,0.12);
      color: #64B5F6;
      border-color: rgba(33,150,243,0.25);
    }

    .modulo-badge[data-modulo="pedidos"] {
      background: rgba(76,175,80,0.12);
      color: #81C784;
      border-color: rgba(76,175,80,0.25);
    }

    .modulo-badge[data-modulo="empaque"] {
      background: rgba(255,152,0,0.12);
      color: #FFB74D;
      border-color: rgba(255,152,0,0.25);
    }

    .modulo-badge[data-modulo="usuarios"] {
      background: rgba(156,39,176,0.12);
      color: #CE93D8;
      border-color: rgba(156,39,176,0.25);
    }

    .modulo-badge[data-modulo="comprobantes"] {
      background: rgba(96,125,139,0.12);
      color: #90A4AE;
      border-color: rgba(96,125,139,0.25);
    }

    .modulo-badge[data-modulo="inventario"] {
      background: rgba(255,193,7,0.12);
      color: #FFD54F;
      border-color: rgba(255,193,7,0.25);
    }

    .modulo-badge[data-modulo="picking"] {
      background: rgba(201,168,76,0.12);
      color: #C9A84C;
      border-color: rgba(201,168,76,0.25);
    }

    /* ── Acción chip ── */
    .accion-chip {
      display: inline-block;
      padding: 0.2rem 0.6rem;
      border-radius: 6px;
      font-size: 0.75rem;
      background: rgba(201,168,76,0.08);
      color: rgba(201,168,76,0.8);
      border: 1px solid rgba(201,168,76,0.15);
      text-transform: capitalize;
    }

    @media (max-width: 768px) {
      .audit-filters {
        flex-direction: column;
        align-items: stretch;
      }
      .filter-field { min-width: 100%; }
      .desc-col { max-width: 150px; }
    }
  `]
})
export class AuditoriaComponent implements OnInit {
  tab: 'historial' | 'logs' = 'historial';

  // Historial
  historial: HistorialItem[] = []; loadingH = false; hPage = 0; hTotalPages = 0;
  hProductoBusq = ''; hBodega: number | null = null; hDesde = ''; hHasta = '';
  bodegas: any[] = [];

  // Logs
  logs: LogItem[] = []; loadingL = false; lPage = 0; lTotalPages = 0;
  lUsuario: number | null = null; lModulo: string | null = null; lDesde = ''; lHasta = '';
  usuarios: any[] = []; modulos: string[] = [];

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.http.get<any[]>(`${environment.apiUrl}/bodegas/activas`).subscribe(b => this.bodegas = b);
    this.http.get<PageResp<any>>(`${environment.apiUrl}/usuarios?page=0&size=100`).subscribe(r => this.usuarios = r.content);
    this.http.get<string[]>(`${environment.apiUrl}/logs/modulos`).subscribe(m => this.modulos = m);
    this.cargarHistorial();
    this.cargarLogs();
  }

  cargarHistorial() {
    this.loadingH = true;
    let p = new HttpParams().set('page', this.hPage).set('size', 20);
    if (this.hProductoBusq) p = p.set('idProducto', this.hProductoBusq);
    if (this.hBodega) p = p.set('idBodega', this.hBodega);
    if (this.hDesde) p = p.set('desde', this.hDesde + 'T00:00:00');
    if (this.hHasta) p = p.set('hasta', this.hHasta + 'T23:59:59');
    this.http.get<PageResp<HistorialItem>>(`${environment.apiUrl}/auditoria/inventario`, { params: p }).subscribe({
      next: r => { this.historial = r.content; this.hTotalPages = r.totalPages; this.loadingH = false; },
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
      next: r => { this.logs = r.content; this.lTotalPages = r.totalPages; this.loadingL = false; },
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
