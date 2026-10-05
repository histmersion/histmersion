#!/usr/bin/env bash
# Create a local code-signing identity for Histmersion's binaries — free, no Apple Developer account:
#
#   scripts/bundle/codesign-identity-macos.sh [name]        (default name: "Histmersion Local")
#
# Why: the native binaries are otherwise signed ad hoc, and macOS privacy permissions (e.g. access to
# ~/Documents) are tied to an ad-hoc binary's exact hash — every rebuild looks like a new app and the
# permission is lost. Signed with one stable identity, a permission granted once survives every update.
# The identity is trusted for code signing on THIS Mac only; it does not satisfy Gatekeeper for
# copies downloaded elsewhere (that would need Apple's paid program and notarisation).
#
# Idempotent: does nothing if the identity already exists. Creating the trust setting asks for your
# login password once (a macOS dialog). install-macos.sh then signs with it automatically.
set -euo pipefail
name="${1:-Histmersion Local}"
keychain="$HOME/Library/Keychains/login.keychain-db"
openssl=/usr/bin/openssl   # the system LibreSSL writes a PKCS#12 that `security import` reads

if security find-identity -v -p codesigning | grep -qF "\"$name\""; then
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

echo "→ importing it into the login keychain (codesign may use the key without asking)"
security import "$tmp/id.p12" -k "$keychain" -P "$pass" -T /usr/bin/codesign >/dev/null

echo "→ trusting it for code signing on this Mac (macOS asks for your login password)"
security add-trusted-cert -r trustRoot -p codeSign -k "$keychain" "$tmp/cert.pem"

if security find-identity -v -p codesigning | grep -qF "\"$name\""; then
  echo "done: \"$name\" is a valid code-signing identity. The next install signs with it."
else
  echo "the identity was imported but is not valid for code signing — check Keychain Access" >&2
  exit 1
fi
