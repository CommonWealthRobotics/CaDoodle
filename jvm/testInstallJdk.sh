#!/usr/bin/env bash
# Edge-case tests for installJdk.sh using synthetic JDK archives and a curl stub,
# so the suite is fast and offline. Set RUN_REAL_DOWNLOAD=1 to also run one
# scenario against the real CDN archive (~350 MB).
set -u
PASS=0
FAIL=0
HERE=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
INSTALLER=$HERE/installJdk.sh
TMP=${TMPDIR:-/tmp}
WORK=$TMP/test-installjdk
rm -rf "$WORK"
mkdir -p "$WORK"
STUB=$WORK/stub-bin
mkdir -p "$STUB"

case $(uname -s) in
MINGW* | MSYS* | CYGWIN*) HOST_OS=Windows ;;
Darwin) HOST_OS=Mac ;;
*) HOST_OS=Linux ;;
esac

# Fake java launchers with the behaviour baked in at creation time
mk_fake_java() { # dest mode: ok|broken|wrongversion
	case $2 in
	ok)
		cat >"$1" <<'EOF'
#!/usr/bin/env bash
echo 'openjdk version "25.0.4" 2026-01-20'
echo 'OpenJDK Runtime Environment Zulu25.36+205-CA (build 25.0.4+105-LTS)'
exit 0
EOF
		;;
	broken)
		cat >"$1" <<'EOF'
#!/usr/bin/env bash
exit 3
EOF
		;;
	wrongversion)
		cat >"$1" <<'EOF'
#!/usr/bin/env bash
echo 'openjdk version "21.0.1" 2025-10-21'
exit 0
EOF
		;;
	esac
	chmod +x "$1"
}

# curl stub: curl [-fL --retry N] -o DEST URL -> copy the scenario archive to DEST
make_stub() { # archive to serve
	cat >"$STUB/curl" <<EOF
#!/usr/bin/env bash
dest=""
while [ \$# -gt 0 ]; do
	case \$1 in
	-o) dest=\$2; shift 2 ;;
	*) shift ;;
	esac
done
[ -n "\$dest" ] || { echo "stub: no -o destination" >&2; exit 9; }
echo "stub: serving $1"
cp "$1" "\$dest"
EOF
	chmod +x "$STUB/curl"
}

# zip -r is absent from Git Bash; build the archive with python's zipfile instead
make_zip() { # out.zip source-dir
	if command -v zip >/dev/null 2>&1; then
		# -y: store symlinks as symlinks (zip follows them by default)
		(cd "$2" && zip -qry "$1" .)
	elif command -v python3 >/dev/null 2>&1; then
		python3 - "$1" "$2" <<'PYEOF'
import os, sys, zipfile
out, src = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(out, "w") as zf:
    for root, dirs, files in os.walk(src):
        for name in dirs + files:
            path = os.path.join(root, name)
            rel = os.path.relpath(path, src)
            if os.path.islink(path):
                # store symlinks like zip -r does so extraction recreates them
                zi = zipfile.ZipInfo(rel)
                zi.external_attr = 0o120777 << 16
                zf.writestr(zi, os.readlink(path))
            else:
                zf.write(path, rel)
PYEOF
	else
		echo "zip or python3 is required to build test archives" >&2
		return 1
	fi
}

run_installer() { # JDK_INSTALL_ROOT [VAR=val ...]
	local root=$1
	shift
	# env is required: a quoted "$@" in command position is never parsed as assignments
	env PATH=$STUB:$PATH JDK_INSTALL_ROOT=$root "$@" bash "$INSTALLER" >"$WORK/out.log" 2>&1
}

expect_ok() { # label root [VAR=val ...]
	local label=$1 root=$2
	shift 2
	if run_installer "$root" "$@" && [ -x "$root/$DEFAULT_NAME/bin/$LAUNCHER" ]; then
		echo "PASS: $label"
		PASS=$((PASS + 1))
	else
		echo "FAIL: $label"
		cat "$WORK/out.log"
		FAIL=$((FAIL + 1))
	fi
}

expect_fail() { # label root
	if run_installer "$2"; then
		echo "FAIL: $1 (expected non-zero exit but got 0)"
		cat "$WORK/out.log"
		FAIL=$((FAIL + 1))
	else
		echo "PASS: $1"
		PASS=$((PASS + 1))
	fi
}

