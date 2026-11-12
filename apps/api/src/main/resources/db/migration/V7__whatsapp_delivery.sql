-- BarberFlow — Fase 4: worker do WhatsApp (spec, linha 231).
-- Persiste o ID da mensagem aceita pela Cloud API (webhook de status
-- correlaciona a entrega) e o ultimo erro para diagnostico na outbox.

ALTER TABLE notification_outbox ADD COLUMN message_id text;
ALTER TABLE notification_outbox ADD COLUMN last_error text;
