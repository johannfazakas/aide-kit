# shared-client

## MODIFIED Requirements

### Requirement: Multiplatform API clients
The client-side shared module (`client-core`) SHALL provide API clients built on the multiplatform Ktor client with a caller-supplied engine, separated by functional area: a tasks client exposing create/list/get/update/delete, and an assistant client exposing chat (with optional session id). They SHALL use the shared transfer models and surface the server's error responses to callers. The contract module (`shared`) SHALL contain only the transfer models, and the service SHALL depend only on the contract module — never on client-side modules.

#### Scenario: Task operation through the client
- **WHEN** the client's create-task call runs against a server (or mock engine) honoring the REST contract
- **THEN** the request body and path match the contract and the response deserializes into the shared task model

#### Scenario: Chat session continuation
- **WHEN** the client's chat call is made with the session id returned by a prior exchange
- **THEN** the request carries that session id and the response yields the reply and session id from the shared chat models

#### Scenario: Server error surfaced
- **WHEN** the server responds with an error status and an error body
- **THEN** the client exposes the status and parsed error message to the caller rather than failing opaquely

#### Scenario: Service isolated from client code
- **WHEN** the service module is compiled
- **THEN** its dependencies include the contract module but no client-side module, so client classes cannot be referenced from backend code
