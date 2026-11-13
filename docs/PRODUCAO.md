# BarberFlow — Checklist de Produção (Fase 5, piloto)

Preparação para o piloto com **uma barbearia real** (spec, seções 11, 14 e 16).
Este documento é o roteiro do operador; nada aqui roda deploy automático.

## 1. Pré-requisitos de infraestrutura

- [ ] Domínio da barbearia com **TLS/HTTPS** (certificado válido) em todo o tráfego —
      API atrás de reverse proxy com `X-Forwarded-Proto`/`X-Forwarded-Host`
      (a API usa `server.forward-headers-strategy: framework` no perfil `prod`)
- [ ] Domínios: frontend (reservas) e API em origens distintas ou same-origin via proxy
- [ ] PostgreSQL **gerenciado/privado** (não o container de dev) com rede isolada
- [ ] **Backups automáticos** do banco (diário, criptografados) e **teste de restauração
      executado** (spec, linha 225 — backup sem teste de restauração não é backup)
- [ ] Secret manager (ou serviço de env criptografado) para as variáveis abaixo;
      **nenhum segredo no Git, no compose de produção ou em imagem**

## 2. Variáveis obrigatórias (`SPRING_PROFILES_ACTIVE=prod`)

O perfil `prod` (`application-prod.yml`) **falha a subida** se algo essencial faltar:

| Variável | Regra |
|---|---|
| `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | banco gerenciado |
| `AUTH_JWT_SECRET` | **sem default no prod** — mínimo 32 bytes, do secret manager |
| `WHATSAPP_ENABLED` | decisão explícita do operador (`true`/`false`) |
| `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN` | obrigatórias quando `WHATSAPP_ENABLED=true`; número real da Meta |
| `BARBERSHOP_DEFAULT_TENANT_SLUG`, `BARBERSHOP_DEFAULT_TENANT_NAME` | identidade da barbearia piloto |
| `PRIVACY_RETENTION_ENABLED` | padrão `true` no prod (retenção LGPD) |
| `REMINDERS_ENABLED` | padrão `true` no prod (lembrete 24h via outbox; `REMINDERS_WINDOW_HOURS` ajusta a janela) |
| `APPOINTMENT_EXPIRY_ENABLED` | padrão `true` no prod (reserva PENDING a mais de `APPOINTMENT_EXPIRY_PENDING_TTL_HOURS` — padrão 24 — é cancelada pelo job, liberando o slot e a lista de espera) |
| `NEXT_PUBLIC_API_BASE_URL` (web) | URL pública da API |
| `NEXT_PUBLIC_DEFAULT_SLUG` (web) | slug da barbearia piloto |

Migrações (Flyway) rodam na subida da API e são **retrocompatíveis** entre
versões — deploy e migração controlados separadamente quando possível.

## 3. Hardening aplicado

- [x] Headers de segurança na **API**: CSP (`default-src 'none'`), `nosniff`,
      `X-Frame-Options: DENY`, referrer-policy, HSTS e no-cache em toda resposta
- [x] Headers de segurança no **web** (CSP com `connect-src` da API, Permissions-Policy)
- [x] Rate limit por IP (janela de 60s; limites por variável de ambiente)
- [x] Webhook da Meta com assinatura `X-Hub-Signature-256` verificada e dedup
- [x] LGPD: consentimento registrado na coleta, política em `/privacidade`,
      acesso/exclusão do titular pelo link seguro, retenção configurável
- [ ] Revisar limites do rate limit para o tráfego real esperado
- [ ] Logs centralizados (JSON) sem telefone completo nem tokens (spec, linha 292)

## 4. Observabilidade

- [ ] Prometheus exposto em `/actuator/prometheus` — coletar e dashboards mínimos:
      erro 5xx, latência p95, tamanho/idade da outbox, falhas do WhatsApp
- [ ] Métricas de negócio (spec, linha 293) no mesmo scrape:
      `barberflow_appointments_total{outcome="created|cancelled|conflict"}` (reservas),
      `barberflow_waitlist_offers_total`, `barberflow_waitlist_offers_accepted_total`
      e `barberflow_waitlist_offers_expired_total` (funil da fila de espera),
      `barberflow_whatsapp_delivery_failed_total` (falhas de entrega),
      `barberflow_outbox_pending` e `barberflow_outbox_oldest_pending_seconds` (saúde da outbox)
- [ ] Alertas: API indisponível, banco sem conexão, fila atrasada, falha repetida
      de entrega (spec, linha 294)

## 5. Antes de abrir ao público

1. `mvn verify` verde + build do web verde na versão a publicar
2. Migrations aplicadas e validadas no banco de produção
3. Bootstrap do **primeiro gestor** com credencial forte (senha única, trocada após o 1º acesso)
4. Templates do WhatsApp aprovados pela Meta (se `WHATSAPP_ENABLED=true`);
   webhook assinado com `hub.verify_token` correto
5. Smoke test: `BASE_URL=... WEB_URL=... ./scripts/smoke.sh`
6. Teste E2E do fluxo completo (reservar, remarcar, cancelar, avise-me):
   `BASE_URL=... MANAGER_EMAIL=... MANAGER_PASSWORD=... ./scripts/e2e.sh`
7. CI roda a cada push (GitHub Actions: `mvn verify` com PostgreSQL real
   e `next build` do web) — veja a aba Actions do repositorio
6. Plano de **rollback**: imagem anterior disponível; migrations retrocompatíveis

## 6. Piloto

- [ ] Uma barbearia real, horários e clientes de teste claramente marcados
- [ ] Coleta de feedback registrada (UX, agenda, fila, WhatsApp)
- [ ] Métricas de negócio acompanhadas: reservas criadas/canceladas, conflito,
      conversão da lista de espera (spec, linha 293)
