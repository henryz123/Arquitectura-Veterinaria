import { Routes } from '@angular/router';
import { saasSessionGuard, sessionGuard } from './core/auth/session.guard';
import { WorkspacePageComponent } from './features/workspace/workspace-page.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { SaasLoginPageComponent } from './features/saas/saas-login-page.component';
import { SaasConsolePageComponent } from './features/saas/saas-console-page.component';

export const routes: Routes = [
  {
    path: 'login/:tenantSlug',
    component: LoginPageComponent,
    title: 'Acceso | Arquitectura Veterinaria',
  },
  {
    path: 'workspace',
    component: WorkspacePageComponent,
    canActivate: [sessionGuard],
    title: 'Espacio de trabajo | Arquitectura Veterinaria',
  },
  {
    path: 'saas/login',
    component: SaasLoginPageComponent,
    title: 'Administración SaaS | Arquitectura Veterinaria',
  },
  {
    path: 'saas/console',
    component: SaasConsolePageComponent,
    canActivate: [saasSessionGuard],
    title: 'Consola SaaS | Arquitectura Veterinaria',
  },
  { path: '', pathMatch: 'full', redirectTo: 'login/veterinaria-aurora' },
  { path: '**', redirectTo: '' },
];
