# Spec: shared-client

## Purpose

Provide a Kotlin Multiplatform shared module containing the API transfer models and API clients: common-code models serialized with kotlinx.serialization that keep the REST wire format stable, and Ktor-based tasks and assistant clients usable from any platform with a caller-supplied engine.

## Requirements

### Requirement: Shared API models
The API transfer models SHALL live in a Kotlin Multiplatform shared module as common code, usable from the server and from client platforms, serialized with kotlinx.serialization. Date fields SHALL use `kotlinx-datetime` `LocalDate` and serialize to ISO `yyyy-MM-dd`, keeping the REST wire format identical to the pre-restructure contract.

#### Scenario: Wire format unchanged
- **WHEN** a task with a due date is serialized through the shared models
- **THEN** the JSON is identical to the previous server-only serialization, with the due date as `"yyyy-MM-dd"`

#### Scenario: Round-trip integrity
- **WHEN** any transfer model is serialized to JSON and deserialized back
- **THEN** the result equals the original, including null and populated optional fields

### Requirement: Multiplatform API clients
The shared module SHALL provide API clients built on the multiplatform Ktor client with a caller-supplied engine, separated by functional area: a tasks client exposing create/list/get/update/delete, and an assistant client exposing chat (with optional session id). They SHALL use the shared transfer models and surface the server's error responses to callers.

#### Scenario: Task operation through the client
- **WHEN** the client's create-task call runs against a server (or mock engine) honoring the REST contract
- **THEN** the request body and path match the contract and the response deserializes into the shared task model

#### Scenario: Chat session continuation
- **WHEN** the client's chat call is made with the session id returned by a prior exchange
- **THEN** the request carries that session id and the response yields the reply and session id from the shared chat models

#### Scenario: Server error surfaced
- **WHEN** the server responds with an error status and an error body
- **THEN** the client exposes the status and parsed error message to the caller rather than failing opaquely
