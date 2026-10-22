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
Lit aussi le dossier voisin « public/data/map-data » (plans en jeu des cartes, zones, sorties).
Écrit core/src/main/resources/donnees-jeu.json et docs/NOMS-A-VERIFIER.md, et les icônes officielles
des objets (fichiers du jeu, dossier « public/images/items » voisin) réduites pour l'appli dans
app/src/main/assets/objets/<identifiant>.webp. Demande Pillow et NumPy (pip install pillow numpy).
"""
import json
import pathlib
import re
import sys
import unicodedata

RACINE = pathlib.Path(__file__).resolve().parent.parent
SORTIE = RACINE / "core/src/main/resources/donnees-jeu.json"
RAPPORT = RACINE / "docs/NOMS-A-VERIFIER.md"
ICONES = RACINE / "app/src/main/assets/objets"
PLANS = RACINE / "app/src/main/assets/cartes"
# Plans des cartes : image du plan en jeu à 2048 px (niveau 2 de la pyramide de tuiles de raider-tools).
NIVEAU_PLAN = 2
QUALITE_PLAN = 72
# Icônes réduites : nettes jusqu'à ~100 dp sur un écran courant, environ 4 Mo pour tout le catalogue.
TAILLE_ICONE = 160
QUALITE_ICONE = 82
LISTE_BLANCHE = RACINE / "scripts/noms-identiques-verifies.txt"

# Familles d'objets importées (les meubles de l'Avant-poste et les apparences viendront plus tard).
GROUPES = {"CraftingItems", "Misc", "Weapons", "Utilities", "Keys", "Modifications", "Augment",
           "Ammunition", "Armor"}
RARETES = {"Common": "COMMUN", "Uncommon": "INHABITUEL", "Rare": "RARE", "Epic": "EPIQUE",
           "Legendary": "LEGENDAIRE"}
OBJECTIFS_OBJET = {"Deliver", "Obtain"}
# Emplacement d'un objet dans un build (section « Mes builds » de l'appli), d'après sa famille dans le jeu.
EMPLACEMENTS_GROUPE = {"Augment": "MODULE", "Armor": "BOUCLIER", "Weapons": "ARME"}
EMPLACEMENTS_CATEGORIE = {"Utility.Regenerative": "SOINS", "Utility.Grenade": "GRENADE",
                          "Utility.Mine": "DIVERS", "Utility.Tools": "DIVERS", "Gadget": "DIVERS"}
CATEGORIES_HISTOIRE = {"main", "side"}


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
        raison = None
        if not fr:
            raison = "pas de texte français"
        elif fr == en and not self._verifie(fr):
            raison = "texte français identique à l'anglais"
        if raison is None:
            return fr
        exclusion = (domaine, ident, en, raison)
        if exclusion not in self.exclus:  # un même nom peut être demandé plusieurs fois (objets, cartes…)
            self.exclus.append(exclusion)
        return None

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


def emplacement(objet):
    """Emplacement de build de l'objet (MODULE, BOUCLIER, ARME, SOINS, GRENADE, DIVERS), ou None."""
    if objet.get("group") == "Utilities":
        return EMPLACEMENTS_CATEGORIE.get(objet.get("category"))
    return EMPLACEMENTS_GROUPE.get(objet.get("group"))


def quantites(liste):
    return [{"id": e["itemId"], "quantite": e["quantity"]} for e in liste or []]


def convertir_icone(travail):
    source, destination = travail
    from PIL import Image
    with Image.open(source) as image:
        image = image.convert("RGBA")
        image.thumbnail((TAILLE_ICONE, TAILLE_ICONE), Image.LANCZOS)
        image.save(destination, "WEBP", quality=QUALITE_ICONE, method=4)


def importer_icones(dossier_images, objets, items):
    """Icônes des objets gardés ; les anciennes icônes sont supprimées. Renvoie (écrites, manquantes)."""
    try:
        import PIL  # noqa: F401
    except ImportError:
        sys.exit("Pillow est nécessaire pour les icônes : pip install pillow")
    import concurrent.futures
    ICONES.mkdir(parents=True, exist_ok=True)
    for ancien in ICONES.glob("*.webp"):
        ancien.unlink()
    travaux, manquantes = [], []
    for o in objets:
        icone = (items[o["id"]].get("icon") or "").removeprefix("/images/")
        source = dossier_images / icone if icone else None
        if source is None or not source.is_file():
            manquantes.append(o["id"])
            continue
        travaux.append((source, ICONES / f"{o['id']}.webp"))
    with concurrent.futures.ProcessPoolExecutor() as executeur:
        list(executeur.map(convertir_icone, travaux, chunksize=16))
    return len(travaux), manquantes


