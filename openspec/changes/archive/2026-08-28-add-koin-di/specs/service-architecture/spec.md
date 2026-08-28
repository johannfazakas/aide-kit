# Delta: service-architecture

## ADDED Requirements

### Requirement: Dependency injection composition root
The service SHALL declare its application beans — the task repository, task service, and conversation store — in a Koin module installed by the Ktor application, and SHALL resolve them from the container at the composition root (`Application.module`) rather than constructing them inline. Route builders and other collaborators SHALL continue to receive their dependencies as explicit parameters (constructor or function), keeping layers free of container lookups outside the composition root.

#### Scenario: Beans resolved from the container
- **WHEN** the service starts
- **THEN** the task routes, chat routes, and assistant operate on the repository, service, and conversation store instances declared in the Koin module, and each bean is a singleton (the assistant's tools and the REST routes observe the same task store)

#### Scenario: No container access outside the composition root
- **WHEN** the service and route layers are inspected
- **THEN** only the composition root resolves from Koin; `routes`, `service`, `repository`, and `agent` code declares dependencies as plain parameters

### Requirement: Configuration distinct from beans
Environment-derived configuration (LLM API key, LLM base URL, CORS origins, port) SHALL remain explicit parameters of the application module with the existing validation and defaults, not container-managed beans. Startup failure behavior for missing LLM configuration SHALL be unchanged.

#### Scenario: Fail-fast startup preserved
- **WHEN** the service starts without `OPENCODE_API_KEY`
- **THEN** it exits with the same error naming the variable, before serving requests

### Requirement: Test-time bean substitution
Tests SHALL be able to substitute beans by supplying overriding Koin modules through the application module's entry point, without adding bean parameters to its signature.

#### Scenario: Overriding the repository in a test
- **WHEN** an integration test starts the application with a Koin module binding a pre-seeded task repository
- **THEN** the API serves the seeded tasks, and no production code path changes

#### Scenario: Existing tests unaffected
- **WHEN** the existing integration tests run against the default bean modules
- **THEN** they pass without modification beyond the removed `repository` parameter
