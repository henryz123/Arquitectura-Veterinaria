import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { resolveAccessTenant } from './access-tenant';

@Component({
  selector: 'app-access-brand-panel',
  templateUrl: './access-brand-panel.component.html',
  styleUrl: './access-brand-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessBrandPanelComponent {
  readonly tenantSlug = input.required<string>();
  readonly tenant = computed(() => resolveAccessTenant(this.tenantSlug()));
}
