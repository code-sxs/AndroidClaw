// MusicControlSkill.kt
// 音乐控制 Skill - 音乐播放控制和查询

package com.androidclaw.app.skills

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log

/**
 * 音乐控制 Skill
 * 通过 MediaSession / MediaController 控制音乐播放
 */
class MusicControlSkill : SkillDefinition {

    companion object {
        private const val TAG = "MusicControlSkill"
    }

    private var mediaSessionManager: MediaSessionManager? = null

    override val skillName: String = "music_control"
    override val displayName: String = "音乐控制"
    override val description: String = "控制音乐播放：播放、暂停、切换曲目、查询当前播放"
    override val requiredPermissions: List<String> = emptyList()

    override suspend fun initialize(context: Context) {
        mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        Log.i(TAG, "MusicControlSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "play",
            displayName = "播放",
            description = "恢复当前音乐播放",
            parameters = emptyList(),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "pause",
            displayName = "暂停",
            description = "暂停当前音乐播放",
            parameters = emptyList(),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "next",
            displayName = "下一首",
            description = "切换到下一首曲目",
            parameters = emptyList(),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "previous",
            displayName = "上一首",
            description = "切换到上一首曲目",
            parameters = emptyList(),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "seek",
            displayName = "跳转进度",
            description = "跳转到指定播放位置",
            parameters = listOf(
                ToolParameter("position_ms", "long", true, "目标位置（毫秒）")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "get_now_playing",
            displayName = "当前播放",
            description = "获取当前正在播放的曲目信息",
            parameters = emptyList(),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "search_music",
            displayName = "搜索音乐",
            description = "搜索设备上的音乐文件（通过 MediaStore 查询）",
            parameters = listOf(
                ToolParameter("query", "string", true, "搜索关键词（歌曲名或歌手名）")
            ),
            returnType = "list"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        return try {
            when (toolName) {
                "play" -> sendTransportCommand("play")
                "pause" -> sendTransportCommand("pause")
                "next" -> sendTransportCommand("next")
                "previous" -> sendTransportCommand("previous")
                "seek" -> seekTo(parameters)
                "get_now_playing" -> getNowPlaying()
                "search_music" -> searchMusic(parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException for $toolName", e)
            ToolResult.Error("无法访问媒体控制: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("操作失败: ${e.message}", e)
        }
    }

    /**
     * 获取所有活跃的 MediaController
     */
    private fun getActiveControllers(): List<MediaController> {
        val msm = mediaSessionManager ?: return emptyList()

        return try {
            msm.getActiveSessions(null).filter { controller ->
                val pkg = controller.packageName
                // 过滤掉系统媒体会话
                pkg != "android" && pkg != "com.android.systemui"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get active sessions", e)
            emptyList()
        }
    }

    /**
     * 发送播放控制命令
     */
    private fun sendTransportCommand(action: String): ToolResult {
        val controllers = getActiveControllers()
        if (controllers.isEmpty()) {
            return ToolResult.Error("没有找到活跃的媒体播放器")
        }

        // 控制所有活跃的媒体会话
        var controlled = false
        for (controller in controllers) {
            try {
                when (action) {
                    "play" -> controller.transportControls.play()
                    "pause" -> controller.transportControls.pause()
                    "next" -> controller.transportControls.skipToNext()
                    "previous" -> controller.transportControls.skipToPrevious()
                }
                controlled = true
                Log.d(TAG, "Sent $action to ${controller.packageName}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send $action to ${controller.packageName}", e)
            }
        }

        if (controlled) {
            val nowPlaying = getNowPlayingInfo()
            return ToolResult.Success(mapOf(
                "status" to "sent",
                "action" to action,
                "now_playing" to nowPlaying
            ))
        } else {
            return ToolResult.Error("无法控制任何媒体播放器")
        }
    }

    /**
     * 跳转进度
     */
    private fun seekTo(params: Map<String, Any>): ToolResult {
        val positionMs = (params["position_ms"] as? Number)?.toLong()
            ?: return ToolResult.Error("缺少参数: position_ms")

        if (positionMs < 0) {
            return ToolResult.Error("位置不能为负数")
        }

        val controllers = getActiveControllers()
        if (controllers.isEmpty()) {
            return ToolResult.Error("没有找到活跃的媒体播放器")
        }

        var success = false
        for (controller in controllers) {
            try {
                val playbackState = controller.playbackState
                val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

                if (duration > 0 && positionMs > duration) {
                    return ToolResult.Error("超出曲目长度（$duration ms）")
                }

                controller.transportControls.seekTo(positionMs)
                success = true
                Log.d(TAG, "Seek to ${positionMs}ms on ${controller.packageName}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to seek on ${controller.packageName}", e)
            }
        }

        return if (success) {
            ToolResult.Success(mapOf(
                "status" to "seeked",
                "position_ms" to positionMs,
                "position_display" to formatDuration(positionMs)
            ))
        } else {
            ToolResult.Error("无法控制任何媒体播放器")
        }
    }

    /**
     * 获取当前播放信息
     */
    private fun getNowPlaying(): ToolResult {
        val controllers = getActiveControllers()
        if (controllers.isEmpty()) {
            return ToolResult.Success(mapOf(
                "is_playing" to false,
                "message" to "没有正在播放的音乐"
            ))
        }

        val nowPlaying = getNowPlayingInfo()
        return ToolResult.Success(nowPlaying)
    }

    /**
     * 获取当前播放信息（内部方法）
     */
    private fun getNowPlayingInfo(): Map<String, Any?> {
        val controllers = getActiveControllers()
        if (controllers.isEmpty()) {
            return mapOf(
                "is_playing" to false,
                "player_count" to 0
            )
        }

        // 使用第一个有元数据的控制器
        val primaryController = controllers.firstOrNull { it.metadata != null } ?: controllers.first()
        val metadata = primaryController.metadata
        val playbackState = primaryController.playbackState

        if (metadata == null) {
            return mapOf(
                "is_playing" to false,
                "player_count" to controllers.size,
                "active_players" to controllers.map { it.packageName }
            )
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val currentPosition = playbackState?.position ?: 0L

        return mapOf(
            "title" to title,
            "artist" to artist,
            "album" to album,
            "duration" to duration,
            "duration_display" to formatDuration(duration),
            "position" to currentPosition,
            "position_display" to formatDuration(currentPosition),
            "is_playing" to isPlaying,
            "player" to primaryController.packageName,
            "player_count" to controllers.size,
            "shuffle_mode" to when (playbackState?.extras?.getInt("shuffle_mode")) {
                1 -> "on"
                2 -> "off"
                else -> null
            },
            "repeat_mode" to when (playbackState?.extras?.getInt("repeat_mode")) {
                1 -> "off"
                2 -> "one"
                3 -> "all"
                else -> null
            }
        )
    }

    /**
     * 搜索音乐
     */
    private fun searchMusic(params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String
            ?: return ToolResult.Error("缺少参数: query")

        if (query.isBlank()) {
            return ToolResult.Error("搜索关键词不能为空")
        }

        // 从 MediaController 中搜索（依赖于音乐 App 的支持）
        val controllers = getActiveControllers()
        val results = mutableListOf<Map<String, Any?>>()

        for (controller in controllers) {
            val metadata = controller.metadata
            if (metadata != null) {
                val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""

                // 匹配当前播放的曲目
                if (title.contains(query, ignoreCase = true) ||
                    artist.contains(query, ignoreCase = true) ||
                    album.contains(query, ignoreCase = true)) {

                    results.add(mapOf(
                        "title" to title,
                        "artist" to artist,
                        "album" to album,
                        "duration" to metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
                        "track_number" to metadata.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER),
                        "is_now_playing" to true,
                        "player" to controller.packageName
                    ))
                }
            }
        }

        Log.d(TAG, "Searched music '$query': ${results.size} results from active players")
        return ToolResult.Success(mapOf(
            "query" to query,
            "results" to results,
            "total" to results.size,
            "message" to if (results.isEmpty()) "当前播放列表中没有匹配的曲目，可尝试在音乐 App 中搜索"
                         else null
        ))
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun release() {
        mediaSessionManager = null
        Log.i(TAG, "MusicControlSkill released")
    }
}
