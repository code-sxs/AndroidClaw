// AiProviderManager.kt
// AI 提供商管理器
// 负责：提供商切换、API Key 加密存储、连接测试

package com.androidclaw.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AI 提供商管理器
 * 单例模式
 */
class AiProviderManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AiProviderManager"
        private const val KEYSTORE_ALIAS = "AndroidClaw_ApiKey_Key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private var INSTANCE: AiProviderManager? = null

        fun getInstance(context: Context): AiProviderManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AiProviderManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // DataStore for provider settings
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_provider_prefs")
    private val dataStore = context.dataStore

    // Current provider
    private var currentProvider: AiProvider? = null
    private var currentProviderType: AiProviderType = AiProviderType.LOCAL

    // Encryption (Android Keystore)
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    init {
        Log.i(TAG, "AiProviderManager initializing...")
        initializeEncryption()
    }

    /**
     * 初始化加密 (Android Keystore)
     */
    private fun initializeEncryption() {
        try {
            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                Log.i(TAG, "Generating encryption key")
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                Log.i(TAG, "Encryption key generated successfully")
            } else {
                Log.i(TAG, "Encryption key already exists")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize encryption", e)
        }
    }

    /**
     * 加密 API Key
     */
    private fun encryptApiKey(apiKey: String): String {
        return try {
            val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(apiKey.toByteArray(Charset.forName("UTF-8")))
            // 将 IV 和加密数据拼接，转 Base64
            val combined = iv + encryptedBytes
            android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt API key", e)
            throw e
        }
    }

    /**
     * 解密 API Key
     */
    private fun decryptApiKey(encryptedApiKey: String): String {
        return try {
            val combined = android.util.Base64.decode(encryptedApiKey, android.util.Base64.DEFAULT)
            val iv = combined.copyOfRange(0, 12) // GCM IV is 12 bytes
            val encryptedBytes = combined.copyOfRange(12, combined.size)
            val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charset.forName("UTF-8"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt API key", e)
            throw e
        }
    }

    /**
     * 保存 API Key (加密)
     */
    suspend fun saveApiKey(providerType: AiProviderType, apiKey: String) {
        Log.i(TAG, "Saving API key for $providerType")

        val encryptedKey = encryptApiKey(apiKey)
        val key = stringPreferencesKey("api_key_${providerType.name.lowercase()}")

        dataStore.edit { preferences ->
            preferences[key] = encryptedKey
        }

        Log.i(TAG, "API key saved successfully")
    }

    /**
     * 获取 API Key (解密)
     */
    suspend fun getApiKey(providerType: AiProviderType): String? {
        Log.i(TAG, "Getting API key for $providerType")

        val key = stringPreferencesKey("api_key_${providerType.name.lowercase()}")

        return try {
            val encryptedKey = dataStore.data.first()[key]
            if (encryptedKey != null) {
                decryptApiKey(encryptedKey)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get API key for $providerType", e)
            null
        }
    }

    /**
     * 设置 AI 提供商
     * @param type 提供商类型
     * @param apiKey  API Key (如果需要)
     * @param config  额外配置 (如 baseUrl, model)
     */
    suspend fun setProvider(
        type: AiProviderType,
        apiKey: String? = null,
        config: Map<String, String> = emptyMap()
    ): Boolean {
        Log.i(TAG, "Setting AI provider to: $type")

        return try {
            val provider = when (type) {
                AiProviderType.LOCAL -> {
                    // 使用 LLMManager 作为本地提供商
                    val llmManager = com.androidclaw.app.llm.LLMManager.getInstance(context)
                    LocalProvider(llmManager)
                }
                AiProviderType.OPENAI -> {
                    val key = apiKey ?: getApiKey(AiProviderType.OPENAI)
                    if (key == null) {
                        Log.e(TAG, "OpenAI API key not found")
                        return false
                    }
                    val baseUrl = config["baseUrl"] ?: "https://api.openai.com/v1"
                    val model = config["model"] ?: "gpt-4-turbo-preview"
                    OpenAiProvider(key, baseUrl, model)
                }
                AiProviderType.ANTHROPIC -> {
                    val key = apiKey ?: getApiKey(AiProviderType.ANTHROPIC)
                    if (key == null) {
                        Log.e(TAG, "Anthropic API key not found")
                        return false
                    }
                    val model = config["model"] ?: "claude-3-5-sonnet-20240620"
                    AnthropicProvider(key, model = model)
                }
                AiProviderType.GOOGLE -> {
                    val key = apiKey ?: getApiKey(AiProviderType.GOOGLE)
                    if (key == null) {
                        Log.e(TAG, "Google API key not found")
                        return false
                    }
                    val model = config["model"] ?: "gemini-1.5-pro"
                    GeminiProvider(key, model)
                }
                AiProviderType.CUSTOM -> {
                    val key = apiKey ?: getApiKey(AiProviderType.CUSTOM)
                    val baseUrl = config["baseUrl"]
                    if (key == null || baseUrl == null) {
                        Log.e(TAG, "Custom provider requires apiKey and baseUrl")
                        return false
                    }
                    // Custom 使用 OpenAI 兼容 API
                    OpenAiProvider(key, baseUrl)
                }
            }

            // 测试连接
            val connectionOk = provider.testConnection()
            if (!connectionOk && type != AiProviderType.LOCAL) {
                Log.w(TAG, "Provider connection test failed: $type")
                // 可以选择是否允许连接失败的提供商
                // return false
            }

            // 设置当前提供商
            currentProvider?.release()
            currentProvider = provider
            currentProviderType = type

            // 保存提供商类型到 DataStore
            val providerKey = stringPreferencesKey("current_provider")
            dataStore.edit { preferences ->
                preferences[providerKey] = type.name
            }

            Log.i(TAG, "AI provider set successfully: $type")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set provider: $type", e)
            false
        }
    }

    /**
     * 获取当前 AI 提供商
     */
    fun getCurrentProvider(): AiProvider {
        return currentProvider ?: throw IllegalStateException("No AI provider set")
    }

    /**
     * 获取当前提供商类型
     */
    fun getCurrentProviderType(): AiProviderType {
        return currentProviderType
    }

    /**
     * 测试当前提供商连接
     */
    suspend fun testCurrentProvider(): Boolean {
        Log.i(TAG, "Testing current provider: $currentProviderType")
        return currentProvider?.testConnection() ?: false
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing AiProviderManager")
        currentProvider?.release()
        currentProvider = null
    }
}
