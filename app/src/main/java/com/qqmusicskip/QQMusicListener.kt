package com.qqmusicskip

import android.app.Notification
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * QQ 音乐通知监听 + 自动跳过服务
 *
 * 流程：
 * 1. 监听 com.tencent.qqmusic 的通知
 * 2. 解析歌名+歌手
 * 3. 调 QQ 音乐搜索 API 查 pay.payplay
 * 4. payplay=0（免费歌）→ 触发 KEYCODE_MEDIA_NEXT 切歌
 */
class QQMusicListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastKey: String? = null
    private val payplayCache = mutableMapOf<String, Int>()
    private var currentSongId: Long? = null  // 从 MediaSession 拿
    private var currentSongMid: String? = null  // 缓存 mid，避免重复搜索
    private var currentToken: android.media.session.MediaSession.Token? = null  // 缓存 token 切歌用

    override fun onListenerConnected() {
        super.onListenerConnected()
        Status.listenerConnected = true
        Status.lastListenerHeartbeat = System.currentTimeMillis()
        Log.d(TAG, "通知监听服务已连接")
        DiagnosticsStore.add(applicationContext, "监听服务", "已连接，开始扫描当前通知")
        PlaybackControls.previous = { control { skipToPrevious() } }
        PlaybackControls.next = { control { skipToNext() } }
        PlaybackControls.playPause = { togglePlayback() }
        // 服务重连或用户打开应用时，主动读取现有通知，避免等待下一次换歌。
        runCatching {
            activeNotifications
                ?.filter { it.packageName == "com.tencent.qqmusic" }
                ?.forEach { onNotificationPosted(it) }
        }.onFailure { Log.e(TAG, "扫描当前通知失败: ${it.message}") }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Status.listenerConnected = false
        Log.w(TAG, "通知监听服务已断开，准备重新绑定")
        // 厂商系统可能在权限切换或后台回收后断开服务，主动请求系统重连。
        Handler(Looper.getMainLooper()).postDelayed({
            requestRebind(ComponentName(this, QQMusicListener::class.java))
        }, 1_000L)
    }

    override fun onDestroy() {
        Status.listenerConnected = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn?.packageName != "com.tencent.qqmusic") return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(Notification.EXTRA_TEXT)
        val notificationLyrics = extractNotificationLyrics(extras, title, text)

        Log.d(TAG, "通知: title=$title text=$text")
        DiagnosticsStore.add(applicationContext, "title=$title text=${text.orEmpty()}", "收到 QQ 音乐通知")

        if (isNonSongNotification(title, text)) {
            Log.d(TAG, "忽略非歌曲通知: title=$title text=$text")
            return
        }

        if (!notificationLyrics.isNullOrBlank()) {
            Status.currentLyrics = notificationLyrics
        }

        // 拿到 MediaSession token（不管 DEBUG_DUMP）
        val token = extras.getParcelable<android.media.session.MediaSession.Token>(
            Notification.EXTRA_MEDIA_SESSION
        )
        if (token != null) {
            currentToken = token
            // 顺便拿 songid（精确路径用）
            try {
                val controller = android.media.session.MediaController(this, token)
                currentSongId = controller.metadata?.description?.mediaId?.toLongOrNull()
                updatePlaybackStatus(controller)
            } catch (e: Exception) {
                Log.w(TAG, "MediaController 取 songid 失败: ${e.message}")
            }
            // QQ 音乐部分版本将当前歌词行放在 MediaSession title，不能用于歌曲识别，
            // 但可以单独用于歌词展示。
            if (DEBUG_DUMP) dumpMediaSession(token)
        }

        val metadata = token?.let { readMetadata(it) }
        val (parsedTitle, parsedArtist) = parseTitleArtist(title, text)
            ?: metadata ?: return
        val key = "${parsedArtist.trim()}|${parsedTitle.trim()}".lowercase()
        val latestLyric = notificationLyrics ?: token?.let { extractSessionLyric(it, title) }
        if (!latestLyric.isNullOrBlank() && AppSettings.lyrics(applicationContext)) {
            Status.currentLyrics = latestLyric
        }
        if (key == lastKey) return
        lastKey = key
        currentSongMid = null
        Status.currentLyrics = latestLyric.orEmpty()

        Status.update(parsedTitle, parsedArtist, null, album = extractAlbum(text))

        scope.launch {
            val startedAt = System.currentTimeMillis()
            val payplay = if (AppSettings.network(applicationContext)) getPayplay(parsedTitle, parsedArtist) else null
            Log.d(TAG, "$parsedTitle - $parsedArtist payplay=$payplay")
            DiagnosticsStore.add(applicationContext, "$parsedTitle - $parsedArtist", "payplay=${payplay ?: "unknown"}")

            // 查询期间可能已经切换歌曲，旧结果不得更新状态或控制当前播放。
            if (lastKey != key) {
                Log.d(TAG, "忽略已过期的查询结果: $parsedTitle - $parsedArtist")
                return@launch
            }
            if (payplay == null) Status.totalFailed++

            if (!AppSettings.enabled(applicationContext)) {
                Log.d(TAG, "自动跳过已关闭")
                Status.update(parsedTitle, parsedArtist, payplay, album = extractAlbum(text))
                return@launch
            }

            // 记录到记忆（如果 payplay 已知）
            if (payplay != null) {
                SongMemory.record(applicationContext, parsedTitle, parsedArtist, payplay, extractAlbum(text))
            }
            // 检查用户标记（优先于 payplay 规则）
            val action = SongMemory.getAction(applicationContext, parsedTitle, parsedArtist)
            when (action) {
                SongRecord.ACTION_KEEP -> {
                    Log.d(TAG, "[保留] 用户标记强制保留")
                    Status.update(parsedTitle, parsedArtist, payplay, kept = true, album = extractAlbum(text))
                    logDecision(parsedTitle, parsedArtist, payplay, "手动保留", startedAt)
                    return@launch
                }
                SongRecord.ACTION_SKIP -> {
                    Log.d(TAG, "[跳过] 用户标记强制跳过")
                    skipSong()
                    Status.update(parsedTitle, parsedArtist, payplay, skipped = true, album = extractAlbum(text))
                    logDecision(parsedTitle, parsedArtist, payplay, "手动跳过", startedAt)
                    return@launch
                }
            }

            val strategy = AppSettings.strategy(applicationContext)
            val shouldSkip = when (strategy) {
                SkipStrategy.FREE_ONLY -> payplay == 0
                SkipStrategy.VIP_ONLY -> payplay == 1
                SkipStrategy.KEEP_ALL -> false
            }
            if (shouldSkip) {
                skipSong()
                Log.d(TAG, ">>> 按策略跳过: $strategy")
                Status.update(parsedTitle, parsedArtist, payplay, album = extractAlbum(text), skipped = true)
                logDecision(parsedTitle, parsedArtist, payplay, "按 $strategy 跳过", startedAt)
            } else {
                Log.d(TAG, "按策略保留: $strategy payplay=$payplay")
                Status.update(parsedTitle, parsedArtist, payplay, album = extractAlbum(text), kept = payplay != null)
                logDecision(parsedTitle, parsedArtist, payplay, "按 $strategy 保留", startedAt)
            }
        }
    }

    /**
     * 打印所有 extras 字段，用来发现 QQ 音乐通知里到底有什么数据
     */
    private fun dumpExtras(extras: android.os.Bundle) {
        Log.d(TAG, "=== Extras Dump ===")
        for (key in extras.keySet()) {
            val value = try { extras.get(key) } catch (e: Exception) { "<error>" }
            val v = when (value) {
                is android.os.Bundle -> "[Bundle keys=${value.keySet()}]"
                is Array<*> -> "[Array size=${value.size} first=${value.firstOrNull()}]"
                else -> value?.toString()?.take(200)
            }
            Log.d(TAG, "  $key = $v")
        }
        Log.d(TAG, "===================")
    }

    /**
     * 通过 MediaSession token 拿到播放队列
     * 用来发现 QQ 音乐当前播放列表里到底有哪些歌
     */
    private fun dumpMediaSession(token: android.media.session.MediaSession.Token) {
        try {
            currentToken = token
            val controller = android.media.session.MediaController(this, token)
            controller.registerCallback(object : android.media.session.MediaController.Callback() {}, null)

            val metadata = controller.metadata
            Log.d(TAG, "=== MediaSession Dump ===")
            val desc = metadata?.description
            Log.d(TAG, "desc.title: ${desc?.title}")
            Log.d(TAG, "desc.subtitle: ${desc?.subtitle}")
            Log.d(TAG, "desc.description: ${desc?.description}")
            Log.d(TAG, "desc.mediaId: ${desc?.mediaId}")
            currentSongId = desc?.mediaId?.toLongOrNull()
            Log.d(TAG, "desc.mediaUri: ${desc?.mediaUri}")
            Log.d(TAG, "METADATA_KEY_MEDIA_ID: ${metadata?.getString(android.media.MediaMetadata.METADATA_KEY_MEDIA_ID)}")
            Log.d(TAG, "METADATA_KEY_TITLE: ${metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)}")
            Log.d(TAG, "METADATA_KEY_ARTIST: ${metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)}")
            Log.d(TAG, "METADATA_KEY_ALBUM: ${metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM)}")
            Log.d(TAG, "METADATA_KEY_DURATION: ${metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION)}")

            val queue = controller.queue
            Log.d(TAG, "队列长度: ${queue?.size ?: 0}")
            queue?.take(20)?.forEachIndexed { i, item ->
                val d = item.description
                Log.d(TAG, "  [$i] title=${d.title} subtitle=${d.subtitle} mediaId=${d.mediaId}")
            }
            Log.d(TAG, "=========================")
        } catch (e: Exception) {
            Log.e(TAG, "MediaSession dump error: ${e.message}")
        }
    }

    /**
     * 从通知解析歌名+歌手
     * QQ 音乐通知格式：
     *   title = "歌名"            (纯歌名)
     *   text  = "歌手 - 专辑/歌单"
     *   或 text = "歌手"
     */
    private fun parseTitleArtist(title: String, text: String?): Pair<String, String>? {
        val song = title.trim()
        if (song.isBlank()) return null
        if (text.isNullOrBlank()) return Pair(song, "")
        val artist = text.split(Regex("\\s[-–—]\\s"), limit = 2)
            .firstOrNull()?.trim().orEmpty()
        return Pair(song, artist)
    }

    private fun extractAlbum(text: String?): String = text?.split(Regex("\\s[-–—]\\s"), limit = 2)
        ?.getOrNull(1)?.trim().orEmpty()

    private fun extractNotificationLyrics(
        extras: android.os.Bundle,
        title: String,
        text: String?
    ): String? {
        val candidates = buildList {
            add(extras.getString(Notification.EXTRA_BIG_TEXT))
            add(extras.getString(Notification.EXTRA_SUB_TEXT))
            for (key in extras.keySet()) {
                if (key.contains("lyric", true) || key.contains("lrc", true)) {
                    add(extras.get(key)?.toString())
                }
            }
        }
        return candidates.asSequence()
            .filterNotNull()
            .map { it.trim() }
            .firstOrNull { value ->
                value.isNotBlank() && value != title.trim() && value != text?.trim() &&
                    !value.startsWith("正在播放") &&
                    (value.contains('\n') || value.length >= 12)
            }
    }

    private fun extractSessionLyric(
        token: android.media.session.MediaSession.Token,
        notificationTitle: String
    ): String? = try {
        val metadata = android.media.session.MediaController(this, token).metadata ?: return null
        val sessionTitle = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.description?.title?.toString()
        sessionTitle?.trim()?.takeIf {
            it.isNotBlank() && !it.equals(notificationTitle.trim(), ignoreCase = true) &&
                !it.equals("QQ音乐", ignoreCase = true) && it.length >= 2
        }
    } catch (_: Exception) { null }

    private fun isNonSongNotification(title: String, text: String?): Boolean {
        val normalizedTitle = title.trim().lowercase()
        val normalizedText = text?.trim().orEmpty()
        if (normalizedTitle in setOf("qq音乐", "qqmusic", "qq music")) return true
        return normalizedText.startsWith("正在播放") &&
            normalizedText.none { it == '-' || it == '–' || it == '—' }
    }

    /**
     * 调 QQ 音乐搜索 API，返回 payplay 值（0=免费, 1=VIP）
     * 失败返回 null
     */
    private fun readMetadata(token: android.media.session.MediaSession.Token): Pair<String, String>? = try {
        val metadata = android.media.session.MediaController(this, token).metadata ?: return null
        val title = metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.description?.title?.toString()
        val artist = metadata.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.description?.subtitle?.toString()
        if (title.isNullOrBlank()) null else Pair(title.trim(), artist.orEmpty().trim())
    } catch (_: Exception) { null }

    private fun getPayplay(song: String, artist: String): Int? {
        val key = "${artist.trim().lowercase()}|${song.trim().lowercase()}"
        payplayCache[key]?.let { return it }

        // 精确路径：用 songid 查 songmid，再调详情 API
        currentSongId?.let { songId ->
            try {
                val mid = findSongMid(songId)
                if (mid != null) {
                    val pay = getPayplayByMid(mid)
                    payplayCache[key] = pay
                    Log.d(TAG, "[精确] songid=$songId mid=$mid pay_play=$pay")
                    return pay
                }
            } catch (e: Exception) {
                Log.w(TAG, "[精确] 失败: ${e.message}")
            }
        }

        // fallback：搜索接口（保守策略）
        return try {
            val query = URLEncoder.encode("$song $artist", "UTF-8")
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
                "?w=$query&n=5&p=1&format=json&g_tk=5381&t=0"

            val conn = URL(url).openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Referer", "https://y.qq.com/")

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val songs = json.getJSONObject("data").getJSONObject("song").getJSONArray("list")

            var matchedPayplay = -1
            for (i in 0 until songs.length()) {
                val s = songs.getJSONObject(i)
                if (s.optString("songname").trim().equals(song.trim(), ignoreCase = true)) {
                    val singers = s.getJSONArray("singer")
                    val singerNames = (0 until singers.length()).joinToString(",") {
                        singers.getJSONObject(it).getString("name")
                    }
                    if (artistMatches(artist, singerNames)) {
                        currentSongMid = s.optString("songmid").takeIf { it.isNotBlank() }
                        val p = s.optJSONObject("pay")?.optInt("payplay", 0) ?: 0
                        if (p > matchedPayplay) matchedPayplay = p
                    }
                }
            }
            if (matchedPayplay >= 0) {
                payplayCache[key] = matchedPayplay
                return matchedPayplay
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "API error: ${e.message}")
            null
        }
    }

    /**
     * 用 songid 搜索找 mid
     */
    private fun findSongMid(songId: Long): String? {
        currentSongMid?.let { return it }
        val query = URLEncoder.encode(songId.toString(), "UTF-8")
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp" +
            "?w=$query&n=10&p=1&format=json&g_tk=5381&t=0"

        val conn = URL(url).openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Referer", "https://y.qq.com/")

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val songs = JSONObject(response).getJSONObject("data").getJSONObject("song").getJSONArray("list")

        for (i in 0 until songs.length()) {
            val s = songs.getJSONObject(i)
            if (s.optLong("songid") == songId) {
                val mid = s.optString("songmid")
                if (mid.isNotEmpty()) {
                    currentSongMid = mid
                    return mid
                }
            }
        }
        return null
    }

    /**
     * 通过 songmid 调 get_song_detail_yqq，拿精确 pay_play
     */
    private fun getPayplayByMid(mid: String): Int {
        val paramStr = """{"song_mid":"$mid"}"""
        val dataStr = """{"songinfo":{"method":"get_song_detail_yqq","module":"music.pf_song_detail_svr","param":${paramStr}}}"""
        val encodedData = URLEncoder.encode(dataStr, "UTF-8")

        val conn = URL("https://u.y.qq.com/cgi-bin/musicu.fcg?data=$encodedData").openConnection() as HttpsURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("Referer", "https://y.qq.com/")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")

        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        return json.getJSONObject("songinfo").getJSONObject("data")
            .getJSONObject("track_info").optInt("pay_play", 0)
    }

    /**
     * 歌手匹配：处理 / , 、 | 等分隔符，任意一方任一歌手命中即可
     */
    private fun artistMatches(target: String, apiSingerStr: String): Boolean {
        fun split(s: String): Set<String> =
            s.replace("/", " ").replace(",", " ").replace("、", " ").replace("|", " ")
                .split(" ").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        val targetParts = split(target)
        val apiParts = split(apiSingerStr)
        return targetParts.any { it in apiParts }
    }

    /**
     * 通过 MediaController.transportControls.skipToNext() 切歌
     * 比 dispatchMediaKeyEvent 更稳，不依赖前台窗口
     */
    private fun skipSong() {
        val token = currentToken ?: run {
            Log.w(TAG, "[切歌] 没有 MediaSession token，fallback 到 media key")
            skipSongViaMediaKey()
            return
        }
        try {
            val controller = android.media.session.MediaController(this, token)
            controller.transportControls.skipToNext()
            Log.d(TAG, "[切歌] transportControls.skipToNext() 调用成功")
        } catch (e: Exception) {
            Log.w(TAG, "[切歌] MediaController 失败: ${e.message}，fallback")
            skipSongViaMediaKey()
        }
    }

    private fun control(action: android.media.session.MediaController.TransportControls.() -> Unit) {
        currentToken?.let { android.media.session.MediaController(this, it).transportControls.action() }
    }

    private fun togglePlayback() {
        val token = currentToken ?: return
        val controller = android.media.session.MediaController(this, token)
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause()
        else controller.transportControls.play()
    }

    private fun updatePlaybackStatus(controller: android.media.session.MediaController) {
        val metadata = controller.metadata
        Status.duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        Status.position = controller.playbackState?.position ?: 0L
        Status.isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        Status.cover = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }

    private fun logDecision(song: String, artist: String, payplay: Int?, action: String, startedAt: Long) {
        DiagnosticsStore.add(applicationContext, "$song - $artist", "付费=${payplay ?: "未知"}；动作=$action；耗时=${System.currentTimeMillis() - startedAt}ms")
    }

    /**
     * 兜底：模拟 KEYCODE_MEDIA_NEXT
     */
    private fun skipSongViaMediaKey() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            val eventTime = SystemClock.uptimeMillis()
            val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_MEDIA_NEXT, 0)
            val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_MEDIA_NEXT, 0)
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
        } catch (e: Exception) {
            Log.e(TAG, "[切歌] media key fallback 也失败: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "QQMusicSkip"
        private const val DEBUG_DUMP = false  // 调试模式
    }
}

