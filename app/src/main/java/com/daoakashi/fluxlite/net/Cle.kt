package com.daoakashi.fluxlite.net

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Clé du serveur Flux : empreinte (160 bits, base32 de Crockford) de la clé publique de son certificat.
 * Le téléphone la retient à l'appairage puis refuse tout serveur qui ne présente pas exactement la même clé.
 * C'est ce qui rend la connexion sûre même avec un certificat auto-signé : pas d'autorité tierce, pas de
 * « petit curieux » possible au milieu (Wi-Fi public, box piratée, faux certificat).
 */
object CleServeur {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LONGUEUR = 32

    /** « fx7k-2m… » -> « FX7K2M… » ; tolère espaces, tirets, minuscules, O pour 0, I et L pour 1. */
    fun normaliser(texte: String): String = buildString {
        for (c in texte.uppercase()) {
            when {
                c.isWhitespace() || c == '-' || c == '_' || c == '.' -> Unit
                c == 'O' -> append('0')
                c == 'I' || c == 'L' -> append('1')
                c == 'U' -> append('V')
                else -> append(c)
            }
        }
    }

    fun estValide(cle: String) = cle.length == LONGUEUR && cle.all { it in ALPHABET }

    fun formater(cle: String) = cle.chunked(4).joinToString("-")

    fun base32(octets: ByteArray): String {
        val sortie = StringBuilder()
        var tampon = 0
        var bits = 0
        for (o in octets) {
            tampon = (tampon shl 8) or (o.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sortie.append(ALPHABET[(tampon shr bits) and 31])
            }
            tampon = tampon and ((1 shl bits) - 1)
        }
        if (bits > 0) sortie.append(ALPHABET[(tampon shl (5 - bits)) and 31])
        return sortie.toString()
    }

    /** `publicKey.encoded` = SubjectPublicKeyInfo (DER), exactement ce que Flux hache de son côté. */
    fun depuisCertificat(cert: X509Certificate): String {
        val empreinte = MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded)
        return base32(empreinte.copyOf(20))
    }
}

/** Levée quand le serveur présente une autre clé que celle enregistrée. */
class CleIncorrecte(val cleVue: String) :
    CertificateException("La clé présentée par le serveur ne correspond pas à la clé enregistrée.")

/** Ne fait confiance qu'au serveur dont la clé publique correspond à la clé enregistrée. */
class ConfianceEpinglee(private val cle: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val certificat = chain?.firstOrNull() ?: throw CertificateException("Aucun certificat présenté.")
        val vue = CleServeur.depuisCertificat(certificat)
        if (!MessageDigest.isEqual(vue.toByteArray(), cle.toByteArray())) throw CleIncorrecte(vue)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Non utilisé.")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
