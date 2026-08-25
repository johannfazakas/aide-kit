# Tasks — restructure-multiplatform

## 1. Toolchain spike

- [x] 1.1 Resolve the compatible version set (Kotlin 2.x, KMP plugin, Compose Multiplatform plugin, AGP, Gradle 9.3 or a justified wrapper bump); pin everything in `gradle/libs.versions.toml` (including plugins not yet applied) and record the outcome in design.md

## 2. Module restructure

- [x] 2.1 Introduce `service/` module: `git mv` all of `src/` into it, move application/Ktor/Koog build config into `service/build.gradle.kts`, extract shared config (ktlint, group/version) into a `gradle/plugins` convention plugin (`aidekit.common-conventions`); `settings.gradle.kts` includes both modules
- [x] 2.2 Verify byte-identical behavior: full test suite green, app boots, `api.http` requests behave identically, ktlint hook still lint-checks edited files in the new layout

## 3. Shared module

- [x] 3.1 Create `shared/` KMP module (jvm target only for now) and `git mv` the `transfer/` DTOs into commonMain, same packages
- [x] 3.2 Migrate dates to `kotlinx-datetime` `LocalDate` in the DTOs, `model/Task.kt`, and `TaskTools` parsing; delete `LocalDateSerializer`; existing TaskTools error-path tests stay green
- [x] 3.3 Add serialization round-trip tests pinning the exact JSON (including `"yyyy-MM-dd"` dates and null optionals)
- [x] 3.4 Add API clients in commonMain (caller-supplied engine), split by area: `TasksApiClient` (CRUD incl. delete) and `AssistantApiClient` (chat with optional session id); error-response surfacing via shared `ApiException`
- [x] 3.5 Test the API clients against Ktor `MockEngine`: request shapes, response deserialization, chat session echo, error surfacing

## 4. Verification & docs

- [x] 4.1 `./gradlew build` green from a clean checkout — both modules compiled, linted, tested; nothing calls an LLM; confirm the GitHub Actions workflow needs no command changes
- [x] 4.2 Update `README.md`: module layout, unchanged run commands (`./gradlew :service:run` or kept alias), architecture diagram touch-up
