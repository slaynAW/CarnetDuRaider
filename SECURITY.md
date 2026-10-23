# Politique de sécurité

## Signaler une faille

Ne publie pas de faille dans une issue ou une discussion publique. Utilise le signalement privé de
GitHub : onglet **Security** du dépôt → **Report a vulnerability**. Seul le mainteneur le lit.

Sont particulièrement sensibles :

- les **mises à jour intégrées** : tout moyen de faire installer un APK qui ne viendrait pas d'une
  publication officielle, ou de contourner la vérification du manifeste signé, de la taille ou de
  l'empreinte de l'APK (docs/MISES-A-JOUR.md) ;
- la **clé de signature** et le job « Publication officielle » (docs/SIGNATURE.md) ;
- la **sauvegarde chiffrée** des réglages (mot de passe, chiffrement, fichier modifié) ;
- les workflows GitHub Actions (droits, secrets, actions tierces).

## Versions suivies

Seule la dernière version publiée (onglet **Releases**) reçoit des correctifs : l'appli propose
elle-même de se mettre à jour.

## Ce que l'appli ne fait jamais

Elle ne se connecte jamais au compte Embark ni aux serveurs du jeu, ne lit ni la mémoire ni le trafic du
jeu, et ne demande aucun jeton de connexion. Ses seules connexions : GitHub (mises à jour) et
arcraiders.com (calendrier des conditions de la carte).

Les règles de sécurité du dépôt et les réglages GitHub recommandés : docs/SECURITE.md.
