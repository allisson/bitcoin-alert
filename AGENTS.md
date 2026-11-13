# AGENTS.md

Instructions for coding agents working on this repository.

## Project

Bitcoin Alert is a personal Android app that monitors BTC/USDT through the Binance public market
API. It shows real-time prices and candlestick charts and sends local price alerts. It has no
backend, no accounts and no analytics. It is distributed as a signed APK through GitHub Releases.

## Source of truth

- `docs/spec.md` defines the requirements. When code and spec disagree, the spec wins. If the spec
  is wrong or incomplete, update it in the same change and say so.
- `docs/roadmap.md` holds the implementation phases and tasks. Work phase by phase, and keep each
  phase independently testable.
- Mark a roadmap task `[x]` only after it is implemented and its acceptance criteria are verified.
  Never remove a requirement silently. Add newly discovered tasks to the matching phase.
- Cite spec sections (`spec.md` §2.5) in code comments only when the reason isn't obvious from the code.

## Stack

Kotlin, Jetpack Compose, MVVM, Coroutines/Flow, OkHttp (REST and WebSocket),
kotlinx.serialization, Room, DataStore, Vico, Gradle Kotlin DSL with `gradle/libs.versions.toml`.
`minSdk` 26, `targetSdk`/`compileSdk` 37. See `spec.md` §3.1.

- Use no DI framework (manual wiring) and no Retrofit. There is no use-case layer.
- Repositories are concrete classes. Add an interface only when a test needs a fake.
- Do not add a dependency for something a few lines can do.

## Commands

```bash
./gradlew assembleDebug          # debug build (applicationId suffix .debug)
./gradlew testDebugUnitTest      # all JVM tests: unit + Robolectric + MockWebServer
```

Every automated test runs on the JVM. CI runs them without an emulator. Device tests (`spec.md` §7)
are manual, on a physical device.

## Rules that are easy to get wrong

- **Prices:** use `BigDecimal` only, normalized with `setScale(2, RoundingMode.UNNECESSARY)`. Compare
  them with `compareTo`, never with `==` or `equals`. Store them as decimal strings, and never
  `ORDER BY` a price column in SQL. See `spec.md` §2.5 and §3.5.
- **Alerts:** an alert triggers on a crossing, not on a level, and has a baseline for each
  `(id, activationCycle)`. The trigger is one Room transaction with a conditional `UPDATE`. Post the
  notification only after the commit. See `spec.md` §2.5 and §3.5.
- **Binance client:** one process-scoped instance and one WebSocket connection. Stream set changes
  are coalesced to at most one per second, because of the limit of 5 incoming messages per second.
  See `spec.md` §3.3.
- **DataStore:** one process-scoped instance. The service runs in the main process
  (no `android:process`).
- **Room:** bump `@Database(version)` on every schema change, including during `v0.*`. Commit the
  exported files in `app/schemas/`. After `v1.0.0`, every schema change ships a migration.
- **Versioning:** `versionCode` is derived from the `vMAJOR.MINOR.PATCH` tag. Test releases use only
  `v0.*` tags. See `spec.md` §6.
- **Signing:** never commit keystores, passwords or `local.properties`.

## Tests

Write each test in the same change as the feature it covers. `spec.md` §7 lists the required
cases. Cover the critical business rules (alert engine, backoff, kline merge, monitoring state);
there is no coverage percentage target.

## Commits

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/):

```text
<type>(<optional scope>): <description>

[optional body]

[optional footer(s)]
```

- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `build`, `ci`, `chore`, `style`, `revert`.
- Scopes, when useful, name the area: `binance`, `alerts`, `chart`, `service`, `notifications`,
  `db`, `ui`, `release`.
- Write the description in the imperative and lower case, with no trailing period.
- Mark a breaking change with `!` after the type/scope, or with a `BREAKING CHANGE:` footer.
- Make one logical change per commit.

Examples:

```text
feat(alerts): evaluate ABOVE and BELOW crossings per activation cycle
fix(binance): reset backoff only in Connecting or Reconnecting
docs(spec): define alert list ordering
ci: run JVM tests on pull requests
```
