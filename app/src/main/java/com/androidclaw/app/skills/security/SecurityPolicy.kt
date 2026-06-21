// SecurityPolicy.kt
// 安全策略配置
// 定义安全扫描的阈值、开关和权限管理策略
// 支持用户自定义配置并持久化到 DataStore
//
// @security 关键安全操作不可被绕过
// @security 安全策略支持用户自定义并持久化

package com.androidclaw.app.skills.security

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "security_policy")

/**
 * 安全策略配置
 *
 * 控制安全扫描器的行为，包括：
 * - 风险阈值（自动批准/警告/阻止）
 * - 功能开关（未知来源/签名/联网）
 * - 权限管理白名单/黑名单
 * - 用户自定义覆盖决策
 */
data class SecurityPolicy(
    // 阈值配置
    val maxRiskScoreForAutoApprove: Int = 20,
    val warningThreshold: Int = 40,
    val blockThreshold: Int = 70,

    // 开关
    val blockUnknownSources: Boolean = true,
    val requireManifestSignature: Boolean = true,
    val allowNetworkAccess: Boolean = false,
    val enableCommunityReputation: Boolean = true,

    // 权限白名单
    val allowedPermissions: Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.VIBRATE",
        "android.permission.WAKE_LOCK",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.RECEIVE_BOOT_COMPLETED"
    ),

    // 权限黑名单（绝对禁止）
    val blockedPermissions: Set<String> = setOf(
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_MMS",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.REQUEST_INSTALL_PACKAGES",
        "android.permission.MANAGE_EXTERNAL_STORAGE"
    ),

    // 用户自定义覆盖
    val userOverrides: Map<String, UserDecision> = emptyMap()
)

/**
 * 用户决策
 */
enum class UserDecision { ALLOW, BLOCK, IGNORE }

/**
 * 用户决策记录
 */
data class UserDecisionRecord(
    val skillId: String,
    val decision: UserDecision,
    val timestamp: Long,
    val reason: String? = null
)

/**
 * 安全策略管理器
 * 负责持久化安全策略配置并提供运行时访问
 */
class SecurityPolicyManager(private val context: Context) {

