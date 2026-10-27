import { DecimalPipe, NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import {
  AssignableProfessional,
  OfferedService,
  OperatingHours,
  ProfessionalAvailability,
  ScheduleRequest,
} from '../../core/api/api.models';
import { ScheduleApiService } from '../../core/api/schedule-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';

@Component({
  selector: 'app-schedule-page',
  imports: [DecimalPipe, NgFor, NgIf, ReactiveFormsModule, RouterLink],
  templateUrl: './schedule-page.component.html',
  styleUrl: './schedule-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SchedulePageComponent implements OnInit {
  private readonly scheduleApi = inject(ScheduleApiService);
  private readonly auth = inject(AuthStateService);
  private readonly formBuilder = inject(FormBuilder);

  readonly context = this.auth.context;
  readonly professionals = signal<AssignableProfessional[]>([]);
  readonly services = signal<OfferedService[]>([]);
  readonly operatingHours = signal<OperatingHours[]>([]);
  readonly professionalAvailability = signal<ProfessionalAvailability[]>([]);
	readonly scheduleRequests = signal<ScheduleRequest[]>([]);
	readonly selectedProfessionalId = signal<string>('');
	readonly cancellationReason = signal<string>('');
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly weekdays = [
    { value: 1, label: 'Lunes' },
    { value: 2, label: 'Martes' },
    { value: 3, label: 'Miércoles' },
    { value: 4, label: 'Jueves' },
    { value: 5, label: 'Viernes' },
    { value: 6, label: 'Sábado' },
    { value: 7, label: 'Domingo' },
  ];
  readonly canManageSchedule = computed(() => {
    const context = this.context();
    return context?.accountType === 'STAFF'
      && (context.roles.includes('ADMIN_VETERINARIA') || context.roles.includes('RECEPCION'));
  });
  readonly selectedProfessional = computed(() =>
    this.professionals().find((professional) => professional.principalId === this.selectedProfessionalId()) ?? null,
  );

  readonly serviceForm = this.formBuilder.nonNullable.group({
    serviceType: ['CLINICA' as 'CLINICA' | 'GROOMING', Validators.required],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    publicDescription: ['', Validators.maxLength(1000)],
    durationMinutes: [30, [Validators.required, Validators.min(15), Validators.max(480)]],
    referencePrice: [0, [Validators.required, Validators.min(0)]],
    published: [true],
  });
  readonly operatingHoursForm = this.formBuilder.nonNullable.group({
    weekday: [1, [Validators.required, Validators.min(1), Validators.max(7)]],
    startsAtLocal: ['08:00', Validators.required],
    endsAtLocal: ['18:00', Validators.required],
  });
  readonly professionalAvailabilityForm = this.formBuilder.nonNullable.group({
    professionalPrincipalId: ['', Validators.required],
    weekday: [1, [Validators.required, Validators.min(1), Validators.max(7)]],
    startsAtLocal: ['08:00', Validators.required],
    endsAtLocal: ['18:00', Validators.required],
  });

  ngOnInit(): void {
    if (!this.canManageSchedule()) {
      this.loading.set(false);
      this.error.set('Tu sesión no está autorizada para gestionar la agenda. El servidor también rechaza esta consulta.');
      return;
    }
    this.loadScheduleData();
  }

  loadScheduleData(): void {
    if (!this.canManageSchedule()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    forkJoin({
      professionals: this.scheduleApi.listAssignableProfessionals(),
      services: this.scheduleApi.listOfferedServices(),
      operatingHours: this.scheduleApi.listOperatingHours(),
      professionalAvailability: this.scheduleApi.listProfessionalAvailability(),
		scheduleRequests: this.scheduleApi.listScheduleRequests(),
    }).subscribe({
      next: (response) => {
        this.professionals.set(response.professionals.data);
        this.services.set(response.services.data);
        this.operatingHours.set(response.operatingHours.data);
        this.professionalAvailability.set(response.professionalAvailability.data);
		this.scheduleRequests.set(response.scheduleRequests.data);
        const current = this.selectedProfessionalId();
        const selected = response.professionals.data.find((professional) => professional.principalId === current)
          ?? response.professionals.data[0];
        this.selectedProfessionalId.set(selected?.principalId ?? '');
        this.professionalAvailabilityForm.controls.professionalPrincipalId.setValue(selected?.principalId ?? '');
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('No fue posible cargar la agenda. Actualiza la vista para revalidar permisos y estado de profesionales.');
      },
    });
  }

  submitService(): void {
    if (!this.validateForm(this.serviceForm, 'Completa los datos del servicio antes de publicarlo.')) {
      return;
    }
    const value = this.serviceForm.getRawValue();
    if (value.durationMinutes % 15 !== 0) {
      this.error.set('La duración debe registrarse en intervalos de 15 minutos.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.scheduleApi.createOfferedService({
      ...value,
      publicDescription: value.publicDescription.trim() || null,
    }).subscribe({
      next: () => {
        this.serviceForm.reset({
          serviceType: 'CLINICA', name: '', publicDescription: '', durationMinutes: 30, referencePrice: 0, published: true,
        });
        this.finishSave('El servicio quedó guardado en la agenda de esta veterinaria.');
      },
      error: () => this.failSave('No fue posible guardar el servicio. Comprueba que el nombre no esté repetido para el mismo tipo.'),
    });
  }

  submitOperatingHours(): void {
    if (!this.validateTimeForm(this.operatingHoursForm, 'Revisa el horario operativo antes de guardarlo.')) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.scheduleApi.createOperatingHours(this.operatingHoursForm.getRawValue()).subscribe({
      next: () => this.finishSave('El horario operativo quedó guardado para America/Lima.'),
      error: () => this.failSave('No fue posible guardar el horario operativo.'),
    });
  }

  submitProfessionalAvailability(): void {
    if (!this.validateTimeForm(this.professionalAvailabilityForm, 'Revisa la disponibilidad antes de guardarla.')) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.scheduleApi.createProfessionalAvailability(this.professionalAvailabilityForm.getRawValue()).subscribe({
      next: () => this.finishSave('La disponibilidad del profesional fue guardada y se validó contra su rol vigente.'),
      error: () => this.failSave('No fue posible guardar la disponibilidad. El profesional pudo haber sido desactivado o perder su rol.'),
    });
  }

	confirmRequest(request: ScheduleRequest): void {
		const professionalPrincipalId = this.selectedProfessionalId();
		if (!professionalPrincipalId || this.saving()) {
			this.error.set('Selecciona un profesional vigente antes de confirmar la solicitud.');
			return;
		}
		this.saving.set(true);
		this.error.set(null);
		this.notice.set(null);
		this.scheduleApi.checkAvailability(request.serviceId, professionalPrincipalId, request.requestedStartsAt).subscribe({
			next: (availability) => {
				if (!availability.data.available) {
					this.failSave(`El intervalo ya no es confirmable: ${this.availabilityReason(availability.data.reasonCode)}.`);
					return;
				}
				this.scheduleApi.confirmScheduleRequest(request.id, professionalPrincipalId).subscribe({
					next: () => this.finishSave('La cita fue confirmada con el profesional seleccionado. El servidor revalidÃ³ el intervalo dentro de la transacciÃ³n.'),
					error: () => this.failSave('No fue posible confirmar la cita. La disponibilidad pudo cambiar mientras se realizaba la operaciÃ³n.'),
				});
			},
			error: () => this.failSave('No fue posible consultar la disponibilidad actual del profesional.'),
		});
	}

	resolveCancellation(request: ScheduleRequest, decision: 'CANCELADA' | 'CONFIRMADA'): void {
		const reason = this.cancellationReason().trim();
		if (request.status !== 'CANCELACION_SOLICITADA' || this.saving() || reason.length < 3) {
			this.error.set('Indica una justificacion de al menos tres caracteres antes de resolver la cancelacion.');
			return;
		}
		this.saving.set(true);
		this.error.set(null);
		this.notice.set(null);
		this.scheduleApi.resolveCancellation(request.id, { decision, reason }).subscribe({
			next: () => {
				this.cancellationReason.set('');
				this.finishSave(decision === 'CANCELADA'
					? 'La cancelacion fue aprobada y el intervalo quedo liberado.'
					: 'La cancelacion fue rechazada y la cita confirmada se conserva.');
			},
			error: () => this.failSave('No fue posible resolver la cancelacion. Actualiza la agenda para comprobar su estado vigente.'),
		});
	}

  selectProfessional(principalId: string): void {
    this.selectedProfessionalId.set(principalId);
    this.professionalAvailabilityForm.controls.professionalPrincipalId.setValue(principalId);
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

  weekdayLabel(weekday: number): string {
    return this.weekdays.find((item) => item.value === weekday)?.label ?? 'Día no válido';
  }

  serviceState(service: OfferedService): string {
    return service.status === 'ACTIVO' && service.published ? 'Publicado' : service.status === 'ACTIVO' ? 'Borrador' : 'Inactivo';
  }

	serviceName(serviceId: string): string {
		return this.services().find((service) => service.id === serviceId)?.name ?? 'Servicio no disponible';
	}

	requestDateTime(value: string): string {
		return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Lima' })
			.format(new Date(value));
	}

	availabilityReason(reasonCode: string | null): string {
		return ({
			PROFESSIONAL_NOT_ASSIGNABLE: 'el profesional ya no tiene el rol vigente requerido',
			OUTSIDE_OPERATING_HOURS: 'la hora queda fuera del horario de la veterinaria',
			OUTSIDE_PROFESSIONAL_AVAILABILITY: 'la hora queda fuera de su disponibilidad',
			SCHEDULE_BLOCKED: 'hay un bloqueo registrado',
			PROFESSIONAL_INTERVAL_CONFLICT: 'existe otra reserva confirmada para ese profesional',
		} as Record<string, string>)[reasonCode ?? ''] ?? 'el intervalo no puede confirmarse';
	}

  trackById(_: number, item: { id: string }): string {
    return item.id;
  }

  trackProfessional(_: number, professional: AssignableProfessional): string {
    return professional.principalId;
  }

  private validateForm(form: typeof this.serviceForm, message: string): boolean {
    if (form.invalid) {
      form.markAllAsTouched();
      this.error.set(message);
      return false;
    }
    return true;
  }

  private validateTimeForm(form: typeof this.operatingHoursForm | typeof this.professionalAvailabilityForm, message: string): boolean {
    if (form.invalid || form.controls.endsAtLocal.value <= form.controls.startsAtLocal.value) {
      form.markAllAsTouched();
      this.error.set(form.invalid ? message : 'La hora final debe ser posterior a la hora inicial.');
      return false;
    }
    return true;
  }

  private finishSave(message: string): void {
    this.saving.set(false);
    this.notice.set(message);
    this.loadScheduleData();
  }

  private failSave(message: string): void {
    this.saving.set(false);
    this.error.set(message);
  }
}
