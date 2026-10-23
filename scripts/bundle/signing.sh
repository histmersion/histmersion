#!/usr/bin/env bash
# bin/histmersion-signing — OPTIONAL signing of what a relink builds (docs/research-plugins.md §9; D6).
# Nothing here is needed: without it binaries are ad-hoc signed and nothing is verified, as before.
#
#   histmersion-signing status
#   histmersion-signing setup-macos [--name N] [--trust]      create a self-signed code-signing identity
#                                                             in YOUR LOGIN KEYCHAIN (asks first; macOS),
#                                                             untrusted unless --trust (#3647: privacy
#                                                             grants survive relinks without trusting it)
#   histmersion-signing use-gpg <key id> [--no-restart]       sign Histmersion's provenance with an
#                                                             existing GPG key (its fingerprint is kept);
#                                                             signs the installed server binary and points
#                                                             the service at the verifying launcher
#   histmersion-signing generate-gpg [--name N] [--email E]   make a new GPG key for it (gpg asks for the
#                                                             passphrase itself; it is never stored)
#   histmersion-signing off-gpg [--no-restart]                stop signing (the key stays in your keyring);
#                                                             the service starts the binary directly again
#   histmersion-signing sign-binary                           sign bin/histmersion-server's SHA-256 (and
#                                                             .prev's) — after you checked it is yours
#   histmersion-signing verify-binary                         what the launcher checks at each start
#
# Used by histmersion-relink, histmersion-server-jvm and `histmersion plugin …`:
#
#   codesign <file> <identifier>      macOS: with the identity if it exists, else ad hoc
#   sign <file>                       a detached ASCII signature <file>.asc with the GPG key
#   verify-file <file> <file.asc>     verify a signature; prints the signer's fingerprint
#   sign-plugins / verify-plugins     <data>/plugins/plugins.manifest (+ .asc): the jars' SHA-256s
#
# With GPG provenance on, the service starts libexec/histmersion-server-verified, which refuses a server
# binary whose SHA-256 is not the signed one (docs/research-plugins.md §21.3). use-gpg records the gpg
# program and GNUPGHOME it ran with (<data>/signing/gpg-program, gnupg-home), because a LaunchAgent's
# PATH has neither Homebrew's nor MacGPG2's gpg.
#
# The macOS identity: HISTMERSION_CODESIGN_IDENTITY (default "Histmersion Local"; "-" = ad hoc, nothing
# looked up), HISTMERSION_CODESIGN_KEYCHAIN to sign from a keychain other than the search list's.
# The GPG key: <data>/signing/gpg-key (a fingerprint, nothing secret). Passphrases stay with gpg-agent.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
common="$here/../libexec/kit-common.sh"; [ -f "$common" ] || common="$here/kit-common.sh"
# shellcheck source=kit-common.sh
. "$common"

data="$(hk_data_dir)"
sigdir="$data/signing"
plugins="$(hk_plugins_dir)"
install="${HISTMERSION_HOME:-$(cd "$here/.." && pwd)}"
server="$install/bin/histmersion-server"
identity="${HISTMERSION_CODESIGN_IDENTITY:-Histmersion Local}"
gpg_bin="$(hk_gpg_program || echo gpg)"
gpg_home="$(hk_gpg_home)"
[ -z "$gpg_home" ] || export GNUPGHOME="$gpg_home"   # the keyring use-gpg recorded, when not the default

# The identity's SHA-1 when it is in the keychain search list (or the named keychain), trusted or not.
# codesign is given the hash, not the name: by name it looks among VALID (trusted) identities only, and
# an untrusted self-signed one signs just as well (docs/research-plugins.md §9.2).
identity_hash() {
  [ "$identity" != "-" ] || return 1
  command -v security >/dev/null 2>&1 || return 1
  local h
  if [ -n "${HISTMERSION_CODESIGN_KEYCHAIN:-}" ]; then
    h="$(security find-identity -p codesigning "$HISTMERSION_CODESIGN_KEYCHAIN" 2>/dev/null | awk -v n="\"$identity\"" 'index($0, n) {print $2; exit}')"
  else
    h="$(security find-identity -p codesigning 2>/dev/null | awk -v n="\"$identity\"" 'index($0, n) {print $2; exit}')"
  fi
  [ -n "$h" ] && printf '%s\n' "$h"
}
have_identity() { identity_hash >/dev/null; }

