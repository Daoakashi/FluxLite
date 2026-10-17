package com.daoakashi.fluxlite

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.daoakashi.fluxlite.notif.Notifications
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.Etape
import com.daoakashi.fluxlite.ui.icons.LogoVagues
import com.daoakashi.fluxlite.ui.lireLienAppairage
import com.daoakashi.fluxlite.ui.screens.EcranAppairage
import com.daoakashi.fluxlite.ui.screens.EcranCgu
import com.daoakashi.fluxlite.ui.screens.EcranConnexion
import com.daoakashi.fluxlite.ui.screens.EcranPrincipal
import com.daoakashi.fluxlite.ui.theme.Flux
import com.daoakashi.fluxlite.ui.theme.ThemeFlux
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15+ impose l'affichage bord à bord : barres système transparentes, l'interface gère les marges.
        super.onCreate(savedInstanceState)
        vm.verifierMaj(silencieux = true)  // nouvelle version sur le site ?
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        lireIntention(intent)

        // Captures d'écran et aperçu dans les applis récentes bloqués (réglable dans Compte).
        lifecycleScope.launch {
            vm.etat.collect { e ->
                if (e?.bloquerCaptures != false) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        setContent {
            ThemeFlux {
                val etape by vm.etape.collectAsState()
                val info by vm.info.collectAsState()
                val messages = remember { SnackbarHostState() }
                LaunchedEffect(Unit) { vm.messages.collect { messages.showSnackbar(it) } }
                Box(Modifier.fillMaxSize().background(Flux.c.fond)) {
                    AnimatedContent(etape, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "etape") { e ->
                        when (e) {
                            Etape.CHARGEMENT -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LogoVagues(taille = 72.dp) }
                            Etape.APPAIRAGE -> EcranAppairage(vm)
                            Etape.CGU -> EcranCgu(info, { vm.accepterCgu() }, { vm.oublierServeur() })
                            Etape.CONNEXION -> EcranConnexion(vm)
                            Etape.PRINCIPAL -> EcranPrincipal(vm)
                        }
                    }
                    SnackbarHost(messages, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = 90.dp)) { d ->
                        Snackbar(shape = RoundedCornerShape(16.dp), containerColor = Flux.c.surface2, contentColor = Flux.c.texte,
                            modifier = Modifier.padding(horizontal = 16.dp)) { Text(d.visuals.message) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lireIntention(intent)
    }

    override fun onResume() {
        super.onResume()
        if (vm.etape.value == Etape.PRINCIPAL) vm.demarrerVeille()
    }

    private fun lireIntention(i: Intent?) {
        i ?: return
        // QR code scanné avec l'appareil photo du téléphone : fluxlite://appairer?…
        i.data?.let { uri -> lireLienAppairage(uri.toString())?.let { vm.appairageRecu.value = it } }
        // Notification d'alerte touchée : ouvrir la caméra
        i.getStringExtra(Notifications.EXTRA_CAMERA)?.let { vm.cameraDemandee.value = it }
    }
}
