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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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
 * Aurora Motion V2.
 *
 * Important design change:
 * - no scanlines
 * - no straight decorative paths
 * - no sinusoidal "ribbons"
 * - particles have independent phase, speed, depth and turbulent drift
 * - foreground particles are rendered above the artwork so the motion is visible
 * - silhouette illumination is a soft moving field, not a moving face
 *
 * This is still pure native Compose Canvas: no WebView, video or new permission.
 */

private data class TwinParticle(
    val seed: Float,
    val phase: Float,
    val speed: Float,
    val radiusX: Float,
    val radiusY: Float,
    val wobbleX: Float,
    val wobbleY: Float,
    val depth: Float,
    val side: Float,
    val size: Float
)

private fun hash01(i: Int, salt: Int): Float {
    val n = (i * 1103515245L + salt * 12345L + 1013904223L) and 0x7fffffff
    return n.toFloat() / 2147483647f
}

private fun makeParticles(count: Int = 112): List<TwinParticle> =
    List(count) { i ->
        TwinParticle(
            seed = hash01(i, 17),
            phase = hash01(i, 31) * (2f * PI.toFloat()),
            speed = .42f + hash01(i, 43) * 1.35f,
            radiusX = .16f + hash01(i, 59) * .22f,
            radiusY = .16f + hash01(i, 71) * .26f,
            wobbleX = .025f + hash01(i, 83) * .075f,
            wobbleY = .025f + hash01(i, 97) * .075f,
            depth = .15f + hash01(i, 109) * .85f,
            side = hash01(i, 127),
            size = .65f + hash01(i, 149) * 1.65f
        )
    }

private fun organicParticle(p: TwinParticle, t: Float, w: Float, h: Float): Offset {
    val bridge = p.side in .43f.. .57f
    val cx = if (bridge) .5f else if (p.side < .5f) .31f else .69f
    val cy = .52f + .035f * sin(t * .31f + p.phase)

    val a = p.phase +
        t * (.035f + .11f * p.speed) +
        sin(t * (.17f + p.seed * .11f) + p.phase) * .72f +
        sin(t * (.071f + p.seed * .04f) + p.phase * 1.7f) * .34f

    val radial = 1f +
        .16f * sin(t * (.21f + p.seed * .08f) + p.phase * 2.1f)

    var x = cx + cos(a) * p.radiusX * radial
    var y = cy + sin(a * (1.03f + p.seed * .08f)) * p.radiusY

    // Low-frequency drift makes the field feel like fluid air, not an orbit.
    x += sin(t * (.13f + p.seed * .05f) + p.phase * 2.7f) * p.wobbleX
    x += cos(t * (.047f + p.seed * .03f) + p.phase) * p.wobbleX * .55f
    y += cos(t * (.11f + p.seed * .07f) + p.phase * 1.9f) * p.wobbleY
    y += sin(t * (.061f + p.seed * .05f) + p.phase * 2.3f) * p.wobbleY * .7f

    // A few particles genuinely cross the central seam.
    if (bridge) {
        x += sin(t * .17f + p.phase) * .12f
        y += cos(t * .13f + p.phase) * .06f
    }

    return Offset(x * w, y * h)
}

@Composable
private fun liveTime(enabled: Boolean, duration: Int = 24000): Float {
    val context = LocalContext.current
    val allowed = remember(context, enabled) { motionAllowed(context, enabled) }
    if (!allowed) return 0f
    val transition = rememberInfiniteTransition(label = "aurora-motion-v2")
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "motion-time"
    )
    return value
}

@Composable
fun AuroraLivingBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    intensity: Float = 1f
) {
    val t = liveTime(enabled, 30000)
    val strength = intensity.coerceIn(0f, 1f)
    val particles = remember { makeParticles(88) }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Very slow, huge light volumes. They drift independently from particles.
        val c1 = Offset(
            w * (.20f + .16f * sin(t * .21f)),
            h * (.25f + .10f * cos(t * .17f))
        )
        val c2 = Offset(
            w * (.76f + .14f * cos(t * .19f)),
            h * (.70f + .10f * sin(t * .13f + 1.4f))
        )
        val r = max(w, h) * .62f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(Aura.cyan.copy(alpha = .15f * strength),
                    Aura.mint.copy(alpha = .035f * strength),
                    Color.Transparent),
                center = c1, radius = r
            ),
            radius = r, center = c1
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Aura.violet.copy(alpha = .14f * strength),
                    Aura.violet.copy(alpha = .025f * strength),
                    Color.Transparent),
                center = c2, radius = r
            ),
            radius = r, center = c2
        )

        // Free-flight particle field. No connecting lines and no shared trajectory.
        particles.forEach { p ->
            val point = organicParticle(p, t, w, h)
            val twinkle = .55f + .45f * sin(t * (.55f + p.seed * .8f) + p.phase)
            val alpha = (.10f + .20f * twinkle) * strength * (.55f + .45f * p.depth)
            val radius = p.size * (.70f + .45f * p.depth).dp.toPx()

            if (p.depth > .72f) {
                drawCircle(
                    color = if (p.seed > .5f) Aura.violet.copy(alpha = alpha * .16f)
                    else Aura.cyan.copy(alpha = alpha * .16f),
                    radius = radius * 4.2f,
                    center = point
                )
            }
            drawCircle(
                color = if (p.seed > .5f) Aura.violet.copy(alpha = alpha)
                else Aura.cyan.copy(alpha = alpha),
                radius = radius,
                center = point
            )
        }
    }
}

