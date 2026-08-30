#!/usr/bin/env bash
#https://cdn.azul.com/zulu/bin/zulu17.50.19-ca-fx-jdk17.0.11-macosx_x64.tar.gz
set -e

NAME=CaDoodle
VERSION=1.0.1
MAIN=com.commonwealthrobotics.Main

if [[ -z "${VERSION_SEMVER}" ]]; then
  VERSION=4.0.4
else
  VERSION="${VERSION_SEMVER}"
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
ARCH=x86_64
JVM=zulu25.32.21-ca-fx-jdk25.0.2-macosx_x64
if [[ $(uname -m) == 'arm64' ]]; then
  ARCH=arm64
  echo "Apple Silicon Mac detected https://cdn.azul.com/zulu/bin/zulu25.32.21-ca-fx-jdk25.0.2-macosx_aarch64.tar.gz"
  JVM=zulu25.32.21-ca-fx-jdk25.0.2-macosx_aarch64
else
  echo "Intel Mac detected https://cdn.azul.com/zulu/bin/zulu25.32.21-ca-fx-jdk25.0.2-macosx_aarch64_x64.tar.gz"
fi

ZIP=$JVM.tar.gz
export JAVA_HOME=$HOME/bin/java17/
if test -d $JAVA_HOME/$JVM/; then
  echo "$JAVA_HOME exists."
