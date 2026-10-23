#!/usr/bin/env bash
# Create a local code-signing identity for Histmersion's binaries — free, no Apple Developer account:
#
#   scripts/bundle/codesign-identity-macos.sh [--trust|--no-trust] [name]   (default name: "Histmersion Local")
#   (in a bundle: libexec/codesign-identity-macos.sh, run by `histmersion-signing setup-macos`)
#
# Why: the native binaries are otherwise signed ad hoc, and macOS privacy permissions (e.g. access to
# ~/Documents) are tied to an ad-hoc binary's exact hash — every rebuild looks like a new app and the
# permission is lost. Signed with one stable identity, a permission granted once survives every update.
# It serves THIS Mac only; it does not satisfy Gatekeeper for copies downloaded elsewhere (that would
# need Apple's paid program and notarisation).
#
# Idempotent: does nothing if the identity already exists. install-macos.sh then signs with it
# automatically.
#
# The identity is left UNTRUSTED by default (#3647): codesign signs with it just as well, its designated
# requirement is as stable, and the TCC check (scripts/bundle/tcc-check.sh, 2026-10-23) showed a privacy
# grant to a binary it signed survives a relink (docs/research-plugins.md §21.2) — so no trust setting
# of your login keychain is changed. --trust also trusts it for code signing (asks for your login
# password once, a macOS dialog); --no-trust, the default, is still accepted.
# HISTMERSION_KEYCHAIN puts the identity into another keychain than the login one — the tests use a
# temporary keychain they delete; they never touch the login keychain.
set -euo pipefail
trust=""
case "${1:-}" in --trust) trust=1; shift ;; --no-trust) shift ;; esac
name="${1:-Histmersion Local}"
keychain="${HISTMERSION_KEYCHAIN:-$HOME/Library/Keychains/login.keychain-db}"
openssl=/usr/bin/openssl   # the system LibreSSL writes a PKCS#12 that `security import` reads

exists() { # trusted or not, in that keychain
  security find-identity -p codesigning "$keychain" 2>/dev/null | grep -qF "\"$name\""
}
if exists; then
  echo "code-signing identity \"$name\" already exists — nothing to do"
  exit 0
fi

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cat > "$tmp/cert.cnf" <<CNF
[req]
distinguished_name = dn
x509_extensions = ext
prompt = no
[dn]
CN = $name
[ext]
basicConstraints = critical,CA:false
keyUsage = critical,digitalSignature
extendedKeyUsage = critical,codeSigning
subjectKeyIdentifier = hash
CNF

echo "→ creating a self-signed code-signing certificate \"$name\" (valid 10 years)"
"$openssl" req -x509 -newkey rsa:2048 -sha256 -days 3650 -nodes \
  -keyout "$tmp/key.pem" -out "$tmp/cert.pem" -config "$tmp/cert.cnf" 2>/dev/null
pass="$("$openssl" rand -hex 16)"
"$openssl" pkcs12 -export -inkey "$tmp/key.pem" -in "$tmp/cert.pem" -name "$name" \
  -out "$tmp/id.p12" -passout "pass:$pass"

echo "→ importing it into $keychain (codesign may use the key without asking)"
security import "$tmp/id.p12" -k "$keychain" -P "$pass" -T /usr/bin/codesign >/dev/null

if [ -n "$trust" ]; then
  echo "→ trusting it for code signing on this Mac (macOS asks for your login password)"
  security add-trusted-cert -r trustRoot -p codeSign -k "$keychain" "$tmp/cert.pem"
fi

if exists; then
  echo "done: \"$name\" is a code-signing identity${trust:+ trusted on this Mac}. Installs and relinks sign with it."
else
  echo "the identity was imported but is not a code-signing identity — check Keychain Access" >&2
  exit 1
fi
