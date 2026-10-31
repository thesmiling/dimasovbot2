package ru.aurora.twin

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

/**
 * Aurora Soft Mirror v0.3 — purposely non-photorealistic.
 * The original image is fully STATIC. Only reflected ambient illumination, subtle
 * threads and tiny highlights are animated. No breathing/face motions or scanline.
 * Rendered natively using Compose Canvas; no new dependencies or permissions.
 */
@Composable
private fun softTime(enabled: Boolean, duration: Int = 26000): Float {
    val context = LocalContext.current
    val allowed = motionAllowed(context, enabled)
    if (!allowed) return 0f
    val transition = rememberInfiniteTransition(label = "soft-mirror")
    val time by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "mirror-phase")
    return time
}

@Composable
fun AuroraLivingBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    intensity: Float = 1f
) {
    val t = softTime(enabled, 29000)
    val amp = intensity.coerceIn(0f, 1f)
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val center1 = Offset(w * (.76f + .045f * sin(t)), h * (.13f + .045f * cos(t)))
        val center2 = Offset(w * (.05f + .035f * cos(t * .72f)), h * .74f)
        val radius = max(w, h) * .45f
        drawCircle(brush = Brush.radialGradient(
            listOf(Aura.violet.copy(alpha = .095f * amp),
                   Aura.cyan.copy(alpha = .019f * amp), Color.Transparent),
            center = center1, radius = radius), radius = radius, center = center1)
        drawCircle(brush = Brush.radialGradient(
            listOf(Aura.mint.copy(alpha = .04f * amp), Color.Transparent),
            center = center2, radius = radius * .82f),
            radius = radius * .82f, center = center2)
        // Tiny boundary highlights only, not a field of dots over the forms.
        for (i in 0 until 9) {
            val fx = ((i * 41 + 11) % 97) / 97f
            val fy = ((i * 31 + 19) % 89) / 89f
            if (fx in .18f.. .82f && fy in .22f.. .74f) continue
            val pos = Offset(w * fx + sin(t + i) * 5.dp.toPx(),
                             h * fy + cos(t * .67f + i) * 4.dp.toPx())
            val glow = .5f + .5f * sin(t * 1.2f + i)
            drawCircle(
                color = (if (i % 2 == 0) Aura.cyan else Aura.violet)
                    .copy(alpha = (.09f + .11f * glow) * amp),
                radius = (1f + .45f * glow).dp.toPx(), center = pos)
        }
    }
}

/** A soft curved thread that connects abstract identities; no humanoid animation. */
private fun connectingPath(width: Float, height: Float, t: Float, index: Int): Path {
    val phase = t * .72f + index * 1.7f
    val path = Path()
    val fromX = width * .27f
    val toX = width * .73f
    val base = height * (.65f + index * .047f)
    path.moveTo(fromX, base - 10f)
    path.cubicTo(
        width * .40f, height * (.64f + index * .032f) + sin(phase) * height * .035f,
        width * .59f, height * (.60f + index * .032f) + cos(phase) * height * .038f,
        toX, base - 10f
    )
    return path
}