private fun mirrorProfile(w: Float, h: Float, right: Boolean): Path =
    Path().apply {
        fun px(v: Float) = w * (if (right) 1f - v else v)
        moveTo(px(.50f), h * .07f)
        cubicTo(px(.36f), h * .07f, px(.24f), h * .10f, px(.21f), h * .29f)
        cubicTo(px(.20f), h * .40f, px(.17f), h * .46f, px(.12f), h * .52f)
        cubicTo(px(.08f), h * .57f, px(.16f), h * .59f, px(.17f), h * .66f)
        cubicTo(px(.16f), h * .75f, px(.15f), h * .81f, px(.27f), h * .81f)
        cubicTo(px(.35f), h * .82f, px(.40f), h * .92f, px(.40f), h)
        lineTo(px(.50f), h)
        close()
    }

private fun drawParticle(
    draw: androidx.compose.ui.graphics.drawscope.DrawScope,
    point: Offset,
    sizePx: Float,
    color: Color,
    alpha: Float
) {
    draw.drawCircle(color.copy(alpha = alpha * .12f), sizePx * 4.8f, point)
    draw.drawCircle(color.copy(alpha = alpha * .25f), sizePx * 2.2f, point)
    draw.drawCircle(color.copy(alpha = alpha), sizePx, point)
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
    val allowed = remember(context, animate) { motionAllowed(context, animate) }
    val t = liveTime(animate, 19000)
    val strength = intensity.coerceIn(.25f, 1f)
    val particles = remember { makeParticles(116) }

    var touch by remember { mutableStateOf(Offset.Zero) }
    var touchCount by remember { mutableIntStateOf(0) }
    val ripple = remember { Animatable(1f) }

    LaunchedEffect(touchCount) {
        if (touchCount > 0 && allowed) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(25.dp))
            .background(Aura.deep)
    ) {
        // Ambient field behind the artwork.
        AuroraLivingBackdrop(
            Modifier.fillMaxSize(),
            enabled = animate,
            intensity = strength
        )

        Image(
            painter = painterResource(R.drawable.twin_hero),
            contentDescription = "Абстрактный символ цифрового двойника",
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .graphicsLayer {
                    // Almost imperceptible depth movement, not facial animation.
                    val breathe = if (allowed) sin(t * .19f) else 0f
                    scaleX = 1.008f + .004f * breathe
                    scaleY = 1.008f + .004f * breathe
                    translationX = if (allowed) sin(t * .13f) * 1.5f else 0f
                    translationY = if (allowed) cos(t * .11f) * 1.0f else 0f
                },
            contentScale = ContentScale.FillBounds
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(allowed) {
                    detectTapGestures { point ->
                        touch = point
                        touchCount++
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // Foreground particles. Some cross the artwork, some stay near its edge.
            particles.forEachIndexed { index, p ->
                val point = organicParticle(p, t * (1f + p.depth * .18f), w, h)
                val edgeBias = if (p.side < .5f) .0f else 0f
                val visible = if (index % 7 == 0) .90f else .54f
                val twinkle = .55f + .45f * sin(t * (.7f + p.seed) + p.phase)
                val color = if (p.seed > .53f) Aura.violet else Aura.cyan
                val px = p.size * (.65f + p.depth * 1.15f).dp.toPx()

                drawParticle(
                    this,
                    point,
                    px,
                    color,
                    visible * twinkle * strength * (.55f + .45f * p.depth)
                )
            }

            // A small set of larger "hero motes" gives depth without turning the screen into glitter.
            for (i in 0 until 7) {
                val seed = hash01(i, 701)
                val phase = hash01(i, 709) * 2f * PI.toFloat()
                val side = if (i % 2 == 0) .30f else .70f
                val x = side + sin(t * (.07f + seed * .06f) + phase) * (.10f + seed * .07f)
                val y = .48f + cos(t * (.09f + seed * .04f) + phase) * (.22f + seed * .10f)
                val c = if (i % 3 == 0) Aura.violet else Aura.cyan
                drawParticle(
                    this,
                    Offset(x * w, y * h),
                    (1.5f + seed * 2.1f).dp.toPx(),
                    c,
                    (.25f + .20f * sin(t + phase)) * strength
                )
            }

            // Soft moving light volumes INSIDE each silhouette.
            for (right in listOf(false, true)) {
                clipPath(mirrorProfile(w, h, right)) {
                    val phase = if (right) 1.7f else 0f
                    val cx = w * (if (right) .68f else .32f) +
                        sin(t * .17f + phase) * w * .055f
                    val cy = h * .43f +
                        cos(t * .21f + phase) * h * .075f
                    val rr = w * .27f

                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(
                                (if (right) Aura.violet else Aura.cyan).copy(alpha = .20f * strength),
                                (if (right) Aura.cyan else Aura.mint).copy(alpha = .055f * strength),
                                Color.Transparent
                            ),
                            center = Offset(cx, cy),
                            radius = rr
                        ),
                        radius = rr,
                        center = Offset(cx, cy)
                    )

                    // A broad, soft reflection moves diagonally through the digital material.
                    val sweepX = w * (-.15f + ((t / (2f * PI.toFloat()) + phase / 7f) % 1f) * 1.3f)
                    val gloss = Brush.linearGradient(
                        listOf(
                            Color.Transparent,
                            Aura.white.copy(alpha = .05f * strength),
                            Aura.cyan.copy(alpha = .11f * strength),
                            Aura.white.copy(alpha = .07f * strength),
                            Color.Transparent
                        ),
                        start = Offset(sweepX - w * .20f, 0f),
                        end = Offset(sweepX + w * .20f, h)
                    )
                    drawRect(gloss)
                }
            }

            // The seam is a relationship, not a scanning line: one quiet pulse travels through it.
            val seamY = h * (.22f + .56f * (.5f + .5f * sin(t * .48f)))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Aura.mint.copy(alpha = .30f * strength), Color.Transparent),
                    center = Offset(w * .5f, seamY),
                    radius = 24.dp.toPx()
                ),
                radius = 24.dp.toPx(),
                center = Offset(w * .5f, seamY)
            )
            drawCircle(
                color = Aura.white.copy(alpha = .68f * strength),
                radius = 2.3.dp.toPx(),
                center = Offset(w * .5f, seamY)
            )

            if (touchCount > 0 && allowed && ripple.value < 1f) {
                val p = ripple.value
                drawCircle(
                    color = Aura.mint.copy(alpha = (1f - p) * .42f * strength),
                    radius = (10f + 72f * p).dp.toPx(),
                    center = touch,
                    style = Stroke(width = (2.4f - 1.8f * p).dp.toPx())
                )
            }

            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(Aura.cyan.copy(alpha = .16f), Color.Transparent, Aura.violet.copy(alpha = .16f))
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(25.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        if (!allowed) {
            Text(
                "Движение отключено настройками",
                color = Aura.secondary,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Aura.deep.copy(alpha = .88f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun AuraStatusBeacon(active: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val t = liveTime(enabled && active, 3400)
    Canvas(modifier.size(30.dp)) {
        val c = center
        val color = if (active) Aura.green else Aura.orange
        drawCircle(color.copy(alpha = .08f), radius = 13.dp.toPx(), center = c)
        drawCircle(color.copy(alpha = .90f), radius = 3.3.dp.toPx(), center = c)
        if (active && enabled) {
            val r = (6f + 7f * (.5f + .5f * sin(t))).dp.toPx()
            drawCircle(color.copy(alpha = .20f), radius = r, center = c, style = Stroke(width = 1.2.dp.toPx()))
        }
    }
}

@Composable
fun AuroraActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && motionAllowed(context, true)) .982f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "primary-press"
    )
    Button(
        onClick = onClick,
        interactionSource = source,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (pressed) 1.dp else 4.dp, RoundedCornerShape(16.dp), ambientColor = Aura.mint.copy(alpha = .17f)),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Aura.mint,
            contentColor = Aura.deep,
            disabledContainerColor = Aura.raised,
            disabledContentColor = Aura.muted
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun AuroraOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (pressed) Aura.mint.copy(alpha = .70f) else Aura.border
        )
    ) {
        Text(text, color = Aura.white)
    }
}
