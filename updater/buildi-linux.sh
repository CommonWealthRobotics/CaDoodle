#!/bin/bash

# https://github.com/CommonWealthRobotics/CaDoodle/blob/56884343ba7ade94cc9442275b664aaf104ece36/updater/cadoodle-jvm-configuration.json
JVERELEASE=zulu17.44.53-ca-fx-jdk17.0.8.1-linux_x64
JVMDIR=java17-linux/

unzipifiy(){
	testget  $1 $2
	echo "Unzipping $1 to $2"
	tar -xzf $2/$1.tar.gz -C $2
	mv $2/$1/* $2/
	rmdir $2/$1/
}

testget () {
	if [ -f $JVERELEASE.tar.gz ]; then
		echo "$JVERELEASE.tar.gz exist"
	else
		wget https://cdn.azul.com/zulu/bin/$JVERELEASE.tar.gz  -O $2$1.tar.gz
	fi
}

if (! test -e $JVMDIR/$JVERELEASE.tar.gz) then
	rm -rf $JVMDIR
	mkdir -p $JVMDIR
	unzipifiy $JVERELEASE $JVMDIR
fi


./gradlew shadowJar

export JAVA_HOME=$JVMDIR

$JVMDIR/bin/jpackage --input build/libs/ \
  --verbose \
  --name CaDoodleUpdater \
  --main-jar CaDoodleUpdater.jar \
  --type deb \
  $JVMDIR/