# Bitcoin Alert — Project Specification

**Project:** bitcoin-alert  
**Version:** 1.0  
**Status:** MVP Specification  
**Platform:** Android  
**Distribution:** GitHub Releases (APK)

## 1. Overview

Bitcoin Alert is a lightweight, personal Android application for monitoring Bitcoin prices in real time through the Binance public market API.

The application provides real-time BTC/USDT prices, historical candlestick charts, configurable price alerts, and local notifications.

The application is designed to work independently, without a proprietary backend, user accounts, authentication, or cloud infrastructure.

The primary distribution method is direct APK installation through GitHub Releases.

### 1.1 Goals

- Monitor the BTC/USDT trading pair in real time.
- Display the current Bitcoin price and 24-hour price variation.
- Provide historical candlestick charts.
- Support multiple chart timeframes.
- Allow users to configure price-above and price-below alerts.
- Continue monitoring prices while the application is in the background.
- Notify users when configured price conditions are reached.
- Store alerts and alert history locally.
- Keep the application lightweight and easy to maintain.

### 1.2 Non-goals

The MVP will not include:

- Trading or order execution.
- Cryptocurrency purchases or sales.
- Support for additional cryptocurrencies.
- Support for exchanges other than Binance.
- BRL price conversion.
- Technical indicators such as RSI, MACD, or moving averages.
- User accounts or authentication.
- Cloud synchronization or a proprietary backend.
- Advertising or analytics.
- Android home screen widgets.
- External notification services.
- Editing alerts: change an alert by deleting it and creating a new one.
- Clearing or limiting alert history: history grows without a limit in the MVP.

## 2. Functional Requirements

### 2.1 Main Screen

The main screen must display:

