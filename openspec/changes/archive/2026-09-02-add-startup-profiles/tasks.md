## 1. HOCON profile configuration files

- [x] 1.1 Add `service/src/main/resources/application.conf` (base) holding the settings common to both profiles, read from system env: `OPENCODE_API_KEY` (required), `OPENCODE_BASE_URL`, `PORT`, `CORS_ALLOWED_ORIGINS`
- [x] 1.2 Add `application-local.conf` (`include "application.conf"`, `storage = memory`) and `application-live.conf` (`include "application.conf"`, `storage = obsidian`, `obsidian { repoUrl/token/branch/cloneDir }` with `${VAR}`/`${?VAR}` substitution)

## 2. Profile-aware startup

- [x] 2.1 In `main()`, read `APP_PROFILE` (default `local`), load `application-<profile>.conf` via Typesafe Config / `HoconApplicationConfig`; unknown profile aborts with an error naming the variable and allowed profiles
- [x] 2.2 Map the loaded config to the existing `Application.module(...)` parameters (LLM, CORS, port, storage, Obsidian block); remove the `TASK_STORAGE`/`OBSIDIAN_*` `System.getenv` defaults that `main()` now supplies, keeping `module()`'s parameter-based test seam intact
- [x] 2.3 Confirm `HoconApplicationConfig` is available on the classpath through Ktor; add the dependency only if the build requires it

## 3. Tests

- [x] 3.1 Given-when-then unit tests for the profile loader: `local` (and absent `APP_PROFILE`) resolves to memory storage; `live` without the required Obsidian variables fails fast naming the variable; an unknown profile aborts naming `APP_PROFILE`
- [x] 3.2 Run `./gradlew build`; confirm existing integration tests (default memory serving, Obsidian `501`) stay green unchanged

## 4. Docs

- [x] 4.1 Update README and CLAUDE.md environment sections: `APP_PROFILE` and the `local`/`live` profiles replace `TASK_STORAGE`; how to start each profile; note the `OBSIDIAN_*`/`OPENCODE_*` variables now flow through the profile HOCON files

## 5. Docker Compose

- [x] 5.1 Forward `APP_PROFILE` (compose default `live`) and the `OPENCODE_*` / `OBSIDIAN_*` variables from the host `.env` into the service container, and mount a named `vault-clone` volume for the `live` clone; document it in the README run section
