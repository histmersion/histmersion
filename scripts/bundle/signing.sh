#!/usr/bin/env bash
# bin/histmersion-signing — OPTIONAL signing of what a relink builds (docs/research-plugins.md §9; D6).
# Nothing here is needed: without it binaries are ad-hoc signed and nothing is verified, as before.
#
#   histmersion-signing status
#   histmersion-signing setup-macos [--name N] [--no-trust]   create a self-signed code-signing identity
#                                                             in YOUR LOGIN KEYCHAIN (asks first; macOS)
#   histmersion-signing use-gpg <key id>                      sign Histmersion's provenance with an
#                                                             existing GPG key (its fingerprint is kept)
#   histmersion-signing generate-gpg [--name N] [--email E]   make a new GPG key for it (gpg asks for the
#                                                             passphrase itself; it is never stored)
#   histmersion-signing off-gpg                               stop signing (the key stays in your keyring)
#
# Used by histmersion-relink, histmersion-server-jvm and `histmersion plugin …`:
#
#   codesign <file> <identifier>      macOS: with the identity if it exists, else ad hoc
#   sign <file>                       a detached ASCII signature <file>.asc with the GPG key
#   verify-file <file> <file.asc>     verify a signature; prints the signer's fingerprint
#   sign-plugins / verify-plugins     <data>/plugins/plugins.manifest (+ .asc): the jars' SHA-256s
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
identity="${HISTMERSION_CODESIGN_IDENTITY:-Histmersion Local}"
gpg_bin="${HISTMERSION_GPG:-gpg}"

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
    ;;

  setup-macos)
    [ "$(hk_os)" = macos ] || hk_die 1 "setup-macos is for macOS; on Linux use GPG provenance"
    name="Histmersion Local" trust=1
    while [ $# -gt 0 ]; do
      case "$1" in --name) name="$2"; shift ;; --no-trust) trust="" ;; *) hk_die 1 "unknown argument '$1'" ;; esac
      shift
    done
    script="$here/codesign-identity-macos.sh"
    [ -x "$script" ] || script="$here/../libexec/codesign-identity-macos.sh"
    echo "This creates a self-signed code-signing certificate \"$name\" in your LOGIN keychain"
    [ -n "$trust" ] && echo "and trusts it for code signing (macOS asks for your login password)."
    printf 'Go on? [y/N] '
    read -r yn
    case "$yn" in y|Y|yes) ;; *) echo "nothing changed"; exit 0 ;; esac
    if [ -n "$trust" ]; then "$script" "$name"; else "$script" --no-trust "$name"; fi
    ;;

  use-gpg)
    key="${1:?usage: histmersion-signing use-gpg <key id>}"
    fpr="$("$gpg_bin" --batch --with-colons --list-secret-keys "$key" 2>/dev/null | awk -F: '/^fpr:/ {print $10; exit}')"
    [ -n "$fpr" ] || hk_die 2 "gpg has no secret key '$key' (gpg --list-secret-keys)"
    mkdir -p "$sigdir"
    printf '%s\n' "$fpr" > "$sigdir/gpg-key"
    echo "GPG provenance on: key $fpr (passphrases stay with gpg-agent; nothing secret is stored)"
    [ -n "$(hk_plugin_jars "$plugins")" ] && "$0" sign-plugins
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
    rm -f "$sigdir/gpg-key"
    echo "GPG provenance off (the key stays in your keyring; plugins.manifest.asc files are left as they are)"
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
    key="$(need_gpg_key)"
    rm -f "$file.asc"
    "$gpg_bin" --batch --yes --armor --local-user "$key" --detach-sign --output "$file.asc" "$file"
    ;;

  verify-file)
    file="${1:?usage: verify-file <file> <file.asc>}" asc="${2:?usage: verify-file <file> <file.asc>}"
    status="$("$gpg_bin" --batch --status-fd 1 --verify "$asc" "$file" 2>/dev/null || true)"
    fpr="$(printf '%s\n' "$status" | awk '/\[GNUPG:\] VALIDSIG/ {print $3; exit}')"
    if [ -n "$fpr" ]; then printf '%s\n' "$fpr"; exit 0; fi
    missing="$(printf '%s\n' "$status" | awk '/\[GNUPG:\] (NO_PUBKEY|ERRSIG)/ {print $3; exit}')"
    if [ -n "$missing" ]; then hk_die 2 "the signer's public key $missing is not in your keyring — import it (gpg --recv-keys $missing) after checking whose it is"; fi
    hk_die 2 "$(basename "$asc") is not a valid signature of $(basename "$file")"
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
    signer="$("$0" verify-file "$m" "$m.asc")" || exit 2
    [ "$signer" = "$key" ] || hk_die 2 "plugins.manifest is signed by $signer, not by the configured key $key"
    [ "$(plugins_manifest)" = "$(cat "$m")" ] \
      || hk_die 2 "the plugins directory differs from its signed manifest: $(diff <(plugins_manifest) "$m" | grep '^[<>]' | head -3 | tr '\n' ' ')"
    echo "plugins directory matches its manifest signed by $signer"
    ;;

  *) hk_die 1 "unknown command '$cmd' (status, setup-macos, use-gpg, generate-gpg, off-gpg, codesign, sign, verify-file, sign-plugins, verify-plugins)" ;;
esac
