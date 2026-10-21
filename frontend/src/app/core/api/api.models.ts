export interface ApiEnvelope<T> {
  data: T;
}

export interface TenantProfile {
  slug: string;
  commercialName: string;
  status: 'ACTIVO' | 'SUSPENDIDO';
}

export interface SessionContext {
  accountType: 'STAFF' | 'CLIENT';
  tenant: TenantProfile;
  roles: string[];
}

export interface LoginResult {
  csrfToken: string;
  context: SessionContext;
}