/**
 * 共享状态，供 Activity 显示
 */
object Status {
    var listenerConnected by mutableStateOf(false)
    var lastListenerHeartbeat by mutableStateOf(0L)
    var currentSong by mutableStateOf("")
    var currentArtist by mutableStateOf("")
    var currentPayplay by mutableStateOf<Int?>(null)
    var totalSkipped by mutableStateOf(0)
    var totalKept by mutableStateOf(0)
    @Volatile var lastUpdate: Long = 0L

    var currentAlbum by mutableStateOf("")
    var currentLyrics by mutableStateOf("")
    var totalFailed by mutableStateOf(0)
    var duration by mutableStateOf(0L)
    var position by mutableStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var cover by mutableStateOf<android.graphics.Bitmap?>(null)

    fun update(song: String, artist: String, payplay: Int?, skipped: Boolean = false, kept: Boolean = false, album: String = "", lyrics: String = "") {
        currentSong = song
        currentArtist = artist
        currentPayplay = payplay
        currentAlbum = album
        if (lyrics.isNotBlank()) currentLyrics = lyrics
        if (skipped) totalSkipped++
        if (kept) totalKept++
        lastUpdate = System.currentTimeMillis()
    }
}

object PlaybackControls {
    var previous: (() -> Unit)? = null
    var playPause: (() -> Unit)? = null
    var next: (() -> Unit)? = null
}
