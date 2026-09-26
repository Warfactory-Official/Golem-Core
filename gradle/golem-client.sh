#!/bin/sh
# golem-client.sh <mod dir> [node] [gradle args]: builds each `upstream("...")` of <mod dir>/settings.gradle.kts for the
# node, in declaration order, then runs the node's client. Two invocations: obfuscated Fabric nodes resolve and remap
# upstream jars at configuration time (checkUpstreamJars). GOLEM_QUICKPLAY=<save> joins a singleplayer world.
set -e

here=$(CDPATH= cd -- "$1" && pwd)
shift

case "$1" in
    -*|'') node='' ;;
    *) node=$1; shift ;;
esac
[ -n "$node" ] || node=$(sed -n 's/^stonecutter active "\([^"]*\)".*/\1/p' "$here/stonecutter.gradle.kts")
if [ ! -d "$here/versions/$node" ]; then
    echo "client: no such node '$node'. Available:" >&2
    ls "$here/versions" | sed 's/^/          /' >&2
    exit 1
fi

# Loom needs Gradle on Java 25+; each node's toolchain still runs the game.
java_major() {
    [ -x "$1/bin/java" ] || return 1
    "$1/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1
}
if ! [ "$(java_major "$JAVA_HOME" 2>/dev/null || echo 0)" -ge 25 ] 2>/dev/null; then
    JAVA_HOME=${GOLEM_JAVA_HOME:-/usr/lib/jvm/java-25-openjdk}
    if ! [ "$(java_major "$JAVA_HOME" 2>/dev/null || echo 0)" -ge 25 ] 2>/dev/null; then
        echo "client: no JDK 25 or newer at $JAVA_HOME. Set GOLEM_JAVA_HOME to one." >&2
        exit 1
    fi
fi
export JAVA_HOME

# Wrappers are checked in without the executable bit.
sed -n 's/^[[:space:]]*upstream("\([^"]*\)").*/\1/p' "$here/settings.gradle.kts" | while read -r path; do
    echo "==> $path  :$node:build"
    (cd "$here/$path" && sh gradlew ":$node:build" -x test)
done

echo "==> $(basename "$here")  :$node:runClient"
cd "$here"
if [ -n "$GOLEM_QUICKPLAY" ]; then set -- "-PquickPlayWorld=$GOLEM_QUICKPLAY" "$@"; fi
exec sh ./gradlew ":$node:runClient" "$@"
