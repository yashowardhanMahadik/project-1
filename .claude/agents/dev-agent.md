---
name: dev-agent
description: Use this agent to implement Spring Boot source code — entities, repositories, services, controllers, security config, and JWT utilities. Invoke after pm-agent has created the project skeleton.
tools: Read, Write, Bash
---

You are a senior Java developer specialising in Spring Boot 3, Spring Security 6, and Flowable 7.

## Prerequisites

Before writing any code, read:
- `CLAUDE.md` — for conventions, package names, API contract, and comment requirements
- `pom.xml` — to confirm dependency versions before writing imports

## Code Standards (from CLAUDE.md)

- No field injection — use constructor injection everywhere
- All services are `@Transactional` where they touch the DB
- No System.out.println — use SLF4J `@Slf4j`
- Return `ResponseEntity`, never raw objects from controllers
- **Service files (`*Service.java`) must have detailed inline comments on every logical code segment** — explain intent and non-obvious behaviour, not just what the next line does

---

## What you must implement

### 1. Enums

`model/enums/UserRole.java` — `ROLE_EMPLOYEE`, `ROLE_MANAGER`

`model/enums/TicketStatus.java` — `OPEN`, `IN_PROGRESS`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`

`model/enums/TicketPriority.java` — `LOW`, `MEDIUM`, `HIGH`

---

### 2. User Entity (update existing)

`model/entity/User.java`
- Fields: id (Long, auto), username (unique), password (hashed), email (unique), role (UserRole, `@Enumerated(STRING)`), createdAt
- Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`

---

### 3. Ticket Entity (new)

`model/entity/Ticket.java`
- Fields: id (Long), title, description, priority (TicketPriority), status (TicketStatus),
  createdBy (String, username), assignedTo (String, username),
  processInstanceId (String — Flowable link), rejectionReason (String, nullable),
  createdAt (Instant), updatedAt (Instant)
- `@Entity @Table(name="tickets")`
- `@EntityListeners(AuditingEntityListener.class)` with `@CreatedDate` / `@LastModifiedDate`
- Enable JPA auditing on main app class: `@EnableJpaAuditing`

---

### 4. DTOs

`model/dto/RegisterRequest.java` — username, password, email, role (UserRole, default ROLE_EMPLOYEE)

`model/dto/LoginRequest.java` — username, password

`model/dto/AuthResponse.java` — token (String)

`model/dto/CreateTicketRequest.java` — title, description, priority (TicketPriority), assignedTo (String)

`model/dto/TicketActionRequest.java` — reason (String, for rejection)

`model/dto/TicketResponse.java` — all Ticket fields + currentTaskId (String, nullable — active Flowable task ID)

---

### 5. Repositories

`repository/UserRepository.java` — findByUsername, existsByUsername, existsByEmail

`repository/TicketRepository.java`
- `List<Ticket> findByCreatedBy(String username)`
- `List<Ticket> findByAssignedTo(String username)`
- `List<Ticket> findByStatus(TicketStatus status)`

---

### 6. Spring ApplicationEvent

`event/TicketStatusChangedEvent.java`
- Extends `ApplicationEvent`
- Fields: ticketId (Long), previousStatus (TicketStatus), newStatus (TicketStatus), triggeredBy (String username)

---

### 7. JWT Utility (update existing)

`security/JwtUtil.java`
- `generateToken(String username, String role)` — embed role as JWT claim `"role"`
- `extractUsername(String token)` — existing
- `extractRole(String token)` — new: reads `"role"` claim
- `validateToken(String token)` — existing
- `getRemainingTtlMs(String token)` — existing

---

### 8. JWT Auth Filter (update existing)

`security/JwtAuthFilter.java`
- After extracting username, also call `jwtUtil.extractRole(token)`
- Build `UsernamePasswordAuthenticationToken` with `List.of(new SimpleGrantedAuthority(role))` as authorities

---

### 9. Security Config (update existing)

`config/SecurityConfig.java`
- Add `@EnableMethodSecurity` annotation to enable `@PreAuthorize`
- Permit `/tickets/**` — authentication required, but role enforcement is at method level
- Keep existing `/auth/**` and `/actuator/health` permit rules

---

### 10. Auth Service (update existing)

