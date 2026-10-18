#!/usr/bin/env bash
# Spike A (docs/research-plugins.md §9): the hello plugin loaded through ServiceLoader on the JVM and
# in a native image, with and without the plugin jar. Needs the pinned GraalVM (GRAALVM_HOME).
#   spike/plugins/run.sh            JVM runs + two native images (with / without hello)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
: "${GRAALVM_HOME:?set GRAALVM_HOME to the pinned GraalVM (.sdkmanrc)}"
export JAVA_HOME="$GRAALVM_HOME" PATH="$GRAALVM_HOME/bin:$PATH"
cd "$here"
sbt --server -batch "hello/package; hostClasspath; host/testOnly *" | grep -E 'Passed|Failed|error|\+ |==> X' || true
cp_host="$(cat target/host-classpath.txt)"
hello="$(find target -name 'plugin-hello_3-*.jar' | head -1)"
[ -n "$hello" ] || { echo "no plugin-hello jar" >&2; exit 1; }
conf="$here/spike.conf"
out="$here/target/native"; mkdir -p "$out"

echo "== JVM, host only";  java -cp "$cp_host" com.histmersion.spike.host.Main "$conf"
echo "== JVM, host + hello"; java -cp "$cp_host:$hello" com.histmersion.spike.host.Main "$conf"
echo "== JVM, host + hello, env override"; HISTMERSION_PLUGIN_HELLO_MAX_ITEMS=9 java -cp "$cp_host:$hello" com.histmersion.spike.host.Main "$here/spike-defaults.conf"

# the same plugin jar with its reflection entries removed from its metadata (resources kept): the
# reflective greeter lookup must then fail in the image — the plugin's own metadata is what made it work
bare="$out/plugin-hello-no-reflection.jar"; rm -rf "$out/bare" && mkdir -p "$out/bare"
(cd "$out/bare" && jar xf "$here/$hello" \
  && printf '{ "resources": [ { "glob": "META-INF/histmersion/plugins/hello.conf" }, { "glob": "reference.conf" } ] }\n' \
     > META-INF/native-image/com.histmersion.spike/hello/reachability-metadata.json \
  && jar cf "$bare" .)
for variant in with-hello without-hello hello-no-reflection; do
  cp="$cp_host"
  case "$variant" in with-hello) cp="$cp_host:$hello";; hello-no-reflection) cp="$cp_host:$bare";; esac
  echo "== native-image ($variant)"
  /usr/bin/time -l native-image --no-fallback -cp "$cp" com.histmersion.spike.host.Main "$out/host-$variant" \
    > "$out/build-$variant.log" 2>&1 || { tail -40 "$out/build-$variant.log"; exit 1; }
  grep -E 'Finished generating|Peak RSS|real|maximum resident' "$out/build-$variant.log" | sed 's/^/   /'
  "$out/host-$variant" "$conf" || echo "   (exit $?)"
done
echo "== native (with-hello), env override"
HISTMERSION_PLUGIN_HELLO_MAX_ITEMS=9 "$out/host-with-hello" "$here/spike-defaults.conf"
echo "== native (with-hello), a typo'd value"
"$out/host-with-hello" "$here/spike-bad.conf" || echo "   (exit $?)"
