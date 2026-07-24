import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-orden-produccion-detalle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container" *ngIf="op">
      <div class="toolbar">
        <h2>Orden de Producción #{{op.idOrdenProduccion}}</h2>
        <button class="btn-cancel" routerLink="/produccion">Volver</button>
      </div>

      <div class="detail-grid">
        <div class="detail-card"><span class="label">Producto</span><span>{{op.productoNombre}}</span></div>
        <div class="detail-card"><span class="label">Estado</span><span class="op-badge" [ngClass]="'op-' + op.estado">{{op.estado}}</span></div>
        <div class="detail-card"><span class="label">Cantidad planificada</span><span>{{op.cantidadPlanificada}}</span></div>
        <div class="detail-card"><span class="label">Cantidad producida</span><span>{{op.cantidadProducida != null ? op.cantidadProducida : '-'}}</span></div>
        <div class="detail-card"><span class="label">Bodega destino</span><span>{{op.bodegaNombre}}</span></div>
        <div class="detail-card"><span class="label">Registró</span><span>{{op.usuarioRegistroNombre}}</span></div>
        <div class="detail-card" *ngIf="op.usuarioCompletaNombre"><span class="label">Completó</span><span>{{op.usuarioCompletaNombre}}</span></div>
        <div class="detail-card"><span class="label">Creación</span><span>{{op.fechaCreacion | date:'dd/MM/yyyy HH:mm'}}</span></div>
        <div class="detail-card" *ngIf="op.fechaInicio"><span class="label">Inicio</span><span>{{op.fechaInicio | date:'dd/MM/yyyy HH:mm'}}</span></div>
        <div class="detail-card" *ngIf="op.fechaFin"><span class="label">Fin</span><span>{{op.fechaFin | date:'dd/MM/yyyy HH:mm'}}</span></div>
        <div class="detail-card wide" *ngIf="op.observaciones"><span class="label">Observaciones</span><span>{{op.observaciones}}</span></div>
      </div>

      <h3>Consumo de materia prima</h3>
      <table class="data-table">
        <thead><tr><th>Material</th><th>Teórico</th><th>Real</th><th>Merma</th><th>Costo unitario</th><th>Costo línea</th></tr></thead>
        <tbody>
          <tr *ngFor="let c of op.consumos">
            <td>{{c.materiaPrimaNombre}}</td>
            <td>{{c.cantidadTeorica | number:'1.0-3'}} {{c.unidadMedida}}</td>
            <td>{{c.cantidadReal != null ? (c.cantidadReal | number:'1.0-3') + ' ' + c.unidadMedida : '-'}}</td>
            <td>
              <span *ngIf="c.merma != null" [ngClass]="{ 'merma-pos': c.merma > 0, 'merma-neg': c.merma < 0 }">
                {{c.merma | number:'1.0-3'}} {{c.unidadMedida}}
              </span>
              <span *ngIf="c.merma == null">-</span>
            </td>
            <td>$ {{c.costoUnitarioSnapshot | number:'1.4-4'}}</td>
            <td>$ {{c.costoLinea | number:'1.2-4'}}</td>
          </tr>
        </tbody>
      </table>

      <!-- Panel de costos (F29) -->
      <div class="costos-panel" *ngIf="op.estado === 'en_proceso' || op.estado === 'completada'">
        <h3>Costos de producción</h3>
        <div class="costo-row"><span>Costo materia prima</span><span>$ {{op.costoMateriaPrima | number:'1.2-2'}}</span></div>
        <div class="costo-row"><span>Costo mano de obra</span><span>$ {{op.costoManoObra | number:'1.2-2'}}</span></div>
        <div class="costo-row"><span>Costo indirecto</span><span>$ {{op.costoIndirecto | number:'1.2-2'}}</span></div>
        <div class="costo-row total"><span>Costo total</span><span>$ {{op.costoTotal | number:'1.2-2'}}</span></div>
        <div class="costo-row" *ngIf="op.cantidadProducida"><span>Costo unitario producido</span><span>$ {{op.costoUnitarioProducido | number:'1.4-4'}} / unidad</span></div>
      </div>

      <!-- Acciones por estado -->
      <div class="acciones">
        <ng-container *ngIf="op.estado === 'planificada'">
          <button class="btn-save" (click)="mostrarConfirmIniciar = true">Iniciar producción</button>
          <button class="btn-delete" (click)="cancelar()">Cancelar orden</button>
        </ng-container>

        <button *ngIf="op.estado === 'en_proceso'" class="btn-save" (click)="abrirCompletar()">Completar producción</button>
      </div>

      <!-- Modal confirmar iniciar -->
      <div class="modal-overlay" *ngIf="mostrarConfirmIniciar" (click)="mostrarConfirmIniciar = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Iniciar producción</h3>
          <p>Al iniciar se <strong>descontará del stock</strong> la materia prima teórica según el BOM. Esta acción no se puede deshacer (para revertir habría que completar la orden).</p>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="mostrarConfirmIniciar = false">Cancelar</button>
            <button class="btn-save" (click)="iniciar()" [disabled]="guardando">Sí, iniciar</button>
          </div>
        </div>
      </div>

      <!-- Modal completar -->
      <div class="modal-overlay" *ngIf="mostrarCompletar" (click)="mostrarCompletar = false">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Completar producción</h3>
          <div class="form-group">
            <label>Cantidad realmente producida (máx {{op.cantidadPlanificada}}) *</label>
            <input type="number" min="0" [max]="op.cantidadPlanificada" [(ngModel)]="completar.cantidadProducida"/>
          </div>
          <label class="toggle">
            <input type="checkbox" [(ngModel)]="registrarReal"/> Registrar consumo real por material
          </label>
          <div *ngIf="registrarReal" class="reales">
            <div class="real-linea" *ngFor="let c of op.consumos">
              <span>{{c.materiaPrimaNombre}} ({{c.unidadMedida}})</span>
              <input type="number" min="0" step="0.001" [(ngModel)]="realMap[c.idMateriaPrima]"/>
            </div>
          </div>
          <div class="form-row2">
            <div class="form-group">
              <label>Costo mano de obra</label>
              <input type="number" min="0" step="0.01" [(ngModel)]="completar.costoManoObra"/>
            </div>
            <div class="form-group">
              <label>Costo indirecto</label>
              <input type="number" min="0" step="0.01" [(ngModel)]="completar.costoIndirecto"/>
            </div>
          </div>
          <div class="form-group">
            <label>Observaciones</label>
            <input [(ngModel)]="completar.observaciones"/>
          </div>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="mostrarCompletar = false">Cancelar</button>
            <button class="btn-save" (click)="completarProduccion()" [disabled]="guardando">Completar</button>
          </div>
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
    .op-badge { padding: .25rem .6rem; border-radius: 12px; font-size: .72rem; font-weight: 600; text-transform: uppercase; color: #fff; width: fit-content; }
    .op-planificada { background: #6b7280; }
    .op-en_proceso { background: #d97706; }
    .op-completada { background: #16a34a; }
    .op-cancelada { background: #dc2626; }
    .merma-pos { color: #f87171; font-weight: 600; }
    .merma-neg { color: #4ade80; font-weight: 600; }
    .acciones { margin-top: 1.5rem; display: flex; gap: 1rem; }
    .toggle { display: flex; align-items: center; gap: .5rem; margin: 1rem 0; }
    .reales { display: flex; flex-direction: column; gap: .5rem; margin-bottom: 1rem; }
    .real-linea { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
    .real-linea input { width: 120px; background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .5rem; border-radius: 6px; }
    .form-group { display: flex; flex-direction: column; gap: .4rem; margin-bottom: .8rem; }
    .form-group label { font-size: .75rem; text-transform: uppercase; color: rgba(255,255,255,0.5); }
    .form-group input { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .7rem; border-radius: 8px; }
    .form-row2 { display: flex; gap: 1rem; }
    .form-row2 .form-group { flex: 1; }
    .costos-panel { margin-top: 1.5rem; padding: 1rem 1.25rem; border: 1px solid rgba(255,255,255,0.08); border-radius: 10px; max-width: 420px; }
    .costos-panel h3 { margin: 0 0 .75rem; }
    .costo-row { display: flex; justify-content: space-between; padding: .35rem 0; }
    .costo-row.total { border-top: 1px solid rgba(255,255,255,0.12); margin-top: .35rem; padding-top: .6rem; font-weight: 700; color: #C9A84C; }
  `]
})
export class OrdenProduccionDetalleComponent implements OnInit {
  op: any = null;
  guardando = false;
  mostrarConfirmIniciar = false;
  mostrarCompletar = false;
  registrarReal = false;
  completar: any = { cantidadProducida: 0, observaciones: '' };
  realMap: { [id: number]: number } = {};
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private api: ApiService) {}

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar(id);
  }

  cargar(id: number) {
    this.api.get<any>('ordenes-produccion/' + id).subscribe({
      next: (res: any) => { this.op = res; },
      error: () => { this.mostrarToast('Error al cargar', true); }
    });
  }

  iniciar() {
    this.guardando = true;
    this.api.put<any>('ordenes-produccion/' + this.op.idOrdenProduccion + '/iniciar', {}).subscribe({
      next: (res: any) => { this.op = res; this.guardando = false; this.mostrarConfirmIniciar = false; this.mostrarToast('Producción iniciada: materia prima consumida'); },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error al iniciar', true); }
    });
  }

  cancelar() {
    this.api.put<any>('ordenes-produccion/' + this.op.idOrdenProduccion + '/cancelar', {}).subscribe({
      next: (res: any) => { this.op = res; this.mostrarToast('Orden cancelada'); },
      error: (err: any) => { this.mostrarToast(err.error?.message || 'Error al cancelar', true); }
    });
  }

  abrirCompletar() {
    this.completar = { cantidadProducida: this.op.cantidadPlanificada, costoManoObra: 0, costoIndirecto: 0, observaciones: '' };
    this.registrarReal = false;
    this.realMap = {};
    (this.op.consumos || []).forEach((c: any) => { this.realMap[c.idMateriaPrima] = c.cantidadTeorica; });
    this.mostrarCompletar = true;
  }

  completarProduccion() {
    const body: any = {
      cantidadProducida: this.completar.cantidadProducida,
      costoManoObra: this.completar.costoManoObra || 0,
      costoIndirecto: this.completar.costoIndirecto || 0,
      observaciones: this.completar.observaciones || null
    };
    if (this.registrarReal) {
      body.consumosReales = (this.op.consumos || []).map((c: any) => ({
        idMateriaPrima: c.idMateriaPrima,
        cantidadReal: this.realMap[c.idMateriaPrima]
      }));
    }
    this.guardando = true;
    this.api.put<any>('ordenes-produccion/' + this.op.idOrdenProduccion + '/completar', body).subscribe({
      next: (res: any) => { this.op = res; this.guardando = false; this.mostrarCompletar = false; this.mostrarToast('Producción completada'); },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error al completar', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
