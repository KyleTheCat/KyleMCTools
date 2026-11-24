package dev.kyle.mctools.ui

import android.content.Context
import android.net.Uri

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kmt_prefs", Context.MODE_PRIVATE)

    var outputTree: Uri?
        get() = sp.getString("tree", null)?.let { Uri.parse(it) }
        set(v) { sp.edit().putString("tree", v?.toString()).apply() }

    var font: FontChoice
        get() = runCatching { FontChoice.valueOf(sp.getString("font", null)!!) }.getOrDefault(FontChoice.MINECRAFT)
        set(v) { sp.edit().putString("font", v.name).apply() }

    var theme: ThemeChoice
        get() = runCatching { ThemeChoice.valueOf(sp.getString("theme", null)!!) }.getOrDefault(ThemeChoice.SYSTEM)
        set(v) { sp.edit().putString("theme", v.name).apply() }
}
