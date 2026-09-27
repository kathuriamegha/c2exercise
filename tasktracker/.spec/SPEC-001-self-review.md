# SPEC-001 Self-Review (Spec Diff)

**Reviewer:** Megha Kathuria  
**Date:** 2026-09-23 (updated)  
**Commit:** 6804b04  
**Spec Version:** 2026-09-23 (2 drift entries applied, TASK-009/010 added)

---

## AC Traceability Check

| AC    | Criterion Summary | Test | Code Location | Status |
|-------|-------------------|------|---------------|--------|
| AC-1  | POST /register → 201 {id, username} | `AuthIntegrationTest.register_success` | `AuthController.register`, `AuthService.register` | ✅ PASS |
| AC-2  | Duplicate username → 409 | `AuthIntegrationTest.register_duplicateUsername_returns409` | `UsernameTakenException` → `GlobalExceptionHandler` | ✅ PASS |
| AC-3  | POST /login valid creds → 200 {token, expiresIn} | `AuthIntegrationTest.login_validCredentials_returnsToken` | `AuthController.login`, `AuthService.login` | ✅ PASS |
| AC-4  | Wrong password → 401 | `AuthIntegrationTest.login_wrongPassword_returns401` | `AuthService.login` throws `BadCredentialsException` | ✅ PASS |
| AC-5  | No token → 401 | `AuthIntegrationTest.tasks_withoutToken_returns401` | `SecurityConfig` STATELESS + `HttpStatusEntryPoint` | ✅ PASS |
| AC-6  | POST /tasks → 201 full DTO | `TaskIntegrationTest.createTask_returns201WithDto` | `TaskController.create`, `TaskResponse.from` | ✅ PASS |
| AC-7  | GET /tasks → own tasks only | `TaskIntegrationTest.listTasks_returnsOnlyOwnTasks` | `TaskRepository.findAllByOwnerId` | ✅ PASS |
| AC-8  | PUT another user's task → 403 | `TaskIntegrationTest.updateTask_anotherOwner_returns403` | `TaskService.resolveTaskWithOwnerGuard` | ✅ PASS |
| AC-9  | DELETE → 204, then 404 | `TaskIntegrationTest.deleteTask_returns204` | `TaskController.delete` | ✅ PASS |
| AC-10 | JWT expires after 1h (configurable) | `TokenExpiryAndLogoutTest$TokenExpiryTest.expiredToken_returns401` | `JwtUtil` + `@TestPropertySource(expiration-ms=1)` | ✅ PASS |

---

## Non-Functional Constraints Check

| Constraint | Verified By | Status |
|------------|-------------|--------|
| BCrypt cost factor ≥ 12 | `SecurityConfig.passwordEncoder()` → `new BCryptPasswordEncoder(12)` | ✅ |
| JWT secret ≥ 32 chars | `JwtUtil` constructor guard + dev config value | ✅ |
| JWT signed HS256 | JJWT defaults to HS256 with `Keys.hmacShaKeyFor` | ✅ |
| 4xx/5xx logged at WARN | `GlobalExceptionHandler` uses `log.warn` / `log.error` | ✅ |
| Java 17, Spring Boot 4.0 | `pom.xml` `<java.version>17</java.version>`, parent `4.0.0` | ✅ |
| JSON-only API | No XML converters added; `spring-boot-starter-webmvc` default | ✅ |
| Auth < 500ms | Not load-tested; BCrypt cost 12 ~250ms — acceptable for dev | ⚠️ NOT LOAD-TESTED |

---

## Gaps and Follow-Up Items

1. ~~**AC-10 token expiry**~~ — ✅ Closed by TASK-010 (`TokenExpiryAndLogoutTest$TokenExpiryTest`).
2. **Load test** — auth latency NFR (< 500ms) is unverified; needs JMeter/Gatling run.
3. **TASK-006 partial update** — `PUT` ignores `null` fields, spec says `{title?, description?, status?}` (all optional). ✅ confirmed correct.
4. ~~**Logout / token blacklisting**~~ — ✅ Closed by TASK-009/010 (`TokenBlacklistService`, `POST /api/auth/logout`, blacklist integration test).

---

## Drift Log Reference

See `.spec/SPEC-001-user-scoped-tasks.md` Drift Log for the two Spring Boot 4.0 package changes discovered during TASK-008 implementation.

---

**Conclusion:** All 10 ACs fully verified by 12 integration tests. One NFR (auth < 500ms) still unload-tested. All in-scope features shipped.
