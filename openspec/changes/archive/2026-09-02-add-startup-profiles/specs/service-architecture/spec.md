## MODIFIED Requirements

### Requirement: Storage backend selection
The service SHALL select the task repository implementation from the active startup profile named by the `APP_PROFILE` environment variable — `local` (default) binding the in-memory repository, `live` binding the vault-backed Obsidian repository — resolved at the composition root, with all other layers unaware of which implementation is active. An unrecognized value SHALL abort startup with an error naming the variable and the allowed profiles.

#### Scenario: Default local profile
- **WHEN** the service starts without `APP_PROFILE`
- **THEN** the `local` profile is used and tasks are served from the in-memory repository, matching today's default behavior

#### Scenario: Live profile selected
- **WHEN** the service starts with `APP_PROFILE=live` and valid Obsidian configuration
- **THEN** the REST routes and the assistant's tools operate on the vault-backed repository through the same service layer

#### Scenario: Invalid profile value
- **WHEN** the service starts with `APP_PROFILE=staging`
- **THEN** it exits with an error naming `APP_PROFILE` and the allowed profiles

### Requirement: Configuration distinct from beans
Environment-derived configuration (LLM API key, LLM base URL, CORS origins, port, and — for the `live` profile — Obsidian repository settings: URL, token, branch, clone directory) SHALL be loaded at startup from the active profile's HOCON configuration file and passed as explicit parameters of the application module with the existing validation and defaults, not container-managed beans. The configuration files SHALL derive their values from system environment variables via substitution, keeping secrets out of the committed files. Startup failure behavior for missing LLM configuration SHALL be unchanged, and missing Obsidian configuration in the `live` profile SHALL fail startup the same way it does for missing LLM configuration.

#### Scenario: Profile configuration loaded from HOCON
- **WHEN** the service starts with a given `APP_PROFILE`
- **THEN** it loads that profile's HOCON file (the base configuration overlaid with the profile file) and resolves the LLM, CORS, port, and storage settings from it, substituting system environment variables

#### Scenario: Fail-fast startup preserved
- **WHEN** the service starts without `OPENCODE_API_KEY`
- **THEN** it exits with the same error naming the variable, before serving requests

#### Scenario: Obsidian configuration validated at startup
- **WHEN** the service starts with `APP_PROFILE=live` and no `OBSIDIAN_REPO_TOKEN`
- **THEN** it exits with an error naming the variable, before serving requests
