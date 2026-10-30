import { ACCESS_TENANTS, DEFAULT_ACCESS_TENANT, resolveAccessTenant } from './access-tenant';

describe('access tenant configuration', () => {
  it('exposes only the two approved demonstration organizations', () => {
    expect(ACCESS_TENANTS.map((tenant) => tenant.slug)).toEqual([
      'veterinaria-aurora',
      'veterinaria-bosque',
    ]);
  });

  it('keeps an unknown route in the safe Aurora visual fallback without discovering tenants', () => {
    expect(resolveAccessTenant('otro-tenant')).toBe(DEFAULT_ACCESS_TENANT);
  });
});
