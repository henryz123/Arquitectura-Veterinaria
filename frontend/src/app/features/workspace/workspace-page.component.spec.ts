import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { AdministrationApiService } from '../../core/api/administration-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';
import { SessionContext } from '../../core/api/api.models';
import { WorkspacePageComponent } from './workspace-page.component';

describe('WorkspacePageComponent', () => {
  let fixture: ComponentFixture<WorkspacePageComponent>;
  let component: WorkspacePageComponent;
  const context = signal<SessionContext | null>({
    accountType: 'STAFF',
    tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' },
    roles: ['ADMIN_VETERINARIA'],
  });
  const administrationApi = {
    listStaff: vi.fn(() => of({ data: [] })),
    inviteStaff: vi.fn(() => throwError(() => new Error('network error'))),
    replaceStaffRoles: vi.fn(),
    deactivateStaff: vi.fn(),
  };
  const auth = {
    context,
    loadOwnTenantProfile: vi.fn(() => of({ data: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' } })),
    logout: vi.fn(() => of(void 0)),
  };

  beforeEach(() => {
    administrationApi.listStaff.mockClear();
    administrationApi.inviteStaff.mockClear();
    TestBed.configureTestingModule({
      imports: [WorkspacePageComponent],
      providers: [
        provideRouter([]),
        { provide: AuthStateService, useValue: auth },
        { provide: AdministrationApiService, useValue: administrationApi },
      ],
    });
    fixture = TestBed.createComponent(WorkspacePageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('re-enables the invitation form after a protected request fails', () => {
    component.invitationForm.setValue({ email: 'equipo@demo.invalid', roles: ['RECEPCION'] });

    component.inviteStaff();

    expect(administrationApi.inviteStaff).toHaveBeenCalledWith('equipo@demo.invalid', ['RECEPCION']);
    expect(component.actionPending()).toBe(false);
    expect(component.error()).toContain('No fue posible registrar la invitación');
  });
});
