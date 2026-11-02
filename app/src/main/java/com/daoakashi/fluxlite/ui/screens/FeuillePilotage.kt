package com.daoakashi.fluxlite.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.data.idCamera
import com.daoakashi.fluxlite.net.PlageHoraire
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.theme.Flux

private fun iconeCanal(cle: String) = when (cle) {
    "notif.actif" -> Ico.CLOCHE_SIMPLE
    "mail.actif" -> Ico.MAIL
    "whatsapp.actif", "telegram.actif", "sms.actif", "discord.actif" -> Ico.MESSAGE
    "appel.actif" -> Ico.TELEPHONE
    else -> Ico.SERVEUR
}

/**
 * Contrôle à distance du PC Flux : détection (générale et par caméra) et notifications de Flux
 * (mails, WhatsApp, Telegram…). Réservé aux comptes autorisés par le responsable dans Flux.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuillePilotage(vm: AppViewModel, fermer: () -> Unit) {
    val c = Flux.c
    val p by vm.pilotage.collectAsState()
    val etat by vm.etat.collectAsState()
    val srv by vm.pilotageSrv.collectAsState()
    val tous by vm.piloterTous.collectAsState()
    LaunchedEffect(Unit) { vm.chargerPilotage() }
    // PC Flux pilotables : le serveur courant ("") et les autres serveurs suivis qui ont une session
    val serveurs = buildList {
        etat?.serveur?.let { add("" to it.nom) }
        etat?.autres?.filter { it.jeton != null }?.forEach { add(it.serveur.cle to it.serveur.nom) }
    }
    val cleServeur = srv.ifEmpty { etat?.serveur?.cle.orEmpty() }
    val nomServeur = serveurs.firstOrNull { it.first == srv }?.second ?: etat?.serveur?.nom ?: "PC Flux"

    ModalBottomSheet(fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panneau) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.accentDoux), contentAlignment = Alignment.Center) {
                    Icone(Ico.PILOTAGE, taille = 24.dp, couleur = c.accentVif)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Contrôle à distance", style = MaterialTheme.typography.headlineSmall, color = c.texte)
                    Text(nomServeur, style = MaterialTheme.typography.bodySmall, color = c.texte2)
                }
                if (p.chargement) CircularProgressIndicator(Modifier.size(20.dp), color = c.accent, strokeWidth = 2.dp)
            }
            if (serveurs.size > 1) {
                Spacer(Modifier.height(14.dp))
                Pilules(serveurs, srv, { vm.choisirPilotage(it) })
                Spacer(Modifier.height(4.dp))
                LigneReglage(Ico.SERVEUR, "Appliquer aussi aux autres serveurs",
                    "Détection et notifications : le même réglage est envoyé à tous vos PC Flux") {
                    Interrupteur(tous) { vm.piloterTous.value = it }
                }
            }
            val e = p.etat
            if (e == null) {
                Spacer(Modifier.height(20.dp))
                if (p.erreur != null) {
                    MessageVide(Ico.ATTENTION, "Contrôle indisponible", p.erreur!!) {
                        Bouton("Réessayer", { vm.chargerPilotage() }, style = StyleBouton.DISCRET, icone = Ico.RECHARGER)
                    }
                }
                return@Column
            }

            Section("Détection")
            Carte {
                LigneReglage(Ico.CAMERAS, "Toutes les caméras du PC",
                    if (e.detection) "Analyse active : alertes et notifications" else "En pause : images affichées, aucune alerte",
                    couleur = if (e.detection) c.texte else c.attention) {
                    Interrupteur(e.detection) { vm.piloter("detection.actif", it) }
                }
                if (e.cameras.isNotEmpty()) HorizontalDivider(color = c.trait.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                for (cam in e.cameras) {
                    LigneReglage(Ico.PERSONNE, cam.nom,
                        when {
                            !e.detection -> "En pause (interrupteur général)"
                            cam.detection -> if (cam.enMarche) "Détection active" else "Caméra arrêtée sur le PC"
                            else -> "Détection désactivée pour cette caméra"
                        }) {
                        Interrupteur(cam.detection) { vm.piloter("camera.detection", it, idCamera(cleServeur, cam.id)) }
                    }
                }
            }

            e.horaire?.let { h ->
                CartePlageHoraire(h, e.horaireTexte, e.horsPlage, enCours = "horaire" in p.enCours) { vm.piloterHoraire(it) }
            }

            Section("Notifications envoyées par Flux")
            Carte {
                Text("Ces réglages sont ceux du PC Flux : ils valent pour toutes les personnes qui reçoivent ses mails et messages.",
                    style = MaterialTheme.typography.bodySmall, color = c.texte2, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                val general = e.canaux.firstOrNull { it.cle == "notif.actif" }
                for (canal in e.canaux) {
                    val dependant = canal.cle != "notif.actif" && canal.cle != "mail.actif" && general?.actif == false
                    LigneReglage(iconeCanal(canal.cle), canal.nom,
                        when {
                            !canal.configure -> "Pas configuré sur le PC"
                            dependant && canal.actif -> "Activé, mais les notifications sont coupées"
                            canal.actif -> "Activé"
                            else -> "Désactivé"
                        }, couleur = if (canal.configure) c.texte else c.texte3) {
                        Interrupteur(canal.actif) { if (canal.configure || !it) vm.piloter(canal.cle, it) }
                    }
                }
            }
            Text("Chaque changement est noté dans le journal de Flux avec votre nom.",
                style = MaterialTheme.typography.bodySmall, color = c.texte3, modifier = Modifier.padding(top = 16.dp, start = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Bouton("Actualiser", { vm.chargerPilotage() }, style = StyleBouton.FANTOME, icone = Ico.RECHARGER)
            }
        }
    }
}

private val JOURS = listOf("L", "M", "M", "J", "V", "S", "D")
private val JOURS_LONGS = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")

/** Plage horaire : la détection de Flux ne tourne qu'entre deux heures, les jours choisis (Flux 0.10.1+). */
@Composable
private fun CartePlageHoraire(h: PlageHoraire, texte: String, horsPlage: Boolean, enCours: Boolean,
                              enregistrer: (PlageHoraire) -> Unit) {
    val c = Flux.c
    var debut by remember(h) { mutableStateOf(h.debut) }
    var fin by remember(h) { mutableStateOf(h.fin) }
    var jours by remember(h) { mutableStateOf(h.jours.toSet()) }
    var choix by remember { mutableStateOf<String?>(null) }
    val modifie = debut != h.debut || fin != h.fin || jours != h.jours.toSet()
    Section("Plage horaire")
    Carte {
        LigneReglage(Ico.HORLOGE, "Seulement pendant une plage horaire",
            when {
                !h.actif -> "Désactivée : la détection tourne à toute heure"
                horsPlage -> "$texte — en ce moment : hors plage, détection en pause"
                else -> "$texte — en ce moment : détection active"
            }, couleur = if (h.actif && horsPlage) c.attention else c.texte) {
            Interrupteur(h.actif) { actif ->
                if (jours.isNotEmpty()) enregistrer(PlageHoraire(actif, debut, fin, jours.sorted()))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)) {
            Text("De", color = c.texte2, style = MaterialTheme.typography.bodyMedium)
            PuceChoix(debut, true) { choix = "debut" }
            Text("à", color = c.texte2, style = MaterialTheme.typography.bodyMedium)
            PuceChoix(fin, true) { choix = "fin" }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 4.dp, top = 10.dp)) {
            JOURS.forEachIndexed { i, j ->
                PuceChoix(j, i in jours, rond = true, description = JOURS_LONGS[i]) {
                    jours = if (i in jours) jours - i else jours + i
                }
            }
        }
        Text("Fin plus tôt que le début = la nuit (22:00 → 06:00). En dehors, les images restent visibles mais rien n'est analysé.",
            style = MaterialTheme.typography.bodySmall, color = c.texte3, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
        if (modifie || !h.actif) {
            Spacer(Modifier.height(10.dp))
            Bouton("Enregistrer la plage", {
                if (jours.isNotEmpty()) enregistrer(PlageHoraire(true, debut, fin, jours.sorted()))
            }, Modifier.fillMaxWidth(), icone = Ico.HORLOGE, actif = jours.isNotEmpty(), chargement = enCours)
        }
    }
    choix?.let { quel ->
        DialogueHeure(if (quel == "debut") debut else fin, { choix = null }) { v ->
            if (quel == "debut") debut = v else fin = v
            choix = null
        }
    }
}

