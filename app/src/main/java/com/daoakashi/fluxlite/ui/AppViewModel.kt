package com.daoakashi.fluxlite.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daoakashi.fluxlite.FluxLiteApp
import com.daoakashi.fluxlite.data.Affichage
import com.daoakashi.fluxlite.data.Etat
import com.daoakashi.fluxlite.data.ModeAlertes
import com.daoakashi.fluxlite.data.ServeurEnregistre
import com.daoakashi.fluxlite.data.idCamera
import com.daoakashi.fluxlite.net.Api
import com.daoakashi.fluxlite.net.Camera
import com.daoakashi.fluxlite.net.CleServeur
import com.daoakashi.fluxlite.net.ErreurApi
import com.daoakashi.fluxlite.net.EtatPilotage
import com.daoakashi.fluxlite.net.Evenement
import com.daoakashi.fluxlite.net.InfoServeur
import com.daoakashi.fluxlite.net.Profil
import com.daoakashi.fluxlite.net.Relais
import com.daoakashi.fluxlite.net.Serveur
import com.daoakashi.fluxlite.net.messageErreur
import com.daoakashi.fluxlite.notif.AlertService
import com.daoakashi.fluxlite.notif.Ecoute
import com.daoakashi.fluxlite.update.MiseAJour
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Version des conditions d'utilisation affichées dans l'application (identique côté serveur Flux). */
const val CGU_VERSION = "2026-10"

enum class Etape { CHARGEMENT, APPAIRAGE, CGU, CONNEXION, PRINCIPAL }

data class EtatCameras(
    val cameras: List<Camera> = emptyList(),
    val charge: Boolean = false,
    val erreur: String? = null,
    val sansEntreprise: Boolean = false,
    val detectionGlobale: Boolean = true,
    val pilotage: Boolean = false,
)

data class EtatPilotageUi(
    val etat: EtatPilotage? = null,
    val chargement: Boolean = false,
    val erreur: String? = null,
    val enCours: Set<String> = emptySet(),
)

data class EtatJournal(
    val evenements: List<Evenement> = emptyList(),
    val chargement: Boolean = false,
    val fin: Boolean = false,
    val erreur: String? = null,
    val filtreCamera: String? = null,
)

/** Données d'appairage lues dans le QR code (ou le lien fluxlite://). relais : propre au PC Flux, souvent vide. */
data class Appairage(val hotes: List<String>, val port: Int, val cle: String, val nom: String, val relais: String = "")

