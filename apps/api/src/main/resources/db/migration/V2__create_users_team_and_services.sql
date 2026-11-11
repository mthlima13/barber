-- BarberFlow — Fase 1: equipe e servicos (spec, secao 6)
-- Tabelas: users, tenant_memberships, barbers, services, barber_services.
-- Chaves estrangeiras compostas garantem que referencias cruzadas
-- compartilhem o mesmo tenant (spec, secao 6, "Restrições e índices").

CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name          text NOT NULL,
    email         text NOT NULL,
    password_hash text NOT NULL,
    status        text NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE tenant_memberships (
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    user_id    uuid NOT NULL REFERENCES users (id),
    role       text NOT NULL,
    active     boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_tenant_memberships PRIMARY KEY (tenant_id, user_id),
    CONSTRAINT ck_tenant_memberships_role CHECK (role IN ('MANAGER', 'BARBER'))
);

CREATE TABLE barbers (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid NOT NULL REFERENCES tenants (id),
    user_id      uuid REFERENCES users (id),
    display_name text NOT NULL,
    active       boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),

    -- Necessario para FK composta em barber_services
    CONSTRAINT uk_barbers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_barbers_tenant_user UNIQUE (tenant_id, user_id)
);

CREATE TABLE services (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL REFERENCES tenants (id),
    name             text NOT NULL,
    description      text,
    duration_minutes integer NOT NULL,
    price_cents      bigint NOT NULL,
    active           boolean NOT NULL DEFAULT true,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),

    -- Necessario para FK composta em barber_services
    CONSTRAINT uk_services_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_services_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_services_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_services_price CHECK (price_cents >= 0)
);

CREATE TABLE barber_services (
    tenant_id  uuid NOT NULL,
    barber_id  uuid NOT NULL,
    service_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_barber_services PRIMARY KEY (tenant_id, barber_id, service_id),
    CONSTRAINT fk_barber_services_barber
        FOREIGN KEY (tenant_id, barber_id) REFERENCES barbers (tenant_id, id),
    CONSTRAINT fk_barber_services_service
        FOREIGN KEY (tenant_id, service_id) REFERENCES services (tenant_id, id)
);

CREATE INDEX ix_services_tenant_active ON services (tenant_id, active);
CREATE INDEX ix_barbers_tenant_active ON barbers (tenant_id, active);
