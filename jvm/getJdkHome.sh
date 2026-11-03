#!/usr/bin/env bash
# Prints the JAVA_HOME of the pinned JDK for this OS/arch as installed by
# installJdk.sh: <install-root>/<archive-name>[/Contents/Home on macOS bundle
# layouts, where bin/java only lives inside the bundle]. Override the install
# root with JDK_INSTALL_ROOT and the jvm.json key with JVM_KEY.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
JVM_SCRIPTS=$SCRIPT_DIR
[ -f "$JVM_SCRIPTS/jvm.json" ] || { echo "jvm.json not found at $JVM_SCRIPTS" >&2; exit 1; }

INSTALL_ROOT=${JDK_INSTALL_ROOT:-$HOME/bin/CaDoodle-ApplicationInstall}
JVM=$(bash "$JVM_SCRIPTS/getJdkArchiveName.sh")

JAVA_HOME=$INSTALL_ROOT/$JVM
if [ -d "$JAVA_HOME/Contents/Home" ]; then
	JAVA_HOME=$JAVA_HOME/Contents/Home
fi
echo "$JAVA_HOME"
