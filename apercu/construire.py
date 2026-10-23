#!/usr/bin/env python3
"""Construit l'aperçu web : index.html (données du jeu intégrées), icones.json (icônes en data: URI)
cartes/ (plans des cartes) et fonds/ (captures en jeu de « Meilleure carte »)."""
import base64, json, pathlib, shutil, sys
ici = pathlib.Path(__file__).parent
depot = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ici.parent
donnees = (depot / "core/src/main/resources/donnees-jeu.json").read_text(encoding="utf-8")
json.loads(donnees)
modele = (ici / "modele.html").read_text(encoding="utf-8")
fonds = sorted(f.stem for f in (depot / "app/src/main/assets/fonds").glob("*.webp"))
page = modele.replace("/*DONNEES*/", donnees.replace("</", "<\\/")).replace("/*FONDS*/", json.dumps(fonds))
(ici / "index.html").write_text(page, encoding="utf-8")
icones = {f.stem: "data:image/webp;base64," + base64.b64encode(f.read_bytes()).decode() for f in sorted((depot / "app/src/main/assets/objets").glob("*.webp"))}
(ici / "icones.json").write_text(json.dumps(icones, separators=(",", ":")), encoding="utf-8")
plans = ici / "cartes"
shutil.rmtree(plans, ignore_errors=True)
plans.mkdir()
for f in sorted((depot / "app/src/main/assets/cartes").glob("*.webp")):
    shutil.copy(f, plans / f.name)
captures = ici / "fonds"
shutil.rmtree(captures, ignore_errors=True)
captures.mkdir()
for f in sorted((depot / "app/src/main/assets/fonds").glob("*.webp")):
    shutil.copy(f, captures / f.name)
print(f"index.html {len(page)/1e6:.2f} Mo, icones.json {len(icones)} icônes {(ici/'icones.json').stat().st_size/1e6:.2f} Mo")