DEFAULT_NAME=$(bash "$HERE/getJdkArchiveName.sh")
DEFAULT_TYPE=$(bash "$HERE/getJdkArchiveFile.sh" | sed 's/.*\.//')
LAUNCHER=java
[ "$HOST_OS" = Windows ] && LAUNCHER=java.exe

# Synthetic archive in the nested top-dir layout Zulu ships
FAKEDIR=$WORK/fakepkg/$DEFAULT_NAME
mkdir -p "$FAKEDIR/bin"
mk_fake_java "$FAKEDIR/bin/$LAUNCHER" ok
if [ "$DEFAULT_TYPE" = zip ]; then
	(cd "$WORK/fakepkg" && zip -qr "$WORK/fake-archive.zip" .)
	FAKE_ARCHIVE=$WORK/fake-archive.zip
else
	(cd "$WORK/fakepkg" && tar -czf "$WORK/fake-archive.tar.gz" .)
	FAKE_ARCHIVE=$WORK/fake-archive.tar.gz
fi
# Current Zulu macOS zip layout: only a Contents/ bundle at the top level, the
# launcher lives in Contents/Home/bin/java (no top-level bin)
MAC_NAME=$(JVM_KEY=Mac-x64 bash "$HERE/getJdkArchiveName.sh")
mkdir -p "$WORK/fakemac/$MAC_NAME/Contents/Home/bin"
mkdir -p "$WORK/fakemac/$MAC_NAME/Contents/Home/lib"
mk_fake_java "$WORK/fakemac/$MAC_NAME/Contents/Home/bin/java" ok
make_zip "$WORK/fake-mac.zip" "$WORK/fakemac"

# App-style bundle layout: Contents/Home directly at the top level
mkdir -p "$WORK/fakeapp/Contents/Home/bin"
mk_fake_java "$WORK/fakeapp/Contents/Home/bin/java" ok
make_zip "$WORK/fake-app.zip" "$WORK/fakeapp"

# 1. Fresh install; java -version runs and prints the pinned major version
ROOT=$WORK/root1
make_stub "$FAKE_ARCHIVE"
expect_ok "fresh install" "$ROOT"
grep -q 'openjdk version "25' "$WORK/out.log" || {
	echo "FAIL: java -version output missing from installer output"
	FAIL=$((FAIL + 1))
}
# getJdkHome.sh resolves the install root on the flat (non-mac) layout
[ "$(JDK_INSTALL_ROOT=$ROOT bash "$HERE/getJdkHome.sh")" = "$ROOT/$DEFAULT_NAME" ] || {
	echo "FAIL: getJdkHome.sh did not resolve $ROOT/$DEFAULT_NAME"
	FAIL=$((FAIL + 1))
}

# 2. Idempotent re-run on a good install (the stub must not be hit)
ROOT=$WORK/root1
make_stub "$WORK/does-not-exist"
expect_ok "re-run on working install skips download" "$ROOT"
grep -q "stub: serving" "$WORK/out.log" && {
	echo "FAIL: re-run downloaded the archive again"
	FAIL=$((FAIL + 1))
}

# 3. Existing install with the launcher missing -> reinstalled and passes
ROOT=$WORK/root3
mkdir -p "$ROOT/$DEFAULT_NAME/bin"
make_stub "$FAKE_ARCHIVE"
expect_ok "broken install (no java) reinstalled" "$ROOT"

# 4. Existing install with java exiting non-zero -> reinstalled and passes
ROOT=$WORK/root4
mkdir -p "$ROOT/$DEFAULT_NAME/bin"
mk_fake_java "$ROOT/$DEFAULT_NAME/bin/$LAUNCHER" broken
make_stub "$FAKE_ARCHIVE"
expect_ok "broken install (java exits 3) reinstalled" "$ROOT"

# 5. Existing install with wrong major version -> reinstalled and passes
ROOT=$WORK/root5
mkdir -p "$ROOT/$DEFAULT_NAME/bin"
mk_fake_java "$ROOT/$DEFAULT_NAME/bin/$LAUNCHER" wrongversion
make_stub "$FAKE_ARCHIVE"
expect_ok "wrong major version reinstalled" "$ROOT"

