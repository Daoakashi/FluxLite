package com.daoakashi.fluxlite.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.daoakashi.fluxlite.BuildConfig
import com.daoakashi.fluxlite.net.CleServeur
import com.daoakashi.fluxlite.net.Relais
import com.daoakashi.fluxlite.net.SessionAppareil
import com.daoakashi.fluxlite.notif.Notifications
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.theme.Flux
import com.daoakashi.fluxlite.update.EtatMaj
import com.daoakashi.fluxlite.update.MiseAJour
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Dialogue { PROFIL, MOT_DE_PASSE, SUPPRESSION, DECONNEXION, SERVEUR, CGU, FEEDBACK }

private fun date(t: Double?): String = if (t == null || t <= 0) "—" else
    SimpleDateFormat("d MMM yyyy à HH:mm", Locale.FRANCE).format(Date((t * 1000).toLong()))

@SuppressLint("BatteryLife")
@Composable
fun EcranCompte(vm: AppViewModel, basDePage: androidx.compose.ui.unit.Dp) {
    val c = Flux.c
    val contexte = LocalContext.current
    val profil by vm.profil.collectAsState()
    val etat by vm.etat.collectAsState()
    val info by vm.info.collectAsState()
    val route by vm.route.collectAsState()
    val maj by MiseAJour.etat.collectAsState()
    val portee = rememberCoroutineScope()
    var dialogue by remember { mutableStateOf<Dialogue?>(null) }
    var autreChoisi by remember { mutableStateOf<com.daoakashi.fluxlite.data.ServeurEnregistre?>(null) }
    val e = etat ?: return

    // Permissions et batterie relues à chaque retour sur l'écran
    var notifOk by remember { mutableStateOf(Notifications.autorisees(contexte)) }
    var batterieOk by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        vm.chargerProfil()
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            notifOk = Notifications.autorisees(contexte)
            batterieOk = contexte.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(contexte.packageName)
        }
    }
    val demandeNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) portee.launch {
            val json = vm.exporterDonnees() ?: return@launch
            runCatching { contexte.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
                .onSuccess { vm.messages.tryEmit("Vos données ont été exportées.") }
                .onFailure { vm.messages.tryEmit("Export impossible : ${it.message}") }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = basDePage + 20.dp)) {
            Text("Compte", style = MaterialTheme.typography.headlineMedium, color = c.texte, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(14.dp))

            // --- carte d'identité ---------------------------------------------------------------
            Carte {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.accent, c.personne))).padding(3.dp)
                        .clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
                        Text(profil?.initiales ?: "·", style = MaterialTheme.typography.titleLarge, color = c.texte)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profil?.let { "${it.prenom} ${it.nom}" } ?: "…", style = MaterialTheme.typography.titleLarge, color = c.texte)
                        Text(profil?.mail ?: "", style = MaterialTheme.typography.bodySmall, color = c.texte2)
                        Spacer(Modifier.height(6.dp))
                        Row {
                            Puce(profil?.entreprise ?: "Sans entreprise", if (profil?.entreprise != null) c.succes else c.attention,
                                fond = c.surface2)
                            if (profil?.pilotage == true) {
                                Spacer(Modifier.width(6.dp))
                                Puce("Pilotage autorisé", c.accentVif, fond = c.surface2)
                            }
                        }
                    }
                }
            }

            Section("Profil")
            Carte {
                LigneReglage(Ico.CRAYON, "Modifier mon nom", profil?.let { "${it.prenom} ${it.nom}" }, onClick = { dialogue = Dialogue.PROFIL })
                LigneReglage(Ico.INFO, "Compte créé le", date(profil?.creeLe))
            }

            Section("Sécurité")
            Carte {
                LigneReglage(Ico.CADENAS, "Changer de mot de passe", "Vos autres appareils seront déconnectés",
                    onClick = { dialogue = Dialogue.MOT_DE_PASSE })
                HorizontalDivider(color = c.trait.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 6.dp))
                Text("Appareils connectés", style = MaterialTheme.typography.titleSmall, color = c.texte,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp))
                for (s in profil?.sessions.orEmpty()) LigneSession(s) { vm.revoquer(s.id) }
                if ((profil?.sessions?.size ?: 0) > 1) {
                    Spacer(Modifier.height(6.dp))
                    Bouton("Déconnecter mes autres appareils", { vm.revoquer(null) }, Modifier.fillMaxWidth(), StyleBouton.DISCRET, Ico.TELEPHONE)
                }
            }

            Section("Alertes")
            Carte {
                if (!notifOk && Build.VERSION.SDK_INT >= 33) {
                    LigneReglage(Ico.ATTENTION, "Autoriser les notifications", "Sans cela, aucune alerte ne s'affiche",
                        couleur = c.attention, onClick = { demandeNotif.launch(Manifest.permission.POST_NOTIFICATIONS) })
                }
                LigneReglage(Ico.CLOCHE_SIMPLE, "Recevoir les alertes appli fermée",
                    if (e.ecouteArrierePlan) "Une petite notification indique que FluxLite veille" else "Alertes seulement quand l'appli est ouverte") {
                    Interrupteur(e.ecouteArrierePlan) { vm.ecouteArrierePlan(it) }
                }
                if (e.ecouteArrierePlan && !batterieOk) {
                    LigneReglage(Ico.ATTENTION, "Ne pas limiter FluxLite en veille", "Recommandé : sinon Android peut retarder les alertes",
                        couleur = c.attention, onClick = {
                            runCatching {
                                contexte.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${contexte.packageName}")))
                            }
                        })
                }
                LigneReglage(Ico.RECHARGER, "Réglages des notifications Android", "Sons, vibrations, écran verrouillé", onClick = {
                    contexte.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, contexte.packageName))
                })
            }

            Section("Confidentialité")
            Carte {
                LigneReglage(Ico.OEIL_BARRE, "Bloquer les captures d'écran", "Protège les images des caméras (recommandé)") {
                    Interrupteur(e.bloquerCaptures) { vm.bloquerCaptures(it) }
                }
                LigneReglage(Ico.BOUCLIER, "Conditions d'utilisation et RGPD", "Version acceptée : ${profil?.cguVersion ?: e.cguAcceptee ?: "—"}",
                    onClick = { dialogue = Dialogue.CGU })
                LigneReglage(Ico.TELECHARGER, "Exporter mes données", "Fichier JSON (droit d'accès et de portabilité)",
                    onClick = { export.launch("fluxlite-mes-donnees.json") })
                LigneReglage(Ico.POUBELLE, "Supprimer mon compte", "Définitif : efface vos données du serveur",
                    couleur = c.danger, onClick = { dialogue = Dialogue.SUPPRESSION })
            }

            Section("Serveurs Flux")
            Carte {
                Text("Suivez plusieurs établissements : leurs caméras et leurs alertes s'affichent ensemble. Chaque PC Flux " +
                    "garde ses propres comptes ; touchez un serveur pour gérer votre compte ou le piloter.",
                    style = MaterialTheme.typography.bodySmall, color = c.texte2, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                e.serveur?.let { s ->
                    LigneReglage(Ico.SERVEUR, s.nom, "Serveur courant · compte et pilotage ci-dessous") {
                        if (e.autres.any { it.jeton != null }) Interrupteur(e.courantAffiche) { vm.afficherServeur(s.cle, it) }
                    }
                }
                for (a in e.autres) {
                    LigneReglage(Ico.SERVEUR, a.serveur.nom,
                        if (a.jeton != null) (if (a.affiche) "Caméras affichées" else "Masqué") + " · toucher pour le gérer"
                        else "Déconnecté · toucher pour vous reconnecter",
                        couleur = if (a.jeton != null) c.texte else c.attention, onClick = { autreChoisi = a }) {
                        if (a.jeton != null) Interrupteur(a.affiche) { vm.afficherServeur(a.serveur.cle, it) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Bouton("Ajouter un PC Flux", { vm.ajouterServeur() }, Modifier.fillMaxWidth(), StyleBouton.DISCRET, Ico.PLUS)
            }

            Section("Serveur courant")
            Carte {
                LigneReglage(Ico.SERVEUR, e.serveur?.nom ?: "Flux",
                    (if (e.serveur?.hote.isNullOrBlank()) "Par Internet" else "${e.serveur?.hote}:${e.serveur?.port}") +
                        (info?.versionFlux?.takeIf { it.isNotBlank() }?.let { " · Flux $it" } ?: ""),
                    onClick = { dialogue = Dialogue.SERVEUR })
                val relais = Relais.actif()?.toString()
                LigneReglage(Ico.GLOBE, "Accès hors WiFi",
                    when {
                        relais == null -> "Aucun relais connu : seulement sur le réseau du PC Flux"
                        route == "internet" -> "Connecté par Internet · relais $relais"
                        route == "direct" -> "Sur le réseau du PC Flux · relais $relais prêt"
                        else -> "Relais $relais"
                    })
                LigneReglage(Ico.CLE, "Clé du serveur", e.serveur?.cle?.let { CleServeur.formater(it).take(19) + "…" })
            }

            Section("Application")
            Carte {
                LigneReglage(Ico.MESSAGE, "Feedback", "Une idée, un problème, une question ? Écrivez-nous",
                    onClick = { dialogue = Dialogue.FEEDBACK })
                LigneReglage(Ico.GLOBE, "Site de Flux", BuildConfig.SITE_URL.removePrefix("https://"), onClick = {
                    runCatching { contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SITE_URL))) }
                })
                LigneReglage(Ico.TELEPHONE, "FluxLite ${BuildConfig.VERSION_NAME}", texteMaj(maj),
                    onClick = if (maj is EtatMaj.Telechargement || maj is EtatMaj.Installation) null else ({ vm.verifierMaj(false); Unit }))
                val m = maj
                if (m is EtatMaj.Disponible) {
                    if (m.v.notes.isNotBlank()) Text(m.v.notes, style = MaterialTheme.typography.bodySmall, color = c.texte2,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                    Bouton("Installer la version ${m.v.versionName}", {
                        if (!MiseAJour.peutInstaller(contexte)) {
                            vm.messages.tryEmit("Autorisez FluxLite à installer des applications, puis revenez ici.")
                            MiseAJour.ouvrirAutorisation(contexte)
                        } else portee.launch { MiseAJour.telechargerEtInstaller(contexte.applicationContext, m.v) }
                    }, Modifier.fillMaxWidth(), icone = Ico.TELECHARGER)
                }
                if (m is EtatMaj.Telechargement) {
                    Spacer(Modifier.height(8.dp))
                    if (m.progression >= 0) LinearProgressIndicator(progress = { m.progression }, modifier = Modifier.fillMaxWidth(),
                        color = c.accent, trackColor = c.surface2)
                    else LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.surface2)
                }
            }
            Spacer(Modifier.height(22.dp))
            Bouton("Se déconnecter", { dialogue = Dialogue.DECONNEXION }, Modifier.fillMaxWidth(), StyleBouton.DANGER, Ico.SORTIE)
            Text("FluxLite · application compagnon de Flux\nConnexion chiffrée, liée à la clé de votre serveur\n" +
                "© 2026 DMS Security · logiciel libre (GNU AGPL-3.0), sans garantie\nDéveloppé avec l'aide d'outils " +
                "d'intelligence artificielle. Aucune image n'est envoyée à un service d'IA.",
                style = MaterialTheme.typography.bodySmall, color = c.texte3, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
        }
    }

    when (dialogue) {
        Dialogue.PROFIL -> DialogueProfil(profil?.prenom.orEmpty(), profil?.nom.orEmpty(), { dialogue = null }) { p, n ->
            vm.modifierProfil(p, n); dialogue = null
        }
        Dialogue.MOT_DE_PASSE -> DialogueMotDePasse({ dialogue = null }) { a, n -> vm.changerMotDePasse(a, n) { dialogue = null } }
        Dialogue.SUPPRESSION -> DialogueSuppression({ dialogue = null }) { m -> vm.supprimerCompte(m); dialogue = null }
        Dialogue.DECONNEXION -> Confirmation("Se déconnecter ?", "Vous ne recevrez plus d'alertes sur ce téléphone jusqu'à " +
            "votre prochaine connexion.", "Se déconnecter", { dialogue = null }) { vm.deconnexion(); dialogue = null }
        Dialogue.SERVEUR -> Confirmation("Retirer ce serveur ?", "FluxLite oubliera ce serveur, sa clé et votre session " +
            "(votre compte là-bas n'est pas supprimé). Pour le retrouver, il faudra scanner à nouveau son QR code.",
            "Retirer", { dialogue = null }) { vm.oublierServeur(); dialogue = null }
        Dialogue.FEEDBACK -> DialogueFeedback { dialogue = null }
        Dialogue.CGU -> androidx.compose.ui.window.Dialog({ dialogue = null },
            androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            EcranCgu(info, {}, null, surFermer = { dialogue = null })
        }
        null -> Unit
    }
    autreChoisi?.let { a ->
        AlertDialog(
            onDismissRequest = { autreChoisi = null }, containerColor = c.panneau,
            title = { Text(a.serveur.nom, color = c.texte) },
            text = {
                Text(if (a.jeton != null) "Utiliser ce PC Flux comme serveur courant pour gérer votre compte et le piloter ? " +
                    "Ses caméras et ses alertes restent affichées avec les autres."
                else "La session sur ce PC Flux a expiré. Reconnectez-vous avec votre mail et votre mot de passe.", color = c.texte2)
            },
            confirmButton = {
                Row {
                    TextButton({ vm.retirerServeur(a.serveur.cle); autreChoisi = null }) { Text("Retirer", color = c.danger) }
                    TextButton({ vm.basculerVers(a.serveur.cle); autreChoisi = null }) {
                        Text(if (a.jeton != null) "Utiliser" else "Se reconnecter")
                    }
                }
            },
            dismissButton = { TextButton({ autreChoisi = null }) { Text("Annuler", color = c.texte2) } },
        )
    }
}

