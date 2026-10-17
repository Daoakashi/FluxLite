"""Aide à la publication de FluxLite (utilisé par le bouton « Publier FluxLite »).

  python3 .github/scripts/fluxlite.py version   -> lit versionCode et versionName (sorties GitHub : code, nom, tag, apk)
  python3 .github/scripts/fluxlite.py publier   -> copie l'APK signé et écrit version.json dans « publication/ »
"""
import hashlib
import json
import os
import re
import shutil
import sys

GRADLE = "app/build.gradle.kts"
APK_COMPILE = "app/build/outputs/apk/release/app-release.apk"


def lire():
    with open(GRADLE, encoding="utf-8") as f:
        t = f.read()
    code = re.search(r"versionCode\s*=\s*(\d+)", t)
    nom = re.search(r'versionName\s*=\s*"([^"]+)"', t)
    if not code or not nom:
        sys.exit(f"versionCode ou versionName introuvable dans {GRADLE}")
    return int(code.group(1)), nom.group(1)


def sortie(**valeurs):
    lignes = [f"{k}={v}" for k, v in valeurs.items()]
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as f:
            f.write("\n".join(lignes) + "\n")
    print("\n".join(lignes))


def main():
    code, nom = lire()
    tag, apk = f"v{nom}", f"FluxLite-{nom}.apk"
    if sys.argv[1:] == ["version"]:
        return sortie(code=code, nom=nom, tag=tag, apk=apk)
    if not os.path.isfile(APK_COMPILE):
        sys.exit("APK introuvable : la compilation a échoué ou n'est pas signée (secret FLUXLITE_SIGNATURE ?).")
    os.makedirs("publication", exist_ok=True)
    shutil.copyfile(APK_COMPILE, os.path.join("publication", apk))
    h = hashlib.sha256()
    with open(APK_COMPILE, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    depot = os.environ.get("GITHUB_REPOSITORY", "Daoakashi/FluxLite")
    donnees = {
        "versionCode": code, "versionName": nom,
        "apk": f"https://github.com/{depot}/releases/download/{tag}/{apk}",
        "sha256": h.hexdigest(), "notes": os.environ.get("NOTES", "").strip(),
        "obligatoire": os.environ.get("OBLIGATOIRE", "false") == "true", "minSdk": 26,
    }
    with open("publication/version.json", "w", encoding="utf-8") as f:
        json.dump(donnees, f, indent=2, ensure_ascii=False)
    print(json.dumps(donnees, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
