# BarberFlow

Sistema web de agendamentos para barbearia com lista de espera e notificacoes
por WhatsApp. Ver especificacao completa em [`BARBERFLOW_ARQUITETURA.md`](BARBERFLOW_ARQUITETURA.md).

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
mvn verify   # Testcontainers; requer Docker em execucao
```

## Roadmap

- [x] Fase 0 — Fundacao: esqueleto da API, Flyway, healthchecks, Dockerfile/Compose
- [x] Fase 1 — Cadastro e agenda (CRUD barbeiros/servicos, motor de disponibilidade)
- [x] Fase 2 — Agendamentos publicos (+ catalogo publico, rate limit, idempotencia, outbox/auditoria)
- [x] Fase 3 — Lista de espera (hold transacional, ofertas com expiracao)
- [x] Web — Publico de agendamento + link seguro de gerenciamento (Next.js)
- [x] Fase 4 — WhatsApp (Cloud API): worker da outbox e webhook da Meta (entrega real depende de credenciais)
- [x] LGPD — Consentimento na coleta, acesso/exclusao do titular via link seguro, politica de privacidade e retencao configuravel
- [ ] Fase 5 — Producao piloto
