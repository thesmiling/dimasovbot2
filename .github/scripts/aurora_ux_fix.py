#!/usr/bin/env python3
"""Aurora Android v0.1.1: fix on-device UX issues; applies on unpacked sources.
The uploaded source bundle is kept intact for reproducibility.
"""
from pathlib import Path

root = Path("source/AuroraTwinAndroid")
activity = root / "app/src/main/java/ru/aurora/twin/MainActivity.kt"
gradle = root / "app/build.gradle.kts"
visuals = root / "app/src/main/java/ru/aurora/twin/AuroraVisuals.kt"

def swap(s, old, new, label):
    if s.count(old) != 1:
        raise RuntimeError(f"{label}: expected one match, got {s.count(old)}")
    return s.replace(old, new, 1)

s=activity.read_text(encoding="utf-8")
s=swap(s, 'setContent { AuroraTheme { TwinApp() } }',
'''setContent {
            AuroraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Aura.base, contentColor = Aura.white) {
                    TwinApp()
                }
            }
        }''', "Set light content color")
s=swap(s,
'    BackHandler(enabled = page != Page.WELCOME && page != Page.HOME) {',
'    val pageScrollState = remember(page) { ScrollState(0) }\n    BackHandler(enabled = page != Page.WELCOME && page != Page.HOME) {',
"Reset scroll when page changes")
s=swap(s,'    Column(Modifier.fillMaxSize().background(Aura.base)) {',
'''    Column(Modifier.fillMaxSize().background(Aura.base)
        .statusBarsPadding().navigationBarsPadding().imePadding()) {''',
"Respect system insets/keyboard")
s=swap(s, 'Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())',
'Column(Modifier.fillMaxSize().verticalScroll(pageScrollState)',
"Use page-specific scroll state")
s=swap(s, '                        MainButton("Дальше — мои границы", { go(Page.BOUNDARIES) })\n',
'', "Move quick action to fixed footer")
s=swap(s, '                        MainButton("Создать стартового двойника", { created = true; go(Page.ASSEMBLING) })\n',
'', "Move boundaries action to fixed footer")
s=swap(s, 'Text("Мои ограничения", fontWeight = FontWeight.SemiBold)',
'Text("Мои ограничения", color = Aura.white, fontWeight = FontWeight.SemiBold)',
"Rules label contrast")
s=swap(s, 'Text("JSON или TXT · до 2 МБ", fontWeight = FontWeight.SemiBold)',
'Text("JSON или TXT · до 2 МБ", color = Aura.white, fontWeight = FontWeight.SemiBold)',
"Import label contrast")
s=swap(s, 'Text(if (testInput.isBlank()) "Привет! Давай познакомимся?" else testInput)',
'Text(if (testInput.isBlank()) "Привет! Давай познакомимся?" else testInput, color = Aura.white)',
"Test chat user text contrast")
s=swap(s, '                            Text(testReply)\n',
'                            Text(testReply, color = Aura.white)\n',
"Test chat twin reply contrast")
s=swap(s, '        if (page in listOf(Page.HOME, Page.DIALOGS, Page.PERSONA, Page.SETTINGS)) {',
'''        if (page == Page.QUICK || page == Page.BOUNDARIES) {
            // Sticky actions remain visible above Android navigation/keyboard.
            Column(Modifier.fillMaxWidth()
                .background(Aura.base)
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 14.dp)) {
                when (page) {
                    Page.QUICK -> MainButton("Дальше — мои границы", { go(Page.BOUNDARIES) })
                    Page.BOUNDARIES -> MainButton("Создать стартового двойника", {
                        created = true
                        go(Page.ASSEMBLING)
                    })
                    else -> Unit
                }
            }
        }
        if (page in listOf(Page.HOME, Page.DIALOGS, Page.PERSONA, Page.SETTINGS)) {''',
"Fixed CTA area for onboarding")
s=swap(s,'                .navigationBarsPadding().padding(vertical = 4.dp),',
'                .padding(vertical = 4.dp),',
"Avoid double navigation inset in tab bar")
activity.write_text(s,encoding="utf-8")

g=gradle.read_text(encoding="utf-8")
if 'androidx.compose.runtime:runtime-saveable' not in g:
    g=swap(g,'    implementation("androidx.compose.ui:ui")',
           '    implementation("androidx.compose.ui:ui")\n    implementation("androidx.compose.runtime:runtime-saveable")',
           "Add runtime-saveable dependency")
gradle.write_text(g, encoding="utf-8")

v=visuals.read_text(encoding="utf-8")
v=swap(v,'val points = List(35) { i ->','val points = List(24) { i ->',"Reduce particle density")
v=swap(v,'c.copy(alpha = .25f + (i % 4) * .07f)',
'c.copy(alpha = .14f + (i % 4) * .04f)',"Reduce particle contrast")
v=swap(v, 'drawLine(Aura.cyan.copy(alpha = .12f)',
'drawLine(Aura.cyan.copy(alpha = .08f)', "Reduce link intensity")
visuals.write_text(v,encoding="utf-8")

# Build-time guards: protect against regressing the five screenshots.
checks = {
    "insets": ".statusBarsPadding().navigationBarsPadding().imePadding()" in s,
    "scroll_reset": "remember(page) { ScrollState(0) }" in s,
    "card_contrast": "contentColor = Aura.white" in s,
    "safe_quick_cta": s.count('MainButton("Дальше — мои границы"') == 1,
    "safe_boundaries_cta": s.count('MainButton("Создать стартового двойника"') == 1,
    "test_reply_contrast": 'Text(testReply, color = Aura.white)' in s,
}
assert all(checks.values()), checks
print("Aurora UX patch applied and 6 static regressions passed.")
