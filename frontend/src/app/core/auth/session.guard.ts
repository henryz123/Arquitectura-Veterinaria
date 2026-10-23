import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthStateService } from './auth-state.service';

export const sessionGuard: CanActivateFn = () => {
  const auth = inject(AuthStateService);
  const router = inject(Router);
  if (auth.context()) {
    return true;
  }
  return auth.restoreContext().pipe(
    map(() => true),
    catchError(() => {
      auth.clear();
      return of(router.createUrlTree(['/login/veterinaria-aurora']));
    }),
  );
};

export const saasSessionGuard: CanActivateFn = () => {
  const auth = inject(AuthStateService);
  const router = inject(Router);
  const isAuthorized = () => {
    const context = auth.context();
    return context?.accountType === 'SAAS' && context.roles.includes('ADMIN_SAAS');
  };
  if (auth.context()) {
    return isAuthorized() ? true : router.createUrlTree(['/saas/login']);
  }
  return auth.restoreContext().pipe(
    map(() => isAuthorized() ? true : router.createUrlTree(['/saas/login'])),
    catchError(() => {
      auth.clear();
      return of(router.createUrlTree(['/saas/login']));
    }),
  );
};
