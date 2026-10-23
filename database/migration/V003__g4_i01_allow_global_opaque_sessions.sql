-- V001 sessions were tenant-only. B01 adds the separate SaaS global context,
-- whose opaque sessions intentionally have no tenant_id and are constrained by
-- account_type = 'SAAS' plus the RLS policy introduced in V002.
alter table identity_auth.user_session
    alter column tenant_id drop not null;
