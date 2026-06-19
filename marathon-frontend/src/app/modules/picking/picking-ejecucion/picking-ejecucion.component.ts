import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

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
  imports: [CommonModule, FormsModule],
  template: `
    <div class="container" *ngIf="pedido">
      <div class="header">
        <div>
          <h2># Pedido {{pedido.idPedido}}</h2>
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
        <div class="urgente" *ngIf="esUrgente()">⚠️ Entrega urgente — límite {{pedido.fechaLimiteEntrega | date:'dd/MM/yyyy HH:mm'}}</div>
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
                <input type="checkbox" [ngModel]="l.pickingCompletado" (ngModelChange)="onCheck(l, $event)"/>
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
    .container{max-width:900px;margin:0 auto}
    .header{display:flex;justify-content:space-between;align-items:center;margin-bottom:1.5rem;flex-wrap:wrap;gap:.5rem}
    .header h2{color:#2d5a27;margin:0 .8rem 0 0;display:inline}
    .cliente{font-weight:600;margin-right:.8rem}
    .estado-badge{padding:.3rem .8rem;border-radius:12px;font-size:.75rem;font-weight:700;text-transform:uppercase}
    .ep-pendiente{background:#f5f5f5;color:#666}
    .ep-en_progreso{background:#fff8e1;color:#f57f17}
    .ep-completo{background:#e8f5e9;color:#2e7d32}
    .btn-back{background:none;border:1px solid #ddd;padding:.4rem .8rem;border-radius:4px;cursor:pointer}
    .btn-back:hover{background:#f5f5f5}
    .especial-banner{background:#faf5fc;border:1px solid #e6d4ee;border-radius:10px;padding:1rem;margin-bottom:1.5rem}
    .especial-badge{background:#9c27b0;color:#fff;padding:.25rem .7rem;border-radius:10px;font-size:.75rem;font-weight:700}
    .nota{margin:.6rem 0 0;color:#444;font-size:.9rem}
    .urgente{background:#ffebee;color:#c62828;border:1px solid #c62828;padding:.5rem .8rem;border-radius:6px;font-weight:700;margin-top:.8rem;font-size:.85rem}
    .progress-top{background:#fff;border-radius:10px;padding:1.2rem;margin-bottom:1.5rem;box-shadow:0 1px 4px rgba(0,0,0,.08)}
    .progress-info{display:flex;justify-content:space-between;font-size:.85rem;color:#666;margin-bottom:.5rem}
    .progress-bar{height:12px;background:#f5f5f5;border-radius:6px;overflow:hidden}
    .progress-fill{height:100%;background:#2d5a27;transition:width .3s}
    .lineas{display:flex;flex-direction:column;gap:1rem}
    .linea{background:#fff;border-radius:10px;padding:1.2rem;box-shadow:0 1px 4px rgba(0,0,0,.08);border-left:4px solid #ccc}
    .linea.completa{background:#f1f8f0;border-left-color:#2d5a27}
    .linea.parcial{background:#fffaf0;border-left-color:#f57f17}
    .linea.pendiente{background:#fafafa;border-left-color:#ccc}
    .linea-info{margin-bottom:.8rem}
    .prod-nombre{font-weight:700;color:#333;margin-right:.4rem}
    .prod-unidad{color:#666;font-size:.85rem}
    .prod-desc{display:block;color:#888;font-size:.8rem;margin-top:.2rem}
    .linea-controles{display:flex;align-items:flex-end;gap:1rem;flex-wrap:wrap}
    .campo{display:flex;flex-direction:column;gap:.3rem}
    .campo label{font-size:.75rem;color:#666;text-transform:uppercase}
    .cantidad-total{font-weight:700;font-size:1.1rem;color:#2d5a27}
    .input-num{width:90px;padding:.45rem;border:1px solid #ddd;border-radius:4px;font-size:.9rem}
    .campo.check{flex:1}
    .campo.check label{display:flex;align-items:center;gap:.4rem;text-transform:none;font-size:.85rem;color:#333;cursor:pointer}
    .btn-guardar{background:#2d5a27;color:#fff;border:none;padding:.5rem 1.2rem;border-radius:4px;cursor:pointer;font-weight:600;font-size:.85rem}
    .btn-guardar:hover{background:#1e3d1a}
    .btn-guardar:disabled{opacity:.6;cursor:not-allowed}
    .spinner{text-align:center;padding:3rem;color:#666}
    .toast{position:fixed;bottom:2rem;right:2rem;background:#2d5a27;color:#fff;padding:.8rem 1.5rem;border-radius:6px;z-index:9999;animation:fadeIn .3s}
    .toast.error{background:#c00}
    @keyframes fadeIn{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}
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

  onCheck(l: PickingLinea, value: boolean) {
    if (value && l.cantidadRecogida < l.cantidad) {
      const ok = confirm('¿Recoger cantidad parcial?');
      if (!ok) { l.pickingCompletado = false; return; }
    }
    l.pickingCompletado = value;
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
