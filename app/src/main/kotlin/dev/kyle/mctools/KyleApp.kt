package dev.kyle.mctools

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kyle.mctools.geyser.GeyserConverterScreen
import dev.kyle.mctools.pluginconfig.PluginConfigScreen
import dev.kyle.mctools.ui.FontChoice
import dev.kyle.mctools.ui.KButton
import dev.kyle.mctools.ui.KyleTheme
import dev.kyle.mctools.ui.Prefs
import dev.kyle.mctools.ui.ThemeChoice

@Composable
fun KyleApp(prefs: Prefs) {
    var theme by remember { mutableStateOf(prefs.theme) }
    var font by remember { mutableStateOf(prefs.font) }
    var open by remember { mutableStateOf<ToolId?>(null) }

    KyleTheme(theme, font) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BackHandler(enabled = open != null) { open = null }
            AnimatedContent(
                targetState = open,
                transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f)) togetherWith fadeOut(tween(150)) },
                label = "screen",
            ) { tool ->
                when (tool) {
                    null -> HomeScreen(
                        prefs = prefs, theme = theme, font = font,
                        onTheme = { theme = it; prefs.theme = it },
                        onFont = { font = it; prefs.font = it },
                        onOpen = { open = it },
                    )
                    ToolId.GEYSER -> GeyserConverterScreen(prefs) { open = null }
                    ToolId.PLUGIN -> PluginConfigScreen(prefs) { open = null }
                    else -> ComingSoon(tool) { open = null }
                }
            }
        }
    }
}

@Composable
private fun ComingSoon(tool: ToolId, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(tool.title, style = MaterialTheme.typography.headlineMedium)
        Text(tool.subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Coming in build phase ${tool.phase}.", style = MaterialTheme.typography.titleMedium)
        KButton("Back to tools", onBack, Modifier.fillMaxWidth())
    }
}
