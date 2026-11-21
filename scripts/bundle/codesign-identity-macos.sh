#!/usr/bin/env bash
set -euo pipefail
trust=""
case "${1:-}" in --trust) trust=1; shift ;; --no-trust) shift ;; esac
name="${1:-Histmersion Local}"
keychain="${HISTMERSION_KEYCHAIN:-$HOME/Library/Keychains/login.keychain-db}"
openssl=/usr/bin/openssl

exists() {
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
