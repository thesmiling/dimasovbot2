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
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
    val t = softTime(enabled, 14500)
    val amp = intensity.coerceIn(0f, 1f)
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        // Independent moving aurora light: larger travel and brighter than v0.3.
        // Intentionally stays below foreground content and uses low contrast.
        val left = Offset(w * (.18f + .16f * sin(t)), h * (.26f + .09f * cos(t)))
        val right = Offset(w * (.75f + .17f * sin(t + 1.9f)), h * (.71f + .12f * cos(t + 1.1f)))
        val r = max(w, h) * .56f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Aura.cyan.copy(alpha=.20f*amp), Aura.mint.copy(alpha=.06f*amp),
                    Color.Transparent), center=left, radius=r),
            center=left, radius=r
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Aura.violet.copy(alpha=.20f*amp),
                    Aura.violet.copy(alpha=.04f*amp), Color.Transparent),
                center=right, radius=r),
            center=right, radius=r
        )
        // Only a few guiding lights: no noisy particle rain across the UI.
        for (i in 0 until 9) {
            val fx = ((i * 43 + 11) % 97) / 97f
            val fy = ((i * 31 + 19) % 89) / 89f
            val x = w * fx + sin(t + i*.6f) * 13.dp.toPx()
            val y = h * fy + cos(t*.7f + i) * 8.dp.toPx()
            val glow = .55f + .45f * sin(t*1.5f + i)
            drawCircle(
                color=(if (i%2==0) Aura.cyan else Aura.violet)
                    .copy(alpha=(.12f+.13f*glow)*amp),
                radius=(1.1f + .45f*glow).dp.toPx(),
                center=Offset(x,y)
            )
        }
    }
}

/**
 * Loose abstract silhouettes used only as light masks.
 * This moves light within the heads but never moves a face or simulates a human.
 * Both paths approximate the existing twin_hero illustration.
 */
private fun glassProfile(w: Float, h: Float, right: Boolean): Path {
    fun x(v: Float): Float = w * (if(right) 1f-v else v)
    return Path().apply {
        moveTo(x(.50f), h*.085f)
        cubicTo(x(.36f), h*.085f, x(.24f),h*.11f, x(.21f),h*.29f)
        cubicTo(x(.20f), h*.41f, x(.17f),h*.45f, x(.125f),h*.52f)
        cubicTo(x(.09f), h*.57f, x(.165f),h*.59f, x(.168f),h*.65f)
        cubicTo(x(.16f), h*.75f, x(.15f),h*.81f, x(.27f),h*.81f)
        cubicTo(x(.35f),h*.82f, x(.40f),h*.92f, x(.40f),h)
        lineTo(x(.50f),h)
        close()
    }
}

