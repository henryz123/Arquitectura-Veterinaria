import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { registerAppLocale } from './app/app.locale';
import { App } from './app/app';

registerAppLocale();
bootstrapApplication(App, appConfig).catch((err) => console.error(err));
