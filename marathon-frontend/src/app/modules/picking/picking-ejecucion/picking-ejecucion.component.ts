import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { AppIconComponent } from '../../../shared/components/icon/icon.component';

interface PickingLinea {
  idDetalle: number;
  idProducto: number;
  productoNombre: string;
  productoDescripcion: string;
  unidadMedidaNombre: string;
  cantidad: number;
  cantidadRecogida: number;
  pickingCompletado: boolean;
  pendiente: number;
  guardando?: boolean;
}

interface PickingPedido {
  idPedido: number;
  numeroPedido: string;
  clienteNombre: string;
  clienteApellido: string;
  fechaPedido: string;
  estado: string;
  esPedidoEspecial: boolean;
  tipoEspecial: string;
  notaEspecial: string;
  fechaLimiteEntrega: string;
  lineas: PickingLinea[];
  totalLineas: number;
  lineasCompletadas: number;
  estadoPicking: string;
}

@Component({
  selector: 'app-picking-ejecucion',
  standalone: true,
  imports: [CommonModule, FormsModule, AppIconComponent],
  template: `
    <div class="container" *ngIf="pedido">
      <div class="header">
        <div>
          <h2>Pedido {{pedido.numeroPedido}}</h2>
          <span class="cliente">{{pedido.clienteNombre}} {{pedido.clienteApellido}}</span>
          <span class="estado-badge" [ngClass]="'ep-'+estadoPicking">{{estadoLabel(estadoPicking)}}</span>
        </div>
        <button class="btn-back" (click)="volver()">← Volver</button>
      </div>

      <div class="especial-banner" *ngIf="pedido.esPedidoEspecial">
        <div class="especial-top">
          <span class="especial-badge">PEDIDO ESPECIAL · {{tipoLabel(pedido.tipoEspecial)}}</span>
        </div>
        <p class="nota" *ngIf="pedido.notaEspecial">{{pedido.notaEspecial}}</p>
        <div class="urgente inline-icon-text" *ngIf="esUrgente()"><app-icon name="warning" [size]="16"/> Entrega urgente — límite {{pedido.fechaLimiteEntrega | date:'dd/MM/yyyy HH:mm'}}</div>
      </div>

      <div class="progress-top">
        <div class="progress-info">
          <span><strong>{{lineasCompletadas}}</strong> de <strong>{{totalLineas}}</strong> líneas completadas</span>
          <span>{{porcentaje}}%</span>
        </div>
        <div class="progress-bar">
          <div class="progress-fill" [style.width.%]="porcentaje"></div>
        </div>
      </div>

      <div class="lineas">
        <div class="linea" *ngFor="let l of pedido.lineas" [ngClass]="claseLinea(l)">
          <div class="linea-info">
            <span class="prod-nombre">{{l.productoNombre}}</span>
            <span class="prod-unidad" *ngIf="l.unidadMedidaNombre">({{l.unidadMedidaNombre}})</span>
            <span class="prod-desc" *ngIf="l.productoDescripcion">{{l.productoDescripcion}}</span>
          </div>

          <div class="linea-controles">
            <div class="campo">
              <label>Cantidad total</label>
              <span class="cantidad-total">{{l.cantidad}}</span>
            </div>
            <div class="campo">
              <label>Cantidad recogida</label>
              <input type="number" [(ngModel)]="l.cantidadRecogida" [min]="0" [max]="l.cantidad" class="input-num"/>
            </div>
            <div class="campo check">
              <label>
                <input type="checkbox" [checked]="l.pickingCompletado" disabled/>
                Línea completada
              </label>
            </div>
            <button class="btn-guardar" (click)="guardarLinea(l)" [disabled]="l.guardando">
              {{ l.guardando ? 'Guardando...' : 'Guardar línea' }}
            </button>
          </div>
        </div>
      </div>

      <div class="toast" *ngIf="toast" [class.error]="toastError">{{toast}}</div>
    </div>

    <div class="spinner" *ngIf="!pedido">Cargando picking...</div>
  `,
  styles: [`
    /* Inherits global dark theme from styles.scss */
  `]
})
export class PickingEjecucionComponent implements OnInit {
  pedido: PickingPedido | null = null;
  totalLineas = 0;
  lineasCompletadas = 0;
  estadoPicking = 'pendiente';
  toast = '';
  toastError = false;

