import { Routes } from '@angular/router';
import { sessionGuard } from './core/auth/session.guard';
import { WorkspacePageComponent } from './features/workspace/workspace-page.component';
import { LoginPageComponent } from './features/auth/login-page.component';

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
  { path: '', pathMatch: 'full', redirectTo: 'login/veterinaria-aurora' },
  { path: '**', redirectTo: '' },
];
