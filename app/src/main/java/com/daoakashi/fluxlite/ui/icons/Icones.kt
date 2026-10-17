package com.daoakashi.fluxlite.ui.icons

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daoakashi.fluxlite.data.Affichage
import com.daoakashi.fluxlite.data.Cloche
import com.daoakashi.fluxlite.data.ModeAlertes
import com.daoakashi.fluxlite.ui.theme.Flux
import kotlin.math.PI
import kotlin.math.sin

/** Icônes au trait de FluxLite, dessinées sur une grille de 24 (même style que les icônes de Flux sur PC). */
enum class Ico {
    CAMERAS, JOURNAL, COMPTE, RETOUR, PLUS, POINTS, ROTATION, FERMER, CADENAS, CLE, SORTIE, QR, SERVEUR, OEIL, OEIL_BARRE,
    POUBELLE, TELECHARGER, INFO, CHEVRON, RECHARGER, PLEIN_ECRAN, VERIF, ATTENTION, TELEPHONE, BOUCLIER, CRAYON, HORLOGE,
    PERSONNE, PHOTO, CLOCHE_SIMPLE, CLOCHE_BARREE, PILOTAGE, MAIL, MESSAGE, GLOBE,
}

@Composable
fun Icone(ico: Ico, modifier: Modifier = Modifier, taille: Dp = 22.dp, couleur: Color = Flux.c.texte, epaisseur: Float = 1.8f) {
    Canvas(modifier.size(taille)) {
        val s = size.minDimension / 24f
        translate((size.width - 24 * s) / 2, (size.height - 24 * s) / 2) {
            scale(s, s, pivot = Offset.Zero) { dessiner(ico, couleur, epaisseur) }
        }
    }
}

