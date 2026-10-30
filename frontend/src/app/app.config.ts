import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { APP_LOCALE } from './app.locale';
import { csrfSessionInterceptor } from './core/http/csrf-session.interceptor';
import { requestErrorNotificationInterceptor } from './core/http/request-error-notification.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([csrfSessionInterceptor, requestErrorNotificationInterceptor])),
    { provide: LOCALE_ID, useValue: APP_LOCALE },
  ],
};
