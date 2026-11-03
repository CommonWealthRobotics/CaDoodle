#!/usr/bin/env bash
# Prints the full download URL of the JDK archive for this OS and architecture.
# Override auto-detection with JVM_KEY=<key>, e.g. JVM_KEY=Mac-aarch64
set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
JVM_JSON="$SCRIPT_DIR/jvm.json"

command -v jq >/dev/null 2>&1 || { echo "jq is required but not installed" >&2; exit 1; }
[ -f "$JVM_JSON" ] || { echo "jvm.json not found at $JVM_JSON" >&2; exit 1; }

# Detect the jvm.json key: OS-arch, e.g. Linux-x64, Mac-aarch64, Windows-x64
detect_key() {
	local kernel arch
	kernel=$(uname -s)
	local os
	case "$kernel" in
	MINGW* | MSYS* | CYGWIN*) os="Windows" ;; # bash on windows (Git Bash, MSYS2, Cygwin)
	Darwin) os="Mac" ;;
	Linux) os="Linux" ;; # includes WSL, which runs the Linux JDK
	*)
		if [ "${OS:-}" = "Windows_NT" ]; then
			os="Windows"
		else
			echo "Unsupported OS: $kernel" >&2
			exit 1
		fi
		;;
	esac
	case "$(uname -m)" in
	x86_64 | amd64 | AMD64) arch="x64" ;;
	aarch64 | arm64) arch="aarch64" ;;
	*)
		echo "Unsupported architecture: $(uname -m)" >&2
		exit 1
		;;
	esac
	echo "${os}-${arch}"
}

KEY="${JVM_KEY:-$(detect_key)}"

jq -er --arg key "$KEY" '.[$key] | select(. != null) | .url + .name + "." + .type' "$JVM_JSON" 2>/dev/null || {
	echo "No entry '$KEY' in $JVM_JSON (available: $(jq -r 'keys | join(", ")' "$JVM_JSON"))" >&2
	exit 1
}
