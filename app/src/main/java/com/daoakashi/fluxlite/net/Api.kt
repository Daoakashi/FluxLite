package com.daoakashi.fluxlite.net

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.daoakashi.fluxlite.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException

/** Adresse et clé d'un serveur Flux. hote vide : PC appairé hors de son réseau, joint seulement par Internet. */
data class Serveur(val hote: String, val port: Int, val cle: String, val nom: String = "Flux") {
    val base get() = hote.ifBlank { Relais.HOTE_INTERNET }.let { h ->
        "https://${if (h.contains(':') && !h.startsWith("[")) "[$h]" else h}:$port/api/v1"
    }
}

/**
 * Résolution des noms : si le nom du PC Flux est inconnu ici (hors de son réseau, ou appairé sans adresse locale),
 * on rend une adresse vide ; la socket (voir Relais) passe alors directement par le relais Internet.
 */
object DnsFlux : Dns {
    override fun lookup(hostname: String): List<InetAddress> = try {
        if (hostname == Relais.HOTE_INTERNET) throw UnknownHostException(hostname)
        Dns.SYSTEM.lookup(hostname)
    } catch (e: UnknownHostException) {
        if (Relais.actif() == null) throw e
        listOf(InetAddress.getByAddress(hostname, ByteArray(4)))
    }
}

/** Erreur claire du relais (PC Flux éteint, hors ligne…), même enveloppée par OkHttp. */
fun Throwable.erreurRelais(): Relais.ErreurRelais? {
    var c: Throwable? = this
    var n = 0
    while (c != null && n++ < 10) {
        if (c is Relais.ErreurRelais) return c
        c = c.cause
    }
    return null
}

class ErreurApi(val code: Int, message: String, val statut: String? = null) : Exception(message)

fun appareilCourant() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

/** Message clair pour l'utilisateur, quelle que soit l'erreur. */
/** Vrai si l'erreur vient d'une clé de serveur différente (peut être enveloppée dans une erreur TLS). */
fun Throwable.estCleIncorrecte(): Boolean {
    var c: Throwable? = this
    var n = 0
    while (c != null && n++ < 10) {
        if (c is CleIncorrecte) return true
        c = c.cause
    }
    return false
}

fun messageErreur(e: Throwable): String {
    if (e.estCleIncorrecte()) return "Attention : ce serveur ne présente pas la bonne clé. Connexion refusée pour " +
            "votre sécurité. Vérifiez la clé dans Flux (page FluxLite)."
    e.erreurRelais()?.let { return it.message ?: "Le relais Flux refuse la connexion." }
    return when (e) {
        is ErreurApi -> e.message ?: "Erreur ${e.code}"
        is UnknownHostException -> "Adresse introuvable. Vérifiez l'adresse du serveur."
        is ConnectException -> if (Relais.actif() != null) "Serveur injoignable, ni sur ce réseau ni par Internet. " +
            "Vérifiez votre connexion, et que le PC Flux est allumé." else "Serveur injoignable. Le PC Flux est-il " +
            "allumé, le serveur FluxLite activé, et êtes-vous sur le même réseau ?"
        is SocketTimeoutException -> "Le serveur ne répond pas (délai dépassé)."
        is SSLException -> "Connexion sécurisée impossible avec ce serveur."
        is IOException -> "Problème de réseau : ${e.message ?: "connexion interrompue"}"
        else -> e.message ?: "Erreur inattendue"
    }
}

data class ImageFlux(val image: Bitmap, val personnes: Int)

class Api(val serveur: Serveur, private val jeton: String? = null) {

