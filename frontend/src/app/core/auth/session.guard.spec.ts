import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, UrlTree } from '@angular/router';
import { of } from 'rxjs';
import { AuthStateService } from './auth-state.service';
import { SessionContext } from '../api/api.models';
import { clientSessionGuard, saasSessionGuard, sessionGuard } from './session.guard';

describe('saasSessionGuard', () => {
  const context = signal<SessionContext | null>(null);
  const auth = { context, restoreContext: vi.fn(() => of({ data: null })), clear: vi.fn() };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthStateService, useValue: auth }],
    });
  });

  it('redirects a tenant STAFF session away from the SaaS console', () => {
    context.set({ accountType: 'STAFF', tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' }, roles: ['ADMIN_VETERINARIA'] });

    const result = TestBed.runInInjectionContext(() => saasSessionGuard({} as never, {} as never));

    expect(result).toBeInstanceOf(UrlTree);
    expect((result as UrlTree).toString()).toBe('/saas/login');
  });

  it('allows an authenticated ADMIN_SAAS session', () => {
    context.set({ accountType: 'SAAS', tenant: null, roles: ['ADMIN_SAAS'] });

    const result = TestBed.runInInjectionContext(() => saasSessionGuard({} as never, {} as never));

    expect(result).toBe(true);
  });

  it('allows a CLIENT session and denies a STAFF session for client space', () => {
    context.set({ accountType: 'CLIENT', tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' }, roles: [] });
    const allowed = TestBed.runInInjectionContext(() => clientSessionGuard({} as never, {} as never));
    expect(allowed).toBe(true);

    context.set({ accountType: 'STAFF', tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' }, roles: ['ADMIN_VETERINARIA'] });
    const denied = TestBed.runInInjectionContext(() => clientSessionGuard({} as never, {} as never));
    expect(denied).toBeInstanceOf(UrlTree);
    expect((denied as UrlTree).toString()).toBe('/client/login/veterinaria-aurora');
  });

  it('redirects a CLIENT session away from the STAFF workspace', () => {
    context.set({
      accountType: 'CLIENT',
      tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' },
      roles: [],
    });

    const denied = TestBed.runInInjectionContext(() => sessionGuard({} as never, {} as never));

    expect(denied).toBeInstanceOf(UrlTree);
    expect((denied as UrlTree).toString()).toBe('/login/veterinaria-aurora');
  });

  it('allows an authenticated STAFF session into the workspace', () => {
    context.set({
      accountType: 'STAFF',
      tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' },
      roles: ['ADMIN_VETERINARIA'],
    });

    const allowed = TestBed.runInInjectionContext(() => sessionGuard({} as never, {} as never));

    expect(allowed).toBe(true);
  });
});
