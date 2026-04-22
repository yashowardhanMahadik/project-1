# springboot-auth-starter

A Spring Boot 3 REST API with JWT authentication, backed by PostgreSQL and Redis.

## Tech Stack

- Java 21
- Spring Boot 3.2.5
- Spring Security (stateless JWT)
- PostgreSQL 15 (via Docker)
- Redis 7 (via Docker — token blacklist)
- Maven

## Prerequisites

- Java 21
- Maven 3.x
- Docker Engine

## Getting Started

### 1. Start infrastructure

```bash
docker compose up -d
```

This starts:
- PostgreSQL on **port 5433** (mapped from container 5432)
- Redis on **port 6379**

> **Note:** Port 5433 is used to avoid conflicts with any local PostgreSQL installation.

### 2. Run the application

```bash
mvn spring-boot:run
```

The app starts on **http://localhost:8080**.

### 3. Verify

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP",...}`

---

## API Reference

### POST /auth/register

Create a new user account and receive a JWT.

**Request**
```json
{
  "username": "alice",
  "password": "secret123",
  "email": "alice@example.com"
}
```

**Response** `201 Created`
```json
{ "token": "<jwt>" }
```

---

### POST /auth/login

Authenticate and receive a JWT.

**Request**
```json
{
  "username": "alice",
  "password": "secret123"
}
```

**Response** `200 OK`
```json
{ "token": "<jwt>" }
```

---

### POST /auth/logout

Invalidate the current JWT (adds it to the Redis blacklist until it expires).

**Headers**
```
Authorization: Bearer <jwt>
```

**Response** `204 No Content`

---

### GET /actuator/health

Health check — no authentication required.

**Response** `200 OK`
```json
{ "status": "UP" }
```

---

## Error Responses

All errors follow this shape:

```json
{
  "error": "Human-readable message",
  "status": 4xx
}
```

| Status | Condition |
|--------|-----------|
| 400 | Missing or malformed Authorization header |
| 401 | Bad credentials / invalid token |
| 409 | Username or email already registered |
| 500 | Unexpected server error |

---

## Configuration

Key properties in `src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | HTTP port |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5433/authdb` | PostgreSQL URL |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `app.jwt.secret` | *(placeholder)* | HMAC-SHA256 signing key — **change before deploying** |
| `app.jwt.expiration-ms` | `86400000` | Token TTL (24 hours) |

---

## Running Tests

```bash
mvn test
```

Tests use an H2 in-memory database and mock Redis — no Docker required.

---

## Stopping

```bash
# Stop the app: Ctrl+C in the terminal running mvn spring-boot:run

# Stop Docker services
docker compose down

# Stop and remove volumes (wipes DB data)
docker compose down -v
```
