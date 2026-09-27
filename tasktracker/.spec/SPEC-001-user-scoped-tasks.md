# SPEC-001: User-Scoped Task Management API

**Status:** Draft  
**Author:** Megha Kathuria  
**Created:** 2026-09-21  
**Last Updated:** 2026-09-23  

---

## 1. Problem Statement

The Task Tracker application has no concept of users or ownership. Any request can create,
read, update, or delete any task — there is no isolation between clients. This makes it
unsuitable for multi-user deployment.

We need to add user identity so that:
- Each task belongs to exactly one user.
- A user can only see and modify their own tasks.
- Access to the API requires proof of identity (a JWT bearer token).

---

## 2. Scope

**In scope:**
- User registration (username + password)
- JWT-based login (issue token on successful credential check)
- Task CRUD — all operations scoped to the authenticated user
- Logout via token blacklisting (in-memory, non-persistent)

**Out of scope:**
- Password reset / email verification
- Refresh tokens
- Role-based access control (RBAC)
- Persistent token revocation (survives restarts)

---

## 3. Acceptance Criteria

| ID   | Criterion |
|------|-----------|
| AC-1 | `POST /api/auth/register` with `{username, password}` creates a new user; returns 201 with `{id, username}`; password is stored hashed (BCrypt). |
| AC-2 | `POST /api/auth/register` with a duplicate username returns 409 Conflict. |
| AC-3 | `POST /api/auth/login` with valid credentials returns 200 with `{token, expiresIn}`. |
| AC-4 | `POST /api/auth/login` with wrong password returns 401 Unauthorized. |
| AC-5 | `GET /api/tasks` without a bearer token returns 401. |
| AC-6 | `POST /api/tasks` with a valid token creates a task owned by the token's user; returns 201 with full task DTO. |
| AC-7 | `GET /api/tasks` returns only tasks owned by the authenticated user (not other users' tasks). |
| AC-8 | `PUT /api/tasks/{id}` for a task owned by a different user returns 403 Forbidden. |
| AC-9 | `DELETE /api/tasks/{id}` removes the task and returns 204 No Content. |
| AC-10 | JWT tokens expire after 1 hour (configurable via `app.jwt.expiration-ms`). |

---

## 4. Non-Functional Constraints

| Category    | Constraint |
|-------------|-----------|
| Security    | Passwords stored with BCrypt, cost factor ≥ 12. |
| Security    | JWT signed with HS256; secret loaded from `app.jwt.secret` env/config (min 32 chars). |
| Performance | Auth endpoints must respond in < 500 ms under normal load. |
| Observability | All 4xx/5xx responses logged at WARN with request path and user identity (if available). |
| Compatibility | Java 17, Spring Boot 4.0, H2 for dev, API remains JSON-only. |

---

## 5. Data Model

```
User
  id         BIGINT PK AUTO_INCREMENT
  username   VARCHAR(50) UNIQUE NOT NULL
  password   VARCHAR(255) NOT NULL   -- BCrypt hash
  created_at TIMESTAMP NOT NULL DEFAULT NOW()

Task
  id          BIGINT PK AUTO_INCREMENT
  title       VARCHAR(255) NOT NULL
  description TEXT
  status      ENUM('TODO','IN_PROGRESS','DONE') DEFAULT 'TODO'
  owner_id    BIGINT FK → User.id NOT NULL
  created_at  TIMESTAMP NOT NULL DEFAULT NOW()
  updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
```

---

## 6. API Contract

### Auth

```
POST /api/auth/register
Body:  { "username": "alice", "password": "s3cret!" }
201:   { "id": 1, "username": "alice" }
409:   { "error": "username_taken" }

POST /api/auth/login
Body:  { "username": "alice", "password": "s3cret!" }
200:   { "token": "<jwt>", "expiresIn": 3600 }
401:   { "error": "invalid_credentials" }

POST /api/auth/logout   (requires Authorization: Bearer <token>)
200:   { "message": "logged_out" }
```

### Tasks (all require `Authorization: Bearer <token>`)

```
GET    /api/tasks              → 200 [TaskDTO]
POST   /api/tasks              Body: {title, description?, status?} → 201 TaskDTO
GET    /api/tasks/{id}         → 200 TaskDTO | 404
PUT    /api/tasks/{id}         Body: {title?, description?, status?} → 200 TaskDTO | 403 | 404
DELETE /api/tasks/{id}         → 204 | 403 | 404
```

---

## 7. Task Decomposition

Each task maps 1:1 to a feature branch and commit. Commit messages MUST include the task ID.

| Task ID    | Description | Depends On |
|------------|-------------|------------|
| TASK-001   | `User` JPA entity + `UserRepository` | — |
| TASK-002   | `Task` JPA entity + `TaskRepository` | TASK-001 |
| TASK-003   | `POST /api/auth/register` endpoint + service | TASK-001 |
| TASK-004   | JWT utility (`JwtUtil`) — generate & validate tokens | — |
| TASK-005   | `POST /api/auth/login` endpoint + Spring Security filter chain | TASK-001, TASK-004 |
| TASK-006   | Task CRUD endpoints (`TaskController`, `TaskService`) | TASK-002, TASK-005 |
| TASK-007   | Ownership guard — 403 on cross-user access | TASK-006 |
| TASK-008   | Integration tests covering AC-1 through AC-9 | TASK-003–TASK-007 |
| TASK-009   | `POST /api/auth/logout` — in-memory token blacklist (`TokenBlacklistService`), `JwtAuthFilter` rejects blacklisted tokens | TASK-004, TASK-005 |
| TASK-010   | Integration test for AC-10: expired token returns 401; integration test for logout (TASK-009) | TASK-009 |

---

## 8. Spec Drift Protocol

If implementation reveals the spec is wrong (impossible, under-specified, or contradicted by framework constraints), follow this process:

1. **Stop** — do not silently deviate from the spec.
2. **Document** — add a `## Drift Log` entry below with: date, task ID where drift was found, description of the discrepancy, and proposed resolution.
3. **Decide** — get spec sign-off before implementing the deviation.
4. **Update** — bump `Last Updated`, change the affected AC/section, mark the drift entry as Resolved.

---

## Drift Log

| # | Date | Task | Discrepancy | Resolution | Status |
|---|------|------|-------------|------------|--------|
| 1 | 2026-09-21 | TASK-008 | Spring Boot 4.0 moved `@AutoConfigureMockMvc` from `org.springframework.boot.test.autoconfigure.web.servlet` → `org.springframework.boot.webmvc.test.autoconfigure`. Spec assumed Spring Boot 3.x package layout. | Update import in all test classes; no AC change needed. | Resolved |
| 2 | 2026-09-21 | TASK-008 | Spring Boot 4.0 uses Jackson 3.x (`tools.jackson.*`). Auto-configured bean is `tools.jackson.databind.json.JsonMapper`, not `com.fasterxml.jackson.databind.ObjectMapper`. Tests that autowire `ObjectMapper` fail. | Use `JsonMapper.builder().build()` directly in tests; no AC change needed. | Resolved |
