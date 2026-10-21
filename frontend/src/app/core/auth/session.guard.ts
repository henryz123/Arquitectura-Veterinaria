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
