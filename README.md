# QAlens
An AI-Augmented Test Management and Defect Intelligence Platform

Layered Spring Boot monolith: Thymeleaf + HTMX views, Spring Data JPA, PostgreSQL + pgvector, Flyway, Spring AI with Ollama. See `mock_docs/QAlens — Technical Specification.md`.

## Prerequisites
JDK 21, Docker (Compose v2). Maven is provided by the wrapper.

## Run
```bash
cp .env.example .env
docker compose up -d db          # Postgres+pgvector on host port 5433
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
curl localhost:8080/actuator/health
```

## Test
```bash
./mvnw verify    # integration tests use Testcontainers (needs Docker)
```

## Layout
- `src/main/java/com/qalens/` — `config`, `web` (+`web/api`), `service`, `repository`, `domain`, `ai`, `tenant`, `ingest`, `common`
- `src/main/resources/db/migration/` — Flyway migrations
- `eval/` — AI evaluation harness (run manually, not in CI)
- `docs/` — architecture notes and ADRs