# 6. Truncated archive -> extraction fails, installer exits non-zero, no java left behind
ROOT=$WORK/root6
ARCHIVE_SIZE=$(wc -c <"$FAKE_ARCHIVE")
TRUNCATED=$WORK/truncated-${FAKE_ARCHIVE##*/}
head -c $((ARCHIVE_SIZE / 2)) "$FAKE_ARCHIVE" >"$TRUNCATED"
make_stub "$TRUNCATED"
expect_fail "truncated archive fails" "$ROOT"
[ ! -e "$ROOT/$DEFAULT_NAME/bin/$LAUNCHER" ] || {
	echo "FAIL: truncated archive left a java behind"
	FAIL=$((FAIL + 1))
}

# 7. Current Zulu macOS layout: installed verbatim; launcher only inside
#    Contents/Home, and getJdkHome.sh resolves JAVA_HOME into the bundle
ROOT=$WORK/root7
make_stub "$WORK/fake-mac.zip"
if run_installer "$ROOT" JVM_KEY=Mac-x64 &&
	[ ! -e "$ROOT/$MAC_NAME/bin" ] &&
	[ -x "$ROOT/$MAC_NAME/Contents/Home/bin/java" ] &&
	[ "$(JVM_KEY=Mac-x64 JDK_INSTALL_ROOT=$ROOT bash "$HERE/getJdkHome.sh")" = "$ROOT/$MAC_NAME/Contents/Home" ]; then
	echo "PASS: macOS Contents/Home bundle install"
	PASS=$((PASS + 1))
else
	echo "FAIL: macOS Contents/Home bundle scenario"
	cat "$WORK/out.log"
	FAIL=$((FAIL + 1))
fi

# 8. Windows-key install on any host: java.exe launcher must be found and verified
WIN_NAME=$(JVM_KEY=Windows-x64 bash "$HERE/getJdkArchiveName.sh")
ROOT=$WORK/root8
mkdir -p "$WORK/fakewin/$WIN_NAME/bin"
mk_fake_java "$WORK/fakewin/$WIN_NAME/bin/java.exe" ok
make_zip "$WORK/fake-win.zip" "$WORK/fakewin"
make_stub "$WORK/fake-win.zip"
if run_installer "$ROOT" JVM_KEY=Windows-x64 &&
	[ -x "$ROOT/$WIN_NAME/bin/java.exe" ]; then
	echo "PASS: Windows java.exe install verified"
	PASS=$((PASS + 1))
else
	echo "FAIL: Windows java.exe scenario"
	cat "$WORK/out.log"
	FAIL=$((FAIL + 1))
fi

# 9. JVM_MAJOR_VERSION override is honoured: demanding 21 rejects the 25 JVM
ROOT=$WORK/root9
make_stub "$FAKE_ARCHIVE"
if run_installer "$ROOT" JVM_MAJOR_VERSION=21; then
	echo "FAIL: JVM_MAJOR_VERSION=21 accepted the pinned 25 JVM"
	cat "$WORK/out.log"
	FAIL=$((FAIL + 1))
else
	echo "PASS: JVM_MAJOR_VERSION=21 rejects the pinned 25 JVM"
	PASS=$((PASS + 1))
fi

# 10. Read-only install root -> clean failure with non-zero exit
# (chmod-based enforcement is a no-op for directories on Windows)
case $HOST_OS in
Linux | Mac)
	ROOT=$WORK/root10
	mkdir -p "$ROOT"
	chmod 555 "$ROOT"
	expect_fail "read-only install root fails" "$ROOT"
	chmod 755 "$ROOT"
	;;
esac

# 11. Optional real end-to-end download from the CDN (run when jvm.json changes)
if [ "${RUN_REAL_DOWNLOAD:-0}" = "1" ]; then
	ROOT=$WORK/root-real
	rm -f "$STUB/curl" # use the real curl
	if JDK_INSTALL_ROOT=$ROOT bash "$INSTALLER" >"$WORK/out-real.log" 2>&1 &&
		[ -x "$ROOT/$DEFAULT_NAME/bin/$LAUNCHER" ]; then
		echo "PASS: real CDN download end-to-end"
		PASS=$((PASS + 1))
	else
		echo "FAIL: real CDN download end-to-end"
		cat "$WORK/out-real.log"
		FAIL=$((FAIL + 1))
	fi
fi

echo
echo "Results: $PASS passed, $FAIL failed"
exit $((FAIL > 0))
