# macOS signing & notarization — work record (2026-06-20)

A description of the change that added Developer ID signing + notarization to the
macOS build, and the scoping of the Mac App Store target. For *how to set it up*
(certificates, secrets, local/CI usage), see [`MACOS-SIGNING.md`](../MACOS-SIGNING.md).

## Problem

`package-macos.sh` produced an **unsigned, un-notarized** DMG. On macOS 26,
Gatekeeper blocks such a download with *"Apple could not verify this app is free
of malware,"* with no easy user override. A second goal was to prepare the app
for the **Mac App Store**.

## Architecture context (why the two goals diverge)

This repo (`Cadoodle`) is an **installer / auto-updater**, not the application.
At runtime [`CadoodleUpdater.java`](../CaDoodleUpdater/src/main/java/com/commonwealthrobotics/CadoodleUpdater.java)
and [`JvmManager.java`](../CaDoodleUpdater/src/main/java/com/commonwealthrobotics/JvmManager.java)
download a JVM and `CaDoodle-Application.jar` from the `CaDoodle-Application`
releases, write them under `~/bin/CaDoodle-ApplicationInstall/`, and execute
them.

- **Direct download (Developer ID):** notarizing the updater DMG is sufficient.
  Files the updater downloads later are not browser-quarantined, so they run.
- **Mac App Store:** the sandbox **prohibits** downloading/executing code and
  writing outside the container, and bans self-updaters. The updater model is
  therefore incompatible; the App Store needs a *self-contained, sandboxed*
  build instead (see [Pending](#pending)).

## What was implemented (Developer ID path)

| File | Change |
|---|---|
| [`mac-entitlements.plist`](../mac-entitlements.plist) | New. Hardened-runtime entitlements for a JVM: `allow-jit`, `allow-unsigned-executable-memory`, `disable-library-validation`, `allow-dyld-environment-variables`. |
| [`package-macos.sh`](../package-macos.sh) | Adds `--mac-sign` + entitlements to `jpackage`, then `notarytool submit --wait` → `stapler staple` → `stapler validate`. |
| [`.github/workflows/release.yml`](../.github/workflows/release.yml) | Both mac jobs import the Developer ID cert into a temporary keychain, materialize the notary API key, and pass credentials to the build. |

**Credential-driven, no-op without creds.** Everything reads from environment
variables, so the script runs unchanged locally and in CI:

- nothing set → **unsigned** DMG (prior behavior; dev builds keep working);
- `MACOS_SIGN_IDENTITY` set → signed with hardened runtime;
- notary creds also set → notarized + stapled.

Variables: `MACOS_SIGN_IDENTITY`, optional `MACOS_KEYCHAIN`; notarization via an
App Store Connect API key (`NOTARY_KEY_PATH` / `NOTARY_KEY_ID` /
`NOTARY_ISSUER_ID`) or Apple ID (`NOTARY_APPLE_ID` / `NOTARY_TEAM_ID` /
`NOTARY_PASSWORD`).

## Decisions & rationale

- **Developer ID + notarization (not just signing).** Signing alone does not
  clear Gatekeeper on macOS 26; the DMG must be notarized and stapled.
- **Kept `jpackage --type dmg`** rather than refactoring to a manual
  app-image → codesign → dmg pipeline. The `.app` inside is signed with the
  hardened runtime and the DMG is stapled, which satisfies the download case.
  Revisit only if a copied-out `.app` needs its own offline-stapled ticket.
- **App Store as a separate self-contained build** (chosen over putting MAS
  packaging in the `CaDoodle-Application` repo). Bundles the full JVM +
  application jar at build time, enables the sandbox, drops the updater.

## Verification gates

- `bash -n package-macos.sh` — passes.
- `release.yml` — valid YAML; both `macos` and `macos-arm` jobs carry the new steps.
- End-to-end signing/notarization **not yet exercised** — no signing identity is
  installed on the build machine (`security find-identity -v -p codesigning`
  returns 0). Activation prerequisites are in
  [`MACOS-SIGNING.md`](../MACOS-SIGNING.md).

## Pending

- A self-contained, sandboxed Mac App Store build, planned as
  `package-macos-appstore.sh` (Apple Distribution signing, provisioning profile,
  `.pkg` via Transporter).
