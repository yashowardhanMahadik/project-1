# springboot-auth-starter

Spring Boot 3 REST API with JWT authentication. PostgreSQL + Redis via Docker.

## Agent Routing Rules

When delegating tasks, the orchestrator follows these rules:

### Sequential (hard dependencies)
- pm-agent → ALWAYS first. Others depend on the skeleton it creates.
- qa-agent → AFTER dev-agent (needs source code to test)
- enduser-agent → AFTER dev-agent (needs controller code to review)

### Parallel (safe to run together)
- dev-agent + qa-agent plan → can run in parallel once pm-agent is done
  (qa-agent writes test stubs against the API contract while dev-agent writes source)

### Spawn rules
- Use `run_in_background: true` only for tasks with no downstream dependencies in the same phase
- Always pass file paths explicitly in spawn prompts — subagents start with zero context

## Package Structure

```
com.example.app
├── config/          # SecurityConfig, RedisConfig
├── controller/      # AuthController
├── service/         # AuthService
├── repository/      # UserRepository
├── model/
│   ├── entity/      # User
│   └── dto/         # RegisterRequest, LoginRequest, AuthResponse
├── security/        # JwtUtil, JwtAuthFilter, TokenBlacklistService, AppUserDetailsService
└── exception/       # GlobalExceptionHandler, custom exceptions
```

## API Contract

| Method | Path | Auth required | Description |
|--------|------|--------------|-------------|
| POST | /auth/register | No | Create new user |
| POST | /auth/login | No | Login, receive JWT |
| POST | /auth/logout | Yes (Bearer) | Invalidate token |
| GET | /actuator/health | No | Health check |

## Code Conventions

- Constructor injection only (no @Autowired on fields)
- @Transactional on service methods that write to DB
- SLF4J logging only (no System.out)
- All controllers return ResponseEntity<T>
- Error shape: `{ "error": "message", "status": 4xx }`
- Java 21 features allowed (records for DTOs is fine)
