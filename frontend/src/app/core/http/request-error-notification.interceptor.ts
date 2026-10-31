import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from '../notifications/notification.service';

export const requestErrorNotificationInterceptor: HttpInterceptorFn = (request, next) => {
  const notifications = inject(NotificationService);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        notifications.error(errorMessage(request, error));
      }
      return throwError(() => error);
    }),
  );
};

function errorMessage(request: HttpRequest<unknown>, error: HttpErrorResponse): string {
  if (error.status === 0 || [502, 503, 504].includes(error.status)) {
    return 'No fue posible conectar con el servidor. Comprueba que la aplicación local esté iniciada e inténtalo nuevamente.';
  }
  if (error.status === 401) {
    if (isSessionCreationRequest(request.url)) {
      return 'No fue posible iniciar sesión. Verifica tus datos e inténtalo nuevamente.';
    }
    return 'Tu sesión ya no es válida. Inicia sesión nuevamente para continuar.';
  }
  if (error.status === 403) {
    return 'No tienes permisos para realizar esta operación.';
  }
  if (error.status === 404) {
    return 'La información solicitada ya no está disponible. Actualiza la pantalla e inténtalo nuevamente.';
  }
  if (error.status === 409) {
    return 'La operación entró en conflicto con un cambio reciente. Actualiza la información e inténtalo nuevamente.';
  }
  if (error.status >= 500) {
    return 'El servidor no pudo completar la operación. Conserva los datos ingresados e inténtalo nuevamente.';
  }
  return 'Los datos no pudieron guardarse. Revisa los campos e inténtalo nuevamente.';
}

function isSessionCreationRequest(url: string): boolean {
  return /^\/api\/v1\/auth\/(?:tenants\/[^/]+\/(?:staff|client)-sessions|saas-sessions)$/.test(url);
}