# Sorties du jeu (classe du point d'extraction -> type, pour la recherche sur le plan).
TYPES_SORTIE = [
    (r"RaiderHatchMarker|SalvageExtractionPoint_Hatch", "trappe"),
    (r"SalvageExtractionPoint_Elevator", "ascenseur"),
    (r"SalvageExtractionPoint_Metro", "metro"),
    (r"SalvageExtractionPoint_Fan", "conduit"),
    (r"SalvageExtractionPoint_MultiProbe", "telepherique"),
]


def normaliser(texte):
    """Minuscules sans accents (tri alphabétique)."""
    return "".join(c for c in unicodedata.normalize("NFD", texte.lower()) if not unicodedata.combining(c))


def dans_anneau(x, y, anneau):
    dedans = False
    for (x1, y1), (x2, y2) in zip(anneau, anneau[-1:] + anneau[:-1]):
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            dedans = not dedans
    return dedans


def poi_contenant(pois, x, y):
    """Zone nommée (POI) qui contient le point, sinon la plus proche à moins de 1 % du plan."""
    proche, distance = None, 0.01
    for poi in pois:
        anneaux = poi.get("outline", [])
        if any(len(a) > 2 and dans_anneau(x, y, a) for a in anneaux):
            return poi
        for a in anneaux:
            for px, py in a:
                d = ((px - x) ** 2 + (py - y) ** 2) ** 0.5
                if d < distance:
                    proche, distance = poi, d
    return proche


def ennemis_de_table(index, table):
    """Indices des ARC qu'une table d'apparition peut faire venir (index.json, enemyTables)."""
    t = index.get("enemyTables", {}).get(table) if table else None
    if not t:
        return set()
    groupes = [g for liste in t.get("lists", []) for g in liste]
    groupes += [g for liste in t.get("byDifficulty", {}).values() for g in liste]
    return {genre[0] for g in groupes for genre in g.get("kinds", [])}


def importer_plans(dossier_cartes, cartes, filtre):
    """Plan simplifié de chaque carte : image du plan en jeu, zones, sorties et apparitions des ARC (noms officiels)."""
    from PIL import Image
    index = json.loads((dossier_cartes / "index.json").read_text(encoding="utf-8"))
    textes = json.loads((dossier_cartes / "map-strings.fr.json").read_text(encoding="utf-8"))
    PLANS.mkdir(parents=True, exist_ok=True)
    for ancien in PLANS.glob("*.webp"):
        ancien.unlink()
    par_id = {c["id"]: c for c in cartes}
    noms_arc = [filtre.nom("ARC", e.get("nameKey"), textes.get(e.get("nameKey") or ""), e.get("name"))
                for e in index.get("enemies", [])]
    for entree in index["maps"]:
        carte = par_id.get(entree.get("slug"))
        if carte is None:
            continue
        donnees = json.loads((dossier_cartes / entree["file"]).read_text(encoding="utf-8"))
        tuiles = dossier_cartes / entree["image"]["tiles"] / str(NIVEAU_PLAN)
        cote = 512 * 2 ** NIVEAU_PLAN
        plan = Image.new("RGB", (cote, cote), (20, 20, 23))
        for x in range(2 ** NIVEAU_PLAN):
            for y in range(2 ** NIVEAU_PLAN):
                tuile = tuiles / f"{x}-{y}.webp"
                if tuile.is_file():
                    with Image.open(tuile) as t:
                        plan.paste(t.convert("RGB"), (x * 512, y * 512))
        plan.save(PLANS / f"{carte['id']}.webp", "WEBP", quality=QUALITE_PLAN, method=4)

        def nom(cle, anglais, domaine, ident):
            return filtre.nom(domaine, ident, textes.get(cle or ""), anglais)

        zones = []
        for z in donnees.get("areas", []):
            if z.get("layer", 0) != 0:
                continue  # autre étage (Stella Montis) : pas sur ce plan
            n = nom(z.get("nameKey"), z.get("name"), "Zone de carte", z.get("nameKey"))
            if n:
                zones.append({"nom": n, "x": z["uv"][0], "y": z["uv"][1], "detail": z.get("zoom", 0)})
        # Autres lieux nommés (zones de butin) : détail 3, affichés seulement quand on les cherche.
        noms_zones = {z["nom"] for z in zones}
        for p in donnees.get("pois", []):
            cle = p.get("titleKey")
            points = [pt for anneau in p.get("outline", []) for pt in anneau]
            if not cle or cle.startswith("ST_ExtractionPoints/") or not points:
                continue
            n = nom(cle, p.get("title"), "Zone de carte", cle)
            if n and n not in noms_zones:
                noms_zones.add(n)
                zones.append({"nom": n, "x": round(sum(pt[0] for pt in points) / len(points), 4),
                              "y": round(sum(pt[1] for pt in points) / len(points), 4), "detail": 3})

        # Sorties : points d'extraction du jeu, nommés par la zone (POI) qui les contient.
        classes = donnees.get("spawnerClasses", [])
        sorties = []
        for x, y, ensemble in donnees.get("spawners", []):
            type_sortie = next((t for motif, t in TYPES_SORTIE if re.search(motif, classes[ensemble])), None)
            if type_sortie is None:
                continue
            # Le terminal du téléphérique (Col de Pendola) n'a pas de nom à lui : la zone serait la carte entière.
            poi = poi_contenant(donnees.get("pois", []), x, y) if type_sortie != "telepherique" else None
            n = nom(poi.get("titleKey"), poi.get("title"), "Sortie", poi.get("titleKey")) if poi else None
            sortie = {"type": type_sortie, "x": round(x, 4), "y": round(y, 4)}
            if n:
                sortie["nom"] = n
            sorties.append(sortie)

        # ARC : points d'apparition de chaque ARC (tous étages, toutes conditions de la carte).
        points_arc = {}
        probabilites = donnees.get("enemyP", [])
        for i, (x, y, _z, _classe, table, _profil, _chemins) in enumerate(donnees.get("enemySpawners", [])):
            if not any(p[i] > 0 for p in probabilites if i < len(p)):
                continue
            for e in ennemis_de_table(index, table):
                n = noms_arc[e]
                if n:
                    pts = points_arc.setdefault(n, [])
                    pt = [round(x, 4), round(y, 4)]
                    if pt not in pts:
                        pts.append(pt)
        arcs = [{"nom": n, "points": pts} for n, pts in sorted(points_arc.items(), key=lambda e: normaliser(e[0]))]
        carte["plan"] = {"zones": zones, "sorties": sorties, "arcs": arcs}


