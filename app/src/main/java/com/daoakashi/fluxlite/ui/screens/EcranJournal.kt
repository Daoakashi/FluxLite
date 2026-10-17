package com.daoakashi.fluxlite.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daoakashi.fluxlite.net.Camera
import com.daoakashi.fluxlite.net.Evenement
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.theme.Flux
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FORMAT_JOUR = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE)
private val FORMAT_HEURE = SimpleDateFormat("HH:mm", Locale.FRANCE)

fun libelleJour(t: Double): String {
    val cal = Calendar.getInstance().apply { timeInMillis = (t * 1000).toLong() }
    val aujourdhui = Calendar.getInstance()
    val hier = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun meme(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        meme(cal, aujourdhui) -> "Aujourd'hui"
        meme(cal, hier) -> "Hier"
        else -> FORMAT_JOUR.format(Date((t * 1000).toLong())).replaceFirstChar { it.uppercase() }
    }
}

/** Journal en « rivière » : une ligne de temps verticale, un point coloré par événement, regroupés par jour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcranJournal(vm: AppViewModel, surOuvrir: (Camera) -> Unit, basDePage: androidx.compose.ui.unit.Dp) {
    val c = Flux.c
    val j by vm.journal.collectAsState()
    val ec by vm.cameras.collectAsState()
    val liste = rememberLazyListState()
    var photo by remember { mutableStateOf<Evenement?>(null) }

    LaunchedEffect(Unit) { vm.chargerJournal() }
    val finAtteinte by remember {
        derivedStateOf { liste.layoutInfo.visibleItemsInfo.lastOrNull()?.index == liste.layoutInfo.totalItemsCount - 1 }
    }
    LaunchedEffect(finAtteinte) { if (finAtteinte && j.evenements.isNotEmpty()) vm.chargerJournal(reinitialiser = false) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)) {
            Text("Journal", style = MaterialTheme.typography.headlineMedium, color = c.texte)
            Text("Alertes de vos caméras, les plus récentes en haut", style = MaterialTheme.typography.bodySmall, color = c.texte2)
        }
        // filtres par caméra
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Filtre("Toutes", j.filtreCamera == null) { vm.chargerJournal(camera = null) } }
            items(ec.cameras, key = { it.id }) { cam ->
                Filtre(cam.nom, j.filtreCamera == cam.id) { vm.chargerJournal(camera = cam.id) }
            }
        }
        PullToRefreshBox(isRefreshing = j.chargement && j.evenements.isEmpty(), onRefresh = { vm.chargerJournal() },
            modifier = Modifier.fillMaxSize()) {
            if (j.evenements.isEmpty() && !j.chargement) {
                Column(Modifier.fillMaxSize()) {
                    if (j.erreur != null) MessageVide(Ico.SERVEUR, "Journal indisponible", j.erreur!!)
                    else MessageVide(Ico.JOURNAL, "Rien à signaler", "Aucune alerte pour le moment. Tirez vers le bas pour actualiser.")
                }
            } else {
                LazyColumn(state = liste, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = basDePage + 16.dp),
                    modifier = Modifier.fillMaxSize()) {
                    var jourPrecedent = ""
                    for ((i, e) in j.evenements.withIndex()) {
                        val jour = libelleJour(e.heure)
                        if (jour != jourPrecedent) {
                            jourPrecedent = jour
                            item(key = "jour-$jour-$i") {
                                Text(jour, style = MaterialTheme.typography.titleSmall, color = c.texte2,
                                    modifier = Modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp))
                            }
                        }
                        item(key = e.id) {
                            LigneEvenement(vm, e, premier = i == 0, dernier = i == j.evenements.lastIndex,
                                surPhoto = { photo = e },
                                surCamera = { ec.cameras.firstOrNull { it.id == e.cameraId }?.let(surOuvrir) })
                        }
                    }
                    if (j.chargement) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
    photo?.let { e -> GrandePhoto(vm, e) { photo = null } }
}

@Composable
private fun Filtre(texte: String, choisi: Boolean, onClick: () -> Unit) {
    val c = Flux.c
    Text(
        texte, color = if (choisi) Color.White else c.texte2, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (choisi) c.accent else c.surface)
            .border(1.dp, if (choisi) c.accent else c.trait, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun photoEvenement(vm: AppViewModel, e: Evenement): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, e.id) {
        if (e.photo) value = runCatching { vm.apiCourante()?.photo(e.id)?.asImageBitmap() }.getOrNull()
    }
    return image
}

@Composable
private fun LigneEvenement(vm: AppViewModel, e: Evenement, premier: Boolean, dernier: Boolean, surPhoto: () -> Unit, surCamera: () -> Unit) {
    val c = Flux.c
    val couleur = when {
        e.prioritaire -> c.listeNoire
        e.niveau == "alerte" -> c.personne
        e.niveau == "video" -> c.visage
        else -> c.accentVif
    }
    val image = photoEvenement(vm, e)
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Text(FORMAT_HEURE.format(Date((e.heure * 1000).toLong())), style = MaterialTheme.typography.labelMedium,
            color = c.texte2, modifier = Modifier.width(46.dp).padding(top = 18.dp))
        // la rivière
        Box(Modifier.width(22.dp).fillMaxHeight()) {
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width / 2
                val trait = c.trait
                drawLine(trait, Offset(x, if (premier) 26.dp.toPx() else 0f), Offset(x, if (dernier) 26.dp.toPx() else size.height), 2.dp.toPx())
                drawCircle(couleur.copy(alpha = 0.25f), 9.dp.toPx(), Offset(x, 26.dp.toPx()))
                drawCircle(couleur, 5.dp.toPx(), Offset(x, 26.dp.toPx()))
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier.weight(1f).padding(vertical = 6.dp).clip(RoundedCornerShape(18.dp)).background(c.surface)
                .border(1.dp, if (e.prioritaire) c.listeNoire.copy(alpha = 0.6f) else c.trait.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .clickable(onClick = if (e.photo) surPhoto else surCamera).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (e.prioritaire) Text("LISTE NOIRE", style = MaterialTheme.typography.labelSmall, color = c.listeNoire)
                Text(e.texte, style = MaterialTheme.typography.bodyMedium, color = c.texte, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icone(Ico.CAMERAS, taille = 14.dp, couleur = c.texte3)
                    Spacer(Modifier.width(5.dp))
                    Text(e.camera, style = MaterialTheme.typography.bodySmall, color = c.texte3, maxLines = 1)
                }
            }
            if (e.photo) {
                Spacer(Modifier.width(10.dp))
                Box(Modifier.width(92.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).background(c.video),
                    contentAlignment = Alignment.Center) {
                    if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icone(Ico.PHOTO, taille = 18.dp, couleur = c.texte3)
                }
            }
        }
    }
}

@Composable
private fun GrandePhoto(vm: AppViewModel, e: Evenement, fermer: () -> Unit) {
    val c = Flux.c
    val image = photoEvenement(vm, e)
    Dialog(fermer, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(c.panneau)) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(c.video), contentAlignment = Alignment.Center) {
                if (image != null) Image(image, e.texte, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else CircularProgressIndicator(color = c.accent)
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.texte, style = MaterialTheme.typography.titleSmall, color = c.texte)
                    Text("${e.camera} · ${libelleJour(e.heure)} à ${FORMAT_HEURE.format(Date((e.heure * 1000).toLong()))}",
                        style = MaterialTheme.typography.bodySmall, color = c.texte2)
                }
                BoutonIcone(fermer, fond = c.surface2, description = "Fermer") { Icone(Ico.FERMER) }
            }
        }
    }
}
