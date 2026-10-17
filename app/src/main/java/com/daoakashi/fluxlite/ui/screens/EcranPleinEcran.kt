package com.daoakashi.fluxlite.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.daoakashi.fluxlite.net.Camera
import com.daoakashi.fluxlite.net.ErreurApi
import com.daoakashi.fluxlite.net.messageErreur
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.theme.Flux
import kotlinx.coroutines.delay
import java.util.Locale

fun Context.activite(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Une caméra sur tout l'écran : vidéo en direct (MJPEG), zoom à deux doigts, double appui pour zoomer,
 * un appui pour afficher ou masquer les commandes, rotation libre ou forcée en paysage.
 */
@Composable
fun EcranPleinEcran(vm: AppViewModel, camInitiale: Camera, fermer: () -> Unit) {
    val c = Flux.c
    val contexte = LocalContext.current
    val vue = LocalView.current
    val activite = remember { contexte.activite() }
    val ec by vm.cameras.collectAsState()
    val etat by vm.etat.collectAsState()
    val cam = ec.cameras.firstOrNull { it.id == camInitiale.id } ?: camInitiale
    val muette = etat?.cloche?.cameraMuette(cam.id) == true
    var image by remember { mutableStateOf<ImageBitmap?>(Vignettes.images[cam.id]) }
    var personnes by remember { mutableIntStateOf(cam.personnes) }
    var erreur by remember { mutableStateOf<String?>(null) }
    var commandes by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var decalage by remember { mutableStateOf(Offset.Zero) }
    var paysageForce by remember { mutableStateOf(false) }
    var ips by remember { mutableFloatStateOf(0f) }
    val paysage = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(onBack = fermer)

    // Plein écran immersif + écran toujours allumé, rétablis en quittant.
    DisposableEffect(Unit) {
        val fenetre = activite?.window
        val ctrl = fenetre?.let { WindowCompat.getInsetsController(it, vue) }
        ctrl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctrl?.hide(WindowInsetsCompat.Type.systemBars())
        vue.keepScreenOn = true
        onDispose {
            ctrl?.show(WindowInsetsCompat.Type.systemBars())
            vue.keepScreenOn = false
            activite?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    LaunchedEffect(paysageForce) {
        activite?.requestedOrientation = if (paysageForce) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    LaunchedEffect(commandes) {
        if (commandes) { delay(4_000); commandes = false }
    }

    // Vidéo : flux MJPEG, reprise automatique en cas de coupure.
    LaunchedEffect(cam.id) {
        while (true) {
            val api = vm.apiCourante() ?: break
            try {
                var n = 0
                var t0 = System.currentTimeMillis()
                api.flux(cam.id, fps = 15, largeur = 1600).collect { f ->
                    val b = f.image.asImageBitmap()
                    image = b
                    Vignettes.images[cam.id] = b
                    personnes = f.personnes
                    erreur = null
                    n++
                    val dt = System.currentTimeMillis() - t0
                    if (dt >= 2_000) { ips = n * 1000f / dt; n = 0; t0 = System.currentTimeMillis() }
                }
                erreur = "Flux interrompu, reconnexion…"
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                erreur = if (e is ErreurApi && e.code == 429) e.message else messageErreur(e)
            }
            delay(2_000)
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { commandes = !commandes },
                    onDoubleTap = { p ->
                        if (zoom > 1.05f) { zoom = 1f; decalage = Offset.Zero }
                        else { zoom = 2.5f; decalage = (Offset(size.width / 2f, size.height / 2f) - p) * 1.5f }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, deplacement, z, _ ->
                    zoom = (zoom * z).coerceIn(1f, 6f)
                    val maxX = size.width * (zoom - 1) / 2
                    val maxY = size.height * (zoom - 1) / 2
                    decalage = if (zoom <= 1f) Offset.Zero else Offset(
                        (decalage.x + deplacement.x).coerceIn(-maxX, maxX), (decalage.y + deplacement.y).coerceIn(-maxY, maxY))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(it, cam.nom, Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom; translationX = decalage.x; translationY = decalage.y
            }, contentScale = ContentScale.Fit)
        } ?: CircularProgressIndicator(color = c.accent)

        AnimatedVisibility(commandes, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoutonIcone(fermer, fond = Color(0x66000000), description = "Retour") { Icone(Ico.RETOUR, couleur = Color.White) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cam.nom, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Pulsation(if (personnes > 0) c.personne else c.succes, personnes > 0, 7.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                (if (personnes > 0) "$personnes personne(s)" else "En direct") +
                                    (if (ips > 0) String.format(Locale.FRANCE, " · %.0f img/s", ips) else "") +
                                    (cam.resolution?.let { " · $it" } ?: ""),
                                color = Color(0xFFCFD8E6), style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    BoutonIcone({ vm.basculerMuette(cam.id) }, fond = Color(0x66000000),
                        description = if (muette) "Activer les alertes" else "Couper les alertes") {
                        Icone(if (muette) Ico.CLOCHE_BARREE else Ico.CLOCHE_SIMPLE, couleur = if (muette) Color(0xFF9AA8BC) else Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    BoutonIcone({ paysageForce = !paysageForce }, fond = if (paysageForce) c.accent else Color(0x66000000),
                        description = if (paysage) "Rotation libre" else "Passer en paysage") {
                        Icone(Ico.ROTATION, couleur = Color.White)
                    }
                }
                if (zoom > 1.05f) {
                    Puce(String.format(Locale.FRANCE, "Zoom ×%.1f · double appui pour revenir", zoom), c.accent,
                        Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp))
                }
            }
        }
        erreur?.let {
            Puce(it, c.attention, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp))
        }
    }
}