else
    rm -rf $JAVA_HOME
    mkdir -p $JAVA_HOME
    curl https://cdn.azul.com/zulu/bin/$ZIP -o $ZIP
    tar -xvzf $ZIP -C $JAVA_HOME
    mv $JAVA_HOME/$JVM/* $JAVA_HOME/
fi

./gradlew shadowJar
echo "Test jar in: $SCRIPT_DIR"
DIR=$SCRIPT_DIR/CaDoodleUpdater/build/libs/
INPUT_DIR="$SCRIPT_DIR/input"
JAR_NAME=CaDoodleUpdater.jar
echo "Test jar complete"

cp zulu*jdk*-macosx_*.zip $DIR/
cp CaDoodle-ApplicationInstall.zip $DIR/
# NOTE: BowlerStudioInstall (the vendored Eclipse/Groovy/jline install that was
# causing notarization failures) is only unpacked on the arm64 leg today, same
# as in the original script. If the x86_64 build is meant to bundle it too,
# that's a separate pre-existing gap from this signing fix.
if [[ $(uname -m) == 'arm64' ]]; then
    mkdir -p $DIR/BowlerStudioInstall/
    unzip -q -o BowlerStudioInstall-macos-arm.zip -d $DIR/BowlerStudioInstall/
fi
echo -e "\n\nTarget Dir: $DIR"
ls -al $DIR/

ICON=$NAME.png
cp SourceIcon.png $ICON
rm -rf $SCRIPT_DIR/$NAME
rm -rf $SCRIPT_DIR/$NAME.AppDir
BUILDDIR=CaDoodleUpdater/build/libs/
TARGETJAR=CaDoodleUpdater.jar
rm -rf *.dmg
echo "Building icon..."
MACIMAGE=SourceIcon.png
mkdir $NAME.iconset
sips -z 16 16     $MACIMAGE --out $NAME.iconset/icon_16x16.png
sips -z 32 32     $MACIMAGE --out $NAME.iconset/icon_16x16@2x.png
sips -z 32 32     $MACIMAGE --out $NAME.iconset/icon_32x32.png
sips -z 64 64     $MACIMAGE --out $NAME.iconset/icon_32x32@2x.png
sips -z 128 128   $MACIMAGE --out $NAME.iconset/icon_128x128.png
sips -z 256 256   $MACIMAGE --out $NAME.iconset/icon_128x128@2x.png
sips -z 256 256   $MACIMAGE --out $NAME.iconset/icon_256x256.png
sips -z 512 512   $MACIMAGE --out $NAME.iconset/icon_256x256@2x.png
sips -z 512 512   $MACIMAGE --out $NAME.iconset/icon_512x512.png
cp $MACIMAGE $NAME.iconset/icon_512x512@2x.png
iconutil -c icns $NAME.iconset
rm -R $NAME.iconset

# ---------------------------------------------------------------------------
# Code signing + notarization (Developer ID, for direct download).
#
# Everything is driven by environment variables so this one script runs
# unchanged on a dev machine and in CI. With NO credentials set, the build
# proceeds UNSIGNED (a local dev build) and notarization is skipped -- the old
# behavior. macOS will still warn on such a build when downloaded; only a
# notarized + stapled DMG clears Gatekeeper on macOS 26.
#
# Signing:
#   MACOS_SIGN_IDENTITY  Name portion of the "Developer ID Application" identity,
#                        e.g. "Common Wealth Robotics (TEAMID)".
#   MACOS_KEYCHAIN       Optional path to the keychain holding the cert (CI uses
#                        a temporary keychain; omit to use the login keychain).
#
# Notarization (only attempted when signing happened). Provide ONE set:
#   A) App Store Connect API key (recommended, esp. for CI):
#        NOTARY_KEY_PATH   path to the AuthKey_XXXX.p8 file
#        NOTARY_KEY_ID     the 10-char key ID
#        NOTARY_ISSUER_ID  the issuer UUID
#   B) Apple ID + app-specific password:
#        NOTARY_APPLE_ID   your Apple ID email
#        NOTARY_TEAM_ID    your 10-char Team ID
#        NOTARY_PASSWORD   an app-specific password (appleid.apple.com)
#
# IMPORTANT CHANGE FROM THE OLD SCRIPT:
# jpackage's own --mac-sign only deep-signs files that already exist as loose
# Mach-O binaries on disk. It never looks *inside* jars. The BowlerStudioInstall
# payload ships jline/jansi .jnilib native libraries zipped inside jar files
# (org.codehaus.groovy_*/lib/shell/jline-*.jar), plus an "eclipse" launcher
# binary with its own (invalid, from our signing chain's point of view)
# signature. Apple's notary service DOES unzip jars looking for Mach-O
# binaries, so those never got signed and the whole DMG came back "Invalid".
#
# Fix: we no longer let jpackage sign anything itself. Instead we:
#   1. Build an unsigned --type app-image (a real .app directory on disk).
#   2. Extract every jar containing a .dylib/.jnilib, codesign the native
#      libs individually (--timestamp --options runtime), repack the jar.
#   3. Sign any remaining loose native libs / nested .app bundles (e.g.
#      Eclipse.app) directly.
#   4. Deep-sign the whole CaDoodle.app with our Developer ID + entitlements.
#   5. Package that already-signed app-image into the DMG (jpackage
#      --type dmg --app-image), so nothing gets re-zipped after signing.
# ---------------------------------------------------------------------------
DO_SIGN=false
SIGN_IDENTITY_FULL=""
KEYCHAIN_ARGS=()
if [[ -n "${MACOS_SIGN_IDENTITY}" ]]; then
  DO_SIGN=true
  SIGN_IDENTITY_FULL="Developer ID Application: ${MACOS_SIGN_IDENTITY}"
  echo "Signing as: ${SIGN_IDENTITY_FULL}"
  [[ -n "${MACOS_KEYCHAIN}" ]] && KEYCHAIN_ARGS=( --keychain "${MACOS_KEYCHAIN}" )
else
  echo "MACOS_SIGN_IDENTITY not set -> building UNSIGNED (notarization skipped)."
fi

