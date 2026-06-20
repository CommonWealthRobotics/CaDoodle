#https://cdn.azul.com/zulu/bin/zulu17.50.19-ca-fx-jdk17.0.11-macosx_x64.tar.gz
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
  echo "M1 Mac detected https://cdn.azul.com/zulu/bin/zulu25.32.21-ca-fx-jdk25.0.2-macosx_aarch64.tar.gz"
  JVM=zulu25.32.21-ca-fx-jdk25.0.2-macosx_aarch64
else
  echo "x86 Mac detected https://cdn.azul.com/zulu/bin/zulu25.32.21-ca-fx-jdk25.0.2-macosx_x64.tar.gz"

fi
set -e
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
#$JAVA_HOME/bin/java -jar $DIR/$JAR_NAME
echo "Test jar complete"

cp zulu*jre*-macosx_*.zip $DIR/
cp CaDoodle-ApplicationInstall.zip $DIR/

ICON=$NAME.png
cp SourceIcon.png $ICON
rm -rf $SCRIPT_DIR/$NAME
rm -rf $SCRIPT_DIR/$NAME.AppDir
BUILDDIR=CaDoodleUpdater/build/libs/
TARGETJAR=CaDoodleUpdater.jar
rm -rf *.dmg
echo "Building DMG..."
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
#                        e.g. "Common Wealth Robotics (TEAMID)". jpackage looks
#                        up "Developer ID Application: <this>" in the keychain.
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
# ---------------------------------------------------------------------------
SIGN_ARGS=()
DO_SIGN=false
if [[ -n "${MACOS_SIGN_IDENTITY}" ]]; then
  DO_SIGN=true
  echo "Signing as: Developer ID Application: ${MACOS_SIGN_IDENTITY}"
  SIGN_ARGS+=( --mac-sign
               --mac-signing-key-user-name "${MACOS_SIGN_IDENTITY}"
               --mac-package-identifier "com.commonwealthrobotics.cadoodle"
               --mac-entitlements "$SCRIPT_DIR/mac-entitlements.plist" )
  [[ -n "${MACOS_KEYCHAIN}" ]] && SIGN_ARGS+=( --mac-signing-keychain "${MACOS_KEYCHAIN}" )
else
  echo "MACOS_SIGN_IDENTITY not set -> building UNSIGNED (notarization skipped)."
fi

$JAVA_HOME/bin/jpackage --input $BUILDDIR \
  --name $NAME \
  --main-jar $TARGETJAR \
  --main-class $MAIN \
  --type dmg \
  --copyright "Creative Commons" \
  --vendor "Common Wealth Robotics" \
  --icon $NAME.icns \
  --app-version "$VERSION" \
  "${SIGN_ARGS[@]}" \
  --java-options '--enable-preview -Dcom.sun.net.ssl.checkRevocation=false -Djava.security.revocation=false'
ls -al
rm -rf release
mkdir release
DMG="release/$NAME-MacOS-$ARCH.dmg"
mv $NAME-$VERSION.dmg "$DMG"

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
    xcrun notarytool submit "$DMG" "${NOTARY_ARGS[@]}" --wait
    echo "Stapling notarization ticket to $DMG ..."
    xcrun stapler staple "$DMG"
    xcrun stapler validate "$DMG"
    spctl -a -t open --context context:primary-signature -v "$DMG" || true
  else
    echo "Signed, but no notarization credentials set -> skipping notarization."
    echo "WARNING: the DMG is signed but NOT notarized; macOS 26 will still warn on download."
  fi
fi
