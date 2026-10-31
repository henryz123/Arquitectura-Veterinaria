import { NgIf } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { AccessBrandPanelComponent } from '../access/access-brand-panel.component';
import { resolveAccessTenant } from '../access/access-tenant';

@Component({
  selector: 'app-login-page',
  imports: [NgIf, ReactiveFormsModule, RouterLink, AccessBrandPanelComponent],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStateService);

  readonly tenantSlug = toSignal(
    this.route.paramMap.pipe(map((params) => params.get('tenantSlug') ?? 'veterinaria-aurora')),
    { initialValue: 'veterinaria-aurora' },
  );
  readonly accountType = toSignal(
    this.route.data.pipe(map((data) => data['accountType'] === 'CLIENT' ? 'CLIENT' : 'STAFF')),
    { initialValue: 'STAFF' },
  );
  readonly sessionExpired = toSignal(
    this.route.queryParamMap.pipe(map((params) => params.get('reason') === 'session-expired')),
    { initialValue: false },
  );
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly tenant = computed(() => resolveAccessTenant(this.tenantSlug()));
  readonly isClient = computed(() => this.accountType() === 'CLIENT');
  readonly showPassword = signal(false);
  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(12)]],
  });
  private currentFormTenant = this.tenantSlug();
  private readonly clearCredentialsWhenTenantChanges = effect(() => {
    const requestedTenant = this.tenantSlug();
    if (requestedTenant !== this.currentFormTenant) {
      this.form.reset();
      this.error.set(null);
      this.showPassword.set(false);
      this.currentFormTenant = requestedTenant;
    }
  });

  togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    const login = this.isClient()
      ? this.auth.loginClient(this.tenantSlug(), this.form.getRawValue())
      : this.auth.loginStaff(this.tenantSlug(), this.form.getRawValue());
    login.subscribe({
      next: () => {
        const destination = this.isClient() ? '/client-space' : '/workspace';
        void this.router.navigateByUrl(destination)
          .then((navigated) => {
            if (!navigated) {
              this.error.set('El acceso fue validado, pero no se pudo abrir tu espacio. Inténtalo nuevamente.');
            }
          })
          .catch(() => this.error.set('El acceso fue validado, pero no se pudo abrir tu espacio. Inténtalo nuevamente.'))
          .finally(() => this.submitting.set(false));
      },
      error: (error: unknown) => {
        this.error.set('No fue posible iniciar sesión. Verifica tus datos e inténtalo nuevamente.');
        if (error instanceof HttpErrorResponse && [0, 502, 503, 504].includes(error.status)) {
          this.error.set('No fue posible conectar con el servidor. Comprueba que la aplicación local esté iniciada e inténtalo nuevamente.');
        }
        this.submitting.set(false);
      },
    });
  }
}
