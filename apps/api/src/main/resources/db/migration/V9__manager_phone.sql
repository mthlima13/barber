-- WhatsApp do gestor (Fase 4): destino das notificacoes de negocio
-- (reserva nova, inscricao na fila). Nulo = notificacao desligada.
alter table tenants add column manager_phone varchar(20);
