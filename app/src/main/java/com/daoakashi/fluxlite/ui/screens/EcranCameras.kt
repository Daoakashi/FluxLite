package com.daoakashi.fluxlite.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.daoakashi.fluxlite.data.Affichage
import com.daoakashi.fluxlite.data.Cloche
import com.daoakashi.fluxlite.data.ModeAlertes
import com.daoakashi.fluxlite.net.Camera
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.icons.IconeAffichage
import com.daoakashi.fluxlite.ui.icons.IconeCloche
import com.daoakashi.fluxlite.ui.icons.LogoVagues
import com.daoakashi.fluxlite.ui.theme.Flux
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Dernière image connue de chaque caméra : rien ne clignote quand on change d'affichage ou d'onglet. */
object Vignettes {
    val images = HashMap<String, ImageBitmap>()
}

fun couleurEtat(cam: Camera, c: com.daoakashi.fluxlite.ui.theme.PaletteFlux) = when (cam.etat) {
    "alerte" -> c.personne
    "direct" -> c.succes
    "chargement" -> c.attention
    else -> c.texte3
}

fun libelleEtat(cam: Camera) = when (cam.etat) {
    "alerte" -> if (cam.personnes > 1) "${cam.personnes} personnes" else "1 personne"
    "direct" -> "En direct"
    "chargement" -> "Connexion…"
    else -> "Arrêtée"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranCameras(vm: AppViewModel, surOuvrir: (Camera) -> Unit, basDePage: androidx.compose.ui.unit.Dp) {
    val c = Flux.c
    val etat by vm.etat.collectAsState()
    val ec by vm.cameras.collectAsState()
    val route by vm.route.collectAsState()
    val e = etat ?: return
    val visibles = ec.cameras.filter { it.id !in e.camerasMasquees }
    val masquees = ec.cameras.filter { it.id in e.camerasMasquees }
    var menuAffichage by remember { mutableStateOf(false) }
    var feuilleCloche by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Camera?>(null) }
    var feuilleAjout by remember { mutableStateOf(false) }
    var feuillePilotage by remember { mutableStateOf(false) }
    val grille = rememberLazyGridState()
    val portee = rememberCoroutineScope()
    val largeur = LocalConfiguration.current.screenWidthDp
    val colonnes = when (e.affichage) {
        Affichage.FIL -> if (largeur >= 840) 2 else 1
        Affichage.GRILLE -> if (largeur >= 840) 4 else if (largeur >= 600) 3 else 2
        Affichage.MOSAIQUE -> if (largeur >= 840) 6 else if (largeur >= 600) 4 else 3
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.boucleCameras() } }

    Column(Modifier.fillMaxSize()) {
        // --- barre du haut -----------------------------------------------------------------
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            LogoVagues(taille = 34.dp, anime = visibles.any { it.etat == "alerte" })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Caméras", style = MaterialTheme.typography.headlineMedium, color = c.texte)
                val direct = ec.cameras.count { it.enDirect }
                val presences = ec.cameras.sumOf { it.personnes }
                val nbServeurs = e.serveursAffiches
                Text(
                    (if (nbServeurs > 1) "$nbServeurs serveurs" else "${e.serveur?.nom ?: "Flux"}" +
                        (if (route == "internet") " · par Internet" else "")) +
                        " · $direct en direct" + if (presences > 0) " · $presences présence(s)" else "",
                    style = MaterialTheme.typography.bodySmall, color = c.texte2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                BoutonIcone({ menuAffichage = true }, description = "Changer l'affichage") {
                    IconeAffichage(e.affichage, taille = 22.dp)
                }
                DropdownMenu(menuAffichage, { menuAffichage = false }, containerColor = c.surface,
                    shape = RoundedCornerShape(18.dp)) {
                    Text("AFFICHAGE", style = MaterialTheme.typography.labelSmall, color = c.texte3,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    for (a in Affichage.entries) {
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(a.libelle, style = MaterialTheme.typography.titleSmall,
                                        color = if (a == e.affichage) c.accentVif else c.texte)
                                    Text(a.aide, style = MaterialTheme.typography.bodySmall, color = c.texte2)
                                }
                            },
                            leadingIcon = { IconeAffichage(a, taille = 22.dp, couleur = if (a == e.affichage) c.accentVif else c.texte2) },
                            onClick = { vm.affichage(a); menuAffichage = false },
                        )
                    }
                }
            }
            if (ec.pilotage) {
                Spacer(Modifier.width(8.dp))
                BoutonIcone({ feuillePilotage = true }, description = "Contrôle à distance de Flux") {
                    Icone(Ico.PILOTAGE, taille = 22.dp, couleur = if (ec.detectionGlobale) c.texte else c.attention)
                }
            }
            Spacer(Modifier.width(8.dp))
            BoutonIcone({ feuilleCloche = true }, description = "Alertes") { IconeCloche(e.cloche) }
        }

        // --- serveurs affichés : tous, un ou plusieurs (quand plusieurs PC Flux sont suivis) ----------------
        val serveurs = buildList {
            e.serveur?.takeIf { e.jeton != null }?.let { add(Triple(it.cle, it.nom, e.courantAffiche)) }
            e.autres.filter { it.jeton != null }.forEach { add(Triple(it.serveur.cle, it.serveur.nom, it.affiche)) }
        }
        if (serveurs.size > 1) {
            val tous = serveurs.all { it.third }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)) {
                item(key = "tous") {
                    PuceServeur("Tous les serveurs", tous) { serveurs.forEach { vm.afficherServeur(it.first, true) } }
                }
                items(serveurs, key = { it.first }) { (cle, nom, affiche) ->
                    PuceServeur(nom, affiche && !tous) {
                        if (tous) serveurs.forEach { vm.afficherServeur(it.first, it.first == cle) }
                        else if (!(affiche && serveurs.count { it.third } == 1)) vm.afficherServeur(cle, !affiche)
                    }
                }
            }
        }

        // --- ruban d'activité : une capsule par caméra, l'état d'un coup d'œil --------------------------
        if (visibles.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)) {
                items(visibles, key = { it.id }) { cam ->
                    val couleur = couleurEtat(cam, c)
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(c.surface)
                            .border(1.dp, couleur.copy(alpha = if (cam.etat == "alerte") 0.9f else 0.25f), RoundedCornerShape(50))
                            .clickable { portee.launch { grille.animateScrollToItem(visibles.indexOf(cam)) } }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pulsation(couleur, cam.etat == "alerte")
                        Spacer(Modifier.width(7.dp))
                        Text(cam.nom, style = MaterialTheme.typography.labelMedium, color = c.texte, maxLines = 1)
                        if (cam.personnes > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text("${cam.personnes}", style = MaterialTheme.typography.labelMedium, color = couleur)
                        }
                    }
                }
            }
        }

        // --- caméras -------------------------------------------------------------------------
        when {
            !ec.charge -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LogoVagues(taille = 56.dp) }
            ec.sansEntreprise -> MessageVide(Ico.CADENAS, "Aucune caméra attribuée",
                "Votre compte n'est encore rattaché à aucune entreprise. Votre responsable doit vous attribuer des caméras dans Flux.")
            ec.cameras.isEmpty() && ec.erreur != null -> MessageVide(Ico.SERVEUR, "Serveur injoignable", ec.erreur!!) {
                Bouton("Réessayer", { portee.launch { vm.rafraichirCameras() } }, style = StyleBouton.DISCRET, icone = Ico.RECHARGER)
            }
            ec.cameras.isEmpty() -> MessageVide(Ico.CAMERAS, "Aucune caméra", "Aucune caméra n'est disponible pour votre entreprise.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(colonnes),
                state = grille,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = basDePage + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (e.affichage == Affichage.MOSAIQUE) 6.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (e.affichage == Affichage.MOSAIQUE) 6.dp else 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (ec.erreur != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    Puce("Connexion perdue · nouvelle tentative…", c.attention, fond = c.surface2)
                }
                if (!ec.detectionGlobale) item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.attention.copy(alpha = 0.14f))
                            .then(if (ec.pilotage) Modifier.clickable { feuillePilotage = true } else Modifier).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icone(Ico.ATTENTION, taille = 20.dp, couleur = c.attention)
                        Spacer(Modifier.width(10.dp))
                        Text("Détection en pause sur le PC Flux : images visibles, aucune alerte.",
                            style = MaterialTheme.typography.bodySmall, color = c.texte, modifier = Modifier.weight(1f))
                    }
                }
                items(visibles, key = { it.id }) { cam ->
                    CarteCamera(vm, cam, e.affichage, e.cloche, { surOuvrir(cam) }, { details = cam },
                        { vm.masquer(cam.id, true) }, { feuilleAjout = true }, masquees.isNotEmpty(),
                        if (ec.pilotage) ({ vm.piloter("camera.detection", !cam.detection, cam.id); Unit }) else null)
                }
                if (masquees.isNotEmpty() || visibles.isEmpty()) item {
                    TuileAjout(e.affichage) { feuilleAjout = true }
                }
            }
        }
    }

    if (feuilleCloche) FeuilleCloche(vm, e.cloche) { feuilleCloche = false }
    if (feuillePilotage) FeuillePilotage(vm) { feuillePilotage = false; portee.launch { vm.rafraichirCameras() } }
    details?.let { cam -> FeuilleDetails(vm, ec.cameras.firstOrNull { it.id == cam.id } ?: cam, e.cloche, ec.pilotage,
        { details = null }, surOuvrir) }
    if (feuilleAjout) FeuilleAjout(vm, masquees) { feuilleAjout = false }
}

