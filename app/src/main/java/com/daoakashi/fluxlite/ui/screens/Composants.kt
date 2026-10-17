package com.daoakashi.fluxlite.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.ui.icons.Ico
import com.daoakashi.fluxlite.ui.icons.Icone
import com.daoakashi.fluxlite.ui.theme.Flux

enum class StyleBouton { PRINCIPAL, DISCRET, FANTOME, DANGER }

/** Bouton FluxLite : léger rétrécissement à l'appui, dégradé bleu pour l'action principale. */
@Composable
fun Bouton(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: StyleBouton = StyleBouton.PRINCIPAL,
    icone: Ico? = null,
    actif: Boolean = true,
    chargement: Boolean = false,
) {
    val c = Flux.c
    val source = remember { MutableInteractionSource() }
    val appuye by source.collectIsPressedAsState()
    val echelle by animateFloatAsState(if (appuye) 0.97f else 1f, label = "appui")
    val (fond, texteC, bord) = when (style) {
        StyleBouton.PRINCIPAL -> Triple(Brush.horizontalGradient(listOf(c.accent, Color(0xFF1D5BE8))), Color.White, Color.Transparent)
        StyleBouton.DISCRET -> Triple(Brush.horizontalGradient(listOf(c.surface2, c.surface2)), c.texte, c.trait)
        StyleBouton.FANTOME -> Triple(Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)), c.accentVif, Color.Transparent)
        StyleBouton.DANGER -> Triple(Brush.horizontalGradient(listOf(c.danger.copy(alpha = 0.12f), c.danger.copy(alpha = 0.12f))), c.danger, c.danger.copy(alpha = 0.5f))
    }
    Box(
        modifier
            .scale(echelle)
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fond)
            .border(1.dp, bord, RoundedCornerShape(16.dp))
            .clickable(source, null, enabled = actif && !chargement, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (chargement) {
                CircularProgressIndicator(Modifier.size(18.dp), color = texteC, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            } else if (icone != null) {
                Icone(icone, taille = 19.dp, couleur = texteC)
                Spacer(Modifier.width(8.dp))
            }
            Text(texte, color = texteC.copy(alpha = if (actif) 1f else 0.5f), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Bouton rond à icône (barre du haut, cartes). */
@Composable
fun BoutonIcone(onClick: () -> Unit, modifier: Modifier = Modifier, taille: Dp = 42.dp, fond: Color = Flux.c.surface,
                description: String = "", contenu: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val appuye by source.collectIsPressedAsState()
    val echelle by animateFloatAsState(if (appuye) 0.9f else 1f, label = "appui")
    Box(
        modifier
            .scale(echelle)
            .size(taille)
            .clip(CircleShape)
            .background(fond)
            .clickable(source, null, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { contenu() }
}

@Composable
fun Champ(
    valeur: String,
    surChangement: (String) -> Unit,
    libelle: String,
    modifier: Modifier = Modifier,
    motDePasse: Boolean = false,
    clavier: KeyboardType = KeyboardType.Text,
    aide: String? = null,
    erreur: Boolean = false,
    lignes: Int = 1,
) {
    val c = Flux.c
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valeur,
        onValueChange = surChangement,
        label = { Text(libelle) },
        singleLine = lignes == 1,
        maxLines = lignes,
        isError = erreur,
        supportingText = if (aide != null) { { Text(aide) } } else null,
        visualTransformation = if (motDePasse && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (motDePasse) KeyboardType.Password else clavier, imeAction = ImeAction.Next),
        trailingIcon = if (motDePasse) {
            {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { visible = !visible }, contentAlignment = Alignment.Center) {
                    Icone(if (visible) Ico.OEIL_BARRE else Ico.OEIL, taille = 20.dp, couleur = c.texte2)
                }
            }
        } else null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent, unfocusedBorderColor = c.trait, focusedLabelColor = c.accentVif,
            unfocusedLabelColor = c.texte2, cursorColor = c.accent, focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface, focusedTextColor = c.texte, unfocusedTextColor = c.texte,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Carte arrondie. */
@Composable
fun Carte(modifier: Modifier = Modifier, contenu: @Composable ColumnScope.() -> Unit) {
    val c = Flux.c
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(1.dp, c.trait.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        content = contenu,
    )
}

@Composable
fun Section(texte: String, modifier: Modifier = Modifier) {
    Text(texte.uppercase(), color = Flux.c.texte3, style = MaterialTheme.typography.labelSmall,
        modifier = modifier.padding(start = 6.dp, top = 22.dp, bottom = 8.dp))
}

/** Ligne de réglage cliquable : icône, titre, sous-titre, élément à droite. */
@Composable
fun LigneReglage(
    ico: Ico,
    titre: String,
    sousTitre: String? = null,
    couleur: Color = Flux.c.texte,
    onClick: (() -> Unit)? = null,
    droite: @Composable (() -> Unit)? = null,
) {
    val c = Flux.c
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 11.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.accentDoux), contentAlignment = Alignment.Center) {
            Icone(ico, taille = 20.dp, couleur = if (couleur == c.texte) c.accentVif else couleur)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titre, color = couleur, style = MaterialTheme.typography.titleSmall)
            if (sousTitre != null) Text(sousTitre, color = c.texte2, style = MaterialTheme.typography.bodySmall)
        }
        if (droite != null) {
            Spacer(Modifier.width(10.dp))
            droite()
        } else if (onClick != null) {
            Icone(Ico.CHEVRON, taille = 18.dp, couleur = c.texte3)
        }
    }
}

@Composable
fun Interrupteur(actif: Boolean, surChangement: (Boolean) -> Unit) {
    val c = Flux.c
    Switch(
        checked = actif, onCheckedChange = surChangement,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = c.accent, uncheckedThumbColor = c.texte2,
            uncheckedTrackColor = c.surface2, uncheckedBorderColor = c.trait,
        ),
    )
}

/** Pastille d'état (« En direct », « 2 personnes »…). */
@Composable
fun Puce(texte: String, couleur: Color, modifier: Modifier = Modifier, fond: Color = Color(0xB3050B15), point: Boolean = true) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(fond).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (point) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(couleur))
            Spacer(Modifier.width(6.dp))
        }
        Text(texte, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/** Choix parmi quelques options, en « pilules » (cloche, affichage, filtres). */
@Composable
fun <T> Pilules(options: List<Pair<T, String>>, choisi: T, surChoix: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Flux.c
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface2).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((v, lib) in options) {
            val sel = v == choisi
            val fond by animateColorAsState(if (sel) c.accent else Color.Transparent, label = "pilule")
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(fond).clickable { surChoix(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(lib, color = if (sel) Color.White else c.texte2, style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MessageVide(ico: Ico, titre: String, texte: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    val c = Flux.c
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(26.dp)).background(c.accentDoux), contentAlignment = Alignment.Center) {
            Icone(ico, taille = 36.dp, couleur = c.accentVif)
        }
        Spacer(Modifier.height(18.dp))
        Text(titre, style = MaterialTheme.typography.titleLarge, color = c.texte, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(texte, style = MaterialTheme.typography.bodyMedium, color = c.texte2, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
