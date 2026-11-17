#!/usr/bin/env bash
# PreToolUse hook (Bash|Monitor): refuse ad-hoc scripted file edits. Hand edits go through the Edit/Write tools, which
# fail unless the old text matches exactly; mechanical multi-file rewrites are a tool committed to the repository
# (e.g. histmersion's tools/refactor/), reviewed and re-runnable, never a throwaway sed/perl/python one-liner.
# (User, 2026-11-17: rules must live in config, not in memory, because memory is forgotten mid-session.)
# Denies:
#   A. in-place editors: sed -i / --in-place, perl|ruby -i (-pi, -i.bak), awk -i inplace;
#   B. inline interpreter code (python, node, ruby, perl) that writes files (open(..,'w'), write_text, os.replace, ...);
#   C. A or B inside an UNTRACKED script the command runs (.py .sh .rb .pl .js); git-tracked scripts are allowed;
#   D. shell redirection or tee into a source/config/doc file (.scala .sbt .java .py .md .tsv .json .conf ...)
#      outside the temp directories (/tmp, /private/tmp, /var/folders): logs and scratch output stay allowed.
set -u
input=$(cat)
cwd=$(printf '%s' "$input" | jq -r '.cwd // empty' 2>/dev/null)
text=$(printf '%s' "$input" | jq -r '.tool_input | .. | strings' 2>/dev/null)

# C: add the contents of untracked scripts the command names.
scripts=$(printf '%s\n' "$text" | grep -oE '[^[:space:]"'\''<>|;&()]+\.(py|sh|rb|pl|js)\b' | sort -u | head -20)
while IFS= read -r f; do
  [ -n "$f" ] || continue
  f="${f/#\~/$HOME}"
  case "$f" in /*) ;; *) [ -n "$cwd" ] && f="$cwd/$f" ;; esac
  case "$f" in */.claude/hooks/deny-*.sh) continue ;; esac
  [ -f "$f" ] && [ "$(wc -c <"$f")" -lt 1048576 ] || continue
  if git -C "$(dirname "$f")" ls-files --error-unmatch "$(basename "$f")" >/dev/null 2>&1; then continue; fi
  text="$text"$'\n'"$(cat "$f")"
done <<<"$scripts"

flat=$(printf '%s' "$text" | tr '\n' ' ')
has() { printf '%s' "$flat" | grep -qE "$1"; }
reason=""
if has '(^|[^[:alnum:]_.-])g?sed([[:space:]][^|;&]*)?[[:space:]](-[A-Za-z]*i[^[:space:]]*|--in-place)'; then
  reason="sed -i edits files in place"
elif has '(^|[^[:alnum:]_.-])(perl|ruby)([[:space:]][^|;&]*)?[[:space:]]-[a-hj-zA-Z]*i'; then
  reason="perl/ruby -i edits files in place"
elif has '(^|[^[:alnum:]_.-])g?awk[[:space:]][^|;&]*-i[[:space:]]*inplace'; then
  reason="awk -i inplace edits files in place"
elif has '(^|[^[:alnum:]_.-])(python3?|node|ruby|perl)([[:space:]]|$)' &&
     has "open\([^)]*,[[:space:]]*(mode[[:space:]]*=[[:space:]]*)?['\"][wax]|\.open\([[:space:]]*['\"][wax]|\.write_(text|bytes)\(|os\.(replace|rename)\(|shutil\.(copy|copyfile|copy2|move)\(|fileinput|writeFileSync|File\.write|IO\.write"; then
  reason="an inline or untracked script writes files"
else
  targets=$(printf '%s' "$flat" | grep -oE '(>>?|tee([[:space:]]+-a)?)[[:space:]]*"?[^[:space:]|;&<>"]+' |
            sed -E 's/^(>>?|tee([[:space:]]+-a)?)[[:space:]]*"?//')
  while IFS= read -r t; do
    [ -n "$t" ] || continue
    case "$t" in /tmp/*|/private/tmp/*|/var/folders/*|/dev/*|\&*) continue ;; esac
    if printf '%s' "$t" | grep -qE '\.(scala|sbt|java|kt|py|js|mjs|ts|sql|conf|hocon|properties|toml|md|tsv|csv|json|ya?ml|html|css|sh)$'; then
      reason="shell output is redirected into $t"; break
    fi
  done <<<"$targets"
fi

if [ -n "$reason" ]; then
  msg="Blocked: $reason. Edit files with the Edit tool (exact match, one site) or the Write tool (a whole new file). A mechanical multi-file rewrite must be a tool committed to the repository (e.g. histmersion tools/refactor/), reviewed and re-runnable, never a throwaway sed/perl/python edit. Scratch output may go to /tmp, the scratchpad, or a .log/.txt file."
  jq -n --arg m "$msg" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$m}}'
fi
exit 0
