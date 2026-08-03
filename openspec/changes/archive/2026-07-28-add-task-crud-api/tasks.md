# Tasks: add-task-crud-api

## 1. Project Scaffolding

- [x] 1.1 Initialize Gradle project: `settings.gradle.kts`, `build.gradle.kts` (Kotlin DSL), `gradle/libs.versions.toml`, committed Gradle wrapper, JDK 21 toolchain
- [x] 1.2 Add dependencies via version catalog: Ktor 3.x (server-netty, content-negotiation, kotlinx-serialization-json), StatusPages, logback; test: Ktor `testApplication`, JUnit 5
- [x] 1.3 Add `.gitignore` (Gradle, IDE, build outputs)
- [x] 1.4 Create Ktor application entry point and module (Netty, JSON content negotiation) that boots an empty server; verify `./gradlew run` starts

## 2. Domain Model & Repository

- [x] 2.1 Create `Task` domain model (`id: String`, `title`, `dueDate: LocalDate?`, `category: String?`, `completed: Boolean`) in `model` package
- [x] 2.2 Define `TaskRepository` interface (create, findAll with optional category, findById, update, delete)
- [x] 2.3 Implement `InMemoryTaskRepository` on `ConcurrentHashMap` with injectable id generator (`() -> String`, production default UUID)
- [x] 2.4 Unit tests for `InMemoryTaskRepository` (given-when-then naming, deterministic id generator): create assigns id, findAll, category filter, findById hit/miss, update preserves id, delete removes

## 3. Service Layer

- [x] 3.1 Implement `TaskService` wrapping the repository: create (default `completed=false`), list with optional category filter, get, full-replace update, delete; not-found signaled to callers
- [x] 3.2 Unit tests for `TaskService` (given-when-then naming): each operation's success path and not-found behavior

## 4. HTTP API

- [x] 4.1 Create `TaskRequest`/`TaskResponse` DTOs with kotlinx.serialization, ISO-8601 `LocalDate` serializer for `dueDate`, mapping to/from domain `Task`
- [x] 4.2 Implement routes under `/api/v1/tasks`: POST (201), GET list with `?category=` filter (200), GET by id (200), PUT full-replace (200), DELETE (204)
- [x] 4.3 Install StatusPages: unknown id → 404, blank/missing title, malformed JSON, invalid `dueDate` → 400; all errors return `{"message": "..."}` JSON body
- [x] 4.4 Wire repository → service → routes by constructor injection in the application module

## 5. Integration Tests

- [x] 5.1 `testApplication` test: application starts and responds (GET list returns 200 with empty array)
- [x] 5.2 `testApplication` CRUD round-trip: create → get → list (with and without category filter) → update (including mark completed) → delete → get returns 404
- [x] 5.3 `testApplication` error cases: blank title 400, malformed JSON 400, invalid dueDate 400, unknown id 404 on get/put/delete

## 6. Docs & Tooling

- [x] 6.1 Write `README.md`: app purpose/vision (personal aid app, AI agents later), tech stack, build/test/run guide (`./gradlew build`, `./gradlew test`, `./gradlew run`), API overview, in-memory storage caveat
- [x] 6.2 Create `api.http` (JetBrains format) exercising all five endpoints, including category-filtered list, mark-completed PUT, and an error example

## 7. Verification

- [x] 7.1 Run full build and all tests green (`./gradlew build`)
- [x] 7.2 Start the app and exercise every `api.http` request against the running server, confirming spec status codes and bodies
