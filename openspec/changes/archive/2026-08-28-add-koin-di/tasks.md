# Tasks: add-koin-di

## 1. Dependency

- [x] 1.1 Add Koin to `gradle/libs.versions.toml` (version + `koin-ktor` and `koin-logger-slf4j` libraries, current Koin 4.x line compatible with Ktor 3) and wire them into `service/build.gradle.kts`.

## 2. Bean module and composition root

- [x] 2.1 Create `serviceModule` in `service/src/main/kotlin/ro/jf/ai/assistant/config/KoinConfig.kt`: `single<TaskRepository> { InMemoryTaskRepository() }`, `single { TaskService(get()) }`, `single { InMemoryConversationStore() }`.
- [x] 2.2 Rewire `Application.module`: drop the `repository` parameter, add `koinModules: List<Module> = listOf(serviceModule)`, `install(Koin)` with SLF4J logger and those modules, resolve `TaskService` and `InMemoryConversationStore` from the container, and pass them to `installAssistant`/`taskRoutes`/`chatRoutes` as today. Config parameters (`openCodeApiKey`, `openCodeBaseUrl`, `corsAllowedOrigins`) and their fail-fast validation stay untouched.

## 3. Tests

- [x] 3.1 Existing integration tests pass without modification (they never used the `repository` parameter).
- [x] 3.2 Add an integration test (given-when-then naming) proving bean override: start the app with an overriding Koin module binding a pre-seeded `TaskRepository` and assert the API serves the seeded tasks.
- [x] 3.3 Add a test asserting the REST routes and assistant tools share the same singleton store (create via REST, observe via a second REST/service read) if not already covered.

## 4. Documentation and verification

- [x] 4.1 Update `CLAUDE.md` (layering note: beans via Koin at the composition root, env config as parameters), `README.md` tech-stack list, and remove the DI item from `ROADMAP.md`.
- [x] 4.2 `./gradlew build` green (LLM-free, Docker-free); live check: rebuild the service image, restart the container, task CRUD and a chat exchange behave unchanged, startup without `OPENCODE_API_KEY` still fails with the same message.
