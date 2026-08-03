# Design: add-task-crud-api

## Context

Greenfield repository — no code exists yet. This change bootstraps the personal aid application with its first slice: a Kotlin/Ktor HTTP API for task CRUD with in-memory storage. The long-term vision (AI agents, mobile client, persistence) does not need to be built now, but the seams for it should exist: the API will later gain other consumers, and the in-memory store will later be replaced by a database. Chosen stack per the proposal: Kotlin, Ktor, Gradle.

## Goals / Non-Goals

**Goals:**

- Working HTTP API at `/api/v1/tasks` with full CRUD, clean JSON contracts, and correct error semantics.
- Clear layering (`routes → service → repository`) so storage and consumers can evolve independently.
- Test coverage: unit tests for service/repository, integration tests proving the app starts and endpoints respond.
- Developer ergonomics: README with purpose and build/run guide, JetBrains `.http` file for manual testing.

**Non-Goals:**

- Persistence (database) — in-memory only; data loss on restart is accepted.
- Authentication/authorization — single-user, local use for now.
- AI agent integration, mobile app, deployment/packaging (Docker, CI).
- PATCH/partial updates, pagination, sorting — list supports only an optional category filter.

## Decisions

### 1. Ktor 3.x with Netty engine, kotlinx.serialization

Ktor is the requested framework; version 3.x is current. kotlinx.serialization over Jackson because it is Ktor's native integration, Kotlin-first (null-safety aware), and needs no reflection. Netty is the default, battle-tested engine. Alternative considered: Jackson (more ecosystem tooling) — unnecessary here since contracts are simple.

### 2. Gradle Kotlin DSL with version catalog, JDK 21

`build.gradle.kts` + `gradle/libs.versions.toml` keeps dependency versions in one place as the project grows into more modules later. JDK 21 is the current LTS. Gradle wrapper committed so the build is reproducible.

### 3. Single module, layered packages

One Gradle module under base package `ro.jf.ai.assistant` with packages `routes`, `service`, `repository`, `model`, `transfer` (DTOs), and `exception`. Multi-module or hexagonal structure was considered and rejected as ceremony for a single capability; the package-level seam is enough to swap the repository for a database-backed one later.

### 4. Manual dependency wiring (no DI framework)

Dependencies are wired by constructor injection in the Ktor application module. Koin/Kodein considered and rejected — with three classes, a DI container adds a dependency and indirection for no benefit. Revisit when the object graph grows.

### 5. Task id is an opaque `String`; repository owns generation via injectable generator

The model exposes `id: String`. The in-memory repository generates ids using an injected `() -> String` generator (production: `UUID.randomUUID().toString()`). Rationale: the model stays decoupled from any id scheme (a future database or external creator can use its own), and tests can inject deterministic ids. Alternative considered: `java.util.UUID` type on the model — rejected as it leaks an implementation detail into the API contract.

### 6. Separate HTTP DTOs from the domain model, one DTO per operation

Request/response types live in a dedicated `transfer` package and are mapped to/from the domain `Task`:

- `CreateTaskRequest` (POST) and `UpdateTaskRequest` (PUT), currently identical in shape (`title` required non-blank, `dueDate?`, `category?`, `completed` defaulting to `false`) but kept separate so the two contracts can evolve independently.
- `TaskResponse` (GET/all responses): `id`, `title`, `dueDate?`, `category?`, `completed`.

`TaskService` accepts the request DTOs directly instead of exploded parameter lists — fewer arguments at the cost of coupling the service to the transfer types, accepted at this app size. `dueDate` is serialized as ISO-8601 date (`yyyy-MM-dd`) via a `LocalDate` serializer. Alternative considered: serializing the domain model directly — rejected because create must not accept an id and the domain type should not carry serialization annotations.

### 7. In-memory store on `ConcurrentHashMap`

Thread-safe without locks for the operations needed. List returns insertion-order-independent results (no ordering guarantee in v1). Alternative: `LinkedHashMap` + synchronization for stable ordering — not worth it until ordering is a requirement.

### 8. Error handling via StatusPages with a JSON error body

Central `StatusPages` installation maps failures to `{ "message": "..." }` bodies: unknown id → 404, invalid/missing body fields or malformed JSON → 400. Service signals not-found via `TaskNotFoundException`, housed in a dedicated `exception` package. Keeps routes thin and error contracts consistent.

### 9. API versioning under `/api/v1`

All routes mounted under `/api/v1`. Costs nothing now, spares breaking mobile/agent clients later. PUT is full-replace per REST semantics; marking a task done is `PUT` with `completed: true` (no custom `/complete` endpoint).

### 10. Testing: JUnit 5 + Ktor `testApplication`

- Unit tests (given-when-then method naming) for the service and in-memory repository, using an injected deterministic id generator.
- Integration tests use Ktor's `testApplication` with the real application module: verify the app boots, content negotiation works, and each endpoint responds correctly end-to-end against the in-memory store.
- kotest considered; JUnit 5 chosen as the lower-dependency default. Revisable.

## Risks / Trade-offs

- [Data loss on restart] → Accepted by design for v1; README states it explicitly. The repository interface is the seam for a future persistence change.
- [Free-form categories drift ("Work" vs "work")] → Accepted for v1; normalization is a plausible future agent capability.
- [PUT full-replace lets a client accidentally reset fields it omits] → Mitigated by making `title` required in the request and documenting PUT semantics in the README and `.http` file examples.
- [No ordering guarantee on list] → Acceptable while clients are humans with an `.http` file; revisit with the mobile client.
- [Single-instance concurrency only] → `ConcurrentHashMap` is safe within one process; horizontal scaling is out of scope until persistence exists.

## Open Questions

None blocking. Deferred deliberately: due date time-of-day, category normalization, pagination/sorting, persistence.
