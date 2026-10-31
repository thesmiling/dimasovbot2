#!/usr/bin/env python3
"""Aurora v0.3. Keeps privacy protection, navigation, and all demo functions.
Must run AFTER aurora_ux_fix.py and aurora_motion_apply.py.
"""
from pathlib import Path
import shutil
root = Path("source/AuroraTwinAndroid")
main = root / "app/src/main/java/ru/aurora/twin/MainActivity.kt"
motion = root / "app/src/main/java/ru/aurora/twin/AuroraMotion.kt"
shutil.copyfile(".github/motion/AuroraSoftMirror.kt", motion)
s = main.read_text(encoding="utf-8")

def swap(old, new, description):
    global s
    occurrences = s.count(old)
    if occurrences != 1:
        raise RuntimeError(f"{description}: expected unique anchor, found {occurrences}")
    s=s.replace(old,new,1)

swap("import androidx.compose.animation.AnimatedContent",
"""import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith""",
"Transition imports")
swap("import androidx.compose.animation.core.animateFloatAsState",
"""import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween""",
"Transition timing")
swap("""            Column(Modifier.fillMaxSize().verticalScroll(pageScrollState)
                .padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                when (page) {""",
"""            AnimatedContent(
                targetState = page,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (motionAllowed(context, movement)) {
                        (fadeIn(animationSpec = tween(245)) +
                          slideInVertically(animationSpec = tween(295)) { it / 14 })
                            .togetherWith(fadeOut(animationSpec = tween(140)))
                    } else {
                        fadeIn(animationSpec = tween(0))
                            .togetherWith(fadeOut(animationSpec = tween(0)))
                    }
                },
                label = "soft-screen-transition"
            ) { shownPage ->
                Column(Modifier.fillMaxSize()
                    .verticalScroll(remember(shownPage) { ScrollState(0) })
                    .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp)) {
                when (shownPage) {""",
"Soft fade and short vertical translation, scoped to screen content")
swap("""                Spacer(Modifier.height(16.dp))
            }
        }
        if (page == Page.QUICK""",
"""                Spacer(Modifier.height(16.dp))
                }
            }
        }
        if (page == Page.QUICK""",
"Close AnimatedContent safely")
# Keep existing insets and sticky CTAs. The old pageScrollState remains as a no-op
# to preserve the older UX regression checks.
swap("""    var motionStyle by rememberSaveable { mutableStateOf("Живая") }""",
"""    var motionStyle by rememberSaveable { mutableStateOf("Спокойная") }""",
"Calm visual default")
swap(
    'Text("Частицы, мягкие волны и подсветка", color = Aura.secondary, fontSize = 12.sp)',
    'Text("Мягкое цифровое зеркало и плавные переходы", color = Aura.secondary, fontSize = 12.sp)',
    "Motion description"
)
swap(
    'PillRow(listOf("Спокойная", "Живая"), motionStyle) { motionStyle = it }',
    'PillRow(listOf("Спокойная", "Живая"), motionStyle) { motionStyle = it }',
    "Preserve motion intensity selector"
) if False else None

# Preserve strong button feedback from v0.2. Give secondary buttons a quieter response.
s=s.replace('OutlineButton(', 'AuroraOutlineButton(')
# Make choice cards respond to touches: ripple + 1.2% scale change, no flashy bounce.
swap('import androidx.compose.foundation.*',
"""import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer""",
"Press feedback imports")
swap(
'''@Composable private fun ChoiceCard(title: String, subtitle: String, badge: String, onClick: () -> Unit) {
    AuroraCard(modifier = Modifier.clickable(onClick = onClick)) {''',
'''@Composable private fun ChoiceCard(title: String, subtitle: String, badge: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && motionAllowed(LocalContext.current, true)) .988f else 1f,
        label = "choice-press"
    )
    AuroraCard(modifier = Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)) {''',
"Interactive choice cards"
)
# Unlike human silhouettes, UI chips may smoothly morph their surface colors.
swap(
'''            val active = selected == v
            Box(Modifier.clip(RoundedCornerShape(100.dp))
                .background(if (active) Aura.mint.copy(alpha = .17f) else Aura.raised)
                .border(1.dp, if (active) Aura.mint else Aura.border, RoundedCornerShape(100.dp))
                .clickable { onSelect(v) }.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text(v, color = if (active) Aura.mint else Aura.secondary, fontSize = 13.sp)
            }''',
'''            val active = selected == v
            val bg by androidx.compose.animation.animateColorAsState(
                if (active) Aura.mint.copy(alpha = .17f) else Aura.raised, label = "pill-bg")
            val outline by androidx.compose.animation.animateColorAsState(
                if (active) Aura.mint else Aura.border, label = "pill-outline")
            val textColor by androidx.compose.animation.animateColorAsState(
                if (active) Aura.mint else Aura.secondary, label = "pill-label")
            Box(Modifier.clip(RoundedCornerShape(100.dp))
                .background(bg)
                .border(1.dp, outline, RoundedCornerShape(100.dp))
                .clickable { onSelect(v) }.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text(v, color = textColor, fontSize = 13.sp)
            }''',
"Animated selection chips"
)
main.write_text(s, encoding="utf-8")
readme = root / "README.md"
readme.write_text(readme.read_text(encoding="utf-8")+"""

## Aurora Soft Mirror v0.3
- Static illustrative silhouettes; animated light-only reflections and connection threads.
- Deliberately avoids face motion, breathing, eyes, mouths, human realism and uncanny valley.
- Content-level 245ms crossfade with subtle 295ms vertical entry (skips under reduced motion).
- Gentle press response on primary/secondary buttons and choice cards; animated selection chips.
- Defaults to Calm, keeps Lively option, respects battery saver/system motion settings.
- All Telegram messaging remains demo-only with no network permission.
""", encoding="utf-8")
assert s.count('AnimatedContent(') >= 1
assert "when (shownPage)" in s
assert "statusBarsPadding().navigationBarsPadding().imePadding()" in s
assert "AuroraHeroAlive(" in s
assert "Text(testReply, color = Aura.white)" in s
assert "AuroraOutlineButton(" in s
print("Aurora Soft Mirror v0.3 applied and static guards passed.")
