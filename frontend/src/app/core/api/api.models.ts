export interface ApiEnvelope<T> {
  data: T;
}

export interface TenantProfile {
  slug: string;
  commercialName: string;
  status: 'ACTIVO' | 'SUSPENDIDO';
}

export interface SessionContext {
  accountType: 'STAFF' | 'CLIENT' | 'SAAS';
  tenant: TenantProfile | null;
  roles: string[];
}

export interface LoginResult {
  csrfToken: string;
  context: SessionContext;
}

export interface CsrfToken {
  csrfToken: string;
}

export interface ClientSpace {
	tenantSlug: string;
	commercialName: string;
	message: string;
}

export type ClientOwnerLinkStatusCode = 'SIN_SOLICITUD' | 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';

export interface ClientOwnerLinkStatus {
	status: ClientOwnerLinkStatusCode;
	requestReference: string | null;
	requestedAt: string | null;
	resolvedAt: string | null;
	rejectionReason: string | null;
}

export interface ClientOwnerLinkRequest {
	id: string;
	tenantId: string;
	requestReference: string;
	clientPrincipalId: string;
	status: 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';
	proposedOwnerId: string | null;
	verificationMethod: string | null;
	rejectionReason: string | null;
	requestedAt: string;
	resolvedAt: string | null;
	resolvedByPrincipalId: string | null;
}

export interface ClientOwnerLinkResolutionRequest {
	status: 'APROBADA' | 'RECHAZADA';
	ownerId: string | null;
	verificationMethod: string;
	rejectionReason: string | null;
	identityVerified: boolean;
}

export interface ClientPetProfile {
	id: string;
	name: string;
	species: string;
	breed: string | null;
	sex: 'HEMBRA' | 'MACHO' | 'DESCONOCIDO';
	birthDate: string | null;
	estimatedAgeMonths: number | null;
	distinguishingMarks: string | null;
	status: 'ACTIVA' | 'FALLECIDA' | 'INACTIVA';
}

export interface VerificationStatus {
  status: string;
  accountType: string;
  relatedId: string;
}

export type StaffRole = 'ADMIN_VETERINARIA' | 'RECEPCION' | 'VETERINARIO' | 'CAJERO' | 'GROOMING';

export interface StaffAccount {
  principalId: string;
  email: string;
  status: 'INVITADA' | 'ACTIVA' | 'DESACTIVADA';
  roles: StaffRole[];
}

export interface StaffInvitation {
  principalId: string;
  status: 'INVITADA';
  roles: StaffRole[];
}

/** Minimal B03 projection returned only to ADMIN_VETERINARIA or RECEPCION. */
export interface AssignableProfessional {
  principalId: string;
  roles: Array<'VETERINARIO' | 'GROOMING'>;
}

export interface OfferedService {
  id: string;
  tenantId: string;
  serviceType: 'CLINICA' | 'GROOMING';
  name: string;
  publicDescription: string | null;
  durationMinutes: number;
  referencePrice: number;
  published: boolean;
  status: 'ACTIVO' | 'INACTIVO';
  createdAt: string;
  updatedAt: string;
}

export interface OfferedServiceRequest {
  serviceType: 'CLINICA' | 'GROOMING';
  name: string;
  publicDescription: string | null;
  durationMinutes: number;
  referencePrice: number;
  published: boolean;
}

export interface OperatingHours {
  id: string;
  weekday: number;
  startsAtLocal: string;
  endsAtLocal: string;
  createdAt: string;
  updatedAt: string;
}

export interface OperatingHoursRequest {
  weekday: number;
  startsAtLocal: string;
  endsAtLocal: string;
}

