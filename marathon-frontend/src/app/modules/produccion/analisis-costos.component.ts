import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';

@Component({
  selector: 'app-analisis-costos',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Análisis de Costos de Producción</h2>
        <button class="btn-cancel" routerLink="/produccion">Volver a órdenes</button>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead>
          <tr><th>Producto</th><th>Categoría</th><th>Costo prom. fabricación</th><th>Precio venta</th><th>Margen</th><th>OP completadas</th><th></th></tr>
        </thead>
        <tbody>
          <tr *ngFor="let p of data">
            <td>{{p.nombreProducto}}</td>
            <td>{{p.categoria}}</td>
            <td>{{p.costoPromedioFabricacion != null ? ('$ ' + (p.costoPromedioFabricacion | number:'1.2-4')) : 'Sin datos'}}</td>
            <td>$ {{p.precioVenta | number:'1.2-2'}}</td>
            <td>
              <span *ngIf="p.margen != null" [ngClass]="{ 'pos': p.margen >= 0, 'neg': p.margen < 0 }">$ {{p.margen | number:'1.2-2'}}</span>
              <span *ngIf="p.margen == null">-</span>
            </td>
            <td>{{p.ordenesCompletadas}}</td>
            <td><button class="btn-icon" (click)="analizar(p.idProducto)" title="Fabricar vs Comprar">⚖️</button></td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="7" class="empty">No hay productos fabricados</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 0">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">← Anterior</button>
        <span>Página {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente →</button>
      </div>

      <!-- Panel comparación -->
      <div class="modal-overlay" *ngIf="analisis" (click)="analisis=null">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <h3>Fabricar vs Comprar — {{analisis.nombreProducto}}</h3>
          <div class="cmp-row"><span>Categoría</span><span>{{analisis.categoria}}</span></div>
          <div class="cmp-row"><span>Costo promedio de fabricación</span><span>{{analisis.costoPromedioFabricacion != null ? ('$ ' + (analisis.costoPromedioFabricacion | number:'1.2-4')) : 'Sin datos'}}</span></div>
          <div class="cmp-row"><span>Órdenes completadas</span><span>{{analisis.ordenesCompletadas}}</span></div>
          <div class="cmp-row"><span>Costo promedio de compra (categoría)</span><span>{{analisis.costoPromedioCompraCategoria != null ? ('$ ' + (analisis.costoPromedioCompraCategoria | number:'1.2-4')) : 'Sin datos'}}</span></div>
          <div class="cmp-row" *ngIf="analisis.diferencia != null"><span>Diferencia</span><span>$ {{analisis.diferencia | number:'1.2-4'}}</span></div>
          <div class="conclusion" [ngClass]="claseConclusion()">{{analisis.conclusion}}</div>
          <div class="modal-actions">
            <button class="btn-cancel" (click)="analisis=null">Cerrar</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pos { color: #4ade80; font-weight: 600; }
    .neg { color: #f87171; font-weight: 600; }
    .cmp-row { display: flex; justify-content: space-between; padding: .4rem 0; border-bottom: 1px solid rgba(255,255,255,0.06); }
    .conclusion { margin-top: 1rem; padding: .75rem 1rem; border-radius: 8px; font-weight: 700; text-align: center; }
    .conclusion.fab { background: #14532d; color: #bbf7d0; }
    .conclusion.comp { background: #7c2d12; color: #fed7aa; }
    .conclusion.neutro { background: #334155; color: #e2e8f0; }
  `]
})
export class AnalisisCostosComponent implements OnInit {
  data: any[] = [];
  loading = false;
  page = 0;
  size = 10;
  totalPages = 0;
  analisis: any = null;

  constructor(private api: ApiService) {}
  ngOnInit() { this.cargar(); }

  cargar() {
    this.loading = true;
    this.api.get<any>('analisis-costos/productos-fabricados?page=' + this.page + '&size=' + this.size).subscribe({
      next: (res: any) => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }

  analizar(idProducto: number) {
    this.api.get<any>('analisis-costos/fabricar-vs-comprar/' + idProducto).subscribe({
      next: (res: any) => { this.analisis = res; },
      error: () => {}
    });
  }

  claseConclusion(): string {
    if (!this.analisis) return 'neutro';
    if (this.analisis.conclusion === 'Fabricar es más económico') return 'fab';
    if (this.analisis.conclusion === 'Comprar sería más económico') return 'comp';
    return 'neutro';
  }
}