private fun texteMaj(m: EtatMaj) = when (m) {
    EtatMaj.Rien -> "Toucher pour chercher une mise à jour"
    EtatMaj.Verification -> "Recherche en cours…"
    EtatMaj.AJour -> "À jour"
    is EtatMaj.Disponible -> "Nouvelle version ${m.v.versionName} disponible"
    is EtatMaj.Telechargement -> "Téléchargement de ${m.v.versionName}…" +
        if (m.progression >= 0) " ${(m.progression * 100).toInt()} %" else ""
    is EtatMaj.Installation -> "Installation de ${m.v.versionName}…"
    is EtatMaj.Erreur -> m.message
}

@Composable
private fun LigneSession(s: SessionAppareil, revoquer: () -> Unit) {
    val c = Flux.c
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icone(Ico.TELEPHONE, taille = 20.dp, couleur = if (s.courante) c.succes else c.texte2)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text((s.appareil ?: "Appareil") + if (s.courante) " · cet appareil" else "", style = MaterialTheme.typography.bodyMedium, color = c.texte)
            Text("Actif le ${date(s.activite)}" + (s.ip?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = c.texte3)
        }
        if (!s.courante) TextButton(revoquer) { Text("Retirer", color = c.danger) }
    }
}

@Composable
private fun Confirmation(titre: String, texte: String, action: String, fermer: () -> Unit, confirmer: () -> Unit) {
    val c = Flux.c
    AlertDialog(
        onDismissRequest = fermer, containerColor = c.panneau,
        title = { Text(titre, color = c.texte) },
        text = { Text(texte, color = c.texte2) },
        confirmButton = { TextButton(confirmer) { Text(action, color = c.danger) } },
        dismissButton = { TextButton(fermer) { Text("Annuler", color = c.texte2) } },
    )
}

