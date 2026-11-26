#!/usr/bin/env bash
# Re-signs the release APK with the apps' own key, with "key rotation" so it still
# installs over versions signed with the old debug key (Android 9 and later use the new
# key; older Android versions keep checking the old one).
#
# The new key is kept encrypted in signing/release.p12.enc. The password is the
# SIGNING_PASSWORD repository secret, chosen by the owner and never stored anywhere else.
# The first build with the secret makes the key and encrypts it; the workflow then
# commits the encrypted file back to the repo. Without the secret, the APK keeps the old key.
#
# Usage: sign.sh <input.apk> <output.apk>
set -euo pipefail
IN="$1"; OUT="$2"

if [ -z "${SIGNING_PASSWORD:-}" ]; then
  echo "::warning::SIGNING_PASSWORD secret not set: keeping the old debug-key signature"
  cp "$IN" "$OUT"
  exit 0
fi

BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT

if [ ! -f signing/release.p12.enc ]; then
  echo "No key yet: making the apps' signing key"
  mkdir -p signing
  keytool -genkeypair -keystore "$WORK/release.p12" -storetype PKCS12 -alias release \
    -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=minisonmars apps, O=minisonmars, C=GB" \
    -storepass:env SIGNING_PASSWORD -keypass:env SIGNING_PASSWORD
  openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -in "$WORK/release.p12" \
    -out signing/release.p12.enc -pass env:SIGNING_PASSWORD
  rm "$WORK/release.p12"
fi

openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in signing/release.p12.enc \
  -out "$WORK/release.p12" -pass env:SIGNING_PASSWORD

OLD=(--ks app/debug.keystore --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android)
NEW=(--ks "$WORK/release.p12" --ks-type PKCS12 --ks-pass env:SIGNING_PASSWORD --ks-key-alias release --key-pass env:SIGNING_PASSWORD)

# Proof that the old key hands over to the new one
"$BT/apksigner" rotate --out "$WORK/lineage" --old-signer "${OLD[@]}" --new-signer "${NEW[@]}"

"$BT/apksigner" sign "${OLD[@]}" --next-signer "${NEW[@]}" \
  --lineage "$WORK/lineage" --rotation-min-sdk-version 28 --out "$OUT" "$IN"

"$BT/apksigner" verify --verbose --print-certs "$OUT" | grep -E "Verified using|Signer|certificate DN" || true