gpg_key() { hk_gpg_key; }
need_gpg_key() {
  local k; k="$(gpg_key)"
  [ -n "$k" ] || hk_die 2 "no GPG key set up (histmersion-signing use-gpg <key id>)"
  printf '%s\n' "$k"
}

# A detached ASCII signature of <file> into <out> with the configured key.
gpg_sign() { # <file> <out>
  local key; key="$(need_gpg_key)"
  rm -f "$2"
  "$gpg_bin" --batch --yes --armor --local-user "$key" --detach-sign --output "$2" "$1"
}

# The binary, its checksum record and that record's signature — the installed one, then .prev (the
# names relink.sh's records rotate to).
binary_triples() {
  printf '%s\n' "$server|$server.sha256|$server.sha256.asc" "$server.prev|$server.sha256.prev|$server.sha256.asc.prev"
}

# The manifest of the plugins directory: `<sha256>  <file>` per jar, sorted (SHA256SUMS format).
plugins_manifest() {
  local j
  hk_plugin_jars "$plugins" | while IFS= read -r j; do
    printf '%s  %s\n' "$(hk_sha256 "$j")" "$(basename "$j")"
  done
}

cmd="${1:-status}"; [ $# -gt 0 ] && shift
case "$cmd" in
  status)
    if [ "$(hk_os)" = macos ]; then
      if [ "$identity" = "-" ]; then echo "- code signing: ad hoc (HISTMERSION_CODESIGN_IDENTITY=-)"
      elif have_identity; then echo "- code signing: \"$identity\" (a stable identity: macOS permissions survive relinks)"
      else echo "- code signing: ad hoc — no \"$identity\" identity (histmersion-signing setup-macos creates one)"; fi
    else
      echo "- code signing: none (Linux has no OS code identity; GPG provenance below)"
    fi
    if [ -n "$(gpg_key)" ]; then echo "- GPG provenance: key $(gpg_key) — plugin sets and binaries are signed and verified"
    else echo "- GPG provenance: off (histmersion-signing use-gpg <key id> turns it on)"; fi
    prog="$(hk_service_current_program)"
    case "$prog" in
      "") ;;
      */libexec/histmersion-server-verified) echo "- service start: through histmersion-server-verified (the binary's signed SHA-256 is checked at each start)" ;;
      *) if [ -n "$(gpg_key)" ]; then echo "- service start: $prog directly — NOT verified at start (histmersion-signing use-gpg $(gpg_key) sets it up)"
         else echo "- service start: $prog directly"; fi ;;
    esac
    ;;

  setup-macos)
    [ "$(hk_os)" = macos ] || hk_die 1 "setup-macos is for macOS; on Linux use GPG provenance"
    name="Histmersion Local" trust=""
    while [ $# -gt 0 ]; do
      case "$1" in --name) name="$2"; shift ;; --trust) trust=1 ;; --no-trust) trust="" ;; *) hk_die 1 "unknown argument '$1'" ;; esac
      shift
    done
    script="$here/codesign-identity-macos.sh"
    [ -x "$script" ] || script="$here/../libexec/codesign-identity-macos.sh"
    echo "This creates a self-signed code-signing certificate \"$name\" in your LOGIN keychain"
    if [ -n "$trust" ]; then echo "and trusts it for code signing (macOS asks for your login password)."
    else echo "(left untrusted: your keychain's trust settings are not changed; privacy permissions still survive relinks)"; fi
    printf 'Go on? [y/N] '
    read -r yn
    case "$yn" in y|Y|yes) ;; *) echo "nothing changed"; exit 0 ;; esac
    if [ -n "$trust" ]; then "$script" --trust "$name"; else "$script" --no-trust "$name"; fi
    ;;

  use-gpg)
    key="" restart=1
    while [ $# -gt 0 ]; do
      case "$1" in --no-restart) restart="" ;; -*) hk_die 1 "unknown argument '$1'" ;; *) key="$1" ;; esac
      shift
    done
    [ -n "$key" ] || hk_die 1 "usage: histmersion-signing use-gpg <key id> [--no-restart]"
    fpr="$("$gpg_bin" --batch --with-colons --list-secret-keys "$key" 2>/dev/null | awk -F: '/^fpr:/ {print $10; exit}')"
    [ -n "$fpr" ] || hk_die 2 "gpg has no secret key '$key' (gpg --list-secret-keys)"
    mkdir -p "$sigdir"
    printf '%s\n' "$fpr" > "$sigdir/gpg-key"
    # what the service's launcher needs to find the same gpg and keyring outside this shell
    gpath="$(command -v "$gpg_bin" 2>/dev/null || true)"
    case "$gpath" in /*) printf '%s\n' "$gpath" > "$sigdir/gpg-program" ;; *) rm -f "$sigdir/gpg-program" ;; esac
    if [ -n "${GNUPGHOME:-}" ]; then printf '%s\n' "$GNUPGHOME" > "$sigdir/gnupg-home"; else rm -f "$sigdir/gnupg-home"; fi
    echo "GPG provenance on: key $fpr (passphrases stay with gpg-agent; nothing secret is stored)"
    if [ -n "$(hk_plugin_jars "$plugins")" ]; then "$0" sign-plugins; fi
    if [ -x "$server" ]; then
      "$0" sign-binary
      hk_service_repoint "$install" "$(hk_service_program "$install")" "$restart"
    fi
    ;;

  generate-gpg)
    name="Histmersion $(id -un)" email="$(id -un)@localhost"
    while [ $# -gt 0 ]; do
      case "$1" in --name) name="$2"; shift ;; --email) email="$2"; shift ;; *) hk_die 1 "unknown argument '$1'" ;; esac
      shift
    done
    "$gpg_bin" --quick-generate-key "$name <$email>" ed25519 sign 10y
    fpr="$("$gpg_bin" --batch --with-colons --list-secret-keys "$name <$email>" | awk -F: '/^fpr:/ {print $10; exit}')"
    "$0" use-gpg "$fpr"
    ;;

  off-gpg)
    restart=1
    while [ $# -gt 0 ]; do
      case "$1" in --no-restart) restart="" ;; *) hk_die 1 "unknown argument '$1'" ;; esac
      shift
    done
    rm -f "$sigdir/gpg-key" "$sigdir/gpg-program" "$sigdir/gnupg-home"
    echo "GPG provenance off (the key stays in your keyring; plugins.manifest.asc files are left as they are)"
    if [ -x "$server" ]; then hk_service_repoint "$install" "$(hk_service_program "$install")" "$restart"; fi
    ;;

  sign-binary)
    key="$(need_gpg_key)"
    while IFS='|' read -r b sums asc; do
      [ -x "$b" ] || continue
      if hk_verify_binary "$b" "$sums" "$asc" "$key" >/dev/null; then
        echo "$(basename "$b"): its signed checksum is current"; continue
      fi
      printf '%s  histmersion-server\n' "$(hk_sha256 "$b")" > "$sums"
      gpg_sign "$sums" "$asc"
      echo "$(basename "$b"): SHA-256 signed with $key"
    done < <(binary_triples)
    ;;

  verify-binary)
    key="$(need_gpg_key)"
    rc=0
    while IFS='|' read -r b sums asc; do
      [ -e "$b" ] || continue
      if out="$(hk_verify_binary "$b" "$sums" "$asc" "$key")"; then echo "$out"; else echo "NOT VERIFIED: $out"; [ "$b" = "$server" ] && rc=2; fi
    done < <(binary_triples)
    exit "$rc"
    ;;

  codesign)
    file="${1:?usage: codesign <file> <identifier>}" ident="${2:?usage: codesign <file> <identifier>}"
    [ "$(hk_os)" = macos ] || { echo "code signing: none on Linux"; exit 0; }
    if h="$(identity_hash)"; then
      codesign --force --sign "$h" ${HISTMERSION_CODESIGN_KEYCHAIN:+--keychain "$HISTMERSION_CODESIGN_KEYCHAIN"} \
        --identifier "$ident" "$file"
      echo "signed as \"$identity\""
    else
      codesign --force --sign - --identifier "$ident" "$file"
      if [ "$identity" = "-" ]; then echo "signed ad hoc"
      else echo "signed ad hoc (no \"$identity\" identity: macOS may ask for folder permissions again)"; fi
    fi
    ;;

  sign)
    file="${1:?usage: sign <file>}"
    gpg_sign "$file" "$file.asc"
    ;;

  verify-file)
    file="${1:?usage: verify-file <file> <file.asc>}" asc="${2:?usage: verify-file <file> <file.asc>}"
    # the SIGNING key's fingerprint, as before (a plugin's pinned signer in index.json is that one)
    if fpr="$(hk_gpg_verify "$file" "$asc" 2>"${TMPDIR:-/tmp}/hm-verify.$$")"; then rm -f "${TMPDIR:-/tmp}/hm-verify.$$"; printf '%s\n' "${fpr%% *}"; exit 0; fi
    why="$(cat "${TMPDIR:-/tmp}/hm-verify.$$")"; rm -f "${TMPDIR:-/tmp}/hm-verify.$$"
    case "$why" in
      *"is not in the keyring"*) missing="$(printf '%s' "$why" | awk '{print $5}')"
        hk_die 2 "the signer's public key $missing is not in your keyring — import it (gpg --recv-keys $missing) after checking whose it is" ;;
    esac
    hk_die 2 "$why"
    ;;

  sign-plugins)
    key="$(need_gpg_key)"
    mkdir -p "$plugins"
    plugins_manifest > "$plugins/plugins.manifest"
    rm -f "$plugins/plugins.manifest.asc"
    "$gpg_bin" --batch --yes --armor --local-user "$key" --detach-sign --output "$plugins/plugins.manifest.asc" "$plugins/plugins.manifest"
    echo "plugins manifest signed with $key ($(grep -c . "$plugins/plugins.manifest" || true) jar(s))"
    ;;

  verify-plugins)
    key="$(need_gpg_key)"
    m="$plugins/plugins.manifest"
    [ -f "$m" ] && [ -f "$m.asc" ] || hk_die 2 "no signed manifest in $plugins (histmersion-signing sign-plugins)"
    "$0" verify-file "$m" "$m.asc" >/dev/null || exit 2
    # the configured key is the primary's fingerprint: a key that signs with a subkey matches too
    signers="$(hk_gpg_verify "$m" "$m.asc" 2>/dev/null)" || exit 2
    case " $signers " in *" $key "*) signer="$key" ;; *) hk_die 2 "plugins.manifest is signed by ${signers##* }, not by the configured key $key" ;; esac
    [ "$(plugins_manifest)" = "$(cat "$m")" ] \
      || hk_die 2 "the plugins directory differs from its signed manifest: $(diff <(plugins_manifest) "$m" | grep '^[<>]' | head -3 | tr '\n' ' ')"
    echo "plugins directory matches its manifest signed by $signer"
    ;;

  *) hk_die 1 "unknown command '$cmd' (status, setup-macos, use-gpg, generate-gpg, off-gpg, sign-binary, verify-binary, codesign, sign, verify-file, sign-plugins, verify-plugins)" ;;
esac
