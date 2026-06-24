// RequirementParser.kt
// Skill 创建器 - 需求理解与拆分
// 将用户的自然语言需求拆分成结构化的 Skill 规格说明

package com.androidclaw.app.skills.creator

import android.util.Log
import com.androidclaw.app.llm.LLMManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 需求解析器
 * 
 * 功能：
 * - 将用户的模糊需求拆分成结构化步骤
 * - 识别需要的 Android 权限
 * - 识别需要的 Android API
 * - 生成工具列表（ToolDefinition）
 * - 风险评估
 */
class RequirementParser(private val llmManager: LLMManager) {

    companion object {
        private const val TAG = "RequirementParser"
        
        // 常用权限映射
        val PERMISSION_KEYWORDS = mapOf(
            "相机" to android.Manifest.permission.CAMERA,
            "拍照" to android.Manifest.permission.CAMERA,
            "照片" to android.Manifest.permission.READ_EXTERNAL_STORAGE,
            "图片" to android.Manifest.permission.READ_EXTERNAL_STORAGE,
            "相册" to android.Manifest.permission.READ_EXTERNAL_STORAGE,
            "位置" to android.Manifest.permission.ACCESS_FINE_LOCATION,
            "定位" to android.Manifest.permission.ACCESS_FINE_LOCATION,
            "地图" to android.Manifest.permission.ACCESS_FINE_LOCATION,
            "联系人" to android.Manifest.permission.READ_CONTACTS,
            "通讯录" to android.Manifest.permission.READ_CONTACTS,
            "短信" to android.Manifest.permission.READ_SMS,
            "发送短信" to android.Manifest.permission.SEND_SMS,
            "电话" to android.Manifest.permission.CALL_PHONE,
            "拨号" to android.Manifest.permission.CALL_PHONE,
            "录音" to android.Manifest.permission.RECORD_AUDIO,
            "麦克风" to android.Manifest.permission.RECORD_AUDIO,
            "存储" to android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            "文件" to android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            "日历" to android.Manifest.permission.READ_CALENDAR,
            "日程" to android.Manifest.permission.READ_CALENDAR,
            "通知" to android.Manifest.permission.POST_NOTIFICATIONS,
            "蓝牙" to android.Manifest.permission.BLUETOOTH,
            "WiFi" to android.Manifest.permission.ACCESS_WIFI_STATE,
            "网络" to android.Manifest.permission.INTERNET
        )
        
        // 高危权限组合
        val DANGEROUS_COMBINATIONS = listOf(
            setOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.INTERNET
            ) to "短信读取+网络访问可能导致隐私泄露",
            setOf(
                android.Manifest.permission.READ_CONTACTS,
                android.Manifest.permission.INTERNET
            ) to "联系人读取+网络访问可能导致隐私泄露",
            setOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.INTERNET
            ) to "录音+网络访问可能用于窃听",
            setOf(
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.SEND_SMS
            ) to "短信读写组合可能用于短信劫持"
        )
        
        // Android API 关键词映射
        val API_KEYWORDS = mapOf(
            "应用" to listOf("PackageManager", "ApplicationInfo", "ActivityManager"),
            "安装" to listOf("PackageManager", "Intent.ACTION_INSTALL_PACKAGE"),
            "卸载" to listOf("Intent.ACTION_UNINSTALL_PACKAGE"),
            "通知" to listOf("NotificationManager", "NotificationCompat"),
            "剪贴板" to listOf("ClipboardManager", "ClipData"),
            "分享" to listOf("Intent.ACTION_SEND", "Intent.createChooser"),
            "音量" to listOf("AudioManager", "MediaStore"),
            "音乐" to listOf("MediaPlayer", "MediaController", "AudioManager"),
            "屏幕" to listOf("MediaProjection", "SurfaceControl"),
            "截屏" to listOf("MediaProjection", "ImageReader"),
            "浏览器" to listOf("Intent.ACTION_VIEW", "WebView"),
            "网页" to listOf("WebView", "HttpClient"),
            "闹钟" to listOf("AlarmManager", "PendingIntent"),
            "提醒" to listOf("AlarmManager", "NotificationManager"),
            "时间" to listOf("System.currentTimeMillis()", "Calendar"),
            "日期" to listOf("Calendar", "SimpleDateFormat"),
            "天气" to listOf("HttpURLConnection", "OkHttp", "JSONObject"),
            "快递" to listOf("HttpURLConnection", "OkHttp", "JSONObject"),
            "翻译" to listOf("HttpURLConnection", "OkHttp", "JSONObject"),
            "计算" to listOf("BigDecimal", "Math"),
            "二维码" to listOf("Intent", "VisionAPI", "ZXing"),
            "扫码" to listOf("CameraX", "VisionAPI", "ZXing")
        )
    }

    /**
     * 解析用户需求
     * 
     * @param userRequirement 用户的自然语言需求描述
     * @return 解析结果
     */
    suspend fun parse(userRequirement: String): ParsedRequirement = withContext(Dispatchers.IO) {
        Log.i(TAG, "Parsing requirement: ${userRequirement.take(100)}...")
        
        // 1. 使用 LLM 进行深度分析
        val llmAnalysis = analyzeWithLLM(userRequirement)
        
        // 2. 基于关键词识别权限
        val keywordPermissions = identifyPermissionsFromKeywords(userRequirement)
        
        // 3. 基于关键词识别 API
        val keywordApis = identifyApisFromKeywords(userRequirement)
        
        // 4. 合并结果
        val finalResult = mergeResults(llmAnalysis, keywordPermissions, keywordApis, userRequirement)
        
        Log.i(TAG, "Parsed: skillName=${finalResult.skillName}, tools=${finalResult.tools.size}, " +
                   "permissions=${finalResult.requiredPermissions.size}")
        
        finalResult
    }

    /**
     * 使用 LLM 分析需求
     */
    private suspend fun analyzeWithLLM(requirement: String): ParsedRequirement? {
        val prompt = buildAnalysisPrompt(requirement)
        
        return try {
            val response = llmManager.generateText(prompt)
            parseLLMResponse(response)
        } catch (e: Exception) {
            Log.w(TAG, "LLM analysis failed, falling back to heuristics", e)
            null
        }
    }

    /**
     * 构建分析提示词
     */
    private fun buildAnalysisPrompt(requirement: String): String {
        return """
你是一个 Android Skill 开发专家。请分析以下用户需求，并输出 JSON 格式的结构化信息。

## 用户需求
$requirement

## 输出格式
请输出以下 JSON 格式（不要包含 markdown 代码块标记）：
{
  "skillName": "skill_name_in_snake_case",
  "displayName": "显示名称",
  "description": "功能描述",
  "tools": [
    {
      "name": "tool_name",
      "description": "工具描述",
      "parameters": [
        {"name": "param1", "type": "string", "required": true, "description": "参数描述"}
      ],
      "returnType": "map"
    }
  ],
  "permissions": ["android.permission.XXX"],
  "androidApis": ["PackageManager", "NotificationManager"],
  "dataSources": ["网络API", "本地数据库"],
  "riskAssessment": "风险评估说明"
}

## 注意事项
1. skillName 使用 snake_case 格式，只包含字母、数字、下划线
2. 权限使用完整的 Android 权限字符串
3. 参数类型只能是: string, int, boolean, float, map, list
4. 如果涉及网络请求，需要添加 INTERNET 权限
5. 如果涉及敏感数据（位置、联系人、短信等），需要在风险评估中说明

请直接输出 JSON，不要有任何其他文字：
""".trimIndent()
    }

    /**
     * 解析 LLM 响应
     */
    private fun parseLLMResponse(response: String): ParsedRequirement? {
        return try {
            // 尝试提取 JSON
            val jsonStr = extractJson(response)
            val json = JSONObject(jsonStr)
            
            val tools = json.optJSONArray("tools")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val toolObj = arr.getJSONObject(i)
                    val params = toolObj.optJSONArray("parameters")?.let { pArr ->
                        (0 until pArr.length()).map { j ->
                            val pObj = pArr.getJSONObject(j)
                            ToolParameterSpec(
                                name = pObj.getString("name"),
                                type = pObj.getString("type"),
                                required = pObj.optBoolean("required", false),
                                description = pObj.getString("description")
                            )
                        }
                    } ?: emptyList()
                    
                    ToolSpec(
                        name = toolObj.getString("name"),
                        description = toolObj.getString("description"),
                        parameters = params,
                        returnType = toolObj.optString("returnType", "map")
                    )
                }
            } ?: emptyList()
            
            val permissions = json.optJSONArray("permissions")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            
            val apis = json.optJSONArray("androidApis")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            
            val dataSources = json.optJSONArray("dataSources")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            
            ParsedRequirement(
                skillName = json.getString("skillName"),
                displayName = json.getString("displayName"),
                description = json.getString("description"),
                requiredPermissions = permissions,
                androidApis = apis,
                tools = tools,
                dataSources = dataSources,
                riskAssessment = json.optString("riskAssessment", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse LLM response", e)
            null
        }
    }

    /**
     * 从响应中提取 JSON
     */
    private fun extractJson(response: String): String {
        // 尝试直接解析
        if (response.trim().startsWith("{")) {
            return response.trim()
        }
        
        // 尝试提取 markdown 代码块中的内容
        val jsonBlockPattern = Regex("""```(?:json)?\s*([\s\S]*?)```""")
        jsonBlockPattern.find(response)?.let { match ->
            return match.groupValues[1].trim()
        }
        
        // 尝试找到第一个 { 和最后一个 }
        val start = response.indexOf('{')
        val end = response.lastIndexOf('}')
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1)
        }
        
        return response
    }

    /**
     * 基于关键词识别权限
     */
    private fun identifyPermissionsFromKeywords(requirement: String): List<String> {
        val permissions = mutableSetOf<String>()
        
        for ((keyword, permission) in PERMISSION_KEYWORDS) {
            if (requirement.contains(keyword)) {
                permissions.add(permission)
            }
        }
        
        return permissions.toList()
    }

    /**
     * 基于关键词识别 API
     */
    private fun identifyApisFromKeywords(requirement: String): List<String> {
        val apis = mutableSetOf<String>()
        
        for ((keyword, apiList) in API_KEYWORDS) {
            if (requirement.contains(keyword)) {
                apis.addAll(apiList)
            }
        }
        
        return apis.toList()
    }

    /**
     * 合并结果
     */
    private fun mergeResults(
        llmResult: ParsedRequirement?,
        keywordPermissions: List<String>,
        keywordApis: List<String>,
        requirement: String
    ): ParsedRequirement {
        if (llmResult != null) {
            // 合并 LLM 结果和关键词识别结果
            val mergedPermissions = (llmResult.requiredPermissions + keywordPermissions).distinct()
            val mergedApis = (llmResult.androidApis + keywordApis).distinct()
            
            // 检查权限组合风险
            val riskAssessment = buildRiskAssessment(mergedPermissions, llmResult.riskAssessment)
            
            return llmResult.copy(
                requiredPermissions = mergedPermissions,
                androidApis = mergedApis,
                riskAssessment = riskAssessment
            )
        }
        
        // LLM 分析失败，使用启发式方法生成基础结构
        val skillName = generateSkillName(requirement)
        val displayName = generateDisplayName(requirement)
        
        return ParsedRequirement(
            skillName = skillName,
            displayName = displayName,
            description = requirement.take(200),
            requiredPermissions = keywordPermissions,
            androidApis = keywordApis,
            tools = generateBasicTools(requirement),
            dataSources = if (requirement.contains("网络") || requirement.contains("API")) {
                listOf("网络API")
            } else emptyList(),
            riskAssessment = buildRiskAssessment(keywordPermissions, "")
        )
    }

    /**
     * 构建风险评估
     */
    private fun buildRiskAssessment(permissions: List<String>, baseAssessment: String): String {
        val warnings = mutableListOf<String>()
        
        if (baseAssessment.isNotEmpty()) {
            warnings.add(baseAssessment)
        }
        
        // 检查高危权限组合
        for ((combo, warning) in DANGEROUS_COMBINATIONS) {
            if (combo.all { it in permissions }) {
                warnings.add("⚠️ $warning")
            }
        }
        
        // 检查敏感权限
        val sensitivePermissions = permissions.filter {
            it.contains("READ_SMS") || 
            it.contains("READ_CONTACTS") || 
            it.contains("RECORD_AUDIO") ||
            it.contains("CAMERA") ||
            it.contains("ACCESS_FINE_LOCATION")
        }
        
        if (sensitivePermissions.isNotEmpty()) {
            warnings.add("⚠️ 涉及敏感权限: ${sensitivePermissions.joinToString(", ")}")
        }
        
        return warnings.joinToString("\n")
    }

    /**
     * 生成 Skill 名称
     */
    private fun generateSkillName(requirement: String): String {
        // 简单的名称生成：提取关键词
        val keywords = requirement.split(Regex("[\\s,，。！？、]+"))
            .filter { it.length >= 2 }
            .take(3)
        
        return keywords.joinToString("_").lowercase()
            .replace(Regex("[^a-z0-9_]"), "")
            .take(30)
            .ifEmpty { "custom_skill" }
    }

    /**
     * 生成显示名称
     */
    private fun generateDisplayName(requirement: String): String {
        val firstSentence = requirement.split(Regex("[。！？\n]")).firstOrNull() ?: requirement
        return firstSentence.take(20).ifEmpty { "自定义 Skill" }
    }

    /**
     * 生成基础工具列表
     */
    private fun generateBasicTools(requirement: String): List<ToolSpec> {
        return listOf(
            ToolSpec(
                name = "execute",
                description = "执行主要功能",
                parameters = listOf(
                    ToolParameterSpec(
                        name = "input",
                        type = "string",
                        required = true,
                        description = "输入参数"
                    )
                ),
                returnType = "map"
            )
        )
    }
}

/**
 * 解析后的需求
 */
data class ParsedRequirement(
    val skillName: String,           // "express_tracker"
    val displayName: String,         // "快递查询"
    val description: String,         // "查询快递物流信息"
    val requiredPermissions: List<String>,
    val androidApis: List<String>,   // 需要的 Android API
    val tools: List<ToolSpec>,       // 工具规格
    val dataSources: List<String>,   // 数据源
    val riskAssessment: String       // 风险评估
)


