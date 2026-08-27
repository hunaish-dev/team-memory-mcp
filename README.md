# team-memory-mcp

Shared, concurrency-safe memory for a team of engineers each running Claude
Code (or another MCP-speaking agent) against the same project — so a
decision, convention, or gotcha one person's agent learns is immediately
readable by everyone else's, instead of trapped in one person's local
`MEMORY.md` or lagging behind the last git commit.

## Why this exists

Claude Code's own memory (`MEMORY.md`) is scoped per machine, per user — it
never leaves your laptop. `CLAUDE.md` is shared via git, but only as fast as
your next commit. Neither gives a team **always-current, concurrency-safe**
shared state. Nothing off-the-shelf currently solves this well either — see
the evaluation notes below.

## Architecture

- **Postgres** holds the canonical state: one row per memory in
  `memory_entry`, addressed by `path` (e.g. `/decisions/auth-migration.md`),
  categorized (`DECISION` / `CONVENTION` / `GOTCHA` / `GLOSSARY`).
- **Optimistic locking** via JPA's `@Version` — a write against a stale
  version throws `OptimisticLockException`, mapped to HTTP 409 at the API
  layer. Caller re-reads and retries. No silent last-write-wins.
- **Immutable audit trail** in `memory_version`, populated by a DB trigger
  (`record_memory_version`, see `V1__init_schema.sql`) — so history stays
  correct even if a write bypasses the application.
- **Soft delete** (`deleted_at`) rather than hard `DELETE`, so the trigger
  can capture a `DELETED` version without the actor-on-delete problem a hard
  delete creates, and so a path can be safely reused later.
- **API-key auth**, one key per teammate, resolved server-side into the
  audit trail's `actor` — see [Authentication](#authentication) below.

## Authentication

Every request to `/mcp` requires `Authorization: Bearer <key>`
(`/actuator/health` and `/actuator/info` stay open). There is no `actor`
parameter on any tool anymore — the server resolves who's calling from the
key itself, so a client can't claim to be someone else.

Issue a key (one per teammate):

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--issue-api-key=ali"
```

Prints the raw token once — it is never stored or shown again, only its
SHA-256 hash is. Register it with Claude Code:

```bash
claude mcp add --transport http team-memory-mcp http://localhost:8080/mcp \
  --scope user --header "Authorization: Bearer <token>"
