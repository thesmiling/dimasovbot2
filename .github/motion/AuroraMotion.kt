package ru.aurora.twin

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.math.*

/**
 * Aurora Motion 0.2.
 * All visuals are native Compose Canvas; no WebView or looping video.
 * Motion is decorative and never conveys critical state alone.
 * When animations are disabled, every composition has a deterministic static frame.
 */
@Composable
private fun motionTime(enabled: Boolean, period: Int = 16000): Float {
    val context = LocalContext.current
    val allowed = remember(context, enabled) { motionAllowed(context, enabled) }
    return if (allowed) {
        val cycle = rememberInfiniteTransition(label = "aurora-live-cycle")
        val value by cycle.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(period, easing = LinearEasing), repeatMode = RepeatMode.Restart
            ), label = "motion-phase"
        )
        value
    } else 0f
}

@Composable
fun AuroraLivingBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    intensity: Float = 1f
) {
    val t = motionTime(enabled, 24000) * 2f * PI.toFloat()
    val strength = intensity.coerceIn(0f, 1f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w < 1f || h < 1f) return@Canvas
        // Moving, extremely soft light field behind text.
        val focus = Offset(w * (.65f + .09f * sin(t * .65f)), h * (.24f + .08f * cos(t * .43f)))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Aura.violet.copy(alpha = .12f * strength), Aura.cyan.copy(alpha = .025f * strength), Color.Transparent),
                center = focus, radius = max(w, h) * .55f
            ),
            center = focus, radius = max(w, h) * .55f
        )
        // Sparse drifting particles, intentionally much less bright than readable content.
        for (i in 0 until 29) {
            val bx = ((i * 47 + 11) % 97) / 97f
            val by = ((i * 37 + 17) % 89) / 89f
            val x = bx * w + sin(t * .6f + i * .71f) * 13.dp.toPx()
            val y = by * h + cos(t * .47f + i * .91f) * 11.dp.toPx()
            val shimmer = .65f + .35f * sin(t * 1.8f + i)
            val color = if (i % 4 == 0) Aura.violet else Aura.mint
            drawCircle(color.copy(alpha = (.12f + .13f * shimmer) * strength),
                radius = (1.1f + (i % 3) * .32f).dp.toPx(), center = Offset(x,y))
            if (i % 7 == 0) {
                val x2 = (x + sin(t + i) * 24.dp.toPx()).coerceIn(0f,w)
                val y2 = (y + 26.dp.toPx()).coerceIn(0f,h)
                drawLine(Aura.cyan.copy(alpha = .075f * strength),
                    Offset(x,y), Offset(x2,y2), strokeWidth = .65.dp.toPx())
            }
        }
    }
}

