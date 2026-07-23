import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { ApiService } from '../../core/services/api.service';

interface LineaPedido { idDetalle: number; productoNombre: string; cantidad: number; selected: boolean; cantidadDevolver: number; }

@Component({
  selector: 'app-solicitud-devolucion-nueva',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Solicitar Devolucion - Pedido #{{idPedido}}</h2>
        <button class="btn-cancel" [routerLink]="['/pedidos', idPedido]">Volver</button>
      </div>

      <div class="form-group">
        <label>Motivo *</label>
        <select [(ngModel)]="motivo">
          <option value="">Seleccione motivo...</option>
          <option value="producto_defectuoso">Producto defectuoso</option>
          <option value="talla_incorrecta">Talla incorrecta</option>
          <option value="no_esperado">No es lo esperado</option>
          <option value="cambio_opinion">Cambio de opinion</option>
          <option value="producto_incompleto">Producto incompleto</option>
          <option value="otro">Otro</option>
        </select>
      </div>
      <div class="form-group">
        <label>Descripcion (opcional)</label>
        <textarea [(ngModel)]="descripcion" rows="2"></textarea>
      </div>

      <h3>Seleccione productos a devolver</h3>
      <table class="data-table">
        <thead><tr><th></th><th>Producto</th><th>Cant. comprada</th><th>Cant. a devolver</th></tr></thead>
        <tbody>
          <tr *ngFor="let l of lineas">
            <td><input type="checkbox" [(ngModel)]="l.selected"></td>
            <td>{{l.productoNombre}}</td>
            <td>{{l.cantidad}}</td>
            <td><input type="number" min="1" [max]="l.cantidad" [(ngModel)]="l.cantidadDevolver" [disabled]="!l.selected" style="width:60px"></td>
          </tr>
        </tbody>
      </table>

      <button class="btn-save" (click)="enviar()" [disabled]="guardando" style="margin-top:1rem">
        {{guardando ? 'Registrando...' : 'Registrar solicitud'}}
      </button>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>
  `,
  styles: [`
    .form-group { display: flex; flex-direction: column; gap: .4rem; margin-bottom: 1rem; }
    .form-group label { font-size: .75rem; text-transform: uppercase; color: rgba(255,255,255,0.5); }
    .form-group select, .form-group textarea { background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1); color: #fff; padding: .7rem; border-radius: 8px; }
  `]
})
export class SolicitudDevolucionNuevaComponent implements OnInit {
  idPedido!: number;
  lineas: LineaPedido[] = [];
  motivo = '';
  descripcion = '';
  guardando = false;
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private router: Router,
              private http: HttpClient, private api: ApiService) {}

  ngOnInit() {
    this.idPedido = Number(this.route.snapshot.paramMap.get('idPedido'));
    this.http.get<any>(environment.apiUrl + '/pedidos/' + this.idPedido).subscribe({
      next: (res: any) => {
        this.lineas = (res.detalles || []).map((d: any) => ({
          idDetalle: d.idDetalle, productoNombre: d.productoNombre,
          cantidad: d.cantidad, selected: false, cantidadDevolver: d.cantidad
        }));
      }
    });
  }

  enviar() {
    if (!this.motivo) { this.mostrarToast('Seleccione un motivo', true); return; }
    const detalles = this.lineas.filter(l => l.selected).map(l => ({
      idDetallePedido: l.idDetalle, cantidadDevuelta: l.cantidadDevolver
    }));
    if (detalles.length === 0) { this.mostrarToast('Seleccione al menos un producto', true); return; }
    this.guardando = true;
    this.api.post<any>('devoluciones', {
      idPedido: this.idPedido, motivo: this.motivo,
      descripcion: this.descripcion || null, detalles
    }).subscribe({
      next: (res: any) => {
        this.guardando = false;
        this.mostrarToast('Solicitud registrada');
        setTimeout(() => this.router.navigate(['/devoluciones', res.idSolicitud]), 1500);
      },
      error: (err: any) => { this.guardando = false; this.mostrarToast(err.error?.message || 'Error', true); }
    });
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3500);
  }
}
