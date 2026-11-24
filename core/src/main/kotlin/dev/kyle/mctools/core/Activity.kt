package dev.kyle.mctools.core

import kotlinx.serialization.json.*

/** One finished (or failed) tool run, shown on the Activity page. */
data class ActivityEntry(
    val id: Long,
    val tool: String,
    val title: String,
    val file: String,
    val ok: Boolean,
    val summary: String,
    val path: String?,
    val folderUri: String?,
    val time: Long,
    val log: String,
)

object ActivityJson {
    fun toJson(list: List<ActivityEntry>): String = Js.write(
        buildJsonObject {
            putJsonArray("items") {
                for (e in list) add(
                    buildJsonObject {
                        put("id", e.id); put("tool", e.tool); put("title", e.title); put("file", e.file)
                        put("ok", e.ok); put("summary", e.summary); put("path", e.path)
                        put("folderUri", e.folderUri); put("time", e.time); put("log", e.log)
                    },
                )
            }
        },
    )

    fun fromJson(text: String): List<ActivityEntry> {
        val arr = Js.parseObject(text)?.arr("items") ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = o["id"].num()?.toLong() ?: return@mapNotNull null
            ActivityEntry(
                id = id, tool = o.str("tool") ?: "", title = o.str("title") ?: "", file = o.str("file") ?: "",
                ok = (o["ok"] as? JsonPrimitive)?.booleanOrNull ?: false,
                summary = o.str("summary") ?: "", path = o.str("path"), folderUri = o.str("folderUri"),
                time = o["time"].num()?.toLong() ?: 0L, log = o.str("log") ?: "",
            )
        }
    }
}
