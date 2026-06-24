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
    <div class="page">
      <h2>Auditoría</h2>
      <div class="tabs">
        <button [class.active]="tab==='historial'" (click)="tab='historial'">Historial de Inventario</button>
        <button [class.active]="tab==='logs'" (click)="tab='logs'">Log de Acciones</button>
      </div>

      <!-- TAB HISTORIAL -->
      <div *ngIf="tab==='historial'">
        <div class="filtros">
          <input type="text" [(ngModel)]="hProductoBusq" placeholder="ID producto (opcional)"/>
          <select [(ngModel)]="hBodega">
            <option [ngValue]="null">Todas las bodegas</option>
            <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{b.nombre}}</option>
          </select>
          <input type="date" [(ngModel)]="hDesde"/>
          <input type="date" [(ngModel)]="hHasta"/>
          <button class="btn" (click)="cargarHistorial()">Buscar</button>
        </div>
        <div class="spinner" *ngIf="loadingH">Cargando...</div>
        <table class="tabla" *ngIf="!loadingH">
          <thead><tr><th>Fecha</th><th>Producto</th><th>Bodega</th><th>Stock Ant.</th><th>Stock Nuevo</th><th>Diferencia</th><th>Motivo</th><th>Usuario</th></tr></thead>
          <tbody>
            <tr *ngFor="let h of historial">
              <td>{{h.fecha | date:'dd/MM/yyyy HH:mm'}}</td>
              <td>{{h.producto || '-'}}</td>
              <td>{{h.bodega || '-'}}</td>
              <td>{{h.stockAnterior}}</td>
              <td>{{h.stockNuevo}}</td>
              <td [class.up]="h.diferencia>0" [class.down]="h.diferencia<0">
                {{h.diferencia>0 ? '▲'+h.diferencia : (h.diferencia<0 ? '▼'+(-h.diferencia) : '0')}}
              </td>
              <td>{{h.motivo}}</td>
              <td>{{h.usuario || '-'}}</td>
            </tr>
            <tr *ngIf="historial.length===0"><td colspan="8" class="empty">Sin registros</td></tr>
          </tbody>
        </table>
        <div class="pag" *ngIf="hTotalPages>1">
          <button (click)="hPage=hPage-1;cargarHistorial()" [disabled]="hPage===0">← Anterior</button>
          <span>Página {{hPage+1}} de {{hTotalPages}}</span>
          <button (click)="hPage=hPage+1;cargarHistorial()" [disabled]="hPage>=hTotalPages-1">Siguiente →</button>
        </div>
      </div>

      <!-- TAB LOGS -->
      <div *ngIf="tab==='logs'">
        <div class="filtros">
          <select [(ngModel)]="lUsuario">
            <option [ngValue]="null">Todos los usuarios</option>
            <option *ngFor="let u of usuarios" [ngValue]="u.idUsuario">{{u.nombre}} {{u.apellido}}</option>
          </select>
          <select [(ngModel)]="lModulo">
            <option [ngValue]="null">Todos los módulos</option>
            <option *ngFor="let m of modulos" [ngValue]="m">{{m}}</option>
          </select>
          <input type="date" [(ngModel)]="lDesde"/>
          <input type="date" [(ngModel)]="lHasta"/>
          <button class="btn" (click)="cargarLogs()">Buscar</button>
        </div>
        <div class="spinner" *ngIf="loadingL">Cargando...</div>
        <table class="tabla" *ngIf="!loadingL">
          <thead><tr><th>Fecha</th><th>Usuario</th><th>Módulo</th><th>Acción</th><th>Descripción</th><th>IP</th></tr></thead>
          <tbody>
            <tr *ngFor="let l of logs">
              <td>{{l.fecha | date:'dd/MM/yyyy HH:mm'}}</td>
              <td>{{l.usuarioNombre ? (l.usuarioNombre+' '+l.usuarioApellido) : '-'}}</td>
              <td><span class="badge" [ngStyle]="{'background': colorModulo(l.modulo)}">{{l.modulo}}</span></td>
              <td>{{l.accion}}</td>
              <td>{{l.descripcion}}</td>
              <td>{{l.ipAddress || '-'}}</td>
            </tr>
            <tr *ngIf="logs.length===0"><td colspan="6" class="empty">Sin registros</td></tr>
          </tbody>
        </table>
        <div class="pag" *ngIf="lTotalPages>1">
          <button (click)="lPage=lPage-1;cargarLogs()" [disabled]="lPage===0">← Anterior</button>
          <span>Página {{lPage+1}} de {{lTotalPages}}</span>
          <button (click)="lPage=lPage+1;cargarLogs()" [disabled]="lPage>=lTotalPages-1">Siguiente →</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page{max-width:1100px;padding:1.5rem}
    h2{color:#2d5a27;margin-bottom:1rem}
    .tabs{display:flex;gap:.5rem;margin-bottom:1rem;border-bottom:2px solid #eee}
    .tabs button{background:none;border:none;padding:.6rem 1rem;cursor:pointer;font-size:.9rem;color:#666;border-bottom:2px solid transparent;margin-bottom:-2px}
    .tabs button.active{color:#2d5a27;border-bottom-color:#2d5a27;font-weight:600}
    .filtros{display:flex;gap:.5rem;flex-wrap:wrap;margin-bottom:1rem}
    .filtros input,.filtros select{padding:.5rem;border:1px solid #ddd;border-radius:4px;font-size:.85rem}
    .btn{background:#2d5a27;color:#fff;border:none;padding:.5rem 1rem;border-radius:4px;cursor:pointer;font-weight:600}
    .spinner{text-align:center;padding:2rem;color:#666}
    .tabla{width:100%;border-collapse:collapse;background:#fff;border-radius:8px;overflow:hidden;box-shadow:0 1px 4px rgba(0,0,0,.1)}
    .tabla th{background:#2d5a27;color:#fff;padding:.6rem;text-align:left;font-size:.8rem}
    .tabla td{padding:.5rem .6rem;border-bottom:1px solid #eee;font-size:.82rem}
    .tabla tr:hover td{background:#f0f7f0}
    .up{color:#2e7d32;font-weight:600}
    .down{color:#c62828;font-weight:600}
    .badge{color:#fff;padding:.2rem .6rem;border-radius:10px;font-size:.72rem;text-transform:capitalize}
    .empty{text-align:center;color:#999;padding:2rem!important}
    .pag{display:flex;align-items:center;justify-content:center;gap:1rem;margin-top:1rem}
    .pag button{padding:.4rem .8rem;border:1px solid #ddd;border-radius:4px;background:#fff;cursor:pointer}
    .pag button:disabled{opacity:.5;cursor:not-allowed}
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
