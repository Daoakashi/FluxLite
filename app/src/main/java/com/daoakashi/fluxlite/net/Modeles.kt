package com.daoakashi.fluxlite.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val JsonFlux = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

@Serializable
data class InfoServeur(
    val nom: String = "Flux",
    val api: Int = 1,
    @SerialName("version_flux") val versionFlux: String = "",
    val inscription: Boolean = true,
    @SerialName("validation_manuelle") val validationManuelle: Boolean = true,
    @SerialName("cgu_version") val cguVersion: String = "",
    val responsable: String = "",
    val contact: String = "",
    @SerialName("conservation_journal") val conservationJournal: Int = 7,
    val fonctions: List<String> = emptyList(),
    /** Relais choisi par ce PC Flux (vide = celui publié sur le site). */
    val relais: String = "",
    @SerialName("relais_actif") val relaisActif: Boolean = false,
)

@Serializable
data class SessionAppareil(
    val id: Int,
    @SerialName("cree_le") val creeLe: Double = 0.0,
    @SerialName("expire_le") val expireLe: Double = 0.0,
    val activite: Double = 0.0,
    val appareil: String? = null,
    val ip: String? = null,
    val courante: Boolean = false,
)

@Serializable
data class Profil(
    val id: Int,
    val mail: String,
    val nom: String,
    val prenom: String,
    val societe: String = "",
    val entreprise: String? = null,
    val statut: String = "actif",
    @SerialName("cree_le") val creeLe: Double = 0.0,
    @SerialName("derniere_connexion") val derniereConnexion: Double? = null,
    @SerialName("cgu_version") val cguVersion: String? = null,
    @SerialName("cgu_a_jour") val cguAJour: Boolean = true,
    val sessions: List<SessionAppareil> = emptyList(),
    val serveur: String = "",
    val pilotage: Boolean = false,
) {
    val initiales get() = "${prenom.firstOrNull() ?: ' '}${nom.firstOrNull() ?: ' '}".trim().uppercase()
}

@Serializable
data class Camera(
    val id: String,
    val nom: String,
    val etat: String = "arret",
    val personnes: Int = 0,
    val fps: Double = 0.0,
    val resolution: String? = null,
    val detection: Boolean = true,
    /** Nom du PC Flux, affiché quand plusieurs serveurs sont suivis (rempli par l'application). */
    val serveur: String = "",
) {
    val enDirect get() = etat == "direct" || etat == "alerte"
}

@Serializable
data class ListeCameras(
    val cameras: List<Camera> = emptyList(),
    val serveur: String = "",
    val entreprise: Boolean = true,
    val detection: Boolean = true,
    val pilotage: Boolean = false,
)

/** Pilotage à distance de Flux (serveur Flux 0.9 et plus, compte autorisé). */
@Serializable
data class CameraPilotee(val id: String, val nom: String, val detection: Boolean = true,
                         @SerialName("en_marche") val enMarche: Boolean = false)

@Serializable
data class CanalFlux(val cle: String, val nom: String, val actif: Boolean = false, val configure: Boolean = true)

@Serializable
data class EtatPilotage(
    val detection: Boolean = true,
    val cameras: List<CameraPilotee> = emptyList(),
    val canaux: List<CanalFlux> = emptyList(),
)

@Serializable
data class Evenement(
    val id: Long,
    val heure: Double,
    val camera: String,
    @SerialName("camera_id") val cameraId: String = "",
    val texte: String = "",
    val niveau: String = "alerte",
    val prioritaire: Boolean = false,
    val photo: Boolean = false,
    /** Clé et nom du PC Flux d'origine (remplis par l'application quand plusieurs serveurs sont suivis). */
    val srv: String = "",
    val serveur: String = "",
)

@Serializable
data class Journal(val journal: List<Evenement> = emptyList(), val dernier: Long = 0)

@Serializable
data class Attente(val evenements: List<Evenement> = emptyList(), val dernier: Long = 0)

@Serializable
data class ReponseConnexion(val jeton: String, val compte: Profil)

@Serializable
data class Reponse(
    val ok: Boolean = false,
    val message: String? = null,
    val statut: String? = null,
    val erreur: String? = null,
)