private fun DrawScope.trait(p: Path, c: Color, e: Float) =
    drawPath(p, c, style = Stroke(width = e, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun chemin(bloc: Path.() -> Unit) = Path().apply(bloc)

private fun DrawScope.dessiner(ico: Ico, c: Color, e: Float) {
    when (ico) {
        Ico.CAMERAS -> {
            drawRoundRect(c, Offset(3f, 6.5f), Size(13f, 11f), CornerRadius(2.5f), style = Stroke(e))
            trait(chemin { moveTo(16f, 10.5f); lineTo(21f, 7.5f); lineTo(21f, 16.5f); lineTo(16f, 13.5f) }, c, e)
        }
        Ico.JOURNAL -> {
            drawRoundRect(c, Offset(4.5f, 3.5f), Size(15f, 17f), CornerRadius(2.5f), style = Stroke(e))
            for (y in listOf(8.5f, 12f, 15.5f)) trait(chemin { moveTo(8f, y); lineTo(16f, y) }, c, e)
        }
        Ico.COMPTE, Ico.PERSONNE -> {
            drawCircle(c, 3.5f, Offset(12f, 7f), style = Stroke(e))
            trait(chemin { moveTo(4.5f, 20.5f); cubicTo(4.5f, 15.5f, 8f, 13.2f, 12f, 13.2f); cubicTo(16f, 13.2f, 19.5f, 15.5f, 19.5f, 20.5f) }, c, e)
        }
        Ico.RETOUR -> trait(chemin { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) }, c, e + 0.3f)
        Ico.CHEVRON -> trait(chemin { moveTo(9.5f, 5.5f); lineTo(16f, 12f); lineTo(9.5f, 18.5f) }, c, e)
        Ico.PLUS -> trait(chemin { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }, c, e)
        Ico.FERMER -> trait(chemin { moveTo(6.5f, 6.5f); lineTo(17.5f, 17.5f); moveTo(17.5f, 6.5f); lineTo(6.5f, 17.5f) }, c, e)
        Ico.POINTS -> for (y in listOf(5.5f, 12f, 18.5f)) drawCircle(c, 1.9f, Offset(12f, y))
        Ico.ROTATION -> {
            drawRoundRect(c, Offset(7f, 3f), Size(10f, 18f), CornerRadius(2.4f), style = Stroke(e))
            trait(chemin { moveTo(2.5f, 10f); cubicTo(2.5f, 6f, 4f, 4f, 6f, 3.5f); moveTo(4.5f, 2f); lineTo(6f, 3.5f); lineTo(4.5f, 5f) }, c, e)
            trait(chemin { moveTo(21.5f, 14f); cubicTo(21.5f, 18f, 20f, 20f, 18f, 20.5f); moveTo(19.5f, 19f); lineTo(18f, 20.5f); lineTo(19.5f, 22f) }, c, e)
        }
        Ico.CADENAS -> {
            drawRoundRect(c, Offset(5f, 10.5f), Size(14f, 10f), CornerRadius(2.5f), style = Stroke(e))
            trait(chemin { moveTo(8f, 10.5f); lineTo(8f, 7.5f); cubicTo(8f, 2.5f, 16f, 2.5f, 16f, 7.5f); lineTo(16f, 10.5f) }, c, e)
            drawCircle(c, 1.4f, Offset(12f, 15.5f))
        }
        Ico.CLE -> {
            drawCircle(c, 4f, Offset(8f, 12f), style = Stroke(e))
            trait(chemin { moveTo(12f, 12f); lineTo(21f, 12f); moveTo(18f, 12f); lineTo(18f, 15f); moveTo(21f, 12f); lineTo(21f, 14.5f) }, c, e)
        }
        Ico.SORTIE -> {
            trait(chemin { moveTo(10f, 4f); lineTo(6f, 4f); quadraticTo(4f, 4f, 4f, 6f); lineTo(4f, 18f); quadraticTo(4f, 20f, 6f, 20f); lineTo(10f, 20f) }, c, e)
            trait(chemin { moveTo(10f, 12f); lineTo(20f, 12f); moveTo(16.5f, 8.5f); lineTo(20f, 12f); lineTo(16.5f, 15.5f) }, c, e)
        }
        Ico.QR -> {
            for ((x, y) in listOf(3.5f to 3.5f, 14f to 3.5f, 3.5f to 14f))
                drawRoundRect(c, Offset(x, y), Size(6.5f, 6.5f), CornerRadius(1.5f), style = Stroke(e))
            for ((x, y) in listOf(15f to 15f, 18.5f to 15f, 15f to 18.5f, 18.5f to 18.5f))
                drawRect(c, Offset(x, y), Size(2f, 2f))
        }
        Ico.SERVEUR -> {
            drawRoundRect(c, Offset(4f, 4f), Size(16f, 7f), CornerRadius(2f), style = Stroke(e))
            drawRoundRect(c, Offset(4f, 13f), Size(16f, 7f), CornerRadius(2f), style = Stroke(e))
            drawCircle(c, 1.1f, Offset(7.5f, 7.5f)); drawCircle(c, 1.1f, Offset(7.5f, 16.5f))
        }
        Ico.OEIL, Ico.OEIL_BARRE -> {
            trait(chemin { moveTo(2.5f, 12f); cubicTo(5f, 6.5f, 19f, 6.5f, 21.5f, 12f); cubicTo(19f, 17.5f, 5f, 17.5f, 2.5f, 12f) }, c, e)
            drawCircle(c, 2.8f, Offset(12f, 12f), style = Stroke(e))
            if (ico == Ico.OEIL_BARRE) trait(chemin { moveTo(4f, 20f); lineTo(20f, 4f) }, c, e)
        }
        Ico.POUBELLE -> {
            trait(chemin { moveTo(4f, 6.5f); lineTo(20f, 6.5f); moveTo(9f, 6.5f); lineTo(9.5f, 3.5f); lineTo(14.5f, 3.5f); lineTo(15f, 6.5f) }, c, e)
            trait(chemin { moveTo(6f, 6.5f); lineTo(7f, 20.5f); lineTo(17f, 20.5f); lineTo(18f, 6.5f); moveTo(10f, 10.5f); lineTo(10f, 16.5f); moveTo(14f, 10.5f); lineTo(14f, 16.5f) }, c, e)
        }
        Ico.TELECHARGER -> trait(chemin { moveTo(12f, 3.5f); lineTo(12f, 15f); moveTo(7.5f, 10.5f); lineTo(12f, 15f); lineTo(16.5f, 10.5f); moveTo(4.5f, 19.5f); lineTo(19.5f, 19.5f) }, c, e)
        Ico.INFO -> {
            drawCircle(c, 8.5f, Offset(12f, 12f), style = Stroke(e))
            trait(chemin { moveTo(12f, 11f); lineTo(12f, 16.5f) }, c, e); drawCircle(c, 1.2f, Offset(12f, 7.8f))
        }
        Ico.RECHARGER -> {
            trait(chemin { addArc(Rect(4.5f, 4.5f, 19.5f, 19.5f), -60f, 300f) }, c, e)
            trait(chemin { moveTo(15.8f, 2.8f); lineTo(16f, 6.2f); lineTo(19.4f, 6f) }, c, e)
        }
        Ico.PLEIN_ECRAN -> for ((x, y, dx, dy) in listOf(Quad(4f, 4f, 1f, 1f), Quad(20f, 4f, -1f, 1f), Quad(4f, 20f, 1f, -1f), Quad(20f, 20f, -1f, -1f)))
            trait(chemin { moveTo(x + dx * 5.5f, y); lineTo(x, y); lineTo(x, y + dy * 5.5f) }, c, e)
        Ico.VERIF -> trait(chemin { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19.5f, 7f) }, c, e + 0.4f)
        Ico.ATTENTION -> {
            trait(chemin { moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close() }, c, e)
            trait(chemin { moveTo(12f, 9.5f); lineTo(12f, 14f) }, c, e); drawCircle(c, 1.2f, Offset(12f, 17f))
        }
        Ico.TELEPHONE -> {
            drawRoundRect(c, Offset(6.5f, 2.5f), Size(11f, 19f), CornerRadius(2.6f), style = Stroke(e))
            trait(chemin { moveTo(10.5f, 18.3f); lineTo(13.5f, 18.3f) }, c, e)
        }
        Ico.BOUCLIER -> {
            trait(chemin { moveTo(12f, 3f); lineTo(19.5f, 6f); lineTo(19.5f, 11.5f); cubicTo(19.5f, 16f, 16f, 19.5f, 12f, 21f); cubicTo(8f, 19.5f, 4.5f, 16f, 4.5f, 11.5f); lineTo(4.5f, 6f); close() }, c, e)
            trait(chemin { moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f) }, c, e)
        }
        Ico.CRAYON -> trait(chemin { moveTo(4f, 20f); lineTo(5f, 15.5f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8.5f, 19f); close(); moveTo(13f, 7.5f); lineTo(16.5f, 11f) }, c, e)
        Ico.HORLOGE -> {
            drawCircle(c, 8.5f, Offset(12f, 12f), style = Stroke(e))
            trait(chemin { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f) }, c, e)
        }
        Ico.PHOTO -> {
            drawRoundRect(c, Offset(3f, 5f), Size(18f, 14f), CornerRadius(2.5f), style = Stroke(e))
            trait(chemin { moveTo(3.5f, 16.5f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(15.5f, 12.5f); lineTo(20.5f, 17f) }, c, e)
            drawCircle(c, 1.6f, Offset(16f, 9f))
        }
        Ico.PILOTAGE -> {
            // deux interrupteurs : un allumé, un éteint
            drawRoundRect(c, Offset(3f, 4.5f), Size(18f, 6.5f), CornerRadius(3.25f), style = Stroke(e))
            drawCircle(c, 2f, Offset(16.8f, 7.75f))
            drawRoundRect(c, Offset(3f, 13f), Size(18f, 6.5f), CornerRadius(3.25f), style = Stroke(e))
            drawCircle(c, 2f, Offset(7.2f, 16.25f), style = Stroke(e))
        }
        Ico.MAIL -> {
            drawRoundRect(c, Offset(3f, 5.5f), Size(18f, 13f), CornerRadius(2.5f), style = Stroke(e))
            trait(chemin { moveTo(3.8f, 7f); lineTo(12f, 13f); lineTo(20.2f, 7f) }, c, e)
        }
        Ico.MESSAGE -> trait(chemin {
            moveTo(5f, 4.5f); lineTo(19f, 4.5f); quadraticTo(21f, 4.5f, 21f, 6.5f); lineTo(21f, 14.5f)
            quadraticTo(21f, 16.5f, 19f, 16.5f); lineTo(10f, 16.5f); lineTo(5.5f, 20.5f); lineTo(5.5f, 16.5f)
            lineTo(5f, 16.5f); quadraticTo(3f, 16.5f, 3f, 14.5f); lineTo(3f, 6.5f); quadraticTo(3f, 4.5f, 5f, 4.5f); close()
        }, c, e)
        Ico.GLOBE -> {
            drawCircle(c, 8.5f, Offset(12f, 12f), style = Stroke(e))
            drawOval(c, Offset(8.3f, 3.5f), Size(7.4f, 17f), style = Stroke(e))
            trait(chemin { moveTo(3.5f, 12f); lineTo(20.5f, 12f) }, c, e)
        }
        Ico.CLOCHE_SIMPLE, Ico.CLOCHE_BARREE -> {
            dessinerCloche(c, e)
            if (ico == Ico.CLOCHE_BARREE) trait(chemin { moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f) }, c, e)
        }
    }
}

