## MODIFIED Requirements

### Requirement: Shared API models
The API transfer models SHALL live in a Kotlin Multiplatform shared module as common code, usable from the server and from client platforms, serialized with kotlinx.serialization. Date fields SHALL use `kotlinx-datetime` `LocalDate` and serialize to ISO `yyyy-MM-dd`. Task models SHALL expose the task's grouping as `topic` and its completion state as `done`; the former `category` and `completed` names SHALL NOT appear on the wire.

#### Scenario: Renamed fields on the wire
- **WHEN** a task with a topic is serialized through the shared models
- **THEN** the JSON carries `topic` and `done` (and `dueDate` as `"yyyy-MM-dd"` when set), with no `category` or `completed` keys

#### Scenario: Round-trip integrity
- **WHEN** any transfer model is serialized to JSON and deserialized back
- **THEN** the result equals the original, including null and populated optional fields

### Requirement: Multiplatform API clients
The client-side shared module (`client-core`) SHALL provide API clients built on the multiplatform Ktor client with a caller-supplied engine, separated by functional area: a tasks client exposing create/list/get/update/delete plus the known-topics listing (`GET /api/v1/topics`), and an assistant client exposing chat (with optional session id). They SHALL use the shared transfer models and surface the server's error responses to callers. The contract module (`shared`) SHALL contain only the transfer models, and the service SHALL depend only on the contract module — never on client-side modules. Both modules SHALL compile for the web (wasmJs), JVM, and Android targets.

#### Scenario: Task operation through the client
- **WHEN** the client's create-task call runs against a server (or mock engine) honoring the REST contract
- **THEN** the request body and path match the contract and the response deserializes into the shared task model

#### Scenario: Topics fetched through the client
- **WHEN** the client's topics call runs against a server (or mock engine) honoring the REST contract
- **THEN** it returns the list of topic names from the response

#### Scenario: Chat session continuation
- **WHEN** the client's chat call is made with the session id returned by a prior exchange
- **THEN** the request carries that session id and the response yields the reply and session id from the shared chat models

#### Scenario: Server error surfaced
- **WHEN** the server responds with an error status and an error body
- **THEN** the client exposes the status and parsed error message to the caller rather than failing opaquely

#### Scenario: Service isolated from client code
- **WHEN** the service module is compiled
- **THEN** its dependencies include the contract module but no client-side module, so client classes cannot be referenced from backend code
