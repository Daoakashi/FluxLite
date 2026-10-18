package com.daoakashi.fluxlite.notif

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.daoakashi.fluxlite.FluxLiteApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service au premier plan qui garde la connexion d'alertes ouverte quand l'application est fermée.
 * Type « specialUse » : pas de limite de durée sous Android 15 et 16 (contrairement à « dataSync »), et autorisé
 * à démarrer au démarrage du téléphone.
 */
class AlertService : Service() {
    private val portee = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var travail: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as FluxLiteApp
        val notif = Notifications.veille(this, "Connecté à votre serveur Flux : vous recevrez les alertes.")
        try {
            ServiceCompat.startForeground(this, Notifications.ID_VEILLE, notif,
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        } catch (_: Exception) {
            // Démarrage refusé par le système (cas rares en arrière-plan) : on réessaiera à l'ouverture de l'appli.
            stopSelf()
            return START_NOT_STICKY
        }
        if (travail?.isActive != true) {
            travail = portee.launch {
                val e = app.reglages.maintenant()
                if ((e.jeton == null && e.autres.none { it.jeton != null }) || !e.ecouteArrierePlan) {
                    stopSelf()
                    return@launch
                }
                Ecoute(this@AlertService).boucle()
                stopSelf()  // session perdue
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        portee.cancel()
        super.onDestroy()
    }

    companion object {
        fun demarrer(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, AlertService::class.java))
            } catch (_: Exception) {
            }
        }

        fun arreter(context: Context) {
            context.stopService(Intent(context, AlertService::class.java))
        }
    }
}
