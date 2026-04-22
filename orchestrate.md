# Project Orchestrator

## Project Context

You are orchestrating the full development lifecycle for a Spring Boot backend project.

### Tech Stack
- Java 21
- Spring Boot 3.x
- PostgreSQL (via Docker, host port 5433)
- Redis (via Docker, for session/token caching)
- Maven
- JWT for authentication (login/logout), role claim embedded (ROLE_EMPLOYEE / ROLE_MANAGER)
- Spring Security with `@EnableMethodSecurity`
- **Flowable 7.0.1** (embedded BPMN process engine for ticket approval workflow)

### Project Requirements
- REST API backend only (no frontend yet)
- Auth endpoints: POST /auth/register, POST /auth/login, POST /auth/logout
- JWT access token + Redis-backed token invalidation on logout
- **Ticket approval workflow**: OPEN → IN_PROGRESS → PENDING_APPROVAL → APPROVED/REJECTED
- **Role-based access**: EMPLOYEE creates/works tickets; MANAGER approves/rejects
- Docker Compose for local infra (Postgres + Redis)
- Application runs on port 8080
- Health check endpoint: GET /actuator/health

### Constraints
- Developer runs Docker Engine locally (no docker desktop required)
- No cloud infra — everything local via Docker Compose
- No Flyway/Liquibase yet — use spring.jpa.hibernate.ddl-auto=create-drop for now
- Package: com.example.app
- App name: springboot-auth-starter
- PostgreSQL mapped to host port 5433 (not 5432) to avoid local PG conflicts

---

## Your Job as Orchestrator

You must delegate work to the four specialist subagents below. Do NOT implement
anything yourself. Your only job is: plan, delegate, verify, synthesise.

### Execution Order (respect dependencies)

**Phase 1 — Sequential** (PM must go first, others depend on its output):
1. Use the pm-agent to create CLAUDE.md, docker-compose.yml, and the project skeleton

**Phase 2 — Parallel** (Dev and QA plan are independent once PM is done):
2. Use the dev-agent to scaffold the full Spring Boot application (auth + workflow)
3. Use the qa-agent to write the test plan and test stubs (can run in parallel with dev-agent)

**Phase 3 — Sequential** (End User review requires working code):
4. Use the enduser-agent to review the API contract and report gaps

### Notes for subagent prompts
- Always tell dev-agent that `TicketWorkflowService` requires **detailed inline comments** on every code segment
- Always pass explicit file paths in prompts — subagents start with zero context
- dev-agent must implement both the auth layer AND the Flowable workflow layer

### After all agents complete:
- Print a "Setup complete" summary listing:
  - Files created
  - Docker services defined
  - Endpoints implemented
  - Test coverage areas
  - Any gaps flagged by the end user agent
  - Git commands to initialise and push the repo

---

## Invocation

To start, run in your project directory:

```
claude --allowedTools "Task,Read,Write,Bash,Glob" \
       --print "$(cat orchestrate.md)"
```
