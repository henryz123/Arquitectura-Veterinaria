import { NgFor } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationStart, Router } from '@angular/router';
import { filter } from 'rxjs';
import { AppNotification, NotificationService } from './notification.service';

@Component({
  selector: 'app-notification-outlet',
  imports: [NgFor],
  template: `
    <section class="toast-region" aria-label="Notificaciones de la aplicacion">
      <article
        *ngFor="let notification of notifications.notifications()"
        class="toast"
        [class.toast-success]="notification.kind === 'success'"
        [class.toast-warning]="notification.kind === 'warning'"
        [class.toast-error]="notification.kind === 'error'"
        [attr.role]="notification.kind === 'error' ? 'alert' : 'status'"
        aria-atomic="true"
      >
        <div>
          <strong>{{ titleFor(notification) }}</strong>
          <p>{{ notification.message }}</p>
        </div>
        <button type="button" (click)="notifications.dismiss(notification.id)" [attr.aria-label]="'Cerrar notificacion: ' + notification.message">Cerrar</button>
      </article>
    </section>
  `,
  styleUrl: './notification-outlet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationOutletComponent {
  readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly clearOnNavigation = this.router.events.pipe(
    filter((event): event is NavigationStart => event instanceof NavigationStart),
    takeUntilDestroyed(this.destroyRef),
  ).subscribe(() => this.notifications.clear());

  titleFor(notification: AppNotification): string {
    return notification.kind === 'success' ? 'Operación completada'
      : notification.kind === 'warning' ? 'Revisa la información'
        : 'No fue posible completar la operación';
  }
}
