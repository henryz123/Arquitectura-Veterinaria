import { DatePipe, NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin, of, switchMap } from 'rxjs';
import { AdministrationApiService } from '../../core/api/administration-api.service';
import {
  ClinicalEpisode,
  ClinicalEpisodeAmendment,
  ClinicalEpisodeRequest,
  ClinicalPrescription,
  ClientOwnerLinkRequest,
  OwnerProfile,
  OwnershipPeriod,
  PetProfile,
  VaccinationRecord,
} from '../../core/api/api.models';
import { AuthStateService } from '../../core/auth/auth-state.service';

@Component({
  selector: 'app-owner-pet-care-page',
  imports: [DatePipe, NgFor, NgIf, ReactiveFormsModule, RouterLink],
  templateUrl: './owner-pet-care-page.component.html',
  styleUrl: './owner-pet-care-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OwnerPetCarePageComponent implements OnInit {
  private readonly api = inject(AdministrationApiService);
  private readonly auth = inject(AuthStateService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly context = this.auth.context;
  readonly owners = signal<OwnerProfile[]>([]);
  readonly selectedOwner = signal<OwnerProfile | null>(null);
  readonly ownerPets = signal<PetProfile[]>([]);
  readonly selectedPet = signal<PetProfile | null>(null);
  readonly clinicalHistory = signal<ClinicalEpisode[]>([]);
  readonly selectedClinicalEpisode = signal<ClinicalEpisode | null>(null);
  readonly vaccinations = signal<VaccinationRecord[]>([]);
  readonly prescriptions = signal<ClinicalPrescription[]>([]);
  readonly amendments = signal<ClinicalEpisodeAmendment[]>([]);
  readonly ownershipPeriods = signal<OwnershipPeriod[]>([]);
  readonly linkRequests = signal<ClientOwnerLinkRequest[]>([]);
  readonly selectedLinkRequest = signal<ClientOwnerLinkRequest | null>(null);
  readonly loading = signal(true);
  readonly actionPending = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly ownerSubmitted = signal(false);
  readonly petSubmitted = signal(false);
  readonly clinicalSubmitted = signal(false);
  readonly vaccinationSubmitted = signal(false);
  readonly prescriptionSubmitted = signal(false);
  readonly amendmentSubmitted = signal(false);
  private vaccinationOperationId = this.newOperationId();
  readonly canManageRegistry = computed(() => {
    const context = this.context();
    return context?.accountType === 'STAFF'
      && (context.roles.includes('ADMIN_VETERINARIA') || context.roles.includes('RECEPCION'));
  });
  readonly canWriteClinical = computed(() => this.context()?.accountType === 'STAFF' && this.context()?.roles.includes('VETERINARIO'));

  readonly ownerForm = this.formBuilder.nonNullable.group({
    givenNames: ['', [Validators.required, Validators.maxLength(120)]],
    familyNames: ['', [Validators.required, Validators.maxLength(120)]],
    phone: ['', Validators.maxLength(40)],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    address: ['', Validators.maxLength(240)],
  });
  readonly petForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    species: ['', [Validators.required, Validators.maxLength(80)]],
    breed: ['', Validators.maxLength(120)],
    sex: this.formBuilder.nonNullable.control<'HEMBRA' | 'MACHO' | 'DESCONOCIDO'>('DESCONOCIDO'),
    birthDate: [''],
    estimatedAgeMonths: [''],
    distinguishingMarks: ['', Validators.maxLength(500)],
  });
  readonly clinicalForm = this.formBuilder.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(4000)]],
    anamnesis: ['', Validators.maxLength(12000)],
    assessment: ['', [Validators.required, Validators.maxLength(12000)]],
    diagnosis: ['', Validators.maxLength(4000)],
    treatmentPlan: ['', Validators.maxLength(12000)],
    observations: ['', Validators.maxLength(12000)],
  });
  readonly vaccinationForm = this.formBuilder.nonNullable.group({
    episodeId: [''],
    vaccineName: ['', [Validators.required, Validators.maxLength(240)]],
    administeredOn: ['', Validators.required],
    dose: ['', Validators.maxLength(240)],
    batchCode: ['', Validators.maxLength(240)],
    nextDueOn: [''],
  });
  readonly prescriptionForm = this.formBuilder.nonNullable.group({
    medication: ['', [Validators.required, Validators.maxLength(500)]],
    presentation: ['', [Validators.required, Validators.maxLength(500)]],
    dose: ['', [Validators.required, Validators.maxLength(500)]],
    route: ['', [Validators.required, Validators.maxLength(240)]],
    frequency: ['', [Validators.required, Validators.maxLength(240)]],
    duration: ['', [Validators.required, Validators.maxLength(240)]],
    instructions: ['', [Validators.required, Validators.maxLength(12000)]],
    prescribedOn: ['', Validators.required],
  });
  readonly amendmentForm = this.formBuilder.nonNullable.group({
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    addendum: ['', [Validators.required, Validators.maxLength(12000)]],
  });
  readonly transferForm = this.formBuilder.nonNullable.group({
    newOwnerId: ['', Validators.required],
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
    identityVerified: false,
  });
  readonly linkReviewForm = this.formBuilder.nonNullable.group({
    status: this.formBuilder.nonNullable.control<'APROBADA' | 'RECHAZADA'>('APROBADA'),
    ownerId: ['', Validators.required],
    verificationMethod: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(120)]],
    rejectionReason: ['', Validators.maxLength(500)],
    identityVerified: false,
  });

  ngOnInit(): void {
    if (this.context()?.accountType !== 'STAFF') {
      void this.router.navigateByUrl('/login/veterinaria-aurora');
      return;
    }
    this.loadOwners();
    if (this.canManageRegistry()) {
      this.loadLinkRequests();
    }
  }

  loadOwners(query?: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listOwners(query).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => this.owners.set(response.data),
      error: () => this.error.set('No fue posible cargar los propietarios autorizados. Actualiza la página e inténtalo nuevamente.'),
    });
  }

  loadLinkRequests(): void {
    this.api.listClientOwnerLinkRequests().subscribe({
      next: (response) => this.linkRequests.set(response.data),
      error: () => this.error.set('No fue posible cargar las solicitudes de vinculación pendientes.'),
    });
  }

  selectLinkRequest(request: ClientOwnerLinkRequest): void {
    this.selectedLinkRequest.set(request);
    this.linkReviewForm.reset({
      status: 'APROBADA', ownerId: '', verificationMethod: '', rejectionReason: '', identityVerified: false,
    });
    this.clearFeedback();
  }

  isLinkApproval(): boolean {
    return this.linkReviewForm.controls.status.value === 'APROBADA';
  }

  resolveClientLinkRequest(): void {
    const request = this.selectedLinkRequest();
    if (!request || !this.canManageRegistry() || this.actionPending()) {
      return;
    }
    const value = this.linkReviewForm.getRawValue();
    if (!value.identityVerified || !value.verificationMethod.trim()) {
      this.error.set('Registra el método general y confirma la verificación manual antes de resolver.');
      this.linkReviewForm.markAllAsTouched();
      return;
    }
    if (value.status === 'APROBADA' && !value.ownerId) {
      this.error.set('Selecciona el propietario local que fue verificado.');
      return;
    }
    if (value.status === 'RECHAZADA' && value.rejectionReason.trim().length < 3) {
      this.error.set('Indica un motivo de rechazo de al menos 3 caracteres.');
      return;
    }
    this.clearFeedback();
    this.actionPending.set(true);
    this.api.resolveClientOwnerLinkRequest(request.id, {
      status: value.status,
      ownerId: value.status === 'APROBADA' ? value.ownerId : null,
      verificationMethod: value.verificationMethod.trim(),
      rejectionReason: value.status === 'RECHAZADA' ? value.rejectionReason.trim() : null,
      identityVerified: value.identityVerified,
    }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.linkRequests.update((requests) => requests.filter((item) => item.id !== response.data.id));
        this.selectedLinkRequest.set(null);
        this.linkReviewForm.reset({
          status: 'APROBADA', ownerId: '', verificationMethod: '', rejectionReason: '', identityVerified: false,
        });
        this.notice.set(response.data.status === 'APROBADA'
          ? 'La vinculación fue aprobada con verificación manual y trazabilidad.'
          : 'La solicitud fue rechazada con un motivo registrado. No se habilitó acceso.');
      },
      error: () => this.error.set('No fue posible resolver la solicitud. Confirma el estado vigente y tus permisos.'),
    });
  }

  createOwner(): void {
    this.ownerSubmitted.set(true);
    if (!this.canManageRegistry() || this.ownerForm.invalid || this.actionPending()) {
      this.ownerForm.markAllAsTouched();
      return;
    }
    this.clearFeedback();
    this.actionPending.set(true);
    const value = this.ownerForm.getRawValue();
    this.api.createOwner({
      givenNames: value.givenNames,
      familyNames: value.familyNames,
      phone: this.optional(value.phone),
      email: this.optional(value.email),
      address: this.optional(value.address),
    }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.owners.update((owners) => [...owners, response.data].sort((left, right) => this.ownerLabel(left).localeCompare(this.ownerLabel(right))));
        this.selectedOwner.set(response.data);
        this.selectedPet.set(null);
        this.ownerPets.set([]);
        this.clinicalHistory.set([]);
        this.selectedClinicalEpisode.set(null);
        this.vaccinations.set([]);
        this.prescriptions.set([]);
        this.amendments.set([]);
        this.ownershipPeriods.set([]);
        this.ownerForm.reset({ givenNames: '', familyNames: '', phone: '', email: '', address: '' });
        this.ownerSubmitted.set(false);
        this.notice.set('El propietario se registró en la veterinaria actual. Ya puedes asociar una mascota.');
      },
      error: () => this.error.set('No fue posible registrar el propietario. Revisa los datos o tus permisos efectivos.'),
    });
  }

  selectOwner(owner: OwnerProfile): void {
    this.selectedOwner.set(owner);
    this.selectedPet.set(null);
    this.ownerPets.set([]);
    this.clinicalHistory.set([]);
    this.selectedClinicalEpisode.set(null);
    this.vaccinations.set([]);
    this.prescriptions.set([]);
    this.amendments.set([]);
    this.ownershipPeriods.set([]);
    this.petSubmitted.set(false);
    this.clearFeedback();
    this.api.listPetsByOwner(owner.id).subscribe({
      next: (response) => this.ownerPets.set(response.data),
      error: () => this.error.set('No fue posible cargar las mascotas asociadas a este propietario.'),
    });
  }

  createPet(): void {
    const owner = this.selectedOwner();
    this.petSubmitted.set(true);
    if (!owner || !this.canManageRegistry() || this.petForm.invalid || this.actionPending()) {
      this.petForm.markAllAsTouched();
      return;
    }
    const value = this.petForm.getRawValue();
    const birthDate = this.optional(value.birthDate);
    const estimatedAgeMonths = this.ageInMonths(value.estimatedAgeMonths);
    const hasEstimatedAge = String(value.estimatedAgeMonths ?? '').trim().length > 0;
    if ((birthDate === null && estimatedAgeMonths === null) || (birthDate !== null && estimatedAgeMonths !== null)) {
      this.error.set('Indica una fecha de nacimiento o una edad estimada, pero no ambas.');
      return;
    }
    if (hasEstimatedAge && estimatedAgeMonths === null) {
      this.error.set('La edad estimada debe ser un número entero entre 0 y 480 meses.');
      return;
    }
    this.clearFeedback();
    this.actionPending.set(true);
    this.api.createPet(owner.id, {
      name: value.name,
      species: value.species,
      breed: this.optional(value.breed),
      sex: value.sex,
      birthDate,
      estimatedAgeMonths,
      distinguishingMarks: this.optional(value.distinguishingMarks),
    }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.selectedPet.set(response.data);
        this.ownerPets.update((pets) => [...pets, response.data].sort((left, right) => left.name.localeCompare(right.name)));
        this.clinicalHistory.set([]);
        this.selectedClinicalEpisode.set(null);
        this.vaccinations.set([]);
        this.prescriptions.set([]);
        this.amendments.set([]);
        this.ownershipPeriods.set([]);
        this.petForm.reset({ name: '', species: '', breed: '', sex: 'DESCONOCIDO', birthDate: '', estimatedAgeMonths: '', distinguishingMarks: '' });
        this.petSubmitted.set(false);
        this.notice.set(`La ficha de ${response.data.name} quedó asociada al propietario seleccionado.`);
      },
      error: () => this.error.set('No fue posible registrar la mascota. Revisa los datos, el propietario y tus permisos.'),
    });
  }

  selectPet(pet: PetProfile): void {
    this.clearFeedback();
    this.actionPending.set(true);
    const history = this.canWriteClinical() ? this.api.listClinicalEpisodes(pet.id) : of(null);
    const vaccinations = this.canWriteClinical() ? this.api.listVaccinations(pet.id) : of(null);
    const periods = this.canManageRegistry() ? this.api.listOwnershipPeriods(pet.id) : of(null);
    forkJoin({ pet: this.api.getPet(pet.id), history, vaccinations, periods }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: ({ pet: response, history: episodes, vaccinations: records, periods: ownership }) => {
        this.selectedPet.set(response.data);
        this.clinicalHistory.set(episodes?.data ?? []);
        this.selectedClinicalEpisode.set(null);
        this.vaccinations.set(records?.data ?? []);
        this.prescriptions.set([]);
        this.amendments.set([]);
        this.ownershipPeriods.set(ownership?.data ?? []);
      },
      error: () => this.error.set('No fue posible recuperar la ficha autorizada de la mascota.'),
    });
  }

  saveClinicalEpisode(): void {
    const pet = this.selectedPet();
    this.clinicalSubmitted.set(true);
    if (!pet || !this.canWriteClinical() || this.clinicalForm.invalid || this.actionPending()) {
      this.clinicalForm.markAllAsTouched();
      return;
    }
    this.clearFeedback();
    this.actionPending.set(true);
    const request = this.clinicalRequest();
    this.api.createClinicalEpisode(pet.id, request).pipe(
      switchMap((created) => this.api.finalizeClinicalEpisode(created.data.id, request)),
      finalize(() => this.actionPending.set(false)),
    ).subscribe({
      next: (response) => {
        this.clinicalHistory.update((history) => [response.data, ...history]);
        this.selectedClinicalEpisode.set(response.data);
        this.prescriptions.set([]);
        this.amendments.set([]);
        this.clinicalForm.reset({ reason: '', anamnesis: '', assessment: '', diagnosis: '', treatmentPlan: '', observations: '' });
        this.clinicalSubmitted.set(false);
        this.notice.set('La atención clínica fue registrada y finalizada por el veterinario de la sesión.');
      },
      error: () => this.error.set('No fue posible registrar la atención clínica. El servidor mantuvo sus controles de rol, tenant y transición.'),
    });
  }

  selectClinicalEpisode(episode: ClinicalEpisode): void {
    if (!this.canWriteClinical()) {
      return;
    }
    this.selectedClinicalEpisode.set(episode);
    this.prescriptions.set([]);
    this.amendments.set([]);
    forkJoin({
      prescriptions: this.api.listClinicalPrescriptions(episode.id),
      amendments: this.api.listClinicalEpisodeAmendments(episode.id),
    }).subscribe({
      next: ({ prescriptions, amendments }) => {
        this.prescriptions.set(prescriptions.data);
        this.amendments.set(amendments.data);
      },
      error: () => this.error.set('No fue posible cargar las indicaciones y rectificaciones autorizadas de la atención.'),
    });
  }

  selectClinicalEpisodeById(episodeId: string): void {
    const episode = this.clinicalHistory().find((item) => item.id === episodeId);
    if (episode) {
      this.selectClinicalEpisode(episode);
    }
  }

  recordVaccination(): void {
    const pet = this.selectedPet();
    this.vaccinationSubmitted.set(true);
    if (!pet || !this.canWriteClinical() || this.vaccinationForm.invalid || this.actionPending()) {
      this.vaccinationForm.markAllAsTouched();
      return;
    }
    const value = this.vaccinationForm.getRawValue();
    if (value.nextDueOn && value.nextDueOn < value.administeredOn) {
      this.error.set('La próxima dosis no puede ser anterior a la vacunación.');
      return;
    }
    this.clearFeedback();
    this.actionPending.set(true);
    this.api.recordVaccination(pet.id, {
      episodeId: this.optional(value.episodeId),
      operationId: this.vaccinationOperationId,
      vaccineName: value.vaccineName.trim(),
      administeredOn: value.administeredOn,
      dose: this.optional(value.dose),
      batchCode: this.optional(value.batchCode),
      nextDueOn: this.optional(value.nextDueOn),
    }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.vaccinations.update((records) => records.some((item) => item.id === response.data.id)
          ? records : [response.data, ...records]);
        this.vaccinationOperationId = this.newOperationId();
        this.vaccinationForm.reset({ episodeId: '', vaccineName: '', administeredOn: '', dose: '', batchCode: '', nextDueOn: '' });
        this.vaccinationSubmitted.set(false);
        this.notice.set('La vacuna se registró una sola vez con trazabilidad del veterinario.');
      },
      error: () => this.error.set('No fue posible registrar la vacuna. Conserva el formulario y vuelve a intentarlo para no duplicar la operación.'),
    });
  }

  recordPrescription(): void {
    const episode = this.selectedClinicalEpisode();
    this.prescriptionSubmitted.set(true);
    if (!episode || !this.canWriteClinical() || this.prescriptionForm.invalid || this.actionPending()) {
      this.prescriptionForm.markAllAsTouched();
      if (!episode) {
        this.error.set('Selecciona una atención clínica antes de registrar una indicación.');
      }
      return;
    }
    const value = this.prescriptionForm.getRawValue();
    this.clearFeedback();
    this.actionPending.set(true);
    this.api.recordClinicalPrescription(episode.id, {
      medication: value.medication.trim(), presentation: value.presentation.trim(), dose: value.dose.trim(),
      route: value.route.trim(), frequency: value.frequency.trim(), duration: value.duration.trim(),
      instructions: value.instructions.trim(), prescribedOn: value.prescribedOn,
    }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.prescriptions.update((records) => [response.data, ...records]);
        this.prescriptionForm.reset({ medication: '', presentation: '', dose: '', route: '', frequency: '', duration: '', instructions: '', prescribedOn: '' });
        this.prescriptionSubmitted.set(false);
        this.notice.set('La indicación básica quedó registrada para esta atención. No es una receta electrónica legal.');
      },
      error: () => this.error.set('No fue posible registrar la indicación. Confirma la atención seleccionada y los permisos clínicos.'),
    });
  }

  addClinicalAmendment(): void {
    const episode = this.selectedClinicalEpisode();
    this.amendmentSubmitted.set(true);
    if (!episode || !this.canWriteClinical() || this.amendmentForm.invalid || this.actionPending()) {
      this.amendmentForm.markAllAsTouched();
      if (!episode) {
        this.error.set('Selecciona la atención finalizada que necesitas rectificar.');
      }
      return;
    }
    if (episode.status !== 'FINALIZADO') {
      this.error.set('Solo las atenciones finalizadas admiten una rectificación atribuible.');
      return;
    }
    const value = this.amendmentForm.getRawValue();
    this.clearFeedback();
    this.actionPending.set(true);
    this.api.amendClinicalEpisode(episode.id, { reason: value.reason.trim(), addendum: value.addendum.trim() })
      .pipe(finalize(() => this.actionPending.set(false))).subscribe({
        next: (response) => {
          this.amendments.update((records) => [response.data, ...records]);
          this.amendmentForm.reset({ reason: '', addendum: '' });
          this.amendmentSubmitted.set(false);
          this.notice.set('La rectificación se agregó como adenda; el episodio original permanece intacto.');
        },
        error: () => this.error.set('No fue posible registrar la rectificación. El episodio original no fue sobrescrito.'),
      });
  }

  transferOwnership(): void {
    const pet = this.selectedPet();
    if (!pet || !this.canManageRegistry() || this.transferForm.invalid || !this.transferForm.controls.identityVerified.value || this.actionPending()) {
      this.transferForm.markAllAsTouched();
      if (!this.transferForm.controls.identityVerified.value) {
        this.error.set('Confirma que verificaste la identidad y la relación antes de transferir.');
      }
      return;
    }
    this.clearFeedback();
    this.actionPending.set(true);
    const value = this.transferForm.getRawValue();
    this.api.transferPetOwnership(pet.id, {
      expectedOwnerId: pet.currentOwnerId,
      newOwnerId: value.newOwnerId,
      reason: value.reason.trim(),
      identityVerified: value.identityVerified,
    }).pipe(finalize(() => this.actionPending.set(false))).subscribe({
      next: (response) => {
        this.selectedPet.set(response.data);
        this.transferForm.reset({ newOwnerId: '', reason: '', identityVerified: false });
        const newOwner = this.owners().find((owner) => owner.id === response.data.currentOwnerId) ?? null;
        if (newOwner) {
          this.selectedOwner.set(newOwner);
          this.api.listPetsByOwner(newOwner.id).subscribe({ next: (pets) => this.ownerPets.set(pets.data) });
        }
        this.api.listOwnershipPeriods(response.data.id).subscribe({ next: (periods) => this.ownershipPeriods.set(periods.data) });
        this.notice.set('La responsabilidad se transfirió con trazabilidad de períodos. La historia clínica no se modificó.');
      },
      error: () => this.error.set('No fue posible transferir la responsabilidad. Recarga la ficha para confirmar el responsable vigente.'),
    });
  }

  ownerLabel(owner: OwnerProfile): string {
    return `${owner.familyNames}, ${owner.givenNames}`;
  }

  petAge(pet: PetProfile): string {
    return pet.birthDate ? `Nacimiento: ${pet.birthDate}` : `Edad estimada: ${pet.estimatedAgeMonths ?? 0} meses`;
  }

  ownerPeriodLabel(period: OwnershipPeriod): string {
    return `${period.ownerFamilyNames}, ${period.ownerGivenNames}`;
  }

  trackById(_: number, item: { id: string }): string {
    return item.id;
  }

  private clinicalRequest(): ClinicalEpisodeRequest {
    const value = this.clinicalForm.getRawValue();
    return {
      reason: this.optional(value.reason),
      anamnesis: this.optional(value.anamnesis),
      assessment: this.optional(value.assessment),
      diagnosis: this.optional(value.diagnosis),
      treatmentPlan: this.optional(value.treatmentPlan),
      observations: this.optional(value.observations),
    };
  }

  private ageInMonths(value: string | number): number | null {
    const normalized = String(value ?? '').trim();
    if (!/^\d+$/.test(normalized)) {
      return null;
    }
    const parsed = Number(normalized);
    return Number.isSafeInteger(parsed) && parsed >= 0 && parsed <= 480 ? parsed : null;
  }

  private optional(value: string): string | null {
    const normalized = value.trim();
    return normalized || null;
  }

  private newOperationId(): string {
    return globalThis.crypto.randomUUID();
  }

  private clearFeedback(): void {
    this.error.set(null);
    this.notice.set(null);
  }
}
