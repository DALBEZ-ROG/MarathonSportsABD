import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export interface IAResponse {
  pregunta: string;
  sql: string | null;
  explicacion: string | null;
  resultados: Array<{ [key: string]: any }> | null;
  totalResultados: number | null;
  error: string | null;
  timestamp: string;
}

export interface ChatMessage {
  tipo: 'usuario' | 'ia';
  texto?: string;
  respuesta?: IAResponse;
  mostrarSql?: boolean;
  copiado?: boolean;
}

/**
 * La conversación con el asistente, fuera de cualquier pantalla (F88).
 *
 * <p><b>Por qué es un servicio y no vive en el componente.</b> Desde la F88 el
 * asistente se puede abrir de dos maneras: la burbuja flotante, que está en
 * todas las pantallas, y la página completa en <code>/ia</code>. Si cada una
 * guardara sus mensajes serían dos conversaciones distintas, y pasar de una a
 * otra —que es justo lo que ofrece el botón de «abrir en grande»— perdería lo
 * que llevabas preguntado. Aquí el estado es uno solo: preguntas en la burbuja,
 * abres la página y la conversación está entera.
 *
 * <p>Y sobrevive a la navegación. Un servicio <code>providedIn: 'root'</code>
 * vive lo que vive la sesión, así que cambiar de pantalla no borra nada; que es
 * lo que se espera de algo que se queda abierto mientras trabajas.
 *
 * <p><b>Sobrevive a un F5, y no más.</b> La conversación se guarda en
 * <code>sessionStorage</code>, no en <code>localStorage</code>, y la diferencia
 * importa: recargar la página no debería vaciar algo que el usuario dejó
 * abierto, pero cerrar la pestaña sí. Estos mensajes pueden llevar cifras del
 * negocio dentro, y no tienen por qué quedarse en el navegador esperando a la
 * siguiente persona que se siente delante.
 */
@Injectable({ providedIn: 'root' })
export class AsistenteService {

  private apiUrl = environment.apiUrl;

  mensajes: ChatMessage[] = [];
  ejemplos: string[] = [];
  cargando = false;

  /** Si el módulo está encendido. Se pregunta antes de dejar escribir. */
  estado: 'comprobando' | 'encendido' | 'apagado' = 'comprobando';

  /** Solo el administrador recibe el SQL; el servidor lo recorta (F87). */
  esAdmin = false;

  /** Si el panel flotante está desplegado. La burbuja es el estado plegado. */
  abierto = false;

  /** Para que la vista sepa que tiene que bajar el scroll. */
  hayQueBajar = false;

  private arrancado = false;

  private static readonly CLAVE = 'marathon_asistente';

  constructor(private http: HttpClient, private auth: AuthService) {
    this.recuperar();
  }

  /**
   * Recupera lo que hubiera de un F5. Entre try/catch porque en una ventana
   * privada o con el almacenamiento bloqueado, leerlo LANZA en vez de devolver
   * nulo: sin esto, el asistente se caería entero por no poder recordar.
   */
  private recuperar(): void {
    try {
      const crudo = sessionStorage.getItem(AsistenteService.CLAVE);
      if (!crudo) { return; }
      const guardado = JSON.parse(crudo);
      this.mensajes = Array.isArray(guardado?.mensajes) ? guardado.mensajes : [];
      this.abierto = !!guardado?.abierto;
    } catch { /* sin memoria: se empieza de cero, que es lo de antes */ }
  }

  private guardar(): void {
    try {
      sessionStorage.setItem(AsistenteService.CLAVE,
          JSON.stringify({ mensajes: this.mensajes, abierto: this.abierto }));
    } catch { /* cuota llena o almacenamiento bloqueado: no es motivo para fallar */ }
  }

