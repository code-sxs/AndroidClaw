// McpClient.kt
// MCP (Model Context Protocol) 客户端实现
// 支持 HTTP 和 stdin/stdout 两种传输方式
// 协议版本: MCP 2024-11-05

package com.androidclaw.app.mcp

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * MCP 请求数据类
 */
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    val params: Map<String, Any>?
)

/**
 * MCP 响应数据类
 */
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: Int,
    val result: Any?,
    val error: McpError?
)

/**
 * MCP 错误数据类
 */
data class McpError(
    val code: Int,
    val message: String,
    val data: Any?
)

/**
 * MCP 工具定义
 */
data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>  // JSON Schema
)

/**
 * MCP 工具调用
 */
data class McpToolCall(
    val name: String,
    val arguments: Map<String, Any>
)

/**
 * MCP 资源定义
 */
data class McpResource(
    val uri: String,
    val name: String,
    val description: String?,
    val mimeType: String?
)

/**
 * MCP 提示词模板
 */
data class McpPrompt(
    val name: String,
    val description: String?,
    val arguments: List<McpPromptArgument>?
)

/**
 * MCP 提示词参数
 */
data class McpPromptArgument(
    val name: String,
    val description: String?,
    val required: Boolean?
)

/**
 * MCP 客户端接口
 */
interface McpClient {
    /**
     * 初始化连接 (握手)
     */
    suspend fun initialize(): Map<String, Any>

    /**
     * 获取工具列表
     */
    suspend fun listTools(): List<McpTool>

    /**
     * 调用工具
     */
    suspend fun callTool(name: String, arguments: Map<String, Any>): Any

    /**
     * 获取资源列表 (可选)
     */
    suspend fun listResources(): List<McpResource>

    /**
     * 读取资源 (可选)
     */
    suspend fun readResource(uri: String): Any

    /**
     * 获取提示词模板列表 (可选)
     */
    suspend fun listPrompts(): List<McpPrompt>

    /**
     * 关闭连接
     */
    fun close()
}

/**
 * HTTP 传输的 MCP 客户端
 * 优先实现，更简单可靠
 */
