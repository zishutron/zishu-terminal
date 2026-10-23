# Zishu Terminal v6.0.0

- Real Linux-style userspace: bash, coreutils, Python + pip, Git, curl, wget, OpenSSH (`zbootstrap install`).
- Real `pkg` package manager backed by apt/dpkg and a signed repository (Node.js + npm, make, cmake, binutils, clang/clang++ and more).
- Packages are built from the Termux project's termux-packages build scripts. Not affiliated with Termux. See NOTICE.md and THIRD_PARTY_LICENSES.md.
- Shell is now bash; shebang scripts work; real curl replaces the old built-in downloader after install.
- Version 6.0.0 (code 600), Stable channel.

## Install
1. Install the signed APK, open it and run `zbootstrap install` (needs about 600 MB free and internet).
2. Wait for `zbootstrap: OK`, then fully close and reopen the app.

## Upgrading
Updates must be signed with the same key as the installed app, otherwise Android refuses to update in place (uninstalling deletes app data).
