export interface AccessTenant {
  readonly slug: 'veterinaria-aurora' | 'veterinaria-bosque';
  readonly name: string;
  readonly tagline: string;
  readonly accessMessage: string;
  readonly imagePath: string;
  readonly themeClass: 'access-theme-aurora' | 'access-theme-bosque';
}

export const ACCESS_TENANTS: readonly AccessTenant[] = [
  {
    slug: 'veterinaria-aurora',
    name: 'Veterinaria Aurora',
    tagline: 'Cuidamos lo que más importa',
    accessMessage: 'Atención cercana para cada etapa de su bienestar.',
    imagePath: '/access/aurora-retriever.webp',
    themeClass: 'access-theme-aurora',
  },
  {
    slug: 'veterinaria-bosque',
    name: 'Veterinaria Bosque',
    tagline: 'Salud animal, vida en armonía',
    accessMessage: 'Un espacio pensado para acompañar a cada familia.',
    imagePath: '/access/bosque-cat.webp',
    themeClass: 'access-theme-bosque',
  },
];

export const DEFAULT_ACCESS_TENANT = ACCESS_TENANTS[0];

export function resolveAccessTenant(slug: string): AccessTenant {
  return ACCESS_TENANTS.find((tenant) => tenant.slug === slug) ?? DEFAULT_ACCESS_TENANT;
}
