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

## Status

Schema, JPA layer, and MCP tool layer all built and verified end-to-end
against a real Postgres — create, conflicting write (rejected, zero DB
writes), correct-version write (accepted, version incremented), and the
resulting audit trail all confirmed via the raw MCP streamable-HTTP protocol.

Server exposes 3 tools over MCP streamable-HTTP at `POST /mcp`:
`memory_list`, `memory_read`, `memory_write` (see `MemoryTools.java`).
`memory_write` is create-or-update: omit `expectedVersion` to create a new
path, pass the version from `memory_read` to update an existing one. A
version mismatch — or a genuine race caught by JPA's `@Version` at flush
time — comes back as an MCP error result (`isError: true`), which Claude
sees and can act on by re-reading and retrying.

Test suite: 19 tests, all green.
- `MemoryServiceIT` (12, Testcontainers + real Postgres) — create, conflict
  paths, read, list filters, the audit trail, soft-delete-and-reuse-path, and
  a genuine two-thread concurrency race (asserts exactly one of two
  simultaneous conflicting writes succeeds — verified stable across repeated
  runs, not just a single pass).
- `MemoryToolsTest` (6, Mockito) — category parsing, DTO mapping, parameter
  delegation at the MCP tool boundary.
- `TeamMemoryMcpApplicationTests` (1) — full context/wiring smoke test.

Run `./mvnw test` for the fast unit tests, `./mvnw verify` for the full
suite including the container-backed integration tests (needs Docker
running). `MemoryServiceIT` uses the `*IT` naming convention on purpose —
Maven Failsafe (bound to `verify`) picks those up, Surefire (`test`) doesn't,
keeping the fast/slow split explicit as more tests get added.

**Not yet done:**
- No `actor` auto-detection — callers must pass their own identifier today
  (e.g. `$USER`). Worth revisiting once real MCP session-identity support is
  confirmed.
- Not deployed anywhere — currently local-only (`docker compose up` +
  `./mvnw spring-boot:run`). Teammates need a running instance at a shared
  URL, plus the corresponding `CLAUDE.md` convention (read-first, write-last)
  wired into their `mcpServers` config.
- No MCP-protocol-level integration test (i.e. an actual JSON-RPC round trip
  against a running server) — that flow was verified manually via curl
  during development but isn't automated yet.

## Local development

```bash
docker compose up -d       # starts Postgres on localhost:5432
./mvnw spring-boot:run      # runs Flyway migrations, then the app on :8080
```

### Two Spring Boot 4.1.1 gotchas hit while building this (both fixed, worth remembering)

- `flyway-core` alone does **not** wire up Flyway anymore — autoconfiguration
  moved behind the dedicated `spring-boot-starter-flyway` artifact. Without
  it, Flyway silently never runs (no error, absent from even the `--debug`
  condition-evaluation report).
- `@Lob` on a `String` field maps to Postgres `oid` (large object) by
  default in this Hibernate version, not `text`. Removed it — these are
  ordinary text fields, plain `String` maps to `TEXT` correctly.

## Prior art evaluated

Before building this, we checked for existing solutions:

- **Anthropic's own tracker**: shared team memory for Claude Code is an
  open, unresolved feature request
  ([anthropics/claude-code#38536](https://github.com/anthropics/claude-code/issues/38536)) —
  not natively supported.
- **`pg-mnemosyne-mcp`** (closest open-source match): Postgres-backed, but
  its "coordination hub" is a presence bulletin board only — no version
  column, no optimistic locking, no audit trail, and a `run_sql` tool that's
  a real SQL-injection surface for a shared multi-user store. Not viable to
  fork as-is.
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
