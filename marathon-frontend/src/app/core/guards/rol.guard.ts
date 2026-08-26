import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Guard de autorización por rol.
 *
 * Acepta dos formas en `route.data`:
 *   - `rol: 'Administrador'`            → un solo rol
 *   - `roles: ['Administrador', '...']` → cualquiera de la lista
 *
 * F32 — Si el rol no tiene acceso, redirige a /inicio con el query param
 * `?acceso=denegado`, que el DashboardComponent usa para mostrar el mensaje
 * "No tienes acceso a esta sección". Antes redirigía en silencio y el usuario
 * no entendía por qué no pasaba nada.
 */
export const rolGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const rolEsperado = route.data?.['rol'] as string | undefined;
  const rolesEsperados = route.data?.['roles'] as string[] | undefined;

  const denegar = (): boolean => {
    router.navigate(['/inicio'], { queryParams: { acceso: 'denegado' } });
    return false;
  };

  if (rolesEsperados && rolesEsperados.length > 0) {
    return rolesEsperados.some(r => authService.hasRol(r)) ? true : denegar();
  }

  if (!rolEsperado) {
    return true;
  }

  return authService.hasRol(rolEsperado) ? true : denegar();
};
