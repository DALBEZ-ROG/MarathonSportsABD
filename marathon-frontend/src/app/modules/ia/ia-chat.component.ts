import { Component, OnInit, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AsistenteService, IAResponse, ChatMessage } from '../../core/services/asistente.service';
import { AppIconComponent } from '../../shared/components/icon/icon.component';

/**
 * Preguntarle a los datos en castellano (F82).
 *
 * <p><b>Qué estaba mal.</b> La pantalla ofrecía ejemplos y una caja de texto como
 * si el asistente funcionara. En esta instalación <b>está apagado</b>
 * ({@code app.ia.enabled=false}), y eso solo se descubría <i>después</i> de
 * escribir la pregunta y enviarla: llegaba un 503 y ahí te enterabas. Ahora se
 * pregunta al entrar y, si está apagado, se dice antes de que nadie escriba —con
 * lo que haría si estuviera encendido y cómo se enciende.
 *
 * <p><b>Y no decía qué puede ver.</b> Un asistente que consulta la base de datos
 * plantea una pregunta legítima —«¿puede leerlo todo?»— que la pantalla no
 * contestaba. La respuesta es que no: solo ejecuta <b>SELECT</b>, dentro de una
 * transacción de solo lectura, y únicamente sobre una lista blanca de tablas de
 * negocio. Usuarios, roles, permisos y la bitácora quedan fuera a propósito. Eso
 * estaba escrito en el validador del servidor y en ningún sitio donde lo viera
 * quien usa la pantalla.
 */
