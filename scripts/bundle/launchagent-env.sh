la_env_from_caller() {
  local dir="$1" v val
  mkdir -p "$dir"
  for v in HISTMERSION_CORPUS_DIR HISTMERSION_ACQUIRE_CONTACT_EMAIL $(compgen -e | grep '^HISTMERSION_AGENTS_' || true); do
    val="$(printenv "$v" || true)"
    [ -n "$val" ] && printf '%s' "$val" > "$dir/$v"
  done
  return 0
}

la_env_carried_name() {
  case "$1" in
    HISTMERSION_*|ANTHROPIC_API_KEY|OPENAI_API_KEY) return 0 ;;
    *) return 1 ;;
  esac
}

la_env_from_plist() {
  local plist="$1" dir="$2" k
  mkdir -p "$dir"
  [ -f "$plist" ] || return 0
  plutil -extract EnvironmentVariables raw -o - "$plist" >/dev/null 2>&1 || return 0
  plutil -extract EnvironmentVariables raw -o - "$plist" | while IFS= read -r k; do
    [ -n "$k" ] || continue
    if la_env_carried_name "$k"; then
      plutil -extract "EnvironmentVariables.$k" raw -n -o - "$plist" > "$dir/$k" 2>/dev/null || rm -f "$dir/$k"
    else
      echo "  dropped  $k (not a Histmersion setting)"
    fi
  done
}

la_env_secret() {
  case "$2" in true|false|on|off|yes|no|'') return 1 ;; esac
  case "$2" in *[!0-9.]*) ;; *) return 1 ;; esac
  case "$1" in
    *KEY*|*TOKEN*|*SECRET*|*PASSWORD*|*PASSWD*|*CREDENTIAL*|*AUTH*|*COOKIE*|*SESSION*) return 0 ;;
  esac
  case "$2" in sk-*|hmrt_*|ghp_*|xox*) return 0 ;; esac
  return 1
}

la_env_show() {
  if la_env_secret "$1" "$2"; then printf '<hidden, %s chars>' "${#2}"; else printf '%s' "$2"; fi
}

la_env_resolve_commands() {
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

la_env_merge() {
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

la_env_apply() {
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

la_env_prepare() {
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
