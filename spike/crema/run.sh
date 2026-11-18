#!/usr/bin/env bash
# Spike S7 (notes/crema-spike-s7.md): the plugin SPI in a GraalVM CE native image with and without
# -H:+RuntimeClassLoading (Project Crema). NOT production; branch contracts3-crema-spike, never merged.
#
# Prerequisite (sbt): scripts/dev/sbt.sh pluginHello/package cremaSpike/cremaClasspath
#   bash spike/crema/run.sh jars              plugin-side jars: bench (3.10), lazies (3.10, 3.3.6, 3.3.6+Sloth)
#   bash spike/crema/run.sh agent             tracing agent over the driver on the JVM → spike/crema/metadata
#   bash spike/crema/run.sh build <variant>   one native image (closed | rcl | rcl-jit | rcl-compat)
#   bash spike/crema/run.sh measure <variant> start time, plugin load, bench and lazy runs of that image
#   bash spike/crema/run.sh jvm               the same battery on HotSpot (the JVM-mode reference)
# One heavy step at a time; check free memory before `build`.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
here="$root/spike/crema"
graal="${GRAALVM_HOME:-$HOME/.sdkman/candidates/java/25.4.4.1+1-graalce}"
export JAVA_HOME="$graal" PATH="$graal/bin:$PATH"
out="$here/target"; logs="$root/refactor-audit/concept-table"; meta="$here/metadata"
mkdir -p "$out" "$logs"
drv="$(ls -d "$root"/target/out/jvm/scala-*/histmersion-crema-spike | head -1)"
hello="$(ls "$root"/target/out/jvm/scala-*/histmersion-plugin-hello/histmersion-plugin-hello_3-*.jar | grep -v -- '-tests\.jar$' | head -1)"
cp="$(cat "$drv/crema-classpath.txt")"
main=com.histmersion.spike.crema.CremaMain
sloth="$(ls "${SLOTH_HOME:-$HOME/Workspaces/Github/sloth}"/cli/target/scala-*/sloth.jar | head -1)"

scalac() { # <version> <out.jar> <sources...>
  local v="$1" o="$2"; shift 2
  local ccp lcp
  ccp="$(cs fetch --mode offline --classpath "org.scala-lang:scala3-compiler_3:$v" 2>/dev/null)"
  lcp="$(cs fetch --mode offline --classpath "org.scala-lang:scala3-library_3:$v" 2>/dev/null)"
  java -cp "$ccp" dotty.tools.dotc.Main -classpath "$lcp" -d "$o" "$@"
}

