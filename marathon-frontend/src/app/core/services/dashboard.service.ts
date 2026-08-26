import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/**
 * El contrato de `GET /api/dashboard/resumen` (D1/D2).
 *
 * Copia exacta de `IndicadorDTO` en el backend. La regla de este módulo es que
 * el navegador **no calcula nada**: recibe el valor ya hecho, su período, su
 * base de cálculo y un `estado` que le dice qué plantilla pintar.
 */
export type EstadoIndicador = 'ok' | 'vacio' | 'sin_dato' | 'parcial' | 'error';

export interface Comparacion {
  valor: number;
  etiqueta: string;
  /** `null` cuando el período anterior fue cero: no es "infinito por ciento". */
  variacion: number | null;
}

export interface Indicador {
  clave: string;
  titulo: string;
  unidad: string;
  valor: number | null;
  /** Sobre cuántos. `null` cuando el indicador no lleva denominador. */
  denominador: number | null;
  periodo: string;
  base: string;
  comparacion: Comparacion | null;
  estado: EstadoIndicador;
  /** El motivo, cuando el estado no es `ok`. Se pinta en lugar del valor. */
  nota: string | null;
  enlace: string | null;
}

export interface TopProducto {
  nombre: string;
  unidades: number;
}

/**
 * Un día de la serie del gráfico. Llega completa: un día sin pedidos viaja con
 * ceros en vez de faltar, porque un hueco en el eje se lee como "no se midió".
 */
export interface SerieDia {
  /** `yyyy-MM-dd`, tal cual. No se convierte a `Date`: desplazaría el día. */
  dia: string;
  pedidos: number;
  importe: number;
}

export interface DashboardResumen {
  rol: string;
  titulo: string;
  periodo: string;
  periodoEtiqueta: string;
  desde: string;
  hasta: string;
  generadoEn: string;
  indicadores: Indicador[];
  topProductos: TopProducto[];
  serie: SerieDia[];
}

export type ClavePeriodo = '7d' | '30d' | '90d';

@Injectable({ providedIn: 'root' })
export class DashboardService {

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /**
   * El tablero del rol que viene en el token. No se le pasa el rol a propósito:
   * si viajara en la petición, cualquiera pediría el de otro cambiando la URL.
   */
  getResumen(periodo: ClavePeriodo): Observable<DashboardResumen> {
    return this.http.get<DashboardResumen>(
      `${this.apiUrl}/dashboard/resumen`, { params: { periodo } });
  }
}
