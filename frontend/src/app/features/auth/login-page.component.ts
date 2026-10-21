import { NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { map } from 'rxjs';
import { AuthStateService } from '../../core/auth/auth-state.service';

@Component({
  selector: 'app-login-page',
  imports: [NgIf, ReactiveFormsModule, RouterLink],
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
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly tenantLabel = computed(() => this.tenantSlug().replaceAll('-', ' '));
  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(12)]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    this.error.set(null);
    this.submitting.set(true);
    this.auth.loginStaff(this.tenantSlug(), this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/workspace'),
      error: () => {
        this.error.set('No fue posible iniciar sesión. Verifica tus datos e inténtalo nuevamente.');
        this.submitting.set(false);
      },
    });
  }
}