/** Point qui « respire » quand quelqu'un est détecté. */
@Composable
fun Pulsation(couleur: Color, actif: Boolean, taille: androidx.compose.ui.unit.Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "pulsation")
    val a by t.animateFloat(1f, if (actif) 0.25f else 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(taille).alpha(a).clip(CircleShape).background(couleur))
}

/** Puce d'un PC Flux dans la barre des serveurs (choisie = remplie). */
@Composable
private fun PuceServeur(texte: String, choisie: Boolean, onClick: () -> Unit) {
    val c = Flux.c
    Text(texte, style = MaterialTheme.typography.labelLarge, color = if (choisie) Color.White else c.texte2, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (choisie) c.accent else c.surface)
            .border(1.dp, if (choisie) c.accent else c.trait, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

@Composable
fun ImageEnDirect(vm: AppViewModel, cam: Camera, largeurImage: Int, intervalle: Long): ImageBitmap? {
    var image by remember(cam.id) { mutableStateOf(Vignettes.images[cam.id]) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(cam.id, cam.enDirect, largeurImage, intervalle) {
        if (!cam.enDirect) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val (api, idBrut) = vm.apiPour(cam.id) ?: return@repeatOnLifecycle
            while (true) {
                runCatching { api.image(idBrut, largeurImage) }.getOrNull()?.let {
                    val b = it.asImageBitmap()
                    Vignettes.images[cam.id] = b
                    image = b
                }
                delay(intervalle)
            }
        }
    }
    return image
}

@Composable
private fun CarteCamera(
    vm: AppViewModel, cam: Camera, mode: Affichage, cloche: Cloche,
    ouvrir: () -> Unit, voirDetails: () -> Unit, retirer: () -> Unit, ajouter: () -> Unit, peutAjouter: Boolean,
    basculerDetection: (() -> Unit)?,
) {
    val c = Flux.c
    val haptique = LocalHapticFeedback.current
    val couleur = couleurEtat(cam, c)
    val (largeurImage, intervalle, ratio) = when (mode) {
        Affichage.FIL -> Triple(960, 900L, 16f / 9f)
        Affichage.GRILLE -> Triple(640, 1500L, 4f / 3f)
        Affichage.MOSAIQUE -> Triple(400, 2500L, 1f)
    }
    val image = ImageEnDirect(vm, cam, largeurImage, intervalle)
    val alerte = cam.etat == "alerte"
    val anneau = rememberInfiniteTransition(label = "anneau")
    val lueur by anneau.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "lueur")
    val bord by animateColorAsState(if (alerte) c.personne else c.trait.copy(alpha = 0.6f), label = "bord")
    val arrondi = if (mode == Affichage.MOSAIQUE) 14.dp else 22.dp
    var menu by remember { mutableStateOf(false) }
    val muette = cloche.cameraMuette(cam.id)

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(arrondi)).background(c.surface)
            .border(if (alerte) 2.dp else 1.dp, if (alerte) bord.copy(alpha = lueur) else bord, RoundedCornerShape(arrondi))
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio).background(c.video).clickable(onClick = ouvrir)) {
            if (image != null) {
                Image(image, cam.nom, Modifier.fillMaxSize().alpha(if (cam.enDirect) 1f else 0.35f), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icone(Ico.CAMERAS, taille = 30.dp, couleur = c.texte3)
                }
            }
            // rail d'activité à gauche : la couleur dit tout (cyan = présence, vert = direct, gris = arrêtée)
            Box(Modifier.fillMaxHeight().width(4.dp).background(couleur.copy(alpha = if (alerte) lueur else 0.85f)))
            if (mode != Affichage.MOSAIQUE) {
                Puce(libelleEtat(cam), couleur, Modifier.align(Alignment.TopStart).padding(10.dp))
                if (!cam.detection) Puce("Détection coupée", c.attention, Modifier.align(Alignment.TopEnd).padding(10.dp))
            } else if (alerte) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).clip(CircleShape).background(c.personne),
                    contentAlignment = Alignment.Center) {
                    Text("${cam.personnes}", color = Color(0xFF05222A), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (mode == Affichage.MOSAIQUE) {
                Text(cam.nom, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC050B15))))
                        .padding(horizontal = 8.dp, vertical = 5.dp))
            }
        }
        if (mode != Affichage.MOSAIQUE) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(cam.nom, style = MaterialTheme.typography.titleSmall, color = c.texte, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (cam.serveur.isNotBlank()) {
                        Text(cam.serveur, style = MaterialTheme.typography.labelSmall, color = c.texte3, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    if (mode == Affichage.FIL) {
                        Text(listOfNotNull(cam.resolution, if (cam.enDirect) String.format(Locale.FRANCE, "%.0f img/s", cam.fps) else null)
                            .joinToString(" · ").ifEmpty { "Hors ligne" },
                            style = MaterialTheme.typography.bodySmall, color = c.texte3)
                    }
                }
                // bouton notification de la caméra
                BoutonIcone({ haptique.performHapticFeedback(HapticFeedbackType.LongPress); vm.basculerMuette(cam.id) },
                    taille = 38.dp, fond = Color.Transparent,
                    description = if (muette) "Activer les alertes de cette caméra" else "Couper les alertes de cette caméra") {
                    Icone(if (muette) Ico.CLOCHE_BARREE else Ico.CLOCHE_SIMPLE, taille = 20.dp,
                        couleur = if (muette) c.texte3 else c.accentVif)
                }
                Box {
                    BoutonIcone({ menu = true }, taille = 38.dp, fond = Color.Transparent, description = "Plus d'options") {
                        Icone(Ico.POINTS, taille = 20.dp, couleur = c.texte2)
                    }
                    MenuCamera(menu, { menu = false }, voirDetails, retirer, ajouter, peutAjouter,
                        basculerDetection?.let { b -> (if (cam.detection) "Désactiver la détection" else "Activer la détection") to b })
                }
            }
        }
    }
}