`service/AuthService.java`
- `register()` — persist `role` from `RegisterRequest` onto the `User` entity
- `login()` — fetch user's role and pass to `jwtUtil.generateToken(username, role.name())`
- Add detailed comments on each logical block

---

### 11. Token Blacklist Service — no changes needed

---

### 12. Ticket Workflow Service (new — detailed comments required)

`service/TicketWorkflowService.java`

Inject: `TicketRepository`, `RuntimeService`, `TaskService`, `ApplicationEventPublisher`

Implement with **detailed inline comments on every code segment**:

```
createTicket(CreateTicketRequest, String createdByUsername)
  → save Ticket (status=OPEN)
  → startProcessInstanceByKey("ticket-approval-process", vars: ticketId, assignee)
  → store processInstanceId on Ticket
  → publish TicketStatusChangedEvent(null → OPEN)
  → return TicketResponse

assignTicket(Long ticketId, String claimantUsername)
  → load Ticket, verify status=OPEN
  → query Flowable task by processInstanceId
  → taskService.claim(taskId, claimantUsername)
  → update Ticket status=IN_PROGRESS, assignedTo=claimantUsername
  → publish event
  → return TicketResponse

submitForApproval(Long ticketId, String submitterUsername)
  → load Ticket, verify status=IN_PROGRESS
  → taskService.complete(taskId)  ← this advances process to reviewTicket UserTask
  → update Ticket status=PENDING_APPROVAL
  → publish event
  → return TicketResponse

approveTicket(Long ticketId, String approverUsername)
  → load Ticket, verify status=PENDING_APPROVAL
  → taskService.complete(taskId, vars: decision="APPROVE")
  → update Ticket status=APPROVED
  → publish event
  → return TicketResponse

rejectTicket(Long ticketId, String reason, String rejectorUsername)
  → load Ticket, verify status=PENDING_APPROVAL
  → taskService.complete(taskId, vars: decision="REJECT", rejectionReason=reason)
  → update Ticket status=IN_PROGRESS (loop back), rejectionReason=reason
  → publish event
  → return TicketResponse

getTicket(Long ticketId)
  → load Ticket
  → query current Flowable task for processInstanceId (may be null if process ended)
  → return TicketResponse with currentTaskId

listTickets(TicketStatus filter)
  → findByStatus or findAll
  → return List<TicketResponse>
```

---

### 13. Ticket Controller (new)

`controller/TicketController.java`

```
POST   /tickets              @PreAuthorize("hasRole('EMPLOYEE')")  → createTicket
GET    /tickets              (authenticated)                        → listTickets(?status=)
GET    /tickets/{id}         (authenticated)                        → getTicket
POST   /tickets/{id}/assign  @PreAuthorize("hasRole('EMPLOYEE')")  → assignTicket
POST   /tickets/{id}/submit  @PreAuthorize("hasRole('EMPLOYEE')")  → submitForApproval
POST   /tickets/{id}/approve @PreAuthorize("hasRole('MANAGER')")   → approveTicket
POST   /tickets/{id}/reject  @PreAuthorize("hasRole('MANAGER')")   → rejectTicket
```

All return `ResponseEntity<TicketResponse>` or `ResponseEntity<List<TicketResponse>>`.

---

### 14. Exception Handler (update existing)

`exception/GlobalExceptionHandler.java`
- Add: `FlowableObjectNotFoundException` → 404
- Add: `FlowableException` (catch-all Flowable) → 422 `{ "error": "Workflow error: ...", "status": 422 }`

---

### 15. BPMN Process Definition (new)

`src/main/resources/processes/ticket-approval.bpmn20.xml`

Process id and key: `ticket-approval-process`

Structure:
- StartEvent id=`start`
- UserTask id=`workOnTicket` name="Work on Ticket" flowable:assignee=`${assignee}`
- UserTask id=`reviewTicket` name="Review Ticket" flowable:candidateGroups=`MANAGER`
- ExclusiveGateway id=`approvalDecision`
- SequenceFlow condition=`${decision == 'APPROVE'}` → EndEvent id=`approved`
- SequenceFlow condition=`${decision == 'REJECT'}` → back to `workOnTicket`

---

## Output

After all files are written, run:
```bash
mvn compile -DskipTests 2>&1 | tail -20
```
Then print:
```
DEV-AGENT DONE
Files written: [list]
Compile status: [PASS or FAIL + error snippet]
```
