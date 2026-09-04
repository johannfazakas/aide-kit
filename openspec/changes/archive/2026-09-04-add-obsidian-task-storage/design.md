## Context

aide-kit stores tasks in `InMemoryTaskRepository`; everything is lost on restart. Johann's real tasks live in his Obsidian vault — a git repository (shared-notes) with a documented format: markdown checkbox tasks carrying Dataview inline fields on indented lines, distributed across topic files that declare their topic in YAML frontmatter. The vault's own notes (`organization/Obsidian Task Management Process.md` and `projects/Koog Task Agent - Spec.md`) already specify most of the target design: a repo layer over git, a pure deterministic indexer, and agent tools that never touch raw files.

Decisions locked during exploration with Johann: git integration (JGit) from the start — no local-filesystem phase; scope limited to list/get/create; `category` renamed to `topic` and `completed` to `done`; vault is the source of truth for the topic list; identity via `[id::]` field with content-hash fallback; delete, surgical updates, and recurrence handling deferred to ROADMAP.

## Goals / Non-Goals

**Goals:**

- Vault-backed `TaskRepository` implementation selected by environment, leaving the existing layering (`routes` → `service` → `repository`) and the in-memory implementation intact for local/dev/test.
- Correct, deterministic parsing of the vault's task format, including frontmatter topic inheritance and recurrence-template exclusion.
- Safe concurrent operation against a git remote: serialized writes, one commit per create, never-commit-conflict-markers.
- Clean domain rename (`topic`, `done`) end to end.

**Non-Goals:**