@Composable
fun AuroraHeroAlive(
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    animate: Boolean = true,
    active: Boolean = false,
    intensity: Float = 1f
) {
    val context = LocalContext.current
    val allowed = motionAllowed(context, animate)
    val t = softTime(animate, 22000)
    val amp = intensity.coerceIn(.20f, 1f)
    var touchPoint by remember { mutableStateOf(Offset.Zero) }
    var touchToken by remember { mutableIntStateOf(0) }
    val halo = remember { Animatable(1f) }
    LaunchedEffect(touchToken) {
        if (touchToken > 0 && allowed) {
            halo.snapTo(0f)
            halo.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }
    }
    val radius = RoundedCornerShape(25.dp)
    Box(modifier.fillMaxWidth().height(height).clip(radius)
        .background(Brush.verticalGradient(listOf(
            Color(0xFF10182F), Color(0xFF101B37), Aura.deep)))) {
        // Illumination BEHIND the static illustrative silhouettes.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val r = w * .64f
            val shift = if (allowed) sin(t) * w * .018f else 0f
            val a = Offset(w * .36f + shift, h * .46f)
            val b = Offset(w * .65f - shift, h * .48f)
            drawCircle(Brush.radialGradient(
                listOf(Aura.cyan.copy(alpha = .17f * amp), Color.Transparent),
                center=a, radius=r), center=a, radius=r)
            drawCircle(Brush.radialGradient(
                listOf(Aura.violet.copy(alpha = .20f * amp), Color.Transparent),
                center=b, radius=r), center=b, radius=r)
        }
        // This image never moves, distorts, or mimics a living human.
        Image(
            painter=painterResource(R.drawable.twin_hero),
            contentDescription="Абстрактный символ связи тебя и цифрового двойника",
            modifier=Modifier.fillMaxSize().padding(4.dp),
            contentScale=ContentScale.Fit
        )
        Canvas(Modifier.fillMaxSize().pointerInput(allowed) {
            detectTapGestures { point -> touchPoint = point; touchToken++ }
        }) {
            val w = size.width; val h = size.height
            if (w < 1f || h < 1f) return@Canvas
            // Two hand-drawn, flowing threads, with soft bloom under the fine stroke.
            for (j in 0..1) {
                val curve = connectingPath(w, h, t, j)
                val c = if (j == 0) Aura.mint else Aura.violet
                drawPath(curve, c.copy(alpha=.050f*amp), style=Stroke(width=13.dp.toPx()))
                drawPath(curve, c.copy(alpha=(if(active) .37f else .25f)*amp),
                    style=Stroke(width=1.3.dp.toPx(), cap=StrokeCap.Round))
            }
            // A few motes follow the connecting threads, not the silhouette perimeter.
            for (i in 0 until 7) {
                val u = ((i/7f) + t/(2f*PI.toFloat()) * (if(i%2==0) .27f else -.19f))
                    .let { it - floor(it) }
                val x = w*(.27f + .46f*u)
                val pathPhase = t * .72f + (i % 2) * 1.7f
                val y = h*(.64f+(i%2)*.047f) + sin(u*PI.toFloat()*1.6f+pathPhase) * h*.025f
                val alpha = (sin(PI.toFloat()*u).coerceAtLeast(0f)) * .48f * amp
                val color = if(i%2==0) Aura.mint else Aura.violet
                drawCircle(color.copy(alpha=alpha*.20f), radius=7.dp.toPx(), center=Offset(x,y))
                drawCircle(color.copy(alpha=alpha), radius=1.65.dp.toPx(), center=Offset(x,y))
            }
            val pulse = if (allowed) .7f + .3f * sin(t*1.45f) else .8f
            val center = Offset(w*.5f, h*.67f)
            drawCircle(
                brush = Brush.radialGradient(listOf(
                    Aura.mint.copy(alpha=(if(active) .22f else .13f)*amp*pulse),
                    Aura.violet.copy(alpha=.055f*amp), Color.Transparent),
                    center=center, radius=w*.17f),
                radius=w*.17f, center=center)
            if (allowed && touchToken>0 && halo.value<1f) {
                val k=halo.value
                val dist=(15f+54f*k).dp.toPx()
                drawCircle(color=Aura.cyan.copy(alpha=(1f-k)*.25f),
                    center=touchPoint, radius=dist,
                    style=Stroke(width=(1.5f-k).dp.toPx()))
            }
            // A subtle internal glass border to make it feel like a considered object.
            drawRoundRect(
                brush=Brush.horizontalGradient(listOf(
                    Aura.cyan.copy(alpha=.12f), Color.Transparent,
                    Aura.violet.copy(alpha=.12f))),
                cornerRadius=androidx.compose.ui.geometry.CornerRadius(25.dp.toPx()),
                style=Stroke(width=.9.dp.toPx()))
        }
    }
}

@Composable
fun AuraStatusBeacon(active: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val t = softTime(enabled && active, 3400)
    Canvas(modifier.size(30.dp)) {
        val center = center
        val color = if(active) Aura.green else Aura.orange
        drawCircle(color.copy(alpha=.09f), radius=13.dp.toPx(), center=center)
        drawCircle(color.copy(alpha=.88f), radius=3.3.dp.toPx(), center=center)
        if (active && enabled) {
            val radius = (6f + 6.5f*(.5f+.5f*sin(t))).dp.toPx()
            drawCircle(color.copy(alpha=.20f), radius=radius,
                center=center, style=Stroke(width=1.2.dp.toPx()))
        }
    }
}

@Composable
fun AuroraActionButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context=LocalContext.current
    val source=remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue=if(pressed && motionAllowed(context,true)) .98f else 1f,
        animationSpec=spring(stiffness=Spring.StiffnessMedium),
        label="button-soft-press"
    )
    Button(
        onClick=onClick, interactionSource=source, enabled=enabled,
        modifier=modifier.fillMaxWidth().heightIn(min=54.dp)
            .graphicsLayer { scaleX=scale; scaleY=scale }
            .shadow(if(pressed) 1.dp else 4.dp, RoundedCornerShape(16.dp),
                ambientColor=Aura.mint.copy(alpha=.17f)),
        shape=RoundedCornerShape(16.dp),
        colors=ButtonDefaults.buttonColors(
            containerColor=Aura.mint, contentColor=Aura.deep,
            disabledContainerColor=Aura.raised, disabledContentColor=Aura.muted)
    ) {
        Text(text, fontWeight=FontWeight.SemiBold, fontSize=15.sp)
    }
}