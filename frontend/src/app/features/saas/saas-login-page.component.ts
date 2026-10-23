import { NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthStateService } from '../../core/auth/auth-state.service';

@Component({
  selector: 'app-saas-login-page',
  imports: [NgIf, ReactiveFormsModule, RouterLink],
  templateUrl: './saas-login-page.component.html',
  styleUrl: './saas-login-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaasLoginPageComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthStateService);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
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
    this.auth.loginSaas(this.form.getRawValue()).pipe(
      finalize(() => this.submitting.set(false)),
    ).subscribe({
      next: () => void this.router.navigateByUrl('/saas/console'),
      error: () => this.error.set('No fue posible iniciar la sesión SaaS. Verifica los datos e inténtalo nuevamente.'),
    });
  }
}
