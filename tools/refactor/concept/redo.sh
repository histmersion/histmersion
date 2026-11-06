#!/usr/bin/env bash
# Rebuilds the pilot's modules from the commit before the first apply: table, mechanical repairs, values fixes.
set -e
git restore --source=4326e88bd --staged --worktree -- modules
python3 -I tools/refactor/concept/apply.py --write | cut -c1-240
git add -A modules
for n in 1 2; do python3 -I tools/refactor/concept/repair.py refactor-audit/concept-table/compile-$n.log; done
git add -A modules
git apply --3way refactor-audit/concept-table/values-agent.patch 2>&1 | tail -1 | cut -c1-120
git add -A modules tools/refactor/concept refactor-audit/concept-table/values-agent.patch
git commit -qm "$1"
git log --oneline -1
