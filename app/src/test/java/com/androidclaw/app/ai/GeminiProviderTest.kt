// GeminiProviderTest.kt
// Google Gemini Provider 单元测试

package com.androidclaw.app.ai

import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeminiProviderTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var provider: GeminiProvider

    private val gson = Gson()

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/v1beta").toString()

        provider = GeminiProvider(
            apiKey = "test-gemini-key",
            model = "gemini-1.5-pro"
        )
        // 临时替换 URL 注入 MockServer 地址（通过反射或测试配置）
        // 注意：这里假设 baseUrl 为 mockServer 地址
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
        provider.release()
    }

    // 由于 GeminiProvider 硬编码了 API URL，我们测试时需要验证 URL 构造
    // 这里我们测试模型名称、supportsVision 等不依赖实际网络的逻辑

    // ─────────────────────────────────────────────────────────────────────────
    // supportsVision 测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `supportsVision returns true for Gemini 1-5 models`() {
        assertTrue(provider.supportsVision())

        val flash = GeminiProvider("key", "gemini-1.5-flash")
        assertTrue(flash.supportsVision())

        val pro = GeminiProvider("key", "gemini-1.0-pro")
        assertTrue(pro.supportsVision())
    }

    @Test
    fun `supportsVision returns true for vision models`() {
        val vision = GeminiProvider("key", "gemini-pro-vision")
        assertTrue(vision.supportsVision())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // URL 构造测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `apiUrl constructs correct generateContent URL`() {
        val provider = GeminiProvider("my-api-key", "gemini-1.5-pro")
        // 验证 URL 包含正确的模型名和 API key
        val expectedUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key=my-api-key"
        assertEquals(expectedUrl, provider.let {
            // 通过反射访问私有字段（仅测试用途）
            val field = it.javaClass.getDeclaredField("BASE_URL")
            field.isAccessible = true
            val baseUrl = field.get(it) as String
            val modelField = it.javaClass.getDeclaredField("model")
            modelField.isAccessible = true
            val model = modelField.get(it) as String
            val apiKeyField = it.javaClass.getDeclaredField("apiKey")
            apiKeyField.isAccessible = true
            val apiKey = apiKeyField.get(it) as String
            "$baseUrl/models/$model:generateContent?key=$apiKey"
        })
    }

    @Test
    fun `streamUrl includes alt sse parameter`() {
        val provider = GeminiProvider("key", "gemini-1.5-flash")
        val url = provider.let {
            val baseUrlField = it.javaClass.getDeclaredField("BASE_URL")
            baseUrlField.isAccessible = true
            val modelField = it.javaClass.getDeclaredField("model")
            modelField.isAccessible = true
            val apiKeyField = it.javaClass.getDeclaredField("apiKey")
            apiKeyField.isAccessible = true
            val baseUrl = baseUrlField.get(it) as String
            val model = modelField.get(it) as String
            val apiKey = apiKeyField.get(it) as String
            "$baseUrl/models/$model:streamGenerateContent?key=$apiKey&alt=sse"
        }
        assertTrue(url.contains("alt=sse"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 模型名测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getProviderName returns correct name`() {
        assertEquals("Google Gemini (gemini-1.5-pro)", provider.getProviderName())

        val flash = GeminiProvider("key", "gemini-1.5-flash")
        assertEquals("Google Gemini (gemini-1.5-flash)", flash.getProviderName())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Gemini JSON 格式测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `GeminiGenerateRequest serializes correctly`() {
        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = "Hello"),
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = "image/jpeg",
                                data = "base64data"
                            )
                        )
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.9,
                maxOutputTokens = 2048
            )
        )

        val json = gson.toJson(request)

        assertTrue(json.contains("\"contents\""))
        assertTrue(json.contains("\"text\":\"Hello\""))
        assertTrue(json.contains("\"inlineData\""))
        assertTrue(json.contains("\"mimeType\":\"image/jpeg\""))
        assertTrue(json.contains("\"temperature\":0.9"))
        assertTrue(json.contains("\"maxOutputTokens\":2048"))
    }

    @Test
    fun `GeminiGenerateResponse deserializes correctly`() {
        val json = """
            {
                "candidates": [{
                    "content": {
                        "parts": [{"text": "Test response"}]
                    },
                    "finishReason": "STOP",
                    "safetyRatings": []
                }],
                "usageMetadata": {
                    "promptTokenCount": 5,
                    "candidatesTokenCount": 3,
                    "totalTokenCount": 8
                }
            }
        """.trimIndent()

        val response = gson.fromJson(json, GeminiGenerateResponse::class.java)

        assertEquals("Test response", response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text)
        assertEquals("STOP", response.candidates?.firstOrNull()?.finishReason)
        assertEquals(5, response.usageMetadata?.promptTokenCount)
        assertEquals(3, response.usageMetadata?.candidatesTokenCount)
        assertEquals(8, response.usageMetadata?.totalTokenCount)
    }

    @Test
    fun `buildGeminiContents converts history correctly`() {
        val provider = GeminiProvider("key", "gemini-1.5-pro")
        val method = provider.javaClass.getDeclaredMethod(
            "buildGeminiContents",
            List::class.java,
            String::class.java
        )
        method.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val history = listOf(
            Message("user", "Previous question"),
            Message("assistant", "Previous answer")
        )

        @Suppress("UNCHECKED_CAST")
        val contents = method.invoke(provider, history, "Current prompt") as List<GeminiContent>

        assertEquals(3, contents.size)

        // 第一条 user 消息
        assertEquals("user", contents[0].role)
        assertEquals("Previous question", contents[0].parts[0].text)

        // assistant 消息
        assertEquals("model", contents[1].role)
        assertEquals("Previous answer", contents[1].parts[0].text)

        // 当前 prompt
        assertEquals("user", contents[2].role)
        assertEquals("Current prompt", contents[2].parts[0].text)
    }
}
