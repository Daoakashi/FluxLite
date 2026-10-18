package com.daoakashi.fluxlite.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.data.idCamera
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