@Composable
private fun DialogueProfil(prenom0: String, nom0: String, fermer: () -> Unit, valider: (String, String) -> Unit) {
    val c = Flux.c
    var prenom by remember { mutableStateOf(prenom0) }
    var nom by remember { mutableStateOf(nom0) }
    AlertDialog(
        onDismissRequest = fermer, containerColor = c.panneau,
        title = { Text("Modifier mon nom", color = c.texte) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Champ(prenom, { prenom = it }, "Prénom")
                Champ(nom, { nom = it }, "Nom")
            }
        },
        confirmButton = { TextButton({ valider(prenom, nom) }, enabled = prenom.isNotBlank() && nom.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(fermer) { Text("Annuler", color = c.texte2) } },
    )
}

@Composable
private fun DialogueMotDePasse(fermer: () -> Unit, valider: (String, String) -> Unit) {
    val c = Flux.c
    var ancien by remember { mutableStateOf("") }
    var nouveau by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val (_, conseil) = forceMotDePasse(nouveau)
    AlertDialog(
        onDismissRequest = fermer, containerColor = c.panneau,
        title = { Text("Changer de mot de passe", color = c.texte) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Champ(ancien, { ancien = it }, "Mot de passe actuel", motDePasse = true)
                Champ(nouveau, { nouveau = it }, "Nouveau mot de passe", motDePasse = true, aide = conseil)
                Champ(confirmation, { confirmation = it }, "Confirmer", motDePasse = true,
                    erreur = confirmation.isNotEmpty() && confirmation != nouveau)
            }
        },
        confirmButton = {
            TextButton({ valider(ancien, nouveau) },
                enabled = ancien.isNotEmpty() && motDePasseAccepte(nouveau) && nouveau == confirmation) { Text("Changer") }
        },
        dismissButton = { TextButton(fermer) { Text("Annuler", color = c.texte2) } },
    )
}

@Composable
private fun DialogueSuppression(fermer: () -> Unit, valider: (String) -> Unit) {
    val c = Flux.c
    var mdp by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = fermer, containerColor = c.panneau,
        icon = { Icone(Ico.POUBELLE, couleur = c.danger) },
        title = { Text("Supprimer mon compte", color = c.texte) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Votre compte, vos sessions et vos informations seront effacés du serveur. Cette action est définitive.",
                    color = c.texte2)
                Champ(mdp, { mdp = it }, "Mot de passe pour confirmer", motDePasse = true)
            }
        },
        confirmButton = { TextButton({ valider(mdp) }, enabled = mdp.isNotEmpty()) { Text("Supprimer définitivement", color = c.danger) } },
        dismissButton = { TextButton(fermer) { Text("Annuler", color = c.texte2) } },
    )
}
