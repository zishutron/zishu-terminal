# Legal and brand checklist (for the project owner)

This is a practical checklist, not legal advice. For anything commercial
(paid app, company, big public launch) ask a qualified lawyer once.

## Always do
- Keep `LICENSE`, `NOTICE.md`, `THIRD_PARTY_LICENSES.md` and `PRIVACY.md` in the repo and update them when you add packages.
- Show a credit line in the app (About screen / `zabout`): "Packages built from the Termux project (termux-packages). Not affiliated with Termux. Open-source licenses: see NOTICE."
- Keep the GitHub Actions logs and `build-bootstrap.yml`; they are your record of how the binaries were built.
- Answer source requests for GPL/LGPL packages (point to termux-packages and the version used).
- Replace `zishutron` in `LICENSE` with your legal name or brand name if you want.

## Never do
- Don't call the app "Termux", don't use Termux's logo or name in your app name, icon, store listing or domain.
- Don't say or imply Termux or any other project endorses Zishu Terminal.
- Don't remove license files from installed packages.
- Don't copy code from the Termux Android app (GPLv3) into your app without following GPLv3 for that code.
- Don't use other companies' logos or brand names in your icon or marketing.
- Don't claim to have written Python, Git, bash, Node.js etc.; say "includes" or "installs".

## Before publishing on a store (Google Play etc.)
- The app uses targetSdk 28 to run binaries from app storage. Google Play does not accept this for new apps, so distribute the APK from GitHub Releases or another site instead.
- Add a privacy policy link (use `PRIVACY.md`).
- Sign release APKs with your own long-term key and keep it safe; never commit keystores.

## If someone sends a complaint
Stay calm, don't ignore it, and fix quickly (add credit, remove a logo, etc.). Most open-source issues are solved by adding the missing license text or credit.
