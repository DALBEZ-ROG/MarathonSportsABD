import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/**
 * Datos maestros (D5): todo lo que se define una vez y luego se usa en el resto
 * del sistema, en un solo sitio.
 *
 * Antes aquí solo vivían Ciudades, Categorías y Unidades, mientras que
 * Productos, Bodegas y Proveedores —que son igual de maestros: no se operan, se
 * mantienen— colgaban sueltos del menú principal, entre pantallas de trabajo
 * diario. Ahora las seis son pestañas de la misma pantalla.
 *
 * Las tres nuevas conservan su ruta antigua (`/productos`, `/bodegas`,
 * `/proveedores`) además de la nueva, para no romper enlaces guardados.
 */
@Component({
  selector: 'app-datos-maestros',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="dm-shell">
      <header class="dm-head">
        <h1>Datos maestros</h1>
        <p>Lo que se define una vez y usa el resto del sistema.</p>
      </header>

      <nav class="dm-tabs" aria-label="Secciones de datos maestros">
        <a *ngFor="let t of pestanas"
           [routerLink]="t.ruta"
           routerLinkActive="active"
           class="dm-tab">
          <span class="dm-tab-nombre">{{ t.nombre }}</span>
          <span class="dm-tab-pista">{{ t.pista }}</span>
        </a>
      </nav>
    </div>

    <router-outlet></router-outlet>
  `,
  styles: [`
    .dm-shell {
      width: 100%;
      max-width: 1800px;
      margin: 0 auto;
      padding: clamp(1rem, 3vw, 2.5rem) clamp(1rem, 3vw, 2.5rem) 0;
    }

    .dm-head h1 {
      margin: 0; font-size: clamp(1.4rem, 2.6vw, 1.9rem); font-weight: 700;
      letter-spacing: -0.02em; color: #f2f2f5;
    }
    .dm-head p { margin: .3rem 0 1.3rem; font-size: .84rem; color: rgba(255,255,255,0.45); }

    /* Rejilla adaptable en vez de una fila que desborda: con seis pestañas y la
       barra lateral abierta, una sola fila no cabe en un portátil. */
    .dm-tabs {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 165px), 1fr));
      gap: .55rem;
      margin-bottom: 1.6rem;
    }

    .dm-tab {
      display: flex; flex-direction: column; gap: .15rem;
      padding: .7rem .9rem; border-radius: 12px; text-decoration: none;
      border: 1px solid rgba(255,255,255,0.07);
      background: rgba(255,255,255,0.03);
      transition: border-color .18s, background .18s, transform .18s;
    }
    .dm-tab:hover { border-color: rgba(201,168,76,0.3); background: rgba(255,255,255,0.06); transform: translateY(-2px); }
    .dm-tab.active {
      border-color: rgba(201,168,76,0.55);
      background: linear-gradient(150deg, rgba(201,168,76,0.16), rgba(201,168,76,0.04));
    }

    .dm-tab-nombre { font-size: .86rem; font-weight: 600; color: rgba(255,255,255,0.86); }
    .dm-tab.active .dm-tab-nombre { color: #F4E28D; }
    .dm-tab-pista { font-size: .7rem; color: rgba(255,255,255,0.38); line-height: 1.35; }
  `]
})
export class DatosMaestrosComponent {

  /** La pista explica qué se define en cada pestaña; el nombre solo no basta. */
  readonly pestanas = [
    { ruta: 'productos',       nombre: 'Productos',    pista: 'Catálogo y precios' },
    { ruta: 'categorias',      nombre: 'Categorías',   pista: 'Cómo se agrupan los productos' },
    { ruta: 'unidades-medida', nombre: 'Unidades',     pista: 'En qué se mide cada cosa' },
    { ruta: 'ciudades',        nombre: 'Ciudades',     pista: 'Dónde hay clientes y bodegas' },
    { ruta: 'bodegas',         nombre: 'Bodegas',      pista: 'Dónde se guarda el stock' },
    { ruta: 'proveedores',     nombre: 'Proveedores',  pista: 'A quién se le compra' },
  ];
}
