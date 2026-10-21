# Real userspace (bootstrap)

Official Termux binaries have `/data/data/com.termux` hard-coded, so they cannot run under
`com.zishu.terminal`. The **Build Bootstrap** workflow therefore builds the Termux userspace from
source (upstream `termux-packages`) with `TERMUX_APP__PACKAGE_NAME=com.zishu.terminal`.

## Steps
1. Actions → **Build Bootstrap** → Run. Start with `arch=aarch64`, `extra_packages` empty (core: bash, coreutils, apt, curl, ...). Takes ~1-3 h.
2. When it finishes, the run summary shows the release tag and `aarch64.sha256=...`.
3. Put both into `app/src/main/assets/bootstrap.properties` (`tag=`, `aarch64.sha256=`), rebuild the APK.
4. In the app: `zbootstrap install`, then open a new session.
5. Next runs: `extra_packages=python git` (then `nodejs openssh`, then `clang` last — it is huge).

## Limits (honest)
- Untested on a device until you run it; first runs will likely need fixes.
- `pkg install` needs a package repository hosted for this prefix (planned: GitHub Pages + own GPG key). Until then use `extra_packages` to bundle tools.
- Android 8.1 is fine. targetSdk is 28 because Android 10+ blocks executing files from app storage for targetSdk >= 29.
- Not every Linux binary/npm/pip package works on Android.
