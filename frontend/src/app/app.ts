import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NotificationOutletComponent } from './core/notifications/notification-outlet.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NotificationOutletComponent],
  template: '<router-outlet /><app-notification-outlet />',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
