package com.daoakashi.fluxlite.notif

import android.content.Context
import com.daoakashi.fluxlite.FluxLiteApp
import com.daoakashi.fluxlite.data.idCamera
import com.daoakashi.fluxlite.net.Api
import com.daoakashi.fluxlite.net.ErreurApi
import com.daoakashi.fluxlite.net.Evenement
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Écoute les alertes du serveur Flux par « attente longue » : une requête reste ouverte jusqu'à la prochaine
 * alerte (ou ~40 s), puis on recommence. Aucune donnée ne passe par Google ni par un service tiers.
 * Utilisée par le service d'arrière-plan, ou par l'application ouverte quand ce service est désactivé.
 * Une écoute par PC Flux suivi et affiché : le serveur courant et chacun des autres.
 */
class Ecoute(private val context: Context, private val surEvenements: (List<Evenement>) -> Unit = {}) {
    private val app = context.applicationContext as FluxLiteApp
    private val derniersEnvois = HashMap<String, Long>()
    private val retenues = HashMap<String, Int>()

    /** Tourne jusqu'à l'annulation ou la perte de toutes les sessions. */
    suspend fun boucle(): Boolean = coroutineScope {
        val e = app.reglages.maintenant()
        for (a in e.autres.filter { it.jeton != null && it.affiche }) launch { boucleAutre(a.serveur.cle) }
        if (e.courantAffiche || e.autres.none { it.jeton != null && it.affiche }) boucleCourant() else true
    }

    /** Serveur courant. Renvoie false si la session est perdue. */
    private suspend fun boucleCourant(): Boolean {
        var attente = 2_000L
        while (coroutineContext.isActive) {
            val api = app.api() ?: return false
            try {
                var depuis = app.reglages.maintenant().dernierEvenement
                if (depuis == null) {
                    depuis = api.attente(null).dernier
                    app.reglages.dernierEvenement(depuis)
                }
                val r = api.attente(depuis, 40)
                if (r.evenements.isNotEmpty()) {
                    val e = app.reglages.maintenant()
                    val cle = api.serveur.cle
                    val nom = if (e.serveursAffiches > 1) api.serveur.nom else ""
                    val ev = r.evenements.map { it.copy(srv = cle, serveur = nom, cameraId = idCamera(cle, it.cameraId)) }
                    traiter(api, ev)
                    surEvenements(ev)
                }
                if (r.dernier != depuis) app.reglages.dernierEvenement(r.dernier)
                attente = 2_000L
            } catch (e: ErreurApi) {
                if (e.code == 401) {
                    app.reglages.enregistrerJeton(null)
                    app.sessionPerdue.tryEmit(e.message ?: "Session expirée")
                    Notifications.message(context, "FluxLite : reconnectez-vous", e.message ?: "Session expirée.")
                    return false
                }
                delay(attente)
                attente = (attente * 2).coerceAtMost(60_000L)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                delay(attente)  // réseau coupé, PC éteint : on réessaie de plus en plus calmement
                attente = (attente * 2).coerceAtMost(60_000L)
            }
        }
        return true
    }

    /** Un autre PC Flux suivi : même attente longue, avec sa propre session et son propre point de départ. */
    private suspend fun boucleAutre(cle: String) {
        var attente = 2_000L
        while (coroutineContext.isActive) {
            val a = app.reglages.maintenant().autres.firstOrNull { it.serveur.cle == cle && it.jeton != null && it.affiche }
                ?: return
            val api = Api(a.serveur, a.jeton)
            try {
                var depuis: Long? = a.dernier
                if (depuis == null) {
                    val premier = api.attente(null).dernier
                    depuis = premier
                    app.reglages.majAutre(cle) { it.copy(dernier = premier) }
                }
                val r = api.attente(depuis, 40)
                if (r.evenements.isNotEmpty()) {
                    val ev = r.evenements.map { it.copy(srv = cle, serveur = a.serveur.nom, cameraId = idCamera(cle, it.cameraId)) }
                    traiter(api, ev)
                    surEvenements(ev)
                }
                val dernier = r.dernier
                if (dernier != depuis) app.reglages.majAutre(cle) { it.copy(dernier = dernier) }
                attente = 2_000L
            } catch (e: ErreurApi) {
                if (e.code == 401) {
                    app.reglages.majAutre(cle) { it.copy(jeton = null) }
                    Notifications.message(context, "FluxLite : ${a.serveur.nom}",
                        "Session expirée : reconnectez ce serveur (Compte › Serveurs Flux).", id = 3 + (cle.hashCode() and 0xFFFF))
                    return
                }
                delay(attente)
                attente = (attente * 2).coerceAtMost(60_000L)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                delay(attente)
                attente = (attente * 2).coerceAtMost(60_000L)
            }
        }
    }

    private suspend fun traiter(api: com.daoakashi.fluxlite.net.Api, evenements: List<Evenement>) {
        val cloche = app.reglages.maintenant().cloche
        val maintenant = System.currentTimeMillis()
        for (e in evenements.filter { it.niveau == "alerte" || it.prioritaire }.takeLast(10)) {
            if (cloche.mode == com.daoakashi.fluxlite.data.ModeAlertes.COUPEES) continue
            if (cloche.enPause || cloche.cameraMuette(e.cameraId)) continue
            val dernier = derniersEnvois[e.cameraId] ?: 0L
            if (!e.prioritaire && cloche.delaiMinutes > 0 && maintenant - dernier < cloche.delaiMinutes * 60_000L) {
                retenues[e.cameraId] = (retenues[e.cameraId] ?: 0) + 1
                continue
            }
            derniersEnvois[e.cameraId] = maintenant
            val photo = if (e.photo) runCatching { api.photo(e.id) }.getOrNull() else null
            Notifications.alerte(context, e, photo, cloche, retenues.remove(e.cameraId) ?: 0)
        }
    }
}
