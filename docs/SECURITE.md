# Sécurité du dépôt et de sa gestion

Qui contrôle ce dépôt contrôle ce que l'appli installe sur les téléphones : une publication officielle
est proposée en mise à jour à tous les joueurs. Il faut donc protéger trois choses : **ton compte
GitHub**, **la branche `main`** et **la clé de signature**.

## Déjà en place, vérifié à chaque build

- **Clé de signature** : jamais dans le dépôt (`.gitignore`), seulement dans l'environnement protégé
  `publication`, remise au seul job « Publication officielle » après ta validation (docs/SIGNATURE.md).
- **Mises à jour** : manifeste signé avec la clé de l'APK, vérifié par l'appli avec le certificat de
  l'appli installée ; taille et empreinte de l'APK contrôlées ; provenance attestée (docs/MISES-A-JOUR.md).
- **Workflows** : actions épinglées par empreinte de commit (mises à jour par Dependabot, une fois par
  mois, 14 jours après leur sortie), jeton en lecture seule par défaut, aucun secret hors de la
  publication, `persist-credentials: false`.
- **Job « Règles de sécurité »** (`.github/scripts/verifier-depot.sh`), à chaque build : il refuse une
  action non épinglée, un workflow sans `permissions: contents: read`, les déclencheurs
  `pull_request_target` et `workflow_run`, tout secret autre que ceux de la signature ou utilisé hors du
  job « Publication officielle », et tout fichier de clé suivi par git. La publication (officielle ou
  « dev ») n'a pas lieu s'il échoue.
- **Historique** : aucun fichier de clé ni jeton dans les 51 commits du dépôt (vérifié le 23/10/2026 ;
  la seule « clé » présente est une clé publique de test, `core/src/test/resources/vecteurs-mise-a-jour.json`).

## À régler une fois dans GitHub (environ 15 minutes)

Ces réglages ne peuvent pas être faits depuis le code : ils se font dans l'interface de GitHub (en
anglais). Coche-les dans l'ordre.

### 1. Ton compte (le plus important)

- [ ] **Settings → Password and authentication** : double authentification activée, de préférence avec
      une **passkey** ou une clé de sécurité (pas les SMS). Range les codes de récupération dans ton
      gestionnaire de mots de passe.
- [ ] **Settings → Developer settings → Personal access tokens** : supprime les jetons inutiles. Si tu en
      as besoin d'un, prends un jeton *fine-grained*, limité à ce dépôt, avec une date d'expiration.
- [ ] **Settings → Applications → Installed GitHub Apps** : pour chaque appli (dont Claude),
      **Configure → Repository access → Only select repositories**, et seulement les dépôts utiles.
      Révoque les **Authorized OAuth Apps** que tu n'utilises plus.
- [ ] **Settings → SSH and GPG keys** et **Sessions** : supprime ce que tu ne reconnais pas.

### 2. Protéger la branche `main`

**Settings → Rules → Rulesets → New ruleset → New branch ruleset** :

- [ ] *Ruleset Name* : `main protégée` ; *Enforcement status* : **Active**.
- [ ] *Target branches* → **Add target → Include default branch**.
- [ ] Coche **Restrict deletions** et **Block force pushes**.
- [ ] Coche **Require a pull request before merging**, *Required approvals* : **0** (tu es seul ; tu
      fusionnes tes propres pull requests).
- [ ] Coche **Require status checks to pass** → **Add checks** : `Règles de sécurité` et
      `Tests, lint et APK`.
- [ ] *Bypass list* : laisse-la vide. **Create**.

Plus personne, toi compris, ne peut pousser directement sur `main` ni y fusionner du code rouge.
Attention : le workflow « Données du jeu » (lancé à la main, inutilisé tant que sa source est privée)
commite directement sur `main` ; la règle le bloquera. S'il resert, il faudra le faire passer par une
branche et une pull request.

### 3. Figer les versions publiées

**Settings → Rules → Rulesets → New ruleset → New tag ruleset** :

- [ ] *Ruleset Name* : `versions publiées` ; *Enforcement status* : **Active**.
- [ ] *Target tags* → **Add target → Include by pattern** : `v*`.
- [ ] Coche **Restrict updates** et **Restrict deletions** (pas *Restrict creations* : la publication
      crée elle-même le tag). **Create**.

Un tag `v0.12.1` publié ne peut plus être déplacé vers un autre code ni supprimé.

### 4. Environnement `publication` (à vérifier)

**Settings → Environments → publication** (créé avec docs/SIGNATURE.md) :

- [ ] **Required reviewers** : toi. *Prevent self-review* décoché.
- [ ] **Allow administrators to bypass configured protection rules** : **décoché**.
- [ ] **Deployment branches and tags** : *Selected branches and tags*, seulement `main` et `v*`.
- [ ] **Environment secrets** : `CDR_KEYSTORE_BASE64` et `CDR_KEYSTORE_PASSWORD` sont ici, et
      **Settings → Secrets and variables → Actions → Repository secrets** est **vide**.

### 5. GitHub Actions

**Settings → Actions → General** :

- [ ] *Actions permissions* : **Allow slaynAW, and select non-slaynAW, actions and reusable workflows**,
      coche **Allow actions created by GitHub** et, dans *Allow specified actions*, ajoute
      `gradle/actions/setup-gradle@*`. Coche **Require actions to be pinned to a full-length commit
      SHA** si l'option est proposée.
- [ ] *Fork pull request workflows from outside collaborators* : **Require approval for all external
      contributors**.
- [ ] *Workflow permissions* : **Read repository contents and packages permissions**, et **décoche**
      *Allow GitHub Actions to create and approve pull requests*.

### 6. Alertes de sécurité

**Settings → Advanced Security** (anciennement *Code security*) :

- [ ] **Private vulnerability reporting** : *Enable* (c'est le canal de SECURITY.md).
- [ ] **Dependency graph**, **Dependabot alerts** et **Dependabot security updates** : *Enable*.
- [ ] **Secret Protection** (*Secret scanning*) et **Push protection** : *Enable* (gratuit pour un dépôt
      public). GitHub analyse tout l'historique et refuse un push qui contient un jeton.
- [ ] **Code scanning → CodeQL analysis → Set up → Default** : au minimum pour les workflows
      (*GitHub Actions*).

### 7. Divers

- [ ] **Settings → General** : décoche *Wikis* et *Projects* si tu ne t'en sers pas ; coche
      *Automatically delete head branches* ; laisse *Allow auto-merge* décoché.
- [ ] **Settings → Collaborators**, **Deploy keys** et **Webhooks** : vides, sauf ce que tu reconnais.

## Si quelque chose fuit

- **Un jeton GitHub** : révoque-le tout de suite (Settings → Developer settings), puis regarde
  **Settings → Security log** pour ce qu'il a pu faire.
- **Le compte** : change le mot de passe, révoque toutes les sessions, vérifie les clés SSH, les
  applications, les collaborateurs, les règles ci-dessus et les dernières Releases.
- **La clé de signature** : supprime tout de suite les deux secrets de l'environnement `publication`
  (plus aucune publication possible) et supprime toute Release que tu n'as pas publiée toi-même.
  Changer de clé impose aujourd'hui aux joueurs de désinstaller puis réinstaller l'appli
  (docs/SIGNATURE.md) : c'est la protection à ne jamais relâcher.
