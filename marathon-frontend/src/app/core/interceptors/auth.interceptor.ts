import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

let isRefreshing = false;

/**
 * Manda la sesión en cada llamada (F60, D-27).
 *
 * Ya no hay ninguna cabecera `Authorization` que poner: el token vive en una
 * cookie `HttpOnly` que este código no puede leer. Lo único que hace falta es
 * pedirle al navegador que adjunte las cookies, y eso es `withCredentials`.
 *
 * **Sin `withCredentials` no viaja nada.** La API está en `localhost:8080` y el
 * front en `localhost:4300`: para el navegador son orígenes distintos, y a otro
 * origen no se le mandan cookies salvo que se pida expresamente. Si algún día
 * las llamadas empiezan a devolver 401 sin motivo, mirar aquí primero.
 */
export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
  const authService = inject(AuthService);

  const conCookies = req.clone({ withCredentials: true });

  // El login y el refresco no se reintentan: un 401 ahí significa que las
  // credenciales no valen, no que la sesión haya caducado.
  if (req.url.includes('/api/auth/login') || req.url.includes('/api/auth/refresh')) {
    return next(conCookies);
  }

  return next(conCookies).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !isRefreshing) {
        isRefreshing = true;

        return authService.refreshToken().pipe(
          switchMap(() => {
            isRefreshing = false;
            // No se reenvía ningún token: el servidor acaba de reemplazar la
            // cookie, así que basta con repetir la llamada.
            return next(req.clone({ withCredentials: true }));
          }),
          catchError(refreshError => {
            isRefreshing = false;
            authService.limpiarYSalir();
            return throwError(() => refreshError);
          })
        );
      }

      return throwError(() => error);
    })
  );
};
