#!/usr/bin/env bash
# Manifeste de mise à jour : dist/update.json (version, nouveautés, taille et empreinte de l'APK) et
# sa signature dist/update.json.sig (clé de signature de l'APK, RSA / SHA-256, base64). L'appli le
# lit à l'adresse …/releases/latest/download/update.json et refuse tout manifeste dont la signature
# ne correspond pas au certificat de l'appli installée (docs/MISES-A-JOUR.md).
#
# Signé et joint aux versions officielles seulement (l'appli ne lit que la dernière d'entre elles) :
# la clé n'est remise qu'au job « Publication officielle », après validation.
#
# Variables : VERSION, OFFICIAL (true/false), CDR_VERSION_CODE ; pour une version officielle,
# KEYSTORE_BASE64 et KEYSTORE_PASSWORD.
set -euo pipefail

base_version="${VERSION%%-*}"
# Section « ## X.Y.Z » de NOUVEAUTES.md, sans lignes vides au début.
notes=$(awk -v title="## $base_version" '$0 == title { found = 1; next } /^## / { if (found) exit } found' NOUVEAUTES.md | sed '/./,$!d')
if [[ -z "$notes" ]]; then
  if [[ "$OFFICIAL" == true ]]; then
    echo "::error title=Nouveautés::NOUVEAUTES.md ne contient pas de section « ## $base_version »."
    exit 1
  fi
  notes="Version de développement."
fi

# Nouveautés en tête des notes de la Release.
{ echo "## ✨ Nouveautés"; echo; echo "$notes"; echo; cat notes.md; } > notes.tmp && mv notes.tmp notes.md

apk="dist/CarnetDuRaider-${VERSION}.apk"
if [[ ! -f "$apk" ]]; then
  echo "::error title=Mises à jour::$apk introuvable."
  exit 1
fi
files=$(jq -n --arg n "$(basename "$apk")" --argjson s "$(stat -c %s "$apk")" --arg h "$(sha256sum "$apk" | cut -d' ' -f1)" \
  '[{platform: "android", name: $n, size: $s, sha256: $h}]')

jq -n --arg v "$VERSION" --argjson code "$CDR_VERSION_CODE" --arg d "$(date -u +%F)" --arg notes "$notes" --argjson files "$files" \
  '{format: 1, version: $v, code: $code, date: $d, notes: $notes, files: $files}' > update.json
cat update.json

if [[ "$OFFICIAL" != true ]]; then
  echo "Version de développement : manifeste ni signé ni joint."
  exit 0
fi
if [[ -z "${KEYSTORE_BASE64:-}" || -z "${KEYSTORE_PASSWORD:-}" ]]; then
  echo "::error title=Mises à jour::Secrets de signature absents : impossible de signer le manifeste de mise à jour."
  exit 1
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
printf '%s' "$KEYSTORE_BASE64" | tr -d ' \t\r\n' | base64 -d > "$work/release.p12"
openssl pkcs12 -in "$work/release.p12" -nocerts -nodes -passin env:KEYSTORE_PASSWORD -out "$work/key.pem" 2>/dev/null
openssl pkcs12 -in "$work/release.p12" -clcerts -nokeys -passin env:KEYSTORE_PASSWORD 2>/dev/null | openssl x509 -out "$work/cert.pem"
openssl dgst -sha256 -sign "$work/key.pem" -out "$work/update.sig" update.json
# Contrôle avec le certificat, comme le fera l'appli.
openssl dgst -sha256 -verify <(openssl x509 -in "$work/cert.pem" -pubkey -noout) -signature "$work/update.sig" update.json

cp update.json dist/update.json
base64 -w0 "$work/update.sig" > dist/update.json.sig
