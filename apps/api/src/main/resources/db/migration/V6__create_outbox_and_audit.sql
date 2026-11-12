-- BarberFlow — Fase 2: outbox de notificacoes e auditoria (spec, secoes 4, 6 e 9).
-- notification_outbox: entrega assincrona confiavel (spec, linha 231: transacao
-- de negocio -> outbox -> worker -> WhatsApp). Eventos gravados na MESMA
-- transacao da operacao; worker e envio real entram na Fase 4.
-- notification_deliveries e notification_preferences entram na Fase 4,
-- junto com a integracao do WhatsApp.
-- audit_events: auditoria de acoes do gestor, sem dados sensistentes.

CREATE TABLE notification_outbox (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id),
    event_type      text NOT NULL,
    payload_json    text NOT NULL,
    status          text NOT NULL DEFAULT 'PENDING',
    attempt_count   integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    idempotency_key text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint NOT NULL DEFAULT 0,

    CONSTRAINT ck_outbox_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- Worker da Fase 4 busca eventos prontos em ordem
CREATE INDEX ix_outbox_worker
    ON notification_outbox (status, next_attempt_at);

CREATE INDEX ix_outbox_tenant_created
    ON notification_outbox (tenant_id, created_at DESC);

-- Idempotencia de eventos (spec, secao 10): mesmo evento nao se duplica
CREATE UNIQUE INDEX ux_outbox_tenant_idempotency
    ON notification_outbox (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE audit_events (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id),
    actor_id    uuid,
    action      text NOT NULL,
    entity_type text NOT NULL,
    entity_id   uuid,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_tenant_created
    ON audit_events (tenant_id, created_at DESC);
