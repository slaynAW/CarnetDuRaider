#!/usr/bin/env python3
"""Importe les données du jeu (version 2.0) avec leurs textes français officiels.

Source : les fichiers « public/data/game » du projet communautaire baschny/arcraiders-raider-tools,
générés à partir des données du jeu ARC Raiders (textes officiels d'Embark Studios, toutes langues).

Règle du projet (CLAUDE.md) : l'appli n'affiche QUE les traductions françaises officielles. Ce script
n'invente ni ne traduit rien :
  - un nom sans texte français est exclu ;
  - un nom dont le texte français est identique à l'anglais est exclu, sauf s'il figure dans
    scripts/noms-identiques-verifies.txt (vrai nom français identique, vérifié) ;
  - chaque exclusion est listée dans docs/NOMS-A-VERIFIER.md.

Usage : python3 scripts/importer_donnees_jeu.py <dossier public/data/game>
Écrit core/src/main/resources/donnees-jeu.json et docs/NOMS-A-VERIFIER.md.
"""
import json
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent
SORTIE = RACINE / "core/src/main/resources/donnees-jeu.json"
RAPPORT = RACINE / "docs/NOMS-A-VERIFIER.md"
LISTE_BLANCHE = RACINE / "scripts/noms-identiques-verifies.txt"

# Familles d'objets importées (les meubles de l'Avant-poste et les apparences viendront plus tard).
GROUPES = {"CraftingItems", "Misc", "Weapons", "Utilities", "Keys", "Modifications", "Augment",
           "Ammunition", "Armor"}
RARETES = {"Common": "COMMUN", "Uncommon": "INHABITUEL", "Rare": "RARE", "Epic": "EPIQUE",
           "Legendary": "LEGENDAIRE"}
OBJECTIFS_OBJET = {"Deliver", "Obtain"}


def charger(dossier, nom):
    with open(dossier / nom, encoding="utf-8") as f:
        return json.load(f)


def lire_liste_blanche():
    exacts, prefixes = set(), set()
    for ligne in LISTE_BLANCHE.read_text(encoding="utf-8").splitlines():
        ligne = ligne.strip()
        if not ligne or ligne.startswith("#"):
            continue
        if ligne.endswith(" *"):
            prefixes.add(ligne[:-2])
        else:
            exacts.add(ligne)
    return exacts, prefixes


class Filtre:
    """Applique la règle des traductions officielles et garde la trace des exclusions."""

    def __init__(self):
        self.exacts, self.prefixes = lire_liste_blanche()
        self.exclus = []  # (domaine, identifiant, anglais, raison)

    def nom(self, domaine, ident, fr, en):
        fr = (fr or "").strip()
        en = (en or "").strip()
        if not fr:
            self.exclus.append((domaine, ident, en, "pas de texte français"))
            return None
        if fr == en and not self._verifie(fr):
            self.exclus.append((domaine, ident, en, "texte français identique à l'anglais"))
            return None
        return fr

    def _verifie(self, nom):
        if nom in self.exacts:
            return True
        return any(re.fullmatch(re.escape(p) + r"( [IVX]+)?", nom) for p in self.prefixes)


def objets_de_quete(noeud, total):
    action = noeud.get("action") or {}
    if action.get("type") in OBJECTIFS_OBJET and action.get("itemId"):
        total[action["itemId"]] = total.get(action["itemId"], 0) + int(action.get("amount", 1))
    for enfant in noeud.get("children", []):
        objets_de_quete(enfant, total)
    return total


