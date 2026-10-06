# The LaunchAgent's EnvironmentVariables across installs (#914) — sourced by install-macos.sh and by
# scripts/bundle/tests/launchagent-env-test.sh. Bash 3.2 (macOS's /bin/bash): no associative arrays,
# so a set of variables is a directory with one file per name, holding the value (newline-safe).
#
# An install used to write the plist from the caller's environment alone, so every setting added by
# hand, or by an earlier install whose caller had exported it, was lost. Now:
#   1. every entry of the installed plist that the server reads (HISTMERSION_*, ANTHROPIC_API_KEY,
#      OPENAI_API_KEY) is carried over; anything else in it is reported and dropped;
#   2. the caller's HISTMERSION_CORPUS_DIR, HISTMERSION_ACQUIRE_CONTACT_EMAIL and HISTMERSION_AGENTS_*
#      (non-empty) override it;
#   3. HISTMERSION_AGENTS_{CLAUDE,CODEX,OPENCODE}_COMMAND, when neither gives one, is the absolute
#      path of `claude`/`codex`/`opencode` on the installer's PATH (a LaunchAgent's PATH lacks
#      /opt/homebrew/bin, ~/.local/bin, …);
#   4. what was kept, set, changed and dropped is printed; a secret-looking value never is.
# To drop a carried setting, remove it from the installed plist first:
#   plutil -remove EnvironmentVariables.<NAME> ~/Library/LaunchAgents/com.histmersion.server.plist

# The caller's variables that reach the server (besides the carried ones).
la_env_from_caller() { # <dir>
  local dir="$1" v val
  mkdir -p "$dir"
  for v in HISTMERSION_CORPUS_DIR HISTMERSION_ACQUIRE_CONTACT_EMAIL $(compgen -e | grep '^HISTMERSION_AGENTS_' || true); do
    val="$(printenv "$v" || true)"
    [ -n "$val" ] && printf '%s' "$val" > "$dir/$v"
  done
  return 0
}

# Whether a variable of the installed plist is one the server reads, and so is carried over.
la_env_carried_name() { # <name>
  case "$1" in
    HISTMERSION_*|ANTHROPIC_API_KEY|OPENAI_API_KEY) return 0 ;;
    *) return 1 ;;
  esac
}

# The installed plist's EnvironmentVariables, carried ones into <dir>; the others are named on stdout.
# A missing plist, or one without the dict, carries nothing.
la_env_from_plist() { # <plist> <dir>
  local plist="$1" dir="$2" k
  mkdir -p "$dir"
  [ -f "$plist" ] || return 0
  plutil -extract EnvironmentVariables raw -o - "$plist" >/dev/null 2>&1 || return 0
  # a dict's `raw` is its keys, one per line; variable names hold no dot, so a key path is safe
  plutil -extract EnvironmentVariables raw -o - "$plist" | while IFS= read -r k; do
    [ -n "$k" ] || continue
    if la_env_carried_name "$k"; then
      plutil -extract "EnvironmentVariables.$k" raw -n -o - "$plist" > "$dir/$k" 2>/dev/null || rm -f "$dir/$k"
    else
      echo "  dropped  $k (not a Histmersion setting)"
    fi
  done
}

# A name or value that looks like a credential. A boolean or a number is never one
# (HISTMERSION_AGENTS_CLAUDE_USE_API_KEY=false is a switch, not a key).
la_env_secret() { # <name> <value>
  case "$2" in true|false|on|off|yes|no|'') return 1 ;; esac
  case "$2" in *[!0-9.]*) ;; *) return 1 ;; esac
  case "$1" in
    *KEY*|*TOKEN*|*SECRET*|*PASSWORD*|*PASSWD*|*CREDENTIAL*|*AUTH*|*COOKIE*|*SESSION*) return 0 ;;
  esac
  case "$2" in sk-*|hmrt_*|ghp_*|xox*) return 0 ;; esac
  return 1
}

# A value as it may be printed.
la_env_show() { # <name> <value>
  if la_env_secret "$1" "$2"; then printf '<hidden, %s chars>' "${#2}"; else printf '%s' "$2"; fi
}

# Resolve the agent CLIs on the installer's PATH for the commands neither side gives.
la_env_resolve_commands() { # <dir with the merged set>
  local dir="$1" bin var path
  for bin in claude codex opencode; do
    var="HISTMERSION_AGENTS_$(printf '%s' "$bin" | tr '[:lower:]' '[:upper:]')_COMMAND"
    [ -f "$dir/$var" ] && continue
    path="$(command -v "$bin" 2>/dev/null || true)"
    case "$path" in
      /*) printf '%s' "$path" > "$dir/$var"; echo "  resolved $var=$path (from your PATH)" ;;
    esac
  done
}

# old + caller (caller wins) → <out>, saying what happened to each name.
la_env_merge() { # <old dir> <caller dir> <out dir>
  local old="$1" new="$2" out="$3" f k o n
  mkdir -p "$out"
  for f in "$old"/* "$new"/*; do
    [ -f "$f" ] || continue
    k="$(basename "$f")"
    [ -f "$out/$k" ] && continue
    if [ -f "$new/$k" ]; then
      n="$(cat "$new/$k")"
      if [ -f "$old/$k" ]; then
        o="$(cat "$old/$k")"
        if [ "$o" = "$n" ]; then echo "  kept     $k=$(la_env_show "$k" "$n")"
        else echo "  changed  $k: $(la_env_show "$k" "$o") → $(la_env_show "$k" "$n") (your environment)"; fi
      else
        echo "  set      $k=$(la_env_show "$k" "$n") (your environment)"
      fi
      cp "$new/$k" "$out/$k"
    else
      echo "  kept     $k=$(la_env_show "$k" "$(cat "$old/$k")") (installed plist)"
      cp "$old/$k" "$out/$k"
    fi
  done
}

# Write <dir>'s variables into <plist>'s EnvironmentVariables (replacing the dict). plutil does the
# escaping: a value with & < > or quotes stays exactly what it was.
la_env_apply() { # <dir> <plist>
  local dir="$1" plist="$2" f
  plutil -remove EnvironmentVariables "$plist" >/dev/null 2>&1 || true
  set -- "$dir"/*
  [ -f "$1" ] || return 0
  plutil -insert EnvironmentVariables -dictionary "$plist"
  for f in "$dir"/*; do
    plutil -insert "EnvironmentVariables.$(basename "$f")" -string "$(cat "$f")" "$plist"
  done
  plutil -lint -s "$plist"
}

# The whole step: read <installed plist> (before it is overwritten), merge the caller's environment,
# resolve the CLIs, and leave the result in <out dir> for la_env_apply.
la_env_prepare() { # <installed plist> <work dir> <out dir>
  local plist="$1" work="$2" out="$3"
  rm -rf "$work/old" "$work/new" "$out"
  echo "→ LaunchAgent environment"
  la_env_from_plist "$plist" "$work/old"
  la_env_from_caller "$work/new"
  la_env_merge "$work/old" "$work/new" "$out"
  la_env_resolve_commands "$out"
  set -- "$out"/*
  [ -f "$1" ] || echo "  (none)"
  return 0
}
