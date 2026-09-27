# Spec Review: SPEC-001 Quality Bar Analysis

**Reviewer:** Megha Kathuria  
**Date:** 2026-09-23  
**Subject:** SPEC-001-user-scoped-tasks.md (as shipped after TASK-010)  
**Purpose:** Teaching exercise — applying a reviewer's quality bar to our own spec.

---

## Part 1 — The Framework: What Makes an AC Testable?

An AC is testable when a person who did not write the code can turn it into a pass/fail
assertion without making any judgment calls.

The minimum required elements:

```
[Precondition]  Given: a registered user "alice"
[Action]        When:  POST /api/auth/login { "username": "alice", "password": "wrong" }
[Observable]    Then:  response status = 401, body = { "error": "invalid_credentials" }
```

**Observable** is the key word. The outcome must be something that appears in:
- The HTTP response (status, headers, body)
- A subsequent API call (side effects — e.g. create then GET to confirm)
- A log line (only if the spec says *how* to inspect it)
- A DB row (only if the spec names the verification query)

An AC that requires the reader to make a judgment call ("handle gracefully",
"perform well", "should be secure") is descriptive, not testable.

---

## Part 2 — Anti-Pattern Taxonomy

These are the patterns found in this spec. Each is real, not hypothetical.

### AP-1: Untestable Clause Smuggled Into a Behavioral AC

> AC-1: "...password is stored hashed (BCrypt)."

The first half of AC-1 is testable (201 + body shape). The BCrypt clause is not —
you cannot observe password storage through the API response.

**Fix:** Move it to NFRs and name the test type:
> NFR-Security-3: BCrypt cost factor ≥ 12. Verified by: unit test asserting
> `SecurityConfig.passwordEncoder()` returns a `BCryptPasswordEncoder` with strength 12.

---

### AP-2: Ambiguous Field Type in Response Contract

> AC-3: "...returns 200 with `{token, expiresIn}`."

`expiresIn`: seconds? milliseconds? a string? an integer? The test `andExpect(jsonPath("$.expiresIn").value(3600))` happened to pass, but that's because the author wrote both the spec and the test. A second engineer would need to guess.

**Fix:**
> AC-3 (revised): Returns 200 with `{ "token": "<string>", "expiresIn": 3600 }` where
> `expiresIn` is an integer representing seconds until expiry.

---

### AP-3: Happy Path Only — No Failure Mode Defined

> AC-9: `DELETE /api/tasks/{id}` removes the task and returns 204 No Content.

Three missing cases that any reviewer would look for:

| Missing case | Expected behavior | Consequence of not specifying |
|---|---|---|
| `{id}` does not exist | 404? 204 idempotent? | Implementor decides; caller cannot rely on it |
| `{id}` belongs to another user | 403? 404? | Information leak decision made silently in code |
| Same DELETE called twice | 404 on second call? 204 idempotent? | Client retry logic broken |

Same gap exists in AC-6 (no 400 for missing `title`) and AC-7 (no `[]` for empty list).

**Fix:** For every mutating AC, add at minimum: missing resource, wrong owner, invalid input.

---

### AP-4: Security Decision Left to the Implementor

> AC-8: `PUT /api/tasks/{id}` for a task owned by a different user returns 403 Forbidden.

This covers PUT but is silent on GET, DELETE, and on non-existent IDs. More importantly,
it doesn't answer the question that has a security consequence:

> If task `{id}` does not exist, should the response be 403 or 404?

- Return **404** → an attacker can enumerate valid task IDs by observing the difference
  between 403 and 404.
- Return **403** always → safe, but usability cost (caller can't distinguish "not yours"
  from "doesn't exist").

