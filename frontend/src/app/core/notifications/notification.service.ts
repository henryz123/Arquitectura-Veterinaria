import { Injectable, signal } from '@angular/core';

export type NotificationKind = 'success' | 'warning' | 'error';

export interface AppNotification {
  id: number;
  kind: NotificationKind;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 0;

  readonly notifications = signal<readonly AppNotification[]>([]);

  success(message: string): void {
    this.publish('success', message, 4_500);
  }

  warning(message: string): void {
    this.publish('warning', message, 6_000);
  }

  error(message: string): void {
    this.publish('error', message, 8_000);
  }

  dismiss(id: number): void {
    this.notifications.update((items) => items.filter((item) => item.id !== id));
  }

  clear(): void {
    this.notifications.set([]);
  }

  private publish(kind: NotificationKind, message: string, durationMs: number): void {
    const normalized = message.trim();
    if (!normalized) {
      return;
    }

    this.notifications.update((items) => {
      const relevantItems = kind === 'error'
        ? items
        : items.filter((item) => item.kind === 'error');
      const withoutDuplicate = relevantItems.filter((item) => item.kind !== kind || item.message !== normalized);
      return [...withoutDuplicate.slice(-1), { id: ++this.nextId, kind, message: normalized }];
    });

    const id = this.nextId;
    window.setTimeout(() => this.dismiss(id), durationMs);
  }
}
