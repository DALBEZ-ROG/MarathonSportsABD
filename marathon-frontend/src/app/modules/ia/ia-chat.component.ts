import { Component, OnInit, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';

interface IAResponse {
  pregunta: string;
  sql: string | null;
  explicacion: string | null;
  resultados: Array<{ [key: string]: any }> | null;
  totalResultados: number | null;
  error: string | null;
  timestamp: string;
}

interface ChatMessage {
  tipo: 'usuario' | 'ia';
  texto?: string;
  respuesta?: IAResponse;
  mostrarSql?: boolean;
}

@Component({
  selector: 'app-ia-chat',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="ia-wrap">
      <div class="ia-header">
        <div class="title-row">
          <h1>🤖 Asistente IA — Marathon Sports</h1>
          <span class="badge-beta">Beta</span>
        </div>
        <p class="subtitle">Consulta datos en lenguaje natural</p>
      </div>

      <div class="chat-area" #chatArea>
        <div class="ejemplos" *ngIf="mensajes.length === 0">
          <p class="ejemplos-titulo">Prueba preguntando:</p>
          <div class="chips">
            <button class="chip" *ngFor="let ej of ejemplos" (click)="usarEjemplo(ej)">{{ ej }}</button>
          </div>
        </div>

        <div *ngFor="let msg of mensajes" class="msg-row" [class.right]="msg.tipo === 'usuario'">
          <div class="bubble" [class.bubble-user]="msg.tipo === 'usuario'" [class.bubble-ia]="msg.tipo === 'ia'">
            <ng-container *ngIf="msg.tipo === 'usuario'">
              {{ msg.texto }}
            </ng-container>

            <ng-container *ngIf="msg.tipo === 'ia' && msg.respuesta as r">
              <div class="error-msg" *ngIf="r.error">{{ r.error }}</div>

              <ng-container *ngIf="!r.error">
                <p class="explicacion" *ngIf="r.explicacion">{{ r.explicacion }}</p>

                <div class="tabla-wrap" *ngIf="r.resultados && r.resultados.length > 0">
                  <table class="tabla-resultados">
                    <thead>
                      <tr>
                        <th *ngFor="let col of columnas(r.resultados)">{{ col }}</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr *ngFor="let fila of r.resultados">
                        <td *ngFor="let col of columnas(r.resultados)">{{ fila[col] }}</td>
                      </tr>
                    </tbody>
                  </table>
                  <p class="total-filas">{{ r.totalResultados }} resultado(s)</p>
                </div>

                <p class="sin-resultados" *ngIf="r.resultados && r.resultados.length === 0">
                  Sin resultados para esta consulta.
                </p>

                <div class="sql-section" *ngIf="isAdmin && r.sql">
                  <button class="ver-sql" (click)="msg.mostrarSql = !msg.mostrarSql">
                    {{ msg.mostrarSql ? '▼ Ocultar SQL' : '▶ Ver SQL' }}
                  </button>
                  <pre class="sql-code" *ngIf="msg.mostrarSql">{{ r.sql }}</pre>
                </div>
              </ng-container>
            </ng-container>
          </div>
        </div>

        <div class="msg-row" *ngIf="cargando">
          <div class="bubble bubble-ia">
            <span class="spinner"></span> Pensando…
          </div>
        </div>
      </div>

      <div class="input-area">
        <input
          type="text"
          [(ngModel)]="pregunta"
          (keyup.enter)="enviar()"
          placeholder="Pregunta algo sobre tus pedidos, inventario o ventas..."
          [disabled]="cargando" />
        <button class="btn-enviar" (click)="enviar()" [disabled]="cargando || !pregunta.trim()">
          <span *ngIf="!cargando">➤</span>
          <span *ngIf="cargando" class="spinner"></span>
        </button>
      </div>
    </div>
  `,
  styles: [`
    .ia-wrap{display:flex;flex-direction:column;height:100%;min-height:0;background:#f5f5f5}
    .ia-header{padding:1rem 1.5rem;background:#fff;border-bottom:1px solid #e0e0e0}
    .title-row{display:flex;align-items:center;gap:.8rem}
    .ia-header h1{color:#2d5a27;margin:0;font-size:1.3rem}
    .badge-beta{background:#2d5a27;color:#fff;font-size:.7rem;font-weight:700;padding:.15rem .5rem;border-radius:10px;text-transform:uppercase;letter-spacing:.5px}
    .subtitle{color:#666;margin:.3rem 0 0;font-size:.85rem}
    .chat-area{flex:1;overflow-y:auto;padding:1.5rem;display:flex;flex-direction:column;gap:1rem;min-height:0}
    .ejemplos{margin-bottom:.5rem}
    .ejemplos-titulo{color:#666;font-size:.9rem;margin:0 0 .8rem}
    .chips{display:flex;flex-wrap:wrap;gap:.6rem}
    .chip{background:#fff;border:1px solid #2d5a27;color:#2d5a27;padding:.5rem .9rem;border-radius:18px;cursor:pointer;font-size:.82rem;transition:background .15s}
    .chip:hover{background:#2d5a27;color:#fff}
    .msg-row{display:flex;justify-content:flex-start}
    .msg-row.right{justify-content:flex-end}
    .bubble{max-width:80%;padding:.8rem 1rem;border-radius:12px;font-size:.9rem;line-height:1.4;box-shadow:0 1px 4px rgba(0,0,0,.08)}
    .bubble-user{background:#2d5a27;color:#fff;border-bottom-right-radius:2px}
    .bubble-ia{background:#f0f0f0;color:#222;border-bottom-left-radius:2px}
    .explicacion{margin:0 0 .6rem}
    .error-msg{color:#c62828;font-weight:600}
    .tabla-wrap{overflow-x:auto;margin-top:.5rem}
    .tabla-resultados{width:100%;border-collapse:collapse;background:#fff;border-radius:6px;overflow:hidden;font-size:.82rem}
    .tabla-resultados th,.tabla-resultados td{padding:.5rem .6rem;text-align:left;border-bottom:1px solid #e8e8e8;white-space:nowrap}
    .tabla-resultados th{background:#2d5a27;color:#fff;font-size:.72rem;text-transform:uppercase;letter-spacing:.3px}
    .tabla-resultados tr:nth-child(even) td{background:#fafafa}
    .total-filas{color:#666;font-size:.75rem;margin:.5rem 0 0}
    .sin-resultados{color:#888;font-style:italic;margin:.4rem 0 0}
    .sql-section{margin-top:.7rem}
    .ver-sql{background:none;border:none;color:#2d5a27;cursor:pointer;font-size:.8rem;font-weight:600;padding:0}
    .sql-code{background:#1e1e1e;color:#9cdcfe;padding:.8rem;border-radius:6px;margin:.5rem 0 0;font-size:.78rem;overflow-x:auto;white-space:pre-wrap;word-break:break-word}
    .input-area{display:flex;gap:.6rem;padding:1rem 1.5rem;background:#fff;border-top:1px solid #e0e0e0}
    .input-area input{flex:1;padding:.7rem 1rem;border:1px solid #ccc;border-radius:24px;font-size:.9rem;outline:none}
    .input-area input:focus{border-color:#2d5a27}
    .input-area input:disabled{background:#f5f5f5}
    .btn-enviar{background:#2d5a27;color:#fff;border:none;width:44px;height:44px;border-radius:50%;cursor:pointer;font-size:1.1rem;display:flex;align-items:center;justify-content:center;flex-shrink:0}
    .btn-enviar:disabled{opacity:.5;cursor:default}
    .spinner{display:inline-block;width:14px;height:14px;border:2px solid rgba(45,90,39,.3);border-top-color:#2d5a27;border-radius:50%;animation:spin .7s linear infinite;vertical-align:middle}
    .bubble-ia .spinner{border-color:rgba(45,90,39,.3);border-top-color:#2d5a27}
    .btn-enviar .spinner{border-color:rgba(255,255,255,.4);border-top-color:#fff}
    @keyframes spin{to{transform:rotate(360deg)}}
  `]
})
export class IAChatComponent implements OnInit, AfterViewChecked {

  @ViewChild('chatArea') chatArea!: ElementRef<HTMLDivElement>;

  private apiUrl = environment.apiUrl;

  pregunta = '';
  cargando = false;
  isAdmin = false;
  ejemplos: string[] = [];
  mensajes: ChatMessage[] = [];

  private debeScrollear = false;

  constructor(private http: HttpClient, private authService: AuthService) {}

  ngOnInit(): void {
    this.isAdmin = this.authService.hasRol('Administrador');
    this.http.get<string[]>(`${this.apiUrl}/ia/ejemplos`).subscribe({
      next: res => { this.ejemplos = res; },
      error: () => { this.ejemplos = []; }
    });
  }

  ngAfterViewChecked(): void {
    if (this.debeScrollear && this.chatArea) {
      this.chatArea.nativeElement.scrollTop = this.chatArea.nativeElement.scrollHeight;
      this.debeScrollear = false;
    }
  }

  usarEjemplo(ejemplo: string): void {
    this.pregunta = ejemplo;
  }

  columnas(resultados: Array<{ [key: string]: any }>): string[] {
    if (!resultados || resultados.length === 0) { return []; }
    return Object.keys(resultados[0]);
  }

  enviar(): void {
    const texto = this.pregunta.trim();
    if (!texto || this.cargando) { return; }

    this.mensajes.push({ tipo: 'usuario', texto });
    this.pregunta = '';
    this.cargando = true;
    this.debeScrollear = true;

    this.http.post<IAResponse>(`${this.apiUrl}/ia/consultar`, { pregunta: texto }).subscribe({
      next: res => {
        this.mensajes.push({ tipo: 'ia', respuesta: res, mostrarSql: false });
        this.cargando = false;
        this.debeScrollear = true;
      },
      error: err => {
        const respuesta: IAResponse = {
          pregunta: texto,
          sql: null,
          explicacion: null,
          resultados: null,
          totalResultados: null,
          error: 'Error al comunicarse con el servidor. Intenta de nuevo.',
          timestamp: new Date().toISOString()
        };
        this.mensajes.push({ tipo: 'ia', respuesta });
        this.cargando = false;
        this.debeScrollear = true;
      }
    });
  }
}
