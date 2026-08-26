# web-client

## ADDED Requirements

### Requirement: Visual theme
The web client SHALL use a deliberate visual theme shared with the Android app: a neutral palette (near-monochrome surfaces with a single accent color) defined as explicit light and dark Material color schemes, selected automatically from the browser's color-scheme preference; icon-based navigation (no emoji glyphs in app chrome); a defined typography scale; and consistent screen padding. On narrow viewports the create-task form SHALL lay out without truncating or wrapping its field labels.

#### Scenario: Dark scheme follows the browser
- **WHEN** the user's OS/browser prefers a dark color scheme and the app is loaded
- **THEN** the client renders with the dark color scheme, and with the light scheme when the preference is light

#### Scenario: Icons in navigation
- **WHEN** the user views the navigation bar
- **THEN** the Tasks and Chat destinations show Material icons with labels, not emoji characters

#### Scenario: Narrow-width form layout
- **WHEN** the tasks screen renders at a phone-width viewport
- **THEN** all create-task form labels are fully readable without wrapping mid-word, and the form remains usable

#### Scenario: Navigation adapts to window width
- **WHEN** the app renders in a window at least 840dp wide
- **THEN** the Tasks/Chat destinations appear as a vertical rail on the left edge, and as a bottom bar when the window is narrower
