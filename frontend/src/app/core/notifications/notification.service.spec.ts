import { TestBed } from '@angular/core/testing';
import { afterEach, vi } from 'vitest';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    vi.useFakeTimers();
    service = TestBed.inject(NotificationService);
  });

  afterEach(() => vi.useRealTimers());

  it('coalesces duplicate messages and replaces resolved non-error feedback', () => {
    service.success('Mascota registrada correctamente.');
    service.success('Mascota registrada correctamente.');
    service.warning('Completa los campos señalados.');
    service.error('No fue posible conectar con el servidor.');

    expect(service.notifications().map(({ kind, message }) => ({ kind, message }))).toEqual([
      { kind: 'warning', message: 'Completa los campos señalados.' },
      { kind: 'error', message: 'No fue posible conectar con el servidor.' },
    ]);
  });

  it('removes the notification selected by the user', () => {
    service.warning('Completa los campos señalados.');
    const [notification] = service.notifications();

    service.dismiss(notification.id);

    expect(service.notifications()).toEqual([]);
  });

  it('clears stale messages when the user changes sections', () => {
    service.success('Información actualizada.');
    service.clear();

    expect(service.notifications()).toEqual([]);
  });
});
