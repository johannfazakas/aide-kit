# Tasks — polish-client-ui

## 1. Theme foundation

- [x] 1.1 `ui/theme/Theme.kt` in `client` commonMain: neutral light + dark `ColorScheme`s (graphite/slate surfaces, muted indigo accent), `AppTypography` scale, `AppTheme` selecting by `isSystemInDarkTheme()`; `App()` wraps content in `AppTheme`
- [x] 1.2 Material icons dependency (`compose.materialIconsExtended`, core-set fallback if the artifact fights the toolchain); tab emoji replaced with Material icons (Send stayed a text button — see design outcome)

## 2. Screen polish (behavior-neutral)

- [x] 2.1 Tasks screen: create form split into two rows (title / due+category+Add) so labels never wrap; task rows as clean list items with dividers and muted secondary line; consistent screen padding
- [x] 2.2 Chat screen: bubbles capped at ~85% width with consistent radii/spacing; input row aligned to the transcript padding; typography applied (bodyLarge messages)
- [x] 2.3 Adaptive navigation: left `NavigationRail` at ≥ 840dp window width, bottom `NavigationBar` below (one `BoxWithConstraints` check in `App`); Android phone layout unchanged

## 3. Verification

- [x] 3.1 `./gradlew build` green (all targets, all tests)
- [x] 3.2 Live check: Android emulator light + dark (toggle device dark theme, verify both schemes incl. find-highlight-free chat readability and form layout on phone width); web light + dark via browser preference; owner accepts the look or requests adjustments — iterate until accepted; record wasm `isSystemInDarkTheme` outcome in design.md
