echo "Windows bundling"
VERSION=0.0.1
NAME=CaDoodle
MAIN=com.commonwealthrobotics.Main

if [[ -z "${VERSION_SEMVER}" ]]; then
  VERSION=4.0.4
else
  VERSION="${VERSION_SEMVER}"
fi


#https://cdn.azul.com/zulu/bin/zulu17.50.19-ca-fx-jdk17.0.11-win_x64.zip
#   zulu17.50.19-ca-fx-jdk17.0.11-win_x64
JVM=zulu17.50.19-ca-fx-jdk17.0.11-win_x64
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

set -e
ZIP=$JVM.zip
export JAVA_HOME="$HOME/bin/java17/"
if test -d "$JAVA_HOME/$JVM/"; then
  echo "$JAVA_HOME exists."
else
	mkdir -p "$JAVA_HOME"
	curl https://cdn.azul.com/zulu/bin/$ZIP -o $ZIP
	#unzip $ZIP -d $JAVA_HOME
	7z x $ZIP -o"$JAVA_HOME"
	mv "$JAVA_HOME/$JVM/"* "$JAVA_HOME/"
fi
echo "Compile wiht gradle"
./gradlew shadowJar
echo "Test jar in: $SCRIPT_DIR"
DIR="$SCRIPT_DIR/CaDoodleUpdater/build/libs/"
INPUT_DIR="$SCRIPT_DIR/input"
JAR_NAME=CaDoodleUpdater.jar
#$JAVA_HOME/bin/java.exe -jar $DIR/$JAR_NAME
echo "Test jar complete"

ICON=$NAME.ico
magick convert SourceIcon.png -resize 256x256 your_image_256.png
magick convert SourceIcon.png -resize 128x128 your_image_128.png
magick convert SourceIcon.png -resize 64x64 your_image_64.png
magick convert SourceIcon.png -resize 48x48 your_image_48.png
magick convert SourceIcon.png -resize 32x32 your_image_32.png
magick convert SourceIcon.png -resize 16x16 your_image_16.png

magick convert your_image_16.png your_image_32.png your_image_48.png your_image_64.png your_image_128.png your_image_256.png $NAME.ico 
    
PACKAGE="$JAVA_HOME/bin/jpackage.exe"
mkdir -p "$INPUT_DIR"
cp "$DIR/$JAR_NAME" "$INPUT_DIR/"

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
  --java-options '--enable-preview'
  
echo "Zipping standalone version"
rm -rf *.zip
7z a $NAME-$VERSION.zip "$NAME/"
echo "Building system wide installer" 

"$PACKAGE" --input "$INPUT_DIR/" \
  --name "$NAME" \
  --main-jar "$JAR_NAME" \
  --main-class "$MAIN" \
  --type "exe" \
  --temp "temp2" \
  --app-version "$VERSION" \
  --icon "$ICON" \
  --win-shortcut \
  --win-menu \
  --win-dir-chooser \
  --win-per-user-install \
  --java-options '--enable-preview'
ls -al
rm -rf release
mkdir release
export ARCH=x86_64
cp $NAME-$VERSION.exe release/$NAME-Windows-$ARCH.exe
cp $NAME-$VERSION.zip release/$NAME-Windows-$ARCH.zip
