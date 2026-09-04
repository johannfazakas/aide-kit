## 1. Domain rename (topic / done)

- [x] 1.1 Rename `category` → `topic` and `completed` → `done` in `model/Task.kt`, `TaskRepository`, `InMemoryTaskRepository`, `TaskService`, and the shared transfer models (`CreateTaskRequest`, `UpdateTaskRequest`, task response)
- [x] 1.2 Update `TaskRoutes` (query param `topic`) and `TaskMappings`; reject former field names via the existing unknown-field handling
- [x] 1.3 Update `TaskTools` signatures and `@LLMDescription` texts to speak topic/done
- [x] 1.4 Update `client-core` API clients, screen state, and the Compose UI labels (web + Android pick it up via shared code)
- [x] 1.5 Update existing unit and integration tests for the renamed fields; run `./gradlew build`

## 1b. Topic registry in the API and clients

- [x] 1b.1 Add `listTopics()` to `TaskRepository` (seeded configurable list in the in-memory implementation) and topic validation in `TaskService`: create/update with a non-null topic outside the list fails with the 400 / tool-error path
- [x] 1b.2 Add `GET /api/v1/topics` route and the topics call in the shared `client-core` tasks client, with integration and client tests
- [x] 1b.3 Replace the free-text topic fields in the create/edit forms with a selection fed from the topics call plus a no-topic option (web + Android via shared UI)
- [x] 1b.4 Add the `listTopics` agent tool and system-prompt/tool-description guidance: unknown topic → consult topics, clarify with the user (suggest close matches or no topic), never invent a topic

## 2. Obsidian indexer (pure parsing)

- [x] 2.1 Add task-file parsing scoped to task files only (frontmatter `topic:` declarers plus the Inbox file): checkbox lines (titles emphasis-stripped so bold and plain read the same), indented inline fields (`due`, `topic`, `id`, plus `recurrence` for template exclusion only; any other field line including `rid` ignored as part of the task block), frontmatter `topic:` inheritance, `[ ]`/`[x]`/`[-]` status mapping, lenient handling of malformed lines/fields
- [x] 2.2 Exclude recurrence templates (`recurrence` present, no `due`) from list/get results
- [x] 2.3 Implement task identity: explicit `[id::]` matched first, 8-char lowercase-base32 SHA-256 token over canonical content (relative path + title + fields + occurrence index) for unmarked tasks, same token format from a random UUID for generated ids; INFO log for unmarked tasks, WARN on derived/explicit collision
- [x] 2.4 Implement topic registry reading (`organization/Topics.md` frontmatter `topics`) and topic→file resolution from task-file frontmatter; routing: no topic → Inbox plain, registry topic without a file → Inbox with inline `[topic::]`
- [x] 2.5 Unit tests over fixture markdown: scan scope (non-task notes excluded), inheritance, inline override, cancelled-as-done, template exclusion, malformed fields, hash stability and occurrence disambiguation, registry parsing

## 3. Git bridge (JGit)

- [x] 3.1 Add JGit to `gradle/libs.versions.toml` and the service build
- [x] 3.2 Implement clone lifecycle: clone on startup when absent, reuse otherwise; HTTPS remote with PAT credentials; configurable branch and clone directory
- [x] 3.3 Implement read sync (pull before scan, serve last local state on failure) and the serialized write sequence (mutex: pull → mutate → commit `agent: …` → push; abort + reset hard on conflict, domain error)
- [x] 3.4 Unit tests against local temp repos (bare "remote" + clone created with JGit): clone/reuse, pull visibility, one-commit-per-create, conflict abort leaves clean clone, no network access

## 4. ObsidianTaskRepository

- [x] 4.1 Implement `findAll`/`findById` over the git bridge + indexer (rescan per request)
- [x] 4.2 Implement `create`: resolve target file (topic file; Inbox when topicless or when the registry topic has no file), generate unique `[id::]`, append under the `## Tasks` heading (creating it at end of file when absent) in the exact format (bolded title, order due → id → topic, topic line only when not inherited and present, six-space indent, 24h times), commit and push
- [x] 4.3 Golden tests pinning the appended block byte-for-byte, including the topic-omitted and Inbox-routed variants
- [x] 4.4 Implement `update`/`delete` as `UnsupportedTaskOperationException`; map it to REST `501` in status pages and to a readable tool error in `TaskTools`

## 5. Wiring and configuration

- [x] 5.1 Read `TASK_STORAGE` (default `memory`) and Obsidian env vars (`OBSIDIAN_REPO_URL`, `OBSIDIAN_REPO_TOKEN`, optional branch/clone dir) in `Application.module`; fail fast on invalid storage value or missing Obsidian config
- [x] 5.2 Bind the selected `TaskRepository` in `KoinConfig` from module parameters, keeping composition-root-only resolution
- [x] 5.3 Integration tests: default memory mode unchanged; Obsidian mode without config fails startup; `PUT`/`DELETE` return `501` with an error body when the repository is the unsupported-ops implementation

## 6. Vault-side companion change (shared-notes repo)

- [x] 6.1 Create `organization/Topics.md` with the `topics:` frontmatter registry (done during exploration; `familiy` typo fixed, previous version renamed to `work`, `inbox` removed — not a topic)
- [x] 6.2 Repoint the Tasks Dashboard DataviewJS registry lookup to `Topics.md`, remove the list from the dashboard frontmatter, and make the Inbox section list the Inbox file's tasks directly (done during exploration)
- [x] 6.3 Update the process note, Inbox note, and Koog spec note: registry location, Inbox de-topicing, `[id::]` field documentation (done during exploration)
- [x] 6.4 Verify dashboards render in Obsidian: Unmanaged Tasks against the new registry, file-based Inbox section, Dev dashboard with `work`

## 7. Docs and finish

- [x] 7.1 Update README and CLAUDE.md environment sections (`TASK_STORAGE`, `OBSIDIAN_*` vars, PAT scoping note) and the architecture description
- [x] 7.2 Verify ROADMAP deferred items still reflect reality (updates/completion with id-stamping, delete, recurrence awareness)
- [x] 7.3 Full `./gradlew build` green; manual smoke test in Obsidian mode against the real vault (list, get, create into a topic file and into Inbox)
