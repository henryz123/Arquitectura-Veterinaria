import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ACCESS_TENANTS } from './access-tenant';

@Component({
  selector: 'app-tenant-selector-page',
  imports: [RouterLink],
  templateUrl: './tenant-selector-page.component.html',
  styleUrl: './tenant-selector-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TenantSelectorPageComponent {
  readonly tenants = ACCESS_TENANTS;
}
