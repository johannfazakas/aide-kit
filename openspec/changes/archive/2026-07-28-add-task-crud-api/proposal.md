# Proposal: add-task-crud-api

## Why

This repository is the starting point for a personal aid application: a backend service (and later a mobile app) where one or more AI agents help manage aspects of daily life, starting with tasks. Nothing exists yet — this change bootstraps the project with its first usable slice: an HTTP API to manage tasks, with in-memory storage, so the core domain and API conventions are established before persistence, agents, or clients are added.

## What Changes

- Bootstrap a new Kotlin + Ktor + Gradle project (single module, JDK 21, Gradle Kotlin DSL with version catalog, kotlinx.serialization, Netty engine).
- Add a Task domain model: `id` (server-generated opaque string, UUID under the hood), `title` (required), `dueDate` (optional, date-only), `category` (optional, free-form string), `completed` (boolean, defaults to false).
- Add a versioned REST CRUD API under `/api/v1/tasks`: create, list (with optional category filter), get by id, full-replace update (PUT), delete.
- Add an in-memory task repository with an injectable id generator.
- Add unit tests (given-when-then naming) for service and repository, plus integration tests via Ktor `testApplication` verifying the application starts and endpoints respond.
- Add a README covering the app's purpose/vision and a build & run guide.
- Add a JetBrains-format `.http` file exercising all API endpoints for manual testing.

## Capabilities

### New Capabilities

- `task-management`: CRUD management of tasks over a versioned REST API — task model, endpoint behavior, validation, and error semantics (404 on unknown id, 400 on invalid body).

### Modified Capabilities

<!-- none — greenfield change -->

## Impact

- **Code**: new Gradle project at the repository root (`build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, wrapper) with source under `src/main/kotlin` (`routes` / `service` / `repository` / `model` / `transfer` / `exception` packages, base package `ro.jf.ai.assistant`) and tests under `src/test/kotlin`.
- **APIs**: new public HTTP surface `/api/v1/tasks` (JSON). No existing consumers, so no compatibility concerns.
- **Dependencies**: Ktor 3.x (server-netty, content-negotiation, kotlinx-serialization), kotlinx-serialization-json, test tooling (Ktor `testApplication`, JUnit 5 or kotest).
- **Docs/tooling**: `README.md`, `api.http` (JetBrains HTTP client format), `.gitignore`.
- **Storage**: in-memory only — data is lost on restart by design; persistence is a future change. The `routes → service → repository` seam is the planned replacement point for a database later.
