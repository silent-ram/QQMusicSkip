package com.qqmusicskip

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject

data class DiagnosticRecord(val time: Long, val raw: String, val result: String)

object DiagnosticsStore {
    private const val PREFS = "diagnostics"
    private const val KEY = "items"
    val version = mutableIntStateOf(0)

    @Synchronized
    fun add(ctx: Context, raw: String, result: String) {
        val old = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        old.put(JSONObject().put("time", System.currentTimeMillis()).put("raw", raw).put("result", result))
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, old.toString()).apply()
        version.intValue++
    }
    fun all(ctx: Context): List<DiagnosticRecord> {
        val a = JSONArray(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        return (0 until a.length()).mapNotNull { runCatching { a.getJSONObject(it).let { o -> DiagnosticRecord(o.optLong("time"), o.optString("raw"), o.optString("result")) } }.getOrNull() }.reversed()
    }
    @Synchronized
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).commit()
        version.intValue++
    }

    fun exportText(ctx: Context): String = all(ctx).joinToString("\n\n") {
        "${it.time}\n${it.raw}\n${it.result}"
    }
}
