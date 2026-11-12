-- BarberFlow — LGPD (spec, linha 223): consentimento registrado na coleta
-- (politica de privacidade com versao e origem) e anonimizacao como canal
-- de exclusao do titular. Reservas e auditoria sao preservados como
-- registro de negocio sem dados pessoais identificaveis.

ALTER TABLE customers ADD COLUMN consent_at timestamptz;
ALTER TABLE customers ADD COLUMN consent_source text;
ALTER TABLE customers ADD COLUMN privacy_policy_version text;
ALTER TABLE customers ADD COLUMN anonymized_at timestamptz;
