-- src/main/resources/db/migration/V2__create_tenant_sla_policies.sql

CREATE TABLE tenant_sla_policies
(
    id             UUID                     DEFAULT gen_random_uuid(),
    tenant_id      UUID            NOT NULL,
    priority       ticket_priority NOT NULL,
    duration_hours INT             NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT tenant_sla_policies_pkey PRIMARY KEY (id),
    CONSTRAINT tenant_sla_policies_tenant_fkey FOREIGN KEY (tenant_id)
        REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT uq_tenant_sla_priority UNIQUE (tenant_id, priority),
    CONSTRAINT chk_duration_positive CHECK (duration_hours > 0)
);

ALTER TABLE tenant_sla_policies
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_sla_policies
    FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_sla_policies ON tenant_sla_policies
    AS PERMISSIVE FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);