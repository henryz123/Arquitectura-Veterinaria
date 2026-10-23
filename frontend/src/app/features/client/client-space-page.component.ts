import { NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AdministrationApiService } from '../../core/api/administration-api.service';
import { ClientSpace } from '../../core/api/api.models';
import { AuthStateService } from '../../core/auth/auth-state.service';

@Component({
  selector: 'app-client-space-page',
  imports: [NgIf, RouterLink],
  templateUrl: './client-space-page.component.html',
  styleUrl: './client-space-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientSpacePageComponent implements OnInit {
  private readonly administrationApi = inject(AdministrationApiService);
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);
  readonly space = signal<ClientSpace | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.administrationApi.clientSpace().subscribe({
      next: (response) => {
        this.space.set(response.data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No fue posible cargar el espacio de cliente autorizado. Inténtalo nuevamente.');
        this.loading.set(false);
      },
    });
  }

  logout(): void {
    const tenantSlug = this.auth.context()?.tenant?.slug ?? 'veterinaria-aurora';
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/client/login', tenantSlug]),
      error: () => void this.router.navigate(['/client/login', tenantSlug]),
    });
  }
}
