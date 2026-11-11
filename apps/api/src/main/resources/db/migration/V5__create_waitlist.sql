-- BarberFlow — Fase 2: lista de espera e ofertas de horario (spec, secoes 6, 7 e 9.2).
-- waitlist_entries: pedido de alerta por horario especifico; servico e guardado
-- para recriar a reserva quando a oferta for aceita.
-- slot_offers: oferta temporaria "uma pessoa por vez" com hold transacional;
-- o token nunca e salvo em claro (apenas SHA-256).

CREATE TABLE waitlist_entries (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL REFERENCES tenants (id),
    barber_id        uuid NOT NULL,
    customer_id      uuid NOT NULL,
    service_id       uuid NOT NULL,
    desired_start_at timestamptz NOT NULL,
    desired_end_at   timestamptz NOT NULL,
    status           text NOT NULL DEFAULT 'WAITING',
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    expires_at       timestamptz,

    CONSTRAINT fk_waitlist_barber
        FOREIGN KEY (tenant_id, barber_id) REFERENCES barbers (tenant_id, id),
    CONSTRAINT fk_waitlist_customer
        FOREIGN KEY (tenant_id, customer_id) REFERENCES customers (tenant_id, id),
    CONSTRAINT fk_waitlist_service
        FOREIGN KEY (tenant_id, service_id) REFERENCES services (tenant_id, id),
    CONSTRAINT ck_waitlist_range CHECK (desired_end_at > desired_start_at),
    CONSTRAINT ck_waitlist_status
        CHECK (status IN ('WAITING', 'OFFERED', 'FULFILLED', 'CANCELLED', 'EXPIRED'))
);

-- Indice da spec (secao 6): busca FIFO por profissional e horario
CREATE INDEX ix_waitlist_barber_time
    ON waitlist_entries (tenant_id, barber_id, desired_start_at, status);

CREATE TABLE slot_offers (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid NOT NULL REFERENCES tenants (id),
    waitlist_entry_id uuid NOT NULL REFERENCES waitlist_entries (id),
    starts_at         timestamptz NOT NULL,
    ends_at           timestamptz NOT NULL,
    token_hash        text NOT NULL,
    expires_at        timestamptz NOT NULL,
    status            text NOT NULL DEFAULT 'PENDING',
    version           bigint NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT uk_slot_offers_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_slot_offers_range CHECK (ends_at > starts_at),
    CONSTRAINT ck_slot_offers_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED'))
);

CREATE INDEX ix_slot_offers_entry
    ON slot_offers (tenant_id, waitlist_entry_id, status);
