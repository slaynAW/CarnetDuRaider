# Carnet du Raider

Application Android compagnon pour **ARC Raiders**, à garder à côté du clavier pendant les sessions de jeu.

> Projet de fan, **non officiel**. ARC Raiders est une marque d'Embark Studios AB ; cette application
> n'est ni affiliée à Embark Studios ni approuvée par Embark Studios.

## Ce que fait la version 0.1

- **Garder, recycler ou vendre ?** Recherche d'un objet parmi tout le catalogue du jeu (sans tenir
  compte des accents), puis une fiche avec un verdict selon *tes* objectifs : ce qu'il t'en manque, ou,
  s'il est inutile, s'il vaut mieux le vendre ou le recycler (comparaison en pièces).
- **Liste de courses.** Tu choisis des niveaux d'établis de l'Atelier et des quêtes ; l'appli additionne
  les objets à trouver et suit ta progression. Ta réserve se met à jour en une touche (« − » / « + »).
- **Conditions de la carte.** Les cartes et conditions du jeu, et un accès au calendrier officiel
  d'Embark ([arcraiders.com/map-conditions](https://arcraiders.com/map-conditions)). Les minuteurs et
  les alertes arrivent dans une prochaine version.
- **Hors connexion et sans compte.** Les données du jeu sont embarquées dans l'appli ; elle ne se
  connecte jamais à ton compte Embark et ne touche jamais au jeu.

## Noms du jeu : uniquement les traductions françaises officielles

Tous les noms (objets, établis, quêtes, cartes, conditions…) sont ceux de la version française du jeu
(données de la version 2.0). Aucun nom n'est traduit par le projet : un nom dont la traduction
officielle n'est pas vérifiée est retiré de l'appli et listé dans
[docs/NOMS-A-VERIFIER.md](docs/NOMS-A-VERIFIER.md). Détails : [CLAUDE.md](CLAUDE.md).

## Installer

L'APK de test est produit par GitHub à chaque modification : onglet **Actions** → dernier build →
artefact **apk**. Android 8 ou plus récent.

## Développement

```bash
./gradlew :core:test                 # tests de la logique (données, liste de courses, verdict)
./gradlew :app:assembleDebug          # APK de debug
```

## Sources et licences

- Données du jeu : textes officiels d'Embark Studios, extraits par le projet communautaire
  [baschny/arcraiders-raider-tools](https://github.com/baschny/arcraiders-raider-tools)
  (`scripts/importer_donnees_jeu.py`). Tout le contenu du jeu est © Embark Studios AB.
- Polices : [Prompt](https://fonts.google.com/specimen/Prompt) et
  [Barlow](https://fonts.google.com/specimen/Barlow), SIL Open Font License 1.1
  ([third_party/polices](third_party/polices)).
