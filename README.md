# AI Assistant

A personal aid application. The long-term goal is a backend service and a mobile app where one or more AI agents help manage aspects of daily life — starting with task management, with more capabilities to come.

The current slice is an HTTP API for managing tasks (stored in memory) plus a conversational AI assistant that manages those tasks through natural language.

## Tech Stack

- Kotlin on JDK 21
- Ktor 3 (Netty engine, kotlinx.serialization)
- [Koog](https://github.com/JetBrains/koog) agent framework (`koog-ktor` plugin)
- Gradle (Kotlin DSL, version catalog)

## Build & Run

Build and run all tests:

```shell
./gradlew build
```

Run only the tests:

```shell
./gradlew test
```

Start the server (listens on http://localhost:8080):

```shell
./gradlew run
```

## API

Tasks are managed under `/api/v1/tasks`. A task has an `id` (server-generated string), a `title` (required), an optional `dueDate` (`yyyy-MM-dd`), an optional free-form `category`, and a `completed` flag.

| Method | Path                        | Description                          | Success |
|--------|-----------------------------|--------------------------------------|---------|
| POST   | `/api/v1/tasks`             | Create a task                        | 201     |
| GET    | `/api/v1/tasks`             | List tasks (optional `?category=`)   | 200     |
| GET    | `/api/v1/tasks/{id}`        | Get a task by id                     | 200     |
| PUT    | `/api/v1/tasks/{id}`        | Full-replace update (omitted optional fields are unset) | 200 |
| DELETE | `/api/v1/tasks/{id}`        | Delete a task                        | 204     |

Errors return a JSON body `{"message": "..."}` — `404` for unknown ids, `400` for invalid input (missing/blank title, malformed JSON, invalid date format).

Marking a task done is a `PUT` with `"completed": true` — there is no separate complete endpoint.

Example:

```shell
curl -X POST http://localhost:8080/api/v1/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title": "Pay rent", "dueDate": "2026-07-31", "category": "home"}'
```

The [api.http](api.http) file (JetBrains HTTP client format) exercises every endpoint and can be run directly from IntelliJ IDEA.

## Assistant

`POST /api/v1/chat` takes `{"message": "..."}` and returns `{"reply": "..."}`. The assistant is a Koog agent that manages tasks through tools backed by the same service as the REST API: it can list, inspect, create, update, and complete tasks — it cannot delete them yet.

Conversations are stateless: each request starts fresh, with no memory of previous exchanges. The assistant does not know the current date, so it asks for exact dates instead of resolving "tomorrow". When an instruction is ambiguous, it asks a clarifying question in its reply.

### Configuration

The assistant calls the [OpenCode Zen](https://opencode.ai/docs/zen/) gateway (OpenAI-compatible API) with the `glm-5.2` model. It needs an API key:

```shell
export OPENCODE_API_KEY=<your key>
./gradlew run
```

By default requests go to the pay-per-token endpoint (`https://opencode.ai/zen`). On the opencode Go subscription, point `OPENCODE_BASE_URL` at the subscription endpoint instead:

```shell
export OPENCODE_BASE_URL=https://opencode.ai/zen/go
```

Without `OPENCODE_API_KEY`, the server still starts and the task API works normally; chat requests return `503`. Chat validation errors (missing/blank `message`) return `400`. Failures from the LLM gateway (rejected key, model errors, outages) return `502` with the underlying reason in the error body.

The model is defined in one place (`agent/AssistantModel.kt`) — swap the `id` for another [OpenCode Zen model](https://opencode.ai/docs/zen/) to change it. Note that chat requests call a paid external API; nothing calls the LLM at build or test time.

## Storage Caveat

Tasks are held **in memory only** — all data is lost when the server stops. Persistence is planned as a future change.
