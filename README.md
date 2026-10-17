# FluxLite — application Android compagnon de Flux

FluxLite montre les caméras de Flux sur téléphone et reçoit leurs alertes. Chaque PC Flux sert de serveur privé : aucune image ne passe par Google ni par un cloud. Hors WiFi, les échanges transitent par le relais Flux, chiffrés de bout en bout : le relais ne peut pas les lire.

## 1. Compiler l'application

1. Installez **Android Studio** (version 2025.1 ou plus récente).
2. *File › Open* et choisissez ce dossier `FluxLite`. Android Studio télécharge Gradle et les bibliothèques (quelques minutes la première fois).
3. Branchez un téléphone (mode développeur + débogage USB) et cliquez ▶ *Run*.

Pour installer sans Android Studio : *Build › Build App Bundle(s) / APK(s) › Build APK(s)*, puis copiez `app/build/outputs/apk/debug/app-debug.apk` sur le téléphone.

## 2. Relier un téléphone à Flux

1. Dans Flux : page **FluxLite**, activez le serveur (Windows demande d'autoriser Flux dans le pare-feu : acceptez pour le réseau privé).
2. Dans FluxLite : **Scanner le QR code**, ou saisissez l'adresse, le port et la clé affichés dans Flux.
3. Acceptez les conditions d'utilisation, puis créez le compte (nom, prénom, mail, mot de passe).
4. Dans Flux : validez le compte (onglet *Comptes*) et rattachez-le à une entreprise. Dans l'onglet *Entreprises*, cochez les caméras visibles par cette entreprise.

**Hors du réseau local** (4G, autre bâtiment), depuis la version 1.2.0 : FluxLite passe tout seul par le **relais Flux**. Il n'y a rien à ouvrir dans la box, aucun VPN, et le QR code reste le même. Le chiffrement reste de bout en bout avec la clé du PC Flux. L'adresse du relais est lue sur le site (`relais.json`), ou donnée par le PC Flux s'il utilise son propre relais. Le relais lui-même s'installe sur un serveur Linux : voir `relais/LISEZMOI.md` dans le dépôt du site. Le code se trouve dans `net/Relais.java` (choix du chemin à chaque connexion) et `net/Api.kt` (`DnsFlux`).

## 3. Sécurité

- **HTTPS (TLS 1.2+)** entre le téléphone et le PC.
- **Clé du serveur** : empreinte de la clé publique du PC. FluxLite la vérifie à chaque connexion et refuse tout autre serveur, même avec un certificat valide ailleurs. Une interception n'est donc pas possible, même sur un Wi-Fi public.
- La clé est créée une fois et stockée dans le dossier `fluxlite` de Flux. Les mises à jour de Flux ne touchent jamais ce dossier. **Sauvegardez-le** : si vous le perdez, il faudra réappairer tous les téléphones.
- **Mots de passe** : stockés sur le PC seulement sous forme d'empreinte scrypt. Blocage de 15 minutes après 5 essais ratés.
- **Session** : chiffrée sur le téléphone par le Keystore Android, révocable depuis le téléphone et depuis Flux.
- **Captures d'écran** bloquées par défaut (réglable dans *Compte*). Aucun aperçu des images sur l'écran verrouillé.

## 4. Mises à jour depuis le site

FluxLite vérifie à chaque ouverture le fichier **https://daoakashi.github.io/flux-web/fluxlite/version.json**. Si une version plus récente existe :
1. l'application affiche un bandeau ;
2. elle télécharge l'APK en HTTPS et vérifie son empreinte SHA-256 ;
3. elle demande à Android de l'installer.

Android vérifie aussi que l'APK est signé avec la même clé que la version installée.

**La publication est automatique**, depuis ce dépôt :

1. **Une seule fois** : dans ce dépôt, **Settings › Secrets and variables › Actions › New repository secret**, nom **`FLUXLITE_SIGNATURE`**, valeur : un mot de passe long (25 caractères ou plus, créé par un gestionnaire de mots de passe). **Gardez-le en lieu sûr et ne le changez jamais** : sans lui, plus aucune mise à jour ne pourra s'installer sur les téléphones. À la première publication, le robot crée la clé de signature et la range chiffrée dans `signature/`.
2. **Facultatif** : un secret **`SITE_TOKEN`** (jeton GitHub « fine-grained » avec le droit *Actions : Read and write* sur le dépôt `flux-web`) pour que le site se mette à jour tout de suite. Sans lui, le site prend la nouvelle version tout seul dans l'heure.
3. **À chaque version** : augmentez `versionCode` (3 → 4…) et `versionName` (« 1.2.0 » → « 1.3.0 »…) dans `app/build.gradle.kts`, puis **Actions › Publier FluxLite › Run workflow**, et écrivez les nouveautés.

Le robot compile et signe l'APK, puis le publie dans les *Releases* de ce dépôt (tag `v1.2.0`…) avec son `version.json`. Le site recopie ce fichier à l'adresse ci-dessus.

Vous pouvez encore compiler à la main dans Android Studio pour tester, avec *Build › Generate APKs*. Cette version « debug » s'installe à côté de la version officielle et ne reçoit pas les mises à jour.

## 5. Android 15 et 16

- **Cible Android 16** (API 36), compatible à partir d'Android 8.
- **Bord à bord imposé** (Android 15+) : barres système transparentes, marges gérées partout.
- **Retour prédictif** activé.
- **Veille des alertes** : service au premier plan de type `specialUse`. Il n'a pas la limite de 6 heures que les services `dataSync` ont depuis Android 15, et il redémarre avec le téléphone. Dans *Compte*, l'application propose de désactiver l'optimisation de batterie pour ne pas retarder les alertes.
- **Notifications** : permission demandée au premier lancement (Android 13+). Il y a des canaux séparés pour les alertes, les alertes silencieuses et la liste noire.
- **Grands écrans et pliables** : l'affichage s'adapte (jusqu'à 6 colonnes). Aucune orientation n'est bloquée, comme l'exige Android 16. Le plein écran propose sa propre rotation.
- **Pages mémoire de 16 Ko** : aucune bibliothèque native, donc compatible d'office.

## 6. Organisation du code

| Dossier | Contenu |
|---|---|
| `net/` | Clé du serveur et épinglage TLS (`Cle.kt`), appels au serveur Flux et vidéo MJPEG (`Api.kt`) |
| `data/` | Réglages (DataStore), coffre Keystore pour la session |
| `notif/` | Service de veille, attente longue des alertes, canaux de notifications |
| `update/` | Mises à jour depuis votre site (PackageInstaller) |
| `ui/` | Thème Flux, icônes dessinées, écrans (appairage, CGU, connexion, caméras, plein écran, journal, compte) |

Côté PC, le serveur est dans `flux/mobile_serveur.py` et la page dans `flux/page_mobile.py` du dépôt [Flux](https://github.com/Daoakashi/Flux). Le site et le relais sont dans le dépôt [flux-web](https://github.com/Daoakashi/flux-web).

## 7. Contrôle à distance (1.1.0)

Avec un PC Flux 0.9 ou plus récent, un compte autorisé (clic droit sur le compte dans Flux › *Autoriser le pilotage à distance*) voit un bouton en haut de l'écran Caméras. Ce bouton permet :

- d'activer ou de désactiver la détection de toutes les caméras ou d'une seule ;
- d'activer ou de désactiver les notifications de Flux : mails, WhatsApp, Telegram, SMS, appel, ntfy, Discord et webhook.

Chaque action est notée dans le journal de Flux.
