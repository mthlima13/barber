# BarberFlow — Especificação técnica e infraestrutura

> Versão 1.0 · 10/11/2026 · Status: proposta arquitetural · Produto: sistema web de agendamentos de barbearia com lista de espera e notificações WhatsApp.

## 1. Visão do produto

Plataforma web responsiva que permite ao cliente consultar disponibilidade, agendar, reagendar, cancelar e solicitar aviso quando um horário ocupado for liberado. O barbeiro visualiza sua agenda e recebe avisos opcionais. A arquitetura será preparada para múltiplas barbearias (SaaS), mas o MVP pode ser lançado com uma só.

**Objetivos:** impedir reservas conflitantes; facilitar a gestão de profissionais e serviços; recuperar horários cancelados; manter rastreabilidade; respeitar privacidade e consentimento para mensagens.

**Fora do MVP:** pagamentos, marketplace, aplicativo nativo, programa de fidelidade, IA, relatórios financeiros complexos e microserviços.

## 2. Perfis e permissões

| Perfil | Acesso |
|---|---|
| Visitante/cliente | Consultar disponibilidade, reservar, gerenciar a própria reserva por link seguro, aderir/sair da lista de espera |
| Barbeiro (`BARBER`) | Consultar sua agenda, bloquear intervalos, atualizar status de seus atendimentos e optar por avisos |
| Gestor (`MANAGER`) | Administrar unidade, barbeiros, serviços, horários, cancelamentos e relatórios básicos |
| Administrador da plataforma (`PLATFORM_ADMIN`) | Administrar tenants e suporte autorizado, sem acesso irrestrito aos dados pessoais |

No MVP, o cliente pode reservar sem senha mediante verificação de telefone por OTP ou link de gerenciamento com token curto, expirável e de uso limitado. Nunca permitir alterações somente com um ID de agendamento.

## 3. Stack proposta

- **Frontend:** Next.js (App Router), React, TypeScript, Tailwind CSS, biblioteca de componentes acessíveis, React Hook Form + Zod, TanStack Query quando necessário.
- **Backend:** Java 21 LTS, Spring Boot 3.x compatível, Spring Web, Spring Security, Spring Validation, Spring Data JPA, Actuator, Micrometer.
- **Banco:** PostgreSQL 16+; Flyway para versionamento; UUIDs; armazenamento de timestamps em UTC.
- **Notificações:** integração oficial WhatsApp Business Platform (Cloud API) com templates aprovados e consentimento explícito; no MVP, worker de jobs em banco; RabbitMQ opcional em etapa posterior.
- **Infra:** Docker e Docker Compose no desenvolvimento; produção com serviços gerenciados (app/container, PostgreSQL, armazenamento de segredos e observabilidade). CI/CD via GitHub Actions.
- **Testes:** JUnit 5, Mockito, Testcontainers, Spring Boot Test, Playwright e testes de contrato de API.

Validar versões suportadas e licenças no início da implementação; não fixar versões sem checagem de compatibilidade.

## 4. Estrutura do monorepo

```text
barberflow/
├── apps/
│   ├── web/                      # Next.js: público + painéis
│   └── api/                      # Spring Boot: monólito modular
│       ├── src/main/java/com/barberflow/
│       │   ├── auth/
│       │   ├── tenant/
│       │   ├── staff/
│       │   ├── service/
│       │   ├── availability/
│       │   ├── appointment/
│       │   ├── waitlist/
│       │   ├── notification/
│       │   ├── audit/
│       │   └── shared/
│       ├── src/main/resources/db/migration/
│       └── src/test/
├── infra/
│   ├── compose.yaml
│   ├── monitoring/
│   └── deployment/
├── docs/
│   ├── api/openapi.yaml
│   ├── adr/
│   ├── diagrams/
│   ├── security.md
│   └── runbooks.md
├── .github/workflows/
├── .env.example
└── README.md
```

Dentro de cada módulo Java, separar `api` (controllers/DTOs), `application` (casos de uso), `domain` (regras) e `infrastructure` (persistência/provedores). Evitar dependências circulares.

