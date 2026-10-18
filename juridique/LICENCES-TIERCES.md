# Licences des composants tiers — FluxLite

FluxLite est distribué par DMS Security sous la licence GNU Affero General Public License v3.0 (`../LICENSE`). Il utilise les composants ci-dessous, chacun sous sa propre licence.

| Composant | Licence | Source |
|---|---|---|
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 | https://github.com/JetBrains/kotlin |
| AndroidX (Core, Activity, Lifecycle, DataStore) | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Jetpack Compose, Material 3 | Apache-2.0 | https://developer.android.com/jetpack/compose |
| OkHttp | Apache-2.0 | https://github.com/square/okhttp |
| Google code scanner (ML Kit, `play-services-code-scanner`) | Conditions des API Google (propriétaire) | https://developers.google.com/ml-kit/terms |

## Permission supplémentaire (AGPL-3.0, article 7)

Le lecteur de QR code s'appuie sur une bibliothèque de Google Play services, qui n'est pas un logiciel libre. En tant que titulaire des droits sur FluxLite, DMS Security ajoute la permission suivante :

> Si vous modifiez ce programme, ou toute œuvre couverte, en le liant ou en le combinant avec les bibliothèques Google Play services (y compris ML Kit et `play-services-code-scanner`), ou une version modifiée de celles-ci, contenant des parties couvertes par les conditions de Google, les titulaires des droits de ce programme vous accordent une permission supplémentaire de transmettre l'œuvre qui en résulte. Les sources correspondantes de ces bibliothèques n'ont pas à être fournies.

Cette permission couvre seulement ces bibliothèques. Le reste de FluxLite reste entièrement soumis à l'AGPL-3.0.
