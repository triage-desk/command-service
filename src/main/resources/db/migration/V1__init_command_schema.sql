-- ============================================================================
-- V1: Initial Schema for triage-command-service
-- Multi-tenant shared schema with PostgreSQL Row-Level Security (RLS)
-- ============================================================================

-- 1. Extensions, Functions, Domains & Types
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE DOMAIN email_address AS TEXT
    check ( VALUE ~* '^[A-Za-z0-9._+%-]+@[A-Za-z0-9.-]+[.][A-Za-z]+$' );

CREATE TYPE user_role AS ENUM ('TENANT_OWNER', 'TENANT_ADMIN', 'SUPPORT_AGENT', 'CUSTOMER_USER');

CREATE TYPE ticket_priority AS ENUM ('NONE', 'LOW', 'MEDIUM', 'HIGH', 'URGENT');

CREATE TYPE ticket_status AS ENUM ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED');

CREATE TYPE outbox_event_status AS ENUM ('PENDING', 'PUBLISHED', 'FAILED');

-- 2. Tenants Table (Global lookup, no RLS)
CREATE TABLE tenants
(
    id         UUID                 DEFAULT gen_random_uuid(),
    name       TEXT        NOT NULL,
    slug       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT tenants_pkey PRIMARY KEY (id),
    CONSTRAINT tenants_name_check CHECK ( length(trim(name)) >= 1 ),
    CONSTRAINT tenants_slug_key UNIQUE (slug),
    CONSTRAINT tenants_slug_check CHECK ( length(slug) <= 100 )
);

CREATE TRIGGER trg_tenants_updated_at
    BEFORE UPDATE
    ON tenants
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- 3. Users Table (Tenant-scoped)
CREATE TABLE users
(
    id          UUID                   DEFAULT gen_random_uuid(),
    tenant_id   UUID          NOT NULL,
    keycloak_id TEXT          NOT NULL,
    email       email_address NOT NULL,
    first_name  TEXT          NOT NULL,
    last_name   TEXT          NOT NULL,
    role        user_role     NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT users_keycloak_id_key UNIQUE (keycloak_id),
    CONSTRAINT users_first_name_check CHECK ( length(trim(first_name)) >= 1 ),
    CONSTRAINT users_last_name_check CHECK ( length(trim(last_name)) >= 1 ),
    CONSTRAINT users_tenant_id_email_key UNIQUE (tenant_id, email)
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- 4. Tickets Table (Tenant-scoped Write Model)
CREATE TABLE tickets
(
    id                 UUID                     DEFAULT gen_random_uuid(),
    tenant_id          UUID            NOT NULL,
    customer_id        UUID            NOT NULL,
    customer_email     email_address   NOT NULL,
    subject            TEXT            NOT NULL,
    description        TEXT            NOT NULL,
    priority           ticket_priority NOT NULL DEFAULT 'NONE',
    status             ticket_status   NOT NULL DEFAULT 'OPEN',
    assigned_to        UUID,
    assigned_at        TIMESTAMPTZ,
    resolved_by        UUID,
    resolved_at        TIMESTAMPTZ,
    resolution_summary TEXT,
    sla_due_at         TIMESTAMPTZ     NOT NULL,
    version            BIGINT          NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT tickets_pkey PRIMARY KEY (id),
    CONSTRAINT tickets_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
    CONSTRAINT tickets_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT tickets_assigned_to_fkey FOREIGN KEY (assigned_to) REFERENCES users (id),
    CONSTRAINT tickets_resolved_by_fkey FOREIGN KEY (resolved_by) REFERENCES users (id)
);

CREATE TRIGGER trg_tickets_updated_at
    BEFORE UPDATE
    ON tickets
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Indexes for fast query and filtering
CREATE INDEX idx_tickets_tenant_status ON tickets (tenant_id, status);
CREATE INDEX idx_tickets_tenant_assigned_to ON tickets (tenant_id, assigned_to);
CREATE INDEX idx_tickets_tenant_created_at ON tickets (tenant_id, created_at DESC);

-- 5. Outbox Events Table
CREATE TABLE outbox_events
(
    id             UUID                         DEFAULT gen_random_uuid(),
    tenant_id      UUID                NOT NULL,
    aggregate_type TEXT                NOT NULL,
    aggregate_id   UUID                NOT NULL,
    event_type     TEXT                NOT NULL,
    payload        JSONB               NOT NULL,
    status         outbox_event_status NOT NULL DEFAULT 'PENDING',
    retry_count    INT                 NOT NULL DEFAULT 0,
    error_message  TEXT,
    created_at     TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ,

    CONSTRAINT outbox_events_pkey PRIMARY KEY (id),
    CONSTRAINT outbox_events_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- Index for lock-free outbox poller (SELECT ... FOR UPDATE SKIP LOCKED)
CREATE INDEX idx_outbox_pending_events ON outbox_events (status, created_at ASC)
    WHERE status = 'PENDING';

-- 6. Row-Level Security (RLS) Setup
-- Multi-tenant isolation enforced at the database layer via 'app.current_tenant'

-- Enable and FORCE RLS on tenant-scoped tables
ALTER TABLE users
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE users
    FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_users ON users
    AS PERMISSIVE
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE tickets
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets
    FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_tickets ON tickets
    AS PERMISSIVE
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

ALTER TABLE outbox_events
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_events
    FORCE ROW LEVEL SECURITY;

-- Allow insert/select for current tenant, or allow background worker if app.current_tenant is not set / 'all'
CREATE POLICY tenant_isolation_outbox ON outbox_events
    AS PERMISSIVE
    FOR ALL
    USING (
    current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
        OR current_setting('app.current_tenant', true) = 'system'
        OR tenant_id = current_setting('app.current_tenant', true)::uuid
    )
    WITH CHECK (
    current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
        OR current_setting('app.current_tenant', true) = 'system'
        OR tenant_id = current_setting('app.current_tenant', true)::uuid
    );
