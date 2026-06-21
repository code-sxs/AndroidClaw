// ReputationClient.kt
// 社区信誉系统接口
// 提供对 Skill 社区信誉评分的访问能力
// 包括：平均评分、安装量、举报次数、作者信誉等
//
// @security 启用社区信誉系统可帮助识别恶意 Skill

package com.androidclaw.app.skills.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Skill 社区信誉信息
 */
data class SkillReputation(
    val skillId: String,
    val avgRating: Float,
    val totalReviews: Int,
    val totalInstalls: Int,
    val reportCount: Int,
    val lastScanDate: String,
    val authorReputation: Float,
    val isVerifiedAuthor: Boolean
)

/**
 * 信誉风险等级
 */
enum class ReputationRisk {
    SAFE,           // 信誉良好
    LOW_RISK,       // 信誉一般
    MEDIUM_RISK,    // 有潜在问题
    HIGH_RISK,      // 高风险
    UNKNOWN         // 新技能/无法获取
}

/**
 * 社区信誉客户端
 * 连接社区信誉服务器查询 Skill 信誉信息
 */
class ReputationClient(private val context: Context) {

    companion object {
        private const val TAG = "ReputationClient"
        private const val DEFAULT_API_BASE = "https://clawhub.ai/api/v1"
        private const val TIMEOUT_MS = 5000

        private var INSTANCE: ReputationClient? = null

        fun getInstance(context: Context): ReputationClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReputationClient(context.applicationContext)
                    .also { INSTANCE = it }
            }
        }
    }

    /**
     * 获取 Skill 社区信誉信息
     *
     * @param skillId     Skill ID
     * @param apiBaseUrl  可选的 API 基础地址
     * @return SkillReputation 或 null（无法获取时）
     */
    suspend fun getSkillReputation(
        skillId: String,
        apiBaseUrl: String = DEFAULT_API_BASE
    ): SkillReputation? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$apiBaseUrl/skills/reputation?id=$skillId")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"

            return@withContext if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                parseReputationResponse(skillId, JSONObject(response))
            } else {
                Log.w(TAG, "Reputation API returned ${connection.responseCode} for $skillId")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch reputation for $skillId", e)
            null
        }
    }

    /**
     * 批量获取多个 Skill 的信誉信息
     */
    suspend fun getBatchReputation(
        skillIds: List<String>,
        apiBaseUrl: String = DEFAULT_API_BASE
    ): Map<String, SkillReputation> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, SkillReputation>()

        try {
            val idsParam = skillIds.joinToString(",")
            val url = URL("$apiBaseUrl/skills/reputation/batch?ids=$idsParam")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonArray = org.json.JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val sid = item.getString("id")
                    results[sid] = parseReputationResponse(sid, item)
                }
            } else {
                Log.w(TAG, "Batch reputation API returned ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch batch reputation", e)
        }

        results
    }

    /**
     * 获取信誉风险等级
     */
    fun getRiskLevel(reputation: SkillReputation?): ReputationRisk {
        if (reputation == null) return ReputationRisk.UNKNOWN

        return when {
            // 被多次举报
            reputation.reportCount > 10 -> ReputationRisk.HIGH_RISK
            reputation.reportCount > 3 -> ReputationRisk.MEDIUM_RISK

            // 评分过低
            reputation.avgRating < 2.0f && reputation.totalReviews > 5 -> ReputationRisk.HIGH_RISK
            reputation.avgRating < 3.0f && reputation.totalReviews > 10 -> ReputationRisk.MEDIUM_RISK

            // 作者信誉低
            reputation.authorReputation < 2.0f -> ReputationRisk.MEDIUM_RISK

            // 新技能/数据不足
            reputation.totalInstalls < 10 && !reputation.isVerifiedAuthor -> ReputationRisk.LOW_RISK

            // 认证作者 + 高评分 + 良好安装量
            reputation.isVerifiedAuthor && reputation.avgRating >= 4.0f &&
                    reputation.totalReviews > 20 -> ReputationRisk.SAFE

            // 默认
            reputation.reportCount == 0 && reputation.avgRating >= 3.5f -> ReputationRisk.SAFE

            else -> ReputationRisk.LOW_RISK
        }
    }

    /**
     * 举报恶意 Skill
     *
     * @param skillId     Skill ID
     * @param reason      举报原因
     * @param evidence    证据描述
     * @param apiBaseUrl  可选的 API 基础地址
     * @return 是否提交成功
     */
    suspend fun reportMaliciousSkill(
        skillId: String,
        reason: String,
        evidence: String? = null,
        apiBaseUrl: String = DEFAULT_API_BASE
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$apiBaseUrl/skills/report")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")

            val jsonBody = JSONObject().apply {
                put("skill_id", skillId)
                put("reason", reason)
                evidence?.let { put("evidence", it) }
            }

            connection.outputStream.write(jsonBody.toString().toByteArray())

            val success = connection.responseCode in 200..299
            if (success) {
                Log.w(TAG, "Successfully reported skill: $skillId")
            } else {
                Log.e(TAG, "Failed to report skill: $skillId, code=${connection.responseCode}")
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report skill: $skillId", e)
            false
        }
    }

    /**
     * 解析信誉 API 响应
     */
    private fun parseReputationResponse(skillId: String, json: JSONObject): SkillReputation {
        return SkillReputation(
            skillId = skillId,
            avgRating = json.optDouble("avg_rating", 0.0).toFloat(),
            totalReviews = json.optInt("total_reviews", 0),
            totalInstalls = json.optInt("total_installs", 0),
            reportCount = json.optInt("report_count", 0),
            lastScanDate = json.optString("last_scan_date", ""),
            authorReputation = json.optDouble("author_reputation", 0.0).toFloat(),
            isVerifiedAuthor = json.optBoolean("is_verified_author", false)
        )
    }
}
