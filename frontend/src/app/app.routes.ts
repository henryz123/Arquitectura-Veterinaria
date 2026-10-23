import { Routes } from '@angular/router';
import { clientSessionGuard, saasSessionGuard, sessionGuard } from './core/auth/session.guard';
import { WorkspacePageComponent } from './features/workspace/workspace-page.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { SaasLoginPageComponent } from './features/saas/saas-login-page.component';
import { SaasConsolePageComponent } from './features/saas/saas-console-page.component';
import { ClientSpacePageComponent } from './features/client/client-space-page.component';

export const routes: Routes = [
  {
    path: 'login/:tenantSlug',
    component: LoginPageComponent,
    data: { accountType: 'STAFF' },
    title: 'Acceso | Arquitectura Veterinaria',
  },
  {
    path: 'client/login/:tenantSlug',
    component: LoginPageComponent,
    data: { accountType: 'CLIENT' },
    title: 'Acceso cliente | Arquitectura Veterinaria',
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
  {
    path: 'client-space',
    component: ClientSpacePageComponent,
    canActivate: [clientSessionGuard],
    title: 'Espacio cliente | Arquitectura Veterinaria',
  },
  { path: '', pathMatch: 'full', redirectTo: 'login/veterinaria-aurora' },
  { path: '**', redirectTo: '' },
];
