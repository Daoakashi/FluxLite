package com.daoakashi.fluxlite.notif

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daoakashi.fluxlite.MainActivity
import com.daoakashi.fluxlite.R
import com.daoakashi.fluxlite.data.Cloche
import com.daoakashi.fluxlite.data.ModeAlertes
import com.daoakashi.fluxlite.net.Evenement

object Notifications {
    const val CANAL_ALERTES = "alertes"
    const val CANAL_DISCRETES = "alertes_discretes"
    const val CANAL_PRIORITAIRES = "liste_noire"
    const val CANAL_VEILLE = "veille"
    const val CANAL_COMPTE = "compte"
    const val ID_VEILLE = 1
    const val EXTRA_CAMERA = "camera_id"
    private const val COULEUR = 0xFF2F7BFF.toInt()

    fun creerCanaux(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannelGroup(NotificationChannelGroup("cameras", "Caméras"))
        val son = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannels(listOf(
            NotificationChannel(CANAL_ALERTES, "Alertes des caméras", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Une personne est détectée sur une caméra"
                group = "cameras"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 180, 120, 180)
                setSound(son, audio)
                enableLights(true)
                lightColor = COULEUR
            },
            NotificationChannel(CANAL_DISCRETES, "Alertes silencieuses", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Alertes affichées sans son ni vibration (cloche réglée sur « Silencieuses »)"
                group = "cameras"
            },
            NotificationChannel(CANAL_PRIORITAIRES, "Liste noire", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Personne ou plaque en liste noire : alerte prioritaire"
                group = "cameras"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 150, 400, 150, 400)
                setSound(son, audio)
            },
            NotificationChannel(CANAL_VEILLE, "Veille des alertes", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Indique que FluxLite reste connecté pour recevoir les alertes"
                setShowBadge(false)
            },
            NotificationChannel(CANAL_COMPTE, "Compte et application", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Session expirée, mises à jour"
            },
        ))
    }

    fun autorisees(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ouvrir(context: Context, cameraId: String?, code: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            cameraId?.let { putExtra(EXTRA_CAMERA, it) }
        }
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun veille(context: Context, texte: String) = NotificationCompat.Builder(context, CANAL_VEILLE)
        .setSmallIcon(R.drawable.ic_notif)
        .setColor(COULEUR)
        .setContentTitle("FluxLite veille")
        .setContentText(texte)
        .setOngoing(true)
        .setSilent(true)
        .setShowWhen(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setContentIntent(ouvrir(context, null, 0))
        .build()

    /** Affiche une alerte si la cloche le permet. `regroupees` = alertes retenues par le délai depuis la dernière. */
    @SuppressLint("MissingPermission")  // vérifiée par autorisees()
    fun alerte(context: Context, e: Evenement, photo: Bitmap?, cloche: Cloche, regroupees: Int) {
        if (!autorisees(context)) return
        val canal = when {
            e.prioritaire -> CANAL_PRIORITAIRES
            cloche.mode == ModeAlertes.SILENCIEUSES -> CANAL_DISCRETES
            else -> CANAL_ALERTES
        }
        val titre = if (e.prioritaire) "⚠ ${e.camera} · Liste noire" else e.camera
        val texte = e.texte + if (regroupees > 0) "  (+$regroupees pendant le délai)" else ""
        val b = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(if (e.prioritaire) 0xFFFF2E7A.toInt() else COULEUR)
            .setContentTitle(titre)
            .setContentText(texte)
            .setWhen((e.heure * 1000).toLong())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)  // écran verrouillé : pas d'image ni de détail
            .setPublicVersion(
                NotificationCompat.Builder(context, canal).setSmallIcon(R.drawable.ic_notif).setColor(COULEUR)
                    .setContentTitle("FluxLite").setContentText("Nouvelle alerte caméra").build()
            )
            .setGroup("camera_${e.cameraId}")
            .setContentIntent(ouvrir(context, e.cameraId, e.cameraId.hashCode()))
        if (photo != null) {
            b.setLargeIcon(photo)
            b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(photo).bigLargeIcon(null as Bitmap?))
        } else {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(texte))
        }
        if (cloche.mode == ModeAlertes.SILENCIEUSES && !e.prioritaire) b.setSilent(true)
        try {
            // Une notification par caméra : la plus récente remplace la précédente (pas d'avalanche).
            NotificationManagerCompat.from(context).notify("camera", e.cameraId.hashCode(), b.build())
        } catch (_: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun message(context: Context, titre: String, texte: String, id: Int = 2) {
        if (!autorisees(context)) return
        val n = NotificationCompat.Builder(context, CANAL_COMPTE)
            .setSmallIcon(R.drawable.ic_notif).setColor(COULEUR)
            .setContentTitle(titre).setContentText(texte)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texte))
            .setAutoCancel(true)
            .setContentIntent(ouvrir(context, null, id))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
