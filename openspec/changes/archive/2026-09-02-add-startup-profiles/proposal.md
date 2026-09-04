## Why

Storage selection today is a single `TASK_STORAGE` env var read at the composition root, with the Obsidian settings read as separate loose env vars. Johann wants to start the service as one of two named environments — `local` (ephemeral in-memory storage) and `live` (the Obsidian vault backend) — each a self-contained set of properties that documents every setting for that environment and loads its secrets from system variables. A profile is easier to reason about and to launch than remembering which combination of env vars a given mode needs.

## What Changes

- Introduce two startup profiles selected by an `APP_PROFILE` env var (`local` default, `live` for the vault backend). The profile determines the task storage backend, replacing the public `TASK_STORAGE` variable.
- Move configuration into HOCON files under the service resources: a base `application.conf` with the settings common to both profiles (LLM key/base URL, port, CORS) and one file per profile (`application-local.conf`, `application-live.conf`) that `include`s the base and adds its storage block. The `live` file declares the Obsidian block. Values are substituted from system environment variables (`${VAR}` for required, `${?VAR}` for optional), so secrets still come from the environment and are never committed.
- Load the selected profile's HOCON at startup and pass the resolved values into the existing `Application.module(...)`, preserving the current parameter-based test seam. `buildTaskRepository` and the Koin wiring are unchanged. An unknown profile aborts startup; a `live` start missing required Obsidian variables fails fast exactly as `TASK_STORAGE=obsidian` does today.

## Capabilities

### Modified Capabilities

- `service-architecture`: the task repository is selected by the active startup profile (`APP_PROFILE`: `local` → in-memory, `live` → Obsidian) instead of `TASK_STORAGE`; environment-derived configuration is loaded from a per-profile HOCON file (base plus profile overlay) with system-variable substitution and the same fail-fast validation, still passed as explicit module parameters rather than beans.

## Impact

- **Service**: `Application.kt` (profile resolution + HOCON loading in `main()`), a small profile/config loader helper under `config/`, new `application.conf` / `application-local.conf` / `application-live.conf` resources. `module()`, `buildTaskRepository`, and `KoinConfig` are untouched.
- **Environment**: `TASK_STORAGE` is replaced by `APP_PROFILE` (`local` default). The `OPENCODE_*`, `PORT`, `CORS_ALLOWED_ORIGINS`, and `OBSIDIAN_*` variables are unchanged in name and meaning but are now referenced through the profile files.
- **Compose**: `compose.yaml` forwards `APP_PROFILE` (compose default `live`) and the `OPENCODE_*` / `OBSIDIAN_*` variables from the host `.env` into the service container, with a named `vault-clone` volume persisting the `live` clone across restarts.
- **Docs**: README and CLAUDE.md environment sections (profiles, `APP_PROFILE`, how to run each).
- **Dependency**: builds on `add-obsidian-task-storage` — this change modifies requirements that change introduces, so it should be archived after it.

## Non-goals

- No new storage backends, and no change to Obsidian read/write behavior or to the `OBSIDIAN_*` variable names.
- No switch to Ktor `EngineMain`/auto-loaded config; `embeddedServer` stays, HOCON is loaded explicitly to keep the module test seam.
- No Keychain or external secret-store integration; secrets remain system environment variables.
