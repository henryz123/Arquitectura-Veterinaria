import { NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AdministrationApiService } from '../../core/api/administration-api.service';
import { AccessBrandPanelComponent } from '../access/access-brand-panel.component';
import { resolveAccessTenant } from '../access/access-tenant';

type ClientAccessMode = 'REGISTER' | 'VERIFY' | 'RECOVER' | 'RESET';

const passwordPolicy = [
  Validators.required,
  Validators.minLength(12),
  Validators.maxLength(72),
  Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).+$/),
];

function matchingPasswords(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirmation = control.get('passwordConfirmation')?.value;
  return password && confirmation && password !== confirmation ? { passwordMismatch: true } : null;
}

@Component({
  selector: 'app-client-access-page',
  imports: [NgIf, ReactiveFormsModule, RouterLink, AccessBrandPanelComponent],
  templateUrl: './client-access-page.component.html',
  styleUrl: './client-access-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientAccessPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly administrationApi = inject(AdministrationApiService);

  readonly mode = toSignal(
    this.route.data.pipe(map((data) => (data['clientAccessMode'] ?? 'REGISTER') as ClientAccessMode)),
    { initialValue: 'REGISTER' as ClientAccessMode },
  );
  readonly tenantSlug = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('tenantSlug') ?? 'veterinaria-aurora')),
    { initialValue: 'veterinaria-aurora' },
  );
  readonly tokenFromLink = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('token') ?? '')),
    { initialValue: '' },
  );
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);
  readonly tenant = computed(() => resolveAccessTenant(this.tenantSlug()));
  readonly showRegistrationPassword = signal(false);
  readonly showResetPassword = signal(false);
  readonly pageTitle = computed(() => ({
    REGISTER: 'Crear tu cuenta de cliente',
    VERIFY: 'Verifica tu correo',
    RECOVER: 'Recupera tu acceso',
    RESET: 'Define una nueva contraseña',
  })[this.mode()]);
  readonly registrationForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', passwordPolicy],
    passwordConfirmation: ['', [Validators.required]],
  }, { validators: matchingPasswords });
  readonly emailForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });
  readonly tokenForm = this.formBuilder.nonNullable.group({
    token: ['', [Validators.required]],
  });
  readonly resetForm = this.formBuilder.nonNullable.group({
    token: ['', [Validators.required]],
    password: ['', passwordPolicy],
    passwordConfirmation: ['', [Validators.required]],
  }, { validators: matchingPasswords });

  private readonly synchronizeOpaqueToken = effect(() => {
    const token = this.tokenFromLink();
    if (token && !this.tokenForm.controls.token.dirty) {
      this.tokenForm.controls.token.setValue(token);
    }
    if (token && !this.resetForm.controls.token.dirty) {
      this.resetForm.controls.token.setValue(token);
    }
  });

  toggleRegistrationPassword(): void {
    this.showRegistrationPassword.update((visible) => !visible);
  }

  toggleResetPassword(): void {
    this.showResetPassword.update((visible) => !visible);
  }

  register(): void {
    if (!this.ensureValid(this.registrationForm)) {
      return;
    }
    const value = this.registrationForm.getRawValue();
    this.begin();
    this.administrationApi.registerClient(this.tenantSlug(), value.email, value.password).subscribe({
      next: () => this.finish('Solicitud registrada. La cuenta quedará pendiente hasta usar un enlace de verificación válido.'),
      error: () => this.fail('No fue posible registrar la cuenta para esta veterinaria. Revisa los datos o usa otro correo.'),
    });
  }

  verify(): void {
    if (!this.ensureValid(this.tokenForm)) {
      return;
    }
    this.begin();
    this.administrationApi.verifyEmail(this.tokenForm.controls.token.value).subscribe({
      next: (response) => {
        if (response.data.accountType !== 'CLIENT') {
          this.fail('El enlace no corresponde a una cuenta de cliente.');
          return;
        }
        this.finish('Correo verificado. Ya puedes iniciar sesión en la veterinaria correspondiente.');
      },
      error: () => this.fail('El enlace es inválido, venció o ya fue utilizado. Solicita uno nuevo.'),
    });
  }

  resendVerification(): void {
    if (!this.ensureValid(this.emailForm)) {
      return;
    }
    this.begin();
    this.administrationApi.resendClientVerification(this.tenantSlug(), this.emailForm.controls.email.value).subscribe({
      next: () => this.finish('Si la cuenta necesita verificación, se preparó un nuevo enlace de forma segura.'),
      error: () => this.fail('No fue posible procesar la solicitud. Inténtalo nuevamente.'),
    });
  }

  requestRecovery(): void {
    if (!this.ensureValid(this.emailForm)) {
      return;
    }
    this.begin();
    this.administrationApi.requestClientPasswordRecovery(this.tenantSlug(), this.emailForm.controls.email.value).subscribe({
      next: () => this.finish('Si existe una cuenta activa en esta veterinaria, se preparó un enlace de recuperación.'),
      error: () => this.fail('No fue posible procesar la solicitud. Inténtalo nuevamente.'),
    });
  }

  resetPassword(): void {
    if (!this.ensureValid(this.resetForm)) {
      return;
    }
    const value = this.resetForm.getRawValue();
    this.begin();
    this.administrationApi.resetPassword(value.token, value.password).subscribe({
      next: () => this.finish('Contraseña actualizada. El enlace ya no puede volver a utilizarse.'),
      error: () => this.fail('El enlace es inválido, venció o ya fue utilizado. Solicita una nueva recuperación.'),
    });
  }

  private ensureValid(form: AbstractControl): boolean {
    if (form.valid || this.submitting()) {
      return form.valid;
    }
    form.markAllAsTouched();
    this.error.set('Revisa los campos marcados antes de continuar.');
    return false;
  }

  private begin(): void {
    this.error.set(null);
    this.success.set(null);
    this.submitting.set(true);
  }

  private finish(message: string): void {
    this.success.set(message);
    this.submitting.set(false);
  }

  private fail(message: string): void {
    this.error.set(message);
    this.submitting.set(false);
  }
}
