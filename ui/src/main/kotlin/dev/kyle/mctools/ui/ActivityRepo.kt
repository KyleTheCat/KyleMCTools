package dev.kyle.mctools.ui

import android.content.Context
import android.content.SharedPreferences
import dev.kyle.mctools.core.ActivityEntry
import dev.kyle.mctools.core.ActivityJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the last 50 tool runs on the phone (private app storage). */
object ActivityRepo {
    private const val MAX = 50
    private var sp: SharedPreferences? = null
    private val _items = MutableStateFlow<List<ActivityEntry>>(emptyList())
    val items: StateFlow<List<ActivityEntry>> = _items.asStateFlow()

    @Synchronized
    fun init(ctx: Context) {
        if (sp != null) return
        val prefs = ctx.applicationContext.getSharedPreferences("kmt_activity", Context.MODE_PRIVATE)
        sp = prefs
        _items.value = ActivityJson.fromJson(prefs.getString("items", "") ?: "")
    }

    fun add(e: ActivityEntry) = update { listOf(e) + it }
    fun remove(id: Long) = update { list -> list.filter { it.id != id } }
    fun clear() = update { emptyList() }

    @Synchronized
    private fun update(f: (List<ActivityEntry>) -> List<ActivityEntry>) {
        val next = f(_items.value).take(MAX)
        _items.value = next
        sp?.edit()?.putString("items", ActivityJson.toJson(next))?.apply()
    }
}
