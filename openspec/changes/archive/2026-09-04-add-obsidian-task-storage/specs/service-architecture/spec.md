## ADDED Requirements

### Requirement: Storage backend selection
The service SHALL select the task repository implementation from the `TASK_STORAGE` environment variable — `memory` (default) binding the in-memory repository, `obsidian` binding the vault-backed repository — resolved at the composition root, with all other layers unaware of which implementation is active. An unrecognized value SHALL abort startup with an error naming the variable and the allowed values.

#### Scenario: Default memory backend
- **WHEN** the service starts without `TASK_STORAGE`
- **THEN** tasks are served from the in-memory repository, matching today's behavior

#### Scenario: Obsidian backend selected
- **WHEN** the service starts with `TASK_STORAGE=obsidian` and valid Obsidian configuration
- **THEN** the REST routes and the assistant's tools operate on the vault-backed repository through the same service layer

#### Scenario: Invalid storage value
- **WHEN** the service starts with `TASK_STORAGE=postgres`
- **THEN** it exits with an error naming `TASK_STORAGE` and the allowed values

## MODIFIED Requirements

### Requirement: Configuration distinct from beans
Environment-derived configuration (LLM API key, LLM base URL, CORS origins, port, task storage selection, and Obsidian repository settings — URL, token, branch, clone directory) SHALL remain explicit parameters of the application module with the existing validation and defaults, not container-managed beans. Startup failure behavior for missing LLM configuration SHALL be unchanged, and missing Obsidian configuration in Obsidian mode SHALL fail startup the same way.

#### Scenario: Fail-fast startup preserved
- **WHEN** the service starts without `OPENCODE_API_KEY`
- **THEN** it exits with the same error naming the variable, before serving requests

#### Scenario: Obsidian configuration validated at startup
- **WHEN** the service starts with `TASK_STORAGE=obsidian` and no `OBSIDIAN_REPO_TOKEN`
- **THEN** it exits with an error naming the variable, before serving requests
