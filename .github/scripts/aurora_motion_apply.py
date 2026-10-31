#!/usr/bin/env python3
"""Adds live native Compose motion AFTER the v0.1.1 UX patch.
The demo privacy scanner, safe insets, screens and mock Telegram remain intact.
Idempotent when used on the already-patched source for local testing.
"""
from pathlib import Path
import shutil

root = Path("source/AuroraTwinAndroid")
main = root / "app/src/main/java/ru/aurora/twin/MainActivity.kt"
visuals = root / "app/src/main/java/ru/aurora/twin/AuroraMotion.kt"
shutil.copyfile(".github/motion/AuroraMotion.kt", visuals)

s = main.read_text(encoding="utf-8")

def swap(before, after, label):
    global s
    if s.count(before) != 1:
        raise RuntimeError(f"Motion patch {label}: expected 1 anchor, found {s.count(before)}")
    s = s.replace(before, after, 1)

swap(
    '    var movement by rememberSaveable { mutableStateOf(true) }',
    '    var movement by rememberSaveable { mutableStateOf(true) }\n'
    '    var motionStyle by rememberSaveable { mutableStateOf("Живая") }',
    "Motion settings"
)
swap(
    '            AuroraParticles(Modifier.fillMaxSize(), enabled = movement)',
    '''            AuroraLivingBackdrop(
                Modifier.fillMaxSize(),
                enabled = movement,
                intensity = if (page == Page.WELCOME || page == Page.METHOD || page == Page.HOME)
                    (if (motionStyle == "Спокойная") .46f else .72f)
                else (if (motionStyle == "Спокойная") .25f else .36f)
            )''',
    "Animated underlay"
)
count = s.count('AuroraHero(')
if count < 4:
    raise RuntimeError(f"Expected 4+ hero instances, found {count}")
s = s.replace('AuroraHero(', 'AuroraHeroAlive(')
swap(
    'AuroraHeroAlive(height = 204.dp, animate = movement)',
    '''AuroraHeroAlive(height = 204.dp, animate = movement,
                            active = autopilot, intensity = if (motionStyle == "Спокойная") .55f else 1f)''',
    "Reactive homepage hero"
)
count = s.count('MainButton(')
if count < 6:
    raise RuntimeError(f"Expected 6+ CTA instances, found {count}")
s = s.replace('MainButton(', 'AuroraActionButton(')
swap(
    '''                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Headline(if (created)''',
    '''                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AuraStatusBeacon(active = autopilot, enabled = movement)
                            Spacer(Modifier.width(4.dp))
                            Column(Modifier.weight(1f)) {
                                Headline(if (created)''',
    "Autopilot state beacon"
)
swap(
    '''                                Switch(movement, { movement = it })
                            }
                        }
                        ChoiceCard("Защита переписок"''',
    '''                                Switch(movement, { movement = it })
                            }
                            Text("Интенсивность", color = Aura.secondary, fontSize = 13.sp)
                            PillRow(listOf("Спокойная", "Живая"), motionStyle) { motionStyle = it }
                            Footnote("Системное уменьшение анимации и энергосбережение имеют приоритет.")
                        }
                        ChoiceCard("Защита переписок"''',
    "Motion intensity"
)
main.write_text(s, encoding="utf-8")

readme = root / "README.md"
text = readme.read_text(encoding="utf-8")
text += """

## Aurora Motion 0.2
- Native animated motion fields behind screens, smooth breathing silhouette, orbital particles and luminous neural seam.
- Tap the hero to see a short ripple. Primary buttons have tactile press animation.
- Autopilot has a live status beacon, with green active / amber paused.
- Settings > Живое свечение: enable/disable and choose calm/lively intensity.
- Effects automatically show a static fallback under power saver or reduced system animation scale.
- No new permissions or networking; this is a pure graphics demo.
"""
readme.write_text(text, encoding="utf-8")

assert "statusBarsPadding().navigationBarsPadding().imePadding()" in s
assert 'Text(testReply, color = Aura.white)' in s
assert s.count("AuroraHeroAlive(") >= 4
assert "AuroraLivingBackdrop(" in s and "AuroraActionButton(" in s
print(f"Aurora Motion 0.2 applied: {count} dynamic buttons, {s.count('AuroraHeroAlive(')} living identity views.")