```

Keys are hashed with SHA-256, not bcrypt/argon2 — the token is a 256-bit
`SecureRandom` value, not a human-chosen password, so adaptive hashing would
only tax every request for no security benefit (same reasoning GitHub/Stripe
use for API tokens).

## Status

Schema, JPA layer, MCP tool layer, and API-key auth all built and verified
end-to-end against a real Postgres and, separately, a real Claude Code
client — create, conflicting write (rejected, zero DB writes), correct-
version write (accepted, version incremented), the resulting audit trail,
and now auth (401 with no/wrong/revoked key, correct `actor` recorded from
the authenticated key) all confirmed via the raw MCP streamable-HTTP
protocol.

Server exposes 3 tools over MCP streamable-HTTP at `POST /mcp`:
`memory_list`, `memory_read`, `memory_write` (see `MemoryTools.java`).
`memory_write` is create-or-update: omit `expectedVersion` to create a new
path, pass the version from `memory_read` to update an existing one. A
version mismatch — or a genuine race caught by JPA's `@Version` at flush
time — comes back as an MCP error result (`isError: true`), which Claude
sees and can act on by re-reading and retrying.

Test suite: 37 tests, all green.
- `MemoryServiceIT` (12, Testcontainers + real Postgres) — create, conflict
  paths, read, list filters, the audit trail, soft-delete-and-reuse-path, and
  a genuine two-thread concurrency race (asserts exactly one of two
  simultaneous conflicting writes succeeds — verified stable across repeated
  runs, not just a single pass).
- `MemoryToolsTest` (7, Mockito) — category parsing, DTO mapping, parameter
  delegation at the MCP tool boundary, including the auth-context handoff.
- `ApiKeyHasherTest` / `ApiKeyIssuerTest` (8, unit) — token generation,
  hashing, uniqueness.
- `ApiKeyAuthenticationFilterTest` (4, Mockito) — valid/missing/unknown key
  behavior at the filter level.
- `ApiKeySecurityIT` (4, Testcontainers + MockMvc) — 401 for no/wrong/revoked
  key at the real HTTP boundary; `/actuator/health` stays open.
- `McpAuthenticationIT` (1, Testcontainers + MockMvc) — the load-bearing
  test: drives the real streamable-HTTP wire protocol with a valid key and
  confirms the resulting `memory_entry.created_by` matches the key's
  teammate — proves the `McpTransportContext` plumbing works at runtime, not
  just that the auth filter runs.
- `TeamMemoryMcpApplicationTests` (1) — full context/wiring smoke test.

Run `./mvnw test` for the fast unit tests, `./mvnw verify` for the full
suite including the container-backed integration tests (needs Docker
running). `*IT`-suffixed tests use the Maven Failsafe naming convention on
purpose — Failsafe (bound to `verify`) picks those up, Surefire (`test`)
doesn't, keeping the fast/slow split explicit as more tests get added.

**Not yet done:**
- Not deployed anywhere — currently local-only (`docker compose up` +
  `./mvnw spring-boot:run`). Teammates need a running instance at a shared
  URL, plus the corresponding `CLAUDE.md` convention (read-first, write-last)
  wired into their `mcpServers` config — though this turned out to be
  optional in practice; the tool descriptions alone are enough to induce
  correct proactive behavior without any priming.
- No key revocation CLI/endpoint yet — `api_key.revoked_at` and
  `ApiKey.revoke()` exist and are exercised in tests, but there's no
  operator-facing way to call it outside a direct DB/REPL action.
- No rate limiting on the auth layer — fine for a small internal tool
  today, worth adding before any wider exposure.

## Local development

```bash
docker compose up -d       # starts Postgres on localhost:5432
./mvnw spring-boot:run      # runs Flyway migrations, then the app on :8080
```

### Spring Boot 4.1.1 gotchas hit while building this (all fixed, worth remembering)

- `flyway-core` alone does **not** wire up Flyway anymore — autoconfiguration
  moved behind the dedicated `spring-boot-starter-flyway` artifact. Without
  it, Flyway silently never runs (no error, absent from even the `--debug`
  condition-evaluation report).
- `@Lob` on a `String` field maps to Postgres `oid` (large object) by
  default in this Hibernate version, not `text`. Removed it — these are
  ordinary text fields, plain `String` maps to `TEXT` correctly.
- `TestRestTemplate` moved to a new module (`org.springframework.boot.
  resttestclient.TestRestTemplate`, package `spring-boot-resttestclient`)
  and now needs an explicit `@AutoConfigureTestRestTemplate` — no longer
  auto-wired by `@SpringBootTest(webEnvironment = RANDOM_PORT)` alone. Its
  autoconfiguration also has a hard transitive dependency
  (`spring-boot-restclient`'s `RestTemplateBuilder`) that isn't pulled in by
  default, which surfaces as a confusing `NoClassDefFoundError` deep inside
  Spring's condition evaluation, not a straightforward "missing dependency"
  message. Used `MockMvc` instead (already available transitively via
  `spring-boot-starter-webmvc-test`, needs `@AutoConfigureMockMvc`, package
  now `org.springframework.boot.webmvc.test.autoconfigure`) — no extra
  dependency needed and it doesn't require a real bound port.
- An unauthenticated request against `anyRequest().authenticated()` returns
  **403, not 401**, unless you configure an explicit
  `AuthenticationEntryPoint`. Spring Security's `AnonymousAuthenticationFilter`
  runs by default, so a request with no credentials isn't "unauthenticated"
  from the framework's point of view — it's authenticated *as anonymous*,
  which then fails the authorization check (403) rather than the
  authentication check (401). Fixed with
  `.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`.

## Prior art evaluated

Before building this, I checked for existing solutions:

- **Anthropic's own tracker**: shared team memory for Claude Code is an
  open, unresolved feature request
  ([anthropics/claude-code#38536](https://github.com/anthropics/claude-code/issues/38536)) —
  not natively supported.
- **The closest self-hostable, open-source alternative** was Postgres-backed,
  but its take on multi-agent coordination was a presence "bulletin board"
  only — no version column, no optimistic locking, no audit trail. It solves
  visibility, not the actual concurrency-safety problem, so it wasn't a
  viable base to build on.
- **`evalops/shared-memory-mcp`**: solves a different problem (one
  operator's parallel subagent fleet), not separate teammates on separate
  machines.
- **Context Cloud, mem0, Basic Memory, claude-mem, MemPalace**: single-player
  or unverified-vendor-claim territory; none fit a self-hosted team setup
  with real concurrency guarantees.

## Path taxonomy convention

- `/decisions/` — architectural decisions, "why X not Y"
- `/conventions/` — team coding standards, naming rules
- `/gotchas/` — known pitfalls, flaky tests, footguns
- `/glossary/` — project-specific domain terms

## License

[MIT](LICENSE)