@Composable
private fun PuceChoix(texte: String, actif: Boolean, rond: Boolean = false, description: String = texte, surClic: () -> Unit) {
    val c = Flux.c
    Box(
        Modifier
            .clip(if (rond) RoundedCornerShape(50) else RoundedCornerShape(12.dp))
            .background(if (actif) c.accent else c.surface)
            .clickable(onClickLabel = description, onClick = surClic)
            .padding(horizontal = if (rond) 0.dp else 14.dp)
            .then(if (rond) Modifier.size(36.dp) else Modifier.height(40.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(texte, color = if (actif) c.surAccent else c.texte2, style = MaterialTheme.typography.titleSmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogueHeure(valeur: String, fermer: () -> Unit, valider: (String) -> Unit) {
    val c = Flux.c
    val (hh, mm) = valeur.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 6) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val etat = rememberTimePickerState(initialHour = hh, initialMinute = mm, is24Hour = true)
    AlertDialog(
        onDismissRequest = fermer, containerColor = c.panneau,
        text = { TimePicker(state = etat) },
        confirmButton = {
            TextButton({ valider("%02d:%02d".format(etat.hour, etat.minute)) }) { Text("Valider") }
        },
        dismissButton = { TextButton(fermer) { Text("Annuler", color = c.texte2) } },
    )
}

