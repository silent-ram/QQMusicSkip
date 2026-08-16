package com.qqmusicskip

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qqmusicskip.ui.components.AnimatedBackground
import com.qqmusicskip.ui.components.GlassBottomBar
import com.qqmusicskip.ui.components.GlassCard
import com.qqmusicskip.ui.components.GlassNavigationBarItem
import com.qqmusicskip.ui.components.GlassThickness
import com.qqmusicskip.ui.components.GlassTopBar
import com.qqmusicskip.ui.theme.QQmusicskipTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QQmusicskipTheme { MainScreen() } }
    }
}

private enum class AppTab(val label: String) { PLAY("播放"), HISTORY("历史"), SETTINGS("设置"), DIAGNOSTICS("诊断") }
private enum class PayFilter { ALL, FREE, VIP }
private enum class ActionFilter { ALL, AUTO, KEEP, SKIP }

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var tab by remember { mutableStateOf(AppTab.PLAY) }
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var tick by remember { mutableIntStateOf(0) }
    var forcedRecovery by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (listenerEnabled) requestListenerRebind(context)
        while (true) {
            delay(1000); tick++
            listenerEnabled = isNotificationListenerEnabled(context)
            if (listenerEnabled && !Status.listenerConnected && tick == 5 && !forcedRecovery) {
                forcedRecovery = true
                forceRestartListenerComponent(context)
            } else if (listenerEnabled && !Status.listenerConnected && tick % 10 == 0) {
                requestListenerRebind(context)
            }
            if (Status.isPlaying && Status.duration > 0) Status.position = (Status.position + 1000).coerceAtMost(Status.duration)
        }
    }
    @Suppress("UNUSED_VARIABLE") val observe = tick

    Box(Modifier.fillMaxSize()) {
        AnimatedBackground(cover = Status.cover, dark = dark)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = { GlassTopBar(title = "QQ Music Skip") },
            bottomBar = {
                GlassBottomBar {
                    AppTab.entries.forEach { item ->
                        val icon = when (item) {
                            AppTab.PLAY -> Icons.Default.PlayCircle
                            AppTab.HISTORY -> Icons.Default.History
                            AppTab.SETTINGS -> Icons.Default.Settings
                            AppTab.DIAGNOSTICS -> Icons.Default.BugReport
                        }
                        GlassNavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(icon, null) },
                            label = item.label,
                        )
                    }
                }
            },
        ) { padding ->
            when (tab) {
                AppTab.PLAY -> PlaybackPage(context, listenerEnabled, dark, Modifier.padding(padding))
                AppTab.HISTORY -> HistoryPage(context, dark, Modifier.padding(padding))
                AppTab.SETTINGS -> SettingsPage(context, listenerEnabled, dark, Modifier.padding(padding))
                AppTab.DIAGNOSTICS -> DiagnosticsPage(context, dark, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun PlaybackPage(ctx: Context, listener: Boolean, dark: Boolean, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusBanner(listener, AppSettings.enabled(ctx), dark)
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            thickness = GlassThickness.Thick,
            dark = dark,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Status.cover?.let {
                    Image(
                        it.asImageBitmap(),
                        "专辑封面",
                        Modifier.size(176.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } ?: Icon(
                    Icons.Default.MusicNote,
                    null,
                    Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            Status.currentSong.ifBlank { "等待 QQ 音乐播放" },
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            Status.currentArtist.ifBlank { "尚未识别到歌曲" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (Status.currentAlbum.isNotBlank()) Text(
                            Status.currentAlbum,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        AssistChip(
                            onClick = {},
                            label = { Text(payLabel(Status.currentPayplay)) },
                            leadingIcon = { Icon(Icons.Default.Verified, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                Text(
                    if (!AppSettings.lyrics(ctx)) "歌词显示已关闭"
                    else Status.currentLyrics.ifBlank { "暂无歌词，等待 QQ 音乐更新" },
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 28.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (Status.duration > 0) {
                    LinearProgressIndicator(
                        progress = { (Status.position.toFloat() / Status.duration).coerceIn(0f, 1f) },
                        Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatDuration(Status.position), style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.weight(1f))
                        Text(formatDuration(Status.duration), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton({ PlaybackControls.previous?.invoke() }, enabled = PlaybackControls.previous != null) {
                        Icon(Icons.Default.SkipPrevious, "上一首")
                    }
                    FilledIconButton({ PlaybackControls.playPause?.invoke() }, enabled = PlaybackControls.playPause != null) {
                        Icon(
                            if (Status.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (Status.isPlaying) "暂停" else "播放",
                        )
                    }
                    IconButton({ PlaybackControls.next?.invoke() }, enabled = PlaybackControls.next != null) {
                        Icon(Icons.Default.SkipNext, "下一首")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("已跳过", Status.totalSkipped.toString(), Modifier.weight(1f), dark)
            StatCard("已保留", Status.totalKept.toString(), Modifier.weight(1f), dark)
            StatCard("查询失败", Status.totalFailed.toString(), Modifier.weight(1f), dark)
        }
        StatCard("策略：${strategyLabel(AppSettings.strategy(ctx))}", "自动处理", Modifier.fillMaxWidth(), dark)
    }
}

@Composable
private fun StatusBanner(listener: Boolean, enabled: Boolean, dark: Boolean) {
    val ok = listener && enabled && Status.listenerConnected
    val accent = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        thickness = GlassThickness.Small,
        cornerRadius = 16.dp,
        dark = dark,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = accent)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    if (ok) "自动跳过正在运行"
                    else if (!listener) "需要开启通知读取权限"
                    else if (!Status.listenerConnected) "通知监听尚未连接"
                    else "自动跳过已暂停",
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                )
                Text(
                    if (ok) "正在监听 QQ 音乐通知"
                    else if (listener && !Status.listenerConnected) "正在尝试重新连接，可在设置页手动重连"
                    else "可在设置页进行调整",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, dark: Boolean) {
    GlassCard(
        modifier = modifier,
        thickness = GlassThickness.Default,
        cornerRadius = 16.dp,
        dark = dark,
        contentPadding = 14.dp,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (value.isNotBlank()) Text(
                value,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryPage(ctx: Context, dark: Boolean, modifier: Modifier) {
    val version = SongMemory.version.intValue
    var query by remember { mutableStateOf("") }
    var pay by remember { mutableStateOf(PayFilter.ALL) }
    var action by remember { mutableStateOf(ActionFilter.ALL) }
    var clear by remember { mutableStateOf(false) }
    val all = remember(version) { SongMemory.getAll(ctx) }
    val songs = all.filter { r ->
        listOf(r.song, r.artist, r.album).any { it.contains(query, true) } &&
            (pay == PayFilter.ALL || pay == PayFilter.FREE && r.payplay == 0 || pay == PayFilter.VIP && r.payplay == 1) &&
            (action == ActionFilter.ALL || action == ActionFilter.AUTO && r.action == SongRecord.ACTION_DEFAULT || action == ActionFilter.KEEP && r.action == SongRecord.ACTION_KEEP || action == ActionFilter.SKIP && r.action == SongRecord.ACTION_SKIP)
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索歌曲、歌手或专辑") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        LazyColumn(horizontalAlignment = Alignment.Start) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PayFilter.entries.forEach { f ->
                        FilterChip(
                            pay == f,
                            { pay = f },
                            { Text(when (f) { PayFilter.ALL -> "全部"; PayFilter.FREE -> "免费"; PayFilter.VIP -> "VIP" }) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionFilter.entries.forEach { f ->
                        FilterChip(
                            action == f,
                            { action = f },
                            { Text(when (f) { ActionFilter.ALL -> "所有规则"; ActionFilter.AUTO -> "自动"; ActionFilter.KEEP -> "保留"; ActionFilter.SKIP -> "跳过" }) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${songs.size} 首歌曲", Modifier.weight(1f))
                    TextButton({ clear = true }, enabled = all.any { it.action == SongRecord.ACTION_DEFAULT }) {
                        Icon(Icons.Default.DeleteSweep, null)
                        Spacer(Modifier.width(4.dp))
                        Text("清除未标记")
                    }
                }
            }
            if (songs.isEmpty()) item { EmptyState("没有符合条件的歌曲") }
            items(songs, key = { it.key() }) { rec ->
                SongRow(rec, dark) { value -> SongMemory.setAction(ctx, rec.song, rec.artist, value) }
            }
        }
    }
    if (clear) AlertDialog(
        onDismissRequest = { clear = false },
        icon = { Icon(Icons.Default.DeleteSweep, null) },
        title = { Text("清除未标记歌曲？") },
        text = { Text("只删除使用“自动”规则的历史。手动设置为保留或跳过的歌曲不会被删除。") },
        confirmButton = { Button({ SongMemory.clearUnmarked(ctx); clear = false }) { Text("清除") } },
        dismissButton = { TextButton({ clear = false }) { Text("取消") } },
    )
}

@Composable
private fun SongRow(rec: SongRecord, dark: Boolean, onAction: (String) -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        thickness = GlassThickness.Default,
        cornerRadius = 16.dp,
        dark = dark,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rec.song, fontWeight = FontWeight.Medium)
                    Text(
                        listOf(rec.artist, rec.album).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(payLabel(rec.payplay), style = MaterialTheme.typography.labelMedium)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    SongRecord.ACTION_DEFAULT to "自动",
                    SongRecord.ACTION_KEEP to "保留",
                    SongRecord.ACTION_SKIP to "跳过",
                ).forEach { (value, label) ->
                    FilterChip(rec.action == value, { onAction(value) }, { Text(label) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(ctx: Context, listener: Boolean, dark: Boolean, modifier: Modifier) {
    var enabled by remember { mutableStateOf(AppSettings.enabled(ctx)) }
    var lyrics by remember { mutableStateOf(AppSettings.lyrics(ctx)) }
    var network by remember { mutableStateOf(AppSettings.network(ctx)) }
    var strategy by remember { mutableStateOf(AppSettings.strategy(ctx)) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("自动化", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            SettingSwitch(
                title = "自动执行跳过",
                detail = "开启后按照默认策略和单曲规则自动切换下一首；关闭后只识别和记录歌曲。",
                checked = enabled,
                dark = dark,
            ) { enabled = it; AppSettings.setEnabled(ctx, it) }
        }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), dark = dark) {
                Column {
                    Text("默认策略", fontWeight = FontWeight.Medium)
                    Text("手动设置的单曲规则始终优先", style = MaterialTheme.typography.bodySmall)
                    SkipStrategy.entries.forEach { s ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(strategy == s, { strategy = s; AppSettings.setStrategy(ctx, s) })
                            Text(strategyLabel(s))
                        }
                    }
                }
            }
        }
        item { Text("数据来源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item {
            SettingSwitch(
                title = "显示歌词",
                detail = "优先读取 QQ 音乐通知和 MediaSession 的当前歌词。",
                checked = lyrics,
                dark = dark,
            ) { lyrics = it; AppSettings.setLyrics(ctx, it) }
        }
        item {
            SettingSwitch(
                title = "允许网络查询",
                detail = "仅用于查询免费/VIP 状态。歌词直接读取 QQ 音乐通知，不需要网络。",
                checked = network,
                dark = dark,
            ) { network = it; AppSettings.setNetwork(ctx, it) }
        }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), dark = dark) {
                Column {
                    Text("通知读取权限", fontWeight = FontWeight.Medium)
                    Text(
                        if (!listener) "未授权，应用无法识别正在播放的歌曲"
                        else if (Status.listenerConnected) "已授权且监听服务已连接"
                        else "已授权，但监听服务尚未连接",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                            Icon(Icons.Default.Notifications, null)
                            Spacer(Modifier.width(6.dp))
                            Text("管理权限")
                        }
                        Button({ forceRestartListenerComponent(ctx) }, enabled = listener) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(6.dp))
                            Text("重启监听")
                        }
                    }
                }
            }
        }
        item {
            TextButton(
                { AppSettings.reset(ctx); enabled = true; lyrics = true; network = true; strategy = SkipStrategy.FREE_ONLY },
                Modifier.fillMaxWidth(),
            ) { Text("恢复默认设置") }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, dark: Boolean, onChecked: (Boolean) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), dark = dark) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked, onChecked)
        }
    }
}

@Composable
private fun DiagnosticsPage(ctx: Context, dark: Boolean, modifier: Modifier) {
    val version = DiagnosticsStore.version.intValue
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let { writeExport(ctx, it) } }
    val logs = remember(version, query) { DiagnosticsStore.all(ctx).filter { it.raw.contains(query, true) || it.result.contains(query, true) } }
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("搜索诊断日志") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton({ saveLauncher.launch("qqmusic-skip-${System.currentTimeMillis()}.txt") }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Default.FileDownload, "导出文件")
            }
            IconButton({ shareLogs(ctx) }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Default.Share, "分享日志")
            }
            IconButton({ confirmClear = true }, enabled = logs.isNotEmpty()) {
                Icon(Icons.Default.Delete, "清空日志")
            }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            if (logs.isEmpty()) item { EmptyState("暂无诊断日志") }
            items(logs) { log ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    dark = dark,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(log.time), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                            IconButton({ clipboard.setPrimaryClip(ClipData.newPlainText("QQ Music Skip 日志", "${log.raw}\n${log.result}")) }) {
                                Icon(Icons.Default.ContentCopy, "复制")
                            }
                        }
                        Text(log.raw, style = MaterialTheme.typography.bodySmall)
                        Text(log.result, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清空全部诊断日志？") },
        text = { Text("此操作不可撤销。") },
        confirmButton = { Button({ DiagnosticsStore.clear(ctx); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("取消") } },
    )
}

@Composable
private fun EmptyState(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Inbox, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun shareLogs(ctx: Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "QQ Music Skip 诊断日志")
        putExtra(Intent.EXTRA_TEXT, DiagnosticsStore.exportText(ctx))
    }
    ctx.startActivity(Intent.createChooser(intent, "导出诊断日志"))
}

private fun writeExport(ctx: Context, uri: Uri) {
    runCatching { ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(DiagnosticsStore.exportText(ctx)) } }
}

private fun formatTime(time: Long) = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(time))
private fun formatDuration(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1_000 % 60)
private fun payLabel(pay: Int?) = when (pay) { 0 -> "免费"; 1 -> "VIP"; else -> "未知" }
private fun strategyLabel(s: SkipStrategy) = when (s) { SkipStrategy.FREE_ONLY -> "跳过免费"; SkipStrategy.VIP_ONLY -> "跳过 VIP"; SkipStrategy.KEEP_ALL -> "全部保留" }
private fun isNotificationListenerEnabled(ctx: Context): Boolean = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true
private fun requestListenerRebind(ctx: Context) {
    NotificationListenerService.requestRebind(ComponentName(ctx, QQMusicListener::class.java))
}

private fun forceRestartListenerComponent(ctx: Context) {
    val component = ComponentName(ctx, QQMusicListener::class.java)
    DiagnosticsStore.add(ctx, "监听服务", "权限已开启但服务未连接，自动重启监听组件")
    runCatching {
        ctx.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        ctx.packageManager.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        NotificationListenerService.requestRebind(component)
    }.onFailure {
        DiagnosticsStore.add(ctx, "监听服务", "自动恢复失败：${it.message}")
    }
}