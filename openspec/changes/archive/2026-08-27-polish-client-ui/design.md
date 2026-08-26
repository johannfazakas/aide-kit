# Design — polish-client-ui

## Context

The shared screens (`client` commonMain) wrap content in a bare `MaterialTheme {}` — stock purple, default typography, light only. Tabs use emoji glyphs. Both clients render the same composables, so a theme defined once in commonMain lands identically on web and Android. The Android top bar (`androidMain`) and all Material components take colors from `MaterialTheme.colorScheme`, so they restyle for free.

## Goals / Non-Goals

**Goals:**

- Neutral, professional look: near-monochrome surfaces, one accent, defined light and dark schemes following the system preference.
- Real icons in navigation, deliberate type scale, consistent spacing.
- Fix the narrow-width form layout without changing behavior.

**Non-Goals:**

- Custom fonts (bundled font resources add wasm loading complexity; the system font stack is fine — revisit separately).
- A manual theme toggle (system-following only, per interview).
- A date picker (explicitly deferred), new features, or behavior changes of any kind.
- Redesigning the web top area (the find bar remains the only web chrome).

## Decisions

### Theme lives in commonMain as `AppTheme`

New `ui/theme/Theme.kt`: explicit `lightColorScheme(...)`/`darkColorScheme(...)` with graphite/slate neutrals and a single accent (muted indigo), plus an `AppTypography` scale. `AppTheme(content)` picks the scheme via `isSystemInDarkTheme()` — multiplatform in CMP, backed by the OS setting on Android and `prefers-color-scheme` on wasm — and `App()` uses it instead of the bare `MaterialTheme`. Components (top bar, nav bar, buttons, dialogs, text fields) restyle through the scheme with no per-component work.

### Icons via the multiplatform Material icons artifact

Add `compose.materialIconsExtended` (dead-code elimination keeps only referenced icons in both the APK and the wasm bundle) for tab icons (tasks/chat) and small action icons where text alone is weak (send). Buttons that read better as words (Add, Save, Refresh) stay text. *Fallback if the extended artifact fights the toolchain*: the core icons set, with the nearest available glyphs.

**Outcome**: took the fallback preemptively — the icons artifacts froze at CMP 1.7.x and an old wasm klib linking against Compose 1.11 is exactly the risk flagged above, so the two needed glyphs (`task_alt`, `chat`) are hand-built `ImageVector`s from the official Material path data in `ui/theme/AppIcons.kt`. Zero dependencies; all buttons stayed text.

### Type scale, not fonts

Tune the Material3 `Typography` values used by the screens — screen/list/body/label sizes and weights — keeping the default font family. Chat message text uses `bodyLarge`; task titles `titleMedium`; secondary detail `bodySmall` with reduced emphasis (`onSurfaceVariant`).

### Adaptive navigation: left rail on wide layouts

Navigation placement follows window width, not platform: at ≥ 840dp (Material's expanded breakpoint) the destinations render as a left `NavigationRail`; below it, the existing bottom `NavigationBar`. Decided mid-apply with the owner (option chosen over a web-only platform flag): one commonMain `BoxWithConstraints` check gives the web client its left menu in normal browser windows, degrades gracefully when the window is narrow, and leaves the Android phone layout untouched — no new platform seam.

### Layout fixes (behavior-neutral)

- **Create-task form**: title field on its own row; due + category + Add on a second row — labels never wrap on phone widths.
- **Task rows**: list items with subtle dividers, checkbox/emphasis alignment, secondary line in muted style (dropping the ad-hoc mid-row look).
- **Chat**: bubbles capped at ~85% width, consistent corner radii and spacing; input row aligned with the same horizontal padding as the transcript.
- One screen-padding constant applied consistently across both screens.

## Risks / Trade-offs

- **Subjectivity** — mitigated by the interview decisions above and a live check where the owner accepts or adjusts; small iterations expected.
- **Icons artifact version drift** (icons artifacts trail CMP releases) — the core-set fallback is recorded; verify at apply time.
- **Dark theme on wasm** depends on CMP's `isSystemInDarkTheme` honoring `prefers-color-scheme` — verify live; if inert on wasm, read it via a JS media query in `wasmJsMain` and pass it into `AppTheme` through the existing platform seam. **Outcome**: works natively — owner confirmed the web client follows the OS-level theme switch; no bridge needed.
- **Find-highlight contrast** in dark mode (tertiaryContainer spans) — check both schemes in the live pass.