# ---- Comment obtenir chaque objet ----
# Butin : portage du calcul de répartition de raider-tools (src/apps/maps/data/scoring.ts). Les tirages du
# jeu se font sur ses serveurs : ce n'est pas un taux de drop, mais la part des tables de butin de chaque
# zone qui peut donner l'objet (condition normale), pour classer les meilleures cartes et zones.
ALIAS_THEMES = {"Tech": "Technological"}
ALIAS_NATURE = {"cactus": "Prickly Pear", "seedbox_01": "Assorted Seeds", "seedvault": "Assorted Seeds"}
ZONES_PAR_CARTE = 5  # meilleures zones gardées sur chaque carte (fiche objet, préparation du raid)
INDICE_MINIMUM = 2  # une zone qui donne moins de 2 % du meilleur endroit n'est pas un bon conseil


def _lettres(x):
    return re.sub(r"[^a-z]", "", x.lower())


def _meme_nom(a, b):
    a, b = _lettres(a), _lettres(b)
    return len(a) >= 4 and len(b) >= 4 and (a.startswith(b) or b.startswith(a))


def _conteneurs_dedies(objet, conteneurs):
    """Plantes et nids : un conteneur « Nature » ne donne que son propre objet."""
    tags = objet.get("tags") or []
    if "Category.Area.Nature" not in tags:
        return []
    nom = objet.get("name") or ""
    propres = [c for c in conteneurs if c.startswith("Nature.") and (
        _meme_nom(c.split(".")[1], nom) or _meme_nom(ALIAS_NATURE.get(c.split(".")[1].lower(), ""), nom))]
    if propres:
        return propres
    if any(t.endswith("Trinket.Birdnest") for t in tags):
        return [c for c in conteneurs if c.startswith("Nature.BirdNest")]
    return []


def _conteneurs_possibles(numpy, carte, objet):
    """Pour chaque conteneur de la carte (indice + 1 ; 0 = au sol) : peut-il donner l'objet ?"""
    conteneurs = carte["containers"]
    propres = _conteneurs_dedies(objet, conteneurs)
    if propres:
        return numpy.array([False] + [c in propres for c in conteneurs])
    themes = [t.split(".")[2] for t in objet.get("tags") or [] if t.startswith("Category.Area.")]
    nature = [c.startswith("Nature.") and not c.startswith("Nature.WaterTank") for c in conteneurs]
    if not themes:
        return numpy.array([True] + [not n for n in nature])
    return numpy.array([True] + [not nature[i] and ALIAS_THEMES.get(c.split(".")[0], c.split(".")[0]) in themes
                                 for i, c in enumerate(conteneurs)])


