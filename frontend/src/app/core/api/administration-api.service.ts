import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AdministrativeRecovery, ApiEnvelope, ClientSpace, ClinicalEpisode, ClinicalEpisodeRequest, Organization, OwnerProfile, OwnerRequest, PetProfile, PetRequest, RucReview, StaffAccount, StaffInvitation, StaffRole, VerificationStatus } from './api.models';

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
}
