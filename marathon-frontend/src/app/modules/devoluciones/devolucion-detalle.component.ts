import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { CrudService } from '../../core/services/crud.service';

interface Detalle {
  idDetalleSd: number;
  idDetallePedido: number;
  productoNombre: string;
  cantidadOriginal: number;
  cantidadDevuelta: number;
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

@Component({
  selector: 'app-devolucion-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container" *ngIf="sol">
      <div class="toolbar">
        <h2>Devolucion #{{sol.idSolicitud}}</h2>
        <button class="btn-cancel" routerLink="/devoluciones">Volver</button>
      </div>

      <div class="detail-grid">
        <div class="detail-card"><span class="label">Pedido</span><span>#{{sol.idPedido}}</span></div>
        <div class="detail-card"><span class="label">Cliente</span><span>{{sol.clienteNombre}}</span></div>
        <div class="detail-card"><span class="label">Motivo</span><span>{{sol.motivo}}</span></div>
        <div class="detail-card"><span class="label">Estado</span><span class="dev-badge" [ngClass]="'dev-' + sol.estado">{{sol.estado}}</span></div>
        <div class="detail-card"><span class="label">Fecha solicitud</span><span>{{sol.fechaSolicitud | date:'dd/MM/yyyy HH:mm'}}</span></div>
        <div class="detail-card" *ngIf="sol.inspectorNombre"><span class="label">Inspector</span><span>{{sol.inspectorNombre}}</span></div>
        <div class="detail-card wide" *ngIf="sol.descripcion"><span class="label">Descripcion</span><span>{{sol.descripcion}}</span></div>
      </div>

      <h3>Lineas</h3>
      <table class="data-table">
        <thead><tr><th>Producto</th><th>Cant. original</th><th>Cant. devuelta</th><th>Resultado</th><th>Observacion</th></tr></thead>
        <tbody>
          <tr *ngFor="let d of sol.detalles">
            <td>{{d.productoNombre}}</td>
            <td>{{d.cantidadOriginal}}</td>
            <td>{{d.cantidadDevuelta}}</td>
            <td><span *ngIf="d.resultadoInspeccion" class="res-badge" [ngClass]="'res-' + d.resultadoInspeccion">{{d.resultadoInspeccion}}</span><span *ngIf="!d.resultadoInspeccion">Pendiente</span></td>
            <td>{{d.observacionInspeccion || '-'}}</td>
          </tr>
        </tbody>
      </table>

      <!-- Iniciar inspeccion -->
      <div class="acciones" *ngIf="sol.estado === 'solicitada' && esBodega">
        <button class="btn-save" (click)="iniciarInspeccion()">Iniciar inspeccion</button>
      </div>

      <!-- Inspeccionar -->
      <div class="inspeccion-form" *ngIf="sol.estado === 'en_inspeccion' && esBodega && hayPendientes()">
        <h3>Inspeccionar lineas pendientes</h3>
        <div class="form-group">
          <label>Bodega destino (para apto reventa)</label>
          <select [(ngModel)]="idBodega">
            <option [value]="0">Seleccione bodega...</option>
            <option *ngFor="let b of bodegas" [value]="b.idBodega">{{b.nombre}}</option>
          </select>
        </div>
        <div class="insp-items">
          <div *ngFor="let d of sol.detalles" class="insp-item" [class.done]="d.resultadoInspeccion">
            <span class="item-label">{{d.productoNombre}} (x{{d.cantidadDevuelta}})</span>
            <div *ngIf="!d.resultadoInspeccion" class="item-actions">
              <select [(ngModel)]="inspResults[d.idDetalleSd]">
                <option value="">Seleccione...</option>
                <option value="apto_reventa">Apto reventa</option>
                <option value="defectuoso">Defectuoso</option>
                <option value="rechazado">Rechazado</option>
              </select>
              <input [(ngModel)]="inspObs[d.idDetalleSd]" placeholder="Observacion (opcional)">
            </div>
            <span *ngIf="d.resultadoInspeccion" class="res-badge" [ngClass]="'res-' + d.resultadoInspeccion">{{d.resultadoInspeccion}}</span>
          </div>
        </div>
        <button class="btn-save" (click)="guardarInspeccion()" [disabled]="guardando">{{guardando ? 'Guardando...' : 'Guardar inspeccion'}}</button>
      </div>

      <!-- Reembolso -->
      <div class="reembolso-section" *ngIf="sol.estado === 'completada' && !sol.reembolso && esPedidos">
        <h3>Registrar reembolso</h3>
        <div class="form-grid">
          <div class="form-group"><label>Monto</label><input type="number" step="0.01" min="0.01" [(ngModel)]="reembolso.monto"></div>
          <div class="form-group"><label>Metodo</label>
            <select [(ngModel)]="reembolso.metodo"><option value="">Seleccione...</option><option value="nota_credito">Nota de credito</option><option value="transferencia">Transferencia</option><option value="efectivo">Efectivo</option></select>
          </div>
          <div class="form-group wide"><label>Observaciones</label><input [(ngModel)]="reembolso.observaciones"></div>
        </div>
        <button class="btn-save" (click)="registrarReembolso()" [disabled]="guardando">Registrar reembolso</button>
      </div>

      <div class="reembolso-info" *ngIf="sol.reembolso">
        <h3>Reembolso registrado</h3>
        <div class="detail-grid">
          <div class="detail-card"><span class="label">Monto</span><span class="total">$ {{sol.reembolso.monto | number:'1.2-2'}}</span></div>
          <div class="detail-card"><span class="label">Metodo</span><span>{{sol.reembolso.metodo}}</span></div>
          <div class="detail-card"><span class="label">Fecha</span><span>{{sol.reembolso.fechaReembolso | date:'dd/MM/yyyy HH:mm'}}</span></div>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .detail-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem; margin-bottom: 1.5rem; }
    .detail-card { display: flex; flex-direction: column; gap: .35rem; background: rgba(255,255,255,0.02); border: 1px solid rgba(255,255,255,0.05); border-radius: 10px; padding: .85rem 1rem; }
    .detail-card.wide { grid-column: 1 / -1; }
    .detail-card .label { font-size: .7rem; text-transform: uppercase; letter-spacing: 1px; color: rgba(255,255,255,0.4); }
    .detail-card .total { color: #C9A84C; font-size: 1.1rem; font-weight: 600; }
    .acciones { margin-top: 1.5rem; }
    .inspeccion-form { margin-top: 2rem; padding-top: 1.5rem; border-top: 1px solid rgba(255,255,255,0.06); }
    .insp-items { display: flex; flex-direction: column; gap: .8rem; margin: 1rem 0; }
    .insp-item { display: flex; align-items: center; gap: 1rem; flex-wrap: wrap; padding: .6rem; background: rgba(255,255,255,0.02); border-radius: 8px; }
    .insp-item.done { opacity: .6; }
    .item-label { min-width: 150px; font-weight: 500; }
    .item-actions { display: flex; gap: .5rem; flex: 1; }
    .item-actions select, .item-actions input { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .5rem; border-radius: 6px; font-size: .85rem; }
    .reembolso-section { margin-top: 2rem; padding-top: 1.5rem; border-top: 1px solid rgba(255,255,255,0.06); }
    .reembolso-info { margin-top: 1.5rem; }
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1rem; }
    .form-group { display: flex; flex-direction: column; gap: .4rem; }
    .form-group.wide { grid-column: 1 / -1; }
    .form-group label { font-size: .75rem; text-transform: uppercase; color: rgba(255,255,255,0.5); }
    .form-group input, .form-group select { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .7rem; border-radius: 8px; }
  `]
})
export class DevolucionDetalleComponent implements OnInit {
  sol: Solicitud | null = null;
  bodegas: any[] = [];
  idBodega = 0;
  inspResults: Record<number, string> = {};
  inspObs: Record<number, string> = {};
  reembolso = { monto: 0, metodo: '', observaciones: '' };
  guardando = false;
  esBodega = false;
  esPedidos = false;
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private api: ApiService,
              private auth: AuthService, private crud: CrudService) {}

  ngOnInit() {
    this.esBodega = this.auth.hasRol('Administrador') || this.auth.hasRol('Operador de Bodega');
    this.esPedidos = this.auth.hasRol('Administrador') || this.auth.hasRol('Operador de Pedidos');
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
    this.crud.listar<any>('bodegas', { page: 0, size: 100, estado: 'activo' }).subscribe({
      next: (res: any) => { this.bodegas = res.content; }
    });
  }

  cargar(id: number) {
    this.api.get<Solicitud>('devoluciones/' + id).subscribe({
      next: (res: any) => { this.sol = res; },
      error: () => { this.mostrarToast('Error al cargar', true); }
    });
  }

  hayPendientes(): boolean {
    return !!this.sol && this.sol.detalles.some(d => !d.resultadoInspeccion);
  }

  iniciarInspeccion() {
    if (!this.sol) return;
    this.api.put<any>('devoluciones/' + this.sol.idSolicitud + '/iniciar-inspeccion', {}).subscribe({
      next: (res: any) => { this.sol = res; this.mostrarToast('Inspeccion iniciada'); },
      error: (err: any) => { this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  guardarInspeccion() {
    if (!this.sol || !this.idBodega) { this.mostrarToast('Seleccione una bodega destino', true); return; }
    const items = this.sol.detalles
      .filter(d => !d.resultadoInspeccion && this.inspResults[d.idDetalleSd])
      .map(d => ({ idDetalleSd: d.idDetalleSd, resultadoInspeccion: this.inspResults[d.idDetalleSd], observacionInspeccion: this.inspObs[d.idDetalleSd] || null }));
    if (items.length === 0) { this.mostrarToast('Seleccione al menos un resultado', true); return; }
    this.guardando = true;
    this.api.put<any>('devoluciones/' + this.sol.idSolicitud + '/inspeccionar', { idBodega: Number(this.idBodega), items }).subscribe({
      next: (res: any) => { this.sol = res; this.guardando = false; this.mostrarToast('Inspeccion guardada'); },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  registrarReembolso() {
    if (!this.sol || !this.reembolso.monto || !this.reembolso.metodo) { this.mostrarToast('Complete los campos', true); return; }
    this.guardando = true;
    this.api.post<any>('devoluciones/' + this.sol.idSolicitud + '/reembolso', this.reembolso).subscribe({
      next: (res: any) => { this.sol = res; this.guardando = false; this.mostrarToast('Reembolso registrado'); },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
