# macOS Code Signing & Notarization

CaDoodle ships two macOS artifacts with different signing requirements:

| Artifact | Distribution | Signing | Status |
|---|---|---|---|
| `CaDoodle-MacOS-{arm64,x86_64}.dmg` | Direct download (GitHub Releases) | **Developer ID Application** + **notarization** + staple | Wired up (this doc) |
| Mac App Store build | App Store | Apple Distribution + sandbox + provisioning profile | **Not yet implemented** — see [Mac App Store](#mac-app-store-pending) |

This doc covers the Developer ID / notarization path used by [`package-macos.sh`](package-macos.sh). That is what stops macOS (including macOS 26) from blocking the downloaded DMG with *"Apple could not verify this app is free of malware."*

## How signing is wired

`package-macos.sh` is driven entirely by environment variables, so the same script runs on a dev machine and in CI:

- **No variables set** → the DMG is built **unsigned** (local dev builds keep working). macOS warns on such a build when downloaded.
- `MACOS_SIGN_IDENTITY` set → `jpackage` signs the app with the hardened runtime + [`mac-entitlements.plist`](mac-entitlements.plist).
- Notarization credentials also set → the DMG is submitted to Apple's notary service, stapled, and validated.

### Environment variables

**Signing**

| Var | Required | Meaning |
|---|---|---|
| `MACOS_SIGN_IDENTITY` | for signing | Name portion of the identity, e.g. `Common Wealth Robotics (TEAMID)`. `jpackage` resolves `Developer ID Application: <this>` in the keychain. |
| `MACOS_KEYCHAIN` | optional | Path to the keychain holding the cert. Omit to use the login keychain (CI sets a temporary keychain). |

**Notarization** — provide **one** of these sets (API key preferred):

| Vars | Set | Meaning |
|---|---|---|
| `NOTARY_KEY_PATH`, `NOTARY_KEY_ID`, `NOTARY_ISSUER_ID` | A | App Store Connect API key (`.p8`), its key ID, and the issuer UUID |
| `NOTARY_APPLE_ID`, `NOTARY_TEAM_ID`, `NOTARY_PASSWORD` | B | Apple ID, 10-char Team ID, app-specific password |

## Prerequisites

- An **Apple Developer Program** membership. Your 10-char **Team ID** is on the Membership page.
- Xcode (provides `notarytool`, `stapler`, and the easy cert-creation flow).

## 1. Create the Developer ID Application certificate

**Xcode (easiest):** Xcode → Settings → Accounts → sign in → select the team → *Manage Certificates…* → **+** → **Developer ID Application**. The certificate and its private key land in your login keychain.

> Developer ID certificates can only be created by the team's **Account Holder**. If the **+** menu doesn't offer it, use the portal flow below from the Account Holder's account.

**Portal:** create a CSR (Keychain Access → Certificate Assistant → *Request a Certificate From a CA…*, "Saved to disk"), upload it at developer.apple.com → Certificates → **+** → *Developer ID Application*, download the `.cer`, and double-click to install.

Confirm:
```bash
security find-identity -v -p codesigning
#   1) ABC…  "Developer ID Application: Common Wealth Robotics (TEAMID)"
```

## 2. Create the notarization credential

App Store Connect → Users and Access → Integrations → **Keys** → generate a key with **Developer** access. Download `AuthKey_XXXX.p8` (one-time download). Note the **Key ID** (beside the key) and the **Issuer ID** (top of the page).

## 3. Build & verify locally

```bash
export MACOS_SIGN_IDENTITY="Common Wealth Robotics (TEAMID)"
export NOTARY_KEY_PATH="$HOME/keys/AuthKey_XXXX.p8"
export NOTARY_KEY_ID="XXXXXXXXXX"
export NOTARY_ISSUER_ID="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"

bash package-macos.sh
```

Verify the result:
```bash
xcrun stapler validate release/CaDoodle-MacOS-arm64.dmg
spctl -a -t open --context context:primary-signature -v release/CaDoodle-MacOS-arm64.dmg
#   → accepted   source=Notarized Developer ID
```

## 4. CI (GitHub Actions)

**You do not edit the workflow.** [`.github/workflows/release.yml`](.github/workflows/release.yml) already imports the certificate into a throwaway keychain, writes the notary key to a temp file, and runs `package-macos.sh` in both the `macos-arm` and `macos` jobs. The only thing to add in GitHub is the secrets below.

Add each at **Settings → Secrets and variables → Actions → New repository secret**
(`https://github.com/CommonWealthRobotics/CaDoodle/settings/secrets/actions`):

| Secret | Value / how to produce it |
|---|---|
| `MACOS_SIGN_IDENTITY` | The identity's name portion, e.g. `Your Name (TEAMID)` — copy it verbatim from `security find-identity -v -p codesigning` |
| `MACOS_CERT_PASSWORD` | The password you choose when exporting the `.p12` (below) |
| `MACOS_CERT_P12` | base64 of the exported identity (below) |
| `NOTARY_KEY_ID` | The key's 10-char Key ID |
| `NOTARY_ISSUER_ID` | The Issuer UUID from the Keys page |
| `NOTARY_KEY_P8` | base64 of your `AuthKey_XXXX.p8` (below) |

**Export the signing identity → `.p12` → base64.** In Keychain Access, expand the **Developer ID Application** identity, select the certificate **and** its private key, right-click → **Export 2 items…** → save as `.p12` and set a password (that password becomes `MACOS_CERT_PASSWORD`). Then:

```bash
base64 -i devid.p12 | pbcopy            # paste into MACOS_CERT_P12
```

**base64 the notary key:**

```bash
base64 -i ~/keys/AuthKey_XXXX.p8 | pbcopy   # paste into NOTARY_KEY_P8
```

Secret **values live only in GitHub Secrets — never commit them** to this (public) repo.

The workflow runs on every push, so once these six secrets exist, the next push to any branch builds signed + notarized DMGs as artifacts (publishing is still gated on a tag). If the signing secrets are absent the jobs log a notice and build an **unsigned** DMG instead of failing — so forks/PRs without secrets still work; if you push before adding them, add the secrets and **Re-run all jobs** on that run.

> Alternative to the `NOTARY_KEY_*` trio: set `NOTARY_APPLE_ID` / `NOTARY_TEAM_ID` / `NOTARY_PASSWORD` secrets instead; the script accepts either form.

## Entitlements

[`mac-entitlements.plist`](mac-entitlements.plist) grants the hardened runtime what a JVM needs:

- `com.apple.security.cs.allow-jit` and `…allow-unsigned-executable-memory` — the JIT writes and executes generated code.
- `com.apple.security.cs.disable-library-validation` — the bundled JavaFX/runtime native libraries (and the separately launched application JVM) are not all signed by this Team.
- `com.apple.security.cs.allow-dyld-environment-variables` — the launcher passes `JAVA_HOME` and other env through to the child process.

These suit a notarized Developer ID build; the App Store build needs a different, sandboxed set.

## Troubleshooting

- **codesign prompts for keychain access (local):** click *Always Allow* once, or run
  `security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k <login-pw> ~/Library/Keychains/login.keychain-db`.
- **Notarization rejected:** pull the detailed log with
  `xcrun notarytool log <submission-id> --key … --key-id … --issuer …`. The usual cause is an unsigned nested binary (hardened runtime not applied) — confirm `--mac-sign` actually ran.
- **"The binary is not signed with a valid Developer ID certificate":** the name in `MACOS_SIGN_IDENTITY` doesn't match `security find-identity` output, or the cert isn't in the searched keychain (set `MACOS_KEYCHAIN`).

## Mac App Store (pending)

The App Store target is **not yet built**. It cannot reuse the DMG binary, because the App Store sandbox prohibits what the updater does at runtime: download a JVM + `CaDoodle-Application.jar`, execute them, and write under `~/bin`. A compliant build must:

1. Bundle the full JVM + `CaDoodle-Application.jar` at **build time** — no runtime download, no self-updater.
2. Enable `com.apple.security.app-sandbox` plus the file-access / network entitlements the app actually uses.
3. Sign with **Apple Distribution**, embed a provisioning profile, and wrap in a `.pkg` signed with the Mac installer identity.
4. Upload to App Store Connect via Transporter (notarization does not apply to App Store builds).

This is planned as a separate `package-macos-appstore.sh`.
