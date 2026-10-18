package com.daoakashi.fluxlite

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import com.daoakashi.fluxlite.data.Reglages
import com.daoakashi.fluxlite.net.Api
import com.daoakashi.fluxlite.net.Relais
import com.daoakashi.fluxlite.notif.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class FluxLiteApp : Application() {
    lateinit var reglages: Reglages
        private set

    /** Portée qui survit aux écrans (envois au serveur, enregistrements). */
    val portee = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Signalé quand le serveur refuse la session (expirée, compte suspendu) : l'interface revient à la connexion. */
    val sessionPerdue = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Chemin de la dernière connexion au PC Flux : « direct » (même réseau) ou « internet » (relais Flux). */
    val route = MutableStateFlow("")

    override fun onCreate() {
        super.onCreate()
        reglages = Reglages(this)
        Notifications.creerCanaux(this)
        Relais.definirEcouteur { _, r -> route.value = r }
        // Adresses du relais : celles déjà connues tout de suite, puis l'adresse publiée sur le site.
        portee.launch {
            reglages.etat.map { it.relais to it.relaisSite }.distinctUntilChanged().collect { (propre, site) ->
                Relais.definirPropre(propre)
                Relais.definirSite(site)
            }
        }
        portee.launch(Dispatchers.IO) { chargerRelaisSite() }
        // Retour sur un WiFi (ou changement de réseau) : on réessaie tout de suite le chemin direct.
        runCatching {
            getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = Relais.oublierEchecs()
                })
        }
    }

    /** Lit l'adresse du relais publiée sur le site de Flux (relais.json) et la garde pour les prochains démarrages. */
    private suspend fun chargerRelaisSite() {
        val texte = runCatching {
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
            client.newCall(Request.Builder().url("${BuildConfig.SITE_URL}/relais.json").build()).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val o = Json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject
                if (o["actif"]?.jsonPrimitive?.booleanOrNull == false) return@runCatching ""
                val hote = o["hote"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                val port = o["port"]?.jsonPrimitive?.intOrNull ?: Relais.PORT_DEFAUT
                Relais.lire(if (hote.isEmpty()) "" else if (hote.contains(':')) "[$hote]:$port" else "$hote:$port")
                    ?.toString().orEmpty()
            }
        }.getOrNull() ?: return  // site injoignable : on garde la dernière adresse connue
        if (texte != reglages.maintenant().relaisSite) reglages.relaisSite(texte)
        Relais.definirSite(texte)
    }

    /** Client connecté au serveur enregistré, ou null si pas encore appairé / connecté. */
    suspend fun api(): Api? {
        val e = reglages.maintenant()
        val s = e.serveur ?: return null
        val j = e.jeton ?: return null
        if (Relais.actif() == null) {  // service lancé au démarrage du téléphone : adresses du relais pas encore lues
            Relais.definirPropre(e.relais)
            Relais.definirSite(e.relaisSite)
        }
        return Api(s, j)
    }

    /** Client d'un PC Flux suivi (courant ou autre), ou null s'il n'a pas de session. */
    suspend fun apiDe(cle: String): Api? {
        val e = reglages.maintenant()
        if (cle.isEmpty() || e.serveur?.cle == cle) return api()
        val autre = e.autres.firstOrNull { it.serveur.cle == cle } ?: return null
        return Api(autre.serveur, autre.jeton ?: return null)
    }

    /** Pour un identifiant de caméra « clé|id » : le client de son serveur et l'identifiant à lui envoyer. */
    suspend fun apiPour(idCamera: String): Pair<Api, String>? {
        val i = idCamera.indexOf('|')
        if (i < 0) return api()?.let { it to idCamera }
        return apiDe(idCamera.substring(0, i))?.let { it to idCamera.substring(i + 1) }
    }
}
