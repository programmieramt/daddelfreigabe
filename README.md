# Daddelfreigabe

Android app that controls internet access for specific services (e.g. YouTube) via [AdGuard Home](https://github.com/AdguardTeam/AdGuardHome). Tasks must be completed before services get unblocked.

## How it works

1. Configure your AdGuard Home server, client IP and which services to control
2. Complete all tasks in the checklist
3. Tap "Freischalten" to unblock the configured services
4. "Sperren" blocks the services again and resets all tasks

Uses the AdGuard Home REST API (`/control/clients/update`) to toggle `blocked_services` per client.

## Setup

In the app settings, configure:

| Field | Example |
|-------|---------|
| Server URL | `http://192.168.68.124` |
| Username | your AdGuard admin user |
| Password | your AdGuard admin password |
| Client IP | `192.168.68.53` |
| Services | YouTube, TikTok, Discord, ... |

The client must already exist in AdGuard Home under **Settings → Client settings**.

## Build

```sh
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/`

## CI

- **Android CI** — builds on every push/PR to `main`, uploads APK as artifact
- **Release Debug APK** — manual workflow dispatch, creates a GitHub Release with the debug APK
