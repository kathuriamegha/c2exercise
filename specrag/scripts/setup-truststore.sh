#!/usr/bin/env bash
# SPEC-002 environment setup — see Drift Log #1.
#
# On a network with TLS inspection (Netskope, Zscaler, etc.) the JDK cannot verify
# hosts that the proxy re-signs, because Temurin ships its own cacerts and does not
# consult the macOS keychain. DJL's model host is re-signed here, so model download
# fails with PKIX "unable to find valid certification path".
#
# This builds a local truststore = the JDK's cacerts + whatever root the proxy is
# presenting for the DJL host. It contains only public certificates (no private keys)
# and is written to .local/, which is gitignored.
#
# Re-run this if the proxy's CA rotates.
set -euo pipefail

HOST="${1:-mlrepo.djl.ai}"
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/.local"
OUT="$OUT_DIR/truststore.jks"
PASS="changeit"

: "${JAVA_HOME:?JAVA_HOME must point at the JDK you build with}"

mkdir -p "$OUT_DIR"
rm -f "$OUT"

echo "==> Seeding from ${JAVA_HOME}/lib/security/cacerts"
cp "$JAVA_HOME/lib/security/cacerts" "$OUT"
chmod u+w "$OUT"

echo "==> Fetching certificate chain presented for $HOST"
CHAIN="$(mktemp)"
trap 'rm -f "$CHAIN"' EXIT
echo | openssl s_client -connect "$HOST:443" -servername "$HOST" -showcerts 2>/dev/null >"$CHAIN"

# Split the chain and import every CA in it. Importing the leaf too is harmless and
# keeps this working if the proxy presents an incomplete chain.
COUNT=0
awk 'BEGIN{n=0} /-----BEGIN CERTIFICATE-----/{n++} n>0{print > ("'"$CHAIN"'.part" n)} /-----END CERTIFICATE-----/{}' "$CHAIN"
for part in "$CHAIN".part*; do
  [ -f "$part" ] || continue
  SUBJ="$(openssl x509 -in "$part" -noout -subject 2>/dev/null || true)"
  [ -n "$SUBJ" ] || continue
  COUNT=$((COUNT + 1))
  "$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts \
    -alias "proxy-ca-$COUNT" -file "$part" -keystore "$OUT" -storepass "$PASS" >/dev/null
  echo "    imported [$COUNT] $SUBJ"
  rm -f "$part"
done

if [ "$COUNT" -eq 0 ]; then
  echo "!! No certificates recovered for $HOST — is the host reachable?" >&2
  exit 1
fi

echo "==> Wrote $OUT ($COUNT certificate(s) added)"
echo "    Use with: -Djavax.net.ssl.trustStore=$OUT -Djavax.net.ssl.trustStorePassword=$PASS"
