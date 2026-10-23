import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AdministrativeRecovery, ApiEnvelope, ClientSpace, Organization, RucReview, StaffAccount, StaffInvitation, StaffRole } from './api.models';

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
}
