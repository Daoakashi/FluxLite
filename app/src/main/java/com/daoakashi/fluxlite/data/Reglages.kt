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
import com.daoakashi.fluxlite.net.JsonFlux
import com.daoakashi.fluxlite.net.Serveur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

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

/**
 * Un autre PC Flux suivi par FluxLite (un directeur et tous ses établissements). Le serveur « courant » reste dans
 * les champs de [Etat] ; les autres gardent ici leur propre session, chiffrée par le coffre-fort Android.
 */
data class ServeurEnregistre(
    val serveur: Serveur,
    val hotes: List<String> = emptyList(),
    val jeton: String? = null,
    val relais: String = "",
    val cgu: String? = null,
    val dernier: Long? = null,
    val affiche: Boolean = true,
)

@Serializable
private data class StockServeur(
    val hote: String, val port: Int, val cle: String, val nom: String = "Flux", val hotes: List<String> = emptyList(),
    val jeton: String? = null, val relais: String = "", val cgu: String? = null, val dernier: Long? = null,
    val affiche: Boolean = true,
)

/** Identifiant de caméra commun à tous les serveurs : « clé du serveur|identifiant ». */
fun idCamera(cleServeur: String, id: String) = if ('|' in id) id else "$cleServeur|$id"

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
    /** Autres PC Flux suivis en même temps que le serveur courant. */
    val autres: List<ServeurEnregistre> = emptyList(),
    /** Les caméras du serveur courant sont-elles affichées (« voir seulement certains serveurs ») ? */
    val courantAffiche: Boolean = true,
    /** Clé du serveur courant mis de côté pendant l'ajout d'un nouveau serveur (pour pouvoir annuler). */
    val ajoutDepuis: String? = null,
) {
    /** Serveurs dont les caméras et les alertes s'affichent, avec leur session. */
    val serveursAffiches: Int get() = (if (serveur != null && jeton != null && courantAffiche) 1 else 0) +
        autres.count { it.jeton != null && it.affiche }
}

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
        val AUTRES = stringPreferencesKey("serveurs_autres")
        val AFFICHE = booleanPreferencesKey("serveur_affiche")
        val AJOUT = stringPreferencesKey("ajout_depuis")
    }

    val etat: Flow<Etat> = context.magasin.data.map { lire(it) }.flowOn(Dispatchers.Default)  // déchiffrement hors du fil principal

    suspend fun maintenant(): Etat = etat.first()

    private fun lire(p: Preferences): Etat {
        val cle = p[K.CLE]
        val hote = p[K.HOTE]
        val serveur = if (cle != null && hote != null) Serveur(hote, p[K.PORT] ?: 47810, cle, p[K.NOM] ?: "Flux") else null
        // Caméras masquées ou muettes des versions 1.2 et avant : identifiants sans la clé du serveur
        fun complets(ids: Set<String>?) = ids.orEmpty().map { if ('|' in it || cle == null) it else "$cle|$it" }.toSet()
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
                camerasMuettes = complets(p[K.MUETTES]),
            ),
            camerasMasquees = complets(p[K.MASQUEES]),
            ecouteArrierePlan = p[K.ECOUTE] ?: true,
            bloquerCaptures = p[K.CAPTURES] ?: true,
            dernierEvenement = p[K.DERNIER],
            relais = p[K.RELAIS].orEmpty(),
            relaisSite = p[K.RELAIS_SITE].orEmpty(),
            autres = lireAutres(p),
            courantAffiche = p[K.AFFICHE] ?: true,
            ajoutDepuis = p[K.AJOUT],
        )
    }

    private fun lireAutres(p: Preferences): List<ServeurEnregistre> = runCatching {
        JsonFlux.decodeFromString<List<StockServeur>>(p[K.AUTRES] ?: "[]").map {
            ServeurEnregistre(Serveur(it.hote, it.port, it.cle, it.nom), it.hotes, it.jeton?.let { j -> Coffre.dechiffrer(j) },
                it.relais, it.cgu, it.dernier, it.affiche)
        }
    }.getOrDefault(emptyList())

    private fun ecrireAutres(m: androidx.datastore.preferences.core.MutablePreferences, liste: List<ServeurEnregistre>) {
        m[K.AUTRES] = JsonFlux.encodeToString(liste.map {
            StockServeur(it.serveur.hote, it.serveur.port, it.serveur.cle, it.serveur.nom, it.hotes,
                it.jeton?.let { j -> Coffre.chiffrer(j) }, it.relais, it.cgu, it.dernier, it.affiche)
        })
    }

    /** Le serveur courant, tel qu'il serait rangé parmi les autres. */
    private fun courant(p: Preferences): ServeurEnregistre? {
        val e = lire(p)
        val s = e.serveur ?: return null
        return ServeurEnregistre(s, e.hotes, e.jeton, e.relais, e.cguAcceptee, e.dernierEvenement, e.courantAffiche)
    }

    private fun installerCourant(m: androidx.datastore.preferences.core.MutablePreferences, s: ServeurEnregistre?) {
        // Les conditions d'utilisation s'acceptent pour chaque serveur (chacun a son propre responsable)
        m.remove(K.HOTE); m.remove(K.HOTES); m.remove(K.CLE); m.remove(K.NOM); m.remove(K.JETON)
        m.remove(K.DERNIER); m.remove(K.PORT); m.remove(K.RELAIS); m.remove(K.CGU)
        m[K.AFFICHE] = s?.affiche ?: true
        if (s == null) return
        m[K.HOTE] = s.serveur.hote; m[K.HOTES] = s.hotes.joinToString(","); m[K.PORT] = s.serveur.port
        m[K.CLE] = s.serveur.cle; m[K.NOM] = s.serveur.nom; m[K.RELAIS] = s.relais
        s.jeton?.let { m[K.JETON] = Coffre.chiffrer(it) }
        s.dernier?.let { m[K.DERNIER] = it }
        s.cgu?.let { m[K.CGU] = it }
    }

    // --- plusieurs PC Flux ----------------------------------------------------------------------------
    /** Met le serveur courant de côté (avec sa session) pour appairer un nouveau PC Flux. */
    suspend fun commencerAjout() = context.magasin.edit { m ->
        val c = courant(m) ?: return@edit
        val autres = lireAutres(m).filter { it.serveur.cle != c.serveur.cle } + c
        ecrireAutres(m, autres)
        m[K.AJOUT] = c.serveur.cle
        installerCourant(m, null)
    }

    /** Abandon de l'ajout : le serveur mis de côté redevient le serveur courant. */
    suspend fun annulerAjout() = context.magasin.edit { m ->
        val cle = m[K.AJOUT] ?: return@edit
        val autres = lireAutres(m)
        val retour = autres.firstOrNull { it.serveur.cle == cle } ?: return@edit
        ecrireAutres(m, autres - retour)
        installerCourant(m, retour)
        m.remove(K.AJOUT)
    }

    /** Connexion réussie sur le serveur courant : l'ajout est terminé (et il n'apparaît pas deux fois). */
    suspend fun terminerAjout() = context.magasin.edit { m ->
        m.remove(K.AJOUT)
        val cle = m[K.CLE] ?: return@edit
        val autres = lireAutres(m)
        if (autres.any { it.serveur.cle == cle }) ecrireAutres(m, autres.filter { it.serveur.cle != cle })
    }

    /** Le serveur `cle` devient le serveur courant (compte, pilotage) ; l'ancien rejoint les autres. */
    suspend fun basculerVers(cle: String) = context.magasin.edit { m ->
        val autres = lireAutres(m)
        val cible = autres.firstOrNull { it.serveur.cle == cle } ?: return@edit
        val ancien = courant(m)
        ecrireAutres(m, autres.filter { it.serveur.cle != cle } + listOfNotNull(ancien))
        installerCourant(m, cible)
        m.remove(K.AJOUT)
    }

    suspend fun retirerAutre(cle: String) = context.magasin.edit { m ->
        ecrireAutres(m, lireAutres(m).filter { it.serveur.cle != cle })
        for (k in listOf(K.MUETTES, K.MASQUEES)) m[k]?.let { ids -> m[k] = ids.filterNot { it.startsWith("$cle|") }.toSet() }
    }

    suspend fun afficherServeur(cle: String, affiche: Boolean) = context.magasin.edit { m ->
        if (m[K.CLE] == cle) {
            m[K.AFFICHE] = affiche
            return@edit
        }
        ecrireAutres(m, lireAutres(m).map { if (it.serveur.cle == cle) it.copy(affiche = affiche) else it })
    }

    suspend fun majAutre(cle: String, bloc: (ServeurEnregistre) -> ServeurEnregistre) = context.magasin.edit { m ->
        ecrireAutres(m, lireAutres(m).map { if (it.serveur.cle == cle) bloc(it) else it })
    }

    suspend fun enregistrerServeur(s: Serveur, hotes: List<String>) = context.magasin.edit {
        it[K.HOTE] = s.hote; it[K.HOTES] = hotes.joinToString(","); it[K.PORT] = s.port; it[K.CLE] = s.cle
        it[K.NOM] = s.nom
    }

    suspend fun renommerServeur(nom: String) = context.magasin.edit { it[K.NOM] = nom }
    suspend fun relais(adresse: String) = context.magasin.edit { it[K.RELAIS] = adresse }
    suspend fun relaisSite(adresse: String) = context.magasin.edit { it[K.RELAIS_SITE] = adresse }

    /** Oublie le serveur courant, sa session et ses caméras masquées ou muettes. S'il reste d'autres serveurs, le
     *  premier qui a une session devient le serveur courant. */
    suspend fun oublierServeur() = context.magasin.edit { m ->
        val cle = m[K.CLE]
        if (cle != null) for (k in listOf(K.MUETTES, K.MASQUEES)) m[k]?.let { ids ->
            m[k] = ids.filterNot { it.startsWith("$cle|") || '|' !in it }.toSet()
        }
        val autres = lireAutres(m)
        val suivant = autres.firstOrNull { it.jeton != null } ?: autres.firstOrNull()
        ecrireAutres(m, autres.filter { it !== suivant })
        installerCourant(m, suivant)
        m.remove(K.AJOUT)
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