def quantites(liste):
    return [{"id": e["itemId"], "quantite": e["quantity"]} for e in liste or []]


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    src = pathlib.Path(sys.argv[1])
    filtre = Filtre()

    items = charger(src, "items.json")
    items_fr, items_en = charger(src, "items.text.fr.json"), charger(src, "items.text.en.json")
    classif = charger(src, "classification.text.fr.json")
    version = items["gameVersion"]

    objets = []
    for ident, it in sorted(items["items"].items()):
        # Les objets de quête n'ont pas de rareté : ils sont gardés, rareté vide.
        if it.get("group") not in GROUPES:
            continue
        nom = filtre.nom("Objet", ident, items_fr.get(ident, {}).get("name"),
                         items_en.get(ident, {}).get("name") or it.get("nameEn"))
        if nom is None:
            continue
        objets.append({
            "id": ident,
            "nom": nom,
            "rarete": RARETES.get(it.get("rarity")),
            "categorie": classif["subgroups"].get(it.get("subgroup") or "", None),
            "valeur": it.get("value", 0),
            "pile": it.get("stackSize", 1),
            "recyclage": quantites(it.get("recyclesInto")),
            "recuperation": quantites(it.get("salvagesInto")),
            "lieux": [classif["themes"][t] for t in it.get("foundIn", []) if t in classif["themes"]],
        })
    ids_objets = {o["id"] for o in objets}

    benches = charger(src, "benches.json")["benches"]
    benches_fr, benches_en = charger(src, "benches.text.fr.json"), charger(src, "benches.text.en.json")
    etablis = []
    for ident, b in sorted(benches.items()):
        nom = filtre.nom("Établi", ident, benches_fr.get(ident, {}).get("name"),
                         benches_en.get(ident, {}).get("name") or b.get("nameEn"))
        if nom is None:
            continue
        niveaux = [{"niveau": n["level"], "cout": quantites(n.get("buildCost"))}
                   for n in b.get("levels", []) if n.get("buildCost")]
        if niveaux:
            etablis.append({"id": ident, "nom": nom, "niveaux": niveaux})

    trades = charger(src, "trades.json")
    commercants = charger(src, "trades.text.fr.json")
    quests = charger(src, "quests.json")["quests"]
    quests_fr, quests_en = charger(src, "quests.text.fr.json"), charger(src, "quests.text.en.json")
    quetes = []
    for ident, q in sorted(quests.items()):
        requis = objets_de_quete(q.get("objective", {}), {})
        if not requis:
            continue
        nom = filtre.nom("Quête", ident, quests_fr.get(ident, {}).get("name"),
                         quests_en.get(ident, {}).get("name") or q.get("nameEn"))
        if nom is None:
            continue
        commercant = commercants.get(q.get("traderId", ""), {}).get("name")
        if commercant is not None:
            commercant = filtre.nom("Commerçant", q["traderId"], commercant,
                                    trades["traders"].get(q["traderId"], {}).get("nameEn", commercant))
        quetes.append({"id": ident, "nom": nom, "commercant": commercant,
                       "objets": [{"id": i, "quantite": n} for i, n in sorted(requis.items())]})

    cartes_src = charger(src, "maps.json")
    cartes_fr, cartes_en = charger(src, "maps.text.fr.json"), charger(src, "maps.text.en.json")
    cartes, conditions = [], []
    for ident, c in sorted(cartes_src["maps"].items()):
        nom = filtre.nom("Carte", ident, cartes_fr.get(ident, {}).get("name"),
                         cartes_en.get(ident, {}).get("name") or c.get("nameEn"))
        if nom is not None:
            cartes.append({"id": ident, "nom": nom})
    for ident, e in sorted(cartes_src["eventTypes"].items()):
        nom = filtre.nom("Condition de la carte", ident, cartes_fr.get(ident, {}).get("name"),
                         cartes_en.get(ident, {}).get("name") or e.get("nameEn"))
        if nom is not None:
            conditions.append({"id": ident, "nom": nom, "majeure": bool(e.get("major"))})

    donnees = {
        "versionJeu": version,
        "source": "Données du jeu ARC Raiders (textes officiels d'Embark Studios), via "
                  "github.com/baschny/arcraiders-raider-tools",
        "objets": objets,
        "etablis": etablis,
        "quetes": quetes,
        "cartes": cartes,
        "conditions": conditions,
    }
    # Une référence vers un objet exclu reste (l'appli affiche alors « nom à vérifier en jeu ») :
    # mieux vaut un trou visible qu'un nom inventé.
    orphelins = sorted({e["id"] for e in [x for o in objets for x in o["recyclage"] + o["recuperation"]]
                        + [x for b in etablis for n in b["niveaux"] for x in n["cout"]]
                        + [x for q in quetes for x in q["objets"]]} - ids_objets)

    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    SORTIE.write_text(json.dumps(donnees, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")

    lignes = [
        "# Noms à vérifier en jeu",
        "",
        "Généré par `scripts/importer_donnees_jeu.py` (données du jeu " + version + ").",
        "",
        "Ces noms sont **exclus de l'appli** : leur texte français officiel manque dans les données, ou",
        "il est identique à l'anglais sans qu'on ait pu vérifier que c'est bien le nom français. Pour en",
        "rétablir un, vérifie son nom dans le jeu en français : s'il est vraiment identique, ajoute-le à",
        "`scripts/noms-identiques-verifies.txt`, puis relance l'import.",
        "",
        "| Type | Identifiant | Texte anglais | Raison |",
        "| --- | --- | --- | --- |",
    ]
    lignes += [f"| {d} | `{i}` | {en} | {r} |" for d, i, en, r in filtre.exclus]
    RAPPORT.parent.mkdir(parents=True, exist_ok=True)
    RAPPORT.write_text("\n".join(lignes) + "\n", encoding="utf-8")

    print(f"Données {version} : {len(objets)} objets, {len(etablis)} établis, {len(quetes)} quêtes, "
          f"{len(cartes)} cartes, {len(conditions)} conditions ; {len(filtre.exclus)} noms exclus, "
          f"{len(orphelins)} références vers un objet exclu.")


if __name__ == "__main__":
    main()
