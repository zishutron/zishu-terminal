# Zishu Terminal v6.0.0

Android terminal app (`com.zishu.terminal`, versionCode 600).

## Build (GitHub Actions)
1. Push this repo to GitHub.
2. Actions → **Android Build** → *Run workflow*. Download the `Zishu-Terminal-v6.0.0-debug` artifact.

Local: `./gradlew assembleDebug` (JDK 17 + Android SDK 34).

## Signed release
Add repository secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, then push tag `v6.0.0`. Keep the same key forever, or updates will not install over older builds. Never commit keystores (`.gitignore` blocks them).

## Status
| Feature | State |
|---|---|
| Gradle build, CI, version metadata | Done (CI run not yet verified) |
| Terminal UI, sessions, history, files, editor, local `zpkg` | Existing, preserved |
| Real Python / Git / Bash / `pkg` / curl / Node / SSH / clang | **Not implemented** — shell is `/system/bin/sh` |

Next step (see docs): bootstrap prefix + Termux-compatible packages, which needs binaries built with a matching prefix.