This is a product decision. Leaving it out of the spec means the implementor chose 404
(that's what the code does — see `TaskService.getOne`), but no one explicitly agreed to that trade-off.

**Fix:** Add an explicit AC:
> AC-8b: `GET /api/tasks/{id}`, `PUT /api/tasks/{id}`, and `DELETE /api/tasks/{id}` for a
> task ID that exists but belongs to another user return 403. A task ID that does not exist
> returns 404. **Rationale:** Chosen for DX; task IDs are opaque integers with no guessable pattern.

---

### AP-5: Asymmetric Error Path — User Enumeration Gap

> AC-4: `POST /api/auth/login` with wrong password returns 401 Unauthorized.

AC-3 covers valid credentials; AC-4 covers wrong password. Neither covers: **username that
does not exist**. This has a security consequence:

If "wrong password for existing user" → `401 invalid_credentials`  
And "username does not exist" → `404` (or a different error body)

Then an attacker can distinguish registered usernames from unregistered ones.

Our implementation happens to return the same 401 for both (check `AuthService.login` — it
throws `BadCredentialsException` for both cases), but that's luck. The spec doesn't require it.

**Fix:** Merge into one AC:
> AC-4 (revised): `POST /api/auth/login` with a non-existent username, wrong password, or
> any credential mismatch returns 401 with `{ "error": "invalid_credentials" }`. The response
> MUST be identical for all failure modes to prevent user enumeration.

---

### AP-6: Unquantified NFR

> Performance: Auth endpoints must respond in < 500 ms under normal load.

"Under normal load" is undefined. Questions a QA engineer would ask immediately:

- How many concurrent requests?
- p50? p95? p99? (BCrypt at cost 12 takes ~250ms for the hash alone, so p99 under contention
  could easily exceed 500ms even on healthy hardware)
- What counts as an "auth endpoint"? Register and login? Logout?
- What's the measurement environment — dev laptop? staging?

An unquantified performance NFR has never blocked a ship and never will, because it
cannot be failed.

**Fix:**
> NFR-Perf-1: `POST /api/auth/login` p95 latency ≤ 500 ms measured at 10 concurrent users
> in the staging environment, excluding BCrypt warm-up on first request.
> Verification: Gatling script `LoginLoadTest` in `/test/load/`.

---

### AP-7: NFR Not Observable From the API

> Security: JWT signed with HS256; secret loaded from `app.jwt.secret`.

You cannot verify the signing algorithm from an HTTP response. You could decode the JWT
header (base64url), but the spec doesn't say this is the verification method.

Same issue: BCrypt cost factor — not visible in any API response.

**Fix:** For each security NFR that is not API-observable, name the verification method:

| NFR | Verification method |
|---|---|
| BCrypt cost ≥ 12 | Unit test: `SecurityConfigTest.passwordEncoder_usesCostFactor12` |
| JWT alg = HS256 | Unit test: decode token header, assert `alg == HS256` OR `JwtUtilTest.generatedToken_hasHS256Header` |
| Secret ≥ 32 chars | `JwtUtil` constructor guard (existing) + startup smoke test |

---

### AP-8: Undefined Reference Term in AC

> AC-6: "...returns 201 with full task DTO."

"Full task DTO" is not defined in the ACs. A reviewer must hunt the Data Model section
to infer what fields are expected. This is worse than it looks in a long spec — or when
the DTO evolves and no one updates the AC.

**Fix:** Either inline the shape or name the section:
> AC-6 (revised): Returns 201 with a Task object as defined in §5 Data Model:
> `{ id, title, description, status, ownerId, createdAt, updatedAt }`.

---

### AP-9: Missing Input Validation ACs

The spec defines validation in the Data Model (VARCHAR(50), NOT NULL) and in the code
(`@NotBlank`, `@Size`), but no AC covers what happens when a caller violates those constraints:

| Missing AC | Scenario | Expected response |
|---|---|---|
| AC-11 | POST /api/auth/register with `username` < 3 chars | 400 |
| AC-12 | POST /api/auth/register with `password` < 6 chars | 400 |
| AC-13 | POST /api/tasks with missing/blank `title` | 400 |
| AC-14 | POST /api/tasks with `title` > 255 chars | 400 |
| AC-15 | POST /api/tasks with invalid `status` value | 400 |

Without these, a consumer of the API who reads only the spec has no idea these constraints exist,
and a tester has no basis for boundary testing.

**Fix:** Add input-validation ACs for every field with a length or format constraint. The pattern is:
> AC-N: `<endpoint>` with `<field>` violating `<constraint>` returns 400 with an error body
> that identifies the offending field.

---

### AP-10: No Rollback / Atomicity Statement on Mutations

The spec defines logout as "add token to in-memory blacklist". It does not say:

- What happens if the server restarts mid-request? (Loss of blacklist — spec acknowledges
  non-persistence, but the logout endpoint gives no indication to callers)
- Is `POST /api/auth/logout` idempotent? (Calling it with the same token twice should still return 200)
- Is there any confirmation the token was actually in the blacklist, or does logout always return 200?

This matters because callers implementing "logout and redirect" need to know whether they
can rely on the 200 to mean "you are now logged out" or "request was received".

**Fix:**
> Logout contract: `POST /api/auth/logout` always returns 200 regardless of whether the token
> was valid or already revoked. Callers MUST NOT assume a 200 means the token is unreachable —
> if another client has a copy of the token, it may remain usable until expiry. This is a
> known limitation of stateless JWT with in-memory blacklisting.

---

## Part 3 — Per-AC Scorecard

| AC | Testable? | Missing failure modes | Missing edge cases | Security gap | Verdict |
|----|-----------|-----------------------|--------------------|--------------|---------|
| AC-1 | Partial | — | Invalid inputs (length, blank) | BCrypt clause not observable | Revise |
| AC-2 | ✅ | — | Case-insensitive duplicate? | — | Minor gap |
| AC-3 | Partial | Non-existent username | `expiresIn` type unspecified | User enumeration | Revise |
| AC-4 | Partial | Non-existent username | — | User enumeration | Merge with AC-3 |
| AC-5 | Partial | Malformed token, expired token, revoked token | — | — | Expand |
| AC-6 | Partial | Missing title (400), invalid status (400) | Title > 255 chars | — | Revise |
| AC-7 | ✅ | Empty list (200 + `[]`) | Pagination at scale | — | Acceptable |
| AC-8 | Partial | Non-existent ID (403 vs 404 decision), GET/DELETE not covered | — | Info leak | Revise + add AC-8b |
| AC-9 | Partial | Non-existent ID, wrong owner, idempotency | — | — | Revise |
| AC-10 | Partial | Observable effect not stated (just "expire" — 401 where?) | Clock skew / tolerance | — | Revise |

**Summary:** 2 of 10 ACs are unambiguously testable as written (AC-2, AC-7). The other 8 each
require at least one judgment call by the implementor or tester.

---

## Part 4 — A Rewritten AC for Reference

Here is AC-4 rewritten to eliminate all the gaps identified above:

**Original:**
> AC-4: `POST /api/auth/login` with wrong password returns 401 Unauthorized.

**Revised:**
> **AC-4:** `POST /api/auth/login` returns 401 with body `{ "error": "invalid_credentials" }`
> for ALL of the following cases:
> - The `username` field matches no registered user.
> - The `username` matches a registered user but `password` is incorrect.
>
> The response body and status MUST be identical for both cases to prevent user
> enumeration. Implementations MUST NOT return 404 for an unknown username.
>
> Testable by: (a) attempt login with unregistered username; (b) register user, attempt
> login with wrong password; assert both return status 401 and identical body.

This version has zero judgment calls. A QA engineer, a second developer, or a contract
test can all derive the same assertion from this text.

---

## Takeaways for Future Specs

1. **Each AC must name its observable output** — HTTP status + body shape minimum.
2. **Every mutating AC needs its failure siblings** — at least: invalid input, missing resource, wrong actor.
3. **Security decisions belong in the spec, not in the PR** — user enumeration, info leak via 403 vs 404, error message uniformity.
4. **NFRs need a verification owner** — "who will test this, with what tool, at what threshold" or it will never be tested.
5. **Don't mix implementation details into behavioral ACs** — BCrypt, HS256, indexes are NFRs with named verification methods.
6. **Define terms used in ACs** — "full DTO" requires a reference or an inline definition.
7. **State what "revoke" means for callers** — don't make callers assume semantics not written down.