case "${1:-}" in
  jars)
    scalac 3.10.0 "$out/bench.jar" "$here/bench/Bench.scala" "$here/bench/JdkUse.scala"
    scalac 3.10.0 "$out/lazies-310.jar" "$here/lazy/Lazies.scala"
    scalac 3.3.6 "$out/lazies-33.jar" "$here/lazy/Lazies.scala"
    cp "$out/lazies-33.jar" "$out/lazies-33-sloth.jar"
    java -jar "$sloth" "$out/lazies-33-sloth.jar" \
      --hierarchy-classpath "$(cs fetch --mode offline --classpath org.scala-lang:scala3-library_3:3.3.6 2>/dev/null)"
    for j in lazies-310 lazies-33 lazies-33-sloth; do
      echo "== $j: Lazies\$ bytecode refs"
      javap -c -p -cp "$out/$j.jar" 'com.histmersion.spike.cremalazy.Lazies$' | grep -E 'Unsafe|LazyVals|VarHandle|objCAS|compareAndSet' | sort | uniq -c | head
    done
    ls -l "$out"/*.jar
    ;;
  agent)
    rm -rf "$meta"; mkdir -p "$meta"
    filter="$here/agent-filter.json"
    agent="-agentlib:native-image-agent=config-merge-dir=$meta,access-filter-file=$filter"
    java "$agent" -cp "$cp" "$main" ready
    java "$agent" -cp "$cp" "$main" hello "$hello"
    java "$agent" -cp "$cp" "$main" bench aot 1000 1
    java "$agent" -cp "$cp" "$main" bench "$out/bench.jar" 1000 1
    java "$agent" -cp "$cp" "$main" lazy "$out/lazies-310.jar" com.histmersion.spike.cremalazy.Lazies
    java "$agent" -cp "$cp" "$main" lazy "$out/lazies-33-sloth.jar" com.histmersion.spike.cremalazy.Lazies
    ls -l "$meta"
    ;;
  build)
    variant="${2:?variant: closed | rcl | rcl-jit | rcl-compat}"
    image_cp="$cp"; flags=()
    rcl=(-H:+UnlockExperimentalVMOptions -H:+RuntimeClassLoading)
    # the plugin platform's RUN-TIME packages (plugin-api and its Histmersion jars, sconfig, the kindlings readers'
    # runtime, iron, jsoniter core, the Scala library) and the JDK packages plugin code commonly calls, kept whole:
    # interpreted plugin code may call any method of them, and a method analysis did not reach is a crash.
    # Not path=<jar>, and not the kindlings top-level packages: their classes name macro-time classes
    # (internal.compiletime) that need the compiler, and analysis fails with NoClassDefFoundError.
    pkgs=(
      com.histmersion com.histmersion.plugin com.histmersion.util.problem com.histmersion.util.json
      com.histmersion.util.nonempty
      org.ekrich.config org.ekrich.config.impl org.ekrich.config.parser
      hearth.kindlings.sconfigderivation.internal.runtime hearth.kindlings.jsoniterderivation.internal.runtime
      io.github.iltotore.iron 'io.github.iltotore.iron.constraint.*'
      com.github.plokhotnyuk.jsoniter_scala.core
      scala 'scala.collection.*' 'scala.runtime.*' 'scala.util.*' scala.math 'scala.jdk.*' scala.reflect
      java.lang java.lang.invoke java.lang.reflect java.util 'java.util.concurrent.*' java.util.function java.util.regex
      java.util.stream 'java.time.*' java.io java.math java.text java.nio java.nio.charset java.nio.file java.net
    )
    platform="$(printf 'package=%s\n' "${pkgs[@]}" | paste -sd, -)"
    min="-H:Preserve=package=java.lang.invoke,package=scala.runtime"
    case "$variant" in
      closed) image_cp="$cp:$hello" ;;
      rcl) flags=("${rcl[@]}") ;;
      rcl-min) flags=("${rcl[@]}" "$min") ;;
      rcl-platform) flags=("${rcl[@]}" "$min,$platform") ;;
      rcl-all) flags=("${rcl[@]}" -H:Preserve=all) ;;
      rcl-jit) flags=("${rcl[@]}" "$min,$platform" -H:+GraalJITCompileAtRuntime) ;;
      rcl-compat) flags=("${rcl[@]}" "$min,$platform" -H:+CompatibilityMode) ;;
      *) echo "unknown variant $variant" >&2; exit 2 ;;
    esac
    printf '%s\n' "${flags[@]}" | tr ',' '\n' | sed 's#^path=.*/#path=…/#' | head -40
    log="$logs/crema-build-$variant.log"
    echo "native-image ($variant) → $out/crema-$variant; log $log"
    start=$(date +%s)
    rc=0
    # rcl-platform and up: the kindlings reader classes a derived plugin reader calls, which -H:Preserve cannot
    # keep (their package names macro-time classes), registered for reflection instead
    confdirs="$meta"; case "$variant" in rcl-platform|rcl-jit|rcl-compat) confdirs="$meta,$here/platform-metadata" ;; esac
    /usr/bin/time -l native-image "${flags[@]}" -H:ConfigurationFileDirectories="$confdirs" \
      -cp "$image_cp" "$main" "$out/crema-$variant" > "$log" 2>&1 || rc=$?
    echo "exit $rc after $(( $(date +%s) - start )) s"
    grep -E 'Finished generating|Peak RSS|maximum resident|real |Error|error:|Image size|\.dylib' "$log" | tail -20 || true
    ls -l "$out/crema-$variant" 2>/dev/null || true
    exit "$rc"
    ;;
  measure | jvm)
    if [ "$1" = jvm ]; then variant=jvm; bin=(java -cp "$cp" "$main")
    else variant="${2:?variant}"; bin=("$out/crema-$variant"); fi
    hello_arg="$hello"; [ "$variant" = closed ] && hello_arg=static
    {
      echo "== $variant: size"; [ "$variant" = jvm ] || ls -l "$out/crema-$variant"
      echo "== $variant: ready ×9"; python3 -I "$here/timeit.py" 9 "${bin[@]}" ready
      echo "== $variant: hello ($hello_arg) ×9"; python3 -I "$here/timeit.py" 9 "${bin[@]}" hello "$hello_arg"
      echo "== $variant: bench aot"; "${bin[@]}" bench aot 300000 5 || echo "exit $?"
      echo "== $variant: bench jar"; "${bin[@]}" bench "$out/bench.jar" 300000 5 || echo "exit $?"
      for j in lazies-310 lazies-33 lazies-33-sloth; do
        echo "== $variant: lazy $j"
        "${bin[@]}" lazy "$out/$j.jar" com.histmersion.spike.cremalazy.Lazies 2>&1 || echo "exit $?"
      done
      echo "== $variant: JDK classes the image does not use"
      "${bin[@]}" lazy "$out/bench.jar" com.histmersion.spike.cremabench.JdkUse 2>&1 || echo "exit $?"
    } 2>&1 | tee "$logs/crema-measure-$variant.log"
    ;;
  server-build)
    # the REAL server image from its relink kit (sbt server/relinkKit): closed (as shipped) or rcl-jit (runtime class
    # loading + the plugin platform preserved + the runtime JIT). Only size, build cost and --self-check start time
    # are measured: the server has no code that loads plugin jars at run time.
    variant="${2:?closed | rcl-jit}"
    kit="$(ls -d "$root"/target/out/jvm/scala-*/histmersion-server/kit | head -1)"
    kcp="$(sed "s#^#$kit/lib/#" "$kit/classpath" | paste -sd: -)"
    args=(); while IFS= read -r a; do [ -n "$a" ] && args+=("$a"); done < "$kit/native-image.args"
    flags=()
    if [ "$variant" = rcl-jit ]; then
      pkgs=(
        com.histmersion com.histmersion.plugin com.histmersion.util.problem com.histmersion.util.json
        com.histmersion.util.nonempty org.ekrich.config org.ekrich.config.impl org.ekrich.config.parser
        hearth.kindlings.sconfigderivation.internal.runtime hearth.kindlings.jsoniterderivation.internal.runtime
        io.github.iltotore.iron 'io.github.iltotore.iron.constraint.*' com.github.plokhotnyuk.jsoniter_scala.core
        scala 'scala.collection.*' 'scala.runtime.*' 'scala.util.*' scala.math 'scala.jdk.*' scala.reflect
        java.lang java.lang.invoke java.lang.reflect java.util 'java.util.concurrent.*' java.util.function
        java.util.regex java.util.stream 'java.time.*' java.io java.math java.text java.nio java.nio.charset
        java.nio.file java.net
      )
      flags=(-H:+UnlockExperimentalVMOptions -H:+RuntimeClassLoading -H:+GraalJITCompileAtRuntime
        "-H:Preserve=$(printf 'package=%s\n' "${pkgs[@]}" | paste -sd, -)"
        -H:ConfigurationFileDirectories="$here/platform-metadata")
    fi
    log="$logs/crema-build-server-$variant.log"
    echo "native-image server ($variant) → $out/server-$variant; log $log"
    start=$(date +%s); rc=0
    /usr/bin/time -l native-image "${args[@]}" "${flags[@]}" -cp "$kcp" "$(tr -d '\n' < "$kit/main-class")" \
      "$out/server-$variant" > "$log" 2>&1 || rc=$?
    echo "exit $rc after $(( $(date +%s) - start )) s"
    grep -E 'Finished generating|Peak RSS|maximum resident|in total image size|Error|error:' "$log" | tail -10 || true
    ls -l "$out/server-$variant" 2>/dev/null || true
    exit "$rc"
    ;;
  server-measure)
    variant="${2:?closed | rcl-jit}"
    tmp="$(mktemp -d "$out/server-home.XXXXXX")"
    # a throwaway home, config path, data dir and XDG dirs: never the live config or data; the first-party plugins
    # that could reach outside the process at registration (Calibre, the AI providers) switched off
    run=(env -i "PATH=/usr/bin:/bin" "HOME=$tmp" "XDG_CONFIG_HOME=$tmp/config" "XDG_CACHE_HOME=$tmp/cache"
      "XDG_DATA_HOME=$tmp/share" "HISTMERSION_CONFIG=$tmp/histmersion.conf" "HISTMERSION_DATA_DIR=$tmp/data"
      HISTMERSION_PLUGIN_CALIBRE_ENABLED=false HISTMERSION_PLUGIN_AI_OPENAI_ENABLED=false
      HISTMERSION_PLUGIN_AI_LOCAL_ENABLED=false HISTMERSION_PLUGIN_AI_ANTHROPIC_ENABLED=false
      "$out/server-$variant" --self-check)
    {
      echo "== server-$variant: size"; ls -l "$out/server-$variant"
      echo "== server-$variant: --self-check ×9"; python3 -I "$here/timeit.py" 9 "${run[@]}"
    } 2>&1 | tee "$logs/crema-measure-server-$variant.log"
    rm -rf "$tmp"
    ;;
  *) sed -n 2,12p "$0"; exit 2 ;;
esac
