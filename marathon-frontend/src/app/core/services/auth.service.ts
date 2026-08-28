import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface LoginResponse {
  token: string;
  refreshToken: string;
  tipo: string;
  idUsuario: number;
  nombre: string;
  apellido: string;
  correo: string;
  rol: string;
  permisos: string[];
  expiraEn: number;
}

/**
 * La sesión del usuario (F60, cierra D-23 y D-27).
 *
 * **Aquí ya no hay ningún token.** Antes se guardaban el de acceso y el de
 * refresco en `localStorage`, que es un cajón que cualquier XSS puede abrir y
 * vaciar. Ahora los pone el servidor en dos cookies `HttpOnly` que este código
 * —y cualquier otro que corra en la página— **no puede leer**. El navegador las
 * adjunta él solo en cada llamada, y por eso todas van con `withCredentials`.
 *
 * Lo que sí se guarda aquí es lo que no es secreto y hace falta para pintar la
 * pantalla sin ir al servidor: nombre, rol, permisos y **cuándo caduca la
 * sesión**. Esa fecha viene ahora del servidor (`expiraEn`), porque el front ya
 * no puede sacarla descifrando el token, que es lo que hacía antes.
 *
 * `logout()` llama al servidor de verdad. Antes solo borraba `localStorage` y el
 * token seguía siendo válido hasta caducar: cerrar sesión no cerraba nada (D-23).
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private router: Router) {}

  login(correo: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/login`,
      { correo, password }, { withCredentials: true })
      .pipe(tap(response => this.guardarSesion(response)));
  }

  /**
   * Cierra la sesión en el servidor y luego limpia lo local.
   *
   * Se navega a /login pase lo que pase: si la llamada falla —sin red, servidor
   * caído—, dejar al usuario dentro de la aplicación sería peor. El token se
   * revoca en cuanto haya servidor; mientras tanto ya no hay nada aquí con lo
   * que seguir usándolo.
   */
  logout(): void {
    this.http.post(`${this.apiUrl}/auth/logout`, {}, { withCredentials: true })
      .pipe(catchError(() => of(null)))
      .subscribe(() => this.limpiarYSalir());
  }

  /** Salida inmediata, sin avisar al servidor. La usa el interceptor. */
  limpiarYSalir(): void {
    localStorage.removeItem('marathon_user');
    localStorage.removeItem('marathon_expira');
    this.router.navigate(['/login']);
  }

  getCurrentUser(): any {
    const user = localStorage.getItem('marathon_user');
    return user ? JSON.parse(user) : null;
  }

  isAuthenticated(): boolean {
    if (!this.getCurrentUser()) return false;

    const expira = localStorage.getItem('marathon_expira');
    if (!expira) return false;

    return Date.now() < Number(expira);
  }

  hasRol(nombreRol: string): boolean {
    const user = this.getCurrentUser();
    if (!user) return false;
    return user.rol === nombreRol;
  }

  /**
   * Renueva la sesión. El refresco viaja en su cookie, así que no hay nada que
   * mandar en el cuerpo — el servidor lo saca de ahí.
   */
  refreshToken(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/refresh`, {},
      { withCredentials: true })
      .pipe(tap(response => this.guardarSesion(response)));
  }

  private guardarSesion(response: LoginResponse): void {
    localStorage.setItem('marathon_user', JSON.stringify({
      idUsuario: response.idUsuario,
      nombre: response.nombre,
      apellido: response.apellido,
      correo: response.correo,
      rol: response.rol,
      permisos: response.permisos
    }));
    localStorage.setItem('marathon_expira', String(response.expiraEn));
  }
}