class McpHttpClient(
    private val serverUrl: String,
    private val timeoutSeconds: Long = 30
) : McpClient {

    companion object {
        private const val TAG = "McpHttpClient"
        private val gson = Gson()
        private val JSON = "application/json".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private var requestId = 0

    override suspend fun initialize(): Map<String, Any> {
        Log.i(TAG, "Initializing MCP connection to $serverUrl")

        val params = mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to mapOf(
                "tools" to true,
                "resources" to true,
                "prompts" to true
            ),
            "clientInfo" to mapOf(
                "name" to "AndroidClaw",
                "version" to "0.1.0"
            )
        )

        val response = sendRequest("initialize", params)
        Log.i(TAG, "Initialize response: $response")
        return response as Map<String, Any>
    }

    override suspend fun listTools(): List<McpTool> {
        Log.i(TAG, "Listing tools from $serverUrl")

        val response = sendRequest("tools/list", null)
        val result = response as? Map<*, *>
        val tools = result?.get("tools") as? List<*>

        return tools?.mapNotNull { toolMap ->
            try {
                val map = toolMap as Map<*, *>
                McpTool(
                    name = map["name"] as String,
                    description = map["description"] as? String ?: "",
                    inputSchema = map["inputSchema"] as? Map<String, Any> ?: emptyMap()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse tool: $toolMap", e)
                null
            }
        } ?: emptyList()
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any>): Any {
        Log.i(TAG, "Calling tool: $name with arguments: $arguments")

        val params = mapOf(
            "name" to name,
            "arguments" to arguments
        )

        return sendRequest("tools/call", params)
    }

    override suspend fun listResources(): List<McpResource> {
        Log.i(TAG, "Listing resources from $serverUrl")

        val response = sendRequest("resources/list", null)
        val result = response as? Map<*, *>
        val resources = result?.get("resources") as? List<*>

        return resources?.mapNotNull { resourceMap ->
            try {
                val map = resourceMap as Map<*, *>
                McpResource(
                    uri = map["uri"] as String,
                    name = map["name"] as String,
                    description = map["description"] as? String,
                    mimeType = map["mimeType"] as? String
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse resource: $resourceMap", e)
                null
            }
        } ?: emptyList()
    }

    override suspend fun readResource(uri: String): Any {
        Log.i(TAG, "Reading resource: $uri")

        val params = mapOf("uri" to uri)
        return sendRequest("resources/read", params)
    }

    override suspend fun listPrompts(): List<McpPrompt> {
        Log.i(TAG, "Listing prompts from $serverUrl")

        val response = sendRequest("prompts/list", null)
        val result = response as? Map<*, *>
        val prompts = result?.get("prompts") as? List<*>

        return prompts?.mapNotNull { promptMap ->
            try {
                val map = promptMap as Map<*, *>
                McpPrompt(
                    name = map["name"] as String,
                    description = map["description"] as? String,
                    arguments = (map["arguments"] as? List<*>)?.mapNotNull { argMap ->
                        try {
                            val arg = argMap as Map<*, *>
                            McpPromptArgument(
                                name = arg["name"] as String,
                                description = arg["description"] as? String,
                                required = arg["required"] as? Boolean
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse prompt: $promptMap", e)
                null
            }
        } ?: emptyList()
    }

    override fun close() {
        Log.i(TAG, "Closing MCP HTTP client")
        httpClient.dispatcher.executorService.shutdown()
    }

    /**
     * 发送 JSON-RPC 请求
     */
    private suspend fun sendRequest(method: String, params: Map<String, Any>?): Any {
        val id = ++requestId
        val request = McpRequest(id = id, method = method, params = params)

        val json = gson.toJson(request)
        Log.d(TAG, "Sending request: $json")

        val body = json.toRequestBody(JSON)
        val httpRequest = Request.Builder()
            .url(serverUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }

            Log.d(TAG, "Received response: $responseBody")

            val mcpResponse = gson.fromJson(responseBody, McpResponse::class.java)

            if (mcpResponse.error != null) {
                throw Exception("MCP Error ${mcpResponse.error.code}: ${mcpResponse.error.message}")
            }

            mcpResponse.result ?: throw Exception("Empty result")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send request: $method", e)
            throw e
        }
    }
}

/**
 * stdin/stdout 传输的 MCP 客户端 (本地进程)
 * 可选实现，用于连接本地 MCP Server
 */
class McpStdioClient(
    private val context: Context,
    private val command: List<String>
) : McpClient {

    companion object {
        private const val TAG = "McpStdioClient"
        private val gson = Gson()
    }

    private var process: Process? = null
    private var inputStream: BufferedReader? = null
    private var outputStream: OutputStream? = null
    private var requestId = 0

    override suspend fun initialize(): Map<String, Any> {
        Log.i(TAG, "Starting MCP stdio process: $command")

        val processBuilder = ProcessBuilder(command)
        process = processBuilder.start()

        inputStream = BufferedReader(InputStreamReader(process!!.inputStream))
        outputStream = process!!.outputStream

        val params = mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to mapOf(
                "tools" to true,
                "resources" to true,
                "prompts" to true
            ),
            "clientInfo" to mapOf(
                "name" to "AndroidClaw",
                "version" to "0.1.0"
            )
        )

        val response = sendRequest("initialize", params)
        return response as Map<String, Any>
    }

    override suspend fun listTools(): List<McpTool> {
        val response = sendRequest("tools/list", null)
        // 解析逻辑类似 McpHttpClient
        throw NotImplementedError("stdio transport not fully implemented yet")
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any>): Any {
        val params = mapOf(
            "name" to name,
            "arguments" to arguments
        )
        return sendRequest("tools/call", params)
    }

    override suspend fun listResources(): List<McpResource> {
        throw NotImplementedError("stdio transport not fully implemented yet")
    }

    override suspend fun readResource(uri: String): Any {
        throw NotImplementedError("stdio transport not fully implemented yet")
    }

    override suspend fun listPrompts(): List<McpPrompt> {
        throw NotImplementedError("stdio transport not fully implemented yet")
    }

    override fun close() {
        Log.i(TAG, "Closing MCP stdio client")
        process?.destroy()
        inputStream?.close()
        outputStream?.close()
    }

    /**
     * 发送 JSON-RPC 请求 (通过 stdin)
     */
    private suspend fun sendRequest(method: String, params: Map<String, Any>?): Any {
        val id = ++requestId
        val request = McpRequest(id = id, method = method, params = params)

        val json = gson.toJson(request)
        Log.d(TAG, "Sending request: $json")

        outputStream?.write((json + "\n").toByteArray())
        outputStream?.flush()

        val responseLine = inputStream?.readLine()
        if (responseLine == null) {
            throw Exception("No response from MCP server")
        }

        Log.d(TAG, "Received response: $responseLine")

        val mcpResponse = gson.fromJson(responseLine, McpResponse::class.java)

        if (mcpResponse.error != null) {
            throw Exception("MCP Error ${mcpResponse.error.code}: ${mcpResponse.error.message}")
        }

        return mcpResponse.result ?: throw Exception("Empty result")
    }
}

/**
 * MCP 客户端工厂
 */
object McpClientFactory {

    /**
     * 创建 MCP 客户端
     * @param serverUrl MCP Server URL (HTTP) 或命令 (stdio)
     */
    fun create(serverUrl: String): McpClient {
        return if (serverUrl.startsWith("http://") || serverUrl.startsWith("https://")) {
            McpHttpClient(serverUrl)
        } else {
            // 假设是本地命令
            val command = serverUrl.split(" ")
            throw IllegalArgumentException("stdio transport requires context, use createStdio instead")
        }
    }

    /**
     * 创建 stdio 客户端
     */
    fun createStdio(context: Context, command: List<String>): McpClient {
        return McpStdioClient(context, command)
    }
}
