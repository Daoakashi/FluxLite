package com.daoakashi.fluxlite.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.net.CleServeur
import com.daoakashi.fluxlite.ui.AppViewModel
import com.daoakashi.fluxlite.ui.lireLienAppairage
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.LogoVagues
import com.daoakashi.fluxlite.ui.theme.Flux
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Première étape : relier le téléphone à un PC Flux (QR code ou saisie de l'adresse et de la clé). */
@Composable
fun EcranAppairage(vm: AppViewModel) {
    val c = Flux.c
    val contexte = LocalContext.current
    val occupe by vm.occupe.collectAsState()
    val recu by vm.appairageRecu.collectAsState()
    var adresse by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("47810") }
    var cle by rememberSaveable { mutableStateOf("") }

    fun connecter(hotes: List<String>, p: Int, k: String, relais: String = "") = vm.appairer(hotes, p, k, relais)

    LaunchedEffect(recu) {
        recu?.let {
            adresse = it.hotes.first(); port = it.port.toString(); cle = CleServeur.formater(it.cle)
            vm.appairageRecu.value = null
            connecter(it.hotes, it.port, it.cle, it.relais)
        }
    }

    fun scanner() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(contexte, options).startScan()
            .addOnSuccessListener { code ->
                val a = lireLienAppairage(code.rawValue.orEmpty())
                if (a == null) vm.messages.tryEmit("Ce QR code ne vient pas de Flux (page FluxLite).")
                else {
                    adresse = a.hotes.first(); port = a.port.toString(); cle = CleServeur.formater(a.cle)
                    connecter(a.hotes, a.port, a.cle, a.relais)
                }
            }
            .addOnFailureListener {
                vm.messages.tryEmit("Lecteur de QR code indisponible sur ce téléphone : saisissez l'adresse et la clé.")
            }
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.accentDoux, c.fond, c.fond)))
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            LogoVagues(taille = 64.dp)
            Spacer(Modifier.height(22.dp))
            Text("Bienvenue sur FluxLite", style = MaterialTheme.typography.displaySmall, color = c.texte)
            Spacer(Modifier.height(8.dp))
            Text("Reliez ce téléphone au PC Flux qui surveille vos caméras. Dans Flux, ouvrez la page « FluxLite » : " +
                "le QR code et la clé du serveur y sont affichés.", style = MaterialTheme.typography.bodyLarge, color = c.texte2)
            Spacer(Modifier.height(28.dp))
            Bouton("Scanner le QR code", ::scanner, Modifier.fillMaxWidth(), icone = Ico.QR, chargement = occupe)
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = c.trait)
                Text("  ou à la main  ", color = c.texte3, style = MaterialTheme.typography.labelMedium)
                HorizontalDivider(Modifier.weight(1f), color = c.trait)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Champ(adresse, { adresse = it.trim() }, "Adresse du PC", Modifier.weight(2.2f), clavier = KeyboardType.Uri,
                    aide = "Facultative hors de son réseau")
                Champ(port, { port = it.filter(Char::isDigit).take(5) }, "Port", Modifier.weight(1f), clavier = KeyboardType.Number)
            }
            Spacer(Modifier.height(10.dp))
            Champ(cle, { cle = it.uppercase().take(40) }, "Clé du serveur",
                aide = "32 caractères, ex. 7X51-S5WV-SGBE-… (les tirets sont facultatifs)")
            Spacer(Modifier.height(18.dp))
            Bouton("Se connecter au serveur", {
                connecter(adresse.split(',', ' ').filter { it.isNotBlank() }, port.toIntOrNull() ?: 47810, cle)
            }, Modifier.fillMaxWidth(), StyleBouton.DISCRET, Ico.SERVEUR, chargement = occupe)
            Spacer(Modifier.height(26.dp))
            Carte {
                Row(verticalAlignment = Alignment.Top) {
                    com.daoakashi.fluxlite.ui.icons.Icone(Ico.BOUCLIER, taille = 22.dp, couleur = c.succes)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Pourquoi une clé ?", style = MaterialTheme.typography.titleSmall, color = c.texte)
                        Spacer(Modifier.height(4.dp))
                        Text("La clé est l'empreinte unique de votre PC Flux. FluxLite la vérifie à chaque connexion et " +
                            "refuse tout autre ordinateur. Sur le WiFi du PC Flux, la connexion est directe ; ailleurs " +
                            "(4G, autre site), elle passe par Internet grâce au relais Flux, sans rien ouvrir dans la " +
                            "box. Dans les deux cas, images et alertes restent chiffrées de bout en bout entre votre PC " +
                            "et ce téléphone : le relais ne peut pas les lire.",
                            style = MaterialTheme.typography.bodySmall, color = c.texte2)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