export interface ProfessionalAvailability {
  id: string;
  professionalPrincipalId: string;
  weekday: number;
  startsAtLocal: string;
  endsAtLocal: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProfessionalAvailabilityRequest {
  professionalPrincipalId: string;
  weekday: number;
  startsAtLocal: string;
  endsAtLocal: string;
}

export interface ScheduleBlock {
  id: string;
  blockScope: 'ORGANIZACION' | 'PROFESIONAL';
  professionalPrincipalId: string | null;
  startsAt: string;
  endsAt: string;
  reason: string;
  createdAt: string;
}

export interface ScheduleBlockRequest {
  blockScope: 'ORGANIZACION' | 'PROFESIONAL';
  professionalPrincipalId: string | null;
  startsAt: string;
  endsAt: string;
  reason: string;
}

/** Administrative audit projection; it intentionally omits actors and professional identities. */
export interface ScheduleChangeEvent {
  id: string;
  requestId: string;
  actionCode: string;
  previousStartsAt: string | null;
  newStartsAt: string | null;
  reason: string | null;
  createdAt: string;
}

export interface ScheduleNoShowRequest {
  reason: string;
}

export interface ScheduleRequest {
  id: string;
  tenantId: string;
  requesterPrincipalId: string;
  ownerId: string;
  petId: string;
  serviceId: string;
  requestedStartsAt: string;
  requestedDurationMinutes: number;
  status: 'PENDIENTE' | 'PROPUESTA' | 'CONFIRMADA' | 'REPROGRAMACION_PROPUESTA' | 'PENDIENTE_REPROGRAMACION' | 'CANCELACION_SOLICITADA' | 'EN_ATENCION' | 'COMPLETADA' | 'CANCELADA' | 'INASISTENCIA' | 'CERRADA';
  observation: string | null;
  idempotencyKey: string;
  createdAt: string;
  updatedAt: string;
}

export interface ScheduleProposal {
  id: string;
  tenantId: string;
  requestId: string;
  version: number;
  proposedStartsAt: string;
  proposedDurationMinutes: number;
  proposedProfessionalPrincipalId: string | null;
  status: 'ENVIADA' | 'ACEPTADA' | 'RECHAZADA' | 'CERRADA';
  createdByPrincipalId: string;
  createdAt: string;
  decidedAt: string | null;
}

/** CLIENT never receives a staff identifier with a proposed alternative. */
export interface ClientScheduleProposal {
  id: string;
  requestId: string;
  version: number;
  proposedStartsAt: string;
  proposedDurationMinutes: number;
  status: 'ENVIADA';
  createdAt: string;
}

export interface ScheduleProposalRequest {
  proposedStartsAt: string;
  professionalPrincipalId: string;
  reason: string;
}

export interface ScheduleReassignmentRequest {
  professionalPrincipalId: string;
  reason: string;
}

export interface ClientProposalDecisionRequest {
  decision: 'ACEPTADA' | 'RECHAZADA';
}

export interface ClientScheduleRequest {
  petId: string;
  serviceId: string;
  requestedStartsAt: string;
  observation: string | null;
  idempotencyKey: string;
}

export interface ClientScheduleCancellationRequest {
  reason: string | null;
}

export interface ScheduleCancellationResolutionRequest {
  decision: 'CANCELADA' | 'CONFIRMADA';
  reason: string;
}

export interface ScheduleReservation {
  id: string;
  tenantId: string;
  requestId: string;
  professionalPrincipalId: string;
  startsAt: string;
  endsAt: string;
  status: 'CONFIRMADA' | 'EN_ATENCION' | 'COMPLETADA' | 'INASISTENCIA' | 'CANCELADA';
  current: boolean;
  version: number;
  confirmedByPrincipalId: string;
  createdAt: string;
  updatedAt: string;
}

/** Operational projection restricted to the GROOMING worker assigned to the reservation. */
export interface GroomingAttention {
  id: string;
  reservationId: string;
  groomerPrincipalId: string;
  startedAt: string;
  completedAt: string | null;
  result: string | null;
  operationalNotes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface GroomingTask {
  reservationId: string;
  requestId: string;
  serviceName: string;
  startsAt: string;
  endsAt: string;
  reservationStatus: 'CONFIRMADA' | 'EN_ATENCION' | 'COMPLETADA';
  attention: GroomingAttention | null;
}

export interface GroomingCompletionRequest {
  result: string;
  operationalNotes: string | null;
}

export interface ScheduleAvailability {
  professionalPrincipalId: string;
  serviceId: string;
  startsAt: string;
  endsAt: string;
  available: boolean;
  reasonCode: string | null;
}

export interface Organization {
  id: string;
  commercialName: string;
  ruc: string | null;
  commercialAddress: string;
  commercialPhone: string;
  commercialEmail: string;
  registeredAt: string;
  status: 'ACTIVO' | 'SUSPENDIDO';
}

export interface RucReview {
  id: string;
  ruc: string;
  requesterEmail: string;
  normalizedRequesterEmail: string;
  contactChannel: string;
  requestReason: string;
  status: string;
  resolutionReason: string | null;
  resolvedByPrincipalId: string | null;
  resolvedAt: string | null;
  affectedTenantId: string | null;
}

export interface AdministrativeRecovery {
  id: string;
  tenantId: string;
  applicantEmail: string;
  normalizedApplicantEmail: string;
  evidenceReference: string;
  status: string;
  decisionReason: string | null;
  reviewedByPrincipalId: string | null;
  reviewedAt: string | null;
}

export interface OwnerProfile {
  id: string;
  tenantId: string;
  givenNames: string;
  familyNames: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  status: 'ACTIVO' | 'INACTIVO';
  origin: string;
  createdAt: string;
  updatedAt: string;
}

export interface OwnerRequest {
  givenNames: string;
  familyNames: string;
  phone: string | null;
  email: string | null;
  address: string | null;
}

export interface PetProfile {
  id: string;
  tenantId: string;
  currentOwnerId: string;
  name: string;
  species: string;
  breed: string | null;
  sex: 'HEMBRA' | 'MACHO' | 'DESCONOCIDO';
  birthDate: string | null;
  estimatedAgeMonths: number | null;
  distinguishingMarks: string | null;
  status: 'ACTIVA' | 'FALLECIDA' | 'INACTIVA';
  createdAt: string;
  updatedAt: string;
}

export interface PetRequest {
  name: string;
  species: string;
  breed: string | null;
  sex: 'HEMBRA' | 'MACHO' | 'DESCONOCIDO';
  birthDate: string | null;
  estimatedAgeMonths: number | null;
  distinguishingMarks: string | null;
}

export interface DuplicateCandidate {
  id: string;
  label: string;
  matchReason: string;
}

export interface DuplicateReviewRequest {
  duplicateId: string;
  referenceId: string;
}

export interface DuplicateResolutionRequest {
  outcome: 'DESCARTADA' | 'CONFIRMADA_INACTIVA';
  reason: string;
}

export interface OwnerDuplicateReview {
  id: string;
  duplicateOwnerId: string;
  referenceOwnerId: string;
  duplicateLabel: string | null;
  referenceLabel: string | null;
  reportedAt: string;
  outcome: 'DESCARTADA' | 'CONFIRMADA_INACTIVA' | null;
  resolvedAt: string | null;
}

export interface PetDuplicateReview {
  id: string;
  duplicatePetId: string;
  referencePetId: string;
  duplicateLabel: string | null;
  referenceLabel: string | null;
  reportedAt: string;
  outcome: 'DESCARTADA' | 'CONFIRMADA_INACTIVA' | null;
  resolvedAt: string | null;
  clinicalEvaluatedAt: string | null;
  requiresClinicalEvaluation: boolean;
}

export interface ClinicalEpisode {
  id: string;
  tenantId: string;
  petId: string;
  veterinarianPrincipalId: string;
  appointmentId: string | null;
  status: 'BORRADOR' | 'FINALIZADO';
  reason: string | null;
  anamnesis: string | null;
  assessment: string | null;
  diagnosis: string | null;
  treatmentPlan: string | null;
  observations: string | null;
  openedAt: string;
  finalizedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ClinicalEpisodeRequest {
  reason: string | null;
  anamnesis: string | null;
  assessment: string | null;
  diagnosis: string | null;
  treatmentPlan: string | null;
  observations: string | null;
}

export interface VaccinationRecord {
  id: string;
  tenantId: string;
  petId: string;
  episodeId: string | null;
  veterinarianPrincipalId: string;
  operationId: string;
  vaccineName: string;
  administeredOn: string;
  dose: string | null;
  batchCode: string | null;
  nextDueOn: string | null;
  recordedAt: string;
  createdAt: string;
}

export interface VaccinationRequest {
  episodeId: string | null;
  operationId: string;
  vaccineName: string;
  administeredOn: string;
  dose: string | null;
  batchCode: string | null;
  nextDueOn: string | null;
}

export interface ClinicalPrescription {
  id: string;
  tenantId: string;
  petId: string;
  episodeId: string;
  veterinarianPrincipalId: string;
  medication: string;
  presentation: string;
  dose: string;
  route: string;
  frequency: string;
  duration: string;
  instructions: string;
  prescribedOn: string;
  createdAt: string;
}

export interface PrescriptionRequest {
  medication: string;
  presentation: string;
  dose: string;
  route: string;
  frequency: string;
  duration: string;
  instructions: string;
  prescribedOn: string;
}

export interface ClinicalEpisodeAmendment {
  id: string;
  tenantId: string;
  petId: string;
  episodeId: string;
  veterinarianPrincipalId: string;
  reason: string;
  previousContent: string;
  addendum: string;
  createdAt: string;
}

export interface AmendmentRequest {
  reason: string;
  addendum: string;
}

export interface OwnershipPeriod {
  id: string;
  tenantId: string;
  petId: string;
  ownerId: string;
  ownerGivenNames: string;
  ownerFamilyNames: string;
  effectiveFrom: string;
  effectiveUntil: string | null;
  verifiedByPrincipalId: string;
  reason: string | null;
}

export interface OwnershipTransferRequest {
  expectedOwnerId: string;
  newOwnerId: string;
  reason: string;
  identityVerified: boolean;
}

export type CatalogCategoryStatus = 'ACTIVA' | 'INACTIVA';
export type CatalogProductStatus = 'ACTIVO_PUBLICADO' | 'ACTIVO_NO_PUBLICADO' | 'INACTIVO';

export interface CatalogCategory {
  id: string;
  tenantId: string;
  name: string;
  status: CatalogCategoryStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogCategoryRequest {
  name: string;
  status: CatalogCategoryStatus;
}

export interface CatalogProduct {
  id: string;
  tenantId: string;
  categoryId: string | null;
  categoryName: string | null;
  sku: string;
  title: string;
  description: string | null;
  salePricePen: number;
  status: CatalogProductStatus;
  inventoryManaged: boolean;
  imageUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogProductRequest {
  categoryId: string | null;
  sku: string;
  title: string;
  description: string | null;
  salePricePen: number;
  status: CatalogProductStatus;
  inventoryManaged: boolean;
  imageUrl: string | null;
}

export interface StockBalance {
  id: string;
  productId: string;
  sku: string;
  productTitle: string;
  availableQuantity: number;
  version: number;
  updatedAt: string;
}

export interface StockMovement {
  id: string;
  productId: string;
  productTitle: string;
  movementType: 'ALTA_INICIAL' | 'AJUSTE_POSITIVO' | 'AJUSTE_NEGATIVO' | 'VENTA_POS' | 'CONFIRMACION_ONLINE' | 'CANCELACION_ONLINE' | 'DEVOLUCION_APTA' | 'RECONCILIACION';
  quantityDelta: number;
  quantityBefore: number;
  quantityAfter: number;
  operationId: string;
  reason: string;
  createdAt: string;
}

export interface StockAdjustmentRequest {
  quantityDelta: number;
  reason: string;
  operationId: string;
}

export interface StockAdjustment {
  balance: StockBalance;
  movement: StockMovement;
}

export interface PointOfSaleProduct {
  id: string;
  sku: string;
  title: string;
  salePricePen: number;
  status: CatalogProductStatus;
  inventoryManaged: boolean;
  availableQuantity: number | null;
}

export interface PosSaleLineRequest {
  productId: string;
  quantity: number;
}

export interface PosSaleRequest {
  operationId: string;
  lines: PosSaleLineRequest[];
}

export interface PosSaleConfirmationRequest {
  paymentMethod: 'EFECTIVO' | 'TRANSFERENCIA' | 'OTRO';
  declaredAmountPen: number;
}

export interface PosSaleLine {
  id: string;
  productId: string;
  productSkuSnapshot: string;
  productTitleSnapshot: string;
  inventoryManaged: boolean;
  quantity: number;
  unitPricePen: number;
  subtotalPen: number;
}

export interface PosManualPayment {
  id: string;
  paymentMethod: 'EFECTIVO' | 'TRANSFERENCIA' | 'OTRO';
  declaredAmountPen: number;
  status: 'PENDIENTE_VALIDACION' | 'VALIDADO' | 'RECHAZADO';
  declaredAt: string;
  validatedAt: string | null;
}

export interface PosSale {
  id: string;
  tenantId: string;
  cashierPrincipalId: string;
  status: 'PREPARACION' | 'COMPLETADA' | 'ANULADA' | 'DEVOLUCION_PARCIAL' | 'DEVUELTA';
  operationId: string;
  totalAmountPen: number;
  createdAt: string;
  completedAt: string | null;
  updatedAt: string;
  lines: PosSaleLine[];
  payment: PosManualPayment | null;
}

export interface PosSaleCancellationRequest {
  operationId: string;
  reason: string;
  itemsLeftPremises: boolean;
}

export interface PosSaleCancellation {
  id: string;
  saleId: string;
  operationId: string;
  reason: string;
  itemsLeftPremises: boolean;
  stockRestocked: boolean;
  cancelledByPrincipalId: string;
  createdAt: string;
}

export interface PosReturnLineRequest {
  saleLineId: string;
  quantity: number;
}

export interface PosReturnRequest {
  operationId: string;
  reason: string;
  lines: PosReturnLineRequest[];
}

export interface PosReturnReceiptLineRequest {
  returnLineId: string;
  receivedQuantity: number;
}

export interface PosReturnInspectionLineRequest {
  returnLineId: string;
  restockedQuantity: number;
}

export interface PosReturnLine {
  id: string;
  saleLineId: string;
  productId: string;
  productSkuSnapshot: string;
  productTitleSnapshot: string;
  requestedQuantity: number;
  receivedQuantity: number;
  restockedQuantity: number;
  inspectionResult: 'PENDIENTE' | 'APTA' | 'PARCIAL' | 'NO_APTA';
}

export interface PosReturn {
  id: string;
  tenantId: string;
  saleId: string;
  operationId: string;
  status: 'SOLICITADA' | 'RECIBIDA' | 'INSPECCIONADA_APTA' | 'INSPECCIONADA_NO_APTA' | 'CERRADA';
  reason: string;
  requestedByPrincipalId: string;
  receivedByPrincipalId: string | null;
  inspectedByPrincipalId: string | null;
  closedByPrincipalId: string | null;
  createdAt: string;
  receivedAt: string | null;
  inspectedAt: string | null;
  closedAt: string | null;
  updatedAt: string;
  lines: PosReturnLine[];
}