## 5. Modelo multi-tenant

Cada barbearia possui `tenant_id` imutável. Toda entidade operacional e toda consulta administrativa devem estar vinculadas ao tenant autenticado. `tenant_id` não pode ser aceito como fonte de autoridade de um cabeçalho controlado pelo cliente. Resolver tenant por rota pública (`/b/{slug}`) para consultas públicas, e por associação validada na sessão/JWT para operações protegidas. Sempre verificar que o profissional pertence à barbearia.

Para o MVP, usar banco e schema compartilhados com `tenant_id` nas tabelas; testes de isolamento entre tenants obrigatórios. Row Level Security do PostgreSQL pode ser adicionada como defesa em profundidade, com política e contexto transacional testados; não substitui a autorização na API.

## 6. Modelo de dados

### Tabelas principais

| Tabela | Campos essenciais | Observações |
|---|---|---|
| `tenants` | `id`, `slug`, `name`, `timezone`, `status`, `created_at` | Fuso IANA, ex.: `America/Sao_Paulo` |
| `users` | `id`, `name`, `email`, `password_hash`, `status` | Apenas equipe/gestores; senha com Argon2id ou BCrypt |
| `tenant_memberships` | `tenant_id`, `user_id`, `role`, `active` | Vincula usuário e permissão |
| `barbers` | `id`, `tenant_id`, `user_id`, `display_name`, `active` | Profissionais |
| `services` | `id`, `tenant_id`, `name`, `duration_minutes`, `price_cents`, `active` | Duração e preço no momento da reserva devem ser congelados |
| `barber_services` | `tenant_id`, `barber_id`, `service_id` | Serviços habilitados por profissional |
| `customers` | `id`, `tenant_id`, `name`, `phone_e164`, `created_at` | Telefone normalizado; dados mínimos |
| `business_hours` | `id`, `tenant_id`, `barber_id`, `weekday`, `start_local`, `end_local` | Expediente semanal |
| `schedule_exceptions` | `id`, `tenant_id`, `barber_id`, `start_at`, `end_at`, `kind` | Folgas, bloqueios e exceções |
| `appointments` | `id`, `tenant_id`, `barber_id`, `customer_id`, `service_id`, `starts_at`, `ends_at`, `status`, `price_cents_snapshot`, `duration_minutes_snapshot`, `created_at`, `updated_at`, `version` | Intervalo `[início, fim)` |
| `waitlist_entries` | `id`, `tenant_id`, `barber_id`, `customer_id`, `desired_start_at`, `desired_end_at`, `status`, `created_at`, `expires_at` | Solicitação para horário específico; extensão para janelas futuras |
| `slot_offers` | `id`, `tenant_id`, `waitlist_entry_id`, `starts_at`, `ends_at`, `token_hash`, `expires_at`, `status` | Oferta temporária; token nunca salvo em claro |
| `notification_preferences` | `id`, `tenant_id`, `recipient_type`, `recipient_id`, `channel`, `event_type`, `enabled`, `consent_at`, `consent_source` | Preferências e consentimento |
| `notification_outbox` | `id`, `tenant_id`, `event_type`, `payload_json`, `status`, `attempt_count`, `next_attempt_at`, `idempotency_key` | Entrega assíncrona confiável |
| `notification_deliveries` | `id`, `tenant_id`, `outbox_id`, `provider_message_id`, `status`, `error_code`, `created_at` | Histórico técnico mínimo |
| `audit_events` | `id`, `tenant_id`, `actor_id`, `action`, `entity_type`, `entity_id`, `created_at` | Auditoria sem dados sensíveis desnecessários |

### Restrições e índices

