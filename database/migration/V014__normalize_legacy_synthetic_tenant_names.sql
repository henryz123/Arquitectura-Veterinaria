-- B01 presentation-data correction: normalize only the two historical demo
-- tenant names that were seeded with a literal Unicode escape sequence.
-- This is transactional, idempotent and does not change schema or privileges.

update tenancy.tenant
   set commercial_name = replace(commercial_name, E'\\u00e9', 'é')
 where slug in ('veterinaria-aurora', 'veterinaria-bosque')
   and commercial_name like E'%\\u00e9%';
