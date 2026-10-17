package com.daoakashi.fluxlite.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.daoakashi.fluxlite.notif.Notifications

/** Reçoit le résultat de l'installation d'une mise à jour. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val statut = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android demande la confirmation de l'utilisateur (« Mettre à jour cette application ? »)
                val confirmation = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirmation != null) {
                    confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmation)
                    } catch (_: Exception) {
                        Notifications.message(context, "Mise à jour prête",
                            "Ouvrez FluxLite pour terminer l'installation de la mise à jour.", 3)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> MiseAJour.etat.value = EtatMaj.AJour
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                val message = when (statut) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation annulée."
                    PackageInstaller.STATUS_FAILURE_CONFLICT ->
                        "Installation refusée : l'APK n'est pas signé avec la même clé que la version installée."
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Cette version n'est pas compatible avec ce téléphone."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Espace de stockage insuffisant."
                    PackageInstaller.STATUS_FAILURE_INVALID -> "Fichier d'installation invalide."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation bloquée par le téléphone."
                    else -> "Installation impossible. $detail"
                }
                MiseAJour.etat.value = EtatMaj.Erreur(message)
            }
        }
    }

    companion object {
        const val ACTION = "com.daoakashi.fluxlite.RESULTAT_INSTALLATION"
    }
}
