# springboot-auth-starter

Spring Boot 3 REST API with JWT authentication and Flowable-driven ticket approval workflow.
PostgreSQL + Redis via Docker.

## Tech Stack

- Java 21
- Spring Boot 3.2.5
- Spring Security 6 (stateless JWT, method-level `@PreAuthorize`)
- Flowable 7.0.1 (embedded process engine, BPMN 2.0)
- PostgreSQL 15 — port **5433** on host (avoids conflict with local PG installs)
- Redis 7 (token blacklist on logout)
- Maven

## Agent Routing Rules

When delegating tasks, the orchestrator follows these rules:

### Sequential (hard dependencies)
- pm-agent → ALWAYS first. Others depend on the skeleton it creates.
- dev-agent → implements source code after pm-agent
- qa-agent → AFTER dev-agent (needs source code to test)
- enduser-agent → AFTER dev-agent (needs controller code to review)

### Parallel (safe to run together)
- dev-agent + qa-agent plan → can run in parallel once pm-agent is done

### Spawn rules
- Use `run_in_background: true` only for tasks with no downstream dependencies in the same phase
- Always pass file paths explicitly in spawn prompts — subagents start with zero context

## Package Structure

```
com.example.app
├── config/          # SecurityConfig (@EnableMethodSecurity), RedisConfig
├── controller/      # AuthController, TicketController
├── event/           # TicketStatusChangedEvent (Spring ApplicationEvent)
├── exception/       # GlobalExceptionHandler, custom exceptions
├── model/
│   ├── dto/         # RegisterRequest, LoginRequest, AuthResponse,
│   │                #   CreateTicketRequest, TicketActionRequest, TicketResponse
│   ├── entity/      # User (with role), Ticket (with processInstanceId)
│   └── enums/       # TicketStatus, UserRole, TicketPriority
├── repository/      # UserRepository, TicketRepository
├── security/        # JwtUtil (role claim), JwtAuthFilter, TokenBlacklistService,
│                    #   AppUserDetailsService
└── service/         # AuthService, TicketWorkflowService
```

BPMN process definition: `src/main/resources/processes/ticket-approval.bpmn20.xml`

## API Contract

### Auth

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /auth/register | No | Create user (include `role` field) |
| POST | /auth/login | No | Login, receive JWT with role claim |
| POST | /auth/logout | Bearer | Invalidate token via Redis blacklist |
| GET | /actuator/health | No | Health check |

### Ticket Workflow

| Method | Path | Role | Description |
|--------|------|------|-------------|
| POST | /tickets | EMPLOYEE | Create ticket → starts Flowable process → `OPEN` |
| GET | /tickets | Any | List tickets (optional `?status=` filter) |
| GET | /tickets/{id} | Any | Get ticket + current Flowable task ID |
| POST | /tickets/{id}/assign | EMPLOYEE | Claim work task → `IN_PROGRESS` |
| POST | /tickets/{id}/submit | EMPLOYEE | Complete work task → `PENDING_APPROVAL` |
| POST | /tickets/{id}/approve | MANAGER | Approve → `APPROVED` |
| POST | /tickets/{id}/reject | MANAGER | Reject + reason → `IN_PROGRESS` (loop) |

### Workflow State Machine

```
OPEN → IN_PROGRESS → PENDING_APPROVAL → APPROVED
                ↑           └─── REJECTED ──┘
```

## Code Conventions

- Constructor injection only (no @Autowired on fields)
- @Transactional on service methods that write to DB
- SLF4J logging only (no System.out)
- All controllers return ResponseEntity<T>
- Error shape: `{ "error": "message", "status": 4xx }`
- Java 21 features allowed (records for DTOs is fine)
- **Service files (`*Service.java`) must have detailed inline comments on every logical code segment** — explain intent and non-obvious behaviour, not just what the next line says
- `@PreAuthorize` on controller methods for role enforcement (ROLE_EMPLOYEE / ROLE_MANAGER)
- Flowable exceptions: `FlowableObjectNotFoundException` → 404, `FlowableException` → 422
