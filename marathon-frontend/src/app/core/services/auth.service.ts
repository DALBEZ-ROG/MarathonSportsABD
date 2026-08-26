import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
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
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient, private router: Router) {}

  login(correo: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/login`, { correo, password })
      .pipe(
        tap(response => {
          localStorage.setItem('marathon_token', response.token);
          localStorage.setItem('marathon_refresh_token', response.refreshToken);
          localStorage.setItem('marathon_user', JSON.stringify({
            idUsuario: response.idUsuario,
            nombre: response.nombre,
            apellido: response.apellido,
            correo: response.correo,
            rol: response.rol,
            permisos: response.permisos
          }));
        })
      );
  }

  logout(): void {
    localStorage.removeItem('marathon_token');
    localStorage.removeItem('marathon_refresh_token');
    localStorage.removeItem('marathon_user');
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem('marathon_token');
  }

  getCurrentUser(): any {
    const user = localStorage.getItem('marathon_user');
    return user ? JSON.parse(user) : null;
  }

  isAuthenticated(): boolean {
    const token = this.getToken();
    if (!token) return false;

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expiry = payload.exp * 1000;
      return Date.now() < expiry;
    } catch {
      return false;
    }
  }

  hasRol(nombreRol: string): boolean {
    const user = this.getCurrentUser();
    if (!user) return false;
    return user.rol === nombreRol;
  }

  refreshToken(): Observable<LoginResponse> {
    const refreshToken = localStorage.getItem('marathon_refresh_token');
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/refresh`, { refreshToken })
      .pipe(
        tap(response => {
          localStorage.setItem('marathon_token', response.token);
          localStorage.setItem('marathon_refresh_token', response.refreshToken);
          localStorage.setItem('marathon_user', JSON.stringify({
            idUsuario: response.idUsuario,
            nombre: response.nombre,
            apellido: response.apellido,
            correo: response.correo,
            rol: response.rol,
            permisos: response.permisos
          }));
        })
      );
  }
}