private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)

/** Cloche FluxLite : corps arrondi et battant en forme de vague (rappel du logo). */
private fun DrawScope.dessinerCloche(c: Color, e: Float) {
    trait(chemin {
        moveTo(5f, 17f)
        cubicTo(6.4f, 15.6f, 6.6f, 14f, 6.6f, 11.2f)
        cubicTo(6.6f, 7.4f, 8.8f, 4.6f, 12f, 4.6f)
        cubicTo(15.2f, 4.6f, 17.4f, 7.4f, 17.4f, 11.2f)
        cubicTo(17.4f, 14f, 17.6f, 15.6f, 19f, 17f)
        close()
    }, c, e)
    trait(chemin { moveTo(12f, 2.6f); lineTo(12f, 4.6f) }, c, e)
    // battant-vague
    trait(chemin { moveTo(9f, 20f); cubicTo(10f, 19f, 11f, 19f, 12f, 20f); cubicTo(13f, 21f, 14f, 21f, 15f, 20f) }, c, e)
}

/** Cloche de la barre du haut : son dessin change avec le réglage (sonore, silencieuse, coupée, en pause). */
@Composable
fun IconeCloche(cloche: Cloche, modifier: Modifier = Modifier, taille: Dp = 24.dp, couleur: Color = Flux.c.texte) {
    val accent = Flux.c.accentVif
    val attention = Flux.c.attention
    val danger = Flux.c.danger
    val ondes = cloche.mode == ModeAlertes.ACTIVES && !cloche.enPause
    val anim = rememberInfiniteTransition(label = "cloche")
    val t by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "t")
    Canvas(modifier.size(taille)) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            dessinerCloche(couleur, 1.8f)
            when {
                cloche.mode == ModeAlertes.COUPEES -> trait(chemin { moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f) }, danger, 2f)
                cloche.enPause -> {
                    drawCircle(attention, 5f, Offset(18.5f, 5.5f))
                    trait(chemin { moveTo(18.5f, 3.2f); lineTo(18.5f, 5.7f); lineTo(20.2f, 6.7f) }, Color.White, 1.4f)
                }
                cloche.mode == ModeAlertes.SILENCIEUSES -> {
                    drawCircle(accent, 4.6f, Offset(18.8f, 5.2f))
                    trait(chemin { moveTo(16.8f, 4.2f); lineTo(20.6f, 4.2f); lineTo(16.8f, 6.8f); lineTo(20.6f, 6.8f) }, Color.White, 1.2f)
                }
                ondes -> {
                    // petites ondes qui s'éloignent : les alertes sont actives
                    val a = 1f - t
                    trait(chemin { addArc(Rect(1f - t * 2, 5f - t * 2, 9f, 15f + t * 2), 150f, 60f) }, accent.copy(alpha = a), 1.5f)
                    trait(chemin { addArc(Rect(15f, 5f - t * 2, 23f + t * 2, 15f + t * 2), -30f, 60f) }, accent.copy(alpha = a), 1.5f)
                }
            }
        }
    }
}

