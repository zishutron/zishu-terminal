# Notice and credits

Zishu Terminal's app code, UI, screens and commands are original work by the
Zishu Terminal author (see `LICENSE`).

## Packages from the Termux project

The Linux userspace that Zishu Terminal downloads on first run (the
"bootstrap") and the packages installed with `pkg` are **built from the
[termux-packages](https://github.com/termux/termux-packages) project's build
scripts and patches**, rebuilt by this project's own GitHub Actions workflow
with the app's own install prefix
`/data/data/com.zishu.terminal/files/usr`.

- Credit: the [Termux](https://termux.dev) project and its contributors.
- Zishu Terminal is an independent project. It is **not affiliated with,
  endorsed by or sponsored by Termux**. "Termux" is the name of a separate
  project and is used here only to describe where the build scripts come from.
- Zishu Terminal does not contain or use code from the Termux Android app.
  It only uses packages built from `termux-packages`.

## Licenses of the included software

Each package keeps its own license, listed in `THIRD_PARTY_LICENSES.md`.
Every installed package also ships its license files on the device under
`$PREFIX/share/doc/<package>/` and `$PREFIX/share/licenses/<package>/`
(where the upstream package provides them).

## Source code availability (GPL and similar)

Many packages are under the GNU GPL/LGPL, which require that the source be
available to anyone who receives the binaries. You can get it here:

- Build scripts, patches and upstream source locations for every package:
  https://github.com/termux/termux-packages (see `packages/<name>/build.sh`;
  the exact upstream source URL and version are in the `TERMUX_PKG_SRCURL`
  and `TERMUX_PKG_VERSION` fields).
- The exact `termux-packages` revision and patches used for a given release:
  see this repository's `.github/workflows/build-bootstrap.yml` (build
  configuration and patches), and the build logs attached to each GitHub
  Actions run.
- On request, the author will provide the corresponding source for any
  GPL/LGPL package shipped in a Zishu Terminal release. Open an issue at
  https://github.com/zishutron/zishu-terminal/issues.

## Trademarks

All product names and trademarks (Android, Google, Python, Git, Node.js, Linux,
Termux, GitHub, OpenSSH and others) belong to their owners and are used only
to identify the software.
