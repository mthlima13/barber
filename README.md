# BarberFlow

Sistema web de agendamentos para barbearia com lista de espera e notificacoes
por WhatsApp. Ver especificacao completa em [`BARBERFLOW_ARQUITETURA.md`](BARBERFLOW_ARQUITETURA.md).

## Estrutura

```
barberflow/
├── apps/
│   ├── api/        # Backend: Spring Boot 4.1 (monolito modular)
│   └── web/        # Frontend: Next.js (a criar)
├── infra/
│   └── compose.yaml # Docker Compose de desenvolvimento (PostgreSQL + API)
└── BARBERFLOW_ARQUITETURA.md
```

## Desenvolvimento

### Com Docker Compose (recomendado)

```bash
docker compose -f infra/compose.yaml up --build
```

- API: http://localhost:8081 (health: `GET /actuator/health`; host 8081 porque a 8080 ja esta em uso por outro projeto)
- PostgreSQL: localhost:5432 (db `barberflow`, user `barberflow`)

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
- [ ] Fase 1 — Cadastro e agenda (CRUD barbeiros/servicos, motor de disponibilidade)
- [ ] Fase 2 — Agendamentos publicos
- [ ] Fase 3 — Lista de espera
- [ ] Fase 4 — WhatsApp (Cloud API)
- [ ] Fase 5 — Producao piloto
