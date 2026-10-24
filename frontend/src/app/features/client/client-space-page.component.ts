import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { AdministrationApiService } from '../../core/api/administration-api.service';
import { ClientOwnerLinkStatus, ClientPetProfile, ClientSpace } from '../../core/api/api.models';
import { AuthStateService } from '../../core/auth/auth-state.service';

@Component({
  selector: 'app-client-space-page',
  imports: [NgFor, NgIf, RouterLink],
  templateUrl: './client-space-page.component.html',
  styleUrl: './client-space-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientSpacePageComponent implements OnInit {
  private readonly administrationApi = inject(AdministrationApiService);
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);
  readonly space = signal<ClientSpace | null>(null);
  readonly linkStatus = signal<ClientOwnerLinkStatus | null>(null);
  readonly pets = signal<ClientPetProfile[]>([]);
  readonly loading = signal(true);
  readonly actionPending = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  ngOnInit(): void {
    forkJoin({
      space: this.administrationApi.clientSpace(),
      linkStatus: this.administrationApi.getClientOwnerLinkStatus(),
      pets: this.administrationApi.listClientPets(),
    }).subscribe({
      next: (response) => {
        this.space.set(response.space.data);
        this.linkStatus.set(response.linkStatus.data);
        this.pets.set(response.pets.data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No fue posible cargar tu espacio privado autorizado. Inténtalo nuevamente.');
        this.loading.set(false);
      },
    });
  }

  canRequestLink(): boolean {
    const status = this.linkStatus()?.status ?? 'SIN_SOLICITUD';
    return status === 'SIN_SOLICITUD' || status === 'RECHAZADA';
  }

  requestOwnerLink(): void {
    if (!this.canRequestLink() || this.actionPending()) {
      return;
    }
    this.error.set(null);
    this.notice.set(null);
    this.actionPending.set(true);
    this.administrationApi.requestClientOwnerLink().pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.linkStatus.set(response.data);
        this.notice.set('Tu solicitud fue enviada. El personal verificará manualmente la identidad y la relación antes de habilitar el acceso.');
      },
      error: () => this.error.set('No fue posible registrar la solicitud. No se reveló ningún registro privado.'),
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