@Composable
private fun MenuCamera(ouvert: Boolean, fermer: () -> Unit, details: () -> Unit, retirer: () -> Unit, ajouter: () -> Unit,
                       peutAjouter: Boolean, detection: Pair<String, () -> Unit>?) {
    val c = Flux.c
    DropdownMenu(ouvert, fermer, containerColor = c.surface, shape = RoundedCornerShape(16.dp)) {
        DropdownMenuItem({ Text("Détails de la caméra", color = c.texte) }, { fermer(); details() },
            leadingIcon = { Icone(Ico.INFO, taille = 20.dp, couleur = c.texte2) })
        if (detection != null) DropdownMenuItem({ Text(detection.first, color = c.texte) }, { fermer(); detection.second() },
            leadingIcon = { Icone(Ico.PILOTAGE, taille = 20.dp, couleur = c.texte2) })
        DropdownMenuItem({ Text("Retirer ce flux de l'écran", color = c.texte) }, { fermer(); retirer() },
            leadingIcon = { Icone(Ico.OEIL_BARRE, taille = 20.dp, couleur = c.texte2) })
        if (peutAjouter) DropdownMenuItem({ Text("Ajouter un autre flux", color = c.texte) }, { fermer(); ajouter() },
            leadingIcon = { Icone(Ico.PLUS, taille = 20.dp, couleur = c.texte2) })
    }
}