/** Bouton d'affichage : l'icône montre la disposition choisie. */
@Composable
fun IconeAffichage(a: Affichage, modifier: Modifier = Modifier, taille: Dp = 24.dp, couleur: Color = Flux.c.texte) {
    Canvas(modifier.size(taille)) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            val st = Stroke(1.7f)
            when (a) {
                Affichage.FIL -> {
                    drawRoundRect(couleur, Offset(3.5f, 3.5f), Size(17f, 8f), CornerRadius(2f), style = st)
                    drawRoundRect(couleur, Offset(3.5f, 14f), Size(17f, 6.5f), CornerRadius(2f), style = st)
                }
                Affichage.GRILLE -> for (x in listOf(3.5f, 13f)) for (y in listOf(3.5f, 13f))
                    drawRoundRect(couleur, Offset(x, y), Size(7.5f, 7.5f), CornerRadius(1.8f), style = st)
                Affichage.MOSAIQUE -> for (x in listOf(3f, 9.5f, 16f)) for (y in listOf(3f, 9.5f, 16f))
                    drawRoundRect(couleur, Offset(x, y), Size(5f, 5f), CornerRadius(1.2f), style = Fill)
            }
        }
    }
}

/** Logo FluxLite : trois vagues dans un anneau, qui ondulent doucement quand `anime`. */
@Composable
fun LogoVagues(modifier: Modifier = Modifier, taille: Dp = 34.dp, anime: Boolean = true) {
    val c = Flux.c
    val anim = rememberInfiniteTransition(label = "logo")
    val phase by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Canvas(modifier.size(taille)) {
        val s = size.minDimension / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawRoundRect(Brush.linearGradient(listOf(c.accent, Color(0xFF1D4ED8)), Offset(0f, 0f), Offset(24f, 24f)),
                Offset.Zero, Size(24f, 24f), CornerRadius(7f))
            val couleurs = listOf(Color.White, Color(0xFFB9D3FF), c.personne)
            for ((i, y) in listOf(8.5f, 12.5f, 16.5f).withIndex()) {
                val p = Path()
                val d = if (anime) phase * 2 * PI.toFloat() + i * 0.9f else i * 0.9f
                var x = 5f
                p.moveTo(x, y + sin(d) * 1.4f)
                while (x < 19f) {
                    x += 0.5f
                    p.lineTo(x, y + sin(d + (x - 5f) / 14f * 2f * PI.toFloat()) * 1.4f)
                }
                drawPath(p, couleurs[i], style = Stroke(1.9f, cap = StrokeCap.Round))
            }
        }
    }
}
