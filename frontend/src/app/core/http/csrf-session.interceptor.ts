import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthStateService } from '../auth/auth-state.service';

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export const csrfSessionInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthStateService);
  let headers = request.headers;
  const csrf = auth.csrfToken();
  if (!SAFE_METHODS.has(request.method) && csrf) {
    headers = headers.set('X-CSRF-Token', csrf);
  }
  return next(request.clone({ headers, withCredentials: true }));
};
