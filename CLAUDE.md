# Daddelfreigabe

Android app that controls internet access for a specific client via AdGuard Home API. Tasks must be completed before the client gets unblocked.

**Package**: `com.daddelfreigabe.app`

## Build

```powershell
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/`

## Stack

- Kotlin + Jetpack Compose (Material 3)
- OkHttp + Moshi for AdGuard Home REST API
- DataStore Preferences for settings and task persistence
- No DI framework — simple AndroidViewModel

## AdGuard Home API

- Uses `/control/access/list` (GET) and `/control/access/set` (POST)
- Blocks/unblocks clients via `disallowed_clients` list
- Basic Auth with AdGuard credentials
