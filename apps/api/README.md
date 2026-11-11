# BarberFlow API (backend)

Monolito modular Spring Boot. Ver especificacao completa em `../../BARBERFLOW_ARQUITETURA.md`.

## Stack

- Java 21 LTS (bytecode `--release 21`; rodar em JVM 21+)
- Spring Boot 4.1.x (a spec propunha 3.x, mas toda a linha 3.x saiu do suporte OSS em jun/2026; 4.1 e a linha suportada)
- PostgreSQL 16+ via Flyway (migracoes em `src/main/resources/db/migration`)
- Actuator + Micrometer (Prometheus)
- Testes: JUnit 5 + Testcontainers (requer Docker)

## Estrutura (monolito modular)

`src/main/java/com/barberflow/` com modulos: `auth`, `tenant`, `staff`, `service`,
`availability`, `appointment`, `waitlist`, `notification`, `audit`, `shared`.
Cada modulo separa `api` (controllers/DTOs), `application` (casos de uso),
`domain` (regras) e `infrastructure` (persistencia/provedores).

## Como rodar (desenvolvimento)

Requer Java 21+ e Maven (ou o wrapper, quando adicionado).

```bash
mvn spring-boot:run
```

Por padrao conecta em `jdbc:postgresql://localhost:5432/barberflow`.
Variaveis: `DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (ver `.env.example` na raiz).

## Endpoints de saude (Fase 0)

- `GET /actuator/health` — publico; probes de liveness/readiness habilitadas
- `GET /actuator/prometheus` — metricas

Demais rotas exigem autenticacao (basica gerada por padrao no esqueleto);
as regras reais de autenticacao entram no modulo `auth` (Fases 0-1).

## Testes

```bash
mvn verify   # executa com Testcontainers; Docker necessario
```
