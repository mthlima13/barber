-- V10: status SKIPPED na outbox.
-- O worker suprime mensagens que perderam a razao de existir entre o
-- agendamento e a entrega (ex.: lembrete de reserva que o cliente
-- cancelou; oferta de inscricao que saiu da fila) — o evento fica
-- visivel no painel como SKIPPED em vez de ir para FAILED.

ALTER TABLE notification_outbox DROP CONSTRAINT ck_outbox_status;

ALTER TABLE notification_outbox
    ADD CONSTRAINT ck_outbox_status
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'));
