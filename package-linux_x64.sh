#https://cdn.azul.com/zulu/bin/zulu17.50.19-ca-fx-jdk17.0.11-linux_x64.tar.gz
#   zulu17.50.19-ca-fx-jdk17.0.11-linux_x64
NAME=CaDoodle
VERSION=0.0.1
MAIN=com.commonwealthrobotics.Main

if [[ -z "${VERSION_SEMVER}" ]]; then
  VERSION=4.0.4
else
  VERSION="${VERSION_SEMVER}"
fi

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

export ARCH=x86_64
JVM=zulu17.50.19-ca-fx-jdk17.0.11-linux_x64
set -e
ZIP=$JVM.tar.gz
export JAVA_HOME=$HOME/bin/java17/
if test -d $JAVA_HOME/$JVM/; then
  echo "$JAVA_HOME exists."
else
	rm -rf $JAVA_HOME
	mkdir -p $JAVA_HOME
	wget https://cdn.azul.com/zulu/bin/$ZIP 
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
ls -al $JAVA_HOME



ICON=$NAME.png
cp SourceIcon.png $ICON
rm -rf $SCRIPT_DIR/$NAME
rm -rf $SCRIPT_DIR/$NAME.AppDir
BUILDDIR=CaDoodleUpdater/build/libs/ 
TARGETJAR=CaDoodleUpdater.jar

$JAVA_HOME/bin/jpackage --input $BUILDDIR \
  --name $NAME \
  --main-jar $TARGETJAR \
  --main-class $MAIN \
  --type app-image \
  --app-version $VERSION \
  --java-options '--enable-preview'
 
mkdir -p $SCRIPT_DIR/$NAME.AppDir/usr/
cp -r $SCRIPT_DIR/$NAME/* $SCRIPT_DIR/$NAME.AppDir/usr/

mkdir -p  $NAME.AppDir/usr/share/applications
mkdir -p  $NAME.AppDir/usr/share/icons/hicolor/256x256/apps

cat << EOF > $NAME.AppDir/$NAME.desktop
[Desktop Entry]
Name=$NAME
Exec=BowlerStudioUpdater-$ARCH.AppImage
Icon=$NAME
Type=Application
Categories=Utility
EOF

 
chmod 644 $NAME.AppDir/$NAME.desktop

ln -s usr/bin/$NAME  $SCRIPT_DIR/$NAME.AppDir/AppRun

chmod 755 $SCRIPT_DIR/$NAME.AppDir/usr/bin/$NAME
chmod 755 $SCRIPT_DIR/$NAME.AppDir/AppRun

cp $ICON $NAME.AppDir/usr/share/icons/hicolor/256x256/apps/$NAME.png
cp $ICON $NAME.AppDir/$NAME.png


# Step 2: Install AppImageTool (done once)
TOOL=appimagetool-x86_64.AppImage
if test -f $TOOL; then
	echo $TOOL exists
else
	wget https://github.com/AppImage/AppImageKit/releases/download/continuous/$TOOL
	chmod +x $TOOL
fi

./$TOOL $NAME.AppDir
echo "Testing executable:"
#./$NAME-$ARCH.AppImage one two
echo "Building .deb..."
rm -rf *.deb
$JAVA_HOME/bin/jpackage --input $BUILDDIR \
  --name $NAME \
  --main-jar $TARGETJAR \
  --main-class $MAIN \
  --type deb \
  --app-version $VERSION \
  --linux-shortcut \
  --icon $ICON \
  --copyright "Creative Commons" \
  --vendor CommonWealthRobotics \
 --linux-menu-group "Education;Graphics;Development;" \
  --java-options '--enable-preview'
echo "Deb built!"
ls -al
rm -rf release
mkdir release
cp *.deb release/$NAME-Linux-$ARCH.deb
cp $NAME-$ARCH.AppImage release/$NAME-Linux-$ARCH.AppImage
#sudo apt remove bowlerlauncher
#sudo dpkg -i *.deb


