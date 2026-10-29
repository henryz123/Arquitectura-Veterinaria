import { NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { AdministrationApiService } from '../../core/api/administration-api.service';
import { ClientOwnerLinkStatus, ClientPetProfile, ClientScheduleProposal, ClientSpace, OfferedService, OwnerProfile, OwnerRequest, PetRequest, ScheduleRequest } from '../../core/api/api.models';
import { ScheduleApiService } from '../../core/api/schedule-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';

type ClientSection = 'home' | 'pets' | 'register-pet' | 'appointments' | 'request-appointment' | 'profile';

@Component({
  selector: 'app-client-space-page',
  imports: [FormsModule, NgFor, NgIf, RouterLink],
  templateUrl: './client-space-page.component.html',
  styleUrl: './client-space-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ClientSpacePageComponent implements OnInit {
  private readonly administrationApi = inject(AdministrationApiService);
  private readonly scheduleApi = inject(ScheduleApiService);
  private readonly auth = inject(AuthStateService);
  private readonly router = inject(Router);
  readonly space = signal<ClientSpace | null>(null);
  readonly linkStatus = signal<ClientOwnerLinkStatus | null>(null);
  readonly pets = signal<ClientPetProfile[]>([]);
  readonly scheduleServices = signal<OfferedService[]>([]);
  readonly scheduleRequests = signal<ScheduleRequest[]>([]);
  readonly scheduleProposals = signal<ClientScheduleProposal[]>([]);
  readonly ownerProfile = signal<OwnerProfile | null>(null);
  readonly editingPet = signal<ClientPetProfile | null>(null);
  readonly activeSection = signal<ClientSection>('home');
  readonly loading = signal(true);
  readonly actionPending = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly ownerDraft: OwnerRequest = { givenNames: '', familyNames: '', phone: null, email: null, address: null };
  readonly newPetDraft: PetRequest = this.emptyPetDraft();
  readonly petEditDraft: PetRequest = this.emptyPetDraft();
  readonly scheduleDraft = { petId: '', serviceId: '', requestedStartsAt: '', observation: '' };

  ngOnInit(): void {
    forkJoin({
      space: this.administrationApi.clientSpace(),
      linkStatus: this.administrationApi.getClientOwnerLinkStatus(),
      pets: this.administrationApi.listClientPets(),
	  scheduleServices: this.scheduleApi.listClientScheduleServices(),
	  scheduleRequests: this.scheduleApi.listClientScheduleRequests(),
	  scheduleProposals: this.scheduleApi.listClientScheduleProposals(),
    }).subscribe({
      next: (response) => {
        this.space.set(response.space.data);
        this.linkStatus.set(response.linkStatus.data);
        this.pets.set(response.pets.data);
		this.scheduleServices.set(response.scheduleServices.data);
		this.scheduleRequests.set(response.scheduleRequests.data);
		this.scheduleProposals.set(response.scheduleProposals.data);
        if (response.linkStatus.data.status === 'APROBADA') {
          this.loadOwnerProfile();
        }
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

  showSection(section: ClientSection): void {
    this.activeSection.set(section);
  }

  isSection(...sections: ClientSection[]): boolean {
    return sections.includes(this.activeSection());
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

  saveOwnerProfile(): void {
    if (this.actionPending()) {
      return;
    }
    this.error.set(null);
    this.notice.set(null);
    this.actionPending.set(true);
    this.administrationApi.updateClientOwnerProfile({ ...this.ownerDraft }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.applyOwnerProfile(response.data);
        this.notice.set('Tus datos basicos fueron actualizados. El correo de acceso no se modifica desde esta pantalla.');
      },
      error: () => this.error.set('No fue posible actualizar la ficha basica autorizada.'),
    });
  }

  createPet(): void {
    const request = this.validPetRequest(this.newPetDraft);
    if (!request || this.actionPending()) {
      return;
    }
    this.error.set(null);
    this.notice.set(null);
    this.actionPending.set(true);
    this.administrationApi.createClientPet(request).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.pets.update((pets) => [...pets, response.data].sort((left, right) => left.name.localeCompare(right.name)));
        Object.assign(this.newPetDraft, this.emptyPetDraft());
        this.activeSection.set('pets');
        this.notice.set('La ficha basica de tu mascota fue registrada. La historia clinica sigue siendo privada.');
      },
      error: () => this.error.set('No fue posible registrar la mascota. Verifica los datos basicos e intentalo nuevamente.'),
    });
  }

  beginPetEdit(pet: ClientPetProfile): void {
    this.activeSection.set('pets');
    this.editingPet.set(pet);
    Object.assign(this.petEditDraft, {
      name: pet.name,
      species: pet.species,
      breed: pet.breed,
      sex: pet.sex,
      birthDate: pet.birthDate,
      estimatedAgeMonths: pet.estimatedAgeMonths,
      distinguishingMarks: pet.distinguishingMarks,
    });
  }

  cancelPetEdit(): void {
    this.editingPet.set(null);
    Object.assign(this.petEditDraft, this.emptyPetDraft());
  }

  savePet(): void {
    const pet = this.editingPet();
    const request = this.validPetRequest(this.petEditDraft);
    if (!pet || !request || this.actionPending()) {
      return;
    }
    this.error.set(null);
    this.notice.set(null);
    this.actionPending.set(true);
    this.administrationApi.updateClientPet(pet.id, request).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.pets.update((pets) => pets.map((item) => item.id === response.data.id ? response.data : item));
        this.cancelPetEdit();
        this.notice.set('La ficha basica fue actualizada. No se modificaron datos clinicos ni responsabilidades.');
      },
      error: () => this.error.set('No fue posible actualizar esta ficha. Puede que ya no tengas la responsabilidad vigente.'),
    });
  }

	createScheduleRequest(): void {
		if (this.actionPending()) {
			return;
		}
		if (!this.scheduleDraft.petId || !this.scheduleDraft.serviceId || !this.scheduleDraft.requestedStartsAt) {
			this.error.set('Selecciona una mascota, un servicio y una fecha futura para enviar la solicitud.');
			return;
		}
		const requestedStartsAt = new Date(this.scheduleDraft.requestedStartsAt);
		if (Number.isNaN(requestedStartsAt.getTime()) || requestedStartsAt.getTime() <= Date.now()) {
			this.error.set('La solicitud debe proponer una fecha futura. La veterinaria confirmarÃ¡ manualmente la disponibilidad.');
			return;
		}
		this.error.set(null);
		this.notice.set(null);
		this.actionPending.set(true);
		this.scheduleApi.createClientScheduleRequest({
			petId: this.scheduleDraft.petId,
			serviceId: this.scheduleDraft.serviceId,
			requestedStartsAt: requestedStartsAt.toISOString(),
			observation: this.scheduleDraft.observation.trim() || null,
			idempotencyKey: crypto.randomUUID(),
		}).pipe(finalize(() => this.actionPending.set(false))).subscribe({
			next: (response) => {
				this.scheduleRequests.update((requests) => [...requests, response.data]
					.sort((left, right) => left.requestedStartsAt.localeCompare(right.requestedStartsAt)));
				Object.assign(this.scheduleDraft, { petId: '', serviceId: '', requestedStartsAt: '', observation: '' });
				this.activeSection.set('appointments');
				this.notice.set('Tu solicitud quedÃ³ registrada. AÃºn no reserva un cupo: recepciÃ³n o administraciÃ³n confirmarÃ¡ la disponibilidad.');
			},
			error: () => this.error.set('No fue posible registrar la solicitud. Verifica que tu vinculaciÃ³n y la mascota sigan vigentes.'),
		});
	}

	serviceName(serviceId: string): string {
		return this.scheduleServices().find((service) => service.id === serviceId)?.name ?? 'Servicio no disponible';
	}

	scheduleStateLabel(status: ScheduleRequest['status']): string {
		return ({
			PENDIENTE: 'Pendiente de confirmación',
			PROPUESTA: 'Alternativa propuesta',
			CONFIRMADA: 'Confirmada',
			REPROGRAMACION_PROPUESTA: 'Reprogramación propuesta',
			PENDIENTE_REPROGRAMACION: 'Pendiente de confirmar cambio',
			CANCELACION_SOLICITADA: 'Cancelación solicitada',
			CANCELADA: 'Cancelada',
			COMPLETADA: 'Completada',
			INASISTENCIA: 'Inasistencia',
		} as Record<string, string>)[status] ?? 'Estado no disponible';
	}

	cancelScheduleRequest(request: ScheduleRequest): void {
		if (this.actionPending() || !['PENDIENTE', 'PROPUESTA', 'CONFIRMADA', 'REPROGRAMACION_PROPUESTA', 'PENDIENTE_REPROGRAMACION'].includes(request.status)) {
			return;
		}
		this.error.set(null);
		this.notice.set(null);
		this.actionPending.set(true);
		this.scheduleApi.cancelClientScheduleRequest(request.id, { reason: null }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
			next: (response) => {
				this.scheduleRequests.update((requests) => requests.map((item) => item.id === response.data.id ? response.data : item));
				this.notice.set(response.data.status === 'CANCELADA'
					? 'La solicitud pendiente fue cancelada y no ocupaba un cupo.'
					: 'La cancelacion fue enviada para revision. La cita confirmada sigue vigente hasta la decision de la veterinaria.');
			},
			error: () => this.error.set('No fue posible procesar la cancelacion con el estado actual de la solicitud.'),
		});
	}

	proposalFor(request: ScheduleRequest): ClientScheduleProposal | null {
		return this.scheduleProposals().find((proposal) => proposal.requestId === request.id) ?? null;
	}

	decideScheduleProposal(proposal: ClientScheduleProposal, decision: 'ACEPTADA' | 'RECHAZADA'): void {
		if (this.actionPending()) {
			return;
		}
		this.error.set(null);
		this.notice.set(null);
		this.actionPending.set(true);
		this.scheduleApi.decideClientScheduleProposal(proposal.id, { decision }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
			next: (response) => {
				this.scheduleRequests.update((requests) => requests.map((item) => item.id === response.data.id ? response.data : item));
				this.scheduleProposals.update((proposals) => proposals.filter((item) => item.id !== proposal.id));
				this.notice.set(decision === 'RECHAZADA'
					? 'La alternativa fue rechazada. Tu cita confirmada se conserva o la solicitud vuelve a revision.'
					: response.data.status === 'PENDIENTE_REPROGRAMACION'
						? 'Aceptaste la alternativa. Recepcion debe revalidar la disponibilidad antes de cambiar la cita confirmada.'
						: 'Aceptaste la alternativa. La solicitud volvera a confirmarse con disponibilidad vigente.');
			},
			error: () => this.error.set('No fue posible registrar la decision. La propuesta pudo cambiar, vencer o no pertenecer a tu cuenta.'),
		});
	}

	petName(petId: string): string {
		return this.pets().find((pet) => pet.id === petId)?.name ?? 'Mascota no disponible';
	}

  logout(): void {
    const tenantSlug = this.auth.context()?.tenant?.slug ?? 'veterinaria-aurora';
    this.auth.logout().subscribe({
      next: () => void this.router.navigate(['/client/login', tenantSlug]),
      error: () => void this.router.navigate(['/client/login', tenantSlug]),
    });
  }

  private loadOwnerProfile(): void {
    this.administrationApi.getClientOwnerProfile().subscribe({
      next: (response) => this.applyOwnerProfile(response.data),
      error: () => this.error.set('La vinculacion fue aprobada, pero no fue posible cargar tu ficha basica.'),
    });
  }

  private applyOwnerProfile(owner: OwnerProfile): void {
    this.ownerProfile.set(owner);
    Object.assign(this.ownerDraft, {
      givenNames: owner.givenNames,
      familyNames: owner.familyNames,
      phone: owner.phone,
      email: owner.email,
      address: owner.address,
    });
  }

  private validPetRequest(draft: PetRequest): PetRequest | null {
    if (draft.birthDate && draft.estimatedAgeMonths !== null) {
      this.error.set('Indica una fecha de nacimiento o una edad estimada, pero no ambas.');
      return null;
    }
    if (!draft.birthDate && draft.estimatedAgeMonths === null) {
      this.error.set('Indica una fecha de nacimiento o una edad estimada para la mascota.');
      return null;
    }
    return { ...draft, birthDate: draft.birthDate || null };
  }

  private emptyPetDraft(): PetRequest {
    return { name: '', species: '', breed: null, sex: 'DESCONOCIDO', birthDate: null, estimatedAgeMonths: null, distinguishingMarks: null };
  }
}
