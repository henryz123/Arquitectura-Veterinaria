import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';

export const APP_LOCALE = 'es-PE';

export function registerAppLocale(): void {
  registerLocaleData(localeEsPe);
}
