import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const rolGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const rolEsperado = route.data?.['rol'] as string;
  const rolesEsperados = route.data?.['roles'] as string[] | undefined;

  if (rolesEsperados && rolesEsperados.length > 0) {
    if (rolesEsperados.some(r => authService.hasRol(r))) {
      return true;
    }
    router.navigate(['/dashboard']);
    return false;
  }

  if (!rolEsperado) return true;

  if (authService.hasRol(rolEsperado)) {
    return true;
  }

  router.navigate(['/dashboard']);
  return false;
};
