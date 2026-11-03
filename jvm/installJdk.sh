#!/usr/bin/env bash
# Downloads the pinned Zulu JavaFX JDK for this OS/arch from jvm.json (single source
# of truth) and installs the archive's <archive-name> top directory verbatim to
# ~/bin/CaDoodle-ApplicationInstall/<archive-name>/. The java launcher is found
# at bin/java(.exe) everywhere except current macOS zips, which ship only a
# Contents/Home bundle (older macOS zips expose it through top-level symlinks).
# Verifies the install with java -version and the pinned major version; exits
# non-zero if the JVM is missing or broken.
# Re-run any time jvm.json changes: an existing install is replaced when the pinned
# archive differs. Override the install root with JDK_INSTALL_ROOT, the jvm.json key
# with JVM_KEY, and the expected major version with JVM_MAJOR_VERSION. A locally
# pre-downloaded archive can be reused with JDK_ARCHIVE_PATH.
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
JVM_SCRIPTS=$SCRIPT_DIR
[ -f "$JVM_SCRIPTS/jvm.json" ] || { echo "jvm.json not found at $JVM_SCRIPTS" >&2; exit 1; }

JVM=$(bash "$JVM_SCRIPTS/getJdkArchiveName.sh")
ZIP=$(bash "$JVM_SCRIPTS/getJdkArchiveFile.sh")
URL=$(bash "$JVM_SCRIPTS/getJdkUrl.sh")
MAJOR_VERSION=${JVM_MAJOR_VERSION:-25}
INSTALL_ROOT=${JDK_INSTALL_ROOT:-$HOME/bin/CaDoodle-ApplicationInstall}
INSTALL_DIR=$INSTALL_ROOT/$JVM
TEMP_DIR=$(mktemp -d)
trap 'rm -rf "$TEMP_DIR"' EXIT

# Locate the java launcher in an installed JDK dir: top-level bin/java(.exe)
# (Linux, Windows, and older macOS zips through the bin symlink) or
# Contents/Home/bin/java(.exe) (current macOS zips)
find_java() { # dir -> echoes launcher path, non-zero if absent
	local dir=$1
	if [ -e "$dir/bin/java.exe" ]; then
		echo "$dir/bin/java.exe"
	elif [ -e "$dir/bin/java" ]; then
		echo "$dir/bin/java"
	elif [ -e "$dir/Contents/Home/bin/java.exe" ]; then
		echo "$dir/Contents/Home/bin/java.exe"
	elif [ -e "$dir/Contents/Home/bin/java" ]; then
		echo "$dir/Contents/Home/bin/java"
	else
		return 1
	fi
}

# Validate an existing install before trusting it; a stale or broken one is reinstalled.
is_working_jvm() {
	local dir=$1 java out
	java=$(find_java "$dir") || return 1
	chmod +x "$java" 2>/dev/null || true
	# java -version prints to stderr and quotes the version: openjdk version "25.0.4"
	out=$("$java" -version 2>&1) || return 1
	grep -q "version \"${MAJOR_VERSION}\." <<<"$out" || return 1
}

if is_working_jvm "$INSTALL_DIR"; then
	echo "JDK already installed and working: $INSTALL_DIR"
else
	echo "Installing JDK $JVM to $INSTALL_DIR"
	if [ -n "${JDK_ARCHIVE_PATH:-}" ]; then
		# Reuse a pre-downloaded archive (payload builds) instead of re-downloading
		cp "$JDK_ARCHIVE_PATH" "$TEMP_DIR/$ZIP"
	else
		curl -fL --retry 3 -o "$TEMP_DIR/$ZIP" "$URL"
	fi
	rm -rf "$INSTALL_DIR"
	EXTRACT_DIR=$TEMP_DIR/extract
	mkdir -p "$EXTRACT_DIR"
	case $ZIP in
	*.zip)
		if command -v unzip >/dev/null 2>&1; then
			unzip -o -q "$TEMP_DIR/$ZIP" -d "$EXTRACT_DIR"
		elif command -v python3 >/dev/null 2>&1; then
			# Git Bash and minimal MSYS2 installs ship no unzip; python3 does
			python3 -m zipfile -e "$TEMP_DIR/$ZIP" "$EXTRACT_DIR"
		elif command -v python >/dev/null 2>&1; then
			# Windows runners expose python.exe without a python3 alias
			python -m zipfile -e "$TEMP_DIR/$ZIP" "$EXTRACT_DIR"
		else
			echo "unzip or python is required to extract $ZIP" >&2
			exit 1
		fi
		;;
	*) tar -xzf "$TEMP_DIR/$ZIP" -C "$EXTRACT_DIR" ;;
	esac

	# Uniform layout on every OS: the archive's top dir is named after the
	# archive and installed verbatim, symlinks included
	[ -d "$EXTRACT_DIR/$JVM" ] || {
		echo "Archive $ZIP does not contain the expected $JVM/ top directory; extracted:" >&2
		ls -la "$EXTRACT_DIR" >&2
		exit 1
	}
	mkdir -p "$INSTALL_ROOT"
	mv "$EXTRACT_DIR/$JVM" "$INSTALL_DIR"
	chmod -R u+rwX "$INSTALL_DIR"

	if is_working_jvm "$INSTALL_DIR"; then
		echo "JDK installed successfully: $INSTALL_DIR"
	else
		echo "FAIL: JDK install at $INSTALL_DIR did not pass 'java -version' ($MAJOR_VERSION.x expected)" >&2
		exit 1
	fi
fi

JAVA_LAUNCHER=$(find_java "$INSTALL_DIR")
echo "--- java -version ---"
"$JAVA_LAUNCHER" -version
