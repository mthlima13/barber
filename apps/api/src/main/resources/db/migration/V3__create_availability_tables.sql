-- BarberFlow — Fase 1: expediente e excecoes de agenda (spec, secoes 6 e 7).
-- business_hours: expediente semanal por profissional.
-- schedule_exceptions: folgas e bloqueios pontuais (intervalo semiaberto [start,end)).

CREATE TABLE business_hours (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    barber_id   uuid NOT NULL,
    weekday     smallint NOT NULL,
    start_local time NOT NULL,
    end_local   time NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),

    -- 0=domingo ... 6=sabado (mesma convensao do DOW do PostgreSQL)
    CONSTRAINT ck_business_hours_weekday CHECK (weekday BETWEEN 0 AND 6),
    CONSTRAINT ck_business_hours_window CHECK (start_local < end_local),
    -- MVP: uma janela de expediente por dia da semana e profissional
    CONSTRAINT uk_business_hours_barber_weekday UNIQUE (tenant_id, barber_id, weekday),
    CONSTRAINT fk_business_hours_barber
        FOREIGN KEY (tenant_id, barber_id) REFERENCES barbers (tenant_id, id)
);

CREATE INDEX ix_business_hours_barber ON business_hours (tenant_id, barber_id);

CREATE TABLE schedule_exceptions (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  uuid NOT NULL REFERENCES tenants (id),
    barber_id  uuid NOT NULL,
    start_at   timestamptz NOT NULL,
    end_at     timestamptz NOT NULL,
    kind       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_schedule_exceptions_range CHECK (start_at < end_at),
    CONSTRAINT ck_schedule_exceptions_kind CHECK (kind IN ('DAY_OFF', 'BLOCK')),
    CONSTRAINT fk_schedule_exceptions_barber
        FOREIGN KEY (tenant_id, barber_id) REFERENCES barbers (tenant_id, id)
);

CREATE INDEX ix_schedule_exceptions_barber_range
    ON schedule_exceptions (tenant_id, barber_id, start_at, end_at);