@Composable
private fun TuileAjout(mode: Affichage, onClick: () -> Unit) {
    val c = Flux.c
    Box(
        Modifier.fillMaxWidth().aspectRatio(if (mode == Affichage.FIL) 16f / 9f else if (mode == Affichage.GRILLE) 4f / 3f else 1f)
            .clip(RoundedCornerShape(22.dp)).border(1.5.dp, c.trait, RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icone(Ico.PLUS, taille = 28.dp, couleur = c.accentVif)
            if (mode != Affichage.MOSAIQUE) {
                Spacer(Modifier.height(6.dp))
                Text("Ajouter un flux", style = MaterialTheme.typography.labelLarge, color = c.texte2)
            }
        }
    }
}

// --- feuilles ------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleCloche(vm: AppViewModel, cloche: Cloche, fermer: () -> Unit) {
    val c = Flux.c
    ModalBottomSheet(fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panneau) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconeCloche(cloche, taille = 30.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Alertes", style = MaterialTheme.typography.headlineSmall, color = c.texte)
                    Text(resumeCloche(cloche), style = MaterialTheme.typography.bodySmall, color = c.texte2)
                }
            }
            Section("Notifications")
            Pilules(ModeAlertes.entries.map { it to it.libelle }, cloche.mode, { vm.modeAlertes(it) })
            Text(
                when (cloche.mode) {
                    ModeAlertes.ACTIVES -> "Son et vibration à chaque alerte."
                    ModeAlertes.SILENCIEUSES -> "Les alertes s'affichent sans son ni vibration (la liste noire sonne toujours)."
                    ModeAlertes.COUPEES -> "Aucune notification. Le journal continue d'enregistrer les alertes."
                }, style = MaterialTheme.typography.bodySmall, color = c.texte2, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            Section("Délai entre deux alertes d'une même caméra")
            Pilules(listOf(0 to "Aucun", 1 to "1 min", 5 to "5 min", 15 to "15 min", 30 to "30 min"),
                cloche.delaiMinutes, { vm.delai(it) })
            Section("Mettre en pause")
            if (cloche.enPause) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.attention.copy(alpha = 0.14f)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icone(Ico.HORLOGE, couleur = c.attention)
                    Spacer(Modifier.width(10.dp))
                    Text("En pause jusqu'à ${heure(cloche.pauseJusqua)}", Modifier.weight(1f), color = c.texte,
                        style = MaterialTheme.typography.titleSmall)
                    Bouton("Reprendre", { vm.pause(0) }, style = StyleBouton.DISCRET)
                }
            } else {
                Pilules(listOf(0 to "Non", 1 to "1 h", 2 to "2 h", 4 to "4 h", 8 to "8 h", 24 to "24 h"), 0, { vm.pause(it) })
            }
            Text("Chaque caméra a aussi sa propre cloche, sous son image.", style = MaterialTheme.typography.bodySmall,
                color = c.texte3, modifier = Modifier.padding(top = 18.dp, start = 4.dp))
        }
    }
}