fun lireLienAppairage(texte: String): Appairage? = try {
    val u = Uri.parse(texte.trim())
    if (u.scheme != "fluxlite" || u.host != "appairer") null else {
        val cle = CleServeur.normaliser(u.getQueryParameter("k").orEmpty())
        val hotes = u.getQueryParameter("h").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (!CleServeur.estValide(cle) || hotes.isEmpty()) null
        else Appairage(hotes, u.getQueryParameter("p")?.toIntOrNull() ?: 47810, cle, u.getQueryParameter("n") ?: "Flux",
            Relais.lire(u.getQueryParameter("r"))?.toString().orEmpty())
    }
} catch (_: Exception) {
    null
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as FluxLiteApp
    private val reglages = app.reglages

    val etat: StateFlow<Etat?> = reglages.etat.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val etape: StateFlow<Etape> = reglages.etat.map { e ->
        when {
            e.serveur == null -> Etape.APPAIRAGE
            e.cguAcceptee != CGU_VERSION -> Etape.CGU
            e.jeton == null -> Etape.CONNEXION
            else -> Etape.PRINCIPAL
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Etape.CHARGEMENT)

    private val _info = MutableStateFlow<InfoServeur?>(null)
    val info = _info.asStateFlow()

    private val _profil = MutableStateFlow<Profil?>(null)
    val profil = _profil.asStateFlow()

    private val _cameras = MutableStateFlow(EtatCameras())
    val cameras = _cameras.asStateFlow()

    private val _journal = MutableStateFlow(EtatJournal())
    val journal = _journal.asStateFlow()

    /** Messages courts affichés en bas de l'écran. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** Appairage reçu par un lien (QR code scanné avec l'appareil photo du téléphone). */
    val appairageRecu = MutableStateFlow<Appairage?>(null)

    /** Caméra à ouvrir en grand (notification touchée). */
    val cameraDemandee = MutableStateFlow<String?>(null)

    /** « direct » (même réseau que le PC Flux) ou « internet » (relais Flux). */
    val route: StateFlow<String> = app.route.asStateFlow()

    private val _occupe = MutableStateFlow(false)
    val occupe = _occupe.asStateFlow()

    private var ecouteLocale: Job? = null

    /** Profil du compte sur le serveur précédent : repris pour créer le compte sur un nouveau serveur. */
    private val _profilConnu = MutableStateFlow<Profil?>(null)
    val profilConnu = _profilConnu.asStateFlow()

    init {
        viewModelScope.launch {
            app.sessionPerdue.collect { m ->
                _profil.value = null
                messages.tryEmit(m)
            }
        }
        viewModelScope.launch {
            // Informations du serveur (nom, responsable RGPD…) dès qu'il est connu.
            reglages.etat.map { it.serveur }.collect { s -> if (s != null) chargerInfo(s) else _info.value = null }
        }
    }

    private suspend fun chargerInfo(s: Serveur) {
        try {
            val i = Api(s).info()
            _info.value = i
            if (i.nom != s.nom) reglages.renommerServeur(i.nom)
            // Le PC Flux peut changer de relais : on suit (vide = relais du site).
            val relais = Relais.lire(i.relais)?.toString().orEmpty()
            if (i.fonctions.contains("relais") && relais != reglages.maintenant().relais) reglages.relais(relais)
        } catch (_: Exception) {
        }
    }

    private fun lancer(bloc: suspend () -> Unit) = viewModelScope.launch {
        _occupe.value = true
        try {
            bloc()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            gererErreur(e)
        } finally {
            _occupe.value = false
        }
    }

    private suspend fun gererErreur(e: Exception) {
        if (e is ErreurApi && e.code == 401 && etape.value == Etape.PRINCIPAL) {
            reglages.enregistrerJeton(null)
            _profil.value = null
        }
        messages.tryEmit(messageErreur(e))
    }

    // --- appairage ------------------------------------------------------------------------
    fun appairer(hotes: List<String>, port: Int, cleSaisie: String, relais: String = "", surSucces: () -> Unit = {}) {
        val cle = CleServeur.normaliser(cleSaisie)
        if (!CleServeur.estValide(cle)) {
            messages.tryEmit("Clé invalide : 32 caractères, comme affiché dans Flux (page FluxLite).")
            return
        }
        if (relais.isNotBlank()) Relais.definirPropre(relais)
        if (hotes.isEmpty() && Relais.actif() == null) {
            messages.tryEmit("Indiquez l'adresse du PC Flux (aucun relais Internet connu pour l'instant).")
            return
        }
        lancer {
            val (serveur, info) = Api.appairer(hotes, port, cle)
            reglages.enregistrerServeur(serveur, (listOf(serveur.hote) + hotes).filter { it.isNotBlank() }.distinct())
            reglages.relais(Relais.lire(info.relais)?.toString() ?: relais)
            _info.value = info
            val par = if (Relais.route(cle) == "internet") " par Internet" else ""
            messages.tryEmit("Connecté à « ${info.nom} »$par. Clé vérifiée.")
            surSucces()
        }
    }

    fun oublierServeur() = lancer {
        runCatching { app.api()?.deconnexion() }
        AlertService.arreter(getApplication())
        reglages.oublierServeur()
        _profil.value = null
        _cameras.value = EtatCameras()
        _journal.value = EtatJournal()
    }

    fun accepterCgu() = lancer {
        reglages.accepterCgu(CGU_VERSION)
        // Déjà connecté (nouvelle version des conditions) : on l'enregistre aussi sur le serveur.
        app.api()?.let { _profil.value = it.modifierProfil(cguVersion = CGU_VERSION) }
    }

    // --- compte ----------------------------------------------------------------------------
    fun connexion(mail: String, motDePasse: String) = lancer {
        val s = reglages.maintenant().serveur ?: return@lancer
        val r = Api(s).connexion(mail.trim(), motDePasse)
        reglages.enregistrerJeton(r.jeton)
        reglages.terminerAjout()
        var p = r.compte
        if (p.cguVersion != CGU_VERSION) p = Api(s, r.jeton).modifierProfil(cguVersion = CGU_VERSION)
        _profil.value = p
        resetDernier()  // repartir du présent : pas d'avalanche d'anciennes alertes à la connexion
        redemarrerVeille()
    }

    private suspend fun resetDernier() {
        app.api()?.let { api -> runCatching { reglages.dernierEvenement(api.attente(null).dernier) } }
    }

    /** Renvoie le message du serveur (compte créé, en attente de validation…). */
    fun inscription(mail: String, motDePasse: String, nom: String, prenom: String, societe: String,
                    surSucces: (String, Boolean) -> Unit) = lancer {
        val s = reglages.maintenant().serveur ?: return@lancer
        val r = Api(s).inscription(mail.trim(), motDePasse, nom.trim(), prenom.trim(), societe.trim(), CGU_VERSION)
        val actif = r.statut == "actif"
        surSucces(r.message ?: "Compte créé.", actif)
        if (actif) connexion(mail, motDePasse)
    }

    fun deconnexion() = lancer {
        runCatching { app.api()?.deconnexion() }
        AlertService.arreter(getApplication())
        reglages.enregistrerJeton(null)
        _profil.value = null
        _cameras.value = EtatCameras()
        _journal.value = EtatJournal()
        // D'autres serveurs suivis ont encore une session : le premier devient le serveur courant
        reglages.maintenant().autres.firstOrNull { it.jeton != null }?.let { basculer(it.serveur.cle) }
    }

    // --- plusieurs PC Flux --------------------------------------------------------------------------
    /** Ajouter un PC Flux : le serveur courant est mis de côté (avec sa session) le temps de l'appairage. */
    fun ajouterServeur() = lancer {
        _profilConnu.value = _profil.value ?: _profilConnu.value
        reglages.commencerAjout()
        _profil.value = null
        _info.value = null
    }

    fun annulerAjout() = lancer {
        reglages.annulerAjout()
        chargerProfil()
        redemarrerVeille()
    }

    /** Le serveur `cle` devient le serveur courant (compte, pilotage par défaut). */
    fun basculerVers(cle: String) = lancer { basculer(cle) }

    private suspend fun basculer(cle: String) {
        _profilConnu.value = _profil.value ?: _profilConnu.value
        reglages.basculerVers(cle)
        _profil.value = null
        _pilotageSrv.value = ""
        chargerProfil()
        rafraichirCameras()
        redemarrerVeille()
    }

    fun retirerServeur(cle: String) = lancer {
        val e = reglages.maintenant()
        if (e.serveur?.cle == cle) {
            runCatching { app.api()?.deconnexion() }
            reglages.oublierServeur()
            _profil.value = null
            chargerProfil()
        } else {
            e.autres.firstOrNull { it.serveur.cle == cle }?.let { a ->
                runCatching { if (a.jeton != null) Api(a.serveur, a.jeton).deconnexion() }
            }
            reglages.retirerAutre(cle)
        }
        rafraichirCameras()
        redemarrerVeille()
    }

    fun afficherServeur(cle: String, affiche: Boolean) = viewModelScope.launch {
        reglages.afficherServeur(cle, affiche)
        rafraichirCameras()
        redemarrerVeille()
    }

    /** Une autre session est refusée par son serveur (expirée, compte suspendu) : on l'oublie et on prévient. */
    private suspend fun sessionAutrePerdue(a: ServeurEnregistre) {
        reglages.majAutre(a.serveur.cle) { it.copy(jeton = null) }
        messages.tryEmit("${a.serveur.nom} : session expirée. Reconnectez-le dans Compte › Serveurs Flux.")
    }

    private fun redemarrerVeille() = viewModelScope.launch {
        AlertService.arreter(getApplication())
        ecouteLocale?.cancel()
        ecouteLocale = null
        demarrerVeille()
    }

    fun chargerProfil() = viewModelScope.launch {
        try {
            app.api()?.let { _profil.value = it.profil() }
        } catch (e: Exception) {
            if (e is ErreurApi) gererErreur(e)
        }
    }

    fun modifierProfil(prenom: String, nom: String) = lancer {
        app.api()?.let { _profil.value = it.modifierProfil(nom = nom.trim(), prenom = prenom.trim()) }
        messages.tryEmit("Profil enregistré.")
    }

    fun changerMotDePasse(ancien: String, nouveau: String, surSucces: () -> Unit) = lancer {
        val r = app.api()?.changerMotDePasse(ancien, nouveau) ?: return@lancer
        messages.tryEmit(r.message ?: "Mot de passe changé.")
        surSucces()
        chargerProfil()
    }

    fun revoquer(id: Int?) = lancer {
        app.api()?.let { _profil.value = it.revoquerSession(id) }
        messages.tryEmit(if (id == null) "Vos autres appareils sont déconnectés." else "Appareil déconnecté.")
    }

    suspend fun exporterDonnees(): String? = try {
        app.api()?.exporterDonnees()
    } catch (e: Exception) {
        gererErreur(e); null
    }

    fun supprimerCompte(motDePasse: String) = lancer {
        val r = app.api()?.supprimerCompte(motDePasse) ?: return@lancer
        AlertService.arreter(getApplication())
        reglages.enregistrerJeton(null)
        _profil.value = null
        messages.tryEmit(r.message ?: "Compte supprimé.")
    }

    // --- caméras -----------------------------------------------------------------------------
    /** Rafraîchit la liste toutes les 3 s tant que l'écran des caméras est visible. */
    suspend fun boucleCameras() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            rafraichirCameras()
            delay(3_000)
        }
    }

    /** Caméras de tous les PC Flux affichés, repérées par « clé du serveur|identifiant ». */
    suspend fun rafraichirCameras() {
        val e = reglages.maintenant()
        val courant = e.serveur
        val autres = e.autres.filter { it.jeton != null && it.affiche }
        val plusieurs = e.serveursAffiches > 1
        val liste = mutableListOf<Camera>()
        var erreur: String? = null
        var sansEntreprise = false
        var detection = true
        var pilotage = false
        val api = app.api()
        if (api != null && courant != null && (e.courantAffiche || autres.isEmpty())) {
            try {
                val r = api.cameras()
                sansEntreprise = !r.entreprise
                detection = r.detection
                pilotage = r.pilotage
                liste += r.cameras.map { it.copy(id = idCamera(courant.cle, it.id), serveur = if (plusieurs) courant.nom else "") }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                if (ex is ErreurApi && ex.code == 401) gererErreur(ex)
                erreur = messageErreur(ex)
            }
        }
        for (a in autres) {
            try {
                val r = Api(a.serveur, a.jeton).cameras()
                pilotage = pilotage || r.pilotage
                liste += r.cameras.map { it.copy(id = idCamera(a.serveur.cle, it.id), serveur = if (plusieurs) a.serveur.nom else "") }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                if (ex is ErreurApi && ex.code == 401) sessionAutrePerdue(a)
                else if (erreur == null) erreur = "${a.serveur.nom} : ${messageErreur(ex)}"
            }
        }
        _cameras.value = if (liste.isEmpty() && erreur != null) _cameras.value.copy(charge = true, erreur = erreur)
        else EtatCameras(liste, true, null, sansEntreprise && liste.isEmpty(), detection, pilotage)
    }

    suspend fun apiCourante(): Api? = app.api()

    /** Client du serveur d'une caméra (« clé|id ») et l'identifiant à lui envoyer. */
    suspend fun apiPour(idCamera: String): Pair<Api, String>? = app.apiPour(idCamera)

    /** Client d'un PC Flux suivi (vide = serveur courant). */
    suspend fun apiDe(cle: String): Api? = app.apiDe(cle)

    fun masquer(cameraId: String, masquee: Boolean) = viewModelScope.launch { reglages.masquer(cameraId, masquee) }
    fun basculerMuette(cameraId: String) = viewModelScope.launch { reglages.basculerMuette(cameraId) }
    fun affichage(a: Affichage) = viewModelScope.launch { reglages.affichage(a) }

    // --- pilotage à distance de Flux ---------------------------------------------------------------
    private val _pilotage = MutableStateFlow(EtatPilotageUi())
    val pilotage = _pilotage.asStateFlow()

    /** Serveur piloté dans la feuille de pilotage (vide = serveur courant). */
    private val _pilotageSrv = MutableStateFlow("")
    val pilotageSrv = _pilotageSrv.asStateFlow()

    /** Envoyer aussi les réglages généraux aux autres serveurs suivis. */
    val piloterTous = MutableStateFlow(false)

    fun choisirPilotage(cle: String) {
        _pilotageSrv.value = cle
        _pilotage.value = EtatPilotageUi()
        chargerPilotage()
    }

    private suspend fun clePilotage(): String = _pilotageSrv.value.ifEmpty { reglages.maintenant().serveur?.cle.orEmpty() }

    fun chargerPilotage() = viewModelScope.launch {
        val api = app.apiDe(_pilotageSrv.value) ?: return@launch
        _pilotage.value = _pilotage.value.copy(chargement = true, erreur = null)
        try {
            _pilotage.value = EtatPilotageUi(api.pilotage())
        } catch (e: Exception) {
            if (e is ErreurApi && e.code == 401) gererErreur(e)
            _pilotage.value = _pilotage.value.copy(chargement = false,
                erreur = if (e is ErreurApi && e.code == 404) "Ce PC Flux est trop ancien : mettez Flux à jour (0.9 ou plus)."
                else messageErreur(e))
        }
    }

    /** Change un réglage de Flux à distance. L'interrupteur bouge tout de suite ; il revient en arrière si Flux refuse. */
    fun piloter(cle: String, valeur: Boolean, cameraId: String? = null) = viewModelScope.launch {
        val srvPilote = clePilotage()
        // Commande de caméra : envoyée au serveur de la caméra. Réglage général : au serveur choisi dans la feuille.
        val (api, idBrut) = if (cameraId != null) app.apiPour(cameraId) ?: return@launch
        else (app.apiDe(_pilotageSrv.value) ?: return@launch) to null
        val surCeServeur = api.serveur.cle == srvPilote
        val id = cameraId ?: cle
        val avant = _pilotage.value
        if (surCeServeur) _pilotage.value = avant.copy(enCours = avant.enCours + id, etat = avant.etat?.let { e ->
            when {
                cameraId != null -> e.copy(cameras = e.cameras.map { if (it.id == idBrut) it.copy(detection = valeur) else it })
                cle == "detection.actif" -> e.copy(detection = valeur)
                else -> e.copy(canaux = e.canaux.map { if (it.cle == cle) it.copy(actif = valeur) else it })
            }
        })
        try {
            val e = api.piloter(cle, valeur, idBrut)
            if (surCeServeur) _pilotage.value = EtatPilotageUi(e, enCours = _pilotage.value.enCours - id)
            if (cameraId == null && piloterTous.value) {
                val etat = reglages.maintenant()
                val autres = (listOfNotNull(etat.serveur?.cle) + etat.autres.filter { it.jeton != null }.map { it.serveur.cle })
                    .filter { it != api.serveur.cle }
                var refus = 0
                for (c in autres) runCatching { app.apiDe(c)?.piloter(cle, valeur, null) }.onFailure { refus++ }
                messages.tryEmit(if (refus == 0) "Réglage envoyé à ${autres.size + 1} serveurs."
                else "$refus serveur(s) n'ont pas accepté le réglage (droit de pilotage ?).")
            }
            rafraichirCameras()
        } catch (e: Exception) {
            if (e is ErreurApi && e.code == 401 && api.serveur.cle == reglages.maintenant().serveur?.cle) gererErreur(e)
            if (surCeServeur) _pilotage.value = avant.copy(enCours = avant.enCours - id)
            messages.tryEmit(messageErreur(e))
        }
    }

    // --- cloche ----------------------------------------------------------------------------------
    fun modeAlertes(m: ModeAlertes) = viewModelScope.launch { reglages.modeAlertes(m) }
    fun delai(minutes: Int) = viewModelScope.launch { reglages.delai(minutes) }
    fun pause(heures: Int) = viewModelScope.launch {
        reglages.pause(if (heures <= 0) 0L else System.currentTimeMillis() + heures * 3_600_000L)
    }

    // --- journal ---------------------------------------------------------------------------------
    /** Plus ancien identifiant chargé par serveur (null : tout est chargé), pour « charger la suite ». */
    private val plusAnciens = HashMap<String, Long?>()

    /** Journal de tous les PC Flux affichés (ou du serveur de la caméra choisie), le plus récent en haut. */
    fun chargerJournal(reinitialiser: Boolean = true, camera: String? = _journal.value.filtreCamera) = viewModelScope.launch {
        if (!reinitialiser && (_journal.value.fin || _journal.value.chargement)) return@launch
        val e = reglages.maintenant()
        val courant = e.serveur ?: return@launch
        val cleCamera = camera?.takeIf { '|' in it }?.substringBefore('|')
        val cibles = buildList {
            if (e.jeton != null && (cleCamera == courant.cle || (cleCamera == null && e.courantAffiche))) add(courant)
            e.autres.filter { it.jeton != null && (cleCamera == it.serveur.cle || (cleCamera == null && it.affiche)) }
                .forEach { add(it.serveur) }
            if (isEmpty() && cleCamera == null && e.jeton != null) add(courant)
        }
        if (cibles.isEmpty()) return@launch
        _journal.value = _journal.value.copy(chargement = true, filtreCamera = camera, erreur = null,
            evenements = if (reinitialiser && camera != _journal.value.filtreCamera) emptyList() else _journal.value.evenements)
        if (reinitialiser) plusAnciens.clear()
        val idBrut = camera?.substringAfter('|')
        val plusieurs = e.serveursAffiches > 1
        val nouveaux = mutableListOf<Evenement>()
        var erreur: String? = null
        for (s in cibles) {
            if (!reinitialiser && plusAnciens.containsKey(s.cle) && plusAnciens[s.cle] == null) continue
            try {
                val api = app.apiDe(s.cle) ?: continue
                val r = api.journal(if (reinitialiser) null else plusAnciens[s.cle], idBrut)
                plusAnciens[s.cle] = if (r.journal.size < 40) null else r.journal.last().id
                nouveaux += r.journal.map { it.copy(srv = s.cle, serveur = if (plusieurs) s.nom else "",
                    cameraId = idCamera(s.cle, it.cameraId)) }
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                if (ex is ErreurApi && ex.code == 401 && s.cle == courant.cle) gererErreur(ex)
                plusAnciens[s.cle] = null
                erreur = messageErreur(ex)
            }
        }
        nouveaux.sortByDescending { it.heure }
        val liste = if (reinitialiser) nouveaux else _journal.value.evenements + nouveaux
        _journal.value = EtatJournal(liste, false, plusAnciens.values.all { it == null },
            if (liste.isEmpty()) erreur else null, camera)
    }

    // --- veille des alertes ---------------------------------------------------------------------
    /** Appelée quand l'appli est au premier plan et connectée : démarre la veille (service ou écoute locale). */
    fun demarrerVeille() = viewModelScope.launch {
        val e = reglages.maintenant()
        if (e.jeton == null && e.autres.none { it.jeton != null }) return@launch
        if (e.ecouteArrierePlan) {
            ecouteLocale?.cancel()
            AlertService.demarrer(getApplication())
        } else if (ecouteLocale?.isActive != true) {
            ecouteLocale = viewModelScope.launch {
                Ecoute(getApplication()) { if (_journal.value.evenements.isNotEmpty()) chargerJournal() }.boucle()
            }
        }
    }

    fun ecouteArrierePlan(actif: Boolean) = viewModelScope.launch {
        reglages.ecoute(actif)
        if (!actif) AlertService.arreter(getApplication())
        demarrerVeille()
    }

    fun bloquerCaptures(actif: Boolean) = viewModelScope.launch { reglages.bloquerCaptures(actif) }

    // --- mises à jour -------------------------------------------------------------------------------
    fun verifierMaj(silencieux: Boolean) = viewModelScope.launch {
        // Vérification discrète une fois par lancement de l'application (un petit fichier sur le site).
        if (silencieux && MiseAJour.etat.value != com.daoakashi.fluxlite.update.EtatMaj.Rien) return@launch
        reglages.majVerifiee(System.currentTimeMillis())
        MiseAJour.verifier(silencieux)
    }
}
