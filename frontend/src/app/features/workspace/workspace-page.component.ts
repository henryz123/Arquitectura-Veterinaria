import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, of } from 'rxjs';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { AdministrationApiService } from '../../core/api/administration-api.service';
import { SessionContext, StaffAccount, StaffRole, TenantProfile } from '../../core/api/api.models';

const STAFF_ROLE_OPTIONS: ReadonlyArray<{ code: StaffRole; label: string }> = [
  { code: 'ADMIN_VETERINARIA', label: 'Administración de veterinaria' },
  { code: 'VETERINARIO', label: 'Atención veterinaria' },
  { code: 'RECEPCION', label: 'Recepción' },
  { code: 'CAJERO', label: 'Caja' },
  { code: 'GROOMING', label: 'Grooming' },
];

type TenantScopedSession = SessionContext & { tenant: TenantProfile };

@Component({
  selector: 'app-workspace-page',
  imports: [NgFor, NgIf, ReactiveFormsModule, RouterLink],
  templateUrl: './workspace-page.component.html',
  styleUrl: './workspace-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspacePageComponent implements OnInit {
  private readonly auth = inject(AuthStateService);
  private readonly administrationApi = inject(AdministrationApiService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  readonly profile = signal<TenantProfile | null>(null);
  readonly staff = signal<StaffAccount[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly actionPending = signal(false);
  readonly inviteSubmitted = signal(false);
  readonly rolesSubmitted = signal(false);
  readonly selectedStaff = signal<StaffAccount | null>(null);
  readonly pendingDeactivation = signal<StaffAccount | null>(null);
  readonly roleOptions = STAFF_ROLE_OPTIONS;
  readonly context = this.auth.context;
  readonly tenantSession = computed<TenantScopedSession | null>(() => {
    const context = this.context();
    return context?.tenant ? { ...context, tenant: context.tenant } : null;
  });
  readonly tenantCommercialName = computed(() => this.tenantSession()?.tenant.commercialName ?? '');
  readonly tenantStatus = computed(() => this.tenantSession()?.tenant.status ?? 'SUSPENDIDO');
  readonly canManageStaff = computed(() => {
    const context = this.context();
    return context?.accountType === 'STAFF' && context.roles.includes('ADMIN_VETERINARIA');
  });
  readonly activeStaffCount = computed(() => this.staff().filter((member) => member.status === 'ACTIVA').length);
  readonly invitationForm = this.formBuilder.nonNullable.group({
    email: ['', { validators: [Validators.required, Validators.email], updateOn: 'blur' }],
    roles: this.formBuilder.nonNullable.control<StaffRole[]>(['RECEPCION']),
  });
  readonly rolesForm = this.formBuilder.nonNullable.group({
    roles: this.formBuilder.nonNullable.control<StaffRole[]>(['RECEPCION']),
  });

  ngOnInit(): void {
    const context = this.context();
    if (!context || !context.tenant) {
      void this.router.navigateByUrl('/login/veterinaria-aurora');
      return;
    }
    forkJoin({
      profile: this.auth.loadOwnTenantProfile(context.tenant.slug),
      staff: this.canManageStaff() ? this.administrationApi.listStaff() : of(null),
    }).subscribe({
      next: ({ profile, staff }) => {
        this.profile.set(profile.data);
        this.staff.set(staff?.data ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No fue posible cargar el espacio autorizado. Actualiza la página e inténtalo nuevamente.');
        this.loading.set(false);
      },
    });
  }

  inviteStaff(): void {
    this.inviteSubmitted.set(true);
    if (this.invitationForm.invalid || this.actionPending()) {
      this.invitationForm.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.notice.set(null);
    this.actionPending.set(true);
    const { email, roles } = this.invitationForm.getRawValue();
    this.administrationApi.inviteStaff(email, roles).pipe(
      finalize(() => this.actionPending.set(false)),
    ).subscribe({
      next: () => {
        this.invitationForm.reset({ email: '', roles: ['RECEPCION'] });
        this.inviteSubmitted.set(false);
        this.notice.set('La invitación fue registrada. La cuenta conservará el estado pendiente hasta aceptar su enlace.');
        this.reloadStaff();
      },
      error: () => this.error.set('No fue posible registrar la invitación. Revisa los datos e inténtalo nuevamente.'),
    });
  }

  editRoles(member: StaffAccount): void {
    this.rolesSubmitted.set(false);
    this.selectedStaff.set(member);
    this.rolesForm.setValue({ roles: [...member.roles] });
  }

  cancelRoleEdit(): void {
    this.selectedStaff.set(null);
    this.rolesSubmitted.set(false);
  }

  saveRoles(): void {
    const member = this.selectedStaff();
    this.rolesSubmitted.set(true);
    if (!member || this.rolesForm.controls.roles.value.length === 0 || this.actionPending()) {
      this.rolesForm.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.notice.set(null);
    this.actionPending.set(true);
    this.administrationApi.replaceStaffRoles(member.principalId, this.rolesForm.controls.roles.value).pipe(
      finalize(() => this.actionPending.set(false)),
    ).subscribe({
      next: (response) => {
        this.staff.update((members) => members.map((current) => current.principalId === member.principalId ? response.data : current));
        this.selectedStaff.set(null);
        this.rolesSubmitted.set(false);
        this.notice.set('Los roles de la cuenta se actualizaron. Las sesiones afectadas fueron gestionadas por el servidor.');
      },
      error: () => this.error.set('No fue posible actualizar los roles. La protección del último administrador puede haber rechazado la operación.'),
    });
  }

  requestDeactivation(member: StaffAccount): void {
    this.pendingDeactivation.set(member);
  }

  cancelDeactivation(): void {
    this.pendingDeactivation.set(null);
  }

  confirmDeactivation(): void {
    const member = this.pendingDeactivation();
    if (!member || this.actionPending()) {
      return;
    }
    this.error.set(null);
    this.notice.set(null);
    this.actionPending.set(true);
    this.administrationApi.deactivateStaff(member.principalId).pipe(
      finalize(() => this.actionPending.set(false)),
    ).subscribe({
      next: () => {
        this.pendingDeactivation.set(null);
        this.notice.set('La cuenta fue desactivada y sus accesos afectados se revocaron en el servidor.');
        this.reloadStaff();
      },
      error: () => this.error.set('No fue posible desactivar la cuenta. El servidor conserva la protección del último administrador.'),
    });
  }

  roleLabel(role: StaffRole): string {
    return STAFF_ROLE_OPTIONS.find((option) => option.code === role)?.label ?? role;
  }

  statusLabel(status: StaffAccount['status'] | TenantProfile['status']): string {
    return status === 'ACTIVA' || status === 'ACTIVO' ? 'Activo' : status === 'INVITADA' ? 'Invitada' : 'Desactivado';
  }

  trackStaff(_: number, member: StaffAccount): string {
    return member.principalId;
  }

  private reloadStaff(): void {
    this.administrationApi.listStaff().subscribe({
      next: (response) => this.staff.set(response.data),
      error: () => this.error.set('La acción se realizó, pero no fue posible actualizar la lista. Recarga el espacio para confirmar el estado.'),
    });
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => void this.router.navigateByUrl('/login/veterinaria-aurora'),
      error: () => void this.router.navigateByUrl('/login/veterinaria-aurora'),
    });
  }
}
