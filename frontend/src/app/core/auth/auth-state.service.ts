import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ApiEnvelope, LoginResult, SessionContext, TenantProfile } from '../api/api.models';

interface LoginRequest {
  email: string;
  password: string;
}

@Injectable({ providedIn: 'root' })
export class AuthStateService {
  private readonly apiBaseUrl = '/api/v1';
  private readonly http: HttpClient;
  readonly context = signal<SessionContext | null>(null);
  readonly csrfToken = signal<string | null>(null);

  constructor(http: HttpClient) {
    this.http = http;
  }

  loginStaff(tenantSlug: string, request: LoginRequest): Observable<ApiEnvelope<LoginResult>> {
    return this.http
      .post<ApiEnvelope<LoginResult>>(
        `${this.apiBaseUrl}/auth/tenants/${encodeURIComponent(tenantSlug)}/staff-sessions`,
        request,
        { withCredentials: true },
      )
      .pipe(
        tap((response) => {
          this.context.set(response.data.context);
          this.csrfToken.set(response.data.csrfToken);
        }),
      );
  }

  restoreContext(): Observable<ApiEnvelope<SessionContext>> {
    return this.http
      .get<ApiEnvelope<SessionContext>>(`${this.apiBaseUrl}/session-context`, {
        withCredentials: true,
      })
      .pipe(tap((response) => this.context.set(response.data)));
  }

  loadOwnTenantProfile(tenantSlug: string): Observable<ApiEnvelope<TenantProfile>> {
    return this.http.get<ApiEnvelope<TenantProfile>>(
      `${this.apiBaseUrl}/tenant-profile/${encodeURIComponent(tenantSlug)}`,
      { withCredentials: true },
    );
  }

  logout(): Observable<void> {
    return this.http
      .delete<void>(`${this.apiBaseUrl}/auth/session`, { withCredentials: true })
      .pipe(tap(() => this.clear()));
  }

  clear(): void {
    this.context.set(null);
    this.csrfToken.set(null);
  }
}
