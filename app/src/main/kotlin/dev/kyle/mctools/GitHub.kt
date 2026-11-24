package dev.kyle.mctools

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.kyle.mctools.core.Js
import dev.kyle.mctools.core.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.net.HttpURLConnection
import java.net.URL

object AppConfig {
    val repo: String = BuildConfig.GITHUB_REPO
    val owner: String = repo.substringBefore('/')
    val version: String = BuildConfig.VERSION_NAME
}

data class Profile(val login: String, val name: String?)
data class Release(val tag: String, val title: String, val notes: String, val date: String, val pageUrl: String, val apkUrl: String?)

data class GitHubState(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val avatar: ImageBitmap? = null,
    val releases: List<Release> = emptyList(),
    val failed: Boolean = false,
)

/** What the app shows when GitHub can't be reached. */
val BuiltInChangelog = listOf(
    "0.1.1" to "New look, bottom navigation, Activity and About pages, new icon.",
    "0.1.0" to "Java to Bedrock and Pack to plugin config.",
)

object GitHub {
    private fun fetch(url: String): ByteArray? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "KyleMCTools")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
    } catch (e: Exception) { null }

    suspend fun load(owner: String, repo: String): GitHubState = withContext(Dispatchers.IO) {
        val profileJson = fetch("https://api.github.com/users/$owner")?.toString(Charsets.UTF_8)?.let { Js.parseObject(it) }
        val profile = profileJson?.str("login")?.let { Profile(it, profileJson.str("name")) }
        val avatar = fetch("https://github.com/$owner.png?size=160")?.let { b ->
            BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
        }
        val releasesJson = fetch("https://api.github.com/repos/$repo/releases?per_page=10")
            ?.toString(Charsets.UTF_8)?.let { Js.parseElement(it) } as? JsonArray
        val releases = releasesJson?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if ((o["draft"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
            if ((o["prerelease"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
            val tag = o.str("tag_name") ?: return@mapNotNull null
            val apk = (o["assets"] as? JsonArray)?.firstNotNullOfOrNull { a ->
                val ao = a as? JsonObject
                ao?.str("browser_download_url")?.takeIf { ao.str("name")?.endsWith(".apk") == true }
            }
            Release(tag, o.str("name") ?: tag, o.str("body") ?: "", o.str("published_at")?.take(10) ?: "", o.str("html_url") ?: "", apk)
        } ?: emptyList()
        GitHubState(loading = false, profile = profile, avatar = avatar, releases = releases, failed = profile == null && releasesJson == null)
    }
}

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Couldn't open $url", Toast.LENGTH_LONG).show()
    }
}

/** First few readable lines of a release's notes (markdown marks removed). */
fun shortNotes(body: String): String =
    body.lines().map { it.trim().trimStart('#', '-', '*', ' ') }.filter { it.isNotEmpty() }.take(3).joinToString("\n")
