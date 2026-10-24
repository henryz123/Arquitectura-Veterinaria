import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AdministrativeRecovery, AmendmentRequest, ApiEnvelope, ClientOwnerLinkRequest, ClientOwnerLinkResolutionRequest, ClientOwnerLinkStatus, ClientPetProfile, ClientSpace, ClinicalEpisode, ClinicalEpisodeAmendment, ClinicalEpisodeRequest, ClinicalPrescription, Organization, OwnerProfile, OwnerRequest, OwnershipPeriod, OwnershipTransferRequest, PetProfile, PetRequest, PrescriptionRequest, RucReview, StaffAccount, StaffInvitation, StaffRole, VaccinationRecord, VaccinationRequest, VerificationStatus } from './api.models';

@Injectable({ providedIn: 'root' })
export class AdministrationApiService {
  private readonly http = inject(HttpClient);
  private readonly apiBaseUrl = '/api/v1';

  listStaff(): Observable<ApiEnvelope<StaffAccount[]>> {
    return this.http.get<ApiEnvelope<StaffAccount[]>>(`${this.apiBaseUrl}/staff-accounts`);
  }

  inviteStaff(email: string, roles: StaffRole[]): Observable<ApiEnvelope<StaffInvitation>> {
    return this.http.post<ApiEnvelope<StaffInvitation>>(`${this.apiBaseUrl}/staff-invitations`, { email, roles });
  }

  replaceStaffRoles(principalId: string, roles: StaffRole[]): Observable<ApiEnvelope<StaffAccount>> {
    return this.http.patch<ApiEnvelope<StaffAccount>>(
      `${this.apiBaseUrl}/staff-accounts/${encodeURIComponent(principalId)}/roles`,
      { roles },
    );
  }

  deactivateStaff(principalId: string): Observable<void> {
    return this.http.post<void>(
      `${this.apiBaseUrl}/staff-accounts/${encodeURIComponent(principalId)}/deactivations`,
      {},
    );
  }

  listOrganizations(): Observable<ApiEnvelope<Organization[]>> {
    return this.http.get<ApiEnvelope<Organization[]>>(`${this.apiBaseUrl}/tenants`);
  }

