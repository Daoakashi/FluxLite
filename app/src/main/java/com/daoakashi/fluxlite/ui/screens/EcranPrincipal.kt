package com.daoakashi.fluxlite.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.net.Camera
import com.daoakashi.fluxlite.notif.Notifications
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.theme.Flux
import com.daoakashi.fluxlite.update.EtatMaj
import com.daoakashi.fluxlite.update.MiseAJour

private enum class Onglet(val titre: String, val ico: Ico) {
    CAMERAS("Caméras", Ico.CAMERAS), JOURNAL("Journal", Ico.JOURNAL), COMPTE("Compte", Ico.COMPTE)
}

@Composable
fun EcranPrincipal(vm: AppViewModel) {
    val c = Flux.c
    val contexte = LocalContext.current
    var onglet by rememberSaveable { mutableStateOf(Onglet.CAMERAS) }
    var plein by rememberSaveable { mutableStateOf<String?>(null) }
    var camPlein by rememberSaveable { mutableStateOf<String?>(null) }
    val ec by vm.cameras.collectAsState()
    val demandee by vm.cameraDemandee.collectAsState()
    val maj by MiseAJour.etat.collectAsState()
    var bandeauMasque by rememberSaveable { mutableStateOf(false) }
    val hauteurDock = 64.dp
    val basDePage = hauteurDock + 24.dp + with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    // Alertes : permission (Android 13+), veille, mises à jour
    val demande = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.demarrerVeille() }
    LaunchedEffect(Unit) {
        vm.chargerProfil()
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.autorisees(contexte)) demande.launch(Manifest.permission.POST_NOTIFICATIONS)
        else vm.demarrerVeille()
        vm.verifierMaj(silencieux = true)
    }
    // Notification touchée : ouvrir directement la caméra
    LaunchedEffect(demandee) {
        demandee?.let { id -> plein = id; camPlein = ec.cameras.firstOrNull { it.id == id }?.nom; onglet = Onglet.CAMERAS }
        vm.cameraDemandee.value = null
    }
    val ouvrir: (Camera) -> Unit = { plein = it.id; camPlein = it.nom }

    Box(Modifier.fillMaxSize().background(c.fond)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            AnimatedContent(onglet, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "onglet") { o ->
                when (o) {
                    Onglet.CAMERAS -> EcranCameras(vm, ouvrir, basDePage)
                    Onglet.JOURNAL -> EcranJournal(vm, ouvrir, basDePage)
                    Onglet.COMPTE -> EcranCompte(vm, basDePage)
                }
            }
        }
        val nouvelle = (maj as? EtatMaj.Disponible)?.v
        AnimatedVisibility(nouvelle != null && !bandeauMasque && onglet != Onglet.COMPTE,
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = hauteurDock + 24.dp, start = 16.dp, end = 16.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut()) {
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(c.accentDoux).border(1.dp, c.accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .clickable { onglet = Onglet.COMPTE }.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(Ico.TELECHARGER, taille = 20.dp, couleur = c.accentVif)
                Spacer(Modifier.width(10.dp))
                Text("FluxLite ${nouvelle?.versionName} est disponible", color = c.texte, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(6.dp))
                Box(Modifier.clip(RoundedCornerShape(50)).clickable { bandeauMasque = true }.padding(8.dp)) {
                    Icone(Ico.FERMER, taille = 16.dp, couleur = c.texte2)
                }
            }
        }
        Dock(onglet, { onglet = it }, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 14.dp).height(hauteurDock))

        AnimatedVisibility(plein != null, enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut() + scaleOut(targetScale = 0.96f)) {
            val id = plein
            if (id != null) {
                val cam = ec.cameras.firstOrNull { it.id == id } ?: Camera(id, camPlein ?: "Caméra", "chargement")
                EcranPleinEcran(vm, cam) { plein = null }
            }
        }
    }
}

/** Dock flottant : l'onglet choisi s'élargit et affiche son nom. */
@Composable
private fun Dock(choisi: Onglet, surChoix: (Onglet) -> Unit, modifier: Modifier) {
    val c = Flux.c
    Row(
        modifier.shadow(18.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black, spotColor = Color.Black)
            .clip(RoundedCornerShape(32.dp)).background(c.panneau.copy(alpha = 0.97f))
            .border(1.dp, c.trait, RoundedCornerShape(32.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (o in Onglet.entries) {
            val sel = o == choisi
            val fond by animateColorAsState(if (sel) c.accent else Color.Transparent, label = "dock")
            Row(
                Modifier.clip(RoundedCornerShape(26.dp)).background(fond).clickable { surChoix(o) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icone(o.ico, taille = 22.dp, couleur = if (sel) Color.White else c.texte2)
                AnimatedVisibility(sel, enter = expandHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(o.titre, color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
