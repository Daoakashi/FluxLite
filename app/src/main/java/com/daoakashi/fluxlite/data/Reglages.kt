package com.daoakashi.fluxlite.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.daoakashi.fluxlite.net.Serveur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.magasin by preferencesDataStore(name = "fluxlite")

/** Affichage des caméras sur l'écran principal (bouton en haut à droite). */
enum class Affichage(val libelle: String, val aide: String) {
    FIL("Fil", "Une caméra par ligne, en grand"),
    GRILLE("Grille", "Deux caméras côte à côte"),
    MOSAIQUE("Mosaïque", "Le plus de caméras possible, sans texte"),
}

/** Réglage de la cloche. */
enum class ModeAlertes(val libelle: String) {
    ACTIVES("Sonores"),
    SILENCIEUSES("Silencieuses"),
    COUPEES("Coupées"),
}

data class Cloche(
    val mode: ModeAlertes = ModeAlertes.ACTIVES,
    val delaiMinutes: Int = 0,
    val pauseJusqua: Long = 0L,
    val camerasMuettes: Set<String> = emptySet(),
) {
    val enPause get() = pauseJusqua > System.currentTimeMillis()
    fun cameraMuette(id: String) = id in camerasMuettes
}

data class Etat(
    val serveur: Serveur? = null,
    val hotes: List<String> = emptyList(),
    val jeton: String? = null,
    val cguAcceptee: String? = null,
    val affichage: Affichage = Affichage.GRILLE,
    val cloche: Cloche = Cloche(),
    val camerasMasquees: Set<String> = emptySet(),
    val ecouteArrierePlan: Boolean = true,
    val bloquerCaptures: Boolean = true,
    val dernierEvenement: Long? = null,
    /** Relais propre au PC Flux (lien d'appairage ou Flux), vide = celui du site. */
    val relais: String = "",
    /** Dernière adresse du relais lue sur le site (pour démarrer sans attendre le réseau). */
    val relaisSite: String = "",
)

class Reglages(private val context: Context) {
    private object K {
        val HOTE = stringPreferencesKey("serveur_hote")
        val HOTES = stringPreferencesKey("serveur_hotes")
        val PORT = intPreferencesKey("serveur_port")
        val CLE = stringPreferencesKey("serveur_cle")
        val NOM = stringPreferencesKey("serveur_nom")
        val JETON = stringPreferencesKey("jeton_chiffre")
        val CGU = stringPreferencesKey("cgu_acceptee")
        val AFFICHAGE = stringPreferencesKey("affichage")
        val MODE = stringPreferencesKey("cloche_mode")
        val DELAI = intPreferencesKey("cloche_delai")
        val PAUSE = longPreferencesKey("cloche_pause")
        val MUETTES = stringSetPreferencesKey("cameras_muettes")
        val MASQUEES = stringSetPreferencesKey("cameras_masquees")
        val ECOUTE = booleanPreferencesKey("ecoute_arriere_plan")
        val CAPTURES = booleanPreferencesKey("bloquer_captures")
        val DERNIER = longPreferencesKey("dernier_evenement")
        val MAJ = longPreferencesKey("maj_verifiee")
        val RELAIS = stringPreferencesKey("serveur_relais")
        val RELAIS_SITE = stringPreferencesKey("relais_site")
    }

    val etat: Flow<Etat> = context.magasin.data.map { lire(it) }.flowOn(Dispatchers.Default)  // déchiffrement hors du fil principal

    suspend fun maintenant(): Etat = etat.first()

