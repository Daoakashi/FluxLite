package com.daoakashi.fluxlite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.net.InfoServeur
import com.daoakashi.fluxlite.ui.CGU_VERSION
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.theme.Flux

/** Conditions d'utilisation et rappel RGPD : texte court, clair et professionnel. */
fun articlesCgu(info: InfoServeur?): List<Pair<String, String>> {
    val responsable = info?.responsable?.takeIf { it.isNotBlank() } ?: "l'entreprise qui exploite le serveur Flux auquel vous vous connectez"
    val contact = info?.contact?.takeIf { it.isNotBlank() }?.let { " à l'adresse $it" } ?: " auprès de ce responsable"
    val duree = info?.conservationJournal ?: 7
    return listOf(
        "1. Objet" to "FluxLite permet aux personnes autorisées de consulter en direct les caméras d'un système de " +
            "vidéosurveillance Flux et d'en recevoir les alertes. L'application ne fonctionne qu'avec un serveur Flux " +
            "privé ; elle n'est pas un service public.",
        "2. Accès et compte" to "Votre compte est personnel. Il est validé par le responsable du serveur, qui décide des " +
            "caméras auxquelles vous avez accès. Vous gardez votre mot de passe secret et signalez sans délai toute " +
            "utilisation suspecte. Le responsable peut suspendre ou supprimer un compte à tout moment.",
        "3. Bon usage des images" to "Les images montrent des personnes : ce sont des données personnelles. Vous les " +
            "consultez uniquement pour la mission de sécurité qui vous est confiée. Il est interdit de les enregistrer, " +
            "de faire des captures d'écran, de les diffuser ou de les utiliser à d'autres fins. Toute infraction peut " +
            "entraîner la suppression du compte et engager votre responsabilité.",
        "4. Responsable du traitement" to "Le responsable du traitement de vos données est $responsable. Les données " +
            "sont stockées sur son propre serveur Flux, et non chez l'éditeur de l'application ni chez un hébergeur tiers.",
        "5. Données vous concernant" to "Nom, prénom, adresse mail, société indiquée, empreinte chiffrée du mot de passe " +
            "(jamais le mot de passe lui-même), date d'acceptation des présentes conditions, et des informations " +
            "techniques de connexion : modèle et version du téléphone, version de FluxLite, adresse IP, dates de " +
            "connexion. Elles servent à sécuriser l'accès (base légale : exécution du contrat et intérêt légitime de " +
            "sécurité) et sont conservées tant que le compte existe.",
        "6. Alertes et journal" to "Les alertes et leurs photos sont conservées $duree jour(s) sur le serveur puis " +
            "effacées. Sur ce téléphone, rien n'est enregistré dans la galerie ; les captures d'écran peuvent être " +
            "bloquées (réglage activé par défaut).",
        "7. Sécurité" to "Les échanges sont chiffrés (TLS) et liés à la clé unique du serveur : FluxLite refuse tout " +
            "serveur qui ne présente pas cette clé. La session est chiffrée sur le téléphone par le coffre-fort Android.",
        "8. Vos droits (RGPD)" to "Vous pouvez accéder à vos données, les rectifier, les exporter, demander leur " +
            "effacement, vous opposer à leur traitement ou en demander la limitation. L'onglet Compte permet d'exporter " +
            "vos données et de supprimer votre compte. Pour toute autre demande, adressez-vous au responsable$contact. " +
            "Vous pouvez aussi saisir l'autorité de protection des données (APD en Belgique, CNIL en France).",
        "9. Évolution" to "Ces conditions peuvent évoluer ; la nouvelle version vous sera présentée et devra être " +
            "acceptée pour continuer. Version $CGU_VERSION.",
    )
}

@Composable
fun EcranCgu(info: InfoServeur?, surAccepter: () -> Unit, surChangerServeur: (() -> Unit)?, surFermer: (() -> Unit)? = null) {
    val c = Flux.c
    var coche by rememberSaveable { mutableStateOf(false) }
    val lectureSeule = surFermer != null
    Box(Modifier.fillMaxSize().background(c.fond).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Conditions d'utilisation", style = MaterialTheme.typography.headlineMedium, color = c.texte)
                    Text(info?.nom?.let { "Serveur : $it" } ?: "FluxLite", style = MaterialTheme.typography.bodyMedium, color = c.texte2)
                }
                if (surFermer != null) BoutonIcone(surFermer) { Icone(Ico.FERMER) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accentDoux).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icone(Ico.BOUCLIER, taille = 26.dp, couleur = c.accentVif)
                        Spacer(Modifier.width(12.dp))
                        Text("En bref : un accès personnel, des images à usage professionnel uniquement, vos données " +
                            "restent sur le serveur de votre responsable et vous gardez tous vos droits RGPD.",
                            style = MaterialTheme.typography.bodyMedium, color = c.texte)
                    }
                    Spacer(Modifier.height(10.dp))
                }
                items(articlesCgu(info)) { (titre, texte) ->
                    Column(Modifier.padding(vertical = 10.dp)) {
                        Text(titre, style = MaterialTheme.typography.titleSmall, color = c.texte)
                        Spacer(Modifier.height(4.dp))
                        Text(texte, style = MaterialTheme.typography.bodyMedium, color = c.texte2)
                    }
                }
            }
            if (!lectureSeule) {
                Column(Modifier.background(c.panneau).padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { coche = !coche }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(coche, { coche = it }, colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.texte3))
                        Text("J'ai lu les conditions d'utilisation et l'information sur mes données personnelles.",
                            style = MaterialTheme.typography.bodyMedium, color = c.texte)
                    }
                    Spacer(Modifier.height(10.dp))
                    Bouton("J'accepte les conditions d'utilisation", surAccepter, Modifier.fillMaxWidth(), icone = Ico.VERIF, actif = coche)
                    if (surChangerServeur != null) {
                        Spacer(Modifier.height(4.dp))
                        Bouton("Changer de serveur", surChangerServeur, Modifier.fillMaxWidth(), StyleBouton.FANTOME)
                    }
                }
            }
        }
    }
}
