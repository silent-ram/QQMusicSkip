package com.qqmusicskip

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONObject

/**
 * 单条歌曲记录
 *
 * action: 三态
 *   DEFAULT - 按 payplay 规则判断
 *   KEEP    - 永远保留（不管 VIP/免费）
 *   SKIP    - 永远跳过（不管 VIP/免费）
 */
data class SongRecord(
    val song: String,
    val artist: String,
    val album: String = "",
    val payplay: Int,
    val action: String = ACTION_DEFAULT,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun key(): String = "${artist.lowercase()}|${song.lowercase()}"

    companion object {
        const val ACTION_DEFAULT = "default"
        const val ACTION_KEEP = "keep"
        const val ACTION_SKIP = "skip"
    }
}

/**
 * 歌曲记忆持久化
 * - 每首歌首次遇到就记录（带时间戳）
 * - 用户可手动标记为 "保留" 或 "跳过"
 * - getter 决定判断逻辑
 */
object SongMemory {
    private const val PREFS = "song_memory"
    private const val KEY_PREFIX = "song:"
    private const val MAX_RECORDS = 500
    val version = mutableIntStateOf(0)

    /**
     * 记录/更新歌曲（QQMusicListener 自动调用）
     * 更新时间戳，按时间倒序展示
     */
    fun record(ctx: Context, song: String, artist: String, payplay: Int, album: String = "") {
        val prefs = prefs(ctx)
        val key = makeKey(song, artist)
        val existing = prefs.getString(key, null)
        val action = read(existing)?.optString("action", SongRecord.ACTION_DEFAULT) ?: SongRecord.ACTION_DEFAULT
        val ts = System.currentTimeMillis()
        prefs.edit().putString(key, JSONObject().apply { put("song", song); put("artist", artist); put("album", album); put("payplay", payplay); put("action", action); put("timestamp", ts) }.toString()).apply()
        version.intValue++
    }

    /**
     * 标记歌曲：保留/跳过/默认
     */
    fun setAction(ctx: Context, song: String, artist: String, action: String) {
        val prefs = prefs(ctx)
        val key = makeKey(song, artist)
        val existing = prefs.getString(key, null)
        val value = read(existing) ?: JSONObject().apply {
            put("song", song)
            put("artist", artist)
            put("album", "")
            put("payplay", -1)
            put("timestamp", System.currentTimeMillis())
        }
        value.put("song", song).put("artist", artist).put("action", action).put("timestamp", System.currentTimeMillis())
        prefs.edit().putString(key, value.toString()).apply()
        version.intValue++
    }

    /**
     * 获取歌曲标记
     */
    fun getAction(ctx: Context, song: String, artist: String): String {
        val prefs = prefs(ctx)
        val key = makeKey(song, artist)
        val existing = prefs.getString(key, null) ?: return SongRecord.ACTION_DEFAULT
        return read(existing)?.optString("action", SongRecord.ACTION_DEFAULT) ?: legacyAction(existing)
    }

    /**
     * 获取所有记录（按时间戳倒序：最新的在最上面）
     */
    fun getAll(ctx: Context): List<SongRecord> {
        val prefs = prefs(ctx)
        return prefs.all.mapNotNull { (k, v) ->
            if (k.startsWith(KEY_PREFIX) && v is String) {
                val value = read(v)
                if (value != null) {
                    SongRecord(
                        song = value.optString("song"), artist = value.optString("artist"), album = value.optString("album"), payplay = value.optInt("payplay", 0),
                        action = value.optString("action", SongRecord.ACTION_DEFAULT), timestamp = value.optLong("timestamp", 0L)
                    )
                } else null
            } else null
        }.sortedByDescending { it.timestamp }
    }

    /**
     * 只清除未标记（action=DEFAULT）的歌
     * 保留所有 KEEP/SKIP 的歌曲
     * 返回清除的数量
     */
    fun clearUnmarked(ctx: Context): Int {
        val prefs = prefs(ctx)
        val editor = prefs.edit()
        var removed = 0
        val toRemove = mutableListOf<String>()
        for ((k, v) in prefs.all) {
            if (k.startsWith(KEY_PREFIX) && v is String) {
                val action = read(v)?.optString("action", SongRecord.ACTION_DEFAULT) ?: legacyAction(v)
                if (action == SongRecord.ACTION_DEFAULT) {
                    toRemove.add(k)
                    removed++
                }
            }
        }
        toRemove.forEach { editor.remove(it) }
        editor.apply()
        version.intValue++
        return removed
    }

    /**
     * 完全清空（保留方法以备扩展，但 UI 中不暴露按钮）
     */
    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        version.intValue++
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun makeKey(song: String, artist: String): String =
        "$KEY_PREFIX${artist.trim().lowercase()}|${song.trim().lowercase()}"

    private fun read(value: String?): JSONObject? = try {
        if (value.isNullOrBlank()) null
        else if (value.trimStart().startsWith("{")) JSONObject(value)
        else value.split("|").takeIf { it.size >= 3 }?.let { parts ->
            JSONObject().put("song", parts[0]).put("artist", parts[1])
                .put("payplay", parts[2].toIntOrNull() ?: 0)
                .put("action", parts.getOrNull(3) ?: SongRecord.ACTION_DEFAULT)
                .put("timestamp", parts.getOrNull(4)?.toLongOrNull() ?: 0L)
        }
    } catch (_: Exception) { null }
    private fun legacyAction(value: String): String = value.split("|").getOrNull(3) ?: SongRecord.ACTION_DEFAULT
}

enum class SkipStrategy { FREE_ONLY, VIP_ONLY, KEEP_ALL }

object AppSettings {
    private const val PREFS = "app_settings"
    private const val ENABLED = "enabled"
    private const val STRATEGY = "strategy"
    private const val LYRICS = "lyrics"
    private const val NETWORK = "network"

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean(ENABLED, true)
    fun setEnabled(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(ENABLED, value).apply()
    fun strategy(ctx: Context): SkipStrategy = runCatching {
        SkipStrategy.valueOf(prefs(ctx).getString(STRATEGY, SkipStrategy.FREE_ONLY.name)!!)
    }.getOrDefault(SkipStrategy.FREE_ONLY)
    fun setStrategy(ctx: Context, value: SkipStrategy) = prefs(ctx).edit().putString(STRATEGY, value.name).apply()
    fun lyrics(ctx: Context): Boolean = prefs(ctx).getBoolean(LYRICS, true)
    fun setLyrics(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(LYRICS, value).apply()
    fun network(ctx: Context): Boolean = prefs(ctx).getBoolean(NETWORK, true)
    fun setNetwork(ctx: Context, value: Boolean) = prefs(ctx).edit().putBoolean(NETWORK, value).apply()
    fun reset(ctx: Context) = prefs(ctx).edit().clear().apply()
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