fun resumeCloche(cl: Cloche) = when {
    cl.mode == ModeAlertes.COUPEES -> "Notifications coupées"
    cl.enPause -> "En pause jusqu'à ${heure(cl.pauseJusqua)}"
    cl.mode == ModeAlertes.SILENCIEUSES -> "Silencieuses" + if (cl.delaiMinutes > 0) " · une par ${cl.delaiMinutes} min" else ""
    else -> "Actives" + if (cl.delaiMinutes > 0) " · une par ${cl.delaiMinutes} min et par caméra" else ""
}

fun heure(ms: Long): String = java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date(ms))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleDetails(vm: AppViewModel, cam: Camera, cloche: Cloche, pilotage: Boolean, fermer: () -> Unit,
                           ouvrir: (Camera) -> Unit) {
    val c = Flux.c
    val image = ImageEnDirect(vm, cam, 960, 1200L)
    ModalBottomSheet(fermer, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.panneau) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(18.dp)).background(c.video)
                .clickable { fermer(); ouvrir(cam) }) {
                image?.let { Image(it, cam.nom, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Puce(libelleEtat(cam), couleurEtat(cam, c), Modifier.align(Alignment.TopStart).padding(10.dp))
                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(CircleShape).background(Color(0xB3050B15)).padding(8.dp)) {
                    Icone(Ico.PLEIN_ECRAN, taille = 18.dp, couleur = Color.White)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(cam.nom, style = MaterialTheme.typography.headlineSmall, color = c.texte)
            Spacer(Modifier.height(10.dp))
            for ((titre, valeur) in listOf(
                "État" to libelleEtat(cam),
                "Personnes visibles" to "${cam.personnes}",
                "Résolution" to (cam.resolution ?: "—"),
                "Images par seconde" to if (cam.enDirect) String.format(Locale.FRANCE, "%.1f", cam.fps) else "—",
                "Détection" to if (cam.detection) "Active" else "Désactivée",
            )) {
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Text(titre, Modifier.weight(1f), color = c.texte2, style = MaterialTheme.typography.bodyMedium)
                    Text(valeur, color = c.texte, style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider(color = c.trait.copy(alpha = 0.5f))
            }
            Spacer(Modifier.height(8.dp))
            if (pilotage) {
                LigneReglage(Ico.PILOTAGE, "Détection sur le PC Flux",
                    if (cam.detection) "Analyse active pour cette caméra" else "Désactivée : aucune alerte pour cette caméra") {
                    Interrupteur(cam.detection) { vm.piloter("camera.detection", it, cam.id) }
                }
            }
            LigneReglage(Ico.CLOCHE_SIMPLE, "Alertes de cette caméra",
                if (cloche.cameraMuette(cam.id)) "Coupées pour cette caméra" else "Actives (selon la cloche générale)") {
                Interrupteur(!cloche.cameraMuette(cam.id)) { vm.basculerMuette(cam.id) }
            }
            LigneReglage(Ico.OEIL_BARRE, "Retirer ce flux de l'écran", "Vous pourrez le rajouter avec « Ajouter un flux »",
                onClick = { vm.masquer(cam.id, true); fermer() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeuilleAjout(vm: AppViewModel, masquees: List<Camera>, fermer: () -> Unit) {
    val c = Flux.c
    ModalBottomSheet(fermer, containerColor = c.panneau) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Text("Ajouter un flux", style = MaterialTheme.typography.headlineSmall, color = c.texte)
            Text("Caméras de votre entreprise qui ne sont pas affichées.", style = MaterialTheme.typography.bodySmall, color = c.texte2)
            Spacer(Modifier.height(12.dp))
            if (masquees.isEmpty()) {
                Text("Toutes vos caméras sont déjà affichées.", color = c.texte2, modifier = Modifier.padding(vertical = 16.dp))
            }
            for (cam in masquees) {
                LigneReglage(Ico.CAMERAS, cam.nom, libelleEtat(cam)) {
                    Bouton("Afficher", { vm.masquer(cam.id, false); if (masquees.size == 1) fermer() }, style = StyleBouton.DISCRET, icone = Ico.PLUS)
                }
            }
        }
    }
}
