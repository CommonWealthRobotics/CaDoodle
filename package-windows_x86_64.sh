echo "Windows bundling"
VERSION=0.0.1
NAME=CaDoodle
MAIN=com.commonwealthrobotics.Main

if [[ -z "${VERSION_SEMVER}" ]]; then
  VERSION=4.0.4
else
  VERSION="${VERSION_SEMVER}"
fi


SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

set -e
# JVM from jvm.json: download+install to the shared root and resolve JAVA_HOME
JVM_SCRIPTS=$SCRIPT_DIR/jvm
JVM_ARCHIVE=$(bash $JVM_SCRIPTS/getJdkArchiveFile.sh)
# One download serves both the local install and the shipped payload
if [ ! -f "$JVM_ARCHIVE" ]; then
  JVM_URL=$(bash $JVM_SCRIPTS/getJdkUrl.sh)
  echo "Downloading $JVM_URL"
  curl -fL --retry 3 -o "$JVM_ARCHIVE" "$JVM_URL"
fi
JDK_ARCHIVE_PATH=$PWD/$JVM_ARCHIVE bash $JVM_SCRIPTS/installJdk.sh
export JAVA_HOME=$(bash $JVM_SCRIPTS/getJdkHome.sh)
echo "Compile with gradle"
./gradlew shadowJar
echo "Test jar in: $SCRIPT_DIR"
DIR="$SCRIPT_DIR/CaDoodleUpdater/build/libs/"
INPUT_DIR="$SCRIPT_DIR/input"
JAR_NAME=CaDoodleUpdater.jar
#$JAVA_HOME/bin/java.exe -jar $DIR/$JAR_NAME
echo "Test jar complete"

ICON=$NAME.ico
magick SourceIcon.png -resize 256x256 your_image_256.png
magick SourceIcon.png -resize 128x128 your_image_128.png
magick SourceIcon.png -resize 64x64 your_image_64.png
magick SourceIcon.png -resize 48x48 your_image_48.png
magick SourceIcon.png -resize 32x32 your_image_32.png
magick SourceIcon.png -resize 16x16 your_image_16.png

magick your_image_16.png your_image_32.png your_image_48.png your_image_64.png your_image_128.png your_image_256.png $NAME.ico 
    
PACKAGE="$JAVA_HOME/bin/jpackage.exe"
mkdir -p "$INPUT_DIR"
cp "$DIR/$JAR_NAME" "$INPUT_DIR/"
cp "$JVM_ARCHIVE" "$INPUT_DIR/"
cp CaDoodle-ApplicationInstall.zip "$INPUT_DIR/"

export ARCH=x86_64
ls -al
rm -rf release
mkdir release
#$PACKAGE --input "$INPUT_DIR/" --name "$NAME" --main-jar "$JAR_NAME" --app-version "$VERSION" --icon "$ICON" --type "exe" --resource-dir "temp2" --verbose
#exit 1
rm -rf temp*
rm -rf $NAME
# depends on WiX https://github.com/wixtoolset/wix3/releases
"$PACKAGE" --input "$INPUT_DIR/" \
  --name "$NAME" \
  --main-jar "$JAR_NAME" \
  --main-class "$MAIN" \
  --type "app-image" \
  --temp "temp1"  \
  --app-version "$VERSION" \
  --icon "$ICON" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.crypto.mscapi  \
  --java-options '--enable-native-access=javafx.graphics '
  
echo "Zipping standalone version"
rm -rf *.zip
7z a $NAME-$VERSION.zip "$NAME/"

echo "Building MSIX"
mkdir -p MsixStage/Assets
mkdir -p Assets
magick SourceIcon.png -resize 50x50 Assets/StoreLogo.png
magick SourceIcon.png -resize 44x44 Assets/Square44x44Logo.png
magick SourceIcon.png -resize 150x150 Assets/Square150x150Logo.png
magick SourceIcon.png -resize 310x150 -gravity center -background transparent -extent 310x150 Assets/Wide310x150Logo.png
# Copy app-image contents
cp -r "$NAME/"* MsixStage/

# Copy manifest and assets
cp AppxManifest.xml MsixStage/
cp Assets/StoreLogo.png MsixStage/Assets/
cp Assets/Square44x44Logo.png MsixStage/Assets/
cp Assets/Square150x150Logo.png MsixStage/Assets/
cp Assets/Wide310x150Logo.png MsixStage/Assets/

# Patch version into manifest
VERSION_QUAD="$VERSION.0"
#sed -i "s/Version=\"1.0.0.0\"/Version=\"$VERSION_QUAD\"/" MsixStage/AppxManifest.xml

# Build the MSIX
#MAKEAPPX=$(find "C:/Program Files (x86)/Windows Kits/10/bin" -name "makeappx.exe" -path "*/x64/*" | sort -V | tail -1)
#echo "Found makeappx at: $MAKEAPPX"
#"$MAKEAPPX" pack /d MsixStage /p "$NAME-$VERSION.msix" /nv
MAKEAPPX=$(find "C:/Program Files (x86)/Windows Kits/10/bin" -name "makeappx.exe" -path "*/x64/*" | sort -V | tail -1)
echo "Found makeappx at: $MAKEAPPX"
powershell.exe -Command "& '$MAKEAPPX' pack /d MsixStage /p $NAME-$VERSION.msix /nv /o"

cp "$NAME-$VERSION.msix" "$NAME-Windows-$ARCH.msix"


echo "Building Local installer" 

"$PACKAGE" --input "$INPUT_DIR/" \
  --name "$NAME" \
  --main-jar "$JAR_NAME" \
  --main-class "$MAIN" \
  --type "exe" \
  --temp "temp2" \
  --app-version "$VERSION" \
  --icon "$ICON" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.crypto.mscapi  \
  --win-shortcut \
  --win-menu \
  --win-dir-chooser \
  --win-per-user-install \
  --java-options '--enable-native-access=javafx.graphics '
  
echo "Building system wide installer" 
touch "$INPUT_DIR/pinVersionSystem"
"$PACKAGE" --input "$INPUT_DIR/" \
  --name "$NAME"-System \
  --main-jar "$JAR_NAME" \
  --main-class "$MAIN" \
  --type "exe" \
  --temp "temp3" \
  --app-version "$VERSION" \
  --icon "$ICON" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics,javafx.base,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.crypto.mscapi  \
  --win-shortcut \
  --win-menu \
  --win-dir-chooser \
  --java-options '--enable-native-access=javafx.graphics -Djavax.net.ssl.trustStoreType=WINDOWS-ROOT'
cp $NAME-$VERSION.exe release/$NAME-Windows-$ARCH.exe
cp $NAME-System-$VERSION.exe release/$NAME-Windows-System-$ARCH.exe
cp $NAME-$VERSION.zip release/$NAME-Windows-$ARCH.zip