- Update, complete, cancel, delete against the vault (ROADMAP; unsupported error in Obsidian mode).
- Recurrence materialization or any instance management (owned by the vault's scheduled job).
- Id backfill of hand-written tasks (ids are stamped on first mutation, once mutations exist).
- Caching, database, or watching the vault for changes; conversations persistence.
- Editing non-task vault content.

## Decisions

### 1. JGit-managed clone, no local-vault mode

The service owns a private clone (configurable directory, default under the app's working dir; cloned on startup if absent). It never points at a live Obsidian vault directory, avoiding races with Obsidian Git auto-sync. JGit (`org.eclipse.jgit`) keeps everything in-JVM — no git binary in the container image. Remote access is HTTPS + GitHub fine-grained PAT (read/write on the vault repo), via `UsernamePasswordCredentialsProvider`.

*Alternative considered*: direct filesystem access to a synced vault (vault spec's "Phase 1") — rejected by owner; git-first was the explicit goal, and it removes the sync-race class of bugs.

### 2. Sync protocol

- **Reads (`list`, `get`)**: `pull --rebase` then rescan. If the pull fails (offline, conflict), serve the last local state and log — reads prefer stale over failing.
- **Create**: under a single `Mutex`: `pull --rebase` → append the formatted task to the target file → `commit` (`agent: add task "<title>"`) → `push`. On rebase or push failure: abort/reset hard to `origin/<branch>` and throw a domain error the tools/routes surface as "vault has conflicting edits, task not saved". Never leave conflict markers or unpushed commits behind.
- All repository operations run on `Dispatchers.IO`; the mutex covers the whole read-modify-push sequence for writes.

### 3. Indexer: pure, rescan-per-request

A pure Kotlin parser (no I/O beyond reading files handed to it) scans only the vault's **task files** — files declaring `topic: <name>` in frontmatter, plus the Inbox file (`organization/Inbox.md`, a capture file with no topic) — so checklists in process, plan, and research notes stay invisible, mirroring the dashboards. Per file it parses checkbox lines and their indented field lines:

- Fields: `[due::]` (`yyyy-MM-dd`, optional ` HH:mm`), `[topic::]`, `[id::]`, and `[recurrence::]` (only to identify templates for exclusion — not exposed). Any other inline field line — `[rid::]`, `[estimate::]`, unknown ones — is treated as part of the task block and ignored; recurrence instances are indistinguishable from plain tasks in the model (recurrence awareness and `[estimate::]` exposure are ROADMAP items).
- Titles: the checkbox line's text with surrounding markdown emphasis markers (`**`/`*`) stripped — created tasks are written bold, and hand-written tasks may or may not be, so the domain title is always the plain text.
- Effective topic: inline `[topic::]` wins, else the file's frontmatter `topic:` value, else none.
- Status: `[ ]` → `done = false`; `[x]` and `[-]` → `done = true` (the domain has no cancelled state this iteration; cancelled counts as closed).
- Recurrence templates (`[recurrence::]` present, no `[due::]`) are excluded from `list`/`get` results entirely — the agent must not see or act on them yet.
- Tasks with no effective topic (in practice: Inbox tasks) are parsed and listed with a null topic, matching today's optional `category`.

The vault is small; a full rescan per request is simpler and always consistent with the clone. No cache invalidation problem exists because there is no cache.

### 4. Task identity: `[id::]` first, content hash fallback — one uniform format

- If a task carries `[id:: v]`, its id is `v`.
- Otherwise the id is derived: the first 8 characters of lowercase base32 of `SHA-256(canonical input)`, where the canonical input is the relative file path (forward slashes), the trimmed emphasis-stripped title, fields in a fixed order, and the occurrence index (disambiguates identical tasks in one file). Deterministic across rescans while the task is unchanged; 8 base32 chars (40 bits) make birthday collisions negligible at vault scale.
- Lookup order: explicit `[id::]` values are matched first; derived hashes are computed and compared only among tasks without one. A derived hash that collides with an existing explicit id is therefore shadowed deterministically — the indexer logs a WARN when it detects this. The repository logs the count of tasks lacking an explicit id at INFO.
- `get(id)` rescans and matches either kind. A stale derived id (task edited in Obsidian since it was listed) resolves to not-found → existing 404 / tool-error paths.
- `create` always writes an `[id::]` derived by the same hash function over a random UUID (collision-checked against the current scan, regenerated on a hit) — every id in the system has the same format regardless of origin.
- Future mutation operations (ROADMAP) stamp the resolved hash **verbatim** as `[id::]`, computed from the pre-mutation content and written in the same commit as the mutation — so a task's id never changes, including across the derived→persistent transition.

*Alternatives considered*: prefixed derived ids (`h-…`) to mark the resolution path (rejected: stamping would change the task's observable id, breaking id stability across updates; explicit-first lookup plus WARN-on-shadow covers the same concerns); lazy id injection on `list` (rejected: turns reads into writes, commit noise, conflict-prone); Obsidian block refs `^id` (deferred: not Dataview-queryable, easier to clip; may be revisited).

### 5. Topic registry: vault-owned, enforced everywhere

The vault file `organization/Topics.md` (frontmatter `topics: [...]`) is the single registry. `inbox` is deliberately not in it — the Inbox is a capture file whose tasks have no topic yet. The registry is enforced and exposed at every layer:

- **Repository contract** — `listTopics()` joins the `TaskRepository` interface. The Obsidian implementation reads `Topics.md` from the clone at request time; the in-memory implementation holds a seeded list (overridable in tests), so validation behaves identically in both modes.
- **Validation** — `TaskService` rejects create/update whose topic is non-null and not in the registry (`400` on REST, readable tool error in chat). A task's topic is either from the registry or absent — never free-form.
- **Exposure** — new `GET /api/v1/topics` endpoint and `listTopics` agent tool; the clients' task forms offer registry topics (or "no topic") instead of free text.
- **Create routing (Obsidian mode)** — topic absent → Inbox file, plain task. Topic valid with a dedicated topic file (frontmatter `topic:` match) → that file, inline `[topic::]` omitted. Topic valid but no dedicated file yet → Inbox file with inline `[topic::]` (it surfaces on topic dashboards and in the Inbox section until groomed into its own file). Unknown topics never reach the repository — validation rejects them first.

Vault-side companion edits (separate repo): already applied — `Topics.md` created, dashboard registry query repointed, Inbox de-topiced with the dashboard's Inbox section now file-based, process and spec notes aligned.

### 6. Created-task output format (golden-tested)

```
- [ ] **<title>**
      [due:: 2026-09-01]
      [id:: k7f3q9d2]
      [topic:: finance]
```

Created titles are written bold (`**title**`). Field order `due → id → topic`; six-space indentation; omit `[topic::]` when the target file's frontmatter already declares that topic; omit absent fields; 24h times only. Each created block is preceded by a blank line — one blank line follows the `## Tasks` heading before its first capture, and one separates consecutive captures — for readability in the vault. **Placement**: appended under the file's `## Tasks` heading — created at the end of the file on first capture if absent — so agent captures never land inside a thematic section; grooming into sections stays a human activity in Obsidian. Golden tests pin the exact bytes.

### 7. Storage selection and configuration

`TASK_STORAGE` env var: `memory` (default) or `obsidian`. Read in `Application.module` and passed as a module parameter; `KoinConfig` binds `TaskRepository` accordingly — composition-root-only resolution is preserved. Obsidian mode requires `OBSIDIAN_REPO_URL` and `OBSIDIAN_REPO_TOKEN` (plus optional `OBSIDIAN_REPO_BRANCH`, default `main`, and `OBSIDIAN_CLONE_DIR`); missing values fail fast at startup with the same style as the LLM key check. Unsupported operations (`update`, `delete`) in `ObsidianTaskRepository` throw a dedicated `UnsupportedTaskOperationException` mapped to a clear REST error and tool error message.

### 8. Rename mechanics

`category` → `topic` and `completed` → `done` ripple through model, repository interface, service, transfer DTOs, routes (query param `topic`), agent tool signatures/descriptions, and the shared client (DTOs, UI labels). No back-compat aliasing — the API is personal and both sides ship together.

### 9. Testing strategy

- Indexer: unit tests over fixture markdown (fields, inheritance, `[-]`, templates, hash stability/occurrence index).
- Git bridge: unit tests against local temp repos created with JGit itself (a bare "remote" + working clone) — covers pull/commit/push, conflict abort/reset. No network, no GitHub, nothing in `./gradlew build` touches the real vault.
- Create format: golden tests, byte-exact.
- REST/integration tests: continue on the in-memory repository via Koin overrides; one integration test asserts Obsidian-mode wiring fails fast without config and that unsupported operations map to the right error.

## Risks / Trade-offs

- [Concurrent edits from phone/laptop while the agent pushes] → single mutex + pull-rebase-push per create, abort-and-reset on conflict with an honest error; reads tolerate staleness instead of failing.
- [Hash ids go stale when a task is edited in Obsidian] → accepted semantics: stale id = not-found; the agent re-lists. Ids solidify via `[id::]` stamping once mutations land (ROADMAP).
- [PAT with write access to a personal vault lives in server env] → fine-grained PAT scoped to the single repo, contents-only permission; documented rotation note in README.
- [Vault format drift (hand-written tasks that don't parse)] → parser is lenient: unparseable checkbox lines are skipped, never crash a request; malformed field values (bad date) degrade to null fields.
- [Clone growth / startup cost] → vault is small; shallow-ish single-branch clone; clone dir is reused across restarts when persistent.
- [Breaking API rename lands with storage change] → both are in one change so clients and service move together; scope stays honest because update/delete behavior is otherwise untouched.

## Migration Plan

1. Land the rename + storage behind `TASK_STORAGE=memory` default — deploy is a no-op behavior-wise until the env var flips.
2. Vault-side edits (Topics.md registry, dashboard repoint, Inbox de-topicing, process-note fixes, `[id::]` doc row) — already applied.
3. Create the fine-grained PAT, set `TASK_STORAGE=obsidian` + repo env vars in the live deployment.
4. Verify list/get/create against the real vault; rollback = flip `TASK_STORAGE` back to `memory`.

## Open Questions

None currently.

### Resolved

- Append position: under a `## Tasks` capture heading per task file, created at end of file when absent (chosen over plain end-of-file, which would land captures inside the file's last thematic section).
- `[rid::]` stays opaque: not deserialized into the model, recurrence instances are plain tasks to aide-kit; recurrence handling is a ROADMAP item.
