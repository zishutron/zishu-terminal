# Zishu Terminal v6.0.0

- Project migrated to Gradle (AGP 8.5.2, Gradle 8.7, JDK 17); APK is built by GitHub Actions.
- Version 6.0.0 (code 600), Stable channel; beta branding removed.
- Existing features unchanged: terminal, sessions, history, file manager, editor, diagnostics, local `zpkg` packages.

## Not included yet
Native Python, Git, Bash, `pkg` (Termux-style) and other toolchains are **not** bundled in this release. The shell is still Android's `/system/bin/sh`.

## Upgrading
Signed with your own release key. An APK signed with a different key than the one on the device cannot update it in place; uninstall first (this deletes app data) or keep using the original key.
