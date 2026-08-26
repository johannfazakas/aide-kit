# Spec: android-client

## Purpose

Provide the Android application: the same Compose Multiplatform screens as the web client, built from the shared `client` UI and `client-core` screen models, talking to the service's REST and chat API directly over the network with a device-configurable server address.

## Requirements

### Requirement: Android app on shared screens
The Android application SHALL render the same task management and chat screens as the web client, built from the common Compose UI and the shared screen models, against the service's REST and chat API. Feature behavior (task CRUD with delete confirmation and date validation, list refresh semantics, chat session continuity, error surfacing, assistant markdown rendering, selectable transcript text) SHALL match the web client's requirements, except web-only keyboard affordances (in-app find, Enter/Shift+Enter and Tab semantics), which the Android app does not provide. The chat input SHALL accept multiline messages, and the soft keyboard's action key SHALL send the message; there is no soft-keyboard newline path.

#### Scenario: Task and chat parity
- **WHEN** the user operates the task screen and holds a multi-turn chat conversation on the Android app
- **THEN** behavior matches the web client against the same service, including tool-driven task changes appearing after a list refresh

#### Scenario: Soft keyboard sends
- **WHEN** the user types a chat message and taps the soft keyboard's action key
- **THEN** the message is sent under the same conditions that enable the Send button

#### Scenario: Assistant markdown rendered on Android
- **WHEN** the assistant replies with bold words and bullet lists on the Android app
- **THEN** the transcript renders them styled, identically to the web client

#### Scenario: Long-press select and copy
- **WHEN** the user long-presses a word in the transcript, adjusts the selection handles, and taps Copy in the selection toolbar
- **THEN** the selected rendered text lands on the device clipboard

### Requirement: State survives configuration changes
Screen state — including the chat `sessionId`, transcript, task list, and in-flight requests — SHALL survive Android configuration changes such as rotation, via a retained holder in the Android layer; the shared client modules SHALL remain free of Android lifecycle dependencies.

#### Scenario: Rotation mid-conversation
- **WHEN** the user rotates the device between two chat messages
- **THEN** the next message continues the same session with the transcript intact, and no in-flight request is lost

### Requirement: Configurable server address
The app SHALL read the service base URL from a device-persisted setting, editable in the app without rebuilding, defaulting to the emulator's host alias (`http://10.0.2.2:7080`). Plain-HTTP (cleartext) connections SHALL be permitted.

#### Scenario: Emulator works out of the box
- **WHEN** the app runs on an emulator with the service running on the host
- **THEN** both screens work without any configuration

#### Scenario: Switching to a LAN address
- **WHEN** the user changes the server address setting to the host's LAN IP
- **THEN** the new address persists across app restarts and subsequent requests target it

#### Scenario: Installing on a physical phone
- **WHEN** the owner follows the documented install guide with a phone in developer mode connected via adb
- **THEN** the debug app installs on the device and, pointed at the laptop's LAN IP on shared Wi-Fi, both screens work against the service

#### Scenario: Installing on an emulator via the guide
- **WHEN** the owner follows the documented install guide to create and launch an emulator and install the app
- **THEN** the app runs against the host's service with no configuration, using the default emulator address

### Requirement: Android target in the single build
`./gradlew build` SHALL compile and assemble the Android application alongside the existing targets, locally and in CI, without calling an LLM or requiring a Docker daemon.

#### Scenario: One green light includes Android
- **WHEN** CI runs `./gradlew build` on a push
- **THEN** the Android app assembles and all existing module tests still pass
