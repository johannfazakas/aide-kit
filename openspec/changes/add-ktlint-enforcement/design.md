# Design — add-ktlint-enforcement

## Context

The project is a single-module Ktor/Kotlin 2.2 Gradle build using a version catalog (`gradle/libs.versions.toml`). There is no linter, no CI, and no agent-side guard, so style issues (unused imports) accumulate silently. Three enforcement points share one ruleset:

```
                 ┌─────────────────────────────────────────┐
                 │      ktlint (one pinned version)        │
                 │     configured via .editorconfig        │
                 └────────┬──────────────┬─────────┬───────┘
                          │              │         │
              ┌───────────▼───┐  ┌───────▼──────┐  ┌───────▼───────┐
              │ Gradle plugin │  │ GitHub Action │  │  Claude hook  │
              │ ktlintCheck / │  │ build + test  │  │ PostToolUse   │
              │ ktlintFormat  │  │ + ktlintCheck │  │ on *.kt edits │
              └───────────────┘  └───────────────┘  └───────────────┘
                 local dev          merge gate        inner loop
```

The hook only guards agent edits; CI is the backstop for human/IDE edits. This division is intentional.

## Goals / Non-Goals

**Goals:**

- One ktlint version pinned in the version catalog governs Gradle, CI, and the hook — no drift possible.
- `./gradlew build` fails on lint violations locally and in CI with no extra invocation.
- The agent gets immediate, blocking feedback when an edit it makes violates ktlint, scoped to the edited file only.
- Baseline the existing codebase so `main` is compliant from day one.

**Non-Goals:**

- No detekt or other static analysis beyond ktlint's rulesets.
- No pre-commit git hooks — enforcement is Gradle/CI/agent-hook only.
- No CI deployment/publishing; the workflow builds and tests only.
- No custom ktlint rules; standard ruleset with `ktlint_official` style.

## Decisions

### Gradle integration: `org.jlleitschuh.gradle.ktlint` plugin

Adds `ktlintCheck`/`ktlintFormat` and wires `ktlintCheck` into `check`, so `build` enforces lint for free. The plugin's `ktlint { version.set(...) }` reads the pinned engine version from the catalog. *Alternative — Spotless*: broader multi-format tool, but heavier than needed when the requirement is specifically ktlint. *Alternative — raw ktlint CLI + custom tasks*: reinvents what the plugin provides.

### Code style: `ktlint_official` via `.editorconfig`

Ktlint 1.x's default, strictest and most consistent (trailing commas, wrapping). Chosen over `intellij_idea` (smaller baseline diff but permanently diverges from ktlint's defaults). Accepting a larger one-time reformat in exchange for never fighting the default. Set `ktlint_code_style = ktlint_official` in a new `.editorconfig`.

### Version pinning: single source in `libs.versions.toml`

The catalog holds two entries: the plugin version and the ktlint engine version. Gradle reads both natively; the hook script greps the engine version from the catalog at runtime. Exact versions (latest stable) are resolved at implementation time.

### Hook: `PostToolUse` on `Edit|Write`, check-only, exit code 2

Configured in the project's `.claude/settings.json` with a checked-in script at `.claude/hooks/ktlint-check.sh`:

1. Read the hook JSON from stdin, extract `tool_input.file_path`.
2. Exit 0 immediately unless the file ends in `.kt`/`.kts`.
3. Run the ktlint CLI jar against that single file with the repo's `.editorconfig`.
4. On violations: print the report to stderr and exit 2, which surfaces to the agent as a blocking error it must fix.

*Check-only over auto-fix (`ktlint -F`)*: auto-fix would save agent round-trips but mutates files underneath the agent, risking confusion on subsequent edits; per-edit single-file checks are cheap. Revisit if round-trips prove noisy.

### Hook binary: pinned ktlint CLI jar, downloaded on demand

`./gradlew ktlintCheck` per edit is far too slow (Gradle startup, module-wide scope). A brew-installed CLI would drift from the pinned version. Instead the hook script downloads the ktlint self-executing jar for the catalog-pinned version from GitHub releases on first use and caches it under `~/.cache/ktlint/<version>/` (per-user cache; jar name includes the version so a version bump re-downloads automatically). Requires `java` on PATH, which the project already assumes.

### CI: single workflow, `./gradlew build`

`.github/workflows/build.yml`: triggers on push to `main` and on pull requests; steps are checkout → `actions/setup-java` (Temurin 21) → `gradle/actions/setup-gradle` (wrapper validation + caching) → `./gradlew build`. Lint enforcement comes through `check`; tests run as part of `build`. No secrets are configured — the build must never need an LLM key (existing project constraint).

## Risks / Trade-offs

- [Large baseline reformat diff obscures history] → One dedicated commit containing only the `ktlintFormat` output, separate from the tooling commits.
- [Hook adds latency to every Kotlin edit] → Single-file scope and a warm JVM-less invocation path keep it ~1s; acceptable for the correctness gain. If it degrades the loop, fall back to batching or auto-fix mode.
- [First hook run needs network to fetch the jar] → Failure to download exits non-blocking (exit 0 with a warning to stderr) so offline work isn't bricked; CI still enforces.
- [ktlint version bumps change rule behavior] → Version is pinned; bumps are deliberate, and `ktlintFormat` re-baselines in the same change.
- [`ktlint_official` disagrees with IntelliJ's default formatter] → `.editorconfig` is the shared source of truth; IntelliJ respects most of it, and `ktlintFormat` fixes residual drift.

## Open Questions

None — style, hook mode, and hook binary strategy were decided with the owner during exploration.