    companion object {
        private const val TAG = "SecurityPolicyManager"

        private val KEY_MAX_RISK = intPreferencesKey("max_risk_score")
        private val KEY_WARN_THRESHOLD = intPreferencesKey("warning_threshold")
        private val KEY_BLOCK_THRESHOLD = intPreferencesKey("block_threshold")
        private val KEY_BLOCK_UNKNOWN = booleanPreferencesKey("block_unknown_sources")
        private val KEY_REQUIRE_SIG = booleanPreferencesKey("require_manifest_signature")
        private val KEY_ALLOW_NETWORK = booleanPreferencesKey("allow_network_access")
        private val KEY_ENABLE_REPUTATION = booleanPreferencesKey("enable_community_reputation")
        private val KEY_USER_OVERRIDES = stringPreferencesKey("user_overrides_json")

        private var INSTANCE: SecurityPolicyManager? = null

        fun getInstance(context: Context): SecurityPolicyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityPolicyManager(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }

    /**
     * 获取安全策略（带持久化状态）
     */
    val securityPolicy: Flow<SecurityPolicy> = context.dataStore.data.map { prefs ->
        SecurityPolicy(
            maxRiskScoreForAutoApprove = prefs[KEY_MAX_RISK] ?: 20,
            warningThreshold = prefs[KEY_WARN_THRESHOLD] ?: 40,
            blockThreshold = prefs[KEY_BLOCK_THRESHOLD] ?: 70,
            blockUnknownSources = prefs[KEY_BLOCK_UNKNOWN] ?: true,
            requireManifestSignature = prefs[KEY_REQUIRE_SIG] ?: true,
            allowNetworkAccess = prefs[KEY_ALLOW_NETWORK] ?: false,
            enableCommunityReputation = prefs[KEY_ENABLE_REPUTATION] ?: true,
            userOverrides = parseUserOverrides(prefs[KEY_USER_OVERRIDES])
        )
    }

    /**
     * 获取当前策略的快照
     */
    suspend fun getCurrentPolicy(): SecurityPolicy {
        return securityPolicy.first()
    }

    /**
     * 更新阈值配置
     */
    suspend fun updateThresholds(
        maxRiskScore: Int? = null,
        warningThreshold: Int? = null,
        blockThreshold: Int? = null
    ) {
        Log.w(TAG, "Updating security thresholds: autoApprove=$maxRiskScore, " +
                "warning=$warningThreshold, block=$blockThreshold")

        context.dataStore.edit { prefs ->
            maxRiskScore?.let { prefs[KEY_MAX_RISK] = it }
            warningThreshold?.let { prefs[KEY_WARN_THRESHOLD] = it }
            blockThreshold?.let { prefs[KEY_BLOCK_THRESHOLD] = it }
        }
    }

    /**
     * 更新开关设置
     */
    suspend fun updateSwitches(
        blockUnknownSources: Boolean? = null,
        requireManifestSignature: Boolean? = null,
        allowNetworkAccess: Boolean? = null,
        enableCommunityReputation: Boolean? = null
    ) {
        context.dataStore.edit { prefs ->
            blockUnknownSources?.let { prefs[KEY_BLOCK_UNKNOWN] = it }
            requireManifestSignature?.let { prefs[KEY_REQUIRE_SIG] = it }
            allowNetworkAccess?.let { prefs[KEY_ALLOW_NETWORK] = it }
            enableCommunityReputation?.let { prefs[KEY_ENABLE_REPUTATION] = it }
        }
    }

    /**
     * 设置用户对某个 Skill 的决策覆盖
     */
    suspend fun setUserOverride(skillId: String, decision: UserDecision) {
        Log.w(TAG, "Setting user override: $skillId -> $decision")

        val currentPolicy = getCurrentPolicy()
        val newOverrides = currentPolicy.userOverrides.toMutableMap()
        newOverrides[skillId] = decision

        context.dataStore.edit { prefs ->
            prefs[KEY_USER_OVERRIDES] = serializeUserOverrides(newOverrides)
        }
    }

    /**
     * 移除用户对某个 Skill 的决策覆盖
     */
    suspend fun removeUserOverride(skillId: String) {
        Log.w(TAG, "Removing user override for: $skillId")

        val currentPolicy = getCurrentPolicy()
        val newOverrides = currentPolicy.userOverrides.toMutableMap()
        newOverrides.remove(skillId)

        context.dataStore.edit { prefs ->
            prefs[KEY_USER_OVERRIDES] = serializeUserOverrides(newOverrides)
        }
    }

    /**
     * 获取针对特定 Skill 的用户决策（如果有）
     */
    suspend fun getUserDecision(skillId: String): UserDecision? {
        val policy = getCurrentPolicy()
        return policy.userOverrides[skillId]
    }

    /**
     * 检查权限是否在阻塞列表中
     */
    fun isPermissionBlocked(permission: String, policy: SecurityPolicy): Boolean {
        return policy.blockedPermissions.any { blocked ->
            permission.equals(blocked, ignoreCase = true) ||
                    permission.endsWith(blocked.substringAfterLast("."), ignoreCase = true)
        }
    }

    /**
     * 检查权限是否在白名单中
     */
    fun isPermissionAllowed(permission: String, policy: SecurityPolicy): Boolean {
        return policy.allowedPermissions.any { allowed ->
            permission.equals(allowed, ignoreCase = true) ||
                    permission.endsWith(allowed.substringAfterLast("."), ignoreCase = true)
        }
    }

    /**
     * 检查是否有用户覆盖
     */
    suspend fun checkUserOverride(skillId: String, policy: SecurityPolicy): UserDecision? {
        // 先检查精确匹配
        policy.userOverrides[skillId]?.let { return it }

        // 没有覆盖
        return null
    }

    /**
     * 重置为默认策略
     */
    suspend fun resetToDefault() {
        Log.w(TAG, "Resetting security policy to defaults")

        context.dataStore.edit { it.clear() }
    }

    // ===== JSON 序列化辅助 =====

    private fun serializeUserOverrides(overrides: Map<String, UserDecision>): String {
        val json = org.json.JSONObject()
        for ((key, value) in overrides) {
            json.put(key, value.name)
        }
        return json.toString()
    }

    private fun parseUserOverrides(jsonStr: String?): Map<String, UserDecision> {
        if (jsonStr.isNullOrBlank()) return emptyMap()

        return try {
            val json = org.json.JSONObject(jsonStr)
            val result = mutableMapOf<String, UserDecision>()
            for (key in json.keys()) {
                try {
                    result[key] = UserDecision.valueOf(json.getString(key))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse user override: $key", e)
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse user overrides JSON", e)
            emptyMap()
        }
    }
}
