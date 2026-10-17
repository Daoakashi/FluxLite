package com.daoakashi.fluxlite.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.daoakashi.fluxlite.BuildConfig
import com.daoakashi.fluxlite.net.JsonFlux
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Mises à jour de FluxLite depuis votre site (sans Play Store).
 *
 * Sur le site, à côté de l'APK, un fichier version.json :
 * {
 *   "versionCode": 2,
 *   "versionName": "1.1.0",
 *   "apk": "https://votre-site.example/fluxlite/FluxLite-1.1.0.apk",
 *   "sha256": "empreinte SHA-256 de l'APK (64 caractères hexadécimaux)",
 *   "notes": "Nouveautés…",
 *   "obligatoire": false
 * }
 * L'application compare versionCode au sien, télécharge l'APK en HTTPS, vérifie son empreinte, puis demande à
 * Android de l'installer. Android vérifie en plus que l'APK est signé avec la MÊME clé que la version installée.
 */
@Serializable
data class VersionDistante(
    val versionCode: Int,
    val versionName: String,
    val apk: String,
    val sha256: String,
    val notes: String = "",
    val obligatoire: Boolean = false,
    val minSdk: Int = 26,
)

sealed interface EtatMaj {
    data object Rien : EtatMaj
    data object Verification : EtatMaj
    data object AJour : EtatMaj
    data class Disponible(val v: VersionDistante) : EtatMaj
    data class Telechargement(val v: VersionDistante, val progression: Float) : EtatMaj
    data class Installation(val v: VersionDistante) : EtatMaj
    data class Erreur(val message: String) : EtatMaj
}

object MiseAJour {
    val etat = MutableStateFlow<EtatMaj>(EtatMaj.Rien)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    val configuree get() = BuildConfig.UPDATE_URL.startsWith("https://")

    suspend fun verifier(silencieux: Boolean = false): VersionDistante? = withContext(Dispatchers.IO) {
        if (!configuree) {
            if (!silencieux) etat.value = EtatMaj.Erreur("Adresse des mises à jour non configurée (UPDATE_URL).")
            return@withContext null
        }
        if (!silencieux) etat.value = EtatMaj.Verification
        try {
            val texte = client.newCall(Request.Builder().url(BuildConfig.UPDATE_URL).header("Cache-Control", "no-cache")
                .build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("le site répond ${r.code}")
                r.body?.string().orEmpty()
            }
            val v = JsonFlux.decodeFromString<VersionDistante>(texte)
            when {
                v.versionCode <= BuildConfig.VERSION_CODE -> { if (!silencieux) etat.value = EtatMaj.AJour; null }
                Build.VERSION.SDK_INT < v.minSdk -> {
                    if (!silencieux) etat.value = EtatMaj.Erreur("La version ${v.versionName} demande Android plus récent.")
                    null
                }
                else -> { etat.value = EtatMaj.Disponible(v); v }
            }
        } catch (e: Exception) {
            if (!silencieux) etat.value = EtatMaj.Erreur("Vérification impossible : ${e.message}")
            null
        }
    }

    /** Android doit autoriser FluxLite à installer des applications (réglage « Sources inconnues »). */
    fun peutInstaller(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun ouvrirAutorisation(context: Context) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    suspend fun telechargerEtInstaller(context: Context, v: VersionDistante) {
        try {
            val apk = telecharger(context, v)
            etat.value = EtatMaj.Installation(v)
            installer(context, apk)
        } catch (e: Exception) {
            etat.value = EtatMaj.Erreur(e.message ?: "Mise à jour impossible")
        }
    }

    private suspend fun telecharger(context: Context, v: VersionDistante): File = withContext(Dispatchers.IO) {
        if (!v.apk.startsWith("https://")) throw IOException("l'APK doit être téléchargé en HTTPS")
        val dossier = File(context.cacheDir, "maj").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val fichier = File(dossier, "FluxLite-${v.versionName}.apk")
        val sha = MessageDigest.getInstance("SHA-256")
        client.newCall(Request.Builder().url(v.apk).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("téléchargement refusé (${r.code})")
            val corps = r.body ?: throw IOException("fichier vide")
            val total = corps.contentLength().takeIf { it > 0 }
            corps.byteStream().use { entree ->
                fichier.outputStream().use { sortie ->
                    val tampon = ByteArray(64 * 1024)
                    var lu = 0L
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        sortie.write(tampon, 0, n)
                        sha.update(tampon, 0, n)
                        lu += n
                        etat.value = EtatMaj.Telechargement(v, if (total != null) lu.toFloat() / total else -1f)
                    }
                }
            }
        }
        val calcule = sha.digest().joinToString("") { "%02x".format(it) }
        if (!calcule.equals(v.sha256.trim(), ignoreCase = true)) {
            fichier.delete()
            throw IOException("fichier corrompu ou modifié (empreinte SHA-256 différente) : installation annulée")
        }
        fichier
    }

    private fun installer(context: Context, apk: File) {
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            if (Build.VERSION.SDK_INT >= 34) setRequestUpdateOwnership(true)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            session.openWrite("fluxlite.apk", 0, apk.length()).use { sortie ->
                apk.inputStream().use { it.copyTo(sortie) }
                session.fsync(sortie)
            }
            val intent = Intent(context, InstallReceiver::class.java).setAction(InstallReceiver.ACTION)
            // MUTABLE : Android ajoute le résultat de l'installation à cette intention (explicite, donc sûre).
            val drapeaux = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, drapeaux).intentSender)
        }
    }
}
