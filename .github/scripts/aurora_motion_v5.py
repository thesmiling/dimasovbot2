#!/usr/bin/env python3
from pathlib import Path

p = Path("source/AuroraTwinAndroid/app/src/main/java/ru/aurora/twin/AuroraMotion.kt")
s = p.read_text(encoding="utf-8")

# Performance root cause: v0.4 evaluates 48 particles for EVERY screen pixel.
# Reduce shader loop cost heavily; visual depth comes from animated light, not particle count.
s = s.replace('for (int i = 0; i < 48; i++) {', 'for (int i = 0; i < 14; i++) {')
s = s.replace('// 48 independent motes.', '// 14 independent GPU motes: intentionally sparse for stable frame time.')

# Dynamic light: make broad cyan/violet fields visibly travel and breathe.
s = s.replace('0.20 + 0.16 * sin(u_time * 0.055),\n        0.28 + 0.10 * cos(u_time * 0.041)',
'''0.18 + 0.28 * sin(u_time * 0.18),
        0.30 + 0.18 * cos(u_time * 0.14)''')
s = s.replace('0.80 + 0.14 * cos(u_time * 0.047),\n        0.67 + 0.12 * sin(u_time * 0.036 + 1.7)',
'''0.82 + 0.24 * cos(u_time * 0.15),
        0.64 + 0.20 * sin(u_time * 0.12 + 1.7)''')
s = s.replace('float a1 = exp(-d1 * d1 * 2.8) * 0.055 * u_intensity;\n    float a2 = exp(-d2 * d2 * 3.0) * 0.050 * u_intensity;',
'''float breathe1 = 0.72 + 0.28 * sin(u_time * 0.47);
    float breathe2 = 0.72 + 0.28 * sin(u_time * 0.39 + 2.1);
    float a1 = exp(-d1 * d1 * 3.2) * 0.115 * breathe1 * u_intensity;
    float a2 = exp(-d2 * d2 * 3.4) * 0.105 * breathe2 * u_intensity;''')

# Remove the centre pulse entirely (the visible grey ring reported on device).
start = s.find('            // Quiet central "connection" pulse. No scanning line.')
end = s.find('            if (touchCount > 0 && allowed && ripple.value < 1f) {', start)
if start != -1 and end != -1:
    s = s[:start] + s[end:]

# Reduce foreground Compose motes as well.
s = s.replace('for (i in 0 until 12) {', 'for (i in 0 until 7) {')

# Hide the rectangular source-image edges on ALL four sides. The image remains the identity
# artwork, but is visually dissolved into the page instead of reading as a card.
old = '''        // Dissolve the image into the page at the bottom and slightly at the top.
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
        )'''
new = '''        // Four-sided edge dissolve. Two broad edge veils hide the rectangular bitmap bounds.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Aura.deep,
                        .12f to Aura.deep.copy(alpha = .42f),
                        .30f to Color.Transparent,
                        .68f to Color.Transparent,
                        .90f to Aura.deep.copy(alpha = .52f),
                        1f to Aura.deep
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Aura.deep,
                        .10f to Aura.deep.copy(alpha = .52f),
                        .25f to Color.Transparent,
                        .75f to Color.Transparent,
                        .90f to Aura.deep.copy(alpha = .52f),
                        1f to Aura.deep
                    )
                )
        )'''
if old not in s:
    raise SystemExit('v0.4 dissolve block not found')
s = s.replace(old, new)

# Lower bitmap opacity a touch so moving light/particles integrate with it.
s = s.replace('alpha = .88f', 'alpha = .82f')

p.write_text(s, encoding="utf-8")
print("Aurora Motion v0.5: 14 GPU motes, dynamic travelling light, no centre ring, four-edge dissolve")
