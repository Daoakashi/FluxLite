package com.daoakashi.fluxlite.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daoakashi.fluxlite.FluxLiteApp
import kotlinx.coroutines.launch

/** Relance la veille des alertes après un redémarrage du téléphone ou une mise à jour de l'application. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as FluxLiteApp
        val fin = goAsync()
        app.portee.launch {
            try {
                val e = app.reglages.maintenant()
                if (e.jeton != null && e.ecouteArrierePlan) AlertService.demarrer(context)
            } finally {
                fin.finish()
            }
        }
    }
}
