package com.daoakashi.fluxlite.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.icons.LogoVagues
import com.daoakashi.fluxlite.ui.theme.Flux

private val RE_MAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

/** Force du mot de passe de 0 à 1, et le conseil correspondant (règles du serveur : 10 caractères, lettre et chiffre). */
fun forceMotDePasse(m: String): Pair<Float, String> {
    if (m.isEmpty()) return 0f to "Au moins 10 caractères, avec des lettres et des chiffres."
    var score = 0f
    if (m.length >= 10) score += 0.35f
    if (m.length >= 14) score += 0.15f
    if (m.any { it.isLetter() } && m.any { it.isDigit() }) score += 0.25f
    if (m.any { it.isUpperCase() } && m.any { it.isLowerCase() }) score += 0.1f
    if (m.any { !it.isLetterOrDigit() }) score += 0.15f
    val conseil = when {
        m.length < 10 -> "Encore ${10 - m.length} caractère(s) au minimum."
        !(m.any { it.isLetter() } && m.any { it.isDigit() }) -> "Ajoutez des lettres et des chiffres."
        score < 0.75f -> "Correct. Plus long ou avec un symbole, c'est encore mieux."
        else -> "Excellent mot de passe."
    }
    return score.coerceAtMost(1f) to conseil
}

fun motDePasseAccepte(m: String) = m.length >= 10 && m.any { it.isLetter() } && m.any { it.isDigit() }

@Composable
fun EcranConnexion(vm: AppViewModel) {
    val c = Flux.c
    val etat by vm.etat.collectAsState()
    val info by vm.info.collectAsState()
    val occupe by vm.occupe.collectAsState()
    var onglet by rememberSaveable { mutableStateOf(0) }
    var attente by rememberSaveable { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.accentDoux, c.fond, c.fond)))
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LogoVagues(taille = 46.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(etat?.serveur?.nom ?: "Flux", style = MaterialTheme.typography.titleLarge, color = c.texte)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icone(Ico.CADENAS, taille = 14.dp, couleur = c.succes)
                        Spacer(Modifier.width(5.dp))
                        Text("Connexion chiffrée · clé vérifiée", style = MaterialTheme.typography.bodySmall, color = c.succes)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Pilules(listOf(0 to "Se connecter", 1 to "Créer un compte"), onglet, { onglet = it; attente = null })
            Spacer(Modifier.height(20.dp))
            AnimatedContent(onglet, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onglet") { o ->
                if (o == 0) FormulaireConnexion(vm, occupe)
                else if (attente != null) CompteEnAttente(attente!!) { onglet = 0; attente = null }
                else FormulaireInscription(vm, occupe, info?.inscription != false) { message, actif -> if (!actif) attente = message }
            }
            Spacer(Modifier.height(24.dp))
            Bouton("Changer de serveur", { vm.oublierServeur() }, Modifier.fillMaxWidth(), StyleBouton.FANTOME, Ico.SERVEUR)
        }
    }
}

@Composable
private fun FormulaireConnexion(vm: AppViewModel, occupe: Boolean) {
    var mail by rememberSaveable { mutableStateOf("") }
    var mdp by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Champ(mail, { mail = it.trim() }, "Adresse mail", clavier = KeyboardType.Email)
        Champ(mdp, { mdp = it }, "Mot de passe", motDePasse = true)
        Spacer(Modifier.height(6.dp))
        Bouton("Se connecter", { vm.connexion(mail, mdp) }, Modifier.fillMaxWidth(),
            actif = RE_MAIL.matches(mail) && mdp.isNotEmpty(), chargement = occupe)
        Text("Mot de passe oublié ? Demandez à votre responsable de le réinitialiser depuis Flux.",
            style = MaterialTheme.typography.bodySmall, color = Flux.c.texte3, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun FormulaireInscription(vm: AppViewModel, occupe: Boolean, ouvertes: Boolean, surCree: (String, Boolean) -> Unit) {
    val c = Flux.c
    if (!ouvertes) {
        MessageVide(Ico.CADENAS, "Inscriptions fermées", "Ce serveur n'accepte pas les inscriptions depuis le téléphone. " +
            "Demandez à votre responsable de créer votre compte dans Flux.")
        return
    }
    var prenom by rememberSaveable { mutableStateOf("") }
    var nom by rememberSaveable { mutableStateOf("") }
    var mail by rememberSaveable { mutableStateOf("") }
    var societe by rememberSaveable { mutableStateOf("") }
    var mdp by rememberSaveable { mutableStateOf("") }
    var mdp2 by rememberSaveable { mutableStateOf("") }
    val (force, conseil) = forceMotDePasse(mdp)
    val valide = prenom.isNotBlank() && nom.isNotBlank() && RE_MAIL.matches(mail) && motDePasseAccepte(mdp) && mdp == mdp2
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Champ(prenom, { prenom = it }, "Prénom", Modifier.weight(1f))
            Champ(nom, { nom = it }, "Nom", Modifier.weight(1f))
        }
        Champ(mail, { mail = it.trim() }, "Adresse mail", clavier = KeyboardType.Email,
            erreur = mail.isNotEmpty() && !RE_MAIL.matches(mail))
        Champ(societe, { societe = it }, "Société (votre employeur ou client)",
            aide = "Aide votre responsable à vous rattacher aux bonnes caméras.")
        Champ(mdp, { mdp = it }, "Mot de passe", motDePasse = true)
        LinearProgressIndicator(
            progress = { force },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
            color = when { force < 0.4f -> c.danger; force < 0.75f -> c.attention; else -> c.succes },
            trackColor = c.surface2,
            strokeCap = StrokeCap.Round,
        )
        Text(conseil, style = MaterialTheme.typography.bodySmall, color = c.texte2)
        Champ(mdp2, { mdp2 = it }, "Confirmer le mot de passe", motDePasse = true,
            erreur = mdp2.isNotEmpty() && mdp2 != mdp, aide = if (mdp2.isNotEmpty() && mdp2 != mdp) "Les deux mots de passe diffèrent." else null)
        Spacer(Modifier.height(6.dp))
        Bouton("Créer mon compte", { vm.inscription(mail, mdp, nom, prenom, societe, surCree) }, Modifier.fillMaxWidth(),
            actif = valide, chargement = occupe, icone = Ico.PERSONNE)
        Text("En créant un compte, vous confirmez avoir accepté les conditions d'utilisation de FluxLite.",
            style = MaterialTheme.typography.bodySmall, color = c.texte3)
    }
}

@Composable
private fun CompteEnAttente(message: String, retour: () -> Unit) {
    MessageVide(Ico.HORLOGE, "Compte créé", message) {
        Bouton("J'ai été validé : me connecter", retour, style = StyleBouton.DISCRET)
    }
}
