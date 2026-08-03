# AI Assistant

A personal aid application. The long-term goal is a backend service and a mobile app where one or more AI agents help manage aspects of daily life — starting with task management, with more capabilities to come.

The current slice is an HTTP API for managing tasks, stored in memory.

## Tech Stack

- Kotlin on JDK 21
- Ktor 3 (Netty engine, kotlinx.serialization)
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

## Storage Caveat

Tasks are held **in memory only** — all data is lost when the server stops. Persistence is planned as a future change.