  /**
   * Pregunta por el estado del módulo y carga los ejemplos. Idempotente: la
   * llaman la burbuja y la página, y solo la primera hace el viaje.
   */
  iniciar(): void {
    if (this.arrancado) { return; }
    this.arrancado = true;
    this.esAdmin = this.auth.hasRol('Administrador');

    this.http.get<{ habilitado: boolean }>(`${this.apiUrl}/ia/estado`).subscribe({
      next: res => {
        this.estado = res?.habilitado ? 'encendido' : 'apagado';
        if (this.estado === 'encendido') { this.cargarEjemplos(); }
      },
      // Si ni siquiera se puede preguntar por el estado, se trata como apagado:
      // es lo que el usuario va a ver de todas formas, y así no se le deja
      // escribir en una caja que no va a responder.
      error: () => { this.estado = 'apagado'; }
    });
  }

  private cargarEjemplos(): void {
    this.http.get<string[]>(`${this.apiUrl}/ia/ejemplos`).subscribe({
      next: res => { this.ejemplos = res ?? []; },
      error: () => { this.ejemplos = []; }
    });
  }

  abrir(): void { this.abierto = true; this.iniciar(); this.hayQueBajar = true; this.guardar(); }
  minimizar(): void { this.abierto = false; this.guardar(); }

  /** Vacía la conversación. Lo pide el usuario; no pasa solo. */
  limpiar(): void { this.mensajes = []; this.guardar(); }

  enviar(texto: string): void {
    const limpio = (texto ?? '').trim();
    if (!limpio || this.cargando) { return; }

    this.mensajes.push({ tipo: 'usuario', texto: limpio });
    this.cargando = true;
    this.hayQueBajar = true;
    this.guardar();

    this.http.post<IAResponse>(`${this.apiUrl}/ia/consultar`, { pregunta: limpio }).subscribe({
      next: res => {
        this.mensajes.push({ tipo: 'ia', respuesta: res, mostrarSql: false });
        this.cargando = false;
        this.hayQueBajar = true;
        this.guardar();
      },
      error: err => {
        // 503: el modulo esta apagado (app.ia.enabled=false). El backend manda
        // el motivo en el cuerpo; conviene mostrarlo tal cual en vez del
        // generico, porque no es un fallo sino una decision de despliegue.
        const mensaje = err?.status === 503
          ? (err?.error?.error ?? 'El asistente está apagado en esta instalación.')
          : 'No se pudo hablar con el servidor. Vuelve a intentarlo.';

        // Y si lo apagaron mientras estaba abierto, se pasa al panel de apagado
        // en vez de dejar la caja de texto viva.
        if (err?.status === 503) { this.estado = 'apagado'; }

        this.mensajes.push({
          tipo: 'ia',
          respuesta: {
            pregunta: limpio, sql: null, explicacion: null, resultados: null,
            totalResultados: null, error: mensaje, timestamp: new Date().toISOString()
          }
        });
        this.cargando = false;
        this.hayQueBajar = true;
        this.guardar();
      }
    });
  }

  // ---------------------------------------------------------------------
  // Formato de la tabla de resultados. Vive aquí porque lo usan las dos
  // vistas y es exactamente el mismo criterio en ambas.
  // ---------------------------------------------------------------------

  columnas(resultados: Array<{ [key: string]: any }>): string[] {
    if (!resultados || resultados.length === 0) { return []; }
    return Object.keys(resultados[0]);
  }

  /** Una columna es numérica si TODOS sus valores lo son; con uno solo no basta. */
  esNumerica(resultados: Array<{ [key: string]: any }>, col: string): boolean {
    return resultados.every(f => f[col] === null || typeof f[col] === 'number');
  }

  /** `total_vendido` se lee peor que `Total vendido`. */
  titulo(col: string): string {
    const limpio = col.replace(/_/g, ' ').trim();
    return limpio.charAt(0).toUpperCase() + limpio.slice(1);
  }

  celda(v: any): string {
    if (v === null || v === undefined) { return '—'; }
    if (typeof v === 'number') { return new Intl.NumberFormat('es-EC').format(v); }
    return String(v);
  }

  copiar(msg: ChatMessage, sql: string): void {
    navigator.clipboard?.writeText(sql).then(() => {
      msg.copiado = true;
      setTimeout(() => { msg.copiado = false; }, 2000);
    }).catch(() => { /* sin portapapeles: el SQL sigue a la vista para copiarlo a mano */ });
  }
}
