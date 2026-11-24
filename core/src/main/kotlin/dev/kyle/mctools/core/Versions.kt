package dev.kyle.mctools.core

object Versions {
    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            .split('.').map { p -> p.filter { it.isDigit() }.toIntOrNull() ?: 0 }

    /** True when [latest] (e.g. "v0.2.0") is a higher version than [current] (e.g. "0.1.1"). */
    fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