# Sign every Mach-O native library hiding inside a jar, then repackage the
# jar. Must run BEFORE the jar is ever placed inside the .app that jpackage
# assembles -- codesign can't reach into a zip/jar entry.
sign_natives_in_jars() {
  local root="$1"
  find "$root" -type f -name "*.jar" | while read -r jarfile; do
    if unzip -l "$jarfile" 2>/dev/null | grep -qE '\.(dylib|jnilib)$'; then
      echo "  Found native binaries inside: $jarfile"
      local work
      work=$(mktemp -d)
      ( cd "$work" && "$JAVA_HOME/bin/jar" xf "$jarfile" )
      find "$work" -type f \( -name "*.dylib" -o -name "*.jnilib" \) | while read -r nativefile; do
        echo "    Signing $(basename "$nativefile")"
        codesign --force --timestamp --options runtime "${KEYCHAIN_ARGS[@]}" \
          --sign "${SIGN_IDENTITY_FULL}" "$nativefile"
      done
      ( cd "$work" && "$JAVA_HOME/bin/jar" cf "$jarfile.new" . )
      mv "$jarfile.new" "$jarfile"
      rm -rf "$work"
    fi
  done
}

# Sign loose native libs and any nested .app bundles (e.g. Eclipse.app) that
# ship with no signature, or one Apple won't accept from us.
sign_loose_binaries() {
  local root="$1"
  find "$root" -type f \( -name "*.dylib" -o -name "*.jnilib" -o -name "*.so" \) -print0 |
    xargs -0 -I{} codesign --force --timestamp --options runtime "${KEYCHAIN_ARGS[@]}" --sign "${SIGN_IDENTITY_FULL}" {}
  # Extensionless executables (the "eclipse" launcher, embedded JVM binaries, etc.)
  find "$root" -type f -perm -u+x ! -name "*.*" | while read -r f; do
    if file "$f" | grep -q "Mach-O"; then
      codesign --force --timestamp --options runtime "${KEYCHAIN_ARGS[@]}" --sign "${SIGN_IDENTITY_FULL}" "$f"
    fi
  done
  # Re-sign nested .app bundles last so their outer signature covers what we
  # just touched inside them. NOTE: a directory ending in ".app" is not
  # necessarily a real bundle -- Eclipse's OSGi bundle cache uses plugin-ID
  # names like "configuration/org.eclipse.equinox.app" that happen to match
  # the glob but aren't signable bundles at all. Only treat it as a bundle
  # if it actually has Contents/Info.plist.
  find "$root" -type d -name "*.app" | while read -r bundle; do
    if [[ -f "$bundle/Contents/Info.plist" ]]; then
      codesign --force --deep --timestamp --options runtime "${KEYCHAIN_ARGS[@]}" --sign "${SIGN_IDENTITY_FULL}" "$bundle"
    fi
  done
}

if [[ "$DO_SIGN" == "true" ]] && [[ -d "$DIR/BowlerStudioInstall" ]]; then
  echo "Pre-signing bundled Eclipse/Groovy native libraries before packaging..."
  sign_natives_in_jars "$DIR/BowlerStudioInstall"
  sign_loose_binaries "$DIR/BowlerStudioInstall"
fi

# Build the .app as a plain app-image (unsigned by jpackage itself) so we
# have a real directory to fix up before anything gets zipped into a DMG.
APPIMAGE_DIR="$SCRIPT_DIR/appimage"
rm -rf "$APPIMAGE_DIR"
mkdir -p "$APPIMAGE_DIR"

$JAVA_HOME/bin/jpackage --input $BUILDDIR \
  --name $NAME \
  --main-jar $TARGETJAR \
  --main-class $MAIN \
  --type app-image \
  --dest "$APPIMAGE_DIR" \
  --copyright "Creative Commons" \
  --vendor "Common Wealth Robotics" \
  --icon $NAME.icns \
  --app-version "$VERSION" \
  --java-options '--enable-preview -Dcom.sun.net.ssl.checkRevocation=false -Djava.security.revocation=false'

APP_PATH="$APPIMAGE_DIR/$NAME.app"