- Índices em `appointments(tenant_id, barber_id, starts_at)` e `waitlist_entries(tenant_id, barber_id, desired_start_at, status)`.
- `UNIQUE (tenant_id, slug)` onde aplicável; `UNIQUE (tenant_id, barber_id, service_id)` em `barber_services`.
- Usar restrição de exclusão PostgreSQL para impedir sobreposição de agendamentos ativos do mesmo profissional, com `btree_gist` e `tstzrange(starts_at, ends_at, '[)')`; considerar estados `PENDING` e `CONFIRMED` como ocupantes, excluindo `CANCELLED` e `COMPLETED` conforme regra validada.
- `CHECK (ends_at > starts_at)` e preços/durações não negativos.
- Chaves estrangeiras compostas ou validações adicionais devem garantir que referências cruzadas compartilhem o mesmo tenant.
- Restrições equivalentes ou transações serializadas para ofertas temporárias, pois o agendamento e o bloqueio temporário não devem permitir dupla ocupação.

Exemplo conceitual de exclusão (ajustar nomes e estados na migração real):

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE appointments
ADD CONSTRAINT no_overlapping_active_appointments
EXCLUDE USING gist (
  tenant_id WITH =,
  barber_id WITH =,
  tstzrange(starts_at, ends_at, '[)') WITH &&
)
WHERE (status IN ('PENDING', 'CONFIRMED'));
```

## 7. Regras de disponibilidade

1. Cliente escolhe barbearia, serviço, profissional e data.
2. API calcula intervalos possíveis com base em expediente, exceções, duração do serviço, antecedência mínima, horizonte máximo e reservas existentes.
3. Horários devem ser calculados no fuso IANA da barbearia, convertidos para UTC para persistência. Tratar transições de horário de verão quando aplicável.
4. A interface mostra disponibilidade como **estimativa**; confirmação sempre revalida no servidor.
5. Reservas e bloqueios usam intervalo semiaberto `[start, end)`; atendimentos consecutivos podem compartilhar a fronteira.
6. A transação confirma somente se o intervalo estiver disponível; conflito retorna HTTP `409` com mensagem segura e horários alternativos.
7. Reagendamento deve liberar o intervalo anterior e reservar o novo de forma atômica; em falha, preservar o agendamento anterior.
8. Definir políticas configuráveis: cancelamento até X horas antes, antecedência mínima, intervalo entre atendimentos, duração e dias disponíveis.

## 8. Máquina de estados

**Agendamento:** `PENDING -> CONFIRMED -> COMPLETED`; `PENDING/CONFIRMED -> CANCELLED`; `CONFIRMED -> NO_SHOW` (após horário). Definir expiração de `PENDING` para evitar retenção indefinida. Não permitir concluir agendamento cancelado.

**Lista de espera:** `WAITING -> OFFERED -> CONVERTED`; `WAITING/OFFERED -> CANCELLED` ou `EXPIRED`; oferta vencida retorna a entrada a `WAITING` se ainda estiver válida.

**Oferta:** `ACTIVE -> ACCEPTED` ou `EXPIRED` ou `REVOKED`. Um link aceito não pode ser reutilizado.

## 9. Fluxos de negócio

### 9.1 Reserva normal

1. `GET /api/v1/public/barbershops/{slug}/availability?...` apresenta horários.
2. Cliente fornece nome, telefone e aceita política de privacidade; telefone é verificado antes de confirmar conforme estratégia antifraude.
3. `POST /api/v1/public/appointments` recebe dados e chave `Idempotency-Key`.
4. API valida serviço/profissional/tenant, horário, identidade e restrições; cria reserva transacional.
5. API retorna protocolo e link seguro de gerenciamento; evento de notificação é gravado na outbox na mesma transação.
6. Worker envia confirmação quando houver consentimento e template apropriado.

### 9.2 Cancelamento e lista de espera

1. Cliente autenticado por link seguro ou barbeiro autorizado cancela a reserva.
2. A mesma transação altera status e grava evento `AppointmentCancelled` na outbox.
3. Worker busca interessados no **mesmo profissional e intervalo compatível**, ordenados por inscrição; valida que horário segue livre.
4. **Estratégia MVP:** oferecer a vaga a uma pessoa por vez, com validade configurável (ex.: 5 minutos), e registrar um bloqueio/hold transacional antes de enviar. Se não responder, expirar e ofertar ao próximo.
5. Ao clicar, o cliente passa por validação de token, expiração e identidade; servidor transforma hold em agendamento na mesma transação. Nunca confirmar somente pelo clique sem revalidar.
6. Barbeiro recebe aviso se habilitou a preferência; cancelamentos e ofertas geram eventos idempotentes.
7. Se houver cancelamentos sucessivos, retries ou workers paralelos, locks e constraints preservam a consistência.

**Decisão de produto:** se a vaga estiver reservada temporariamente para um cliente, não anunciá-la como livre para outros. Se preferir não bloquear, o texto deve informar que a vaga é por ordem de confirmação e pode acabar; não misturar os dois comportamentos.

### 9.3 Alertas e lembretes

- Eventos: `APPOINTMENT_CREATED`, `APPOINTMENT_CANCELLED`, `WAITLIST_SLOT_AVAILABLE`, `APPOINTMENT_REMINDER`, `APPOINTMENT_CONFIRMED`, `OFFER_EXPIRED`.
- Lembrete opcional (ex.: 24 horas antes) com job periódico e chave idempotente.
- Cliente pode desativar mensagens não essenciais; barbeiro define tipos de alertas.
- Respeitar consentimento e requisitos da plataforma para mensagens iniciadas pela empresa; manter registro da origem do consentimento.

## 10. API REST proposta

| Método | Rota | Finalidade | Autorização |
|---|---|---|---|
| GET | `/api/v1/public/barbershops/{slug}` | Dados públicos da barbearia | Pública |
| GET | `/api/v1/public/barbershops/{slug}/services` | Serviços | Pública |
| GET | `/api/v1/public/barbershops/{slug}/barbers` | Profissionais | Pública |
| GET | `/api/v1/public/barbershops/{slug}/availability` | Horários livres | Pública, rate limit |
| POST | `/api/v1/public/appointments` | Criar reserva | Verificação do cliente, rate limit |
| GET | `/api/v1/customer/appointments/{id}` | Consultar reserva própria | Token de gestão/sessão |
| POST | `/api/v1/customer/appointments/{id}/cancel` | Cancelar | Token de gestão/sessão |
| POST | `/api/v1/customer/appointments/{id}/reschedule` | Reagendar | Token de gestão/sessão |
| POST | `/api/v1/public/waitlist` | Solicitar alerta | Telefone verificado + consentimento |
| DELETE | `/api/v1/customer/waitlist/{id}` | Sair da lista | Token de gestão/sessão |
| POST | `/api/v1/customer/offers/{token}/accept` | Aceitar vaga | Token único + verificação |
| POST | `/api/v1/auth/login` | Login da equipe | Pública com proteção |
| POST | `/api/v1/auth/refresh` | Renovar sessão | Refresh protegido |
| GET | `/api/v1/me` | Identidade e vínculos | Equipe autenticada |
| GET | `/api/v1/dashboard/appointments` | Agenda | Barbeiro/Gestor |
| PATCH | `/api/v1/dashboard/appointments/{id}/status` | Atualizar status | Barbeiro/Gestor |
| CRUD | `/api/v1/dashboard/services` | Serviços | Gestor |
| CRUD | `/api/v1/dashboard/barbers` | Equipe | Gestor |
| CRUD | `/api/v1/dashboard/schedules` | Expediente/bloqueios | Gestor ou próprio barbeiro |
| PUT | `/api/v1/dashboard/notification-preferences` | Preferências | Usuário autenticado |
| POST | `/api/v1/webhooks/whatsapp` | Receber eventos da Meta | Assinatura do provedor |
| GET | `/api/v1/health` | Healthcheck público mínimo | Infra |

**Padrões:** JSON, OpenAPI 3, paginação cursor/limit em listagens, validação de DTOs, erros RFC 9457 (`application/problem+json`), IDs UUID, datas ISO 8601 com offset/UTC, `Idempotency-Key` em mutações críticas, `X-Request-Id` para rastreio. Documentar exemplos e códigos `400/401/403/404/409/422/429/500`.

Exemplo de solicitação:

```json
{
  "barbershopSlug": "barbearia-central",
  "barberId": "uuid-do-barbeiro",
  "serviceId": "uuid-do-servico",
  "startsAt": "2026-11-13T15:00:00-03:00",
  "customer": {"name": "Pedro", "phone": "+5531999999999"}
}
```

## 11. Segurança e privacidade

- **TLS/HTTPS** em todo o tráfego, cookies `HttpOnly`, `Secure`, `SameSite` adequados, CSRF quando autenticação baseada em cookie; CSP e headers de segurança.
- Senhas com hash resistente, recuperação segura e MFA opcional para gestores; sessões revogáveis.
- Autorização por papel **e por recurso** (tenant, profissional, cliente); não confiar em IDs ou permissões do frontend.
- Tokens de gerenciamento e ofertas com entropia alta, prazo curto, hash persistido, uso único quando aplicável; não incluir telefone/segredos na URL.
- Rate limiting por IP/telefone/tenant para consulta, OTP, login, reserva e lista de espera; CAPTCHA adaptativo se necessário.
- Webhook Meta: validar desafio de verificação e assinatura `X-Hub-Signature-256` com app secret sobre corpo bruto; rejeitar assinatura inválida; tratar eventos duplicados.
- Segredos em secret manager, nunca no Git; logs sem tokens, telefone completo ou conteúdo sensível.
- LGPD: finalidade, minimização, política de privacidade, base legal por operação, consentimento específico quando aplicável, canal para exclusão/acesso e política de retenção definida com orientação jurídica.
- Evitar expor nome/telefone de quem ocupou horário ao cliente na lista de espera.
- Backups criptografados e teste periódico de restauração.

## 12. Integração oficial com WhatsApp

**Pré-requisitos:** conta Meta Business adequada, WhatsApp Business Platform/Cloud API, número habilitado, credenciais, webhook HTTPS e templates aprovados para mensagens proativas fora da janela de atendimento. Conferir regras, categorias, limites e preços vigentes diretamente na Meta antes do lançamento.

**Fluxo:** transação de negócio -> `notification_outbox` -> worker -> API WhatsApp -> persistir ID de mensagem -> webhook de status -> atualizar entrega.

**Resiliência:** retries com backoff exponencial e jitter para falhas transitórias; `429` respeita limites; erros permanentes vão para estado `FAILED`/fila de análise; deduplicação por evento e destinatário; não reenviar mensagens aceitas sem necessidade.

**Templates sugeridos:** confirmação de agendamento, cancelamento, vaga liberada, lembrete. Conteúdo objetivo com data, hora, profissional, link seguro e instruções de opt-out quando aplicável. Consentimento para alertas de vagas separado de outros comunicados.

**Custos:** contabilizar envios por tenant e aplicar limites mensais configuráveis. Não assumir que mensagens são gratuitas.

## 13. Docker Compose — desenvolvimento

Serviços mínimos:

- `postgres`: banco com volume persistente, healthcheck e usuário restrito.
- `api`: Spring Boot, depende da saúde do banco, variáveis por ambiente.
- `web`: Next.js, consome API por URL configurável.
- `mailpit` (opcional): captura e-mails de teste; **não** substitui a API oficial do WhatsApp.

Não incluir credenciais reais no Compose. Expor somente portas necessárias em `localhost` durante desenvolvimento; produção deve ter rede privada, TLS, backup e banco não exposto à internet.

Exemplo ilustrativo de `.env.example`:

```dotenv
APP_ENV=local
POSTGRES_DB=barberflow
POSTGRES_USER=barberflow
POSTGRES_PASSWORD=change-me-local-only
DATABASE_URL=jdbc:postgresql://postgres:5432/barberflow
APP_BASE_URL=http://localhost:3000
API_BASE_URL=http://localhost:8080
BARBERSHOP_DEFAULT_TIMEZONE=America/Sao_Paulo
WHATSAPP_ENABLED=false
WHATSAPP_PHONE_NUMBER_ID=
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_APP_SECRET=
WHATSAPP_VERIFY_TOKEN=
```

Configurar `DATABASE_URL` conforme rede onde API executa; `postgres` só resolve dentro do Compose. No navegador, URLs públicas devem ser acessíveis pelo host. Usar `.env.local` ignorado pelo Git e secrets na produção.

## 14. Ambientes e implantação

| Ambiente | Uso | Regras |
|---|---|---|
| Local | Desenvolvimento | Compose, dados fictícios, WhatsApp desabilitado/mock |
| Staging | Teste integrado | Infra isolada, número de teste, migrations e smoke tests |
| Produção | Clientes reais | HTTPS, backups, monitoramento, segredos, limites, rollback |

**Topologia recomendada:** CDN/edge -> frontend Next.js; reverse proxy/API gateway -> serviço Spring Boot em rede privada; PostgreSQL gerenciado privado; worker de notificações; secret manager; serviço de logs/métricas. A aplicação deve ser stateless. Migrações Flyway executadas de forma controlada antes do rollout da API, com compatibilidade entre versões.

**Domínios ilustrativos:** `app.barberflow.com` para dashboard, `barberflow.com/b/{slug}` para reservas e `api.barberflow.com` para API. Definir CORS estrito apenas para origens autorizadas quando necessário; preferir proxy same-origin quando possível.

## 15. CI/CD

**Pull request:** format/lint -> análise estática -> testes unitários -> testes de integração com Testcontainers -> build web/API -> scan de dependências e segredos -> validação de migrations -> testes E2E essenciais.

**Merge para main:** gerar artefatos versionados por commit SHA; deploy staging; smoke tests; aprovação manual para produção no início; deploy progressivo com healthchecks; rollback da aplicação (migrations precisam ser retrocompatíveis).

Usar GitHub Actions com permissões mínimas e autenticação OIDC para cloud quando disponível. Não armazenar secrets em workflows ou imagens.

## 16. Observabilidade e operação

- Logs JSON com `request_id`, `tenant_id` pseudonimizado e tipo de evento, sem dados pessoais desnecessários.
- Métricas: reservas criadas/canceladas, taxa de conflito, conversão da lista de espera, tempo de resposta p95, erros 5xx, tamanho/idade da outbox, falhas e custo de notificações.
- Alertas: API indisponível, banco sem conexão, fila atrasada, falha repetida no WhatsApp, erro elevado em agendamentos.
- Endpoints liveness/readiness separados; readiness valida dependências críticas sem vazar detalhes.
- Rotina: backup diário conforme RPO, teste mensal de restauração, revisão de acessos e atualizações de segurança.
- Metas iniciais sugeridas (validar): disponibilidade mensal 99,5%; p95 de consulta de horários abaixo de 500 ms sob carga típica; RPO 24h e RTO 4h, sujeitos ao provedor e orçamento.

## 17. Estratégia de testes

| Tipo | Cenários obrigatórios |
|---|---|
| Unitário | Geração de horários, fusos, duração, políticas de cancelamento, transições de estado |
| Integração | Constraints de sobreposição, transações, isolamento multi-tenant, outbox, Flyway |
| Concorrência | Duas reservas simultâneas no mesmo intervalo: apenas uma confirmada |
| API | Autorização por recurso, validação, idempotência, rate limit, erros padronizados |
| E2E | Reserva -> cancelamento -> oferta -> aceite -> agenda atualizada |
| Webhook | Assinatura inválida, replay, eventos duplicados e fora de ordem |
| Segurança | IDOR, vazamento cross-tenant, brute force, tokens expirados e CSRF |
| Resiliência | WhatsApp indisponível, retries, reinício do worker e oferta expirada |

## 18. Roadmap de implementação

### Fase 0 — Fundação
- [ ] Criar monorepo, README, ADRs, padrões de código e CI.
- [ ] Subir PostgreSQL + API + frontend em Compose.
- [ ] Configurar Flyway, OpenAPI, logs, healthchecks e testes base.
- [ ] Definir contratos de autenticação e tenant.

### Fase 1 — Cadastro e agenda
- [ ] CRUD de barbearia, profissionais e serviços.
- [ ] Expediente semanal e bloqueios.
- [ ] Motor de disponibilidade com fusos e conflitos.
- [ ] Login e painel do barbeiro/gestor.

### Fase 2 — Agendamentos
- [ ] Página pública responsiva.
- [ ] Verificação/gestão segura da identidade do cliente.
- [ ] Criar, consultar, cancelar e reagendar reservas.
- [ ] Restrições transacionais e testes de concorrência.

### Fase 3 — Lista de espera
- [ ] Inscrição com horário/profissional e consentimento.
- [ ] Detecção de cancelamento e ordenação FIFO.
- [ ] Oferta temporária, expiração e aceite seguro.
- [ ] Testes de corrida, duplicidade e recuperação.

### Fase 4 — WhatsApp
- [ ] Configurar Cloud API, templates e webhook assinado.
- [ ] Outbox e worker com retry/idempotência.
- [ ] Preferências de cliente e barbeiro.
- [ ] Testes em staging e limites de custo.

### Fase 5 — Produção piloto
- [ ] Política de privacidade, retenção e termos.
- [ ] Backups, observabilidade, hardening e smoke tests.
- [ ] Teste com uma barbearia real e coleta de feedback.
- [ ] Ajustes de UX, métricas e estabilidade.

### Fase 6 — SaaS (posterior)
- [ ] Onboarding self-service de barbearias.
- [ ] Planos, cobrança, quotas e gestão de assinaturas.
- [ ] Painel da plataforma e suporte auditado.
- [ ] Relatórios e limites de mensagens por tenant.

## 19. Critérios de aceite do MVP

1. Cliente visualiza somente horários válidos para serviço e profissional.
2. Duas solicitações concorrentes não conseguem ocupar o mesmo intervalo.
3. Cliente consegue reservar, cancelar e reagendar com autenticação apropriada.
4. Barbeiro vê apenas reservas autorizadas do próprio tenant.
5. Cancelamento torna horário elegível para lista de espera.
6. Oferta expira corretamente; somente um cliente pode confirmar a vaga.
7. Repetir uma chamada com a mesma chave idempotente não cria reservas duplicadas.
8. Notificações respeitam opt-in, preferências e regras do WhatsApp.
9. Falha no WhatsApp não desfaz reserva já confirmada nem perde evento de notificação.
10. Fluxos principais passam em testes automatizados e staging.

## 20. Decisões pendentes antes de codificar

- Uma barbearia ou SaaS desde o primeiro deploy? **Proposta:** modelar multi-tenant, lançar com uma.
- Um ou vários barbeiros por estabelecimento? **Proposta:** vários, cada um com agenda própria.
- Cliente precisa de conta? **Proposta:** não; OTP/link seguro para gerenciar reservas.
- Duração fixa ou variável? **Proposta:** por serviço, com intervalo opcional.
- Horário liberado deve ser exclusivo ao primeiro da fila? **Proposta:** sim, hold temporário.
- Prazo da oferta? **Proposta inicial:** 5 minutos, configurável.
- Políticas de cancelamento e antecedência? **Definir com barbearia piloto.**
- WhatsApp é obrigatório no primeiro lançamento? **Proposta:** entregar primeiro o agendamento; integrar após fluxo consistente.
- Hospedagem e orçamento? **Selecionar depois de medir carga, disponibilidade e custo por mensagem.**

## 21. Riscos e mitigação

| Risco | Mitigação |
|---|---|
| Dupla reserva | Constraint no banco, transação, idempotência e teste concorrente |
| Vazamento entre barbearias | Autorização por tenant e testes cross-tenant |
| Spam e abuso de WhatsApp | Opt-in, rate limit, opt-out, quotas e auditoria |
| Falhas da Meta | Outbox, retry com backoff, estado de entrega e fallback operacional |
| Horários errados por fuso | Timezone IANA por tenant, UTC persistido e testes de borda |
| Complexidade excessiva | Monólito modular e implementação por fases |
| Custos crescentes | Métricas por tenant, limites e revisão periódica |

---

**Nota:** este documento é uma especificação de arquitetura, não uma implementação pronta. Antes do desenvolvimento, criar os ADRs de autenticação, política de disponibilidade, estratégia de hold e integração WhatsApp, e transformar os requisitos em issues pequenas e testáveis.
