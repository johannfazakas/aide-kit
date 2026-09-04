## Why

Tasks currently live in an in-memory store and vanish on restart, while Johann's real task system lives in his Obsidian vault (a git repo with a well-defined markdown format: checkbox tasks with Dataview inline fields, topic files, dashboards). Making the vault the live storage backend gives aide-kit real, persistent data and makes the assistant useful against the tasks that actually matter — following the design already sketched in the vault's "Koog Task Agent - Spec" note.

## What Changes

- New `ObsidianTaskRepository` backed by a JGit-managed clone of the vault repo (HTTPS remote + fine-grained PAT). The service only ever touches its own clone, never a live vault directory.
- Live-mode scope is **list + get + create**. Reads pull then rescan; create runs pull → append → commit → push behind a mutex, with abort + reset on conflict. Update and delete return a clear "not supported yet" error in Obsidian mode (deferred to ROADMAP).
- Deterministic markdown task indexer: parses checkbox tasks with `[due::]`, `[topic::]`, `[recurrence::]`, `[rid::]`, `[id::]` inline fields (other field lines are ignored) and frontmatter topic inheritance; recurrence templates are excluded from listings; rescan on every request (no cache, no database).
- Task identity: explicit `[id::]` inline field wins; otherwise a deterministic content hash. Agent-created tasks are always written with an `[id::]`.
- **BREAKING**: domain and API rename `category` → `topic` and `completed` → `done` across model, REST API, agent tools, and clients.
- Topic list becomes vault-owned and enforced: read at runtime from the `organization/Topics.md` frontmatter registry and exposed via a new `GET /api/v1/topics` endpoint and a `listTopics` agent tool. A created task's topic MUST be from the registry or absent — topicless captures land in the Inbox file (which is a capture file, not a topic), unknown topics are rejected with a clear error, and the clients offer only registry topics (or none) at creation.
- Storage selection via a `TASK_STORAGE` env var (`memory` default, `obsidian` for live) resolved at the composition root; Obsidian mode fails fast when its repo URL/token env vars are missing. Nothing in the build requires git network access — tests keep using the in-memory implementation, and JGit logic is tested against local temp repos.
- Companion vault-side change (shared-notes repo, already applied during exploration): `organization/Topics.md` created as the registry, Tasks Dashboard registry query repointed to it, Inbox de-topiced (`topic: inbox` removed; the dashboard's Inbox section now lists the Inbox file's tasks directly), process note and Koog spec note aligned, `[id::]` field documented.

## Capabilities

### New Capabilities

- `obsidian-task-storage`: the vault-backed task repository — JGit clone lifecycle and sync, markdown task parsing (fields, frontmatter topic inheritance, template exclusion), task identity (`[id::]` / content hash), topic registry from `Topics.md`, Inbox routing, created-task output format, and unsupported-operation behavior.

### Modified Capabilities

- `task-management`: **BREAKING** field renames `category` → `topic` and `completed` → `done` in the task representation, request bodies, and query parameters; topic values validated against the known-topics list on create/update; new `GET /api/v1/topics` endpoint; update and delete respond with an error in Obsidian storage mode.
- `assistant-chat`: task tools speak `topic`/`done` instead of `category`/`completed`; new `listTopics` tool; the assistant clarifies with the user on unknown topics instead of guessing; the update tool surfaces the storage backend's "not supported" error in Obsidian mode.
- `service-architecture`: repository bean selected by `TASK_STORAGE` at the composition root; Obsidian configuration is env-derived module parameters with fail-fast validation.
- `shared-client`: task DTOs and API client renamed to `topic`/`done`; tasks client gains the topics call.
- `web-client`: task forms offer topic selection from the fetched registry (or no topic) instead of free text. (The android-client spec inherits web-client behavior by reference and needs no delta.)

## Impact

- **Service**: `model/Task.kt`, `repository/*`, new `repository/obsidian/*` (or similar), `service/TaskService.kt`, `agent/TaskTools.kt`, `routes/TaskRoutes.kt`, `transfer/TaskMappings.kt`, `config/KoinConfig.kt`, `Application.kt`; new JGit dependency in `gradle/libs.versions.toml`.
- **Clients**: shared client DTOs/UI, web and Android apps pick up the renamed fields.
- **Environment**: new `TASK_STORAGE`, `OBSIDIAN_REPO_URL`, `OBSIDIAN_REPO_TOKEN` (and branch/clone-dir knobs); documented in README/CLAUDE.md.
- **External repo**: shared-notes vault gains `organization/Topics.md` and small edits to the Tasks Dashboard and process note (coordinated manual step, not part of this repo's build).
- **Docs**: README, CLAUDE.md environment section, ROADMAP (deferred items already recorded).