  suspendOrganization(tenantId: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.apiBaseUrl}/tenants/${encodeURIComponent(tenantId)}/suspensions`, { reason, confirmed: true });
  }

  reactivateOrganization(tenantId: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.apiBaseUrl}/tenants/${encodeURIComponent(tenantId)}/reactivations`, { reason, confirmed: true });
  }

  listRucReviews(): Observable<ApiEnvelope<RucReview[]>> {
    return this.http.get<ApiEnvelope<RucReview[]>>(`${this.apiBaseUrl}/ruc-reviews`);
  }

  resolveRucReview(reviewId: string, status: string, reason: string, releaseRuc: boolean): Observable<void> {
    return this.http.patch<void>(`${this.apiBaseUrl}/ruc-reviews/${encodeURIComponent(reviewId)}`, {
      status,
      reason,
      releaseRuc,
      confirmed: true,
    });
  }

  listAdministrativeRecoveries(): Observable<ApiEnvelope<AdministrativeRecovery[]>> {
    return this.http.get<ApiEnvelope<AdministrativeRecovery[]>>(`${this.apiBaseUrl}/administrative-recoveries`);
  }

  reviewAdministrativeRecovery(recoveryId: string, status: string, reason: string, legitimacyChecked: boolean): Observable<void> {
    return this.http.patch<void>(`${this.apiBaseUrl}/administrative-recoveries/${encodeURIComponent(recoveryId)}`, {
      status,
      reason,
      legitimacyChecked,
    });
  }

  clientSpace(): Observable<ApiEnvelope<ClientSpace>> {
    return this.http.get<ApiEnvelope<ClientSpace>>(`${this.apiBaseUrl}/client-space`);
  }

  requestClientOwnerLink(): Observable<ApiEnvelope<ClientOwnerLinkStatus>> {
    return this.http.post<ApiEnvelope<ClientOwnerLinkStatus>>(`${this.apiBaseUrl}/client/owner-link-requests`, {});
  }

  getClientOwnerLinkStatus(): Observable<ApiEnvelope<ClientOwnerLinkStatus>> {
    return this.http.get<ApiEnvelope<ClientOwnerLinkStatus>>(`${this.apiBaseUrl}/client/owner-link-status`);
  }

  listClientPets(): Observable<ApiEnvelope<ClientPetProfile[]>> {
    return this.http.get<ApiEnvelope<ClientPetProfile[]>>(`${this.apiBaseUrl}/client/pets`);
  }

  listClientOwnerLinkRequests(status = 'PENDIENTE'): Observable<ApiEnvelope<ClientOwnerLinkRequest[]>> {
    return this.http.get<ApiEnvelope<ClientOwnerLinkRequest[]>>(
      `${this.apiBaseUrl}/owner-link-requests?status=${encodeURIComponent(status)}`,
    );
  }

  resolveClientOwnerLinkRequest(requestId: string, request: ClientOwnerLinkResolutionRequest): Observable<ApiEnvelope<ClientOwnerLinkRequest>> {
    return this.http.post<ApiEnvelope<ClientOwnerLinkRequest>>(
      `${this.apiBaseUrl}/owner-link-requests/${encodeURIComponent(requestId)}/resolution`, request,
    );
  }

  registerClient(tenantSlug: string, email: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.apiBaseUrl}/tenants/${encodeURIComponent(tenantSlug)}/client-registrations`, { email, password });
  }

  resendClientVerification(tenantSlug: string, email: string): Observable<void> {
    return this.http.post<void>(`${this.apiBaseUrl}/tenants/${encodeURIComponent(tenantSlug)}/client-verification-resends`, { email });
  }

  verifyEmail(token: string): Observable<ApiEnvelope<VerificationStatus>> {
    return this.http.post<ApiEnvelope<VerificationStatus>>(`${this.apiBaseUrl}/email-verifications`, { token });
  }

  requestClientPasswordRecovery(tenantSlug: string, email: string): Observable<void> {
    return this.http.post<void>(`${this.apiBaseUrl}/auth/password-recovery-requests`, {
      accountType: 'CLIENT',
      tenantSlug,
      email,
    });
  }

  resetPassword(token: string, password: string): Observable<void> {
    return this.http.post<void>(`${this.apiBaseUrl}/auth/password-recoveries`, { token, password });
  }

  listOwners(query?: string): Observable<ApiEnvelope<OwnerProfile[]>> {
    const suffix = query?.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    return this.http.get<ApiEnvelope<OwnerProfile[]>>(`${this.apiBaseUrl}/owners${suffix}`);
  }

  createOwner(request: OwnerRequest): Observable<ApiEnvelope<OwnerProfile>> {
    return this.http.post<ApiEnvelope<OwnerProfile>>(`${this.apiBaseUrl}/owners`, request);
  }

  createPet(ownerId: string, request: PetRequest): Observable<ApiEnvelope<PetProfile>> {
    return this.http.post<ApiEnvelope<PetProfile>>(`${this.apiBaseUrl}/owners/${encodeURIComponent(ownerId)}/pets`, request);
  }

  listPetsByOwner(ownerId: string): Observable<ApiEnvelope<PetProfile[]>> {
    return this.http.get<ApiEnvelope<PetProfile[]>>(`${this.apiBaseUrl}/owners/${encodeURIComponent(ownerId)}/pets`);
  }

  getPet(petId: string): Observable<ApiEnvelope<PetProfile>> {
    return this.http.get<ApiEnvelope<PetProfile>>(`${this.apiBaseUrl}/pets/${encodeURIComponent(petId)}`);
  }

  listOwnershipPeriods(petId: string): Observable<ApiEnvelope<OwnershipPeriod[]>> {
    return this.http.get<ApiEnvelope<OwnershipPeriod[]>>(`${this.apiBaseUrl}/pets/${encodeURIComponent(petId)}/ownership-periods`);
  }

  transferPetOwnership(petId: string, request: OwnershipTransferRequest): Observable<ApiEnvelope<PetProfile>> {
    return this.http.post<ApiEnvelope<PetProfile>>(
      `${this.apiBaseUrl}/pets/${encodeURIComponent(petId)}/owner-transfers`,
      request,
    );
  }

  listClinicalEpisodes(petId: string): Observable<ApiEnvelope<ClinicalEpisode[]>> {
    return this.http.get<ApiEnvelope<ClinicalEpisode[]>>(`${this.apiBaseUrl}/pets/${encodeURIComponent(petId)}/clinical-episodes`);
  }

  createClinicalEpisode(petId: string, request: ClinicalEpisodeRequest): Observable<ApiEnvelope<ClinicalEpisode>> {
    return this.http.post<ApiEnvelope<ClinicalEpisode>>(`${this.apiBaseUrl}/pets/${encodeURIComponent(petId)}/clinical-episodes`, request);
  }

  finalizeClinicalEpisode(episodeId: string, request: ClinicalEpisodeRequest): Observable<ApiEnvelope<ClinicalEpisode>> {
    return this.http.post<ApiEnvelope<ClinicalEpisode>>(
      `${this.apiBaseUrl}/clinical-episodes/${encodeURIComponent(episodeId)}/finalization`,
      request,
    );
  }

  listVaccinations(petId: string): Observable<ApiEnvelope<VaccinationRecord[]>> {
    return this.http.get<ApiEnvelope<VaccinationRecord[]>>(`${this.apiBaseUrl}/pets/${encodeURIComponent(petId)}/vaccinations`);
  }

  recordVaccination(petId: string, request: VaccinationRequest): Observable<ApiEnvelope<VaccinationRecord>> {
    return this.http.post<ApiEnvelope<VaccinationRecord>>(
      `${this.apiBaseUrl}/pets/${encodeURIComponent(petId)}/vaccinations`, request,
    );
  }

  listClinicalPrescriptions(episodeId: string): Observable<ApiEnvelope<ClinicalPrescription[]>> {
    return this.http.get<ApiEnvelope<ClinicalPrescription[]>>(
      `${this.apiBaseUrl}/clinical-episodes/${encodeURIComponent(episodeId)}/prescriptions`,
    );
  }

  recordClinicalPrescription(episodeId: string, request: PrescriptionRequest): Observable<ApiEnvelope<ClinicalPrescription>> {
    return this.http.post<ApiEnvelope<ClinicalPrescription>>(
      `${this.apiBaseUrl}/clinical-episodes/${encodeURIComponent(episodeId)}/prescriptions`, request,
    );
  }

  listClinicalEpisodeAmendments(episodeId: string): Observable<ApiEnvelope<ClinicalEpisodeAmendment[]>> {
    return this.http.get<ApiEnvelope<ClinicalEpisodeAmendment[]>>(
      `${this.apiBaseUrl}/clinical-episodes/${encodeURIComponent(episodeId)}/amendments`,
    );
  }

  amendClinicalEpisode(episodeId: string, request: AmendmentRequest): Observable<ApiEnvelope<ClinicalEpisodeAmendment>> {
    return this.http.post<ApiEnvelope<ClinicalEpisodeAmendment>>(
      `${this.apiBaseUrl}/clinical-episodes/${encodeURIComponent(episodeId)}/amendments`, request,
    );
  }
}
