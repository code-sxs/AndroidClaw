// AnthropicProviderTest.kt
// Anthropic Claude Provider 单元测试

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
class AnthropicProviderTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var provider: AnthropicProvider

    private val gson = Gson()

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/v1").toString()

        provider = AnthropicProvider(
            apiKey = "test-claude-key",
            baseUrl = baseUrl,
            model = "claude-3-5-sonnet-20241022"
        )
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
        provider.release()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // generateText 测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `generateText returns correct response`() = runTest {
        val response = AnthropicMessageResponse(
            id = "msg_123",
            type = "message",
            role = "assistant",
            content = listOf(
                AnthropicContentBlock(type = "text", text = "Hello! I'm Claude.")
            ),
            model = "claude-3-5-sonnet-20241022",
            stop_reason = "end_turn",
            usage = AnthropicUsage(8, 12, 20)
        )

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(gson.toJson(response))
        )

        val result = provider.generateText("Hello", emptyList())

        assertEquals("Hello! I'm Claude.", result)

        // 验证请求头
        val recordedRequest = mockServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v1/messages", recordedRequest.path)
        assertEquals("test-claude-key", recordedRequest.getHeader("x-api-key"))
        assertEquals("2023-06-01", recordedRequest.getHeader("anthropic-version"))
    }

    @Test
    fun `generateText handles history correctly`() = runTest {
        val response = AnthropicMessageResponse(
            id = "msg_456",
            content = listOf(AnthropicContentBlock(type = "text", text = "Response")),
            usage = null
        )

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(response))
        )

        val history = listOf(
            Message("user", "Previous question"),
            Message("assistant", "Previous answer")
        )

        provider.generateText("Follow-up", history)

        val recordedRequest = mockServer.takeRequest()
        val body = recordedRequest.body.readUtf8()

        assertTrue(body.contains("Previous question"))
        assertTrue(body.contains("Previous answer"))
        assertTrue(body.contains("Follow-up"))
    }

    @Test
    fun `generateText handles error 401`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": {"type": "authentication_error", "message": "Invalid API key"}}""")
        )

        assertThrows(SecurityException::class.java) {
            provider.generateText("Hi", emptyList())
        }
    }

    @Test
    fun `generateText handles error 429 with retry`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(429).setBody("Rate limited"))
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(AnthropicMessageResponse(
                    id = "msg_retry",
                    content = listOf(AnthropicContentBlock(type = "text", text = "After retry")),
                    usage = null
                )))
        )

        val result = provider.generateText("Hi", emptyList())
        assertEquals("After retry", result)
        assertEquals(2, mockServer.requestCount)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // supportsVision 测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `supportsVision returns true for Claude 3 models`() {
        assertTrue(provider.supportsVision())

        val opus = AnthropicProvider("key", "url", "claude-3-opus-20240229")
        assertTrue(opus.supportsVision())

        val sonnet = AnthropicProvider("key", "url", "claude-3-sonnet-20240229")
        assertTrue(sonnet.supportsVision())
    }

    @Test
    fun `supportsVision returns false for older models`() {
        // Claude 2 系列不支持视觉
        val older = AnthropicProvider("key", "url", "claude-2-1")
        assertFalse(older.supportsVision())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // testConnection 测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `testConnection returns true on 200`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(AnthropicMessageResponse(
                    id = "test",
                    content = listOf(AnthropicContentBlock(type = "text", text = "Hi")),
                    usage = null
                )))
        )

        assertTrue(provider.testConnection())
    }

    @Test
    fun `testConnection returns false on 401`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(401))
        assertFalse(provider.testConnection())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getProviderName 测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getProviderName returns correct name`() {
        assertEquals("Anthropic Claude (claude-3-5-sonnet-20241022)", provider.getProviderName())
    }
}
