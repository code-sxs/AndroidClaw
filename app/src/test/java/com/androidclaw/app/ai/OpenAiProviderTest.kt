// OpenAiProviderTest.kt
// OpenAI Provider 单元测试

package com.androidclaw.app.ai

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiProviderTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var provider: OpenAiProvider

    private val gson = Gson()

    @Before
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()
        val baseUrl = mockServer.url("/v1").toString()

        provider = OpenAiProvider(
            apiKey = "test-api-key",
            baseUrl = baseUrl,
            model = "gpt-4o"
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
        val response = OpenAiChatResponse(
            id = "chatcmpl-123",
            `object` = "chat.completion",
            created = 1677652288,
            model = "gpt-4o",
            choices = listOf(
                OpenAiChatChoice(
                    index = 0,
                    message = OpenAiChatMessage("assistant", "Hello! How can I help you?"),
                    finishReason = "stop"
                )
            ),
            usage = OpenAiUsage(10, 15, 25)
        )

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(gson.toJson(response))
        )

        val result = provider.generateText("Hi", emptyList())

        assertEquals("Hello! How can I help you?", result)

        // 验证请求参数
        val recordedRequest = mockServer.takeRequest()
        assertEquals("POST", recordedRequest.method)
        assertEquals("/v1/chat/completions", recordedRequest.path)
        assertEquals("Bearer test-api-key", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `generateText handles error 401`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": {"message": "Invalid API key", "type": "invalid_request_error"}}""")
        )

        assertThrows(SecurityException::class.java) {
            provider.generateText("Hi", emptyList())
        }
    }

    @Test
    fun `generateText handles error 429 rate limit`() = runTest {
        mockServer.enqueue(
            MockResponse().setResponseCode(429).setBody("Rate limit exceeded")
        )
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(OpenAiChatResponse(
                    id = "ok",
                    choices = listOf(OpenAiChatChoice(
                        index = 0,
                        message = OpenAiChatMessage("assistant", "Success after retry"),
                        finishReason = "stop"
                    ))
                )))
        )

        // 第一次 429，第二次成功
        val result = provider.generateText("Hi", emptyList())
        assertEquals("Success after retry", result)

        // 验证重试了
        assertEquals(2, mockServer.requestCount)
    }

    @Test
    fun `generateText with history includes all messages`() = runTest {
        val response = OpenAiChatResponse(
            id = "test",
            choices = listOf(OpenAiChatChoice(
                index = 0,
                message = OpenAiChatMessage("assistant", "Response"),
                finishReason = "stop"
            ))
        )

        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(response))
        )

        val history = listOf(
            Message("user", "First message"),
            Message("assistant", "First response"),
            Message("user", "Second message")
        )

        provider.generateText("Third message", history)

        val recordedRequest = mockServer.takeRequest()
        val body = recordedRequest.body.readUtf8()

        // 验证历史消息包含在请求中
        assertTrue(body.contains("First message"))
        assertTrue(body.contains("First response"))
        assertTrue(body.contains("Second message"))
        assertTrue(body.contains("Third message"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // supportsVision 测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `supportsVision returns true for vision models`() {
        val gpt4oProvider = OpenAiProvider("key", "url", "gpt-4o")
        assertTrue(gpt4oProvider.supportsVision())

        val turboProvider = OpenAiProvider("key", "url", "gpt-4-turbo-preview")
        assertTrue(turboProvider.supportsVision())
    }

    @Test
    fun `supportsVision returns false for non-vision models`() {
        val gpt35Provider = OpenAiProvider("key", "url", "gpt-3.5-turbo")
        assertFalse(gpt35Provider.supportsVision())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // testConnection 测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `testConnection returns true on success`() = runTest {
        mockServer.enqueue(
            MockResponse().setResponseCode(200).setBody("{}")
        )

        assertTrue(provider.testConnection())
    }

    @Test
    fun `testConnection returns false on failure`() = runTest {
        mockServer.enqueue(
            MockResponse().setResponseCode(401)
        )

        assertFalse(provider.testConnection())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getProviderName 测试
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getProviderName returns correct name`() {
        assertEquals("OpenAI (gpt-4o)", provider.getProviderName())
    }
}
