# Proposal: polish-client-ui

## Why

The clients run on Material3 defaults: the stock purple scheme, emoji tab icons (☑ 💬), default typography, light-only, and rough layout edges (the create-task form's date field wraps its label badly on narrow screens). It works, but it looks like a template. The owner wants a professional look (interview 2026-08-26: neutral palette, system dark theme, real icons + type scale, layout fixes — no date picker yet).

## What Changes

- **A deliberate theme in the shared UI**: a neutral, minimal palette — graphite/slate surfaces with one restrained accent — defined as explicit light and dark Material3 color schemes, selected automatically from the system preference (OS setting on Android, `prefers-color-scheme` on the web).
- **Real iconography**: Material icons replace the emoji tab symbols and decorate key actions; emoji leave the navigation bar.
- **A defined type scale**: deliberate sizes/weights for screen titles, list items, chat text, and labels instead of wholesale defaults.
- **Layout polish, no behavior changes**: the create-task form restructured so fields don't wrap their labels on narrow screens, task rows styled as cleaner list items, chat bubbles capped in width with consistent spacing, and uniform screen padding.
- Both clients get all of it identically — everything lives in the shared Compose screens.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `web-client`: gains a visual-theme requirement (neutral palette, system-following dark theme, icon navigation, type scale, narrow-width form layout).
- `android-client`: parity — the same shared theme, following the device dark setting.

## Impact

- `client` commonMain: new theme package (color schemes + typography + `AppTheme` wrapper used by `App`), icon dependency (`material-icons`), reworked layouts in `TasksScreen`/`ChatScreen`; `androidMain` top bar picks up theme colors automatically.
- `gradle/libs.versions.toml` / `client/build.gradle.kts`: the Material icons artifact.
- No service, `shared`, or `client-core` changes; no behavior changes (all existing specs and their live-verified behaviors stand).