def calculer_butin(dossier_cartes, ids_objets, cartes, filtre):
    """{objet: {"cartes": [...], "zones": [...]}} : meilleures cartes et zones, indice 1 à 100."""
    import numpy
    index = json.loads((dossier_cartes / "index.json").read_text(encoding="utf-8"))
    textes = json.loads((dossier_cartes / "map-strings.fr.json").read_text(encoding="utf-8"))
    par_objet = {}
    for i, it in enumerate(index["items"]):
        if it["slug"] in ids_objets:
            par_objet.setdefault(it["slug"], []).append(i)
    parts = {}
    for ident, indices in par_objet.items():
        part = {}
        for table, entrees in index["tables"].items():
            p = sum(1 / len(entrees) / len(e["items"]) for e in entrees for i in indices if i in e["items"])
            if p:
                part[table] = p
        parts[ident] = part
    ids_cartes = {c["id"] for c in cartes}
    totaux = {ident: {} for ident in par_objet}
    zones = {ident: {} for ident in par_objet}
    for entree in index["maps"]:
        if entree.get("slug") not in ids_cartes:
            continue
        carte = json.loads((dossier_cartes / entree["file"]).read_text(encoding="utf-8"))
        normale = next((i for i, c in enumerate(carte["conditions"]) if c.get("category") == "normal"), 0)
        couches = set(carte["conditions"][normale]["layers"])
        prises = numpy.array(carte["sockets"], dtype=float)
        presente = (prises[:, 6] < 0) | numpy.isin(prises[:, 6], list(couches))
        conteneur = prises[:, 3].astype(int) + 1
        ensemble = prises[:, 4].astype(int)
        poi = prises[:, 5].astype(int)
        dans_zone = poi >= 0
        noms_zones = [filtre.nom("Zone de carte", p.get("titleKey"), textes.get(p.get("titleKey") or ""), p.get("title"))
                      if p.get("titleKey") else None for p in carte["pois"]]
        for ident, indices in par_objet.items():
            part = parts[ident]
            if not part:
                continue
            valeurs = []
            for h in carte["handlers"]:
                v = sum(pool["value"] / len(pool["tables"]) * part[t["t"]] * (t.get("x") or 1)
                        for pool in h["pools"] for t in pool["tables"] if t["t"] in part and normale in t["c"])
                valeurs.append(v / h["sockets"] if h["sockets"] else 0)
            par_ensemble = numpy.array([sum(valeurs[h] for h in e) for e in carte["handlerSets"]])
            possibles = numpy.zeros(len(carte["containers"]) + 1, bool)
            for i in indices:
                possibles |= _conteneurs_possibles(numpy, carte, index["items"][i])
            v = numpy.where(presente & possibles[conteneur], par_ensemble[ensemble], 0.0)
            total = float(v.sum())
            if total <= 0:
                continue
            totaux[ident][entree["slug"]] = total
            par_zone = numpy.bincount(poi[dans_zone], weights=v[dans_zone], minlength=len(carte["pois"]))
            for j, score in enumerate(par_zone):
                if score > 0 and noms_zones[j]:
                    cle = (entree["slug"], noms_zones[j])
                    zones[ident][cle] = zones[ident].get(cle, 0) + float(score)
    resultat = {}
    for ident in par_objet:
        if not totaux[ident]:
            continue
        meilleure = max(totaux[ident].values())
        meilleure_zone = max(zones[ident].values(), default=0)
        gardees = []
        for carte in sorted(totaux[ident], key=lambda c: -totaux[ident][c]):
            de_la_carte = sorted(((z, s) for (c, z), s in zones[ident].items() if c == carte), key=lambda e: -e[1])
            for z, s in de_la_carte[:ZONES_PAR_CARTE]:
                indice = round(100 * s / meilleure_zone)
                if indice >= INDICE_MINIMUM:
                    gardees.append({"carte": carte, "zone": z, "indice": indice})
        resultat[ident] = {
            "cartes": [{"carte": c, "indice": max(1, round(100 * t / meilleure))}
                       for c, t in sorted(totaux[ident].items(), key=lambda e: -e[1])],
            # Meilleures zones de chaque carte ; indice relatif à la meilleure zone toutes cartes confondues.
            "zones": sorted(gardees, key=lambda z: -z["indice"]),
        }
    return resultat