- Current BTC/USDT price.
- 24-hour percentage price change.
- Binance connection status: `Connecting` (until a connection succeeds after launch or after leaving `Idle`, including the backoff waits after failed attempts), `Connected`, `Reconnecting` (connection lost, retrying with backoff). When no stream is needed the connection is closed and the client is in the internal `Idle` state (§3.3); that only happens with Home not visible, so Home never shows a disconnected state.
- Stale data indication when the last ticker message is older than 10 seconds, in any connection state (e.g. the last known price shown while `Connecting` after returning to Home). See §3.3. Before the first price arrives in the process (e.g. a fresh launch), the price and the 24-hour change show the placeholder `—` instead of a value or the stale indication.
- Price monitoring status (`Stopped`, `Running`, `Interrupted` — see §2.8).
- Monitoring control: `Start` while `Stopped`, `Stop` while `Running`, and both while `Interrupted` (`Start` starts the service again, `Stop` writes `monitoringRequested = false`; §2.8).
- Battery optimization exemption action (§2.8), shown only while the application is not exempt (`PowerManager.isIgnoringBatteryOptimizations()` is `false`). The exemption changes outside the application without any callback, so it is re-evaluated whenever Home reaches `ON_RESUME`, like the notifications warning below.
- A warning when notifications are disabled while Active alerts exist (alerts cannot be delivered): `POST_NOTIFICATIONS` denied, app notifications turned off (`NotificationManagerCompat.areNotificationsEnabled()` is `false`, which covers Android 8–12 without a runtime permission), or the Price alerts channel blocked (`IMPORTANCE_NONE`). The warning opens the system settings that fix the cause (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`, or `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS` for a blocked channel): after two denials Android no longer shows the permission dialog. These settings change outside the application without any callback, so the warning conditions are re-evaluated whenever Home reaches `ON_RESUME`.
- A warning when Active alerts exist and monitoring is `Stopped` or `Interrupted` (alerts are not being evaluated).
- Candlestick chart.
- Available chart timeframes.
- Access to alert management and alert history.

The interface should be minimal, with dark and light themes that follow the system setting (`isSystemInDarkTheme()`); there is no in-app theme selector. The Material 3 palette is fixed: no dynamic color (Material You), so the price-change colors look the same on every device.

The user interface is in English only; there are no translations. Number and date formatting still follow the device locale (§2.5, §2.7).

Prices must be displayed in USDT, with exactly 2 decimal places (BTCUSDT tick size, 0.01).

The 24-hour percentage change (ticker field `P`, sent with 3 decimal places, e.g. `"-3.829"`) is displayed with 2 decimal places (`RoundingMode.HALF_UP`), its sign and an arrow (§4). When the rounded value is zero it shows `0.00%` with no sign and no arrow, in a neutral color: `BigDecimal` has no negative zero, so `-0.004` rounds to `0.00`. The only volume shown is the candle volume in the chart (kline field `v`, drawn as bars below the candles, §2.3), in BTC, with the device locale formatting and trailing zeros removed (Binance sends 8 decimal places: `"336.78983000"` is shown as `336.78983`); the ticker's 24-hour volume is not used.

### 2.2 Real-time Price Updates

The application must use Binance WebSocket public market streams.

Ticker stream:

```text
btcusdt@ticker
```

WebSocket endpoint (combined streams, single connection — see §3.3):

```text
wss://stream.binance.com:443/stream?streams=<current subscriptions>
```

Binance serves the same streams on ports `9443` and `443`; port `443` is used because some Wi-Fi and carrier networks block non-standard ports.

The `streams` parameter lists the subscriptions required at connection time (§3.3), e.g. `btcusdt@ticker/btcusdt@kline_1h` with the chart visible or `btcusdt@ticker` with monitoring only. Later changes use `SUBSCRIBE`/`UNSUBSCRIBE` on the same connection.

Combined-stream messages are wrapped as `{"stream": "...", "data": {...}}`.

Each `SUBSCRIBE`/`UNSUBSCRIBE` request carries an integer `id` (Binance also accepts alphanumeric strings up to 36 characters and `null`) from a counter in the client, starting at 1 and incremented on every request, so each response matches its request.

Responses to `SUBSCRIBE`/`UNSUBSCRIBE` are not wrapped: they arrive as `{"result": null, "id": <request id>}`. The parser must recognize them, not treat them as stream data. A non-null `result` or an error response is a subscription failure: log it and treat it as a lost connection, so the reconnection (backoff in §2.8) restores every subscription through the connection URL. Without this, the chart would wait forever for a stream that never arrives. Market stream errors arrive as `{"code": <int>, "msg": "..."}`, with `"id"` only when the request carried a readable one (e.g. `{"code": 0, "msg": "Unknown property", "id": 1}`; an invalid JSON request gets no `id`). The parser must accept both.

Responsibilities:

- Receive ticker updates.
- Update the displayed last traded price.
- Update the 24-hour percentage change.
- Provide price updates to the alert engine.
- Track connection status.
- Handle connection interruptions and reconnections.

The application must avoid unnecessary network connections and duplicate subscriptions.

### 2.3 Historical Candlestick Data

Historical candles must be retrieved using Binance REST API.

Endpoint:

```http
GET https://api.binance.com/api/v3/klines
```

Parameters:

- `symbol=BTCUSDT`
- `interval=5m|15m|1h|1d`
- `limit=200`

The application must initially load up to 200 candles for the selected timeframe.

The chart must support:

- Open price.
- High price.
- Low price.
- Close price.
- Volume, drawn as bars below the candles, sharing their time axis.
- Horizontal scrolling within the loaded candles (no pagination beyond the initial 200 in the MVP).
- Zoom.
- Selected candle OHLC information.
- Real-time updates from the kline stream: a message with the same `openTime` as the last candle replaces it, a newer one is appended (a new period started), an older one is discarded. The same rule merges the buffered updates after a REST load (§3.3). Appended candles grow the series beyond 200; the next reload trims it back.

Chart layout (Vico): one chart with two layers sharing the time axis, so scroll and zoom need no synchronization (two Vico charts cannot share one scroll state). Candles use the start axis with the intrinsic range of the loaded candles; volume bars use the end axis, which is not drawn, with the range `0` to 4 × the highest volume, so the bars fill the bottom quarter. The _x_ value is the candle `openTime` in epoch milliseconds; time labels use the device time zone and locale (`HH:mm`, the short date for `1d`). The chart opens at the last candle showing about 60 candles; zoom goes from all loaded candles to about 15. A new candle scrolls the chart only when it showed the last candle before the update. Tapping a candle selects it (tapping it again clears the selection); the line above the chart shows the selected candle's time, OHLC and volume, or the last candle when none is selected.

### 2.4 Chart Timeframes

The following timeframes must be supported:

| Timeframe | Binance Interval |
|---|---|
| 5 minutes | `5m` |
| 15 minutes | `15m` |
| 1 hour | `1h` |
| 1 day | `1d` |

The default timeframe is `1h`. The selected timeframe is persisted in DataStore and restored on the next launch.

The selected timeframe must not affect real-time price monitoring.

Binance candles are aligned to UTC: a `1d` candle opens at 00:00 UTC (21:00 in Brasília, UTC-3). This is expected, not a bug.

### 2.5 Price Alerts

Users must be able to create price alerts with two conditions:

- Price rises above a configured target.
- Price falls below a configured target.

Each alert must contain:

- Unique identifier (`UUID.randomUUID().toString()`).
- Target price.
- Direction (`ABOVE` or `BELOW`).
- Enabled/disabled status.
- Creation timestamp.
- Activation cycle (`activationCycle`): an integer, `0` on creation, incremented on enabling and reactivation. It identifies the current activation cycle. A counter, unlike a timestamp, never repeats when two activations fall in the same millisecond or the device clock moves back.
- Trigger timestamp (`null` while not triggered; an alert is triggered when it is set).

Alert states:

| State | Condition | Evaluated |
|---|---|---|
| Active | `enabled = true`, `triggeredAt = null` | Yes |
| Triggered | `enabled = true`, `triggeredAt != null` | No |
| Disabled | `enabled = false` (`triggeredAt` kept as is) | No |

Transitions:

- Triggering sets `triggeredAt`; `enabled` stays `true`.
- Disabling sets `enabled = false` and keeps `triggeredAt`.
- Enabling a Disabled alert or reactivating a Triggered alert sets `enabled = true`, clears `triggeredAt` and increments `activationCycle`. Both start a new activation cycle with a new baseline (see Crossing semantics).

Target price validation:

- Must be a decimal number greater than zero.
- At most 2 decimal places (BTCUSDT tick size is 0.01), counted as typed: trailing zeros count, so `1.000` (one thousand in pt-BR) and `100,000` (one hundred thousand in en-US) are rejected, never read as `1` or `100`. Never call `stripTrailingZeros()` before this check.
- Input accepts digits and at most one decimal separator, either `.` or `,`, regardless of locale; grouping separators are rejected. Leading and trailing whitespace is trimmed before parsing. A separator needs at least one digit on each side: `.50` and `100.` are rejected. Leading zeros are accepted (`0100` parses to `100`). Many Android decimal keyboards show only `.`, and locale-aware lenient parsing would read `100000.50` in pt-BR as `10000050`. The input is parsed into `BigDecimal` and normalized to scale 2 like every other price (below): `100` is stored as `100.00`.
- Before saving, the form shows the parsed target formatted with the device locale (e.g. `100.000,50` in pt-BR, `100,000.50` in en-US), so the user can confirm the value. Prices are displayed with the same locale formatting everywhere.
- Duplicate alerts (same direction and target) are allowed.

Example:

```text
BTC/USDT

Alert 1:
Direction: ABOVE
Target: 100000 USDT

Alert 2:
Direction: BELOW
Target: 90000 USDT
```

#### Alert behavior

1. Active alerts must be evaluated against incoming ticker prices only while monitoring (§2.8) is running. With monitoring stopped, no alert is evaluated, even with the application open.
2. An alert must trigger only once per activation cycle.
3. Triggered alerts must be recorded in the local history.
4. Triggered alerts must remain available for inspection.
5. Users must be able to reactivate previously triggered alerts.
6. Users must be able to disable or delete alerts.
7. Disabled alerts must not be evaluated.
8. Alert configuration must survive application restarts.

#### Crossing semantics

Alerts must trigger on price crossings, not simply because the current price is already beyond the configured threshold.

For an `ABOVE` alert:

```text
previousPrice <= targetPrice
AND
currentPrice > targetPrice
```

For a `BELOW` alert:

```text
previousPrice >= targetPrice
AND
currentPrice < targetPrice
```

When an alert is created, enabled or reactivated, the application must establish a new baseline price for that alert before evaluating subsequent crossings: the first ticker price received after the activation is the alert's `previousPrice`, and evaluation starts with the next one. Baselines are per activation cycle, keyed by `(id, activationCycle)`: a cycle the engine has not seen yet gets a new baseline, even if the engine never observed the alert leaving the Active state (e.g. disabled and re-enabled between two ticker updates). This avoids false notifications caused by loading an initial price that is already beyond the target. The engine drops the baseline of every `(id, activationCycle)` that is no longer Active (triggered, disabled, deleted or replaced by a new cycle), so the baselines do not grow during a long monitoring session.

`previousPrice` across interruptions:

- **WebSocket reconnection within the same monitoring session:** keep the last price received before the disconnection as `previousPrice`. A crossing that happened during the gap triggers on the first price after reconnection.
- **Monitoring start or service restart:** discard any previous price; the first received price becomes the new baseline for every active alert.

The ticker stream delivers snapshots every 1000 ms; spikes that reverse between two snapshots are not detected. This is accepted behavior.

All price comparisons must use decimal arithmetic (`BigDecimal`), never floating-point comparisons.

Binance sends prices with 8 decimal places (e.g. `"100000.01000000"`). Every parsed price (ticker `c`; kline `o`, `h`, `l`, `c`; the alert target, after validation) is normalized with `setScale(2, RoundingMode.UNNECESSARY)` (lossless: the tick size is 0.01) before it is compared, stored or displayed. If normalization throws `ArithmeticException` (a price finer than the tick size, e.g. after a Binance tick size change), the message is discarded and logged; it must never crash the service. The rule applies only to prices: kline volume (`v`) and the 24-hour percentage change (`P`) are not normalized to scale 2. Compare prices only with `compareTo`: `BigDecimal.equals` (and Kotlin `==`) also compares the scale, so `100000.50` and `100000.5` are not equal.

### 2.6 Notifications

Notifications must be generated locally using Android's notification system.

Each alert notification must include:

- Alert direction.
- Target price.
- Observed price.
- Trigger timestamp.

Notification channels:

| Channel | Importance | Use |
|---|---|---|
| Monitoring | Low | Persistent Foreground Service notification. |
| Price alerts | High | Alert notifications, with sound and vibration. |

Notification behavior:

- Play the default notification sound (alerts channel).
- Enable vibration (alerts channel).
- Avoid duplicate notifications for the same alert activation.
- Post each alert notification with its history entry id as the tag and a fixed alert ID (`notify(historyId.toString(), ALERT_NOTIFICATION_ID, …)`; the id is the one the history insert returns). Android identifies a notification by tag and ID, so alerts triggered by the same ticker update do not replace each other, and no alert can replace the monitoring notification.
- Notify only after the trigger transaction commits (§3.5). If the process dies between commit and notification, that notification is lost; the trigger remains in the history. This is accepted: at most one notification per activation.
- Tapping an alert notification opens the History screen.
- Respect Android notification permissions and user-configured notification channel settings. Sound and vibration are channel properties on Android 8+; after the channel is created, only the user can change them.
- Request `POST_NOTIFICATIONS` (Android 13+), while it is not granted, every time the user starts monitoring or creates an alert. After two denials Android no longer shows the dialog, so repeated requests are harmless. The request does not block the action: monitoring starts (and the alert is saved) immediately, without waiting for the dialog result. If denied, or if notifications are otherwise disabled (§2.1), monitoring still runs, alerts are still recorded in history, and the main screen shows the warning from §2.1.

### 2.7 Alert History

The application must provide a local history of triggered alerts.

Each history entry must contain:

- Alert identifier.
- Target price.
- Observed price.
- Direction.
- Trigger timestamp.

Entries must be ordered from newest to oldest: `ORDER BY triggeredAt DESC, id DESC`. Alerts triggered by the same ticker update share `triggeredAt`; the auto-generated `id` (insertion order) keeps their order stable. The implicit `rowid` is not used: in a table without an `INTEGER PRIMARY KEY`, SQLite may renumber it on `VACUUM`.

Timestamps (history and notifications) are shown in the device time zone, formatted with the device locale.

History must remain available after application restarts.

Deleting an alert must not delete its history entries (no foreign key cascade from `alert_history` to `price_alerts`).

### 2.8 Background Monitoring

Continuous price monitoring must be implemented using an Android Foreground Service, subject to Android platform requirements.

The service must:

- Maintain the Binance WebSocket connection.
- Receive BTC/USDT ticker updates.
- Evaluate active alerts.
- Trigger local notifications.
- Handle connection interruptions.
- Implement reconnection with exponential backoff. After a connection that stayed up for at least 60 s is lost, the first attempt runs without delay. Each consecutive failed attempt waits longer: after the n-th consecutive failure (n starting at 1), the base delay is `min(1 s × 2ⁿ⁻¹, 60 s)` (1 s, 2 s, 4 s, … up to 60 s), and the wait is the base delay plus a random jitter of 0 to 20% of it (`min(1 s × 2ⁿ⁻¹, 60 s) × (1 + U[0, 0.2])`, so at most 72 s). The jitter only adds: the wait is never shorter than the base delay. The random source is injected, so tests are deterministic. The attempt counter resets only after a connection stays up for 60 s; a connection that opens and drops sooner counts as a failed attempt. Binance limits connection attempts to 300 per 5 minutes per IP, and IPs that are repeatedly disconnected may be banned, so consecutive failures must never retry without delay.
- Expose monitoring and connection status to the UI.

#### User control

- Monitoring starts through an explicit user action.
- A persistent notification must indicate that monitoring is active. It is not updated on every ticker message: Android drops notification updates posted too often. On Android 14+ the user can dismiss it; the service keeps running, and the main screen remains the source of truth for the monitoring state.
- The monitoring notification (Monitoring channel) reads "Monitoring BTC/USDT" with the number of Active alerts, updated only when that number changes, and has a `Stop` action. Tapping it opens Home.
- Users must be able to stop monitoring, from the main screen or from the notification action.
- Monitoring keeps running with no Active alerts: only the user stops it.
- Active alerts must remain persisted when monitoring is stopped.
- Restarting monitoring must restore active alert evaluation.

#### Monitoring state

The application persists a `monitoringRequested` flag in DataStore: set to `true` when the user starts monitoring, `false` when the user stops it.

| State | Condition |
|---|---|
| Stopped | `monitoringRequested = false` |
| Running | `monitoringRequested = true` and the service is running |
| Interrupted | `monitoringRequested = true` and the service is not running |

"The service is running" is a process-scoped in-memory `StateFlow<Boolean>`, set to `true` by the start action as soon as `startForegroundService` returns without an exception and again in the service's `onCreate`, and to `false` in `onDestroy`. `startForegroundService` is asynchronous: `onCreate` arrives later, through the system server, and can run after the `monitoringRequested` write; setting the flag only in `onCreate` would show Interrupted for an instant on every start. Process death resets it to `false`, so a killed service shows Interrupted.

The service runs in the application's main process (no `android:process` attribute): this flag, the Binance client (§3.3) and the DataStore (§3.5) are process-scoped instances shared with the UI, which only works within one process.

Order of writes, so the UI never shows Interrupted during a normal start or stop:

- Start: call `startForegroundService` first, set the running flag to `true` once it returns (above), then write `monitoringRequested = true`. The state goes Stopped → Running. If `startForegroundService` throws, neither is written.
- Stop (main screen or notification action): write `monitoringRequested = false` first, then stop the service. The state goes Running → Stopped.

- The service returns `START_STICKY`, so Android may restart it after killing the process. A restart counts as a service restart for `previousPrice` (§2.5). If Android refuses the restart (e.g. `ForegroundServiceStartNotAllowedException`), the service stops and the state becomes Interrupted.
- Every `onStartCommand` calls `ServiceCompat.startForeground` first, before any suspending work such as a DataStore read: a service started with `startForegroundService` must do it within a few seconds or Android throws `ForegroundServiceDidNotStartInTimeException`, and a restarted service is no longer in the foreground, so without it Android stops the service under the background execution limits. On a restart that call is the one that can throw `ForegroundServiceStartNotAllowedException` (handled as above).
- On a restart (`onStartCommand` with a `null` intent), after `startForeground` the service reads `monitoringRequested` and calls `stopSelf()` if it is `false`: the process may have died after the user stopped monitoring but before the service stopped. The monitoring notification then shows for an instant, which is harmless.
- Monitoring is not restarted automatically after a device reboot (no `BOOT_COMPLETED` receiver). After a reboot the flag is still `true`, so the application shows Interrupted until the user starts or stops monitoring.

#### Foreground Service type

Use the `specialUse` Foreground Service type, declared with the `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property describing continuous price monitoring.

`specialUse` exists from API 34. Call `ServiceCompat.startForeground` (AndroidX Core 1.12+): on API 34+ it passes the type; on API 29–33 it masks out types newer than API 29, so `specialUse` becomes type `0`; below API 29 it calls the overload without a type.

`dataSync` is not suitable: on Android 15 it is limited to 6 hours of execution per 24-hour period. Because the application is distributed outside Google Play, the Play Console `specialUse` declaration does not apply.

#### Wake lock and battery optimization

- The service does not hold a wake lock by default.
- The application offers, through an explicit user action on Home (§4), the system dialog to exclude it from battery optimization (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`). It is never requested automatically, and the action is hidden once the application is exempt (§2.1).
- If the screen-off device tests (§7) show missed ticker updates, a `PARTIAL_WAKE_LOCK` held only while monitoring runs is the next step, with its battery cost measured and documented. It requires adding the `WAKE_LOCK` permission (§5).

#### Binance connection limit

Binance closes every connection to `stream.binance.com` after 24 hours. The service must treat this as a routine reconnection, without exposing it to the user as an error. No special case is needed: the connection was up for more than 60 s, so the backoff rule above reconnects without delay.

#### Android lifecycle considerations

The implementation must account for:

- Foreground Service launch restrictions.
- Appropriate foreground service type and permissions.
- Notification permission requirements.
- Android process termination.
- Battery optimization and manufacturer-specific restrictions.

The application must not promise uninterrupted monitoring under all Android system conditions.

If the monitoring service is interrupted, the application must clearly expose the monitoring state when it becomes available again.

WorkManager must not be used as the primary mechanism for continuous real-time price monitoring.

## 3. Technical Architecture

### 3.1 Technology Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose |
| Architecture | MVVM |
| Asynchronous operations | Kotlin Coroutines and Flow |
| HTTP and WebSocket client | OkHttp |
| JSON parsing | kotlinx.serialization |
| Local database | Room |
| Preferences | DataStore |
| Charts | Vico |
| Notifications | `NotificationManagerCompat` (AndroidX Core) |
| Dependency injection | Manual (no DI framework) |
| Build system | Gradle Kotlin DSL, versions in a version catalog (`gradle/libs.versions.toml`) |
| JDK | 21 (Temurin), pinned in `.tool-versions`; CI uses the same major version |
| CI/CD | GitHub Actions |
| Tests | JUnit 4 (`RobolectricTestRunner` is a JUnit 4 runner; JUnit 5 is not used), `kotlinx-coroutines-test`, Robolectric (Room and notifications on the JVM), OkHttp `MockWebServer` |

Unit and integration tests (§7) run on the JVM (`./gradlew testDebugUnitTest`), so CI runs them without an emulator. Device tests (§7) are manual, on a physical device.

Retrofit is not used: the application makes a single REST call (klines), whose array-of-arrays response is parsed directly with kotlinx.serialization.

Android SDK levels:

| Setting | Value |
|---|---|
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` / `compileSdk` | 37 (Android 17, stable since June 2026) |

Use stable, mutually compatible dependency versions.

Avoid unnecessary libraries and abstractions.

### 3.2 Suggested Project Structure

```text
bitcoin-alert/
├── app/
│   ├── schemas/          (exported Room schemas, committed; §3.5)
│   └── src/main/
│       ├── java/.../bitcoinalert/
│       │   ├── data/
│       │   │   ├── binance/
│       │   │   ├── database/
│       │   │   └── repository/
│       │   ├── domain/
│       │   │   └── model/
│       │   ├── service/
│       │   │   └── PriceMonitorService.kt
│       │   ├── notifications/
│       │   ├── ui/
│       │   │   ├── home/
│       │   │   ├── charts/
│       │   │   ├── alerts/
│       │   │   └── theme/
│       │   └── MainActivity.kt
│       └── AndroidManifest.xml
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── release.yml
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

The structure is a starting point, not a requirement to introduce unnecessary modules. There is no use case layer, and repositories are concrete classes: introduce an interface only when a test needs a fake.

### 3.3 Binance Integration

Implement a dedicated Binance market data client.

Responsibilities:

- REST API requests.
- WebSocket connection management.
- Message parsing.
- Subscription management.
- Connection state tracking.
- Reconnection and backoff.
- Error handling.

The client is a single process-scoped instance shared by the UI and the Foreground Service. It holds one WebSocket connection; UI components and alert consumers never open their own.

Subscriptions:

| Situation | Streams |
|---|---|
| Home visible | `btcusdt@ticker` + `btcusdt@kline_<selected interval>` |
| Alerts visible, or monitoring running | `btcusdt@ticker` |
| Neither screen visible, monitoring stopped | None — the connection is closed |

Alerts needs the ticker for the distance from the current price to each target (§4). "Monitoring running" is the in-memory running flag (§2.8); its change takes effect at once, with no one-second delay. "Not visible" includes the History screen and the application in the background. Home or Alerts becoming not visible takes effect after one second, and becoming visible again within it cancels it: a configuration change (rotation, theme, font size) stops and restarts the activity, and must not close and reopen the connection. Going from Home to Alerts keeps the ticker and drops the kline stream after that second.

With no stream needed, the client is in an internal `Idle` state (never shown in the UI): the connection is closed on purpose, and the backoff, the dead connection watchdog and the network callback do nothing. It leaves `Idle` (to `Connecting`) only when the desired stream set becomes non-empty. Entering `Idle` resets the backoff counter (§2.8): a deliberate close is not a failure, so the first attempt after leaving `Idle` runs without delay. Leaving `Idle` takes a user navigation, so this cannot approach Binance's attempt limit.

Changing the timeframe replaces the kline subscription (`UNSUBSCRIBE` the old one, `SUBSCRIBE` the new one) on the same connection; Home visibility changes add or remove it. Binance accepts at most 5 incoming messages per second per connection, counting ping and pong frames as well as JSON control messages. Every change to the stream set is coalesced: at most once per second, the client compares the desired stream set with the current one and sends only the difference (at most one `UNSUBSCRIBE` and one `SUBSCRIBE`). The first change after at least one second without sending is sent immediately and opens a one-second window; changes made within that window replace each other, and only the latest desired set is sent when the window ends (opening a new window). A window that ends with the desired set equal to the current one sends nothing. Two control messages plus a pong stay under the limit.

While `Connecting` or `Reconnecting`, no control message is sent: each attempt builds its URL from the latest desired set. If the desired set changes while an attempt is in flight, the current set is the one in that attempt's URL; after `onOpen`, the coalescing above sends the difference.

After a timeframe change, kline messages from the previous stream can still arrive. The client discards any kline message whose `stream` is not the selected interval's, and cancels the REST candle load still in flight for the previous timeframe.

Kline streams update every 2000 ms; the ticker every 1000 ms.

The server sends a ping frame every 20 seconds and expects a pong with the same payload. OkHttp answers pings automatically; do not disable that behavior.

Dead connection detection: a network change can leave the socket half-open without any error. While `Connected`, if no message (of any stream) arrives for 10 seconds, the client reconnects. The watchdog does not run while `Connecting` or `Reconnecting`, where the backoff controls retries, nor while `Idle`. Stale data (§2.1) is derived only from the age of the last ticker message, since the displayed price comes from the ticker; kline messages and control responses do not refresh it. The watchdog counts any message and decides reconnection, not staleness.

The client also listens to `ConnectivityManager` network callbacks. When a network becomes available, the backoff is reset, so the next attempt runs without delay; if that attempt fails, the backoff applies again. Each `onAvailable` grants one immediate attempt, so a network that flaps N times produces N immediate attempts; a flapping network cannot plausibly approach Binance's 300 attempts per 5 minutes (§2.8), and failures without a network change always wait. `onAvailable` also fires right after registration for the current network, so the callback acts only in `Connecting` or `Reconnecting` (never in `Connected` or `Idle`).

Loading candles (only while Home is visible, the only situation with a kline stream: on start, timeframe change, reconnection and Home becoming visible again; a reconnection with Home not visible loads nothing):

1. Subscribe to the kline stream and buffer its updates. The subscription counts only once it is in effect: the connection whose URL lists the stream is open (`onOpen`), or the response `{"result": null, "id": <n>}` to its coalesced `SUBSCRIBE` has arrived (§2.2). A stream in the URL of a connection still `Connecting`, or a `SUBSCRIBE` only sent, does not count yet.
2. Only then load the 200 candles of the selected timeframe through REST. Starting it earlier could miss the final update of a candle that closes before the subscription.
3. Apply the buffered updates, discarding any whose `openTime` is older than the last loaded candle.

After reconnection, reload the candles this way to restore chart consistency. Returning to Home with monitoring running re-subscribes the kline stream on the existing connection, without a reconnection; it must reload too, or the chart keeps a gap for the time Home was not visible.

If the REST request fails, the chart shows the error with a retry action. The next reconnection also retries the load. A REST response with any price finer than the tick size fails the whole load like any other error: discarding only that candle, as the WebSocket parser does with a message, would leave a silent gap in the chart.

Binance closes connections after 24 hours (see §2.8); reconnection must restore the current subscriptions.

There is no fallback host for geo-blocking (HTTP 451): Binance does not block Brazil, where the application is used. If that changes, the market-data-only hosts `data-api.binance.vision` (REST) and `data-stream.binance.vision` (WebSocket) serve the same public data.

Do not assume that WebSocket delivery is guaranteed or that no updates are lost during disconnection.

### 3.4 Alert Engine

Implement the alert evaluation logic as a domain-level component independent of Android UI and notification APIs.

Responsibilities:

- Receive price updates.
- Evaluate active alerts.
- Detect price crossings.
- Prevent duplicate triggers.
- Persist trigger state.
- Produce alert-trigger events.

The alert engine must be deterministic and independently testable.

The engine keeps the last price seen by each Active `(id, activationCycle)`. A cycle seen for the first time takes the price as its baseline and is evaluated from the next price; every other cycle compares its stored price with the new one, then stores the new one. This is the `previousPrice` of §2.5 without a special case for baselines. A WebSocket reconnection keeps the stored prices; a monitoring start or service restart clears them. The engine returns the crossed alerts; the trigger transaction (§3.5) is the only duplicate-trigger guard, so a cycle replaced between the evaluation and the transaction changes nothing.

### 3.5 Persistence

Use Room for alert configuration and history.

Use DataStore for lightweight application preferences, such as monitoring preferences.

The DataStore is a single process-scoped instance, like the Binance client (§3.3), shared by the UI and the Foreground Service: two DataStore instances open on the same file throw `IllegalStateException`.

Suggested database entities:

#### PriceAlertEntity

```kotlin
@Entity(tableName = "price_alerts")
data class PriceAlertEntity(
    @PrimaryKey val id: String, // UUID.randomUUID().toString()
    val targetPrice: String,
    val direction: String,
    val enabled: Boolean,
    val triggeredAt: Long?,
    val createdAt: Long,
    val activationCycle: Int
)
```

#### AlertHistoryEntity

```kotlin
@Entity(tableName = "alert_history")
data class AlertHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alertId: String,
    val targetPrice: String,
    val observedPrice: String,
    val direction: String,
    val triggeredAt: Long
)
```

Store monetary values as decimal strings to preserve precision, written with `toPlainString()` from the scale-2 value (`100.00`).

Schema export is on (`exportSchema = true`, directory set with the Room Gradle plugin's `room { schemaDirectory(...) }`), and the exported `schemas/` JSON files are committed: the migrations after `v1.0.0` are written and tested against them.

Every schema change bumps `@Database(version)`, including during `v0.*`. Room's fallbacks run only on a version upgrade: a changed schema with the same version fails the identity hash check with `IllegalStateException` ("Room cannot verify the data integrity"), fallback or not.

Schema changes before v1.0.0: `v0.*` releases are installed over each other, so while the version is `v0.*` the database is built with `fallbackToDestructiveMigration(dropAllTables = true)` (a schema change drops the test alerts and history instead of crashing). Before the `v1.0.0` tag, the fallback is replaced by `fallbackToDestructiveMigrationFrom(dropAllTables = true, <every schema version older than v1.0.0's>)`: an update from a `v0.*` install whose schema differs drops its test data instead of crashing on a missing migration (`IllegalStateException`). Every schema change after `v1.0.0` ships a Room migration. The overloads without `dropAllTables` are deprecated.

Alert triggering runs in one Room transaction:

1. `UPDATE price_alerts SET triggeredAt = :now WHERE id = :id AND activationCycle = :activationCycle AND enabled = 1 AND triggeredAt IS NULL`.
2. Only if exactly one row changed: insert the `alert_history` entry.
3. After the commit: post the notification (§2.6).

The conditional update is the duplicate-trigger guard: a second evaluation of the same activation changes zero rows, and so does an evaluation based on a cycle that was replaced in the meantime (`activationCycle` no longer matches).

Enabling and reactivating run as one statement, so the counter never depends on a value read earlier: `UPDATE price_alerts SET enabled = 1, triggeredAt = NULL, activationCycle = activationCycle + 1 WHERE id = :id AND (enabled = 0 OR triggeredAt IS NOT NULL)`. The last condition leaves an Active alert alone, so a repeated tap does not restart its baseline.

## 4. User Interface

The application should contain three primary areas:

### Home

- Current BTC/USDT price.
- 24-hour percentage change.
- Connection status and stale data indication.
- Monitoring status and start/stop control.
- Notifications-disabled and monitoring-stopped warnings (§2.1).
- Battery optimization exemption action, next to the monitoring control, shown only while not exempt (§2.1, §2.8).
- Candlestick chart.
- Timeframe selector.
- Quick access to alerts and history.

### Alerts

- List of active alerts.
- List of triggered alerts.
- List of disabled alerts.
- Within the Active and Disabled lists, newest first: `ORDER BY createdAt DESC, id DESC`. The Triggered list shows the most recent trigger first: `triggeredAt DESC, id DESC`. Never sort by `targetPrice` in SQL: it is stored as a string, so `"90000.00"` would sort after `"100000.00"`. Sorting by target, if ever added, is done in Kotlin with `BigDecimal.compareTo`.
- Create alert action.
- Enable/disable actions.
- Delete action.
- Reactivate action.

### History

- Triggered alert records.
- Direction.
- Target and observed prices.
- Trigger timestamps.
- Newest-first ordering.

Keep navigation simple and avoid unnecessary screens.

Edge-to-edge is mandatory with `targetSdk` 35+ and cannot be opted out of from 36: call `enableEdgeToEdge()` and apply system bar insets on every screen.

### Visual design

The visual direction is "Ticker" (`docs/prototypes/a-ticker.html`):

- A fixed Material 3 palette, light and dark, with an orange accent; the rising and falling colors belong to the same palette. Text keeps a contrast of at least 4.5:1 against its background.
- Numbers (prices, the 24-hour change, OHLC, axes, timeframes) use JetBrains Mono, bundled in the APK with its SIL Open Font License (`assets/licenses/`), so digits keep their width as the price updates. Downloadable fonts are not used: they need Google Play services and a network call. Other text uses the system font.
- A bottom navigation bar with Market (Home), Alerts and History. Home stays at the bottom of the back stack, so Back from Alerts or History returns to it.
- Home: the pair with the connection status as a pill (`Connected`, `Connecting`, `Reconnecting`) and a separate `Stale` pill; the price; the 24-hour change; the monitoring row; timeframe tabs; the chart.
  - The monitoring row shows a dot (rising color while Running, muted while Stopped, amber while Interrupted), "Monitoring running", "Monitoring stopped" or "Monitoring interrupted", the number of Active alerts ("No active alerts" at zero) and the buttons of §2.1. At large font sizes the buttons move to the next line as a whole.
- Alerts: the current price under the title, then the Active, Triggered and Disabled sections; empty sections are hidden. The Alerts navigation item shows the number of Active alerts as a badge (hidden at zero).
  - Each Active alert shows the distance from the current price to its target, `|target − price| / price` with 2 decimal places (`HALF_UP`), and a bar filled `1 − distance / 10%`, clamped to [0, 1] (full at the target, empty at 10% or more). When the price is already past the target (above it for `ABOVE`, below it for `BELOW`), the alert shows "Price above target · waits for a crossing" (or below) and no bar. Without a price, only the creation date.
  - Actions by state: Active has a switch (on; turning it off disables) and Disabled a switch (off; turning it on enables); Triggered has a Reactivate button and shows the observed price and trigger time from its history entry. Every alert has a menu with Delete, an explicit action instead of a swipe, confirmed by a dialog ("Its history stays").
  - New alert opens a bottom sheet: Above/Below segmented buttons, the target field, the parsed target and its percentage from the current price ("Saves as 100,000.50 · +2.66% from now"), the validation error inline, and Create, enabled only for a valid target. A target already past the current price for the chosen direction is allowed, with a warning that the alert fires only after the price comes back and crosses it. There are no quick-value chips.
- History: entries grouped by day (device time zone), newest first. Day headers read "Today", "Yesterday", then the short date with the device locale (`Nov 8`), with the year only when it is not the current one (`Oct 9, 2025`). Each entry shows the direction, the target, the observed price and the trigger time.
- The target price is typed with the system decimal keyboard.

### Accessibility

- The 24-hour change shows its sign and an arrow, not only a red or green color.
- Icon-only buttons have a `contentDescription`.
- Layouts remain usable at the largest system font size.

## 5. Security and Privacy

The application must:

- Use HTTPS and secure WebSocket connections; block cleartext traffic with a Network Security Configuration (`android:networkSecurityConfig` pointing to `<base-config cleartextTrafficPermitted="false" />`). Blocking is the default only from API 28, and `minSdk` is 26. Do not use `android:usesCleartextTraffic`: Android 17 announced it will be deprecated in a future release, in favor of the Network Security Configuration (available from API 24).
- Request only necessary Android permissions.
- Avoid storing sensitive information.
- Avoid unnecessary network access.
- Avoid third-party analytics.
- Avoid advertising SDKs.
- Avoid collecting personal information.
- Avoid embedding API credentials or secrets.
- Keep all alert data locally on the device: set `android:allowBackup="false"`, since Android Auto Backup is on by default and would copy the Room database to the user's Google Drive. On Android 12+ `allowBackup="false"` does not disable device-to-device transfer, so also set `android:dataExtractionRules` excluding all data from both cloud backup and device transfer.

Binance public market endpoints do not require API keys.

Permissions:

| Permission | Reason |
|---|---|
| `INTERNET` | Binance REST and WebSocket. |
| `ACCESS_NETWORK_STATE` | Network callbacks for immediate reconnection (§3.3). |
| `FOREGROUND_SERVICE` | Monitoring service. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | `specialUse` service type (API 34+). |
| `POST_NOTIFICATIONS` | Monitoring and alert notifications (API 33+, runtime). |
| `VIBRATE` | Alert vibration. Possibly unnecessary: with `minSdk` 26, vibration is a channel property. Remove it if the Phase 4 test shows channel vibration works without it. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Battery optimization dialog, on user action (§2.8). |
| `WAKE_LOCK` | Only if the screen-off tests require a `PARTIAL_WAKE_LOCK` (§2.8). Not declared otherwise. |

## 6. Build and Distribution

The application will be distributed exclusively through GitHub Releases.

### Release requirements

- Generate a signed Release APK.
- Keep signing credentials in GitHub Actions Secrets.
- Use the same signing key for subsequent releases.
- Generate a SHA-256 checksum. It only detects a corrupted download: it is published next to the APK, so it does not prove the APK was not tampered with. The file uses the `sha256sum` format (`<hash>  <file name>`), generated with `sha256sum bitcoin-alert-vX.Y.Z.apk > bitcoin-alert-vX.Y.Z.apk.sha256`, so users check it with `sha256sum -c` (Linux) or `shasum -a 256 -c` (macOS) in the download folder.
- Publish the SHA-256 fingerprint of the signing certificate in the README, so users can check an APK with `apksigner verify --print-certs`.
- Attach the APK and checksum to the GitHub Release.
- Trigger releases through version tags.
- Derive `versionCode` from the tag (`vMAJOR.MINOR.PATCH` → `MAJOR * 10000 + MINOR * 100 + PATCH`, e.g. `v1.2.3` → `10203`) and `versionName` from the tag without the `v`. Android rejects an update whose `versionCode` is lower than the installed one (`INSTALL_FAILED_VERSION_DOWNGRADE`).
- The workflow accepts only tags matching `^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)$` (no leading zeros, MINOR and PATCH from 0 to 99, so the formula never collides) and fails on any other tag and on `v0.0.0`, whose `versionCode` would be 0 (Android requires a positive value). Pre-release tags are not supported in the MVP.
- Builds without a version tag (local builds, pull request CI) use `versionCode = 1` and `versionName = "0.0.0-dev"`. They are never published.
- The tag validation and `versionCode` derivation live in one function in the Gradle build logic, so they can be unit tested (§7) and the release workflow only passes the tag to Gradle.
- Release builds do not enable R8 minification or resource shrinking in the MVP: the APK size gain does not justify keep-rule debugging for kotlinx.serialization and Room.
- Publish `v0.*` tags as GitHub pre-releases (`--prerelease`), so test builds never become the "Latest" release users are pointed to.

Example:

```text
v1.0.0
  ├── bitcoin-alert-v1.0.0.apk
  └── bitcoin-alert-v1.0.0.apk.sha256
```

Example release trigger:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The application must use a stable Android application ID and consistent signing configuration to support future APK updates without uninstalling the previous version.

The debug build type uses `applicationIdSuffix = ".debug"` (`io.github.allisson.bitcoinalert.debug`). Debug builds are signed with the debug key; with the same application ID, a debug build on the device would block installing the release APK (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) until it is uninstalled, which deletes the alerts. With the suffix both coexist, each with its own data. Debug builds are installed through Android Studio or `adb`, so the suffixed ID needs no developer verification registration.

### Distribution risk: Android developer verification

Google requires apps installed on certified Android devices to come from a verified developer with a registered application ID. Enforcement started on September 30, 2026 in Brazil, Indonesia, Singapore and Thailand, and expands globally in 2027.

What is known (sources checked on 2026-11-13):

- Unregistered apps can still be installed through `adb` or through the "advanced flow" for experienced users. Unregistered package names cannot be installed through the normal flow on certified devices in Brazil.
- Apps distributed only outside Google Play are registered in the Android Developer Console. Registering a package name requires the SHA-256 certificate fingerprint of the app's signing key. A package name already in use outside the console also requires proof of ownership: an APK signed with that key, carrying a snippet the console provides.
- The free limited distribution account is available to everyone. It is meant for hobbyists and personal use, with no government ID required. It needs a Google Account with 2-Step Verification, a linked Google payments profile (legal name and address) and a contact email. It registers apps and shares them with up to 50 devices whose users authorize them through a QR code or link handshake.

The limited distribution account fits this application (personal use). Development builds installed through Android Studio or `adb` are exempt, so registration is needed only before the first release is installed from GitHub Releases. It is done in Phase 6, before the first release tag.

Decisions and status:

- Application ID: `io.github.allisson.bitcoinalert` (derived from the GitHub account; a new name, so registration needs no proof of ownership). Fixed in Phase 0; never changed after the first release.
- 2-Step Verification on the Google Account: done.
- Release signing key: pending (Phase 6).
- Limited distribution account and application ID registration: pending (Phase 6).

Record the outcome here when done: application ID registered, signing certificate SHA-256 fingerprint registered, and the installation path documented for users.

Sources: [Android Developers Blog, 2026-06](https://android-developers.googleblog.com/2026/06/android-developer-verification.html), [Android Developers Blog, 2026-03](https://android-developers.googleblog.com/2026/03/android-developer-verification-rolling-out-to-all-developers.html), [Register on Android Developer Console](https://developer.android.com/developer-verification/guides/android-developer-console), [Limited distribution account](https://developer.android.com/developer-verification/guides/limited-distribution).

## 7. Testing Requirements

Testing must cover the critical application behavior.

### Unit tests

- Price parsing.
- Decimal price comparisons, including normalization of 8-decimal Binance prices to scale 2 and comparison with `compareTo` across different scales; a price finer than the tick size is discarded without a crash; volume and the 24-hour change are not normalized.
- ABOVE and BELOW crossing detection.
- Per-cycle baseline on creation, enabling and reactivation, including a disable/enable between two ticker updates; each enabling or reactivation increments `activationCycle`; the baseline of a cycle that is no longer Active is dropped.
- `previousPrice` kept across reconnections and reset on monitoring start or service restart.
- Alert state transitions (trigger, disable, enable, reactivate).
- Target price validation: `100000.50` and `100000,50` both parse to `100000.50` in pt-BR and en-US; `100.000,50`, `100,000.50`, more than one separator, more than 2 decimal places (including trailing zeros: `1.000`, `100,000`), zero, `.50` and `100.` are rejected; ` 100.50 ` (surrounding spaces) and `0100` are accepted; `100` is normalized to `100.00`.
- 24-hour change display, with the device locale's decimal separator: `-3.829` shows `-3.83` (pt-BR `-3,83`) with a down arrow; `-0.004` shows `0.00` with no sign and no arrow.
- Candle volume display: `"336.78983000"` shows `336.78983`, with the device locale's decimal separator (pt-BR `336,78983`).
- Alert lists ordered `createdAt DESC, id DESC` (§4); history ordered `triggeredAt DESC, id DESC` (§2.7).
- Timeframe: `1h` with nothing stored; a selected timeframe is restored from DataStore.
- Duplicate trigger prevention, including an evaluation from a replaced cycle.
- Alert history creation.
- Monitoring state derivation (Stopped, Running, Interrupted), and the monitoring control actions for each state (both `Start` and `Stop` while Interrupted).
- Home warnings (§2.1): the monitoring warning shows only with Active alerts and monitoring `Stopped` or `Interrupted`; the notifications warning shows only with Active alerts and one of permission denied, app notifications off or Price alerts channel blocked, and names the matching settings screen. Neither shows without Active alerts. The battery optimization action shows only while not exempt.
- Price placeholder `—` before the first price; stale indication only after a price was received.
- `versionCode` derivation from the tag, including rejection of leading zeros and `v0.0.0`, and the `1` / `0.0.0-dev` default without a tag.
- Reconnection backoff delays, with an injected random source: the first failure waits 1 s base, the base doubles per consecutive failure, jitter adds 0 to 20% of the base delay and never subtracts, the base caps at 60 s (wait at most 72 s), the first attempt runs without delay after a connection that stayed up for 60 s and after leaving `Idle`, and a connection that drops before 60 s counted as a failed attempt.
- Kline merge, for buffered updates after the REST load and for live messages (§2.3, §3.3): an update with the same `openTime` as the last candle replaces it, a newer one is appended, an older one is discarded.
- Candle reload on start, timeframe change, reconnection and Home becoming visible again; no reload on a reconnection with Home not visible.
- Candle REST load starts only once the kline subscription is in effect (connection open with the stream in its URL, or `SUBSCRIBE` response received); not while `Connecting`, nor right after the `SUBSCRIBE` is sent.
- A failed `SUBSCRIBE`/`UNSUBSCRIBE` response triggers a reconnection.
- Monitoring start/stop write order: no Interrupted state derived during a normal start or stop, including when the `monitoringRequested` write completes before the service's `onCreate`.
- Stream set derivation from Home visibility and monitoring state (§3.3 subscription table).
- Stream set changes during `Connecting`/`Reconnecting`: no control message is sent, the next attempt uses the latest set, and after `onOpen` only the difference from the URL's set is sent.
- Subscription change coalescing: the first change after a quiet second is sent immediately; rapid timeframe selections and Home visibility changes send at most one `UNSUBSCRIBE`/`SUBSCRIBE` pair per second, carrying the latest desired stream set; a window that ends with no difference sends nothing.
- Alert notification IDs: two alerts triggered by the same ticker update produce two notifications.
- Kline messages from a previous interval are discarded after a timeframe change.
- Stale data: derived from the age of the last ticker message (over 10 seconds), in any connection state; kline messages and control responses do not refresh it.
- Dead connection watchdog: 10 seconds without messages while `Connected` triggers a reconnection; it does not fire while `Connecting` or `Reconnecting`.
- Network availability resets the backoff once; a failed attempt after it waits the backoff delay.
- `Idle` state: with an empty stream set, network availability, the watchdog and the backoff never open a connection; a non-empty stream set leaves `Idle`.
- Monitoring state after process death: the in-memory running flag starts `false`, so `monitoringRequested = true` derives Interrupted.

### Integration tests

JVM tests (§3.1): Robolectric for Room and notifications, `MockWebServer` for REST and WebSocket.

- Binance REST response parsing.
- WebSocket message parsing, including `SUBSCRIBE`/`UNSUBSCRIBE` responses.
- Room persistence.
- Alert transaction consistency.
- Notification generation.
- Service restart with `monitoringRequested = true`: `onStartCommand(null, …)` calls `startForeground`.
- Service restart with `monitoringRequested = false`: `onStartCommand(null, …)` calls `startForeground`, then `stopSelf()`. On a device this needs the process to die between the flag write and the service stop, so it is tested here: build the service with `Robolectric.buildService(...).create()` and call `onStartCommand(null, 0, 1)` on it directly.

### Device tests

- Background monitoring.
- Screen-off monitoring.
- Network disconnection.
- Network reconnection, including half-open connections after a network change.
- Application process termination.
- Notification permission denial, app notifications turned off and Price alerts channel blocked.
- Stopping the app from the Task Manager (Android 13+): expected state Interrupted; verify whether `START_STICKY` restarts it.
- Foreground Service lifecycle.
- Battery consumption.

Prioritize complete coverage of critical business rules rather than an arbitrary overall code coverage percentage.

## 8. MVP Acceptance Criteria

The MVP is complete when:

- [ ] The application installs through a signed APK.
- [ ] The current BTC/USDT price updates in real time.
- [ ] The 24-hour percentage change is displayed.
- [ ] Binance connection status is visible.
- [ ] Stale data is indicated after 10 seconds without ticker messages.
- [ ] Historical candles load successfully.
- [ ] All four timeframes work.
- [ ] Candlestick charts update correctly.
- [ ] Users can create ABOVE alerts.
- [ ] Users can create BELOW alerts.
- [ ] Users can disable, enable, reactivate and delete alerts.
- [ ] Users can start and stop monitoring.
- [ ] Alert crossing detection works correctly.
- [ ] Duplicate notifications are prevented.
- [ ] Alerts survive application restarts.
- [ ] Triggered alerts appear in history, newest first.
- [ ] Sound and vibration work when permitted.
- [ ] Foreground monitoring works with the screen off under supported Android conditions.
- [ ] WebSocket reconnection works.
- [ ] Monitoring interruption is communicated to the user (Interrupted state, including after a reboot).
- [ ] The main screen warns when Active alerts exist and monitoring is not running.
- [ ] The main screen warns when Active alerts exist and notifications are disabled, and the warning disappears after the cause is fixed in system settings.
- [ ] The battery optimization action opens the system dialog and disappears once the application is exempt.
- [ ] The selected timeframe is restored on the next launch.
- [ ] Tapping an alert notification opens the History screen.
- [ ] Alert data is excluded from cloud backup and device-to-device transfer.
- [ ] Every screen is edge-to-edge with system bar insets applied.
- [ ] The accessibility requirements in §4 are met.
- [ ] GitHub Actions builds and publishes a signed APK.

## 9. Future Considerations

The following features are explicitly outside the MVP but may be considered in future versions:

- BRL price conversion.
- Additional cryptocurrencies.
- Additional exchanges.
- Technical indicators.
- Android home screen widget.
- Advanced alert conditions.
- Price alerts with configurable cooldowns.
- Optional encrypted backup.
- Historical alert statistics.
- Additional chart interactions.

These features must not increase the complexity of the initial implementation.

---

**End of Specification**
