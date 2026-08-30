import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Subscription, interval } from 'rxjs';
import { environment } from '../../../environments/environment';

interface Tarea {
  tipo: string; descripcion: string; fase: string;
  porcentaje: number; segundos: number; bytes: number; bytesEsperados: number;
}
interface Respaldo {
  idRespaldo: number; nombre: string; origen: string; estado: string;
  fechaInicio: string; fechaFin: string | null; duracionMs: number | null;
  tamanoBytes: number | null; filas: number | null;
  idUsuario: number | null; usuarioNombre: string | null;
  nota: string | null; mensaje: string | null; disponible: boolean;
}
interface Operacion {
  idOperacion: number; tipo: string; idRespaldo: number | null; respaldoNombre: string | null;
  estado: string; fechaInicio: string; fechaFin: string | null; duracionMs: number | null;
  idUsuario: number | null; usuarioNombre: string | null; ip: string | null;
  filasAfectadas: number | null; detalle: string | null;
}
interface Estado {
  disponible: boolean; motivo: string | null; tarea: Tarea | null;
  ultimoRespaldo: Respaldo | null; totalRespaldos: number; totalDisponibles: number;
  bytesOcupados: number; bytesLibresDisco: number | null;
  automaticoActivo: boolean; automaticoCron: string; automaticoDescripcion: string;
  proximoAutomatico: string | null; mantenimiento: boolean;
  palabraBorrado: string; palabraRestauracion: string;
}
interface VistaPrevia { tablas: string[]; cuantasTablas: number; filasEstimadas: number; }

/**
 * Respaldos y recuperación (F92).
 *
 * Tres cosas en una pantalla porque son la misma historia contada en orden:
 * se guarda un punto, se pierde todo, se vuelve a ese punto.
 */
