# Add ktlint enforcement

## Why

The codebase has accumulated style drift (notably unused imports) with no automated guard against it. There is also no CI: nothing verifies that the project builds and tests pass before changes land, and nothing keeps the AI agent's edits style-compliant during a session.

## What Changes

- Integrate ktlint into the Gradle build via the `org.jlleitschuh.gradle.ktlint` plugin, pinned to an explicit ktlint engine version in the version catalog, with the `ktlint_official` code style configured in `.editorconfig`. `ktlintCheck` becomes part of `check`.
- Run a one-time `ktlintFormat` baseline pass over the existing sources (removes the unused imports and applies the official style).
- Add a GitHub Actions workflow that builds and tests the service on pushes to `main` and on pull requests; lint compliance is enforced because `ktlintCheck` is wired into the build. The workflow requires no secrets (nothing in the build calls an LLM).
- Add a Claude Code `PostToolUse` hook (project `.claude/settings.json` plus a checked-in hook script) that runs ktlint on every `.kt`/`.kts` file the agent edits and reports violations back to the agent as a blocking error. Non-Kotlin edits are ignored. The script uses the same pinned ktlint version as Gradle, downloading and caching the CLI jar on first use.
- Update `CLAUDE.md` and `README.md` to document the lint setup and commands.

## Capabilities

### New Capabilities

- `code-style`: ktlint-based formatting and lint enforcement — Gradle tasks, pinned version, `ktlint_official` style, and the agent-side edit hook.
- `ci-pipeline`: GitHub Actions workflow that builds, tests, and lint-checks the service on pushes to `main` and pull requests.

### Modified Capabilities

_None — no existing spec-level behavior changes._

## Impact

- `build.gradle.kts`, `gradle/libs.versions.toml`: new plugin and pinned ktlint version.
- `.editorconfig`: new file, code style configuration.
- All Kotlin sources: one-time reformat diff from the baseline `ktlintFormat` pass.
- `.github/workflows/`: new build workflow.
- `.claude/settings.json`, `.claude/hooks/`: new hook configuration and script.
- `CLAUDE.md`, `README.md`: documentation updates.
