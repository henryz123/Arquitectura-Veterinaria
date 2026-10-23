import { Routes } from '@angular/router';
import { clientSessionGuard, saasSessionGuard, sessionGuard } from './core/auth/session.guard';
import { WorkspacePageComponent } from './features/workspace/workspace-page.component';
import { LoginPageComponent } from './features/auth/login-page.component';
import { SaasLoginPageComponent } from './features/saas/saas-login-page.component';
import { SaasConsolePageComponent } from './features/saas/saas-console-page.component';
import { ClientSpacePageComponent } from './features/client/client-space-page.component';
import { ClientAccessPageComponent } from './features/client/client-access-page.component';

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
    path: 'client/register/:tenantSlug',
    component: ClientAccessPageComponent,
    data: { clientAccessMode: 'REGISTER' },
    title: 'Registro cliente | Arquitectura Veterinaria',
  },
  {
    path: 'client/verify-email',
    component: ClientAccessPageComponent,
    data: { clientAccessMode: 'VERIFY' },
    title: 'Verificar correo | Arquitectura Veterinaria',
  },
  {
    path: 'client/recover/:tenantSlug',
    component: ClientAccessPageComponent,
    data: { clientAccessMode: 'RECOVER' },
    title: 'Recuperar acceso | Arquitectura Veterinaria',
  },
  {
    path: 'client/reset-password',
    component: ClientAccessPageComponent,
    data: { clientAccessMode: 'RESET' },
    title: 'Nueva contraseña | Arquitectura Veterinaria',
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
