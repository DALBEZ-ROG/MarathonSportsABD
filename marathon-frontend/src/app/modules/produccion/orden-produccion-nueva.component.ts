import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-orden-produccion-nueva',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Nueva Orden de Producción</h2>
        <button class="btn-cancel" routerLink="/produccion">Volver</button>
      </div>

      <div class="form-grid">
        <div class="form-group">
          <label>Producto a fabricar *</label>
          <select [(ngModel)]="form.idProducto" (change)="onCambio()">
            <option [ngValue]="null">-- Seleccione --</option>
            <option *ngFor="let p of productos" [ngValue]="p.idProducto">{{p.nombre}}</option>
          </select>
          <small *ngIf="productos.length === 0" class="hint">No hay productos con origen 'fabricado'. Configure un producto fabricado con BOM primero.</small>
        </div>
        <div class="form-group">
          <label>Cantidad a producir *</label>
          <input type="number" min="1" [(ngModel)]="form.cantidadPlanificada" (input)="onCambio()"/>
        </div>
        <div class="form-group">
          <label>Bodega destino *</label>
          <select [(ngModel)]="form.idBodegaDestino">
            <option [ngValue]="null">-- Seleccione --</option>
            <option *ngFor="let b of bodegas" [ngValue]="b.idBodega">{{b.nombre}}</option>
          </select>
        </div>
        <div class="form-group wide">
          <label>Observaciones</label>
          <input [(ngModel)]="form.observaciones"/>
        </div>
      </div>

      <!-- Panel de disponibilidad en vivo -->
      <div class="disp-panel" *ngIf="verif">
        <h3>Disponibilidad de materia prima</h3>
        <table class="data-table">
          <thead><tr><th>Material</th><th>Necesario</th><th>Disponible</th><th>Estado</th></tr></thead>
          <tbody>
            <tr *ngFor="let m of verif.materiales">
              <td>{{m.nombreMateriaPrima}}</td>
              <td>{{m.cantidadNecesaria | number:'1.0-3'}} {{m.unidadMedida}}</td>
              <td>{{m.stockDisponible | number:'1.0-3'}} {{m.unidadMedida}}</td>
              <td>
                <span *ngIf="m.suficiente" class="ok">✅ suficiente</span>
                <span *ngIf="!m.suficiente" class="bad">❌ faltan {{m.faltante | number:'1.0-3'}} {{m.unidadMedida}}</span>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="max-msg">Con el stock actual puedes producir hasta <strong>{{verif.cantidadMaximaProducible}}</strong> unidades.</p>
        <p *ngIf="!verif.puedeProducir" class="bad">No hay materia prima suficiente para la cantidad solicitada.</p>
      </div>

      <div class="disp-panel error-panel" *ngIf="verifError">{{verifError}}</div>

      <div class="acciones">
        <button class="btn-save" (click)="crear()" [disabled]="!puedeCrear() || guardando">
          {{guardando ? 'Creando...' : 'Crear orden'}}
        </button>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1.5rem; }
    .form-group { display: flex; flex-direction: column; gap: .4rem; }
    .form-group.wide { grid-column: 1 / -1; }
    .form-group label { font-size: .75rem; text-transform: uppercase; color: rgba(255,255,255,0.5); }
    .form-group input, .form-group select { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .7rem; border-radius: 8px; }
    .hint { color: #d97706; font-size: .75rem; }
    .disp-panel { margin-bottom: 1.5rem; padding: 1rem; border: 1px solid rgba(255,255,255,0.08); border-radius: 10px; }
    .error-panel { color: #f87171; }
    .max-msg { margin-top: .75rem; }
    .ok { color: #4ade80; font-weight: 600; }
    .bad { color: #f87171; font-weight: 600; }
    .acciones { margin-top: 1rem; }
  `]
})
export class OrdenProduccionNuevaComponent implements OnInit {
  productos: any[] = [];
  bodegas: any[] = [];
  form: any = { idProducto: null, cantidadPlanificada: 1, idBodegaDestino: null, observaciones: '' };
  verif: any = null;
  verifError = '';
  guardando = false;
  toast = '';
  toastError = false;
  private timer: any;

  constructor(private api: ApiService, private router: Router) {}

  ngOnInit() {
    this.api.get<any>('productos?origen=fabricado&size=1000').subscribe({
      next: (res: any) => { this.productos = res.content || []; },
      error: () => {}
    });
    this.api.get<any>('bodegas?size=1000').subscribe({
      next: (res: any) => { this.bodegas = res.content || []; },
      error: () => {}
    });
  }

  onCambio() {
    clearTimeout(this.timer);
    this.verif = null;
    this.verifError = '';
    if (!this.form.idProducto || !this.form.cantidadPlanificada || this.form.cantidadPlanificada < 1) return;
    this.timer = setTimeout(() => this.verificar(), 300);
  }

  verificar() {
    this.api.get<any>('ordenes-produccion/verificar-disponibilidad?idProducto=' + this.form.idProducto
        + '&cantidad=' + this.form.cantidadPlanificada).subscribe({
      next: (res: any) => { this.verif = res; this.verifError = ''; },
      error: (err: any) => { this.verif = null; this.verifError = err.error?.message || 'No se pudo verificar disponibilidad'; }
    });
  }

  puedeCrear(): boolean {
    return !!this.form.idProducto && !!this.form.idBodegaDestino
        && this.form.cantidadPlanificada >= 1
        && !!this.verif && this.verif.puedeProducir === true;
  }

  crear() {
    if (!this.puedeCrear()) return;
    this.guardando = true;
    this.api.post<any>('ordenes-produccion', this.form).subscribe({
      next: (res: any) => { this.guardando = false; this.router.navigate(['/produccion', res.idOrdenProduccion]); },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error al crear', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