    private val client: OkHttpClient = clientEpingle(serveur.cle)
    private val clientLong by lazy { client.newBuilder().readTimeout(70, TimeUnit.SECONDS).build() }
    private val clientFlux by lazy { client.newBuilder().readTimeout(25, TimeUnit.SECONDS).build() }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun clientEpingle(cle: String): OkHttpClient {
            val confiance = ConfianceEpinglee(cle)
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(confiance), SecureRandom()) }
            return OkHttpClient.Builder()
                // Chemin choisi à chaque connexion : réseau local, sinon Internet par le relais Flux (Relais.java).
                .socketFactory(Relais.Fabrique(cle))
                .dns(DnsFlux)
                .sslSocketFactory(ssl.socketFactory, confiance)
                // Le nom d'hôte importe peu : l'identité du serveur est prouvée par sa clé (épinglage).
                .hostnameVerifier { _, _ -> true }
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        /**
         * Essaie chaque adresse du QR code et renvoie la première qui répond avec la bonne clé. Hors du réseau du
         * PC Flux, chaque essai passe tout seul par le relais Internet. Sans adresse : directement par Internet.
         */
        suspend fun appairer(hotes: List<String>, port: Int, cle: String): Pair<Serveur, InfoServeur> {
            var derniere: Throwable = IOException("Aucune adresse")
            val liste = hotes.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("") }
            for (h in liste) {
                try {
                    val s = Serveur(h, port, cle)
                    val info = Api(s).info()
                    return s.copy(nom = info.nom) to info
                } catch (e: Exception) {
                    if (e.estCleIncorrecte()) throw e
                    derniere = e
                }
            }
            throw derniere
        }
    }

    // --- requêtes -------------------------------------------------------------------------
    private fun requete(chemin: String) = Request.Builder().url(serveur.base + chemin).apply {
        header("User-Agent", "FluxLite/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
        if (jeton != null) header("Authorization", "Bearer $jeton")
    }

    private fun verifier(r: Response): String {
        val corps = r.body?.string().orEmpty()
        if (!r.isSuccessful) {
            val rep = runCatching { JsonFlux.decodeFromString<Reponse>(corps) }.getOrNull()
            throw ErreurApi(r.code, rep?.erreur ?: "Erreur ${r.code}", rep?.statut)
        }
        return corps
    }

    private suspend inline fun <reified T> get(chemin: String, c: OkHttpClient = client): T = withContext(Dispatchers.IO) {
        c.newCall(requete(chemin).get().build()).execute().use { JsonFlux.decodeFromString<T>(verifier(it)) }
    }

    private suspend inline fun <reified T> envoyer(methode: String, chemin: String, corps: JsonObject): T =
        withContext(Dispatchers.IO) {
            val b = corps.toString().toRequestBody(JSON)
            client.newCall(requete(chemin).method(methode, b).build()).execute()
                .use { JsonFlux.decodeFromString<T>(verifier(it)) }
        }

    private suspend fun octets(chemin: String): ByteArray? = withContext(Dispatchers.IO) {
        client.newCall(requete(chemin).get().build()).execute().use { r ->
            if (r.code == 503 || r.code == 404) return@use null
            if (!r.isSuccessful) verifier(r)
            r.body?.bytes()
        }
    }

    // --- serveur et compte --------------------------------------------------------------------
    suspend fun info(): InfoServeur = get("/info")

    suspend fun inscription(mail: String, motDePasse: String, nom: String, prenom: String, societe: String,
                            cguVersion: String): Reponse =
        envoyer("POST", "/inscription", buildJsonObject {
            put("mail", mail); put("mot_de_passe", motDePasse); put("nom", nom); put("prenom", prenom)
            put("societe", societe); put("cgu_version", cguVersion); put("appareil", appareilCourant())
            put("version_app", BuildConfig.VERSION_NAME)
        })

    suspend fun connexion(mail: String, motDePasse: String): ReponseConnexion =
        envoyer("POST", "/connexion", buildJsonObject {
            put("mail", mail); put("mot_de_passe", motDePasse); put("appareil", appareilCourant())
            put("version_app", BuildConfig.VERSION_NAME)
        })

    suspend fun deconnexion(): Reponse = envoyer("POST", "/deconnexion", JsonObject(emptyMap()))

    suspend fun profil(): Profil = get("/compte")

    suspend fun modifierProfil(nom: String? = null, prenom: String? = null, cguVersion: String? = null): Profil =
        envoyer("PATCH", "/compte", buildJsonObject {
            nom?.let { put("nom", it) }; prenom?.let { put("prenom", it) }; cguVersion?.let { put("cgu_version", it) }
        })

    suspend fun changerMotDePasse(ancien: String, nouveau: String): Reponse =
        envoyer("POST", "/compte/mot_de_passe", buildJsonObject { put("ancien", ancien); put("nouveau", nouveau) })

    suspend fun revoquerSession(id: Int?): Profil =
        envoyer("POST", "/compte/sessions/revoquer", JsonObject(mapOf("id" to (id?.let { JsonPrimitive(it) }
            ?: JsonPrimitive("autres")))))

    suspend fun exporterDonnees(): String = withContext(Dispatchers.IO) {
        client.newCall(requete("/compte/export").get().build()).execute().use { verifier(it) }
    }

    suspend fun supprimerCompte(motDePasse: String): Reponse =
        envoyer("DELETE", "/compte", buildJsonObject { put("mot_de_passe", motDePasse) })

    // --- caméras --------------------------------------------------------------------------------
    suspend fun cameras(): ListeCameras = get("/cameras")

    // --- pilotage à distance (Flux 0.9+) -----------------------------------------------------
    suspend fun pilotage(): EtatPilotage = get("/pilotage")

    /** cle : "detection.actif", "camera.detection" (avec cameraId), "notif.actif", "mail.actif", "whatsapp.actif"… */
    suspend fun piloter(cle: String, valeur: Boolean, cameraId: String? = null): EtatPilotage =
        envoyer("POST", "/pilotage", buildJsonObject {
            put("cle", cle); put("valeur", valeur); cameraId?.let { put("camera", it) }
        })

    suspend fun image(cameraId: String, largeur: Int = 640): Bitmap? =
        octets("/cameras/$cameraId/image?largeur=$largeur")?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    /** Vidéo en direct (MJPEG) : une image après l'autre, jusqu'à ce que l'écran se ferme. */
    fun flux(cameraId: String, fps: Int = 12, largeur: Int = 1280): Flow<ImageFlux> = callbackFlow {
        val appel = clientFlux.newCall(requete("/cameras/$cameraId/flux?fps=$fps&largeur=$largeur").get().build())
        val lecture = launch(Dispatchers.IO) {
            try {
                appel.execute().use { r ->
                    if (!r.isSuccessful) verifier(r)
                    val source = r.body!!.source()
                    while (isActive) {
                        val ligne = source.readUtf8Line() ?: break
                        if (!ligne.startsWith("--fluxlite")) continue
                        var longueur = -1
                        var personnes = 0
                        while (true) {
                            val entete = source.readUtf8Line() ?: break
                            if (entete.isEmpty()) break
                            val (cle, valeur) = entete.split(":", limit = 2).let { it[0].trim().lowercase() to it.getOrElse(1) { "" }.trim() }
                            when (cle) {
                                "content-length" -> longueur = valeur.toIntOrNull() ?: -1
                                "x-personnes" -> personnes = valeur.toIntOrNull() ?: 0
                            }
                        }
                        if (longueur <= 0 || longueur > 20_000_000) break
                        val jpeg = source.readByteArray(longueur.toLong())
                        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { trySend(ImageFlux(it, personnes)) }
                    }
                }
                close()
            } catch (e: Exception) {
                close(e)
            }
        }
        awaitClose {
            appel.cancel()
            lecture.cancel()
        }
    }.buffer(Channel.CONFLATED).flowOn(Dispatchers.IO)

    // --- journal et alertes ---------------------------------------------------------------------
    suspend fun journal(avant: Long? = null, cameraId: String? = null, limite: Int = 40): Journal {
        val q = buildList {
            add("limite=$limite"); avant?.let { add("avant=$it") }; cameraId?.let { add("camera=$it") }
        }.joinToString("&")
        return get("/journal?$q")
    }

    suspend fun photo(id: Long): Bitmap? =
        octets("/journal/$id/photo")?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

    /** Attente longue : rend la main dès qu'une alerte arrive (ou après ~40 s). Sans `depuis` : point de départ. */
    suspend fun attente(depuis: Long?, delai: Int = 40): Attente =
        get(if (depuis == null) "/alertes/attente" else "/alertes/attente?depuis=$depuis&delai=$delai", clientLong)
}
