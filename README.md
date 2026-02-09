# FundMobile

FundMobile is an Android (Kotlin) app port of [real-time-fund](https://github.com/hzm0321/real-time-fund), focused on real-time fund valuation and top holdings tracking on mobile.

## Features

- Real-time fund valuation by fund code
- Top holdings tracking with intraday stock quote changes
- Favorites and tab filtering
- Fund grouping and group-level summary
- Holding position and trade record management
- Pull-to-refresh and configurable auto-refresh interval
- Local persistence for all user data

## Tech Stack

- Language: Kotlin
- UI: Android Views + ViewBinding
- Architecture: MVVM
- Networking: OkHttp + Gson + Jsoup
- Concurrency: Kotlin Coroutines
- Local storage: SharedPreferences
- Tests: JUnit4, Robolectric, MockWebServer, coroutines-test

## Requirements

- Android Studio (latest stable recommended)
- JDK 11+ (project currently built with JDK 21 locally)
- Android SDK (kept unchanged):
  - `compileSdk = 36`
  - `minSdk = 24`
  - `targetSdk = 36`

## Build and Run

```bash
./gradlew :app:assembleDebug
```

APK path after build:

`app/build/outputs/apk/debug/app-debug.apk`

## Run Tests

```bash
./gradlew :app:testDebugUnitTest
```

Current unit test status: 94 passed, 0 failed.

## Project Structure

- `app/src/main/java/com/example/fundmobile/data`: API, models, local persistence, repository
- `app/src/main/java/com/example/fundmobile/domain`: business logic (calculators, trading-day checks)
- `app/src/main/java/com/example/fundmobile/ui`: Activity, ViewModel, fragments, adapters, bottom sheets
- `app/src/test/java/com/example/fundmobile`: unit test suites

## Data Sources and Disclaimer

This project reads data from publicly available endpoints used by the original project. Data may be delayed or incomplete. For learning and reference only; not investment advice.

## License

This project is licensed under **GNU Affero General Public License v3.0 (AGPL-3.0)**, same as the original project.

- Full text: [`LICENSE`](./LICENSE)
- Official page: <https://www.gnu.org/licenses/agpl-3.0.html>

## Attribution

- Original project: <https://github.com/hzm0321/real-time-fund>
- This repository is an Android app adaptation implemented in Kotlin.
