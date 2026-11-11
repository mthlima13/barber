-- BarberFlow — migração inicial (Fase 0)
-- Fundação: tabela de tenants. Demais tabelas chegam por migrações versionadas
-- nas fases seguintes (Fase 1: usuários, memberships, barbeiros, serviços...).

CREATE TABLE tenants (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       text NOT NULL,
    name       text NOT NULL,
    timezone   text NOT NULL,          -- fuso IANA, ex.: 'America/Sao_Paulo'
    status     text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_tenants_timezone CHECK (timezone ~ '^[A-Za-z0-9_+\-/]+$')
);

CREATE UNIQUE INDEX uk_tenants_slug ON tenants (slug);