@Component({
  selector: 'app-ia-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppIconComponent],
  template: `
    <div class="ia-wrap">

      <header class="ia-cab">
        <div class="cab-txt">
          <h1 class="inline-icon-text"><app-icon name="bot" [size]="22"/> Asistente</h1>
          <p class="sub">Pregúntale a los datos en castellano y te contesta con una tabla.</p>
        </div>
        <span class="badge-beta">Beta</span>
      </header>

      <!-- ── Apagado: se dice antes de escribir, no después ───────── -->
      <section class="apagado" *ngIf="estado === 'apagado'">
        <h2>El asistente está apagado en esta instalación</h2>
        <p>
          No es un fallo: es un interruptor. Con <code>app.ia.enabled=false</code>
          ni se llama al modelo ni se toca la base de datos, y encenderlo es una
          <strong>decisión consciente</strong> porque implica mandar la pregunta a
          un servicio externo.
        </p>
        <p class="como">
          Se enciende poniendo <code>app.ia.enabled=true</code> —o la variable
          <code>IA_ENABLED</code>— y una clave de Anthropic en la configuración
          local del servidor.
        </p>
        <p class="mientras">
          Mientras tanto, lo que casi siempre se le pregunta ya está resuelto sin
          IA: <a routerLink="/analitica">el análisis del negocio</a> tiene lo más
          vendido, los mejores clientes y las ventas por región, y
          <a routerLink="/reportes">los reportes</a> dan la lista filtrada y
          exportable.
        </p>
      </section>

      <!-- ── Encendido ────────────────────────────────────────────── -->
      <ng-container *ngIf="estado === 'encendido'">

        <details class="alcance">
          <summary>Qué puede ver y qué no</summary>
          <ul>
            <li><strong>Solo lee.</strong> Cada consulta se comprueba antes de
              ejecutarse: tiene que ser una única sentencia y tiene que ser un
              <code>SELECT</code>. Además corre en una transacción de solo lectura,
              así que el motor rechazaría cualquier escritura aunque colara.</li>
            <li><strong>Solo tablas de negocio.</strong> Pedidos, inventario,
              compras, producción, clientes… sobre una lista <em>blanca</em>: una
              tabla nueva queda fuera mientras nadie la añada a mano.</li>
            <li><strong>Nunca el modelo de seguridad ni la bitácora.</strong>
              Usuarios, roles, permisos y el registro de acciones están excluidos a
              propósito. Para eso está <a routerLink="/auditoria">Auditoría</a>,
              con su propio permiso.</li>
          </ul>
        </details>

        <div class="chat-area" #chatArea>
          <div class="ejemplos" *ngIf="mensajes.length === 0">
            <p class="ejemplos-titulo">Prueba con una de estas, o escribe la tuya:</p>
            <div class="chips">
              <button class="chip" *ngFor="let ej of ejemplos" (click)="usarEjemplo(ej)">{{ ej }}</button>
            </div>
            <p class="consejo">
              Cuanto más concreta la pregunta, mejor sale la tabla. «Los cinco
              productos más vendidos este mes» funciona mejor que «ventas».
            </p>
          </div>

          <div *ngFor="let msg of mensajes" class="msg-row" [class.right]="msg.tipo === 'usuario'">
            <div class="bubble" [class.bubble-user]="msg.tipo === 'usuario'" [class.bubble-ia]="msg.tipo === 'ia'">
              <ng-container *ngIf="msg.tipo === 'usuario'">
                {{ msg.texto }}
              </ng-container>

              <ng-container *ngIf="msg.tipo === 'ia' && msg.respuesta as r">
                <div class="error-msg" *ngIf="r.error">
                  {{ r.error }}
                  <button class="reintentar" (click)="reintentar(r.pregunta)">Volver a preguntar</button>
                </div>

                <ng-container *ngIf="!r.error">
                  <p class="explicacion" *ngIf="r.explicacion">{{ r.explicacion }}</p>

                  <div class="tabla-wrap" *ngIf="r.resultados && r.resultados.length > 0">
                    <table class="tabla-resultados">
                      <thead>
                        <tr>
                          <th *ngFor="let col of columnas(r.resultados)"
                              [class.num]="esNumerica(r.resultados, col)">{{ titulo(col) }}</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr *ngFor="let fila of r.resultados">
                          <td *ngFor="let col of columnas(r.resultados)"
                              [class.num]="esNumerica(r.resultados, col)">{{ celda(fila[col]) }}</td>
                        </tr>
                      </tbody>
                    </table>
                    <p class="total-filas">
                      {{ r.totalResultados }} {{ r.totalResultados === 1 ? 'fila' : 'filas' }}
                    </p>
                  </div>

                  <p class="sin-resultados" *ngIf="r.resultados && r.resultados.length === 0">
                    La consulta corrió bien, pero no hay ninguna fila que cumpla lo
                    que preguntaste.
                  </p>

                  <div class="sql-section" *ngIf="isAdmin && r.sql">
                    <div class="sql-barra">
                      <button class="ver-sql" (click)="msg.mostrarSql = !msg.mostrarSql">
                        {{ msg.mostrarSql ? '▼ Ocultar la consulta' : '▶ Ver la consulta que ejecutó' }}
                      </button>
                      <button class="copiar" *ngIf="msg.mostrarSql" (click)="copiar(msg, r.sql)">
                        {{ msg.copiado ? 'Copiada' : 'Copiar' }}
                      </button>
                    </div>
                    <pre class="sql-code" *ngIf="msg.mostrarSql">{{ r.sql }}</pre>
                    <p class="sql-nota" *ngIf="msg.mostrarSql">
                      Se comprobó antes de ejecutarla y corrió en solo lectura.
                      Si no cuadra con lo que preguntaste, esto es lo que hay que mirar.
                    </p>
                  </div>
                </ng-container>
              </ng-container>
            </div>
          </div>

          <div class="msg-row" *ngIf="cargando">
            <div class="bubble bubble-ia">
              <span class="spinner"></span> Pensando la consulta…
            </div>
          </div>
        </div>

        <div class="input-area">
          <input
            type="text"
            [(ngModel)]="pregunta"
            (keyup.enter)="enviar()"
            placeholder="Pregunta algo sobre pedidos, inventario, compras o ventas…"
            [disabled]="cargando" />
          <button class="btn-enviar" (click)="enviar()" [disabled]="cargando || !pregunta.trim()"
                  aria-label="Enviar">
            <span *ngIf="!cargando">➤</span>
            <span *ngIf="cargando" class="spinner"></span>
          </button>
        </div>
        <p class="pie-aviso">
          La pregunta se envía a un servicio externo para traducirla a una consulta.
          Los datos que vuelven salen de esta base.
        </p>
      </ng-container>

      <p class="cargando-estado" *ngIf="estado === 'comprobando'">Comprobando si el asistente está disponible…</p>
    </div>
  `,
  styles: [`
    .ia-wrap { max-width: 1000px; margin: 0 auto; padding: clamp(1rem, 3vw, 2.5rem);
               display: flex; flex-direction: column; min-height: 0; }

    .ia-cab { display: flex; justify-content: space-between; align-items: flex-start;
              gap: 1rem; margin-bottom: 1.25rem; }
    .ia-cab h1 { margin: 0 0 .3rem; font-size: 1.6rem; color: var(--ms-text); }
    .sub { margin: 0; color: var(--ms-text-muted); font-size: .92rem; }
    .badge-beta { flex-shrink: 0; font-size: .66rem; font-weight: 700;
                  letter-spacing: .06em; text-transform: uppercase;
                  background: var(--ms-gold-dim); color: var(--ms-gold-light);
                  border: 1px solid rgba(201,168,76,.35);
                  padding: .2rem .5rem; border-radius: 99px; }

    /* ── Apagado ───────────────────────────────────────────────── */
    .apagado { background: var(--ms-bg-card); border: 1px solid var(--ms-border);
               border-left: 3px solid #d97706; border-radius: var(--ms-radius);
               padding: 1.6rem 1.8rem; max-width: 78ch; }
    .apagado h2 { margin: 0 0 .8rem; font-size: 1.15rem; color: var(--ms-text); }
    .apagado p { margin: 0 0 .9rem; font-size: .9rem; line-height: 1.7;
                 color: var(--ms-text-muted); }
    .apagado p:last-child { margin-bottom: 0; }
    .apagado strong { color: rgba(255,255,255,0.85); }
    .apagado code { font-size: .82rem; background: rgba(255,255,255,0.06);
                    padding: .1rem .35rem; border-radius: 4px;
                    color: rgba(255,255,255,0.75); }
    .apagado a { color: var(--ms-gold); text-decoration: none; }
    .apagado a:hover { text-decoration: underline; }
    .como { padding-top: .9rem; border-top: 1px solid var(--ms-border); }
    .mientras { padding-top: .9rem; border-top: 1px solid var(--ms-border); }

    /* ── Alcance ───────────────────────────────────────────────── */
    .alcance { margin-bottom: 1.25rem; background: rgba(255,255,255,0.02);
               border: 1px solid var(--ms-border); border-radius: var(--ms-radius);
               padding: .8rem 1.2rem; }
    .alcance summary { cursor: pointer; font-size: .85rem; color: var(--ms-text-muted);
                       list-style: none; }
    .alcance summary::before { content: '▸ '; }
    .alcance[open] summary::before { content: '▾ '; }
    .alcance summary:hover { color: var(--ms-gold); }
    .alcance ul { margin: .9rem 0 .2rem; padding-left: 1.1rem; }
    .alcance li { font-size: .84rem; line-height: 1.7; color: var(--ms-text-muted);
                  margin-bottom: .5rem; }
    .alcance strong { color: rgba(255,255,255,0.85); }
    .alcance code { font-size: .8rem; color: rgba(255,255,255,0.7); }
    .alcance a { color: var(--ms-gold); text-decoration: none; }

    .consejo { margin: 1.1rem 0 0; font-size: .8rem; line-height: 1.6;
               color: rgba(255,255,255,0.35); }

    /* ── Resultados ────────────────────────────────────────────── */
    .tabla-resultados th.num, .tabla-resultados td.num { text-align: right;
                                                         font-variant-numeric: tabular-nums; }
    .total-filas { margin: .6rem 0 0; font-size: .78rem; color: var(--ms-text-muted); }
    .sin-resultados { margin: 0; font-size: .88rem; line-height: 1.6;
                      color: var(--ms-text-muted); }

    .sql-barra { display: flex; align-items: center; gap: .6rem; flex-wrap: wrap; }
    .copiar { background: transparent; border: 1px solid var(--ms-border);
              color: var(--ms-text-muted); padding: .2rem .6rem; border-radius: 99px;
              font-size: .72rem; cursor: pointer; font-family: inherit; }
    .copiar:hover { border-color: var(--ms-gold); color: var(--ms-gold); }
    .sql-nota { margin: .5rem 0 0; font-size: .74rem; line-height: 1.6;
                color: rgba(255,255,255,0.32); }

    .error-msg .reintentar { display: block; margin-top: .7rem; background: transparent;
                             border: 1px solid var(--ms-border); color: var(--ms-text-muted);
                             padding: .35rem .8rem; border-radius: var(--ms-radius-sm);
                             font-size: .78rem; cursor: pointer; font-family: inherit; }
    .error-msg .reintentar:hover { border-color: var(--ms-gold); color: var(--ms-gold); }

    .pie-aviso { margin: .7rem 0 0; font-size: .74rem; line-height: 1.6;
                 color: rgba(255,255,255,0.3); text-align: center; }
    .cargando-estado { color: var(--ms-text-muted); font-size: .9rem; }
  `]
})
export class IAChatComponent implements OnInit, AfterViewChecked {

