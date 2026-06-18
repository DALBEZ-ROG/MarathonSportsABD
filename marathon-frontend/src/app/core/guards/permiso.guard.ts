import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const permisoGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const modulo = route.data?.['modulo'] as string;
  const accion = route.data?.['accion'] as string;

  if (!modulo || !accion) return true;

  if (authService.hasPermiso(modulo, accion)) {
    return true;
  }

  router.navigate(['/dashboard']);
  return false;
};