if [[ "$DO_SIGN" == "true" ]]; then
  echo "Deep-signing $APP_PATH ..."
  # Catch anything the pre-pass missed (files that only exist after jpackage
  # assembled the bundle), innermost first.
  find "$APP_PATH" -type f \( -name "*.dylib" -o -name "*.jnilib" -o -name "*.so" \) -print0 |
    xargs -0 -I{} codesign --force --timestamp --options runtime "${KEYCHAIN_ARGS[@]}" --sign "${SIGN_IDENTITY_FULL}" {}
  find "$APP_PATH" -type d -name "*.app" ! -path "$APP_PATH" | while read -r bundle; do
    if [[ -f "$bundle/Contents/Info.plist" ]]; then
      codesign --force --deep --timestamp --options runtime "${KEYCHAIN_ARGS[@]}" --sign "${SIGN_IDENTITY_FULL}" "$bundle"
    fi
  done
  codesign --force --deep --timestamp --options runtime "${KEYCHAIN_ARGS[@]}" \
    --entitlements "$SCRIPT_DIR/mac-entitlements.plist" \
    --sign "${SIGN_IDENTITY_FULL}" "$APP_PATH"

  echo "Verifying signature..."
  codesign --verify --deep --strict --verbose=2 "$APP_PATH"
  spctl --assess --type execute --verbose "$APP_PATH" || true
fi

ls -al
rm -rf release
mkdir release
DMG="release/$NAME-MacOS-$ARCH.dmg"

# Package the (already-signed, if DO_SIGN) app-image straight into a DMG.
# Building the DMG from an existing --app-image -- instead of asking
# jpackage to build + sign + package in one shot -- means jpackage never
# gets a chance to touch our nested jars/binaries again after we fixed them.
$JAVA_HOME/bin/jpackage --type dmg \
  --app-image "$APP_PATH" \
  --name $NAME \
  --dest "$SCRIPT_DIR" \
  --app-version "$VERSION"

mv "$NAME-$VERSION.dmg" "$DMG"

# ---- Notarize + staple (only when the build was signed) ----
if [[ "$DO_SIGN" == "true" ]]; then
  NOTARY_ARGS=()
  if [[ -n "${NOTARY_KEY_PATH}" && -n "${NOTARY_KEY_ID}" && -n "${NOTARY_ISSUER_ID}" ]]; then
    echo "Notarizing with App Store Connect API key..."
    NOTARY_ARGS=( --key "${NOTARY_KEY_PATH}" --key-id "${NOTARY_KEY_ID}" --issuer "${NOTARY_ISSUER_ID}" )
  elif [[ -n "${NOTARY_APPLE_ID}" && -n "${NOTARY_TEAM_ID}" && -n "${NOTARY_PASSWORD}" ]]; then
    echo "Notarizing with Apple ID + app-specific password..."
    NOTARY_ARGS=( --apple-id "${NOTARY_APPLE_ID}" --team-id "${NOTARY_TEAM_ID}" --password "${NOTARY_PASSWORD}" )
  fi

  if [[ ${#NOTARY_ARGS[@]} -gt 0 ]]; then
    NOTARY_JSON=$(xcrun notarytool submit "$DMG" "${NOTARY_ARGS[@]}" --wait --output-format json)
    echo "$NOTARY_JSON"

    SUBMISSION_ID=$(echo "$NOTARY_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
    NOTARY_STATUS=$(echo "$NOTARY_JSON" | python3 -c "import json,sys; print(json.load(sys.stdin)['status'])")

    if [[ "$NOTARY_STATUS" != "Accepted" ]]; then
      echo "Notarization finished with status: $NOTARY_STATUS"
      echo "Fetching notarization log for submission $SUBMISSION_ID ..."
      xcrun notarytool log "$SUBMISSION_ID" "${NOTARY_ARGS[@]}" || true
      echo "See the issues above for why Apple rejected the DMG (unsigned nested binaries, missing hardened runtime, invalid entitlements, etc.)."
      exit 1
    fi

    echo "Stapling notarization ticket to $DMG ..."
    xcrun stapler staple "$DMG"
    xcrun stapler validate "$DMG"
    spctl -a -t open --context context:primary-signature -v "$DMG" || true
  else
    echo "Signed, but no notarization credentials set -> skipping notarization."
    echo "WARNING: the DMG is signed but NOT notarized; macOS 26 will still warn on download."
  fi
fi