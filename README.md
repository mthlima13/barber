# BarberFlow

Sistema web de agendamentos para barbearia com lista de espera e notificacoes
por WhatsApp. Ver especificacao completa em [`BARBERFLOW_ARQUITETURA.md`](BARBERFLOW_ARQUITETURA.md).

## Funcionalidades

- **Pagina publica de reserva** (`/b/{slug}`): servico -> profissional ->
  data/horario (disponibilidade calculada no servidor) -> dados do cliente;
  telefone com mascara BR normalizado para E.164; resumo da reserva antes de
  confirmar; quando a data esta cheia, formulario **"Avise-me"** inscreve o
  cliente na lista de espera por horario especifico
- **Gerenciamento sem conta** (`/reservas/{id}?t=...`): o cliente consulta,
  remarca e cancela pelo link seguro (token unico; no banco so existe o hash)
- **Painel do gestor** (`/gestor`): agenda do dia com acoes de status,
  CRUD de servicos/profissionais, expediente e excecoes, fila de espera com
  cliente resolvido (nome/telefone e slot ofertado), auditoria e outbox
- **Papel BARBER**: o gestor cria a conta do profissional, que ve e altera
  APENAS a propria agenda (`/gestor/minha-agenda`); reserva de colega
  responde 404 e o CRUD do painel fica 403
- **Lista de espera** (spec 9.2): oferta "uma pessoa por vez" com hold
  transacional (a vaga em hold nao e anunciada como livre), aceite por
  token com revalidacao do slot e expiracao de 5 minutos que devolve a
  vaga ao proximo da fila
- **WhatsApp (Cloud API)**: entrega via outbox (worker com retry/backoff),
  webhook da Meta com assinatura HMAC validada e dedupe; templates pt-BR
  sem segredos. Enquanto `WHATSAPP_ENABLED=false`, envio simulado em log
- **Lembrete 24h**: job periodico idempotente (chave unica por reserva na
  outbox) agenda o lembrete das reservas PENDING/CONFIRMED na janela
- **Notificacao ao gestor**: o WhatsApp configurado em
  `/gestor/configuracoes` recebe reserva nova (inclusive via fila),
  inscricao na lista de espera, cancelamento e remarcacao feitos pelo
  cliente, pela mesma outbox; vazio desliga
- **Expiracao de PENDING**: job periodico cancela a reserva pendente de
  confirmacao alem do prazo (`APPOINTMENT_EXPIRY_PENDING_TTL_HOURS`,
  padrao 24h), libera o slot, oferta a lista de espera e notifica o
  cliente; desligado por padrao no dev
- **LGPD**: consentimento obrigatorio na reserva e na fila, politica em
  `/privacidade`, acesso/exclusao do titular (anonimizacao irreversivel) e
  retencao automatica configuravel
- **Observabilidade**: metricas de negocio no Prometheus (reservas por
  resultado, funil da fila, falhas de entrega, idade da outbox) e p95/5xx
  do `http.server.requests`

## Endpoints principais

Publicos (rate limit por IP):
- `GET /api/v1/public/barbershops/{slug}` — dados da barbearia
- `GET /api/v1/public/barbershops/{slug}/services` e `/barbers`
- `GET /api/v1/public/barbershops/{slug}/availability?barberId=&serviceId=&date=`
- `POST /api/v1/public/appointments` — cria reserva (header `Idempotency-Key`)
- `POST /api/v1/public/waitlist` — inscricao na lista de espera

Titular do link seguro (`Authorization: Bearer <token>`):
- `GET/POST /api/v1/customer/appointments/{id}` e `/{id}/cancel`, `/{id}/reschedule`
- `GET|DELETE /api/v1/customer/appointments/{id}/privacy` — acesso/exclusao LGPD
- `GET|DELETE /api/v1/customer/waitlist/{id}` — inscricao e saida da fila
- `POST /api/v1/customer/offers/{token}/accept` — aceite da oferta de vaga

Dashboard do gestor (`MANAGER`) e agenda do profissional (`BARBER`):
- `POST /api/v1/auth/bootstrap` e `/auth/login`, `GET /api/v1/me`
- `CRUD /api/v1/dashboard/services` e `/barbers` (+ `/{id}/hours`,
  `/{id}/services`, `/{id}/exceptions`, `/{id}/credentials`)
- `GET /api/v1/dashboard/appointments?date=` + `PATCH /{id}/status`
- `GET /api/v1/dashboard/waitlist` + `POST /waitlist/process`
- `GET /api/v1/dashboard/outbox` e `GET /api/v1/dashboard/audit`
- `GET|PATCH /api/v1/dashboard/settings` — WhatsApp do gestor
- `GET /api/v1/dashboard/barber/agenda?date=` + `PATCH .../appointments/{id}/status` (BARBER)

