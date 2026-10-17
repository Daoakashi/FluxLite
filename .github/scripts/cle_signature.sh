#!/usr/bin/env bash
# Clé de signature de FluxLite : créée une seule fois, puis réutilisée pour toutes les versions
# (Android refuse une mise à jour signée avec une autre clé).
# Elle est rangée chiffrée dans signature/fluxlite.p12.enc, protégée par le secret FLUXLITE_SIGNATURE
# (AES-256, dérivation PBKDF2 600 000 tours) : sans le mot de passe, le fichier est inutilisable.
set -euo pipefail
DESTINATION="$1"
CHIFFREE="signature/fluxlite.p12.enc"

if [ -z "${MOT_DE_PASSE:-}" ]; then
  echo "::error::Le secret FLUXLITE_SIGNATURE n'existe pas. Créez-le : Settings › Secrets and variables › Actions › New repository secret (voir LISEZMOI.md)."
  exit 1
fi
if [ "${#MOT_DE_PASSE}" -lt 16 ]; then
  echo "::error::Le secret FLUXLITE_SIGNATURE doit faire au moins 16 caractères."
  exit 1
fi

if [ ! -f "$CHIFFREE" ]; then
  echo "Première publication : création de la clé de signature de FluxLite."
  mkdir -p "$(dirname "$CHIFFREE")"
  keytool -genkeypair -storetype PKCS12 -keystore "$DESTINATION" -alias fluxlite -keyalg RSA -keysize 4096 \
    -validity 36500 -dname "CN=FluxLite, O=Flux" -storepass "$MOT_DE_PASSE" -keypass "$MOT_DE_PASSE" -noprompt
  openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt -in "$DESTINATION" -out "$CHIFFREE" -pass env:MOT_DE_PASSE
  git config user.name "github-actions[bot]"
  git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
  git add "$CHIFFREE"
  git commit -m "FluxLite : clé de signature (chiffrée)"
  git push
else
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in "$CHIFFREE" -out "$DESTINATION" -pass env:MOT_DE_PASSE \
    || { echo "::error::Impossible de déchiffrer la clé : le secret FLUXLITE_SIGNATURE n'est pas le même que lors de la première publication."; exit 1; }
fi
