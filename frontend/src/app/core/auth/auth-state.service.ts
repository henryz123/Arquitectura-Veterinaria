import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { Observable, map, switchMap, tap } from 'rxjs';
import { ApiEnvelope, CsrfToken, LoginResult, SessionContext, TenantProfile } from '../api/api.models';

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

  loginSaas(request: LoginRequest): Observable<ApiEnvelope<LoginResult>> {
    return this.http
      .post<ApiEnvelope<LoginResult>>(`${this.apiBaseUrl}/auth/saas-sessions`, request, { withCredentials: true })
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
      .pipe(
        switchMap((sessionContext) => this.http
          .post<ApiEnvelope<CsrfToken>>(`${this.apiBaseUrl}/auth/session-csrf-token`, null, {
            headers: new HttpHeaders({ 'X-AV-CSRF-Bootstrap': '1' }),
            withCredentials: true,
          })
          .pipe(map((csrf) => ({ sessionContext, csrfToken: csrf.data.csrfToken })))),
        tap(({ sessionContext, csrfToken }) => {
          this.context.set(sessionContext.data);
          this.csrfToken.set(csrfToken);
        }),
        map(({ sessionContext }) => sessionContext),
      );
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
