import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { AdministrationApiService } from '../../core/api/administration-api.service';
import { AdministrativeRecovery, Organization, RucReview } from '../../core/api/api.models';
import { AuthStateService } from '../../core/auth/auth-state.service';

type OrganizationAction = 'SUSPENDER' | 'REACTIVAR';

@Component({
  selector: 'app-saas-console-page',
  imports: [NgFor, NgIf, ReactiveFormsModule],
  templateUrl: './saas-console-page.component.html',
  styleUrl: './saas-console-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaasConsolePageComponent implements OnInit {
  private readonly auth = inject(AuthStateService);
  private readonly administrationApi = inject(AdministrationApiService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly context = this.auth.context;
  readonly isSaasAdministrator = computed(() => {
    const context = this.context();
    return context?.accountType === 'SAAS' && context.roles.includes('ADMIN_SAAS');
  });
  readonly organizations = signal<Organization[]>([]);
  readonly rucReviews = signal<RucReview[]>([]);
  readonly recoveries = signal<AdministrativeRecovery[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly actionPending = signal(false);
  readonly selectedOrganization = signal<Organization | null>(null);
  readonly organizationAction = signal<OrganizationAction>('SUSPENDER');
  readonly selectedRucReview = signal<RucReview | null>(null);
  readonly selectedRecovery = signal<AdministrativeRecovery | null>(null);
  readonly organizationForm = this.formBuilder.nonNullable.group({
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    confirmed: [false, Validators.requiredTrue],
  });
  readonly rucResolutionForm = this.formBuilder.nonNullable.group({
    status: ['ACREDITADA', Validators.required],
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    releaseRuc: false,
    confirmed: [false, Validators.requiredTrue],
  });
  readonly recoveryReviewForm = this.formBuilder.nonNullable.group({
    status: ['VERIFICANDO', Validators.required],
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    legitimacyChecked: false,
  });

  ngOnInit(): void {
    if (!this.isSaasAdministrator()) {
      void this.router.navigateByUrl('/saas/login');
      return;
    }
    this.reload();
  }

  openOrganizationAction(organization: Organization): void {
    this.selectedOrganization.set(organization);
    this.organizationAction.set(organization.status === 'ACTIVO' ? 'SUSPENDER' : 'REACTIVAR');
    this.organizationForm.reset({ reason: '', confirmed: false });
  }

  cancelOrganizationAction(): void {
    this.selectedOrganization.set(null);
  }

  submitOrganizationAction(): void {
    const organization = this.selectedOrganization();
    if (!organization || this.organizationForm.invalid || this.actionPending()) {
      this.organizationForm.markAllAsTouched();
      return;
    }
    const { reason } = this.organizationForm.getRawValue();
    const action = this.organizationAction();
    this.performAction(
      action === 'SUSPENDER'
        ? this.administrationApi.suspendOrganization(organization.id, reason)
        : this.administrationApi.reactivateOrganization(organization.id, reason),
      `La organización fue ${action === 'SUSPENDER' ? 'suspendida' : 'reactivada'} por el servidor.`,
      () => this.selectedOrganization.set(null),
    );
  }

  openRucReview(review: RucReview): void {
    this.selectedRucReview.set(review);
    this.rucResolutionForm.reset({ status: 'ACREDITADA', reason: '', releaseRuc: false, confirmed: false });
  }

  cancelRucReview(): void {
    this.selectedRucReview.set(null);
  }

  submitRucReview(): void {
    const review = this.selectedRucReview();
    if (!review || this.rucResolutionForm.invalid || this.actionPending()) {
      this.rucResolutionForm.markAllAsTouched();
      return;
    }
    const { status, reason, releaseRuc } = this.rucResolutionForm.getRawValue();
    this.performAction(
      this.administrationApi.resolveRucReview(review.id, status, reason, releaseRuc),
      'La revisión de RUC fue registrada por el servidor.',
      () => this.selectedRucReview.set(null),
    );
  }

  openRecoveryReview(recovery: AdministrativeRecovery): void {
    this.selectedRecovery.set(recovery);
    this.recoveryReviewForm.reset({ status: 'VERIFICANDO', reason: '', legitimacyChecked: false });
  }

  cancelRecoveryReview(): void {
    this.selectedRecovery.set(null);
  }

  submitRecoveryReview(): void {
    const recovery = this.selectedRecovery();
    const { status, reason, legitimacyChecked } = this.recoveryReviewForm.getRawValue();
    if (!recovery || this.recoveryReviewForm.invalid || (status === 'APROBADA' && !legitimacyChecked) || this.actionPending()) {
      this.recoveryReviewForm.markAllAsTouched();
      return;
    }
    this.performAction(
      this.administrationApi.reviewAdministrativeRecovery(recovery.id, status, reason, legitimacyChecked),
      'La revisión de recuperación fue registrada por el servidor.',
      () => this.selectedRecovery.set(null),
    );
  }

  statusLabel(status: string): string {
    return status.replaceAll('_', ' ').toLowerCase().replace(/(^|\s)\S/g, (letter) => letter.toUpperCase());
  }

  trackById(_: number, item: { id: string }): string {
    return item.id;
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/saas/login'),
      error: () => void this.router.navigateByUrl('/saas/login'),
    });
  }

  private reload(): void {
    this.loading.set(true);
    forkJoin({
      organizations: this.administrationApi.listOrganizations(),
      rucReviews: this.administrationApi.listRucReviews(),
      recoveries: this.administrationApi.listAdministrativeRecoveries(),
    }).subscribe({
      next: (result) => {
        this.organizations.set(result.organizations.data);
        this.rucReviews.set(result.rucReviews.data);
        this.recoveries.set(result.recoveries.data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No fue posible cargar la administración SaaS autorizada. Actualiza la página e inténtalo nuevamente.');
        this.loading.set(false);
      },
    });
  }

  private performAction(request: ReturnType<AdministrationApiService['suspendOrganization']>, notice: string, onSuccess: () => void): void {
    this.error.set(null);
    this.notice.set(null);
    this.actionPending.set(true);
    request.pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: () => {
        onSuccess();
        this.notice.set(notice);
        this.reload();
      },
      error: () => this.error.set('La operación fue rechazada o no se pudo completar. Revisa la confirmación y el estado actual.'),
    });
  }
}
