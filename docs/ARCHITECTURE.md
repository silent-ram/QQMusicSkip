# 架构说明

## 运行流程

```text
QQ 音乐通知
  -> QQMusicListener
  -> 通知字段与 MediaSession 解析
  -> 付费状态查询 / 通知歌词提取
  -> 单曲规则与全局策略
  -> MediaController 跳歌
  -> Status / SongMemory / DiagnosticsStore
  -> Compose UI
```

## 主要模块

- `QQMusicListener.kt`：通知监听、MediaSession、网络查询和媒体控制。
- `MainActivity.kt`：播放、历史、设置、诊断四个 Compose 页面。
- `SongMemory.kt`：歌曲记录、用户规则和应用设置。
- `DiagnosticsStore.kt`：本地诊断日志的保存、读取和清理。

## 核心约束

- 通知 `title` 是歌曲名的首选来源。
- 通知 `text` 按“歌手 - 专辑”解析。
- MediaSession 标题可能是当前歌词，只用于歌词展示。
- 手动规则优先于全局策略。
- 未知付费状态保守保留。
- 查询完成后必须检查歌曲 key，避免旧结果控制新歌曲。

## 后续重构方向

当前监听服务仍承担较多职责。后续应逐步拆分：

- `NotificationParser`
- `RuleEngine`
- `PayplayRepository`
- `LyricsRepository`
- `StatusStore`（StateFlow）

拆分时应保持现有行为和存储兼容性，并先补充解析与规则测试。