  constructor(private route: ActivatedRoute, private router: Router, private http: HttpClient) {}

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('idPedido');
    if (id) { this.cargar(+id); }
  }

  cargar(id: number) {
    this.http.get<PickingPedido>(`${environment.apiUrl}/picking/pedidos/${id}`).subscribe({
      next: res => { this.pedido = res; this.recalcular(); },
      error: (err) => { this.mostrarToast(err.error?.message || 'Error al cargar el picking', true); }
    });
  }

  get porcentaje(): number {
    if (!this.totalLineas) return 0;
    return Math.round((this.lineasCompletadas / this.totalLineas) * 100);
  }

  recalcular() {
    if (!this.pedido) return;
    this.totalLineas = this.pedido.lineas.length;
    this.lineasCompletadas = this.pedido.lineas.filter(l => l.pickingCompletado).length;
    if (this.totalLineas > 0 && this.lineasCompletadas === this.totalLineas) {
      this.estadoPicking = 'completo';
    } else if (this.lineasCompletadas === 0) {
      this.estadoPicking = 'pendiente';
    } else {
      this.estadoPicking = 'en_progreso';
    }
  }

  guardarLinea(l: PickingLinea) {
    if (!this.pedido) return;
    if (l.cantidadRecogida == null || l.cantidadRecogida < 0) {
      this.mostrarToast('La cantidad recogida no puede ser negativa', true);
      return;
    }
    if (l.cantidadRecogida > l.cantidad) {
      this.mostrarToast('Cantidad recogida no puede superar la cantidad del pedido (máximo: ' + l.cantidad + ')', true);
      return;
    }
    // El estado se deriva de la cantidad; el usuario no debe mantener dos
    // controles que pueden contradecirse entre si.
    l.pickingCompletado = l.cantidad > 0 && l.cantidadRecogida === l.cantidad;
    l.guardando = true;
    const body = { idDetalle: l.idDetalle, cantidadRecogida: l.cantidadRecogida, pickingCompletado: l.pickingCompletado };
    this.http.put<PickingLinea>(`${environment.apiUrl}/picking/pedidos/${this.pedido.idPedido}/lineas`, body).subscribe({
      next: res => {
        l.cantidadRecogida = res.cantidadRecogida;
        l.pickingCompletado = res.pickingCompletado;
        l.pendiente = res.pendiente;
        l.guardando = false;
        this.recalcular();
        this.mostrarToast('Línea guardada correctamente');
      },
      error: (err) => { l.guardando = false; this.mostrarToast(err.error?.message || 'Error al guardar la línea', true); }
    });
  }

  claseLinea(l: PickingLinea): string {
    if (l.pickingCompletado && l.cantidadRecogida >= l.cantidad) return 'completa';
    if (l.cantidadRecogida > 0 && l.cantidadRecogida < l.cantidad) return 'parcial';
    if (l.pickingCompletado) return 'completa';
    return 'pendiente';
  }

  volver() { this.router.navigate(['/picking']); }

  estadoLabel(estado: string): string {
    switch (estado) {
      case 'pendiente': return 'Pendiente';
      case 'en_progreso': return 'En progreso';
      case 'completo': return 'Completo';
      default: return estado || '';
    }
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
    const diff = new Date(this.pedido.fechaLimiteEntrega).getTime() - Date.now();
    return diff >= 0 && diff <= 24 * 60 * 60 * 1000;
  }

  mostrarToast(msg: string, error = false) {
    this.toast = msg; this.toastError = error;
    setTimeout(() => { this.toast = ''; }, 3000);
  }
}
