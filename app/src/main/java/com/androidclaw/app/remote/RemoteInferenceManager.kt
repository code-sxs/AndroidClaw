// RemoteInferenceManager.kt
// 远程推理接口 - 暂时禁用
// TODO: 完整实现需要修复类定义不匹配问题

package com.androidclaw.app.remote

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.androidclaw.app.skills.ToolDefinition
import com.androidclaw.app.skills.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/*
// 暂时禁用的导入
import com.androidclaw.app.skills.ToolParameter
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
*/

// ─────────────────────────────────────────────────────────────────────────────
// 简化版本 - 只保留基本定义
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 消息
 */
data class Message(
    val role: String,
    val content: String
)

/**
 * 工具调用
 */
data class ToolCall(
    val toolName: String,
    val parameters: Map<String, Any>
)

/**
 * 视觉请求
 */
data class VisionRequest(
    val imageUrl: String? = null,
    val imageBase64: String? = null,
    val prompt: String
)

/**
 * 视觉响应
 */
data class VisionResponse(
    val description: String,
    val objects: List<String>? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// Remote Inference Manager（简化版 - 禁用状态）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 远程推理管理器（简化版）
 * 远程推理功能暂时禁用，等待完整实现
 */
class RemoteInferenceManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "RemoteInferenceManager"
        
        private var INSTANCE: RemoteInferenceManager? = null
        
        fun getInstance(context: Context): RemoteInferenceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteInferenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    init {
        Log.w(TAG, "Remote inference is disabled (simplified mode)")
    }

    /**
     * 是否已连接 - 始终返回 false（禁用状态）
     */
    fun isConnected(): Boolean = false

    /**
     * 是否启用 - 始终返回 false（禁用状态）
     */
    fun isEnabled(): Boolean = false

    /**
     * 生成文本 - 抛出异常（功能禁用）
     */
    suspend fun generateText(prompt: String, history: List<Message>): String {
        throw IllegalStateException("Remote inference is disabled")
    }

    /**
     * 生成文本（流式）- 返回空 Flow（功能禁用）
     */
    suspend fun generateTextStream(prompt: String, history: List<Message>): Flow<String> {
        return kotlinx.coroutines.flow.flow { }
    }

    /**
     * 分析图片 - 抛出异常（功能禁用）
     */
    suspend fun analyzeImage(imageBytes: ByteArray, prompt: String): String {
        throw IllegalStateException("Remote inference is disabled")
    }

    /**
     * 获取可用工具列表 - 返回空列表（功能禁用）
     */
    suspend fun listTools(): List<ToolDefinition> = emptyList()

    /**
     * 调用工具 - 返回错误（功能禁用）
     */
    suspend fun callTool(name: String, params: Map<String, Any>): ToolResult {
        return ToolResult.Error("Remote inference is disabled")
    }

    /**
     * 测试连接 - 返回 false（功能禁用）
     */
    suspend fun testConnection(): Boolean = false

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "release (disabled mode)")
    }
}
