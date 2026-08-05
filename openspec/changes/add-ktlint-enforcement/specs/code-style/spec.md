# code-style

## ADDED Requirements

### Requirement: Ktlint enforcement in the Gradle build
The build SHALL enforce ktlint rules via the `org.jlleitschuh.gradle.ktlint` Gradle plugin, with the ktlint engine version pinned in `gradle/libs.versions.toml` and the `ktlint_official` code style declared in `.editorconfig`. The `check` lifecycle task SHALL depend on `ktlintCheck`, and `ktlintFormat` SHALL be available to auto-fix violations.

#### Scenario: Build fails on lint violation
- **WHEN** a Kotlin source file contains a ktlint violation (e.g., an unused import) and `./gradlew build` runs
- **THEN** the build fails with a report identifying the file and rule

#### Scenario: Format task fixes mechanical violations
- **WHEN** `./gradlew ktlintFormat` runs on sources with auto-correctable violations
- **THEN** the violations are fixed in place and a subsequent `ktlintCheck` passes

### Requirement: Codebase is lint-compliant
All Kotlin sources in the repository SHALL pass `ktlintCheck` with the configured style, including the removal of previously accumulated unused imports.

#### Scenario: Clean checkout passes lint
- **WHEN** `./gradlew ktlintCheck` runs on an unmodified checkout of `main`
- **THEN** it succeeds with no violations

### Requirement: Agent edits are lint-checked via Claude hook
The repository SHALL configure a Claude Code `PostToolUse` hook (project `.claude/settings.json` plus a checked-in script) that runs ktlint on each `.kt`/`.kts` file the agent edits or writes, using the same ktlint version pinned in the version catalog. Violations SHALL be reported back to the agent as a blocking error containing the ktlint report; edits to non-Kotlin files SHALL NOT trigger a check.

#### Scenario: Agent edit with violation is blocked
- **WHEN** the agent edits a `.kt` file and the result violates a ktlint rule
- **THEN** the hook exits with code 2 and the ktlint report on stderr, and the agent receives it as an error to fix

#### Scenario: Compliant agent edit passes silently
- **WHEN** the agent edits a `.kt` file and the result is lint-compliant
- **THEN** the hook exits 0 and produces no error

#### Scenario: Non-Kotlin edit is ignored
- **WHEN** the agent edits a file that is not `.kt`/`.kts` (e.g., `README.md`)
- **THEN** the hook exits 0 without running ktlint

#### Scenario: Ktlint CLI unavailable and cannot be downloaded
- **WHEN** the hook runs on a machine without the cached ktlint jar and the download fails
- **THEN** the hook exits 0 with a warning rather than blocking the edit
