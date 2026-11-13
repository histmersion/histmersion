#!/bin/bash
# compile test configs of the given projects (all ...), then auto-fix imports/getMessage; summary
cd /Users/dev/Workspaces/GitHub/histmersion-wt/contracts3-scalafix
export JAVA_HOME=$HOME/.sdkman/candidates/java/25.4.4.1+1-graalce PATH=$HOME/.sdkman/candidates/java/25.4.4.1+1-graalce/bin:$PATH
tag=$1; shift
L=refactor-audit/concept-table/compile-g62-$tag.log
cmd="all"; for p in "$@"; do cmd="$cmd $p"; done
timeout 2400 sbt --server -batch -J-Xss16m -J-Xmx6g "set every scalafmtOnCompile := false" "set every maxErrors := 100000" "$cmd" > $L 2>&1
python3 refactor-audit/concept-table/scratch/tools/fixmsg.py $L >/dev/null
python3 refactor-audit/concept-table/scratch/tools/autoimp.py $L | wc -l
tail -2 $L | cut -c1-150
sed 's/\x1b\[[0-9;]*m//g' $L | rg -o '^\[error\] -- .*?modules/[^:]+' | sed 's/.*modules\///' | sort | uniq -c | sort -rn | head -40
