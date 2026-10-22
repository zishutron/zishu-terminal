# Zishu Terminal

A terminal app for Android (`com.zishu.terminal`) with a real Linux-style userspace: bash, coreutils, Python, Git, curl, OpenSSH and a real, signed `pkg` package manager.

**Version:** 6.0.0 (versionCode 600) · **Min Android:** 5.0 (API 21) · **Architecture:** aarch64 (arm 32-bit not built yet)

## Features

- Real `bash` shell (not just `/system/bin/sh`) with `LD_PRELOAD=termux-exec`, so `#!/bin/bash` scripts work.
- Real packages from our own signed apt repository: `python`, `pip`, `git`, `curl`, `wget`, `openssh`, `nano`, `make` and more.
- `pkg install / uninstall / search / show / list-installed / update`, backed by apt + dpkg.
- Built-in Zishu commands (`zhelp`, `zinfo`, `zdoctor`, `zpkg`, `zproject`, `zedit`, `zbackup`, `ztheme` ...). Type `zhelp` to list them.
- Sessions, command history, file tools, editor, themes, backup / restore.

## Installing the userspace (first run)

1. Install the APK and open it.
2. Run `zbootstrap install`. It downloads the bootstrap (~60 MB) from this repo's GitHub Releases, verifies its SHA-256, extracts it and runs the second stage.
3. Wait for `zbootstrap: OK`.
4. **Fully close the app and reopen it** (or Menu → Restart Shell). Only a new session starts real bash.
5. Check:
   ```
   echo $SHELL          # .../usr/bin/bash
   echo $PREFIX         # /data/data/com.zishu.terminal/files/usr
   python3 -c "print(2+3)"
   git --version
   ssh -V
   curl -I https://example.com
   ```

Needs about 600 MB free storage and an internet connection for the first install.

## Packages

```
pkg update
pkg search <name>
pkg install <name>
pkg uninstall <name>
pkg list-installed
```

The apt repo is published at `https://zishutron.github.io/zishu-terminal/apt` and signed with the project's own GPG key (public key in `repo-keys/`). Only this key is trusted by the app. Packages are built from [termux-packages](https://github.com/termux/termux-packages) with this app's prefix `/data/data/com.zishu.terminal/files/usr`.

| Package | State |
|---|---|
| bash, coreutils, apt, dpkg, nano, less, grep, sed, tar, ... | Included in bootstrap |
| python, python-pip, git, wget, openssh | Included in bootstrap |
| make | Available via `pkg install` |
| Node.js / npm (`nodejs-lts`) | Being built |
| C / C++ (clang, cmake, binutils) | Planned |

Notes: pip packages that need native compilation may fail until the C/C++ toolchain is available.

## Building

All builds run on GitHub Actions, no local setup needed.

**App (APK)**
1. Actions → **Android Build** → Run workflow.
2. Download the `Zishu-Terminal-v6.0.0-debug` artifact.

Local alternative: `./gradlew assembleDebug` (JDK 17, Android SDK 34).

Debug builds are signed with the fixed public debug key `app/debug.keystore`, so new builds install over old ones without uninstalling. This key is for debug builds only.

**Bootstrap and packages**
- Actions → **Build Bootstrap**.
  - `extra_packages`: built and bundled into the bootstrap zip.
  - `repo_only_packages`: built and published to the apt repo only (e.g. `nodejs-lts`, one group per run).
  - `build_core`: ON rebuilds and releases the bootstrap zip, OFF only adds packages to the apt repo.
- After a bootstrap run with `build_core` ON, put the new release tag and the `aarch64.sha256=` value from the run summary into `app/src/main/assets/bootstrap.properties`, then rebuild the app.
- One-time setup: run **Gen Repo Key**, store the private key as the secret `ZISHU_REPO_GPG_PRIVATE`, and delete the downloaded private key file afterwards. Never share or commit it.

## Release signing

Workflows do not hold release signing keys. The release workflow produces an unsigned APK plus its SHA-256; sign it yourself (e.g. with `apksigner`) using the same key every time. Never commit release keystores.

## Project layout

```
app/                       Android app (Java)
  src/main/assets/bootstrap.properties   bootstrap release tag + SHA-256
.github/workflows/         android-build, android-release, build-bootstrap, gen-repo-key
repo-keys/                 public GPG key of the apt repo
docs/                      BOOTSTRAP.md, RELEASE_NOTES.md
```

## Known limitations

- aarch64 only for now.
- Apps run with targetSdk 28, which is what allows executing binaries from app storage on Android 10+. This means the app is not suited for Google Play distribution.
- Python bytecode precompile during install prints harmless linker warnings.

## Security

- Bootstrap download is checked against a pinned SHA-256.
- apt only trusts the project's own signing key.
- No fake functionality: commands either run real binaries or say they are unavailable.
