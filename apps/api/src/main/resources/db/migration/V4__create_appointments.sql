-- BarberFlow — Fase 2: agendamentos (spec, secoes 6, 7 e 8).
-- customers: dados minimos do cliente (telefone normalizado E.164).
-- appointments: intervalo semiaberto [starts_at, ends_at) com snapshot de
-- preco/duracao e versionamento otimista. A restricao de exclusao impede
-- sobreposicao de agendamentos ativos do mesmo profissional.

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE customers (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    name        text NOT NULL,
    phone_e164  text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    -- Necessario para FK composta em appointments
    CONSTRAINT uk_customers_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_customers_tenant_phone UNIQUE (tenant_id, phone_e164)
);

CREATE TABLE appointments (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 uuid NOT NULL REFERENCES tenants (id),
    barber_id                 uuid NOT NULL,
    customer_id               uuid NOT NULL,
    service_id                uuid NOT NULL,
    starts_at                 timestamptz NOT NULL,
    ends_at                   timestamptz NOT NULL,
    status                    text NOT NULL DEFAULT 'PENDING',
    price_cents_snapshot      bigint NOT NULL,
    duration_minutes_snapshot integer NOT NULL,
    idempotency_key           text,
    created_at                timestamptz NOT NULL DEFAULT now(),
    updated_at                timestamptz NOT NULL DEFAULT now(),
    version                   bigint NOT NULL DEFAULT 0,

    CONSTRAINT fk_appointments_barber
        FOREIGN KEY (tenant_id, barber_id) REFERENCES barbers (tenant_id, id),
    CONSTRAINT fk_appointments_customer
        FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    CONSTRAINT fk_appointments_service
        FOREIGN KEY (tenant_id, service_id) REFERENCES services (tenant_id, id),
    CONSTRAINT ck_appointments_range CHECK (ends_at > starts_at),
    CONSTRAINT ck_appointments_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_appointments_price CHECK (price_cents_snapshot >= 0),
    CONSTRAINT ck_appointments_duration CHECK (duration_minutes_snapshot > 0)
);

CREATE INDEX ix_appointments_barber_time
    ON appointments (tenant_id, barber_id, starts_at);

-- Idempotencia de mutacoes criticas (spec, secao 10)
CREATE UNIQUE INDEX ux_appointments_tenant_idempotency
    ON appointments (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Impede sobreposicao de agendamentos ativos do mesmo profissional
-- (spec, secao 6; estados PENDING e CONFIRMED ocupam o intervalo)
ALTER TABLE appointments
    ADD CONSTRAINT no_overlapping_active_appointments
    EXCLUDE USING gist (
        tenant_id WITH =,
        barber_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    )
    WHERE (status IN ('PENDING', 'CONFIRMED'));