Webhooks e operacao:
- `GET|POST /api/v1/webhooks/whatsapp` — challenge e eventos da Meta (HMAC)
- `GET /actuator/health`, `GET /actuator/prometheus`

## Estrutura

```
barberflow/
├── apps/
│   ├── api/        # Backend: Spring Boot 4.1 (monolito modular)
│   └── web/        # Frontend: Next.js (App Router, TypeScript, Tailwind)
├── infra/
│   └── compose.yaml # Docker Compose de desenvolvimento (PostgreSQL + API + Web)
└── BARBERFLOW_ARQUITETURA.md
```

## Desenvolvimento

### Com Docker Compose (recomendado)

```bash
docker compose -f infra/compose.yaml up --build
```

- API: http://localhost:8081 (health: `GET /actuator/health`; host 8081 porque a 8080 ja esta em uso por outro projeto)
- Web: http://localhost:3000
- PostgreSQL: localhost:5432 (db `barberflow`, user `barberflow`)

### Web sem Docker

Requer Node.js 20+:

```bash
cd apps/web
npm install
npm run dev   # http://localhost:3000
```

Configuracao: `NEXT_PUBLIC_API_BASE_URL` (URL da API consumida pelo browser)
e `NEXT_PUBLIC_DEFAULT_SLUG` (barbearia padrao do MVP). Ver `.env.example`.

### Bootstrap inicial

A barbearia (tenant `barbearia-piloto`) e criada no primeiro bootstrap do
gestor (uma vez por base):

```bash
curl -X POST http://localhost:8081/api/v1/auth/bootstrap \
  -H "Content-Type: application/json" \
  -d '{"name":"Gestor","email":"gestor@barbearia.local","password":"senha-segura-123"}'
```

Depois cadastre servicos e profissionais no dashboard e libere o expediente.

### API sem Docker

Requer Java 21+ e Maven; PostgreSQL local na porta 5432:

```bash
cd apps/api
mvn spring-boot:run
```

Variaveis: ver `.env.example` (`DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`).

### Testes

```bash
cd apps/api
mvn verify   # 91 testes de integracao; Testcontainers; requer Docker em execucao
```

### Observabilidade

`GET /actuator/prometheus` expoe, alem das metricas padrao:

- `barberflow_appointments_total{outcome="created|cancelled|conflict"}` — reservas
- `barberflow_waitlist_offers_total`, `barberflow_waitlist_offers_accepted_total`,
  `barberflow_waitlist_offers_expired_total` — funil da lista de espera
- `barberflow_whatsapp_delivery_failed_total` — entregas esgotadas
- `barberflow_outbox_pending` e `barberflow_outbox_oldest_pending_seconds` —
  saude da outbox (tamanho e idade do evento mais antigo)
- p95 e 5xx de `http.server.requests` (padrao do Spring)

## Roadmap

- [x] Fase 0 — Fundacao: esqueleto da API, Flyway, healthchecks, Dockerfile/Compose
- [x] Fase 1 — Cadastro e agenda (CRUD barbeiros/servicos, motor de disponibilidade)
- [x] Fase 2 — Agendamentos publicos (+ catalogo publico, rate limit, idempotencia, outbox/auditoria)
- [x] Fase 3 — Lista de espera (hold transacional, ofertas com expiracao)
- [x] Web — Publico de agendamento + link seguro de gerenciamento (Next.js)
- [x] Fase 4 — WhatsApp (Cloud API): worker da outbox e webhook da Meta (entrega real depende de credenciais)
- [x] LGPD — Consentimento na coleta, acesso/exclusao do titular via link seguro, politica de privacidade e retencao configuravel
- [x] Fase 5 (preparacao) — Headers de seguranca (CSP/HSTS) na API e no web, perfil `prod` fail-fast, smoke test e checklist de producao (`docs/PRODUCAO.md`); deploy fica para quando houver infra
- [x] Plano do piloto — roteiro de observacao, hipoteses com criterio de sucesso e template de feedback (`docs/PILOTO.md`)
- [x] Papel BARBER — acesso restrito a propria agenda, com login criado pelo gestor
- [x] Metricas de negocio — contadores/gauges no Prometheus e checklist de observabilidade
- [x] Lembrete 24h — job idempotente via outbox (`REMINDERS_ENABLED`)
- [x] Expiracao de PENDING — job libera o slot e oferta a lista de espera (`APPOINTMENT_EXPIRY_ENABLED`)
- [x] CI (GitHub Actions: mvn verify com Testcontainers + next build) e teste E2E do fluxo publico (`scripts/e2e.sh`)
- [x] Web publico completo — avise-me (lista de espera) na pagina de reserva e telefone E.164
- [ ] Fase 5 — Producao piloto (deploy real, backups, observabilidade em operacao, feedback da barbearia)