@Component({
  selector: 'app-respaldos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="bk-page">
      <header class="bk-header">
        <h1>Respaldos y recuperación</h1>
        <p class="bk-subtitle">
          Un punto al que volver. Se guarda solo cada noche, o cuando tú lo pidas; y
          desde aquí se puede borrar la base entera y traerla de vuelta.
        </p>
      </header>

      <!-- ── Módulo no disponible ─────────────────────────────────── -->
      <div class="aviso aviso-rojo" *ngIf="estado && !estado.disponible">
        <h3>Esta pantalla no puede trabajar todavía</h3>
        <p>{{ estado.motivo }}</p>
      </div>

      <!-- ── Tarea en curso ───────────────────────────────────────── -->
      <div class="tarea" *ngIf="estado?.tarea as t">
        <div class="tarea-cab">
          <span class="tarea-punto"></span>
          <div>
            <strong>{{ t.fase }}</strong>
            <span class="tarea-seg">{{ t.segundos }} s</span>
          </div>
        </div>
        <div class="barra" *ngIf="t.porcentaje >= 0">
          <div class="barra-relleno" [style.width.%]="t.porcentaje"></div>
        </div>
        <p class="tarea-detalle" *ngIf="t.bytes > 0">
          {{ tam(t.bytes) }} escritos<span *ngIf="t.bytesEsperados > 0">
            de unos {{ tam(t.bytesEsperados) }}</span>.
        </p>
        <!-- Sin barra no se miente con un porcentaje inventado: se dice cuánto
             suele tardar, que es la información que de verdad hace falta. -->
        <p class="tarea-detalle" *ngIf="t.porcentaje < 0">
          {{ t.tipo === 'RESTAURACION'
             ? 'Medido en esta base: la restauración tarda unos 4 minutos. Mientras dura, el resto del sistema responde «en mantenimiento».'
             : 'Sin un respaldo anterior con el que comparar no se puede estimar el avance. El contador de arriba es real.' }}
        </p>
      </div>

      <!-- ── Resumen ──────────────────────────────────────────────── -->
      <div class="tarjetas" *ngIf="estado">
        <div class="tarjeta">
          <span class="t-lab">Último respaldo</span>
          <span class="t-num" *ngIf="estado.ultimoRespaldo">
            {{ estado.ultimoRespaldo.fechaInicio | date:'dd/MM HH:mm' }}
          </span>
          <span class="t-num vacio" *ngIf="!estado.ultimoRespaldo">nunca</span>
          <span class="t-sub" *ngIf="estado.ultimoRespaldo">
            hace {{ hace(estado.ultimoRespaldo.fechaInicio) }}
          </span>
          <span class="t-sub alerta" *ngIf="!estado.ultimoRespaldo">
            no hay ningún punto al que volver
          </span>
        </div>
        <div class="tarjeta">
          <span class="t-lab">Puntos guardados</span>
          <span class="t-num">{{ estado.totalDisponibles }}</span>
          <span class="t-sub">{{ tam(estado.bytesOcupados) }} en disco</span>
        </div>
        <div class="tarjeta">
          <span class="t-lab">Próximo automático</span>
          <span class="t-num" *ngIf="estado.proximoAutomatico">
            {{ estado.proximoAutomatico | date:'dd/MM HH:mm' }}
          </span>
          <span class="t-num vacio" *ngIf="!estado.proximoAutomatico">apagado</span>
          <span class="t-sub">{{ estado.automaticoDescripcion }}</span>
        </div>
        <div class="tarjeta">
          <span class="t-lab">Espacio libre</span>
          <span class="t-num">{{ estado.bytesLibresDisco ? tam(estado.bytesLibresDisco) : '—' }}</span>
          <span class="t-sub">en el disco de respaldos</span>
        </div>
      </div>

      <!-- ── Guardar ──────────────────────────────────────────────── -->
      <section class="bloque">
        <h2>Guardar un punto ahora</h2>
        <p class="bloque-que">
          Vuelca la base entera a disco. Con esta base son unos <strong>30 segundos</strong>
          y unos 2,4 GB. Se puede seguir trabajando mientras corre.
        </p>
        <div class="fila-accion">
          <input type="text" class="campo" [(ngModel)]="notaRespaldo" maxlength="200"
                 placeholder="Para qué es este punto — «antes de cargar el catálogo nuevo»"/>
          <button class="btn-oro" (click)="respaldar()"
                  [disabled]="!puedeActuar">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 01-2-2V5a2 2 0 012-2h11l5 5v11a2 2 0 01-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
            Guardar ahora
          </button>
        </div>
        <p class="nota-auto">
          <b>Automático:</b> {{ estado?.automaticoDescripcion }}. Lo lanza la propia
          aplicación, así que si el servidor está apagado a esa hora, esa noche no hay
          respaldo — para el desastre de verdad están los respaldos del sistema
          operativo, que corren igual.
        </p>
      </section>

      <!-- ── Puntos guardados ─────────────────────────────────────── -->
      <section class="bloque">
        <h2>Puntos guardados</h2>
        <div class="tabla-wrap">
          <table class="tabla">
            <thead>
              <tr><th>Cuándo</th><th>Origen</th><th>Quién</th><th>Para qué</th>
                  <th class="num">Tamaño</th><th class="num">Filas</th><th class="num">Duró</th>
                  <th>Estado</th><th></th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of respaldos">
                <td class="fecha-col">{{ r.fechaInicio | date:'dd/MM/yyyy HH:mm' }}</td>
                <td><span class="chip" [attr.data-origen]="r.origen">
                  {{ r.origen === 'MANUAL' ? 'a mano' : 'automático' }}</span></td>
                <td>{{ r.usuarioNombre || 'el sistema' }}</td>
                <td class="nota-col">{{ r.nota || '—' }}</td>
                <td class="num">{{ r.tamanoBytes ? tam(r.tamanoBytes) : '—' }}</td>
                <td class="num">{{ r.filas ? (r.filas | number) : '—' }}</td>
                <td class="num">{{ r.duracionMs ? (r.duracionMs / 1000 | number:'1.0-0') + ' s' : '—' }}</td>
                <td>
                  <span class="chip" [attr.data-estado]="r.estado">{{ etiquetaEstado(r.estado) }}</span>
                  <!-- El mensaje distingue «lo purgó la retención» de «alguien
                       borró la carpeta», que no son lo mismo y ante un hueco en
                       la lista es justo lo que hay que poder saber. -->
                  <span class="chip perdido" *ngIf="r.estado === 'COMPLETADO' && !r.disponible"
                        [title]="r.mensaje || 'Consta en el diario pero su carpeta ya no está en el disco. Alguien la movió o la borró.'">
                    ya no está en disco
                  </span>
                </td>
                <td>
                  <button class="btn-restaurar" *ngIf="r.disponible"
                          [disabled]="!puedeActuar"
                          (click)="pedirRestauracion(r)">Restaurar</button>
                </td>
              </tr>
              <tr *ngIf="respaldos.length === 0">
                <td colspan="9" class="empty">Todavía no se ha guardado ningún punto</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <!-- ── Simulacro ────────────────────────────────────────────── -->
      <section class="bloque bloque-peligro">
        <h2>Simular que se dañó el servidor</h2>
        <p class="bloque-que">
          Vacía las tablas de negocio, como si la base se hubiera perdido. Sirve para
          comprobar de verdad que los respaldos valen: un respaldo que nunca se ha
          restaurado es una suposición, no una copia de seguridad.
        </p>

        <div class="aviso aviso-ambar">
          <p><b>No se borra todo, y conviene saber qué se queda:</b></p>
          <ul>
            <li><b>Usuarios, roles y permisos se conservan.</b> Sin ellos nadie podría
                volver a entrar, y quien acaba de borrar se quedaría fuera justo cuando
                necesita pulsar «Restaurar».</li>
            <li><b>El diario de esta pantalla se conserva.</b> Vive en un esquema aparte
                que no entra en los volcados: es lo único que sobrevive a un borrado y a
                una restauración, y por eso puede decir quién hizo cada cosa.</li>
            <li><b>Las bitácoras de auditoría se conservan</b>, salvo que marques la
                casilla de abajo. El historial de inventario sí se va: cuelga del
                inventario por clave ajena y no se puede separar.</li>
          </ul>
        </div>

        <label class="check">
          <input type="checkbox" [(ngModel)]="borrarBitacoras" (ngModelChange)="cargarVistaPrevia()"/>
          <span>Borrar también las bitácoras de auditoría (desastre completo)</span>
        </label>

        <div class="previa" *ngIf="previa">
          Se vaciarían <strong>{{ previa.cuantasTablas }}</strong> tablas y unos
          <strong>{{ previa.filasEstimadas | number }}</strong> registros.
          <button class="enlace" (click)="verTablas = !verTablas">
            {{ verTablas ? 'ocultar' : 'ver la lista' }}
          </button>
          <div class="lista-tablas" *ngIf="verTablas">
            <span *ngFor="let t of previa.tablas">{{ t }}</span>
          </div>
        </div>

        <label class="check">
          <input type="checkbox" [(ngModel)]="respaldarAntes"/>
          <span>Guardar un punto justo antes de borrar <em>(muy recomendable)</em></span>
        </label>

        <div class="fila-accion">
          <input type="text" class="campo campo-peligro" [(ngModel)]="confirmacionBorrado"
                 [placeholder]="'Escribe: ' + (estado?.palabraBorrado || '')"/>
          <button class="btn-rojo" (click)="borrar()"
                  [disabled]="!puedeActuar || confirmacionBorrado !== estado?.palabraBorrado">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2"/></svg>
            Borrar los datos
          </button>
        </div>
      </section>

      <!-- ── Diario ───────────────────────────────────────────────── -->
      <section class="bloque">
        <h2>Qué se ha borrado y restaurado</h2>
        <p class="bloque-que">
          Este diario no se puede editar ni borrar desde aquí, y no entra en los
          respaldos: sobrevive a lo que registra.
        </p>
        <div class="tabla-wrap">
          <table class="tabla">
            <thead>
              <tr><th>Cuándo</th><th>Qué</th><th>Quién</th><th>Desde dónde</th>
                  <th>Punto usado</th><th class="num">Duró</th><th>Estado</th><th>Detalle</th></tr>
            </thead>
            <tbody>
              <tr *ngFor="let o of operaciones">
                <td class="fecha-col">{{ o.fechaInicio | date:'dd/MM/yyyy HH:mm:ss' }}</td>
                <td><span class="chip" [attr.data-tipo]="o.tipo">
                  {{ o.tipo === 'BORRADO_TOTAL' ? 'borrado' : 'restauración' }}</span></td>
                <td>{{ o.usuarioNombre || '—' }}</td>
                <td class="ip-col">{{ o.ip || '—' }}</td>
                <td>{{ o.respaldoNombre || '—' }}</td>
                <td class="num">{{ o.duracionMs ? (o.duracionMs / 1000 | number:'1.0-0') + ' s' : '—' }}</td>
                <td><span class="chip" [attr.data-estado]="o.estado">{{ etiquetaEstado(o.estado) }}</span></td>
                <td class="detalle-col">{{ o.detalle || '—' }}</td>
              </tr>
              <tr *ngIf="operaciones.length === 0">
                <td colspan="8" class="empty">Nunca se ha borrado ni restaurado nada</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <!-- ── Diálogo de restauración ────────────────────────────────── -->
    <div class="modal-fondo" *ngIf="restaurando" (click)="restaurando = null">
      <div class="modal" (click)="$event.stopPropagation()">
        <h3>Restaurar desde «{{ restaurando.nombre }}»</h3>
        <p>
          La base volverá <strong>exactamente</strong> al estado que tenía el
          {{ restaurando.fechaInicio | date:'d \\'de\\' MMMM \\'a las\\' HH:mm' }}.
          Todo lo que se haya hecho después se pierde.
        </p>
        <p class="modal-nota">
          Mientras dura —unos cuatro minutos con esta base, medido— el resto del sistema
          responde «en mantenimiento» a todo el mundo. Esta pantalla sigue viva y te
          irá diciendo por dónde va.
        </p>
        <input type="text" class="campo campo-peligro" [(ngModel)]="confirmacionRestauracion"
               [placeholder]="'Escribe: ' + (estado?.palabraRestauracion || '')"/>
        <div class="modal-botones">
          <button class="btn-gris" (click)="restaurando = null">Dejarlo</button>
          <button class="btn-rojo"
                  [disabled]="confirmacionRestauracion !== estado?.palabraRestauracion"
                  (click)="restaurar()">Restaurar</button>
        </div>
      </div>
    </div>

    <div class="mensaje" *ngIf="mensaje" [class.error]="mensajeEsError">
      {{ mensaje }}
      <button class="enlace" (click)="mensaje = ''">cerrar</button>
    </div>
  `,
  styles: [`
    .bk-page { max-width: 1300px; padding: 2rem; margin: 0 auto; }

    .bk-header { margin-bottom: 2rem; }
    .bk-header h1 { font-size: 1.8rem; font-weight: 400; letter-spacing: 1px;
                    color: #fff; margin-bottom: .3rem; }
    .bk-subtitle { color: rgba(255,255,255,.35); font-size: .85rem; font-weight: 300;
                   max-width: 78ch; line-height: 1.6; }

    /* ── Avisos ── */
    .aviso { padding: 1rem 1.25rem; border-radius: 12px; margin-bottom: 1.5rem;
             font-size: .85rem; line-height: 1.65; }
    .aviso h3 { margin: 0 0 .4rem; font-size: .95rem; font-weight: 500; }
    .aviso p { margin: 0; }
    .aviso ul { margin: .5rem 0 0; padding-left: 1.1rem; }
    .aviso li { margin-bottom: .4rem; }
    .aviso-rojo { background: rgba(220,38,38,.08); border: 1px solid rgba(220,38,38,.25);
                  color: #FCA5A5; }
    .aviso-ambar { background: rgba(217,119,6,.07); border: 1px solid rgba(217,119,6,.22);
                   color: rgba(252,211,77,.85); }
    .aviso-ambar b { color: #fcd34d; }

    /* ── Tarea en curso ── */
    .tarea { padding: 1.1rem 1.35rem; margin-bottom: 1.5rem; border-radius: 14px;
             background: rgba(201,168,76,.06); border: 1px solid rgba(201,168,76,.22); }
    .tarea-cab { display: flex; align-items: center; gap: .7rem; }
    .tarea-cab strong { color: #F4E28D; font-weight: 500; font-size: .92rem; }
    .tarea-seg { margin-left: .6rem; font-size: .8rem; color: rgba(255,255,255,.4);
                 font-variant-numeric: tabular-nums; }
    .tarea-punto { width: 9px; height: 9px; border-radius: 50%; background: #C9A84C;
                   animation: latir 1.2s ease-in-out infinite; flex-shrink: 0; }
    @keyframes latir { 0%,100% { opacity: 1; } 50% { opacity: .25; } }
    .barra { height: 6px; margin-top: .8rem; border-radius: 99px; overflow: hidden;
             background: rgba(255,255,255,.06); }
    .barra-relleno { height: 100%; background: linear-gradient(90deg, #C9A84C, #F4E28D);
                     transition: width .5s ease; }
    .tarea-detalle { margin: .6rem 0 0; font-size: .8rem; color: rgba(255,255,255,.45);
                     line-height: 1.55; }

    /* ── Tarjetas ── */
    .tarjetas { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
                gap: 1rem; margin-bottom: 2rem; }
    .tarjeta { display: flex; flex-direction: column; gap: .2rem; padding: 1.1rem 1.25rem;
               background: rgba(255,255,255,.02); border: 1px solid rgba(255,255,255,.06);
               border-radius: 14px; }
    .t-lab { font-size: .68rem; text-transform: uppercase; letter-spacing: .8px;
             color: rgba(255,255,255,.3); }
    .t-num { font-size: 1.45rem; font-weight: 300; color: #F4E28D; line-height: 1.3;
             font-variant-numeric: tabular-nums; }
    .t-num.vacio { color: rgba(255,255,255,.25); }
    .t-sub { font-size: .76rem; color: rgba(255,255,255,.4); }
    .t-sub.alerta { color: #FCA5A5; }

    /* ── Bloques ── */
    .bloque { padding: 1.5rem; margin-bottom: 1.75rem; border-radius: 16px;
              background: rgba(255,255,255,.015); border: 1px solid rgba(255,255,255,.06); }
    .bloque h2 { margin: 0 0 .3rem; font-size: 1.05rem; font-weight: 400;
                 color: rgba(255,255,255,.9); letter-spacing: .4px; }
    .bloque-que { margin: 0 0 1.1rem; font-size: .83rem; line-height: 1.65;
                  color: rgba(255,255,255,.42); max-width: 82ch; }
    .bloque-que strong { color: rgba(255,255,255,.7); }
    .bloque-peligro { border-color: rgba(220,38,38,.2); background: rgba(220,38,38,.02); }

    .fila-accion { display: flex; gap: .8rem; align-items: center; flex-wrap: wrap;
                   margin-top: 1rem; }
    .campo { flex: 1 1 320px; padding: .65rem .95rem; border-radius: 9px;
             background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.08);
             color: rgba(255,255,255,.85); font-size: .85rem; font-family: inherit;
             outline: none; transition: all .25s; }
    .campo:focus { border-color: rgba(201,168,76,.4); background: rgba(255,255,255,.06); }
    .campo::placeholder { color: rgba(255,255,255,.22); }
    .campo-peligro:focus { border-color: rgba(220,38,38,.45); }

    .btn-oro, .btn-rojo, .btn-gris {
      display: flex; align-items: center; gap: .55rem; padding: .65rem 1.25rem;
      border: none; border-radius: 9px; cursor: pointer; font-weight: 600;
      font-size: .83rem; font-family: inherit; transition: all .25s; white-space: nowrap;
    }
    .btn-oro { background: linear-gradient(135deg, #C9A84C, #a08339); color: #0a0a0f;
               box-shadow: 0 4px 12px rgba(201,168,76,.2); }
    .btn-oro:hover:not(:disabled) { box-shadow: 0 6px 20px rgba(201,168,76,.35);
                                    transform: translateY(-1px); }
    .btn-rojo { background: linear-gradient(135deg, #dc2626, #991b1b); color: #fff; }
    .btn-rojo:hover:not(:disabled) { box-shadow: 0 6px 20px rgba(220,38,38,.3); }
    .btn-gris { background: rgba(255,255,255,.05); color: rgba(255,255,255,.6);
                border: 1px solid rgba(255,255,255,.1); }
    .btn-oro:disabled, .btn-rojo:disabled { opacity: .35; cursor: not-allowed;
                                            box-shadow: none; transform: none; }

    .nota-auto { margin: 1.1rem 0 0; padding: .7rem 1rem; font-size: .8rem; line-height: 1.6;
                 color: rgba(255,255,255,.4); background: rgba(255,255,255,.02);
                 border-left: 2px solid rgba(201,168,76,.4); border-radius: 0 8px 8px 0; }
    .nota-auto b { color: rgba(255,255,255,.65); }

    .check { display: flex; align-items: center; gap: .55rem; margin: .9rem 0;
             font-size: .84rem; color: rgba(255,255,255,.6); cursor: pointer; }
    .check input { width: 15px; height: 15px; accent-color: #C9A84C; cursor: pointer; }
    .check em { color: rgba(255,255,255,.35); font-style: normal; }

    .previa { margin: .9rem 0; padding: .8rem 1rem; font-size: .84rem;
              color: rgba(255,255,255,.55); background: rgba(255,255,255,.02);
              border: 1px solid rgba(255,255,255,.06); border-radius: 10px; }
    .previa strong { color: #F4E28D; }
    .lista-tablas { display: flex; flex-wrap: wrap; gap: .35rem; margin-top: .7rem; }
    .lista-tablas span { padding: .18rem .55rem; border-radius: 6px; font-size: .72rem;
                         background: rgba(255,255,255,.04); color: rgba(255,255,255,.45);
                         font-family: 'Cascadia Code', 'Fira Code', monospace; }

    .enlace { background: none; border: none; color: #C9A84C; cursor: pointer;
              font-size: .8rem; font-family: inherit; text-decoration: underline;
              padding: 0 .3rem; }

    /* ── Tablas ── */
    .tabla-wrap { overflow-x: auto; border-radius: 12px; border: 1px solid rgba(255,255,255,.06); }
    .tabla { width: 100%; border-collapse: collapse; }
    .tabla th { background: rgba(255,255,255,.03); color: rgba(255,255,255,.45);
                padding: .75rem 1rem; text-align: left; font-size: .68rem; font-weight: 600;
                text-transform: uppercase; letter-spacing: .8px;
                border-bottom: 1px solid rgba(255,255,255,.06); white-space: nowrap; }
    .tabla td { padding: .7rem 1rem; border-bottom: 1px solid rgba(255,255,255,.03);
                font-size: .83rem; color: rgba(255,255,255,.7); }
    .tabla tr:hover td { background: rgba(255,255,255,.02); }
    .tabla .num { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    .fecha-col { color: rgba(255,255,255,.4) !important; font-size: .79rem !important;
                 white-space: nowrap; }
    .ip-col { font-family: 'Cascadia Code', 'Fira Code', monospace; font-size: .76rem !important;
              color: rgba(255,255,255,.4) !important; }
    .nota-col, .detalle-col { max-width: 260px; white-space: nowrap; overflow: hidden;
                              text-overflow: ellipsis; }
    .empty { text-align: center; color: rgba(255,255,255,.25); padding: 2rem !important; }

    .chip { display: inline-block; padding: .18rem .6rem; border-radius: 6px;
            font-size: .72rem; background: rgba(255,255,255,.04);
            border: 1px solid rgba(255,255,255,.08); color: rgba(255,255,255,.55); }
    .chip[data-estado="COMPLETADO"] { background: rgba(76,175,80,.12); color: #81C784;
                                      border-color: rgba(76,175,80,.25); }
    .chip[data-estado="EN_CURSO"]   { background: rgba(201,168,76,.12); color: #F4E28D;
                                      border-color: rgba(201,168,76,.25); }
    .chip[data-estado="FALLIDO"]    { background: rgba(229,115,115,.12); color: #EF9A9A;
                                      border-color: rgba(229,115,115,.25); }
    .chip[data-origen="AUTOMATICO"] { background: rgba(100,181,246,.1); color: #90CAF9;
                                      border-color: rgba(100,181,246,.2); }
    .chip[data-tipo="BORRADO_TOTAL"] { background: rgba(220,38,38,.12); color: #FCA5A5;
                                       border-color: rgba(220,38,38,.28); }
    .chip[data-tipo="RESTAURACION"]  { background: rgba(100,181,246,.12); color: #90CAF9;
                                       border-color: rgba(100,181,246,.25); }
    .chip.perdido { margin-left: .35rem; background: rgba(217,119,6,.14); color: #fcd34d;
                    border-color: rgba(217,119,6,.3); cursor: help; }

    .btn-restaurar { padding: .35rem .85rem; border-radius: 99px; cursor: pointer;
                     font-size: .76rem; font-family: inherit; white-space: nowrap;
                     background: rgba(100,181,246,.1); color: #90CAF9;
                     border: 1px solid rgba(100,181,246,.28); }
    .btn-restaurar:hover:not(:disabled) { background: rgba(100,181,246,.2); }
    .btn-restaurar:disabled { opacity: .3; cursor: not-allowed; }

    /* ── Modal ── */
    .modal-fondo { position: fixed; inset: 0; z-index: 500; display: flex;
                   align-items: center; justify-content: center; padding: 1.5rem;
                   background: rgba(0,0,0,.7); backdrop-filter: blur(6px); }
    .modal { width: 100%; max-width: 520px; padding: 1.75rem; border-radius: 16px;
             background: #14141c; border: 1px solid rgba(220,38,38,.25);
             box-shadow: 0 24px 60px rgba(0,0,0,.55); }
    .modal h3 { margin: 0 0 .8rem; font-size: 1.1rem; font-weight: 400; color: #fff; }
    .modal p { margin: 0 0 .8rem; font-size: .86rem; line-height: 1.65;
               color: rgba(255,255,255,.6); }
    .modal p strong { color: #FCA5A5; }
    .modal-nota { font-size: .8rem !important; color: rgba(255,255,255,.4) !important; }
    .modal .campo { width: 100%; margin-bottom: 1.1rem; }
    .modal-botones { display: flex; gap: .7rem; justify-content: flex-end; }

    /* ── Mensaje ── */
    .mensaje { position: fixed; bottom: 1.5rem; right: 1.5rem; z-index: 600;
               max-width: 460px; padding: .9rem 1.2rem; border-radius: 12px;
               font-size: .85rem; line-height: 1.6; background: rgba(76,175,80,.12);
               border: 1px solid rgba(76,175,80,.3); color: #A5D6A7;
               box-shadow: 0 12px 36px rgba(0,0,0,.4); }
    .mensaje.error { background: rgba(220,38,38,.12); border-color: rgba(220,38,38,.3);
                     color: #FCA5A5; }

    @media (max-width: 768px) {
      .bk-page { padding: 1.25rem; }
      .fila-accion { flex-direction: column; align-items: stretch; }
      .btn-oro, .btn-rojo { justify-content: center; }
    }
  `]
})
export class RespaldosComponent implements OnInit, OnDestroy {

  estado: Estado | null = null;
  respaldos: Respaldo[] = [];
  operaciones: Operacion[] = [];
  previa: VistaPrevia | null = null;

  notaRespaldo = '';
  borrarBitacoras = false;
  respaldarAntes = true;
  verTablas = false;
  confirmacionBorrado = '';
  confirmacionRestauracion = '';
  restaurando: Respaldo | null = null;

  mensaje = '';
  mensajeEsError = false;

  /**
   * El sondeo del estado. Dos segundos: bastante para que la barra se mueva sin
   * dar tirones, y poco trabajo — la respuesta es un puñado de campos y una
   * consulta al diario, no a los datos.
   */
  private sondeo?: Subscription;
  /** Para saber cuándo una tarea acaba de terminar y refrescar las listas. */
  private habiaTarea = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.refrescarTodo();
    this.cargarVistaPrevia();
    this.sondeo = interval(2000).subscribe(() => this.cargarEstado());
  }

  ngOnDestroy(): void {
    this.sondeo?.unsubscribe();
  }

  /** Falso mientras algo corre o si el módulo no puede trabajar. */
  get puedeActuar(): boolean {
    return !!this.estado && this.estado.disponible && !this.estado.tarea;
  }

  private refrescarTodo(): void {
    this.cargarEstado();
    this.cargarRespaldos();
    this.cargarOperaciones();
  }

  cargarEstado(): void {
    this.http.get<Estado>(`${environment.apiUrl}/respaldos/estado`).subscribe({
      next: e => {
        const hayTarea = !!e.tarea;
        this.estado = e;
        // Al pasar de «trabajando» a «libre» se recargan las listas: es cuando
        // el respaldo o la restauración acaba de escribir su fila.
        if (this.habiaTarea && !hayTarea) {
          this.cargarRespaldos();
          this.cargarOperaciones();
          this.cargarVistaPrevia();
        }
        this.habiaTarea = hayTarea;
      },
      error: () => { /* el sondeo no molesta con errores: el siguiente lo reintenta */ }
    });
  }

  cargarRespaldos(): void {
    this.http.get<Respaldo[]>(`${environment.apiUrl}/respaldos`)
      .subscribe({ next: r => this.respaldos = r, error: () => this.respaldos = [] });
  }

  cargarOperaciones(): void {
    this.http.get<Operacion[]>(`${environment.apiUrl}/respaldos/operaciones`)
      .subscribe({ next: o => this.operaciones = o, error: () => this.operaciones = [] });
  }

  cargarVistaPrevia(): void {
    const p = new HttpParams().set('borrarBitacoras', this.borrarBitacoras);
    this.http.get<VistaPrevia>(`${environment.apiUrl}/respaldos/vista-previa-borrado`, { params: p })
      .subscribe({ next: v => this.previa = v, error: () => this.previa = null });
  }

  respaldar(): void {
    this.http.post<Respaldo>(`${environment.apiUrl}/respaldos`, { nota: this.notaRespaldo })
      .subscribe({
        next: () => {
          this.avisar('Guardando el punto. Puedes seguir trabajando.', false);
          this.notaRespaldo = '';
          this.cargarEstado();
          this.cargarRespaldos();
        },
        error: e => this.avisar(this.porQue(e), true)
      });
  }

  borrar(): void {
    const p = new HttpParams().set('borrarBitacoras', this.borrarBitacoras);
    this.http.post<Operacion>(`${environment.apiUrl}/respaldos/borrar-datos`, {
      confirmacion: this.confirmacionBorrado,
      respaldarAntes: this.respaldarAntes
    }, { params: p }).subscribe({
      next: o => {
        this.avisar(`Base vaciada: ${(o.filasAfectadas ?? 0).toLocaleString('es')} registros. `
                  + 'Ahora se puede restaurar desde cualquier punto de la lista.', false);
        this.confirmacionBorrado = '';
        this.refrescarTodo();
        this.cargarVistaPrevia();
      },
      error: e => { this.avisar(this.porQue(e), true); this.refrescarTodo(); }
    });
  }

  pedirRestauracion(r: Respaldo): void {
    this.restaurando = r;
    this.confirmacionRestauracion = '';
  }

  restaurar(): void {
    if (!this.restaurando) { return; }
    this.http.post<Operacion>(`${environment.apiUrl}/respaldos/restaurar`, {
      confirmacion: this.confirmacionRestauracion,
      idRespaldo: this.restaurando.idRespaldo
    }).subscribe({
      next: () => {
        this.avisar('Restaurando. El resto del sistema queda en mantenimiento hasta que termine.', false);
        this.restaurando = null;
        this.confirmacionRestauracion = '';
        this.cargarEstado();
      },
      error: e => this.avisar(this.porQue(e), true)
    });
  }

  // ── Presentación ────────────────────────────────────────────────

  etiquetaEstado(e: string): string {
    return e === 'COMPLETADO' ? 'bien' : e === 'EN_CURSO' ? 'en curso' : 'falló';
  }

  tam(bytes: number): string {
    if (!bytes) { return '0 B'; }
    const u = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), u.length - 1);
    return (bytes / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1) + ' ' + u[i];
  }

  hace(fecha: string): string {
    const min = Math.floor((Date.now() - new Date(fecha).getTime()) / 60000);
    if (min < 1) { return 'un momento'; }
    if (min < 60) { return min + ' min'; }
    const h = Math.floor(min / 60);
    if (h < 24) { return h + (h === 1 ? ' hora' : ' horas'); }
    const d = Math.floor(h / 24);
    return d + (d === 1 ? ' día' : ' días');
  }

  private avisar(texto: string, esError: boolean): void {
    this.mensaje = texto;
    this.mensajeEsError = esError;
    // Los errores se quedan hasta que se cierren; los avisos buenos se van solos.
    if (!esError) { setTimeout(() => this.mensaje = '', 8000); }
  }

  /** El mensaje del servidor si lo hay; nunca un «error inesperado» a secas. */
  private porQue(e: any): string {
    return e?.error?.message || 'No se pudo completar la operación.';
  }
}
