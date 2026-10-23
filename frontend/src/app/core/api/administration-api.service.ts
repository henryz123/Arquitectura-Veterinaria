import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiEnvelope, StaffAccount, StaffInvitation, StaffRole } from './api.models';

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
}