  @ViewChild('chatArea') chatArea!: ElementRef<HTMLDivElement>;

  pregunta = '';

  /**
   * F88: el estado ya no vive aquí.
   *
   * <p>La conversación, el encendido del módulo y los ejemplos están en
   * {@link AsistenteService}, que es de la sesión y no de la pantalla. El motivo
   * es la burbuja flotante: si cada vista guardara lo suyo, pasar de la burbuja
   * a esta página —que es justo lo que ofrece su botón de «abrir en grande»—
   * perdería lo que llevaras preguntado. Ahora es la misma conversación.
   *
   * <p>El servicio se expone como `a` para que la plantilla lo use directamente.
   * Los accesores de abajo existen para no reescribir la plantilla entera.
   */
  constructor(public a: AsistenteService) {}

  get isAdmin(): boolean { return this.a.esAdmin; }
  get cargando(): boolean { return this.a.cargando; }
  get ejemplos(): string[] { return this.a.ejemplos; }
  get mensajes(): ChatMessage[] { return this.a.mensajes; }
  get estado(): 'comprobando' | 'encendido' | 'apagado' { return this.a.estado; }

  ngOnInit(): void {
    this.a.iniciar();
    // Al entrar por la pantalla completa, la burbuja sobra: se pliega.
    this.a.minimizar();
  }

  ngAfterViewChecked(): void {
    if (this.a.hayQueBajar && this.chatArea) {
      this.chatArea.nativeElement.scrollTop = this.chatArea.nativeElement.scrollHeight;
      this.a.hayQueBajar = false;
    }
  }

  usarEjemplo(ejemplo: string): void { this.pregunta = ejemplo; }

  reintentar(pregunta: string): void { this.pregunta = pregunta; }

  columnas(resultados: Array<{ [key: string]: any }>): string[] { return this.a.columnas(resultados); }

  esNumerica(resultados: Array<{ [key: string]: any }>, col: string): boolean {
    return this.a.esNumerica(resultados, col);
  }

  titulo(col: string): string { return this.a.titulo(col); }

  celda(v: any): string { return this.a.celda(v); }

  copiar(msg: ChatMessage, sql: string): void { this.a.copiar(msg, sql); }

  enviar(): void {
    this.a.enviar(this.pregunta);
    this.pregunta = '';
  }
}
