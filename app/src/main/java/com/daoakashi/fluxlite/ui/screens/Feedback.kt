package com.daoakashi.fluxlite.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.BuildConfig
import com.daoakashi.fluxlite.ui.theme.Flux

/** Adresse et page qui reçoivent les retours (idées, problèmes, questions). */
const val FEEDBACK_MAIL = "dms.security.list@gmail.com"
const val FEEDBACK_GITHUB = "https://github.com/Daoakashi/FluxLite/issues/new"

/** Crédits affichés dans l'onglet Compte. */
const val CREDITS_IA = "Crédits : conception, choix des fonctions, tests et validation par DMS Security. Code et textes écrits en partie avec l'assistant d'IA Claude (Anthropic), qui cède à l'utilisateur ses droits éventuels sur ce qu'il produit. La licence AGPL-3.0 couvre l'apport humain ; les parties produites uniquement par l'IA peuvent ne pas être protégées par le droit d'auteur."

private val SUJETS = listOf("Idée", "Problème", "Question")

private fun contexteFeedback() =
    "Programme : FluxLite ${BuildConfig.VERSION_NAME}\nTéléphone : ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

private fun ouvrir(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Feedback : le message est relu par la personne puis envoyé par sa messagerie ou sur GitHub. Rien n'est envoyé
 * automatiquement ; seules les versions de l'application et d'Android sont ajoutées.
 */
@Composable
fun DialogueFeedback(fermer: () -> Unit) {
    val c = Flux.c
    val contexte = LocalContext.current
    var sujet by remember { mutableStateOf(SUJETS[0]) }
    var message by remember { mutableStateOf("") }
    val corps = { "${message.trim()}\n\n---\n${contexteFeedback()}" }
    AlertDialog(
        onDismissRequest = fermer, containerColor = c.panneau,
        title = { Text("Feedback", color = c.texte) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Une idée, un problème, une question ? N'y mettez ni mot de passe ni image des caméras.", color = c.texte2)
                Pilules(SUJETS.map { it to it }, sujet, { sujet = it })
                Champ(message, { message = it }, "Votre message", lignes = 6)
            }
        },
        confirmButton = {
            Row {
                TextButton({
                    val titre = Uri.encode("[$sujet] ")
                    ouvrir(contexte, Intent(Intent.ACTION_VIEW,
                        Uri.parse("$FEEDBACK_GITHUB?title=$titre&body=${Uri.encode(corps())}")))
                    fermer()
                }, enabled = message.isNotBlank()) { Text("GitHub") }
                TextButton({
                    ouvrir(contexte, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$FEEDBACK_MAIL")).apply {
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_MAIL))
                        putExtra(Intent.EXTRA_SUBJECT, "FluxLite · $sujet")
                        putExtra(Intent.EXTRA_TEXT, corps())
                    })
                    fermer()
                }, enabled = message.isNotBlank()) { Text("Envoyer par mail") }
            }
        },
        dismissButton = { TextButton(fermer) { Text("Annuler", color = c.texte2) } },
    )
}