def obtention_objet(ident, it, src_textes, trades, recettes, filtre, noms_projets):
    """Achats, fabrication et récompenses d'un objet (noms officiels uniquement)."""
    commercants, items_fr, items_en, benches_fr, benches_en, quests_fr, quests_en = src_textes
    achats = []
    for tid in it.get("soldBy", []):
        t = trades["trades"].get(tid)
        if not t or not t.get("visible", True):
            continue
        trader = t.get("traderId", "")
        nom = filtre.nom("Commerçant", trader, commercants.get(trader, {}).get("name"),
                         trades["traders"].get(trader, {}).get("nameEn"))
        if nom is None:
            continue
        cout = t.get("cost", {})
        achat = {"commercant": nom,
                 "quantite": next((r["quantity"] for r in t.get("rewards", []) if r["itemId"] == ident), 1)}
        pieces = sum(c["quantity"] for c in cout.get("items", []) if c["itemId"] == "coins")
        if pieces:
            achat["pieces"] = pieces
        objets = [{"id": c["itemId"], "quantite": c["quantity"]} for c in cout.get("items", []) if c["itemId"] != "coins"]
        if objets:
            achat["objets"] = objets
        if cout.get("scrapValue"):
            achat["valeurObjets"] = cout["scrapValue"]
        limite = t.get("limit") or {}
        if limite.get("max"):
            achat["limite"] = limite["max"]
            if limite.get("refreshSeconds") == 86400:
                achat["parJour"] = True
        if achat not in achats:  # le même échange peut être proposé deux fois
            achats.append(achat)
    fabrications = []
    for r in recettes:
        if not any(x["itemId"] == ident for x in r.get("rewards", [])):
            continue
        fab = {"ingredients": quantites(r.get("cost", {}).get("items")),
               "quantite": next(x["quantity"] for x in r["rewards"] if x["itemId"] == ident)}
        if r.get("station") == "bench":
            etabli = filtre.nom("Établi", r.get("benchId"), benches_fr.get(r.get("benchId"), {}).get("name"),
                                benches_en.get(r.get("benchId"), {}).get("name"))
            if etabli is None:
                continue
            fab["etabli"] = etabli
            if r.get("benchLevel"):
                fab["niveau"] = r["benchLevel"]
        for req in r.get("requires", []):
            if req.get("kind") == "unlock" and req.get("id") in items_fr:
                plan = filtre.nom("Plan", req["id"], items_fr[req["id"]].get("name"), items_en.get(req["id"], {}).get("name"))
                if plan:
                    fab["plan"] = plan
        if fab not in fabrications:
            fabrications.append(fab)
    recompenses = []
    for q in it.get("rewardedBy", {}).get("quests", []):
        n = filtre.nom("Quête", q, quests_fr.get(q, {}).get("name"), quests_en.get(q, {}).get("name"))
        if n and n not in recompenses:
            recompenses.append(n)
    for pr in it.get("rewardedBy", {}).get("projects", []):
        if pr in noms_projets and noms_projets[pr] not in recompenses:
            recompenses.append(noms_projets[pr])
    obtention = {}
    if achats:
        obtention["achats"] = achats
    if fabrications:
        obtention["fabrications"] = fabrications
    if recompenses:
        obtention["recompenses"] = recompenses
    return obtention


def amelioration_objet(ident, it, tous, ids_objets):
    """Arme améliorée (« Ferro III ») : l'arme d'avant et ce que coûte l'amélioration, ou None."""
    for depuis in it.get("upgradesFrom", []):
        if depuis not in ids_objets:
            continue
        for u in tous.get(depuis, {}).get("upgradesTo", []):
            if u.get("itemId") == ident:
                return {"depuis": depuis, "ingredients": quantites(u.get("cost", {}).get("items"))}
    return None


# ---- À quoi sert chaque objet ----
# Familles d'équipement : ce qui s'utilise en raid (armes, modules, boucliers, utilitaires, mods, munitions).
GROUPES_EQUIPEMENT = {"Weapons", "Augment", "Armor", "Utilities", "Modifications", "Ammunition"}


