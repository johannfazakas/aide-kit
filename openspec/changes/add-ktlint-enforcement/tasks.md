# Tasks — add-ktlint-enforcement

## 1. Gradle ktlint integration

- [x] 1.1 Resolve latest stable versions of the `org.jlleitschuh.gradle.ktlint` plugin and the ktlint engine; add both to `gradle/libs.versions.toml` (plugin alias + pinned `ktlint` version entry)
- [x] 1.2 Apply the plugin in `build.gradle.kts` and configure it to use the pinned engine version from the catalog
- [x] 1.3 Create `.editorconfig` with `ktlint_code_style = ktlint_official` (plus standard root/indent settings for Kotlin sources)
- [x] 1.4 Verify `./gradlew ktlintCheck` runs, is wired into `check`, and reports the existing violations (unused imports)

## 2. Baseline reformat

- [x] 2.1 Run `./gradlew ktlintFormat`, review the diff, and fix any violations ktlint cannot auto-correct
- [x] 2.2 Verify `./gradlew build` passes (lint + tests) and commit the reformat as a dedicated commit separate from the tooling changes

## 3. CI workflow

- [x] 3.1 Create `.github/workflows/build.yml`: triggers on push to `main` and pull requests; checkout → `actions/setup-java` (Temurin 21) → `gradle/actions/setup-gradle` → `./gradlew build`; no secrets
- [ ] 3.2 Push the branch and verify the workflow runs green on GitHub

## 4. Claude hook

- [x] 4.1 Create `.claude/hooks/ktlint-check.sh`: read hook JSON from stdin, exit 0 for non-`.kt`/`.kts` files, resolve the pinned ktlint version from `gradle/libs.versions.toml`, download and cache the ktlint CLI jar under `~/.cache/ktlint/<version>/` on first use (exit 0 with a stderr warning if download fails), run it against the edited file, and exit 2 with the report on stderr on violations
- [x] 4.2 Register the script as a `PostToolUse` hook matching `Edit|Write` in the project's `.claude/settings.json`
- [x] 4.3 Test the hook manually: pipe sample hook JSON for a compliant file, a violating file, and a non-Kotlin file; verify exit codes 0/2/0

## 5. Documentation

- [x] 5.1 Update `CLAUDE.md`: note ktlint enforcement, the `ktlintFormat`/`ktlintCheck` commands, and that the agent should run `ktlintFormat` rather than hand-fixing style errors
- [x] 5.2 Update `README.md` with the lint commands and a note about the CI workflow