/**
 * This is the actual visible animation layer for v0.4.
 * All dynamic highlights draw ABOVE the opaque bitmap and INSIDE the silhouette.
 */
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
    val t = softTime(animate, 9200)
    val u = (t/(2f*PI.toFloat())).coerceIn(0f,1f)
    val amp = intensity.coerceIn(.33f,1f)
    var touchPoint by remember { mutableStateOf(Offset.Zero) }
    var touches by remember { mutableIntStateOf(0) }
    val ring = remember { Animatable(1f) }
    LaunchedEffect(touches) {
        if (touches > 0 && allowed) {
            ring.snapTo(0f)
            ring.animateTo(1f, animationSpec=tween(850, easing=FastOutSlowInEasing))
        }
    }
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(25.dp))
        .background(Aura.deep)) {
        // Static original art is preserved. The foreground Canvas below gives it life.
        Image(
            painter=painterResource(R.drawable.twin_hero),
            contentDescription="Абстрактный символ цифрового двойника",
            modifier=Modifier.fillMaxSize().padding(2.dp),
            contentScale=ContentScale.FillBounds
        )
        Canvas(Modifier.fillMaxSize().pointerInput(allowed) {
            detectTapGestures { point -> touchPoint=point; touches++ }
        }) {
            val w=size.width; val h=size.height
            if (w<1f||h<1f) return@Canvas
            // Every full loop the reflection travels through the TWO profiles.
            // The seam's point of light moves separately, so motion is easy to notice.
            val sweep = w*(-.23f + 1.46f*u)
            val visibility = (.38f + .62f*sin(PI.toFloat()*u)).coerceIn(0f,1f)
            for (right in listOf(false,true)) {
                clipPath(glassProfile(w,h,right)) {
                    // A clearly moving, broad glass reflection (not hidden behind the png).
                    val gloss = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.28f to Aura.cyan.copy(alpha=.09f*amp*visibility),
                            0.48f to Aura.white.copy(alpha=.27f*amp*visibility),
                            0.55f to Aura.mint.copy(alpha=.18f*amp*visibility),
                            0.79f to Aura.violet.copy(alpha=.08f*amp*visibility),
                            1.0f to Color.Transparent
                        ),
                        startX=sweep-w*.25f,endX=sweep+w*.25f
                    )
                    drawRect(brush=gloss)
                    // Two translucent floating light clouds per silhouette.
                    val cx=w*(if(right) .70f else .30f) +
                        sin(t+(if(right) 1.4f else 0f))*w*.065f
                    val cy=h*(.35f + .12f*cos(t*.82f+(if(right) .9f else 0f)))
                    val ctr=Offset(cx,cy)
                    val r=w*.28f
                    drawCircle(brush=Brush.radialGradient(
                        listOf(
                            (if(right) Aura.violet else Aura.cyan)
                                .copy(alpha=.28f*amp),
                            (if(right) Aura.cyan else Aura.mint)
                                .copy(alpha=.07f*amp), Color.Transparent
                        ),center=ctr,radius=r),center=ctr,radius=r)
                    // Fine animated fibers flow across the *interior*, not around the face.
                    for (fiber in 0 until 3) {
                        val curve=Path()
                        val y=h*(.22f + .17f*fiber) +
                            sin(t + fiber*1.2f + (if(right) .7f else 0f))*h*.052f
                        curve.moveTo(w*.17f,y)
                        curve.cubicTo(w*.35f,y-h*.045f,
                            w*.57f,y+h*.06f,
                            w*.84f,y-h*.02f)
                        val c=if (fiber==1) Aura.violet else Aura.mint
                        drawPath(curve,color=c.copy(alpha=.12f*amp),
                            style=Stroke(width=5.dp.toPx(),cap=StrokeCap.Round))
                        drawPath(curve,color=c.copy(alpha=.28f*amp),
                            style=Stroke(width=.85.dp.toPx(),cap=StrokeCap.Round))
                    }
                }
            }
            // Bright travel along the central connection, visible even on a dark screen.
            val beamStart=Offset(w*.5f,h*.12f)
            val beamEnd=Offset(w*.5f,h*.9f)
            val beamGlow=.16f + .10f*(.5f+.5f*cos(t*.7f))
            drawLine(Aura.mint.copy(alpha=beamGlow*amp),
                start=beamStart,end=beamEnd,strokeWidth=6.dp.toPx())
            drawLine(brush=Brush.verticalGradient(listOf(
                Color.Transparent,Aura.cyan.copy(alpha=.56f*amp),
                Aura.violet.copy(alpha=.40f*amp),Color.Transparent)),
                start=beamStart,end=beamEnd,strokeWidth=1.6.dp.toPx())
            val beamY=h*(.15f+.70f*(.5f-.5f*cos(t)))
            val bead=Offset(w*.5f,beamY)
            drawCircle(brush=Brush.radialGradient(
                listOf(Aura.mint.copy(alpha=.31f*amp),
                    Aura.cyan.copy(alpha=.10f*amp),Color.Transparent),
                center=bead,radius=31.dp.toPx()),
                radius=31.dp.toPx(),center=bead)
            drawCircle(Aura.white.copy(alpha=.72f*amp),
                radius=2.5.dp.toPx(),center=bead)
            // Two gently animated connectors sweep between the forms.
            for (j in 0..1) {
                val thread=Path()
                val y=h*(.70f+j*.07f)
                thread.moveTo(w*.25f,y)
                thread.cubicTo(
                    w*.38f,y-h*.08f + sin(t+j)*h*.033f,
                    w*.61f,y+h*.07f + cos(t+j)*h*.033f,
                    w*.75f,y
                )
                val col=if(j==0) Aura.cyan else Aura.violet
                drawPath(thread,col.copy(alpha=.10f*amp),
                    style=Stroke(width=12.dp.toPx(),cap=StrokeCap.Round))
                drawPath(thread,col.copy(alpha=.48f*amp),
                    style=Stroke(width=1.7.dp.toPx(),cap=StrokeCap.Round))
            }
            // A deliberate touch response, distinct from the ambient movement.
            if(allowed && touches>0 && ring.value<1f) {
                val progress=ring.value
                drawCircle(Aura.mint.copy(alpha=(1f-progress)*.45f),
                    center=touchPoint,radius=(12f+65f*progress).dp.toPx(),
                    style=Stroke(width=(2.3f-1.7f*progress).dp.toPx()))
            }
            drawRoundRect(
                brush=Brush.horizontalGradient(listOf(
                    Aura.cyan.copy(alpha=.22f),Color.Transparent,
                    Aura.violet.copy(alpha=.22f))),
                cornerRadius=androidx.compose.ui.geometry.CornerRadius(25.dp.toPx()),
                style=Stroke(width=1.dp.toPx()))
        }
        if (!allowed) {
            val reason= if(!animate) "Свечение выключено в настройках"
                else "Движение приостановлено настройками Android или энергосбережением"
            Text(
                reason,
                color=Aura.secondary,
                fontSize=10.sp,
                modifier=Modifier.align(Alignment.BottomCenter)
                    .background(Aura.deep.copy(alpha=.91f),
                        RoundedCornerShape(topStart=8.dp,topEnd=8.dp))
                    .padding(horizontal=10.dp, vertical=5.dp)
            )
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

/** Quiet secondary action feedback; same semantics as the former OutlineButton. */
@Composable
fun AuroraOutlineButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && motionAllowed(context, true)) .988f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "secondary-press"
    )
    OutlinedButton(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (pressed) Aura.mint.copy(alpha = .70f) else Aura.border
        )
    ) {
        Text(text, color = Aura.white)
    }
}
