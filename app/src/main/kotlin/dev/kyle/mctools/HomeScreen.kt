package dev.kyle.mctools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import dev.kyle.mctools.ui.FontChoice
import dev.kyle.mctools.ui.KButton
import dev.kyle.mctools.ui.Pill
import dev.kyle.mctools.ui.Prefs
import dev.kyle.mctools.ui.ThemeChoice
import dev.kyle.mctools.ui.cardStyle
import dev.kyle.mctools.ui.persistTree
import dev.kyle.mctools.ui.statusColor
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Top bar + eyebrow + headline used by every tab. */
@Composable
fun PageHeader(eyebrow: String, headline: String, sub: String?, trailing: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 16.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(cs.primary), contentAlignment = Alignment.Center) {
                Text("K", color = cs.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text("KyleMCTools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill("v${AppConfig.version}", dot = statusColor(true))
            trailing()
        }
        Text(eyebrow, color = cs.primary, fontSize = 14.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(headline, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
        if (sub != null) Text(sub, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun HomeScreen(
    prefs: Prefs, theme: ThemeChoice, font: FontChoice,
    onTheme: (ThemeChoice) -> Unit, onFont: (FontChoice) -> Unit, onOpen: (ToolId) -> Unit,
) {
    val tools = ToolId.entries
    val pager = rememberPagerState(pageCount = { tools.size })
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var showSettings by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun goTo(page: Int) { scope.launch { pager.animateScrollToPage(page.coerceIn(0, tools.lastIndex)) } }

    Column(
        Modifier.fillMaxSize().statusBarsPadding()
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) false else when (e.key) {
                    Key.DirectionLeft -> { goTo(pager.currentPage - 1); true }
                    Key.DirectionRight -> { goTo(pager.currentPage + 1); true }
                    Key.Enter, Key.NumPadEnter -> { onOpen(tools[pager.currentPage]); true }
                    else -> false
                }
            }
            .focusRequester(focus).focusable(),
    ) {
        PageHeader("${tools.size} TOOLS", "Pick a tool.", "Swipe the cards, then open one.") {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, cs.outline, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { showSettings = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp)) }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 110.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(
                    state = pager, modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    contentPadding = PaddingValues(horizontal = 40.dp), pageSpacing = 14.dp,
                ) { page ->
                    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                    ToolCard(
                        tool = tools[page], index = page, total = tools.size,
                        modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp).graphicsLayer {
                            val s = lerp(0.92f, 1f, 1f - offset)
                            scaleX = s; scaleY = s; alpha = lerp(0.55f, 1f, 1f - offset)
                        },
                        current = page == pager.currentPage,
                        onClick = { if (page == pager.currentPage) onOpen(tools[page]) else goTo(page) },
                    )
                }
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    tools.indices.forEach { i ->
                        val w by animateDpAsState(if (i == pager.currentPage) 28.dp else 8.dp, label = "dot")
                        Box(Modifier.padding(horizontal = 3.dp).width(w).height(8.dp).clip(CircleShape).background(if (i == pager.currentPage) cs.primary else cs.outline))
                    }
                }
                Text(
                    "Swipe, drag or use arrow keys", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    if (showSettings) SettingsDialog(prefs, theme, font, onTheme, onFont) { showSettings = false }
}

@Composable
private fun ToolCard(tool: ToolId, index: Int, total: Int, modifier: Modifier, current: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, label = "cardPress")
    Column(
        modifier.graphicsLayer { scaleX = press; scaleY = press }
            .cardStyle(if (current) cs.primary.copy(alpha = 0.6f) else cs.outline)
            .clickable(interactionSource = src, indication = null, role = Role.Button, onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(tool.icon, contentDescription = null, tint = cs.onPrimaryContainer, modifier = Modifier.size(28.dp))
        }
        Text(tool.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text(tool.subtitle, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
        Box(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tool ${index + 1} of $total", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (tool.available) Pill("Ready", dot = statusColor(true)) else Pill("Phase ${tool.phase}")
        }
        KButton(if (tool.available) "Open tool" else "Preview", onClick, Modifier.fillMaxWidth(), primary = tool.available)
    }
}

@Composable
private fun SettingsDialog(
    prefs: Prefs, theme: ThemeChoice, font: FontChoice,
    onTheme: (ThemeChoice) -> Unit, onFont: (FontChoice) -> Unit, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var treeLabel by remember { mutableStateOf(prefs.outputTree?.lastPathSegment ?: "App storage (not visible in Files)") }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { persistTree(ctx, uri); prefs.outputTree = uri; treeLabel = uri.lastPathSegment ?: "Chosen folder" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Font")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FontChoice.entries.forEach { f -> FilterChip(selected = font == f, onClick = { onFont(f) }, label = { Text(f.label) }) }
                }
                Text("Theme")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice.entries.forEach { t -> FilterChip(selected = theme == t, onClick = { onTheme(t) }, label = { Text(t.label) }) }
                }
                Text("Output folder")
                Text(treeLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KButton("Choose folder", { pickTree.launch(null) }, Modifier.fillMaxWidth(), primary = false)
            }
        },
    )
}
