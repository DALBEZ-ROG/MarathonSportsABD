import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Los tres estados de un listado que no son «aquí están los datos» (D6).
 *
 * **El problema.** Las pantallas de lista mostraban la misma tabla en blanco
 * tanto si estaban cargando, como si no había registros, como si la petición
 * había fallado. La fila de relleno decía literalmente «No hay registros»
 * incluso cuando el servidor no había contestado — es decir, la pantalla
 * afirmaba algo sobre los datos que nadie había comprobado.
 *
 * Aquí los tres casos se ven distintos:
 *
 * - **Cargando:** un esqueleto con la forma de la tabla, para que se note que
 *   viene contenido y no que la pantalla está rota.
 * - **Vacío:** una frase concreta —«Aún no hay bodegas»— y, si hay filtros
 *   puestos, lo dice, porque «no hay» y «no hay con este filtro» son cosas
 *   distintas y la segunda tiene arreglo.
 * - **Error:** el motivo y un botón de reintentar. Nunca «no hay registros».
 */
@Component({
  selector: 'app-estado-lista',
  standalone: true,
  imports: [CommonModule],
  template: `
    <!-- ── Cargando ── -->
    <div class="el-skeleton" *ngIf="cargando" aria-busy="true"
         [attr.aria-label]="'Cargando ' + nombrePlural">
      <div class="el-fila cabecera"></div>
      <div class="el-fila" *ngFor="let f of filas"></div>
    </div>

    <!-- ── Error ── -->
    <div class="el-panel err" *ngIf="!cargando && error" role="alert">
      <h3>No se pudieron cargar {{ nombrePlural }}</h3>
      <p>{{ error }}</p>
      <button type="button" class="el-btn" (click)="reintentar.emit()">Reintentar</button>
    </div>

    <!-- ── Vacío ── -->
    <div class="el-panel" *ngIf="!cargando && !error && vacio">
      <h3>{{ hayFiltro ? 'Nada coincide con la búsqueda' : 'Aún no hay ' + nombrePlural }}</h3>
      <p *ngIf="hayFiltro">Prueba a quitar los filtros o a buscar otra cosa.</p>
      <p *ngIf="!hayFiltro">{{ pistaVacio }}</p>
    </div>
  `,
  styles: [`
    :host { display: block; }

    .el-skeleton { display: grid; gap: .45rem; margin: 1rem 0; }
    .el-fila {
      height: 38px; border-radius: 8px;
      background: linear-gradient(90deg, rgba(255,255,255,0.04), rgba(255,255,255,0.09), rgba(255,255,255,0.04));
      background-size: 220% 100%; animation: el-brillo 1.4s ease-in-out infinite;
    }
    .el-fila.cabecera { height: 30px; opacity: .65; }
    @keyframes el-brillo { 0% { background-position: 200% 0; } 100% { background-position: -40% 0; } }

    .el-panel {
      margin: 1rem 0; padding: 2.2rem 1.5rem; border-radius: 14px; text-align: center;
      border: 1px dashed rgba(255,255,255,0.14); background: rgba(255,255,255,0.02);
    }
    .el-panel h3 {
      margin: 0 0 .45rem; font-size: 1rem; font-weight: 600; color: rgba(255,255,255,0.85);
    }
    .el-panel p {
      margin: 0 auto; max-width: 52ch; font-size: .85rem; line-height: 1.6;
      color: rgba(255,255,255,0.5);
    }
    .el-panel.err { border-color: rgba(217,83,79,0.4); background: rgba(217,83,79,0.06); }
    .el-panel.err h3 { color: #e79a95; }

    .el-btn {
      margin-top: 1.1rem; cursor: pointer; font-size: .82rem; font-weight: 600;
      padding: .5rem 1.2rem; border-radius: 10px; color: #1a1a1f; border: none;
      background: linear-gradient(100deg, #C9A84C, #F4E28D);
    }
  `]
})
export class EstadoListaComponent {

  @Input() cargando = false;

  /** Motivo del fallo. `null` cuando la carga fue bien: un fallo nunca es un vacío. */
  @Input() error: string | null = null;

  @Input() vacio = false;

  /** En plural y en minúscula: «bodegas», «órdenes de compra». */
  @Input() nombrePlural = 'registros';

  /** `true` si hay búsqueda o filtros puestos: cambia el mensaje de vacío. */
  @Input() hayFiltro = false;

  /** Qué hacer para que deje de estar vacío. */
  @Input() pistaVacio = 'Usa el botón de crear para añadir el primero.';

  /** Cuántas filas fantasma pintar mientras carga. */
  @Input() filas = [1, 2, 3, 4, 5];

  @Output() reintentar = new EventEmitter<void>();
}
