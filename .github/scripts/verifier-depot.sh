#!/usr/bin/env bash
# Règles de sécurité du dépôt, vérifiées à chaque build (job « Règles de sécurité », docs/SECURITE.md).
# Échoue au moindre écart :
# - chaque action est épinglée par l'empreinte complète d'un commit (40 caractères), jamais par un tag ;
# - chaque workflow déclare ses permissions au niveau du fichier, en lecture seule (contents: read) ;
# - aucun déclencheur qui exécute du code d'une pull request avec des droits ou des secrets
#   (pull_request_target, workflow_run) ;
# - les secrets de signature (CDR_KEYSTORE_*) n'apparaissent que dans build.yml, et seulement dans le job
#   « Publication officielle » (environnement protégé « publication ») ; aucun autre secret n'est utilisé ;
# - aucune clé de signature ni aucun fichier de secrets n'est suivi par git.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

erreurs=0
erreur() {
  echo "::error title=Règles de sécurité::$1"
  erreurs=$((erreurs + 1))
}

shopt -s nullglob
workflows=(.github/workflows/*.yml .github/workflows/*.yaml)
[[ ${#workflows[@]} -gt 0 ]] || erreur "aucun workflow trouvé"

for f in "${workflows[@]}"; do
  # Actions épinglées : « uses: propriétaire/dépôt[/chemin]@<40 hexadécimaux> » ou action locale « ./… ».
  while IFS= read -r ligne; do
    ref=$(sed -E 's/^[[:space:]-]*uses:[[:space:]]*//; s/[[:space:]]*#.*$//; s/^["'\'']//; s/["'\'']$//' <<< "${ligne#*:}")
    [[ "$ref" == ./* ]] && continue
    if [[ ! "$ref" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+@[0-9a-f]{40}$ ]]; then
      erreur "$f : action non épinglée par empreinte de commit : $ref"
    fi
  done < <(grep -nE '^[[:space:]-]*uses:' "$f" || true)

  # Permissions déclarées en tête du fichier, en lecture seule.
  if ! grep -qE '^permissions:' "$f"; then
    erreur "$f : pas de « permissions: » au niveau du fichier"
  elif ! awk '/^permissions:/{p=1;next} p && /^[^ ]/{exit} p' "$f" | grep -qE '^[[:space:]]+contents:[[:space:]]*read'; then
    erreur "$f : les permissions du fichier doivent être « contents: read »"
  fi

  # Déclencheurs dangereux.
  if grep -qE '^[[:space:]]*(pull_request_target|workflow_run)[[:space:]]*:' "$f"; then
    erreur "$f : déclencheur pull_request_target ou workflow_run interdit"
  fi

  # Secrets : seulement ceux de la signature, seulement dans le job de publication.
  while IFS= read -r nom; do
    if [[ "$nom" != secrets.CDR_KEYSTORE_* || "$f" != .github/workflows/build.yml ]]; then
      erreur "$f : secret non autorisé ici : $nom"
    fi
  done < <(grep -vE '^[[:space:]]*#' "$f" | grep -oE 'secrets\.[A-Za-z0-9_]+' || true)
done

# Dans build.yml, les secrets de signature sont dans le job « release », qui a l'environnement « publication ».
job_secrets=$(awk '
  /^  [A-Za-z0-9_-]+:[[:space:]]*$/ { job = $1; sub(":", "", job) }
  /^[[:space:]]*#/ { next }
  /secrets\.CDR_KEYSTORE_/ { print job }
' .github/workflows/build.yml | sort -u)
if [[ -n "$job_secrets" && "$job_secrets" != "release" ]]; then
  erreur "build.yml : secrets de signature hors du job « release » : $(tr '\n' ' ' <<< "$job_secrets")"
fi
if ! awk '/^  release:/{p=1;next} p && /^  [A-Za-z0-9_-]+:[[:space:]]*$/{exit} p' .github/workflows/build.yml | grep -qE '^[[:space:]]+environment:[[:space:]]*publication[[:space:]]*$'; then
  erreur "build.yml : le job « release » doit utiliser l'environnement protégé « publication »"
fi

# Aucun fichier de clé ni de secrets suivi par git.
while IFS= read -r fichier; do
  erreur "fichier sensible suivi par git : $fichier"
done < <(git ls-files | grep -iE '\.(jks|keystore|p12|pfx|pem|key)$|(^|/)(local\.properties|keystore\.properties|\.env)$|CDR_KEYSTORE' || true)

if [[ $erreurs -gt 0 ]]; then
  echo "$erreurs écart(s) aux règles de sécurité du dépôt (docs/SECURITE.md)."
  exit 1
fi
echo "Règles de sécurité du dépôt : tout est conforme (${#workflows[@]} workflows vérifiés)."
