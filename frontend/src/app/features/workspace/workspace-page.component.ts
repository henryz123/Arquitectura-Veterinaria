import { NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { TenantProfile } from '../../core/api/api.models';

@Component({
  selector: 'app-workspace-page',
  imports: [NgIf],
  templateUrl: './workspace-page.component.html',
  styleUrl: './workspace-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspacePageComponent implements OnInit {
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);
  readonly profile = signal<TenantProfile | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    const context = this.auth.context();
    if (!context) {
      void this.router.navigateByUrl('/login/veterinaria-aurora');
      return;
    }
    this.auth.loadOwnTenantProfile(context.tenant.slug).subscribe({
      next: (response) => {
        this.profile.set(response.data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No fue posible cargar el perfil autorizado de la veterinaria.');
        this.loading.set(false);
      },
    });
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login/veterinaria-aurora'),
      error: () => void this.router.navigateByUrl('/login/veterinaria-aurora'),
    });
  }
}
