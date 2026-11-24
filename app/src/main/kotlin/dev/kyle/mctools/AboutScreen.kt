package dev.kyle.mctools

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kyle.mctools.core.Versions
import dev.kyle.mctools.ui.KButton
import dev.kyle.mctools.ui.Pill
import dev.kyle.mctools.ui.SmallButton
import dev.kyle.mctools.ui.cardStyle

/** The newest published release if it is newer than the installed version, else null. */
fun availableUpdate(gh: GitHubState): Release? =
    gh.releases.firstOrNull()?.takeIf { Versions.isNewer(it.tag, AppConfig.version) }

@Composable
fun AboutScreen(gh: GitHubState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val update = availableUpdate(gh)
    val login = gh.profile?.login ?: AppConfig.owner

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader("ABOUT", "Made by Kyle.", null)

        if (update != null) {
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().cardStyle(cs.primary.copy(alpha = 0.6f)).background(cs.primaryContainer.copy(alpha = 0.5f)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Update available", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Pill(update.tag)
                }
                Text(shortNotes(update.notes).ifBlank { "A new version is ready to download." }, color = cs.onSurfaceVariant, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton("Download", { openUrl(ctx, update.apkUrl ?: update.pageUrl) }, Modifier.weight(1f))
                    KButton("Release page", { openUrl(ctx, update.pageUrl) }, Modifier.weight(1f), primary = false)
                }
            }
        }

        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().cardStyle().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val avatar = gh.avatar
                if (avatar != null) {
                    Image(avatar, contentDescription = "GitHub profile picture", contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(CircleShape))
                } else {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(cs.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text("K", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(gh.profile?.name ?: login, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("@$login on GitHub", color = cs.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                }
                SmallButton("Profile", { openUrl(ctx, "https://github.com/$login") })
            }
            HorizontalDivider(color = cs.outline)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Repository", color = cs.onSurfaceVariant, fontSize = 14.sp)
                    Text(AppConfig.repo, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                SmallButton("Visit repo", { openUrl(ctx, "https://github.com/${AppConfig.repo}") })
            }
        }

        Column(
            Modifier.padding(horizontal = 20.dp).fillMaxWidth().cardStyle().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Changelog", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            val rows: List<Pair<String, String>> =
                if (gh.releases.isNotEmpty()) gh.releases.take(5).map { it.tag to shortNotes(it.notes).lineSequence().firstOrNull().orEmpty().ifBlank { it.title } }
                else BuiltInChangelog
            rows.forEach { (tag, text) ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill(tag)
                    Text(text, color = cs.onSurfaceVariant, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(top = 3.dp))
                }
            }
            Text(
                when {
                    gh.loading -> "Checking GitHub..."
                    gh.failed -> "Couldn't reach GitHub, so this is the built-in list. Check your connection."
                    gh.releases.isEmpty() -> "No releases published yet. Showing the built-in list."
                    else -> "Installed version ${AppConfig.version}."
                },
                color = cs.onSurfaceVariant, fontSize = 13.sp,
            )
        }
    }
}
