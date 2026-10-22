# Third-party software

This list is a summary for convenience. The license that ships with each
package is the authoritative one. Packages are installed on the user's device
at runtime; they are separate programs and are not linked into the Zishu
Terminal app.

| Component | License (SPDX, as published upstream) | Upstream |
|---|---|---|
| termux-packages (build scripts) | Apache-2.0 for build infrastructure; package patches/scripts use the license of the package they build | https://github.com/termux/termux-packages |
| bash | GPL-3.0-or-later | https://www.gnu.org/software/bash/ |
| GNU coreutils, grep, sed, tar, gawk, findutils, diffutils, make, nano, binutils, wget | GPL-3.0-or-later | https://www.gnu.org/software/ |
| apt, dpkg | GPL-2.0-or-later | https://wiki.debian.org/Apt |
| Python | PSF-2.0 | https://www.python.org |
| pip | MIT | https://pip.pypa.io |
| Git | GPL-2.0-only | https://git-scm.com |
| curl, libcurl | curl license (MIT-style) | https://curl.se |
| OpenSSH | BSD / ISC / permissive mix | https://www.openssh.com |
| OpenSSL 3.x | Apache-2.0 | https://www.openssl.org |
| Node.js | MIT (plus bundled third-party notices) | https://nodejs.org |
| npm | Artistic-2.0 | https://www.npmjs.com |
| ICU | Unicode-3.0 | https://icu.unicode.org |
| c-ares | MIT | https://c-ares.org |
| CMake | BSD-3-Clause | https://cmake.org |
| less | GPL-3.0-or-later or BSD-2-Clause | https://www.greenwoodsoftware.com/less/ |
| termux-exec, termux-tools, termux-core, termux-keyring | see each package in termux-packages (permissive licenses) | https://github.com/termux |

Packages not listed above that you install later with `pkg` keep their own
licenses; see `pkg show <name>` and `$PREFIX/share/doc/<name>/`.

If a license entry here is wrong or out of date, please open an issue and it
will be corrected.
