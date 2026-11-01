#!/usr/bin/env python3
"""Aurora Motion v0.4 — GPU particle field + seamless identity background.

Design goals from device review:
- the twin artwork is a background layer, not a bordered card
- no bokeh/cloud circles
- no common orbital/line trajectories
- particles use a GPU RuntimeShader on Android 13+
- each particle has independent position, velocity and turbulent drift
- only a few tiny depth glints are used; glow is diffuse, never bubble-shaped
- fallback remains native Canvas for older Android
"""
from pathlib import Path

root = Path("source/AuroraTwinAndroid")
motion = root / "app/src/main/java/ru/aurora/twin/AuroraMotion.kt"

code = r'''
package ru.aurora.twin

import android.os.Build
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/*
 * Aurora Motion v0.4
 *
 * The important change is architectural: the particle field is no longer a
 * collection of Compose circles following shared orbits. Android 13+ uses
 * AGSL RuntimeShader so the GPU computes a fluid field of independent motes.
 * The twin artwork is composited as a full-bleed atmospheric layer with a
 * fade into the page background, not a framed illustration.
 */

private const val PARTICLE_SHADER = """
uniform float2 u_resolution;
uniform float u_time;
uniform float u_intensity;

float hash(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

float2 hash2(float n) {
    return float2(hash(n + 17.13), hash(n + 91.71));
}

float2 wrap(float2 p) {
    return p - floor(p);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / u_resolution.xy;
    float aspect = u_resolution.x / max(u_resolution.y, 1.0);
    float2 p = uv;

    // A very slow atmospheric field. These are broad color volumes, not blobs.
    float2 q1 = float2(
        0.20 + 0.16 * sin(u_time * 0.055),
        0.28 + 0.10 * cos(u_time * 0.041)
    );
    float2 q2 = float2(
        0.80 + 0.14 * cos(u_time * 0.047),
        0.67 + 0.12 * sin(u_time * 0.036 + 1.7)
    );

    float d1 = distance(p * float2(aspect, 1.0), q1 * float2(aspect, 1.0));
    float d2 = distance(p * float2(aspect, 1.0), q2 * float2(aspect, 1.0));
    float a1 = exp(-d1 * d1 * 2.8) * 0.055 * u_intensity;
    float a2 = exp(-d2 * d2 * 3.0) * 0.050 * u_intensity;

    half3 col = half3(0.02, 0.035, 0.075);
    col += half3(0.05, 0.75, 0.72) * a1;
    col += half3(0.35, 0.18, 0.75) * a2;

    // 48 independent motes. Each has its own start point and velocity.
    // Turbulence bends the path continuously so no straight parade appears.
    float particleAlpha = 0.0;
    half3 particleColor = half3(0.0);

    for (int i = 0; i < 48; i++) {
        float fi = float(i);
        float2 seed = hash2(fi * 13.17 + 3.0);

        float speed = 0.010 + hash(fi * 5.31) * 0.025;
        float direction = hash(fi * 8.71) * 6.2831853;
        float2 velocity = float2(cos(direction), sin(direction)) * speed;

        float2 pos = wrap(
            seed +
            velocity * u_time +
            float2(
                sin(u_time * (0.17 + hash(fi * 2.7) * 0.18) + fi) * 0.035,
                cos(u_time * (0.13 + hash(fi * 3.9) * 0.16) + fi * 1.7) * 0.028
            )
        );

        float2 delta = p - pos;
        delta.x *= aspect;

        float depth = hash(fi * 11.3);
        float radius = mix(0.0015, 0.0038, depth);
        float d = length(delta);

        float core = 1.0 - smoothstep(radius * 0.35, radius, d);
        float halo = 1.0 - smoothstep(radius, radius * 2.8, d);

        float twinkle = 0.65 + 0.35 * sin(u_time * (0.8 + depth * 1.6) + fi * 2.3);
        float alpha = (core * 0.46 + halo * 0.055) * twinkle * u_intensity;

        half3 c = mix(
            half3(0.15, 0.72, 0.88),
            half3(0.55, 0.35, 0.92),
            hash(fi * 19.7)
        );

        particleColor += c * alpha;
        particleAlpha += alpha;
    }

    // Keep the shader subtle: it should feel like air carrying light.
    col += particleColor;
    float vignette = 1.0 - smoothstep(0.28, 0.88, distance(uv, float2(0.5, 0.48)));
    col *= 0.84 + vignette * 0.16;

    return half4(col, 1.0);
}
"""

@Composable
private fun liveTime(enabled: Boolean, duration: Int = 26000): Float {
    val context = LocalContext.current
    val allowed = remember(context, enabled) { motionAllowed(context, enabled) }
    if (!allowed) return 0f
    val transition = rememberInfiniteTransition(label = "aurora-motion-v4")
    val value by transition.animateFloat(
        initialValue = 0f,
        targetValue = duration / 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "motion-seconds"
    )
    return value
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun GpuParticleField(
    modifier: Modifier,
    time: Float,
    intensity: Float
) {
    val shader = remember { RuntimeShader(PARTICLE_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }

    Canvas(modifier) {
        shader.setFloatUniform("u_resolution", size.width, size.height)
        shader.setFloatUniform("u_time", time)
        shader.setFloatUniform("u_intensity", intensity.coerceIn(0f, 1f))
        drawRect(brush = brush)
    }
}

@Composable
private fun NativeParticleFallback(
    modifier: Modifier,
    time: Float,
    intensity: Float
) {
    // Older Android fallback. Tiny points only — deliberately no large halos.
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 0 until 34) {
            val f = i.toFloat()
            val sx = fract(sin(f * 91.17f) * 43758.5453f)
            val sy = fract(sin(f * 17.31f) * 19341.123f)
            val speed = .018f + fract(sin(f * 7.11f) * 1712.1f) * .035f
            val dir = fract(sin(f * 13.71f) * 8123.7f) * 2f * PI.toFloat()
            var x = fract(sx + cos(dir) * speed * time)
            var y = fract(sy + sin(dir) * speed * time)
            x += sin(time * (.11f + f * .0017f) + f) * .025f
            y += cos(time * (.09f + f * .0013f) + f * 1.4f) * .02f
            x = fract(x)
            y = fract(y)
            val c = if (i % 3 == 0) Aura.violet else Aura.cyan
            drawCircle(
                color = c.copy(alpha = (.18f + .16f * sin(time * .8f + f) * .5f + .08f) * intensity),
                radius = (0.75f + (i % 3) * .35f).dp.toPx(),
                center = Offset(x * w, y * h)
            )
        }
    }
}

@Composable
fun AuroraLivingBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    intensity: Float = 1f
) {
    val t = liveTime(enabled)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GpuParticleField(modifier, t, intensity)
    } else {
        NativeParticleFallback(modifier, t, intensity)
    }
}

@Composable
fun AuroraHeroAlive(
    modifier: Modifier = Modifier,
    height: Dp = 230.dp,
    animate: Boolean = true,
    active: Boolean = false,
    intensity: Float = 1f
) {
    val context = LocalContext.current
    val allowed = remember(context, animate) { motionAllowed(context, animate) }
    val t = liveTime(animate, 21000)
    val strength = intensity.coerceIn(.25f, 1f)

    var touch by remember { mutableStateOf(Offset.Zero) }
    var touchCount by remember { mutableIntStateOf(0) }
    val ripple = remember { Animatable(1f) }

    LaunchedEffect(touchCount) {
        if (touchCount > 0 && allowed) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        }
    }

    // No clip, no border, no card. The artwork is a visual layer inside the page.
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Aura.deep)
    ) {
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
                .graphicsLayer {
                    // Slight parallax/breathing of the whole composition, never the face.
                    val drift = if (allowed) sin(t * .17f) else 0f
                    scaleX = 1.075f
                    scaleY = 1.075f
                    translationX = drift * 2.2f
                    translationY = if (allowed) cos(t * .13f) * 1.3f else 0f
                    alpha = .88f
                },
            contentScale = ContentScale.Crop
        )

        // Dissolve the image into the page at the bottom and slightly at the top.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .56f to Color.Transparent,
                        .88f to Aura.deep.copy(alpha = .58f),
                        1f to Aura.deep
                    )
                )
        )

        // Only a handful of crisp micro-motes sit in front of the artwork.
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

            for (i in 0 until 12) {
                val f = i.toFloat()
                val baseX = fract(sin(f * 23.17f) * 43758.5453f)
                val baseY = fract(sin(f * 71.31f) * 19341.123f)
                val vx = (0.006f + fract(sin(f * 4.7f) * 901.1f) * .012f)
                val vy = (fract(sin(f * 9.2f) * 401.1f) - .5f) * .012f
                val x = fract(baseX + vx * t + sin(t * .13f + f) * .025f)
                val y = fract(baseY + vy * t + cos(t * .11f + f * 1.2f) * .018f)
                val c = if (i % 2 == 0) Aura.cyan else Aura.violet
                val pulse = .58f + .42f * sin(t * 1.1f + f * 1.9f)
                drawCircle(
                    c.copy(alpha = .28f * pulse * strength),
                    radius = (0.65f + (i % 3) * .28f).dp.toPx(),
                    center = Offset(x * w, y * h)
                )
            }

            // Quiet central "connection" pulse. No scanning line.
            val seamY = h * (.30f + .40f * (.5f + .5f * sin(t * .32f)))
            drawCircle(
                color = Aura.mint.copy(alpha = .10f * strength),
                radius = 18.dp.toPx(),
                center = Offset(w * .5f, seamY)
            )
            drawCircle(
                color = Aura.white.copy(alpha = .56f * strength),
                radius = 1.7.dp.toPx(),
                center = Offset(w * .5f, seamY)
            )

            if (touchCount > 0 && allowed && ripple.value < 1f) {
                val p = ripple.value
                drawCircle(
                    color = Aura.mint.copy(alpha = (1f - p) * .34f * strength),
                    radius = (12f + 64f * p).dp.toPx(),
                    center = touch,
                    style = Stroke(width = (1.8f - 1.2f * p).dp.toPx())
                )
            }
        }

        if (!allowed) {
            Text(
                "Движение отключено настройками",
                color = Aura.secondary,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Aura.deep.copy(alpha = .84f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
fun AuraStatusBeacon(active: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    val t = liveTime(enabled && active, 3400)
    Canvas(modifier.size(30.dp)) {
        val c = if (active) Aura.green else Aura.orange
        drawCircle(c.copy(alpha = .07f), radius = 11.dp.toPx(), center = center)
        drawCircle(c.copy(alpha = .92f), radius = 3.dp.toPx(), center = center)
        if (active && enabled) {
            val r = (5f + 7f * (.5f + .5f * sin(t))).dp.toPx()
            drawCircle(c.copy(alpha = .18f), radius = r, center = center, style = Stroke(width = 1.dp.toPx()))
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
            .shadow(
                if (pressed) 1.dp else 4.dp,
                MaterialTheme.shapes.medium,
                ambientColor = Aura.mint.copy(alpha = .15f)
            ),
        shape = MaterialTheme.shapes.medium,
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
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (pressed) Aura.mint.copy(alpha = .70f) else Aura.border
        )
    ) {
        Text(text, color = Aura.white)
    }
}
'''
motion.write_text(code, encoding="utf-8")
print("Aurora Motion v0.4 installed: GPU field, full-bleed identity background, tiny particles, no card frame.")