/** Interactive living identity: moving aurora ribbons, breath, scanning seam, tap ripple. */
@Composable
fun AuroraHeroAlive(
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    animate: Boolean = true,
    active: Boolean = false,
    intensity: Float = 1f
) {
    val context = LocalContext.current
    val effectiveMotion = remember(context, animate) { motionAllowed(context, animate) }
    val t = motionTime(animate, 20000) * 2f * PI.toFloat()
    val strength = intensity.coerceIn(.25f, 1f)
    val shift = with(LocalDensity.current) { (4f * sin(t * .8f)).dp.toPx() }
    var touch by remember { mutableStateOf(Offset.Zero) }
    var touchId by remember { mutableIntStateOf(0) }
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(touchId) {
        if (touchId > 0 && effectiveMotion) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
        }
    }
    Box(modifier.fillMaxWidth().height(height)
        .clip(RoundedCornerShape(25.dp))
        .background(Brush.linearGradient(listOf(Aura.deep, Color(0xFF172344), Aura.deep)))) {
        AuroraLivingBackdrop(Modifier.fillMaxSize(), enabled = animate, intensity = strength)
        // The original twin artwork remains the core of the identity, but it now 'breathes'.
        Image(
            painter = painterResource(R.drawable.twin_hero),
            contentDescription = "Два связанных силуэта цифрового двойника",
            modifier = Modifier.fillMaxSize().padding(3.dp).graphicsLayer {
                val breathe = if (effectiveMotion) sin(t * .65f) else 0f
                scaleX = 1.012f + .009f * breathe
                scaleY = 1.012f + .009f * breathe
                translationX = if (effectiveMotion) shift else 0f
                alpha = .96f + .035f * ((breathe + 1f) / 2f)
            },
            contentScale = ContentScale.Fit
        )
        Canvas(
            Modifier.fillMaxSize().pointerInput(effectiveMotion) {
                detectTapGestures {
                    touch = it
                    touchId += 1
                }
            }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            // Three translucent moving aurora streams.
            for (layer in 0 until 3) {
                val path = Path()
                val base = h * (.78f + layer * .06f)
                for (step in 0..42) {
                    val f = step / 42f
                    val x = -w*.06f + w*1.12f*f
                    val y = base + sin(f * 2f*PI.toFloat() * 1.25f + t * (.55f+layer*.12f) + layer) *
                        h * (.042f + layer*.015f)
                    if (step == 0) path.moveTo(x,y) else path.lineTo(x,y)
                }
                val c = if (layer == 1) Aura.violet else Aura.mint
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(listOf(
                        Color.Transparent, c.copy(alpha = (.12f+layer*.06f)*strength),
                        c.copy(alpha=.045f*strength), Color.Transparent
                    ), start = Offset(0f,base), end=Offset(w,base)),
                    style = Stroke(width=(22f + layer*9f).dp.toPx(), cap=StrokeCap.Round)
                )
            }
            // Animated luminous vertical relationship between both silhouettes.
            val seamPulse = .53f + .47f * (sin(t * 1.6f).let { (it+1f)*.5f })
            val lineStart = Offset(w * .5f, h * .13f)
            val lineEnd = Offset(w * .5f, h * .92f)
            drawLine(Aura.cyan.copy(alpha=.13f*strength*seamPulse),
                lineStart,lineEnd,strokeWidth=13.dp.toPx())
            drawLine(
                Brush.verticalGradient(listOf(Color.Transparent, Aura.mint.copy(alpha=(if(active) .63f else .48f)*strength*seamPulse), Aura.violet.copy(alpha=.46f*strength*seamPulse),Color.Transparent)),
                lineStart,lineEnd,strokeWidth=1.8.dp.toPx()
            )
            // Slow-moving motes around the silhouette contour; not over text.
            for (i in 0 until 17) {
                val a = (i/17f)*2f*PI.toFloat()+t*(if (i%2==0) .24f else -.17f)
                val x = w*.5f + cos(a)*w*(.32f+(i%3)*.015f)
                val y = h*.54f + sin(a)*h*(.35f+(i%2)*.02f)
                val shimmer = (.5f+.5f*sin(t*1.8f+i*.87f))
                drawCircle(if(i%3==0) Aura.violet.copy(alpha=.2f*strength) else Aura.cyan.copy(alpha=.28f*strength),
                    radius=(.85f+shimmer*1.1f).dp.toPx(), center=Offset(x,y))
            }
            if (touchId > 0 && effectiveMotion && ripple.value < 1f) {
                val progress = ripple.value
                val radius = (20f + 110f*progress).dp.toPx()
                drawCircle(
                    color = Aura.mint.copy(alpha=(1f-progress)*.45f*strength),
                    radius = radius,
                    center = touch,
                    style = Stroke(width=(2.5f*(1f-progress)+.3f).dp.toPx())
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(
                        Aura.cyan.copy(alpha = .14f*(1f-progress)*strength), Color.Transparent
                    ), center=touch, radius=radius),
                    radius=radius, center=touch
                )
            }
        }
    }
}

@Composable
fun AuraStatusBeacon(active: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val t = motionTime(enabled && active, 2700)
    Canvas(modifier.size(34.dp)) {
        val c = center
        val color = if (active) Aura.green else Aura.orange
        drawCircle(color.copy(alpha=.08f), radius=14.dp.toPx(), center=c)
        drawCircle(color.copy(alpha=.92f), radius=4.dp.toPx(), center=c)
        if (active && enabled) {
            val radius = (7f + 9f*t).dp.toPx()
            drawCircle(color.copy(alpha=.45f*(1f-t)), radius=radius,
                center=c, style=Stroke(width=1.5.dp.toPx()))
        }
    }
}

@Composable
fun AuroraActionButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .974f else 1f,
        animationSpec=spring(stiffness=Spring.StiffnessMedium), label="press-response")
    Button(
        onClick=onClick, enabled=enabled, interactionSource=source,
        modifier=modifier.fillMaxWidth().heightIn(min=54.dp).graphicsLayer {
            scaleX=scale
            scaleY=scale
        }.shadow(if(pressed) 10.dp else 4.dp, RoundedCornerShape(16.dp), ambientColor=Aura.mint.copy(alpha=.45f)),
        shape=RoundedCornerShape(16.dp),
        colors=ButtonDefaults.buttonColors(
            containerColor=Aura.mint, contentColor=Aura.deep,
            disabledContainerColor=Aura.raised, disabledContentColor=Aura.muted
        )
    ) {
        Text(text, fontWeight=FontWeight.SemiBold, fontSize=15.sp)
    }
}