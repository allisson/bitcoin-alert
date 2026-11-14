# Bitcoin Alert — Implementation Roadmap

**Project:** bitcoin-alert  
**Version:** 1.0  
**Status:** Planned  
**Reference:** `spec.md`

## Development Principles

- Follow the requirements defined in `spec.md`.
- Implement features incrementally.
- Keep each phase independently testable.
- Prioritize reliability over additional features.
- Avoid unnecessary dependencies and abstractions.
- Do not introduce backend infrastructure.
- Keep the application compatible with direct APK distribution.
- Update this roadmap as implementation progresses.
- Do not mark tasks as completed without verifying their acceptance criteria.

---

## Phase 0 — Project Foundation

**Goal:** Establish the Android project, development environment, and initial CI configuration.

### Project setup

- [x] Create the Android project.
- [x] Configure Kotlin and Gradle Kotlin DSL (AGP 9 built-in Kotlin; the Kotlin version comes from the catalog's Kotlin plugins).
- [x] Configure the application ID `io.github.allisson.bitcoinalert` (`spec.md` §6). It is registered in Phase 6 and never changes after the first release. The debug build type uses `applicationIdSuffix = ".debug"`, so debug and release builds coexist on the same device.
- [x] Configure `minSdk = 26` and `targetSdk`/`compileSdk = 37` (`spec.md` §3.1).
- [x] Configure Jetpack Compose.
- [x] Configure the application theme: dark and light, following the system setting, with a fixed Material 3 palette and no dynamic color (`spec.md` §2.1).
- [x] Keep UI strings in English only, in `res/values/strings.xml`, with no translations (`spec.md` §2.1).
- [x] Default `versionCode = 1` and `versionName = "0.0.0-dev"` for builds without a version tag (`spec.md` §6).
- [x] Establish the initial project package structure: only the packages with files (`MainActivity.kt`, `ui/theme/`); each phase creates the packages it fills (`spec.md` §3.2), since Git does not track empty directories.
- [x] Expand the existing README.md with project information (description, requirements, build and test commands; installation docs in Phase 6).
- [x] Replace `.gitignore` with the Android template: the current one ignores `*.jar` (drops `gradle/wrapper/gradle-wrapper.jar` and breaks CI) and lacks `build/`, `.gradle/`, `local.properties`, `*.jks`, `*.keystore`.

### Dependencies

- [x] Add Kotlin Coroutines and Flow.
- [x] Add OkHttp.
- [x] Add kotlinx.serialization.
- [x] Add Room (runtime only; KSP with `room-compiler` and the Room Gradle plugin are added in Phase 3, with the first entity).
- [x] Add DataStore.
- [x] Add Vico chart library.
- [x] Add test dependencies: JUnit 4 (Robolectric's runner is JUnit 4), `kotlinx-coroutines-test`, Robolectric, OkHttp `MockWebServer` (`spec.md` §3.1).
- [x] Configure dependency version management in `gradle/libs.versions.toml`.

### CI foundation

- [x] Create the GitHub Actions workflow (`.github/workflows/ci.yml`), triggered on pull requests and on pushes to `main`.
- [x] Configure Android build environment with JDK 21 (Temurin, `actions/setup-java` with `distribution: temurin`, `java-version: 21`), matching `.tool-versions` (`spec.md` §3.1).
- [x] Add a Debug build job.
- [x] Add JVM test execution (`./gradlew testDebugUnitTest`: unit tests and Robolectric integration tests; `spec.md` §3.1).
- [x] Validate the initial build (CI green on PR #2).

**Acceptance criteria:**

- The project builds successfully.
- The application launches on an Android emulator.
- The initial CI workflow passes.

---

## Phase 1 — Binance Market Data Integration

**Goal:** Implement reliable access to Binance public market data.

### REST client

- [x] Implement the Binance REST API client (OkHttp + kotlinx.serialization).
- [x] Implement historical kline retrieval.
- [x] Support 5m timeframe.
- [x] Support 15m timeframe.
- [x] Support 1h timeframe.
- [x] Support 1d timeframe.
- [x] Implement API response parsing.
- [x] Implement error handling.
- [x] Implement request cancellation.
- [x] Add unit tests for response parsing.

### WebSocket client

- [x] Implement the Binance WebSocket client as a single process-scoped instance (combined streams).
- [x] Subscribe to `btcusdt@ticker`.
- [x] Subscribe to `btcusdt@kline_<interval>` for the selected timeframe.
- [x] Declare `INTERNET` and `ACCESS_NETWORK_STATE` and block cleartext traffic with a Network Security Configuration (`cleartextTrafficPermitted="false"`; `spec.md` §5).
- [x] Switch the kline subscription on timeframe change (`UNSUBSCRIBE` + `SUBSCRIBE`, same connection). Coalesce every stream set change (timeframe and Home visibility): the first change after a quiet second goes out immediately, then at most once per second, send only the difference to the latest desired set (5 messages per second limit, pings and pongs included); while `Connecting`/`Reconnecting`, send nothing and build the next URL from the latest set, then send the difference after `onOpen` (`spec.md` §3.3), with unit tests.
- [x] Discard kline messages from the previous interval after a timeframe change and cancel the previous REST candle load (`spec.md` §3.3), with unit tests.
- [x] Implement ticker message parsing.
- [x] Implement kline message parsing.
- [x] Give each `SUBSCRIBE`/`UNSUBSCRIBE` request an integer `id` from a counter in the client starting at 1 (`spec.md` §2.2).
- [x] Handle `SUBSCRIBE`/`UNSUBSCRIBE` responses (`{"result", "id"}` and errors `{"code", "msg"}` with an optional `"id"`, not wrapped; `spec.md` §2.2). A failure response is logged and treated as a lost connection, so the reconnection restores the subscriptions, with unit tests.
- [x] Implement connection state tracking.
- [x] Implement connection lifecycle management.
- [x] Implement exponential backoff (first attempt without delay after a connection up for 60 s or after leaving `Idle`; after the n-th consecutive failure, `min(1 s × 2ⁿ⁻¹, 60 s)`, plus 0–20% jitter that only adds (at most 72 s), from an injected random source; the counter resets only after 60 s up, so a connection dropping sooner counts as failed; `spec.md` §2.8), with unit tests.
- [x] Implement network interruption handling.
- [x] Reconnect after 10 seconds without messages while `Connected` (half-open connection watchdog; off while `Connecting`/`Reconnecting`), with unit tests.
- [x] Reset the backoff on `ConnectivityManager` network availability, only in `Connecting` or `Reconnecting` (`onAvailable` fires on registration); a failed attempt after it waits the backoff delay, with unit tests.
- [x] Implement the internal `Idle` state (empty stream set, connection closed on purpose; backoff, watchdog and network callback do nothing; entering it resets the backoff counter; `spec.md` §3.3), with unit tests.
- [x] Normalize parsed prices (not volume or the 24-hour change) with `setScale(2, RoundingMode.UNNECESSARY)`, discard and log a message whose price is finer than the tick size instead of crashing, and compare prices only with `compareTo` (`spec.md` §2.5), with unit tests.
- [x] Derive the stream set from Home visibility and monitoring state (`spec.md` §3.3), with unit tests. Monitoring state exists only from Phase 4: here, test the derivation function and treat monitoring as stopped; Phase 4 wires the real state.
- [x] Implement connection recovery, restoring current subscriptions.
- [x] Handle the Binance 24-hour connection close as a routine reconnection.
- [x] Avoid duplicate subscriptions.
- [x] Add WebSocket parser tests.
- [x] Validate behavior with a real Binance connection.

### Domain models

- [x] Define the Bitcoin ticker model.
- [x] Define the candlestick model.
- [x] Define the market connection state.
- [x] Implement market data repositories as concrete classes (interface only when a test needs a fake).

**Acceptance criteria:**

- The application can retrieve BTCUSDT market data.
- The ticker updates through WebSocket.
- Historical candles load for all supported timeframes.
- Connection interruptions are handled without crashing.

---

## Phase 2 — Main Screen and Chart Interface

**Goal:** Build the primary application experience.

### Main screen

- [x] Implement the main screen using Jetpack Compose.
- [x] Display the current BTCUSDT price.
- [x] Display the 24-hour percentage change with 2 decimal places (`HALF_UP`), sign and arrow, not only color; a value that rounds to zero shows `0.00%` with no sign or arrow (`spec.md` §2.1, §4), with unit tests.
- [x] Display Binance connection status.
- [x] Implement loading states.
- [x] Implement error states, including a retry action when the candle REST load fails (`spec.md` §3.3).
- [x] Implement stale data indication (last ticker message older than 10 seconds, in any connection state; kline messages and control responses do not refresh it), and the `—` placeholder before the first price (`spec.md` §2.1), with unit tests.
- [x] Format prices with the device locale and exactly 2 decimal places.
- [x] Implement navigation from Home to Alerts and History.
- [x] Wire Home visibility (lifecycle `STARTED` and current route) into the stream set derivation (Phase 1): leaving Home with monitoring stopped closes the connection (`spec.md` §3.3).
- [x] Apply the dark and light themes, following the system setting; validate both, including chart and price-change colors.
- [x] Call `enableEdgeToEdge()` and apply system bar insets on Home, Alerts and History (mandatory with `targetSdk` 35+; `spec.md` §4).
- [x] Validate layouts on different screen sizes and at the largest system font size.
- [x] Give icon-only buttons a `contentDescription` (`spec.md` §4). The navigation bar icons have text labels and are decorative; the first icon-only button is the alert menu (Phase 3).
- [x] Apply the visual design (`spec.md` §4 Visual design): fixed palette, JetBrains Mono for numbers, bottom navigation, connection and stale pills, timeframe tabs; validate both themes and font scale 2.0.

### Candlestick chart

- [x] Spike before the chart tasks: check how Vico draws volume bars below the candles sharing the time axis (Vico layers may share one drawing area). Options: two charts with synchronized scroll and zoom, or bars in the chart's bottom band on their own axis. Record the choice in `spec.md` §2.3 (one chart: candles on the start axis, volume on the end axis).
- [x] Integrate Vico.
- [x] Implement candlestick visualization.
- [x] Load historical candles.
- [x] Create the single process-scoped DataStore instance (`spec.md` §3.5); Phase 4 reuses it.
- [x] Implement timeframe selection (default `1h`, persisted in DataStore), with unit tests.
- [x] Implement 5m chart.
- [x] Implement 15m chart.
- [x] Implement 1h chart.
- [x] Implement 1d chart.
- [x] Implement horizontal navigation within the loaded candles (200 from REST plus the appended live ones; `spec.md` §2.3).
- [x] Implement zoom.
- [x] Display selected candle OHLC values.
- [x] Display candle volume as bars below the candles (kline `v`, in BTC with locale formatting and trailing zeros removed; the ticker's 24-hour volume is not used; `spec.md` §2.1), with unit tests for the formatting.
- [x] Implement live candle updates: same `openTime` replaces the last candle, newer appends, older is discarded (`spec.md` §2.3).
- [x] Load candles as subscribe → REST → apply buffered updates (REST starts only once the subscription is in effect: the connection with the stream in its URL is open, or the `SUBSCRIBE` response with the matching `id` has arrived), on start, timeframe change, reconnection and Home becoming visible again, only while Home is visible (`spec.md` §3.3), with unit tests for the merge (buffered and live) and the reload triggers.

**Acceptance criteria:**

- The main screen displays real BTCUSDT market data.
- All four timeframes work.
- Historical and live candle data remain consistent.
- The interface handles loading and network failures correctly.

---

## Phase 3 — Local Persistence and Alert Management

**Goal:** Implement alert creation, management, and persistence.

### Database

- [x] Configure Room: add KSP with `room-compiler` and the Room Gradle plugin (not added in Phase 0).
- [x] Create `PriceAlertEntity` (including `activationCycle`).
- [x] Create `AlertHistoryEntity`.
- [x] Implement alert DAO.
- [x] Implement history DAO (its queries live in `AlertDao`: the trigger transaction writes both tables).
- [x] Enable Room schema export (`exportSchema = true`, `room { schemaDirectory(...) }`) and commit the `schemas/` directory; migrations start after v1.0.0.
- [x] Bump `@Database(version)` on every schema change, including during `v0.*`: without the bump Room crashes on the identity hash check, fallback or not (`spec.md` §3.5). Schema v1; the rule is a comment on `AppDatabase` and in `AGENTS.md`.
- [x] Build the database with `fallbackToDestructiveMigration(dropAllTables = true)` while the version is `v0.*`, so a schema change between pre-releases drops test data instead of crashing (`spec.md` §3.5). Replaced in Phase 7.
- [x] Keep history entries when an alert is deleted (no FK cascade).
- [x] Implement transactional alert triggering (conditional `UPDATE` matching `activationCycle`, history insert only if one row changed; enabling and reactivation increment `activationCycle` in a single `UPDATE`; `spec.md` §3.5).
- [x] Add database tests.

### Alert domain

- [x] Define the `PriceAlert` domain model.
- [x] Define the `AlertDirection` enum.
- [x] Implement ABOVE alert evaluation.
- [x] Implement BELOW alert evaluation.
- [x] Implement price crossing detection.
- [x] Use BigDecimal for all monetary comparisons.
- [x] Implement per-cycle baseline handling keyed by `(id, activationCycle)` on creation, enabling and reactivation; drop the baseline of every cycle that is no longer Active (`spec.md` §2.5).
- [x] Keep `previousPrice` across reconnections within a monitoring session; reset it on monitoring start or service restart.
- [x] Implement alert state transitions (trigger, disable, enable, reactivate; `spec.md` §2.5).
- [x] Implement duplicate trigger prevention (the conditional `UPDATE` in the trigger transaction; the engine adds no second guard).
- [x] Implement alert state persistence.
- [x] Implement alert history creation.
- [x] Add comprehensive alert engine unit tests.

### Alert interface

- [x] Implement the alerts screen.
- [x] Subscribe the ticker while Alerts is visible, for the distance to each target (`spec.md` §3.3), with unit tests.
- [x] Implement alert creation form.
- [x] Implement target price validation (> 0, at most 2 decimal places counted as typed, so `1.000` and `100,000` are rejected; normalized to scale 2 before saving; one decimal separator `.` or `,` with a digit on each side, no grouping separators, surrounding whitespace trimmed; `spec.md` §2.5), with unit tests.
- [x] Show the parsed target formatted with the device locale before saving.
- [x] Implement ABOVE/BELOW direction selection.
- [x] Implement alert activation and deactivation.
- [x] Implement alert deletion, as an action in the alert's menu (no swipe; `spec.md` §4 Visual design).
- [x] Implement alert reactivation.
- [x] Show the distance from the current price to the target (percentage and bar) on each Active alert (`spec.md` §4 Visual design).
- [x] Show the number of Active alerts as a badge on the Alerts navigation item.
- [x] Display active, triggered and disabled alert states: Active and Disabled ordered `createdAt DESC, id DESC`, Triggered `triggeredAt DESC, id DESC` (never `ORDER BY targetPrice`: it is a string; `spec.md` §4), with database and unit tests.

### History interface

- [x] Implement alert history screen.
- [x] Display target price.
- [x] Display observed price.
- [x] Display alert direction.
- [x] Display trigger timestamp (device time zone and locale; `spec.md` §2.7).
- [x] Group entries by day in the device time zone (`spec.md` §4 Visual design).
- [x] Sort records by newest first (`ORDER BY triggeredAt DESC, id DESC`, auto-generated `Long` id; `spec.md` §2.7).
- [x] Validate history persistence across a database reopen (integration test).

**Acceptance criteria:**

Alerts are evaluated only while monitoring runs (`spec.md` §2.5), which arrives in Phase 4; here, triggering is verified by tests.

- Users can create and manage alerts.
- Alert evaluation correctly detects price crossings (unit tests).
- Alerts are not triggered repeatedly (unit and database tests).
- Alert configuration survives application restarts.
- The trigger transaction writes the history entry (database tests).

---

## Phase 4 — Background Price Monitoring

**Goal:** Implement continuous price monitoring through an Android Foreground Service.

This is a critical phase because Android background execution restrictions can affect the application's main functionality.

### Platform investigation

- [ ] Review current Android Foreground Service requirements.
- [x] Declare the `specialUse` Foreground Service type with `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`; start it with `ServiceCompat.startForeground`.
- [ ] Declare the remaining permissions listed in `spec.md` §5 (network ones are declared in Phase 1), except `WAKE_LOCK`, declared only if the screen-off test requires it (Battery validation below). `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is declared (4c); `VIBRATE` waits for the device check below.
- [ ] Check whether alert vibration works without `VIBRATE`; if it does, remove the permission and its row in `spec.md` §5.
- [x] Set `android:allowBackup="false"` and `android:dataExtractionRules` excluding all data from cloud backup and device transfer (`spec.md` §5).
- [ ] Review required manifest declarations.
- [ ] Review runtime permission requirements.
- [ ] Validate behavior on the `minSdk` and `targetSdk` versions.
- [ ] Document manufacturer-specific battery restrictions.
- [ ] Validate service startup restrictions.

### Service implementation

- [x] Implement `PriceMonitorService`, in the main process (no `android:process`; `spec.md` §2.8).
- [x] Implement explicit monitoring start action.
- [x] Implement monitoring stop action.
- [x] Integrate the Binance WebSocket client.
- [x] Integrate the alert engine.
- [x] Implement service lifecycle management.
- [x] Implement persistent notification.
- [x] Persist the `monitoringRequested` flag in DataStore, through the single process-scoped DataStore instance created in Phase 2 and shared with the UI (`spec.md` §3.5).
- [x] Expose "the service is running" as a process-scoped in-memory `StateFlow`: `true` from the start action once `startForegroundService` returns and from `onCreate`, `false` in `onDestroy` (`spec.md` §2.8).
- [x] Derive and expose the monitoring state (Stopped, Running, Interrupted; `spec.md` §2.8), with unit tests.
- [x] Return `START_STICKY`; handle a refused restart as Interrupted; call `ServiceCompat.startForeground` first in every `onStartCommand`, before any suspending work; on a restart with a `null` intent, then `stopSelf()` if `monitoringRequested` is `false` (`spec.md` §2.8), with Robolectric tests calling `onStartCommand(null, 0, 1)` directly for both values (`spec.md` §7).
- [x] Start: `startForegroundService`, then set the running flag, then write `monitoringRequested = true`; stop: write `false` before stopping the service, so a normal start or stop never shows Interrupted (`spec.md` §2.8), with unit tests.
- [x] Display monitoring status and the monitoring control on the main screen: `Start` while Stopped, `Stop` while Running, both while Interrupted (`spec.md` §2.1), with unit tests.
- [x] Warn on the main screen when Active alerts exist and monitoring is Stopped or Interrupted, with unit tests (`spec.md` §7).
- [x] Offer the battery optimization exemption dialog on user action, from Home next to the monitoring control; hide the action once `isIgnoringBatteryOptimizations()` is `true`, re-evaluated on Home `ON_RESUME` (`spec.md` §2.1, §4), with unit tests.
- [x] Evaluate alerts only while monitoring is running.
- [ ] Implement connection status exposure.
- [x] Implement service cleanup.
- [x] Make `onStartCommand` idempotent: a repeated start must not open a second connection or evaluation loop (Android already keeps a single service instance).
- [x] Wire the real monitoring state into the stream set derivation (Phase 1).
- [ ] Ensure database operations are safe during service execution.

### Notification implementation

- [x] Create the monitoring notification channel (low importance).
- [x] Create the price alerts notification channel (high importance).
- [x] Display monitoring status (do not update the notification on every ticker message).
- [x] Add an action to stop monitoring.
- [x] Open the History screen when an alert notification is tapped.
- [x] Open Home when the monitoring notification is tapped (`spec.md` §2.8).
- [x] Implement price alert notifications, posted only after the trigger transaction commits, each tagged with its history entry id and a fixed alert ID (`notify(historyId.toString(), ALERT_NOTIFICATION_ID, …)`; `spec.md` §2.6), with unit tests.
- [x] Configure default notification sound.
- [x] Configure vibration.
- [x] Request notification permission, while not granted, on every monitoring start and alert creation, without blocking the action on the dialog result (`spec.md` §2.6).
- [x] Warn on the main screen when Active alerts exist and notifications are disabled: permission denied, app notifications off (`areNotificationsEnabled()`) or Price alerts channel blocked; the warning opens the matching system settings and is re-evaluated on Home `ON_RESUME` (`spec.md` §2.1), with unit tests (`spec.md` §7).
- [ ] Verify behavior when the user dismisses the monitoring notification (Android 14+).

### Lifecycle and recovery

- [ ] Handle network disconnection.
- [ ] Handle network reconnection.
- [ ] Handle WebSocket failure.
- [ ] Handle service interruption.
- [ ] Handle application process termination.
- [ ] Verify monitoring is not restarted after device reboot and the UI shows it as Interrupted.
- [x] Ensure users are informed when monitoring is inactive.
- [ ] Validate behavior when the application is swiped away.

### Battery validation

- [ ] Measure idle battery consumption with monitoring disabled.
- [ ] Measure battery consumption with monitoring enabled.
- [ ] Test with the screen turned off; if ticker updates are missed, add a `PARTIAL_WAKE_LOCK` while monitoring (declaring `WAKE_LOCK`) and measure its cost (`spec.md` §2.8, §5).
- [ ] Test extended monitoring sessions.
- [ ] Review network activity.
- [ ] Optimize unnecessary processing and database writes.
- [ ] Document measured results.

**Acceptance criteria:**

- Monitoring works with the application in the background.
- Monitoring works with the screen turned off under supported conditions.
- The persistent notification is displayed correctly.
- Alerts are evaluated while monitoring is active.
- A real price crossing posts a notification and appears in History, and the entry survives an application restart.
- Connection failures are handled gracefully.
- Battery consumption is measured and documented.

---

## Phase 5 — Application Hardening and Testing

**Goal:** Validate application reliability, correctness, and usability.

### Unit testing

Unit tests are written with each feature, in the phase that implements it.

- [ ] Verify every unit test listed in `spec.md` §7 exists and passes; add the missing ones.

### Integration testing

- [ ] Test Room persistence.
- [ ] Test database transactions.
- [ ] Test repository behavior.
- [ ] Test WebSocket reconnection.
- [ ] Test historical candle recovery.
- [ ] Test notification generation.
- [ ] Test service and alert engine integration.

### Device testing

- [ ] Test on a physical Android device.
- [ ] Test on different Android versions.
- [ ] Test screen-off monitoring.
- [ ] Test Wi-Fi disconnection.
- [ ] Test mobile data transitions.
- [ ] Test airplane mode transitions.
- [ ] Test notification permission denial, app notifications off and Price alerts channel blocked.
- [ ] Test application process termination.
- [ ] Test stopping the app from the Task Manager (Android 13+); record whether `START_STICKY` restarts it.
- [ ] Test device reboot behavior.
- [ ] Test long-running monitoring sessions.

### Application quality

- [ ] Review error handling.
- [ ] Review logging.
- [ ] Review permission declarations.
- [ ] Review network security configuration.
- [ ] Remove unused dependencies.
- [ ] Remove debug-only code from Release builds.
- [ ] Validate application startup performance.
- [ ] Validate chart rendering performance.
- [ ] Verify no unnecessary personal data is collected.
- [ ] Verify alert data is not included in Android backups or device-to-device transfer (`allowBackup="false"`, `dataExtractionRules`).

**Acceptance criteria:**

- Critical business rules are covered by automated tests.
- No critical application crashes remain.
- Network failures do not corrupt alert state.
- The application behaves predictably under tested lifecycle conditions.

---

## Phase 6 — Release Automation

**Goal:** Automate signed APK generation and GitHub Releases publication.

### Signing configuration

- [ ] Generate the release signing key outside the repository, then back up the `.jks` and its passwords in a password manager (losing it blocks every future update):

  ```bash
  mkdir -p ~/keys
  keytool -genkeypair -v -keystore ~/keys/bitcoin-alert-release.jks -alias bitcoin-alert -keyalg RSA -keysize 4096 -validity 10000
  keytool -list -v -keystore ~/keys/bitcoin-alert-release.jks -alias bitcoin-alert | grep SHA256
  ```

- [ ] Create the limited distribution account at https://android.google.com/developerconsole/developers (2-Step Verification is already on; link a Google payments profile and a contact email).
- [ ] On the **Packages** page, register `io.github.allisson.bitcoinalert` with the SHA-256 fingerprint; wait for *Registered* (email notice). Required before the first release is installed from GitHub Releases on a certified device.
- [ ] Record the outcome and the installation path (device authorization by QR code or link, up to 50 devices) in `spec.md` §6.
- [ ] Configure signing credentials as GitHub Actions Secrets: `KEYSTORE_BASE64` (output of `base64 -i ~/keys/bitcoin-alert-release.jks`), `KEYSTORE_PASSWORD` and `KEY_ALIAS`. The PKCS12 keystore `keytool` generates uses the same password for the store and the key, so Gradle passes `KEYSTORE_PASSWORD` as both `storePassword` and `keyPassword`.
- [ ] Configure Release signing.
- [ ] Verify signing locally.
- [ ] Document secure backup of the signing key.
- [ ] Verify that subsequent releases use the same signing identity.

### GitHub Actions

- [ ] Create the release workflow, with `permissions: contents: write` so the `GITHUB_TOKEN` can create the release.
- [ ] Trigger builds from version tags.
- [ ] Keep R8 minification and resource shrinking disabled for Release (`spec.md` §6).
- [ ] Derive `versionCode` and `versionName` from the tag in one Gradle build logic function; fail on tags outside `vMAJOR.MINOR.PATCH` with MINOR/PATCH ≤ 99, on leading zeros and on `v0.0.0` (`spec.md` §6), with unit tests.
- [ ] Configure Android build environment.
- [ ] Run unit tests before building.
- [ ] Build the Release APK and name it `bitcoin-alert-vX.Y.Z.apk` (Gradle outputs `app-release.apk`; the checksum file and the release asset use the final name; `spec.md` §6).
- [ ] Sign the APK.
- [ ] Generate the SHA-256 checksum in `sha256sum` format (`spec.md` §6).
- [ ] Create GitHub Release (`v0.*` tags as pre-releases; `spec.md` §6).
- [ ] Attach APK to the release.
- [ ] Attach checksum to the release.
- [ ] Validate the complete release workflow with `v0.x.y` tags only: every tag consumes a `versionCode`, so never use a `v1.x` tag for testing.

### Documentation

- [ ] Create the adaptive launcher icon (Phase 0 ships the system default icon).
- [ ] Write installation instructions.
- [ ] Document application permissions.
- [ ] Document monitoring behavior.
- [ ] Document notification configuration.
- [ ] Document known Android limitations.
- [ ] Document the release process.
- [ ] Publish the signing certificate SHA-256 fingerprint in the README (`spec.md` §6).
- [ ] Document how to build locally.

**Acceptance criteria:**

- A version tag triggers the release workflow.
- The workflow generates a signed APK.
- The APK is published as a GitHub Release asset.
- The SHA-256 checksum is available.
- The APK can update an existing installation signed with the same key.

---

## Phase 7 — MVP Final Validation

**Goal:** Validate the complete application before declaring version 1.0.0 stable.

### Final checklist

- [ ] Every MVP acceptance criterion in `spec.md` §8 is checked (that list is the single source; do not copy it here).
- [ ] Battery consumption is documented.
- [ ] Application documentation is complete.

### Release

- [ ] Replace `fallbackToDestructiveMigration(dropAllTables = true)` with `fallbackToDestructiveMigrationFrom(dropAllTables = true, <every schema version older than v1.0.0's>)` and freeze the v1 schema; from now on every schema change ships a Room migration (`spec.md` §3.5). Verify that updating the last `v0.*` install to `v1.0.0` opens without a crash.
- [ ] Prepare version `v1.0.0`.
- [ ] Create Git tag.
- [ ] Publish GitHub Release.
- [ ] Verify APK download.
- [ ] Verify APK installation.
- [ ] Verify application update behavior.

**Final acceptance criteria:**

The Bitcoin Alert MVP is considered complete when all critical requirements in `spec.md` have been implemented, tested, and verified on a physical Android device.

---

## Future Roadmap — Post-MVP

These items are intentionally excluded from version 1.0.0.

They can be prioritized after the MVP is stable.

The candidate list lives in `spec.md` §9.

---

## Progress Tracking

Update this document as implementation progresses.

Rules:

1. Only mark a task complete after implementation and verification.
2. Keep incomplete tasks visible.
3. Add newly discovered tasks to the appropriate phase.
4. Document important architectural decisions.
5. Do not silently remove requirements.
6. Update acceptance criteria when implementation details change.

**End of Roadmap**
