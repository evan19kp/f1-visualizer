# F1 3D Race Visualizer

Real-time Formula 1 race visualization with 3D rendering, WebSocket position streaming, and AI race engineer commentary.

## Stack

- **Backend:** Java 21, Spring Boot 4.x, JPA, JWT, STOMP/WebSocket, Redis, PostgreSQL 16, Flyway, S3, OpenAI
- **Frontend:** React 18, TypeScript, Three.js (@react-three/fiber), Zustand, Tailwind, Vite

## Prerequisites

Java 21+, Docker Compose, Node 20+ (frontend). Use `./mvnw` if Maven is not installed.

## Quick start

```bash
docker compose up -d
./mvnw spring-boot:run -pl f1-api
```

Frontend (separate terminal):

```bash
cd f1-frontend && npm install && npm run dev
```

## Modules

| Module | Role |
|--------|------|
| `f1-core` | Domain models, events, shared interfaces |
| `f1-persistence` | JPA, Redis, S3, Flyway |
| `f1-ingestion` | OpenF1 client, scheduler, GPS normalizer |
| `f1-ai` | OpenAI race engineer |
| `f1-api` | REST, WebSocket, security |
| `f1-frontend` | React + Three.js UI |

## Configuration

| Variable | Default |
|----------|---------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/f1` |
| `DB_USER` / `DB_PASSWORD` | `f1user` / `f1pass` |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` |
| `JWT_SECRET` | required in production |
| `OPENAI_API_KEY` | required for AI features |
| `AWS_REGION` / `S3_BUCKET` | S3 track assets |

## Tests

```bash
./mvnw test      # unit
./mvnw verify    # unit + integration (Docker)
```