    private fun lire(p: Preferences): Etat {
        val cle = p[K.CLE]
        val hote = p[K.HOTE]
        val serveur = if (cle != null && hote != null) Serveur(hote, p[K.PORT] ?: 47810, cle, p[K.NOM] ?: "Flux") else null
        return Etat(
            serveur = serveur,
            hotes = p[K.HOTES]?.split(',')?.filter { it.isNotBlank() } ?: listOfNotNull(hote),
            jeton = p[K.JETON]?.let { Coffre.dechiffrer(it) },
            cguAcceptee = p[K.CGU],
            affichage = p[K.AFFICHAGE]?.let { runCatching { Affichage.valueOf(it) }.getOrNull() } ?: Affichage.GRILLE,
            cloche = Cloche(
                mode = p[K.MODE]?.let { runCatching { ModeAlertes.valueOf(it) }.getOrNull() } ?: ModeAlertes.ACTIVES,
                delaiMinutes = p[K.DELAI] ?: 0,
                pauseJusqua = p[K.PAUSE] ?: 0L,
                camerasMuettes = p[K.MUETTES] ?: emptySet(),
            ),
            camerasMasquees = p[K.MASQUEES] ?: emptySet(),
            ecouteArrierePlan = p[K.ECOUTE] ?: true,
            bloquerCaptures = p[K.CAPTURES] ?: true,
            dernierEvenement = p[K.DERNIER],
            relais = p[K.RELAIS].orEmpty(),
            relaisSite = p[K.RELAIS_SITE].orEmpty(),
        )
    }

    suspend fun enregistrerServeur(s: Serveur, hotes: List<String>) = context.magasin.edit {
        it[K.HOTE] = s.hote; it[K.HOTES] = hotes.joinToString(","); it[K.PORT] = s.port; it[K.CLE] = s.cle
        it[K.NOM] = s.nom
    }

    suspend fun renommerServeur(nom: String) = context.magasin.edit { it[K.NOM] = nom }
    suspend fun relais(adresse: String) = context.magasin.edit { it[K.RELAIS] = adresse }
    suspend fun relaisSite(adresse: String) = context.magasin.edit { it[K.RELAIS_SITE] = adresse }

    /** Oublie le serveur, la session et tout ce qui le concerne (changement de serveur). */
    suspend fun oublierServeur() = context.magasin.edit {
        it.remove(K.HOTE); it.remove(K.HOTES); it.remove(K.CLE); it.remove(K.NOM); it.remove(K.JETON)
        it.remove(K.DERNIER); it.remove(K.PORT); it.remove(K.MUETTES); it.remove(K.MASQUEES); it.remove(K.RELAIS)
    }

    suspend fun enregistrerJeton(jeton: String?) = context.magasin.edit {
        if (jeton == null) { it.remove(K.JETON); it.remove(K.DERNIER) } else it[K.JETON] = Coffre.chiffrer(jeton)
    }

    suspend fun accepterCgu(version: String) = context.magasin.edit { it[K.CGU] = version }
    suspend fun affichage(a: Affichage) = context.magasin.edit { it[K.AFFICHAGE] = a.name }
    suspend fun modeAlertes(m: ModeAlertes) = context.magasin.edit { it[K.MODE] = m.name }
    suspend fun delai(minutes: Int) = context.magasin.edit { it[K.DELAI] = minutes }
    suspend fun pause(jusqua: Long) = context.magasin.edit { it[K.PAUSE] = jusqua }
    suspend fun ecoute(actif: Boolean) = context.magasin.edit { it[K.ECOUTE] = actif }
    suspend fun bloquerCaptures(actif: Boolean) = context.magasin.edit { it[K.CAPTURES] = actif }
    suspend fun dernierEvenement(id: Long) = context.magasin.edit { it[K.DERNIER] = id }

    suspend fun basculerMuette(cameraId: String) = context.magasin.edit {
        val s = it[K.MUETTES] ?: emptySet()
        it[K.MUETTES] = if (cameraId in s) s - cameraId else s + cameraId
    }

    suspend fun masquer(cameraId: String, masquee: Boolean) = context.magasin.edit {
        val s = it[K.MASQUEES] ?: emptySet()
        it[K.MASQUEES] = if (masquee) s + cameraId else s - cameraId
    }

    suspend fun derniereVerifMaj(): Long = context.magasin.data.first()[K.MAJ] ?: 0L
    suspend fun majVerifiee(t: Long) = context.magasin.edit { it[K.MAJ] = t }
}
