#!/usr/bin/env bash
set -uo pipefail

quiet="${HISTMERSION_WATCHDOG_QUIET_SECONDS:-300}"
poll="${HISTMERSION_WATCHDOG_POLL_SECONDS:-5}"
dumps="${HISTMERSION_THREAD_DUMPS:-${RUNNER_TEMP:-/tmp}/thread-dumps}"
jstack="${JAVA_HOME:+$JAVA_HOME/bin/}jstack"

work="$(mktemp -d)"
log="$work/output"
rcfile="$work/rc"
: > "$log"

dump() {
  mkdir -p "$dumps"
  local stamp
  stamp="$(date -u +%Y%m%dT%H%M%SZ)"
  echo "watchdog: no output for ${quiet}s from: $*; thread dumps of every java process into $dumps ($stamp)"
  ps -eo pid,ppid,etime,rss,args > "$dumps/$stamp-ps.txt" 2>&1
  local pid
  for pid in $(pgrep -x java); do
    "$jstack" -l "$pid" > "$dumps/$stamp-java-$pid.txt" 2>&1 || echo "watchdog: jstack $pid failed" >> "$dumps/$stamp-ps.txt"
  done
}

( "$@" 2>&1; echo $? > "$rcfile" ) | tee "$log" &
pipeline=$!

size=0
still=0
while kill -0 "$pipeline" 2>/dev/null; do
  sleep "$poll"
  now="$(wc -c < "$log")"
  if [ "$now" != "$size" ]; then
    size="$now"
    still=0
  else
    still=$((still + poll))
    if [ "$still" -ge "$quiet" ]; then
      dump "$@"
      still=0
    fi
  fi
done
wait "$pipeline"
rc="$(cat "$rcfile" 2>/dev/null || echo 1)"
rm -rf "$work"
exit "$rc"