class Utilisations:
    """Index inversés des données du jeu : où chaque objet est demandé (fabrication, recherche, Avant-poste,
    échanges), avec les noms français officiels."""

    def __init__(self, src, ids_objets, filtre, textes):
        self.ids_objets = ids_objets
        self.filtre = filtre
        self.commercants, self.items_fr, self.items_en, self.benches_fr, self.benches_en = textes
        self.trades = charger(src, "trades.json")
        self.recettes = {}
        for r in charger(src, "recipes.json")["recipes"].values():
            for c in (r.get("cost") or {}).get("items", []):
                self.recettes.setdefault(c["itemId"], []).append(r)
        recherche = charger(src, "research.json")["research"]
        recherche_fr, recherche_en = charger(src, "research.text.fr.json"), charger(src, "research.text.en.json")
        self.recherches = {}
        for ident, r in sorted(recherche.items()):
            nom = filtre.nom("Recherche", ident, recherche_fr.get(ident, {}).get("name"), recherche_en.get(ident, {}).get("name"))
            if nom is None:
                continue
            for c in (r.get("cost") or {}).get("items", []):
                self.recherches.setdefault(c["itemId"], []).append(nom)
        self.meubles = {}
        for ident, m in sorted(charger(src, "outpost.json")["furniture"].items()):
            nom = filtre.nom("Meuble", ident, self.items_fr.get(ident, {}).get("name"), self.items_en.get(ident, {}).get("name"))
            if nom is None:
                continue
            for c in ((m.get("craft") or {}).get("cost") or {}).get("items", []):
                self.meubles.setdefault(c["itemId"], []).append(nom)
        # Designs de l'Avant-poste : le meuble qu'ils débloquent.
        self.designs = {}
        for ident, dsg in charger(src, "outpost.json")["designs"].items():
            for meuble in dsg.get("unlocks", []):
                nom = filtre.nom("Meuble", meuble, self.items_fr.get(meuble, {}).get("name"), self.items_en.get(meuble, {}).get("name"))
                if nom:
                    self.designs.setdefault(ident, []).append(nom)
        # Amplification des armes : objets demandés pour amplifier telle arme.
        self.amplification = {}
        for arme in charger(src, "amplification.json")["weapons"].values():
            for depart, transitions in arme.get("graph", {}).items():
                for tr in transitions:
                    for c in (tr.get("cost") or {}).get("items", []):
                        self.amplification.setdefault(c["itemId"], []).append(depart)
        self.echanges, self.monnaie = {}, {}
        for t in self.trades["trades"].values():
            if not t.get("visible", True):
                continue
            cout = t.get("cost") or {}
            for c in cout.get("items", []):
                self.echanges.setdefault(c["itemId"], []).append((t, c["quantity"]))
            for i in cout.get("itemIds", []) if cout.get("scrapValue") else []:
                self.monnaie.setdefault(i, []).append(t)

    def _commercant(self, t):
        trader = t.get("traderId", "")
        return self.filtre.nom("Commerçant", trader, self.commercants.get(trader, {}).get("name"),
                               self.trades["traders"].get(trader, {}).get("nameEn"))

    def _etabli(self, ident):
        return self.filtre.nom("Établi", ident, self.benches_fr.get(ident, {}).get("name"),
                               self.benches_en.get(ident, {}).get("name"))

    def pour(self, ident, it):
        fabrication = []
        for r in self.recettes.get(ident, []):
            for produit in r.get("rewards", []):
                if produit["itemId"] not in self.ids_objets or produit["itemId"] == ident:
                    continue
                usage = {"objet": produit["itemId"]}
                if r.get("station") == "bench":
                    etabli = self._etabli(r.get("benchId"))
                    if etabli is None:
                        continue
                    usage["etabli"] = etabli
                    if r.get("benchLevel"):
                        usage["niveau"] = r["benchLevel"]
                if usage not in fabrication:
                    fabrication.append(usage)
        echanges = []
        for t, donne in self.echanges.get(ident, []):
            commercant = self._commercant(t)
            for recu in t.get("rewards", []):
                if commercant and recu["itemId"] in self.ids_objets and recu["itemId"] != ident:
                    e = {"commercant": commercant, "objet": recu["itemId"], "quantite": recu.get("quantity", 1), "donne": donne}
                    if e not in echanges:
                        echanges.append(e)
        monnaie = sorted({c for c in (self._commercant(t) for t in self.monnaie.get(ident, [])) if c})
        u = {}
        if fabrication:
            u["fabrication"] = fabrication
        if self.recherches.get(ident):
            u["recherches"] = sorted(set(self.recherches[ident]))
        if self.meubles.get(ident):
            u["avantPoste"] = sorted(set(self.meubles[ident]))
        if echanges:
            u["echanges"] = echanges
        if monnaie:
            u["echangesValeur"] = monnaie
        amplifiees = [a for a in dict.fromkeys(self.amplification.get(ident, [])) if a in self.ids_objets]
        if amplifiees:
            u["amplification"] = amplifiees
        # Ce qui s'apprend : schémas (débloquent une fabrication), designs (un meuble), apparences.
        categorie = it.get("category") or ""
        if categorie == "Recipe.ItemSkin":
            u["apparence"] = True
        elif categorie.startswith("Recipe") or ident in self.designs:
            produit = it.get("blueprintFor")
            debloque = [produit] if produit in self.ids_objets else []
            u["aApprendre"] = True
            if debloque:
                u["debloque"] = debloque
            if self.designs.get(ident):
                u["meubles"] = self.designs[ident]
        if it.get("group") == "Keys":
            u["cle"] = True
        if it.get("group") in GROUPES_EQUIPEMENT:
            u["equipement"] = True
        return u


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
            **({"emplacement": e} if (e := emplacement(it)) else {}),
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
        # Quête cachée : déblocage de contenu (nombre de manches disputées, niveau, carte visitée…), que le jeu
        # fait tout seul. Jamais affichée, et considérée comme faite par l'appli (ses suites ne l'attendent pas).
        if q.get("hidden"):
            continue
        # Seuls de vrais objets se rapportent de raid : pas les compteurs (« Manches disputées »…).
        requis = {i: n for i, n in objets_de_quete(q.get("objective", {}), {}).items()
                  if items["items"].get(i, {}).get("group") in GROUPES}
        # Quêtes de l'histoire (principales et secondaires) : toutes gardées, pour suivre leur
        # enchaînement ; les autres (quotidiennes, hebdomadaires…) seulement si elles demandent des objets.
        histoire = q.get("category") in CATEGORIES_HISTOIRE
        if not requis and not histoire:
            continue
        nom = filtre.nom("Quête", ident, quests_fr.get(ident, {}).get("name"),
                         quests_en.get(ident, {}).get("name") or q.get("nameEn"))
        if nom is None:
            # Quête technique sans nom français (déblocage de contenu…) : jamais affichée, et
            # considérée comme faite par l'appli (ses suites ne l'attendent pas).
            continue
        commercant = commercants.get(q.get("traderId", ""), {}).get("name")
        if commercant is not None:
            commercant = filtre.nom("Commerçant", q["traderId"], commercant,
                                    trades["traders"].get(q["traderId"], {}).get("nameEn", commercant))
        quetes.append({"id": ident, "nom": nom, "commercant": commercant, "histoire": histoire,
                       "requiert": sorted(r["questId"] for r in q.get("requires", []) if r.get("questId")),
                       "objets": [{"id": i, "quantite": n} for i, n in sorted(requis.items())]})
    ids_quetes = {q["id"] for q in quetes}
    for q in quetes:
        q["requiert"] = [r for r in q["requiert"] if r in ids_quetes]

    # Projets (Expéditions, projets de saison…) : étapes successives, chacune avec ses objets à livrer.
    projets_src = charger(src, "projects.json")["projects"]
    projets_fr, projets_en = charger(src, "projects.text.fr.json"), charger(src, "projects.text.en.json")
    projets = []
    for ident, pr in sorted(projets_src.items()):
        nom = filtre.nom("Projet", ident, projets_fr.get(ident, {}).get("name"),
                         projets_en.get(ident, {}).get("name") or pr.get("nameEn"))
        if nom is None:
            continue
        etapes = []
        for phase in pr.get("phases", []):
            for etape in phase.get("steps", []):
                cle_phase, cle_etape = etape["key"].split(".")[:2]
                textes_fr = projets_fr.get(ident, {}).get("steps", {}).get(cle_phase, {}).get(cle_etape, {})
                textes_en = projets_en.get(ident, {}).get("steps", {}).get(cle_phase, {}).get(cle_etape, {})
                nom_etape = None
                if textes_fr.get("name"):
                    nom_etape = filtre.nom("Étape de projet", f"{ident}/{etape['key']}", textes_fr.get("name"), textes_en.get("name"))
                # Seuls les objets précis comptent ; les objectifs « valeur » ou « quêtes » restent au joueur.
                cout = [{"id": g["itemIds"][0], "quantite": g.get("amount", 1)} for g in etape.get("goals", [])
                        if g.get("goalType") == "items" and len(g.get("itemIds", [])) == 1]
                autres = sum(1 for g in etape.get("goals", []) if not (g.get("goalType") == "items" and len(g.get("itemIds", [])) == 1))
                etapes.append({"nom": nom_etape, "cout": cout, "autresObjectifs": autres})
        if etapes:
            projets.append({"id": ident, "nom": nom, "etapes": etapes})

    cartes_src = charger(src, "maps.json")
    cartes_fr, cartes_en = charger(src, "maps.text.fr.json"), charger(src, "maps.text.en.json")
    cartes, conditions = [], []
    for ident, c in sorted(cartes_src["maps"].items()):
        anglais = cartes_en.get(ident, {}).get("name") or c.get("nameEn")
        nom = filtre.nom("Carte", ident, cartes_fr.get(ident, {}).get("name"), anglais)
        if nom is not None:
            cartes.append({"id": ident, "nom": nom, "cleCalendrier": c.get("nameEn") or anglais})
    for ident, e in sorted(cartes_src["eventTypes"].items()):
        anglais = cartes_en.get(ident, {}).get("name") or e.get("nameEn")
        nom = filtre.nom("Condition de la carte", ident, cartes_fr.get(ident, {}).get("name"), anglais)
        if nom is not None:
            conditions.append({"id": ident, "nom": nom, "majeure": bool(e.get("major")),
                               "cleCalendrier": e.get("nameEn") or anglais})

    importer_plans(src.parent / "map-data", cartes, filtre)

    # Comment obtenir chaque objet : butin (meilleures cartes et zones), achats, fabrication, récompenses.
    recettes = list(charger(src, "recipes.json")["recipes"].values())
    textes_obtention = (commercants, items_fr, items_en, benches_fr, benches_en, quests_fr, quests_en)
    noms_projets = {p["id"]: p["nom"] for p in projets}
    butin = calculer_butin(src.parent / "map-data", ids_objets, cartes, filtre)
    utilisations = Utilisations(src, ids_objets, filtre, (commercants, items_fr, items_en, benches_fr, benches_en))
    for o in objets:
        it = items["items"][o["id"]]
        # Description officielle (« Déverrouille certaines portes… »), si le jeu la traduit vraiment.
        fr, en = items_fr.get(o["id"], {}).get("description"), items_en.get(o["id"], {}).get("description")
        if fr and fr.strip() and fr.strip() != (en or "").strip():
            o["description"] = fr.strip()
        u = utilisations.pour(o["id"], it)
        if u:
            o["utilisations"] = u
        obtention = obtention_objet(o["id"], items["items"][o["id"]], textes_obtention, trades, recettes, filtre, noms_projets)
        if o["id"] in butin:
            obtention = {**butin[o["id"]], **obtention}
        amelioration = amelioration_objet(o["id"], it, items["items"], ids_objets)
        if amelioration:
            obtention["amelioration"] = amelioration
        if obtention:
            o["obtention"] = obtention

    donnees = {
        "versionJeu": version,
        "source": "Données du jeu ARC Raiders (textes officiels d'Embark Studios), via "
                  "github.com/baschny/arcraiders-raider-tools",
        "objets": objets,
        "etablis": etablis,
        "quetes": quetes,
        "projets": projets,
        "cartes": cartes,
        "conditions": conditions,
    }
    # Une référence vers un objet exclu reste (l'appli affiche alors « nom à vérifier en jeu ») :
    # mieux vaut un trou visible qu'un nom inventé.
    orphelins = sorted({e["id"] for e in [x for o in objets for x in o["recyclage"] + o["recuperation"]]
                        + [x for b in etablis for n in b["niveaux"] for x in n["cout"]]
                        + [x for q in quetes for x in q["objets"]]
                        + [x for p in projets for e in p["etapes"] for x in e["cout"]]
                        + [x for o in objets for a in o.get("obtention", {}).get("achats", []) for x in a.get("objets", [])]
                        + [x for o in objets for f in o.get("obtention", {}).get("fabrications", []) for x in f["ingredients"]]
                        + [x for o in objets for x in o.get("obtention", {}).get("amelioration", {}).get("ingredients", [])]
                        + [{"id": f["objet"]} for o in objets for f in o.get("utilisations", {}).get("fabrication", [])]
                        + [{"id": e["objet"]} for o in objets for e in o.get("utilisations", {}).get("echanges", [])]}
                       - ids_objets)

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

    ecrites, manquantes = importer_icones(src.parent.parent / "images", objets, items["items"])
    taille = sum(f.stat().st_size for f in ICONES.glob("*.webp"))
    taille_plans = sum(f.stat().st_size for f in PLANS.glob("*.webp"))
    print(f"Plans : {len(list(PLANS.glob('*.webp')))} cartes ({taille_plans / 1e6:.1f} Mo).")
    print(f"Icônes : {ecrites} objets ({taille / 1e6:.1f} Mo), {len(manquantes)} sans icône dans les données.")

    print(f"Données {version} : {len(objets)} objets, {len(etablis)} établis, {len(quetes)} quêtes, {len(projets)} projets, "
          f"{len(cartes)} cartes, {len(conditions)} conditions ; {len(filtre.exclus)} noms exclus, "
          f"{len(orphelins)} références vers un objet exclu.")


if __name__ == "__main__":
    main()
