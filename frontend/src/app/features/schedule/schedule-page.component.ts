import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AssignableProfessional } from '../../core/api/api.models';
import { ScheduleApiService } from '../../core/api/schedule-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';

@Component({
  selector: 'app-schedule-page',
  imports: [NgFor, NgIf, RouterLink],
  templateUrl: './schedule-page.component.html',
  styleUrl: './schedule-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SchedulePageComponent implements OnInit {
  private readonly scheduleApi = inject(ScheduleApiService);
  private readonly auth = inject(AuthStateService);

  readonly context = this.auth.context;
  readonly professionals = signal<AssignableProfessional[]>([]);
  readonly selectedProfessionalId = signal<string>('');
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly canManageSchedule = computed(() => {
    const context = this.context();
    return context?.accountType === 'STAFF'
      && (context.roles.includes('ADMIN_VETERINARIA') || context.roles.includes('RECEPCION'));
  });
  readonly selectedProfessional = computed(() =>
    this.professionals().find((professional) => professional.principalId === this.selectedProfessionalId()) ?? null,
  );

  ngOnInit(): void {
    if (!this.canManageSchedule()) {
      this.loading.set(false);
      this.error.set('Tu sesión no está autorizada para gestionar la agenda. El servidor también rechaza esta consulta.');
      return;
    }
    this.loadProfessionals();
  }

  loadProfessionals(): void {
    if (!this.canManageSchedule()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.scheduleApi.listAssignableProfessionals().subscribe({
      next: (response) => {
        this.professionals.set(response.data);
        const current = this.selectedProfessionalId();
        if (!response.data.some((professional) => professional.principalId === current)) {
          this.selectedProfessionalId.set(response.data[0]?.principalId ?? '');
        }
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('No fue posible cargar los profesionales asignables. Actualiza la vista para revalidar sus permisos y estado.');
      },
    });
  }

  selectProfessional(principalId: string): void {
    this.selectedProfessionalId.set(principalId);
  }

  professionalLabel(professional: AssignableProfessional): string {
    const role = professional.roles.includes('VETERINARIO') && professional.roles.includes('GROOMING')
      ? 'Veterinaria y grooming'
      : professional.roles.includes('VETERINARIO') ? 'Atención veterinaria' : 'Grooming';
    return `${role} · ${professional.principalId.slice(0, 8)}`;
  }

  roleSummary(professional: AssignableProfessional): string {
    return professional.roles.map((role) => role === 'VETERINARIO' ? 'Veterinario' : 'Grooming').join(' y ');
  }

  trackProfessional(_: number, professional: AssignableProfessional): string {
    return professional.principalId;
  }
}
