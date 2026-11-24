package dev.kyle.mctools

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tab(val label: String, val icon: ImageVector) {
    TOOLS("Tools", Icons.Default.Build),
    ACTIVITY("Activity", Icons.Default.DateRange),
    ABOUT("About", Icons.Default.Info),
}

/** Floating pill navigation at the bottom of the screen. A dot on About means an update is available. */
@Composable
fun FloatingNav(selected: Tab, onSelect: (Tab) -> Unit, updateDot: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.width(320.dp).height(64.dp)
            .shadow(16.dp, shape)
            .clip(shape).background(cs.surface).border(1.dp, cs.outline, shape)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Tab.entries.forEach { t ->
            val on = t == selected
            val bg by animateColorAsState(if (on) cs.primaryContainer else Color.Transparent, label = "tabBg")
            val fg by animateColorAsState(if (on) cs.onPrimaryContainer else cs.onSurfaceVariant, label = "tabFg")
            val itemShape = RoundedCornerShape(16.dp)
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(itemShape).background(bg)
                    .border(1.dp, if (on) cs.primary.copy(alpha = 0.5f) else Color.Transparent, itemShape)
                    .clickable(role = Role.Tab) { onSelect(t) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box {
                    Icon(t.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                    if (t == Tab.ABOUT && updateDot) {
                        Box(Modifier.size(9.dp).offset(x = 16.dp, y = (-2).dp).clip(CircleShape).background(cs.error))
                    }
                }
                Text(t.label, color = fg, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
