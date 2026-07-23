import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/services/api.service';

interface Movimiento {
  idMovimientoMp: number;
  tipoMovimiento: string;
  cantidad: number;
  stockAnterior: number;
  stockNuevo: number;
  observacion: string;
  fecha: string;
  usuarioNombre: string;
}

@Component({
  selector: 'app-kardex-materia-prima',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="crud-container">
      <div class="toolbar">
        <h2>Kardex - Materia Prima #{{idMp}}</h2>
        <button class="btn-cancel" routerLink="/materia-prima">Volver</button>
      </div>

      <div class="spinner" *ngIf="loading">Cargando...</div>

      <table class="data-table" *ngIf="!loading">
        <thead><tr><th>Fecha</th><th>Tipo</th><th>Cantidad</th><th>Stock ant.</th><th>Stock nuevo</th><th>Usuario</th><th>Observacion</th></tr></thead>
        <tbody>
          <tr *ngFor="let m of data">
            <td>{{m.fecha | date:'dd/MM/yyyy HH:mm'}}</td>
            <td><span class="tipo-badge" [ngClass]="'tipo-' + m.tipoMovimiento">{{tipoLabel(m.tipoMovimiento)}}</span></td>
            <td>{{m.cantidad | number:'1.3-3'}}</td>
            <td>{{m.stockAnterior | number:'1.3-3'}}</td>
            <td>{{m.stockNuevo | number:'1.3-3'}}</td>
            <td>{{m.usuarioNombre}}</td>
            <td>{{m.observacion || '-'}}</td>
          </tr>
          <tr *ngIf="data.length === 0"><td colspan="7" class="empty">Sin movimientos</td></tr>
        </tbody>
      </table>

      <div class="pagination" *ngIf="totalPages > 1">
        <button (click)="cambiarPagina(page-1)" [disabled]="page===0">Anterior</button>
        <span>Pagina {{page+1}} de {{totalPages}}</span>
        <button (click)="cambiarPagina(page+1)" [disabled]="page>=totalPages-1">Siguiente</button>
      </div>
    </div>
  `,
  styles: [`
    .tipo-badge { padding: .2rem .5rem; border-radius: 8px; font-size: .7rem; font-weight: 600; color: #fff; }
    .tipo-entrada_compra { background: #16a34a; }
    .tipo-salida_produccion { background: #2563eb; }
    .tipo-ajuste { background: #d97706; }
    .tipo-merma { background: #dc2626; }
  `]
})
export class KardexMateriaPrimaComponent implements OnInit {
  idMp!: number;
  data: Movimiento[] = [];
  loading = false;
  page = 0;
  size = 15;
  totalPages = 0;

  constructor(private route: ActivatedRoute, private api: ApiService) {}

  ngOnInit() {
    this.idMp = Number(this.route.snapshot.paramMap.get('id'));
    this.cargar();
  }

  tipoLabel(t: string): string {
    const map: Record<string, string> = {
      entrada_compra: 'Entrada compra', salida_produccion: 'Salida produccion',
      ajuste: 'Ajuste', merma: 'Merma'
    };
    return map[t] || t;
  }

  cargar() {
    this.loading = true;
    this.api.get<any>('materia-prima/' + this.idMp + '/movimientos?page=' + this.page + '&size=' + this.size).subscribe({
      next: (res: any) => { this.data = res.content; this.totalPages = res.totalPages; this.loading = false; },
      error: () => { this.loading = false; }
    });
  }

  cambiarPagina(p: number) { this.page = p; this.cargar(); }
}
