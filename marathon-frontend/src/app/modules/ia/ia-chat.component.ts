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
    /* Inherits global dark theme from styles.scss */
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
