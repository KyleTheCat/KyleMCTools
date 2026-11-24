package dev.kyle.mctools.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kyle.mctools.core.LogLevel
import dev.kyle.mctools.core.LogLine

/** Button with a press-down animation. primary = filled, otherwise outlined. */
@Composable
fun KButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .clip(shape)
            .background(if (primary) cs.primary else cs.surface)
            .then(if (primary) Modifier else Modifier.border(1.5.dp, cs.primary, shape))
            .clickable(interactionSource = src, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            color = if (primary) cs.onPrimary else cs.primary,
        )
    }
}

@Composable
fun AnimatedProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "progress")
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(p).height(12.dp).clip(RoundedCornerShape(6.dp)).background(cs.primary))
    }
}

@Composable
fun LogPanel(lines: List<LogLine>, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) state.animateScrollToItem(lines.lastIndex) }
    val dark = isDarkColors()
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant)) {
        LazyColumn(state = state, modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(lines) { l ->
                val (tag, color) = when (l.level) {
                    LogLevel.OK -> "ok   " to if (dark) Color(0xFF5CD6A0) else Color(0xFF1E7A4F)
                    LogLevel.WARN -> "warn " to if (dark) Color(0xFFFFB454) else Color(0xFF8A5300)
                    LogLevel.ERROR -> "error" to cs.error
                    LogLevel.INFO -> "info " to cs.onSurfaceVariant
                }
                Text("$tag ${l.text}", color = color, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

/** Animated check mark that draws itself. Shown when a task finishes. */
@Composable
fun SuccessCheck(modifier: Modifier = Modifier, boxSize: Dp = 88.dp) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(boxSize)) {
        val p = anim.value
        val r = this.size.minDimension / 2f
        val sw = r * 0.12f
        drawCircle(color, radius = r * (0.35f + 0.65f * minOf(1f, p * 2f)), alpha = 0.15f)
        drawCircle(color, radius = r * 0.92f * minOf(1f, p * 2f), style = Stroke(width = sw * 0.6f))
        val a = Offset(this.size.width * 0.28f, this.size.height * 0.52f)
        val b = Offset(this.size.width * 0.44f, this.size.height * 0.68f)
        val d = Offset(this.size.width * 0.74f, this.size.height * 0.36f)
        val t = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
        val s1 = (t / 0.4f).coerceIn(0f, 1f)
        val s2 = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
        drawLine(color, a, lerp(a, b, s1), strokeWidth = sw, cap = StrokeCap.Round)
        if (s2 > 0f) drawLine(color, b, lerp(b, d, s2), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Dashed drop zone. Tap to browse, or drag a file in from another app (split screen / desktop mode). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DropZone(
    title: String,
    fileName: String?,
    sizeLabel: String?,
    onBrowse: () -> Unit,
    onDropUri: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    var hover by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()
    val currentOnDrop by rememberUpdatedState(onDropUri)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hover = true }
            override fun onExited(event: DragAndDropEvent) { hover = false }
            override fun onEnded(event: DragAndDropEvent) { hover = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover = false
                val drag = event.toAndroidDragEvent()
                val clip = drag.clipData ?: return false
                if (clip.itemCount == 0) return false
                val uri = clip.getItemAt(0).uri ?: return false
                activity?.requestDragAndDropPermissions(drag)
                currentOnDrop(uri)
                return true
            }
        }
    }
    val scale by animateFloatAsState(if (hover) 1.03f else 1f, label = "dropScale")
    val lineColor = cs.primary
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(if (hover) cs.primaryContainer else cs.surface)
            .drawBehind {
                drawRoundRect(
                    lineColor, cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                )
            }
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
            .clickable(role = Role.Button, onClick = onBrowse)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("+", style = MaterialTheme.typography.headlineLarge, color = cs.primary)
        if (fileName == null) {
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text("or tap to browse", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        } else {
            Text(fileName, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text("${sizeLabel ?: ""}  Tap to change", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
    }
}

fun formatBytes(b: Long): String = when {
    b >= 1024L * 1024 -> "%.1f MB".format(b / 1024.0 / 1024.0)
    b >= 1024 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}


/** Bordered dark card used across the app. */
@Composable
fun Modifier.cardStyle(borderColor: Color = MaterialTheme.colorScheme.outline): Modifier {
    val shape = RoundedCornerShape(20.dp)
    return this.clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, borderColor, shape)
}

/** Small rounded status pill, optionally with a colored dot. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, textColor: Color = MaterialTheme.colorScheme.onSurface, dot: Color? = null) {
    val shape = RoundedCornerShape(20.dp)
    androidx.compose.foundation.layout.Row(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
        Text(text, color = textColor, fontSize = 14.sp)
    }
}

/** Compact outlined button for rows of actions inside cards. */
@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.heightIn(min = 44.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = color, fontSize = 15.sp) }
}

/** Success / warning / danger colors that work on both light and dark. */
@Composable
fun statusColor(ok: Boolean): Color = if (ok) { if (isDarkColors()) Color(0xFF4ADE9A) else Color(0xFF1E7A4F) } else MaterialTheme.colorScheme.error
