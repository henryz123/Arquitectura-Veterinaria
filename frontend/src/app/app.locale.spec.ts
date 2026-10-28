import { DatePipe } from '@angular/common';
import { APP_LOCALE, registerAppLocale } from './app.locale';

describe('app locale', () => {
  it('registers the Peruvian Spanish data required by DatePipe', () => {
    registerAppLocale();

    expect(() => new DatePipe(APP_LOCALE).transform('2031-01-20T18:00:00Z', 'medium', 'America/Lima', APP_LOCALE))
      .not.toThrow();
  });
});
