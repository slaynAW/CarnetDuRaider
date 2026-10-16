#!/usr/bin/env bash
# Notes de la Release (notes.md) et sommes de contrôle (dist/SHA256SUMS.txt), pour une version
# officielle comme pour la pré-version de développement. manifeste-mise-a-jour.sh ajoute ensuite les
# nouveautés en tête des notes.
#
# Variables : VERSION, OFFICIAL (true/false).
set -euo pipefail

(cd dist && sha256sum -- * > SHA256SUMS.txt)
blob="${GITHUB_SERVER_URL}/${GITHUB_REPOSITORY}/blob/${GITHUB_SHA}"
{
  echo "## 📱 Installer l'application"
  echo "Depuis le téléphone, téléchargez **CarnetDuRaider-${VERSION}.apk** ci-dessous puis ouvrez-le"
  echo "(autorisez l'installation depuis le navigateur si Android le demande). Android 8 ou plus récent."
  echo
  if [[ "$OFFICIAL" == true ]]; then
    echo "Déjà installée ? L'application propose elle-même cette version (*Réglages → Mises à jour*)."
  else
    echo "> ⚠️ Version de développement : APK signé avec une **clé de test**, pas avec la clé des versions officielles."
    echo "> Il ne s'installe pas par-dessus l'application officielle (il faut d'abord la désinstaller, ce qui efface"
    echo "> la réserve et les objectifs saisis) et ne se met pas à jour tout seul."
    echo "> Voir [docs/SIGNATURE.md](${blob}/docs/SIGNATURE.md)."
  fi
  echo
  echo "## 🔏 Vérifier les fichiers"
  echo "- Sommes de contrôle : \`SHA256SUMS.txt\`. Commit : ${GITHUB_SHA}."
  if [[ "$OFFICIAL" == true ]]; then
    echo "- Provenance (compilé par la CI de ce dépôt, depuis ce commit) : \`gh attestation verify <fichier> --repo ${GITHUB_REPOSITORY}\`."
  fi
  echo "- Mises à jour intégrées : [docs/MISES-A-JOUR.md](${blob}/docs/MISES-A-JOUR.md)."
  echo
  echo "_Projet de fan, non officiel. ARC Raiders est une marque d'Embark Studios AB ; cette application n'est ni affiliée à Embark Studios ni approuvée par Embark Studios._"
} > notes.md
ls -l dist
