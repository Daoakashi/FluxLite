package com.daoakashi.fluxlite.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daoakashi.fluxlite.FluxLiteApp
import com.daoakashi.fluxlite.data.Affichage
import com.daoakashi.fluxlite.data.Etat
import com.daoakashi.fluxlite.data.ModeAlertes
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
        var p = r.compte
        if (p.cguVersion != CGU_VERSION) p = Api(s, r.jeton).modifierProfil(cguVersion = CGU_VERSION)
        _profil.value = p
        resetDernier()  // repartir du présent : pas d'avalanche d'anciennes alertes à la connexion
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

    suspend fun rafraichirCameras() {
        val api = app.api() ?: return
        try {
            val r = api.cameras()
            _cameras.value = EtatCameras(r.cameras, true, null, !r.entreprise, r.detection, r.pilotage)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ErreurApi && e.code == 401) gererErreur(e)
            _cameras.value = _cameras.value.copy(charge = true, erreur = messageErreur(e))
        }
    }

    suspend fun apiCourante(): Api? = app.api()

    fun masquer(cameraId: String, masquee: Boolean) = viewModelScope.launch { reglages.masquer(cameraId, masquee) }
    fun basculerMuette(cameraId: String) = viewModelScope.launch { reglages.basculerMuette(cameraId) }
    fun affichage(a: Affichage) = viewModelScope.launch { reglages.affichage(a) }

    // --- pilotage à distance de Flux ---------------------------------------------------------------
    private val _pilotage = MutableStateFlow(EtatPilotageUi())
    val pilotage = _pilotage.asStateFlow()

    fun chargerPilotage() = viewModelScope.launch {
        val api = app.api() ?: return@launch
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
        val api = app.api() ?: return@launch
        val id = cameraId ?: cle
        val avant = _pilotage.value
        _pilotage.value = avant.copy(enCours = avant.enCours + id, etat = avant.etat?.let { e ->
            when {
                cameraId != null -> e.copy(cameras = e.cameras.map { if (it.id == cameraId) it.copy(detection = valeur) else it })
                cle == "detection.actif" -> e.copy(detection = valeur)
                else -> e.copy(canaux = e.canaux.map { if (it.cle == cle) it.copy(actif = valeur) else it })
            }
        })
        try {
            val e = api.piloter(cle, valeur, cameraId)
            _pilotage.value = EtatPilotageUi(e, enCours = _pilotage.value.enCours - id)
            rafraichirCameras()
        } catch (e: Exception) {
            if (e is ErreurApi && e.code == 401) gererErreur(e)
            _pilotage.value = avant.copy(enCours = avant.enCours - id)
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
    fun chargerJournal(reinitialiser: Boolean = true, camera: String? = _journal.value.filtreCamera) = viewModelScope.launch {
        val api = app.api() ?: return@launch
        val avant = if (reinitialiser) null else _journal.value.evenements.lastOrNull()?.id
        if (!reinitialiser && (_journal.value.fin || _journal.value.chargement)) return@launch
        _journal.value = _journal.value.copy(chargement = true, filtreCamera = camera, erreur = null,
            evenements = if (reinitialiser && camera != _journal.value.filtreCamera) emptyList() else _journal.value.evenements)
        try {
            val r = api.journal(avant, camera)
            val liste = if (reinitialiser) r.journal else _journal.value.evenements + r.journal
            _journal.value = EtatJournal(liste, false, r.journal.size < 40, null, camera)
        } catch (e: Exception) {
            if (e is ErreurApi && e.code == 401) gererErreur(e)
            _journal.value = _journal.value.copy(chargement = false, erreur = messageErreur(e))
        }
    }

    // --- veille des alertes ---------------------------------------------------------------------
    /** Appelée quand l'appli est au premier plan et connectée : démarre la veille (service ou écoute locale). */
    fun demarrerVeille() = viewModelScope.launch {
        val e = reglages.maintenant()
        if (e.jeton == null) return@launch
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
