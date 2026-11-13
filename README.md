# bitcoin-alert

A lightweight, personal Android application for monitoring Bitcoin prices in real time through the
Binance public market API: live BTC/USDT price, candlestick charts and local price alerts. No
backend, no accounts, no analytics. Distributed as a signed APK through GitHub Releases.

Status: in development. See [`docs/spec.md`](docs/spec.md) for the requirements and
[`docs/roadmap.md`](docs/roadmap.md) for the implementation phases.

## Requirements

- JDK 21 (Temurin), pinned in `.tool-versions`.
- Android SDK with platform 37. Point Gradle to it with `ANDROID_HOME` or with
  `sdk.dir=<path>` in `local.properties` (not committed).

## Build and test

```bash
./gradlew assembleDebug          # debug APK (application ID io.github.allisson.bitcoinalert.debug)
./gradlew testDebugUnitTest      # all JVM tests: unit, Robolectric and MockWebServer
```

The debug build installs next to the release build, with its own data.

## License

[MIT](LICENSE)
