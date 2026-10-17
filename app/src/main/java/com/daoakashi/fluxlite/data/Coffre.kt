package com.daoakashi.fluxlite.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chiffre le jeton de session avec une clé AES-256 rangée dans le Keystore Android (matériel sécurisé quand le
 * téléphone en a un). La clé ne peut pas être extraite : même une copie des fichiers de l'appli est inutilisable.
 */
object Coffre {
    private const val ALIAS = "fluxlite_session"
    private const val MAGASIN = "AndroidKeyStore"

    private fun cle(): SecretKey {
        val ks = KeyStore.getInstance(MAGASIN).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, MAGASIN)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun chiffrer(texte: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, cle())
        val chiffre = c.doFinal(texte.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + chiffre, Base64.NO_WRAP)
    }

    fun dechiffrer(donnees: String): String? = try {
        val brut = Base64.decode(donnees, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, cle(), GCMParameterSpec(128, brut, 0, 12))
        String(c.doFinal(brut, 12, brut.size - 12), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}
