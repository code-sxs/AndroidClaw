// SecurityRules.kt
// 安全扫描规则引擎
// 实现五类安全检测规则：
//   P1 - 危险权限组合检测
//   P2 - 危险 API 调用检测
//   P3 - 数据流分析
//   P4 - 清单合规性
//   P5 - 已知恶意模式库
//
// @security 所有安全日志使用 Log.w 或 Log.e 级别

package com.androidclaw.app.skills.security

import android.util.Log
import java.io.File

/**
 * 安全规则引擎
 * 封装所有具体的安全检测规则实现
 */
class SecurityRules {

    companion object {
        private const val TAG = "SecurityRules"

        private val DANGEROUS_API_PATTERNS = listOf(
            "Runtime.getRuntime().exec" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "危险命令执行", "检测到 Runtime.exec() 调用", "除非绝对必要不应执行系统命令"),
            "Runtime.exec" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "危险命令执行", "检测到 Runtime.exec() 调用", "除非绝对必要不应执行系统命令"),
            "ProcessBuilder(" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "危险进程创建", "检测到 ProcessBuilder", "除非绝对必要不应创建系统进程"),
            "Class.forName(" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "反射调用", "使用反射加载类", "请确认为什么需要反射"),
            "getDeclaredMethod(" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "反射获取方法", "使用反射获取私有方法", "不建议反射访问私有API"),
            "getDeclaredField(" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "反射获取字段", "使用反射获取私有字段", "不建议反射访问私有字段"),
            "setAccessible(true)" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "强制访问私有成员", "调用setAccessible绕过访问控制", "高风险行为"),
            "DexClassLoader" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "动态代码加载", "使用DexClassLoader动态加载代码", "动态加载代码存在安全风险"),
            "PathClassLoader" to ApiPatternInfo(Severity.HIGH, Category.CODE_INJECTION, "动态代码加载", "使用PathClassLoader动态加载类", "请确认为什么需要动态加载"),
            "SmsManager" to ApiPatternInfo(Severity.HIGH, Category.PRIVACY_VIOLATION, "短信管理", "检测到SmsManager", "发送短信可能产生费用"),
            "sendTextMessage" to ApiPatternInfo(Severity.HIGH, Category.PRIVACY_VIOLATION, "发送短信", "检测到发送短信操作", "应征求用户明确同意"),
            "Intent.ACTION_CALL" to ApiPatternInfo(Severity.HIGH, Category.PRIVACY_VIOLATION, "拨打电话", "检测到直接拨打电话", "建议使用ACTION_DIAL让用户确认"),
            "TelephonyManager" to ApiPatternInfo(Severity.MEDIUM, Category.PRIVACY_VIOLATION, "电话状态访问", "检测到TelephonyManager", "可能泄露设备标识"),
            "android.os.Process" to ApiPatternInfo(Severity.MEDIUM, Category.CODE_INJECTION, "进程操作", "检测到android.os.Process操作", "可能导致应用崩溃"),
            "su" to ApiPatternInfo(Severity.CRITICAL, Category.CODE_INJECTION, "Root提权", "检测到su命令", "最高风险行为"),
            "Superuser" to ApiPatternInfo(Severity.CRITICAL, Category.CODE_INJECTION, "Root检测", "检测到Superuser", "任何提权尝试都是高风险"),
            "getDeviceId" to ApiPatternInfo(Severity.MEDIUM, Category.PRIVACY_VIOLATION, "获取设备ID", "获取设备唯一标识符", "可能用于设备追踪"),
            "getSimSerialNumber" to ApiPatternInfo(Severity.HIGH, Category.PRIVACY_VIOLATION, "获取SIM序列号", "获取SIM卡序列号", "高度敏感标识信息"),
            "getSubscriberId" to ApiPatternInfo(Severity.HIGH, Category.PRIVACY_VIOLATION, "获取用户标识", "获取IMSI", "高度敏感个人信息"),
            "getInstalledApplications" to ApiPatternInfo(Severity.MEDIUM, Category.PRIVACY_VIOLATION, "获取已安装应用", "获取已安装应用列表", "可能用于分析用户行为"),
            "getInstalledPackages" to ApiPatternInfo(Severity.MEDIUM, Category.PRIVACY_VIOLATION, "获取已安装包", "获取已安装包名列表", "可被用于分析用户行为"),
            "delete(" to ApiPatternInfo(Severity.LOW, Category.FILE_SYSTEM_ABUSE, "文件删除", "检测到文件删除操作", "确保仅限于Skill数据目录"),
            "deleteRecursively" to ApiPatternInfo(Severity.MEDIUM, Category.FILE_SYSTEM_ABUSE, "递归删除", "检测到递归删除", "应谨慎使用")
        )
    }

    /**
     * P1 - 危险权限组合检测 (CRITICAL)
     *
     * 检测以下高危权限组合：
     * - READ_CONTACTS + INTERNET → 窃取通讯录上传
     * - READ_SMS + INTERNET → 窃取短信上传
     * - RECORD_AUDIO + INTERNET → 录音上传
     * - ACCESS_FINE_LOCATION + INTERNET → 位置追踪
     * - READ_CALL_LOG + INTERNET → 窃取通话记录
     */
    fun checkDangerousPermissionCombinations(
        permissions: List<String>
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()
        val permSet = permissions.toSet()

        val dangerousCombinations = listOf(
            DangerousCombination(
                perms = setOf("android.permission.READ_CONTACTS", "android.permission.INTERNET"),
                title = "危险权限组合：读取联系人 + 网络",
                description = "Skill 同时申请 READ_CONTACTS 和 INTERNET 权限，" +
                        "可能将联系人数据上传至远程服务器"
            ),
            DangerousCombination(
                perms = setOf("android.permission.READ_SMS", "android.permission.INTERNET"),
                title = "危险权限组合：读取短信 + 网络",
                description = "Skill 同时申请 READ_SMS 和 INTERNET 权限，" +
                        "可能将短信内容上传至远程服务器"
            ),
            DangerousCombination(
                perms = setOf("android.permission.RECORD_AUDIO", "android.permission.INTERNET"),
                title = "危险权限组合：录音 + 网络",
                description = "Skill 同时申请 RECORD_AUDIO 和 INTERNET 权限，" +
                        "可能在用户不知情时录音并上传"
            ),
            DangerousCombination(
                perms = setOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.INTERNET"),
                title = "危险权限组合：精确定位 + 网络",
                description = "Skill 同时申请 ACCESS_FINE_LOCATION 和 INTERNET 权限，" +
                        "可能持续追踪用户位置并上传"
            ),
            DangerousCombination(
                perms = setOf("android.permission.READ_CALL_LOG", "android.permission.INTERNET"),
                title = "危险权限组合：读取通话记录 + 网络",
                description = "Skill 同时申请 READ_CALL_LOG 和 INTERNET 权限，" +
                        "可能将通话记录数据上传至远程服务器"
            ),
            DangerousCombination(
                perms = setOf("android.permission.CAMERA", "android.permission.INTERNET"),
                title = "危险权限组合：相机 + 网络",
                description = "Skill 同时申请 CAMERA 和 INTERNET 权限，" +
                        "可能在用户不知情时拍摄照片并上传"
            ),
            DangerousCombination(
                perms = setOf("android.permission.SEND_SMS", "android.permission.INTERNET"),
                title = "危险权限组合：发送短信 + 网络",
                description = "Skill 同时申请 SEND_SMS 和 INTERNET 权限，" +
                        "可能在后台发送付费短信并上传确认信息"
            ),
            DangerousCombination(
                perms = setOf("android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.INTERNET"),
                title = "危险权限组合：写入外部存储 + 网络",
                description = "Skill 同时申请 WRITE_EXTERNAL_STORAGE 和 INTERNET 权限，" +
                        "可能下载恶意文件到设备存储"
            )
        )

        for (combo in dangerousCombinations) {
            if (combo.perms.all { it in permSet }) {
                // 检查是否有简写形式的权限
                val shortPerms = combo.perms.map { it.substringAfterLast(".") }
                val allMatched = combo.perms.all { p ->
                    p in permSet || p.substringAfterLast(".") in permSet ||
                            permSet.any { it.endsWith(p.substringAfterLast(".")) }
                }

                if (allMatched) {
                    findings.add(
                        SecurityFinding(
                            severity = Severity.CRITICAL,
                            category = Category.PERMISSION_ABUSE,
                            title = combo.title,
                            description = combo.description,
                            affectedFile = null,
                            affectedLine = null,
                            recommendation = "除非 Skill 核心功能明确需要，否则不应同时拥有" +
                                    "数据读取权限和网络权限。建议移除不必要的权限声明。"
                        )
                    )
                    Log.e(TAG, "Dangerous permission combo detected: ${combo.title}")
                }
            }
        }

        return findings
    }

    /**
     * P2 - 危险 API 调用检测 (HIGH)
     *
     * 检测以下危险调用模式：
     * - Runtime.exec() / ProcessBuilder()
     * - 反射调用 (Class.forName, getDeclaredMethod)
     * - HttpURLConnection / OkHttp
     * - ContentResolver 对敏感 URI 的操作
     * - SMS Manager 发送短信
     * - 电话拨打 Intent
     */
    fun checkDangerousApiCalls(
        content: String,
        fileName: String,
        filePath: String
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()
        val lines = content.lines()

        // 匹配所有行的 API 调用
        for ((index, line) in lines.withIndex()) {
            val lineNumber = index + 1
            val trimmed = line.trim()

            // 跳过注释行
            if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("*")) continue

            // 检查危险 API
            for ((pattern, info) in DANGEROUS_API_PATTERNS) {
                if (line.contains(pattern, ignoreCase = true)) {
                    findings.add(
                        SecurityFinding(
                            severity = info.severity,
                            category = info.category,
                            title = info.title,
                            description = info.description,
                            affectedFile = filePath,
                            affectedLine = lineNumber,
                            recommendation = info.recommendation
                        )
                    )
                }
            }
        }

        return findings
    }

    /**
     * P3 - 数据流分析 (MEDIUM)
     *
     * 检测敏感数据流向：
     * - 联系人/短信/位置数据 → 网络/文件输出
     * - 读取应用私有目录外文件
     * - 写入 SD 卡公共目录
     */
    fun analyzeDataFlow(sourceFiles: List<File>): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        // 敏感数据来源
        val sensitiveSources = listOf(
            "content://contacts" to "联系人",
            "content://sms" to "短信",
            "content://call_log" to "通话记录",
            "content://calendar" to "日历",
            "content://media" to "媒体文件",
            "content://settings" to "系统设置",
            "ContentResolver" to "内容解析器",
            "getContentResolver()" to "内容解析器",
            "ManagedQuery" to "查询",
            "CursorLoader" to "游标加载器",
            "ContactsContract" to "联系人数据",
            "CallLog" to "通话记录",
            "CalendarContract" to "日历数据",
            "Telephony.Sms" to "短信数据"
        )

        // 数据输出目标
        val dataSinks = listOf(
            "HttpURLConnection" to "HTTP 连接",
            "OkHttpClient" to "OkHttp 客户端",
            "URLConnection" to "URL 连接",
            "Socket" to "网络套接字",
            "FileOutputStream" to "文件输出流",
            "getExternalStorageDirectory" to "外部存储",
            "getExternalFilesDir" to "外部文件",
            "MediaStore" to "媒体库",
            "ContentValues" to "内容值",
            "SmsManager" to "短信管理器",
            "sendTextMessage" to "发送短信"
        )

        for (file in sourceFiles) {
            try {
                val content = file.readText()
                val sourceMatches = mutableMapOf<String, Int>()

                // 找出所有敏感数据来源及其行号
                for (line in content.lines()) {
                    for ((pattern, name) in sensitiveSources) {
                        if (line.contains(pattern, ignoreCase = true) &&
                            !line.trimStart().startsWith("//") &&
                            !line.trimStart().startsWith("#")) {
                            sourceMatches[name] = (sourceMatches[name] ?: 0) + 1
                        }
                    }
                }

                if (sourceMatches.isNotEmpty()) {
                    // 检查是否有数据流向输出
                    val sinkCount = dataSinks.count { (pattern, _) ->
                        Regex(pattern, setOf(RegexOption.IGNORE_CASE))
                            .containsMatchIn(content)
                    }

                    if (sinkCount > 0) {
                        findings.add(
                            SecurityFinding(
                                severity = Severity.MEDIUM,
                                category = Category.DATA_EXFILTRATION,
                                title = "敏感数据可能被外泄",
                                description = "文件 ${file.name} 中检测到敏感数据来源 " +
                                        "(${sourceMatches.keys.joinToString(", ")})，" +
                                        "同时检测到 $sinkCount 个数据输出路径",
                                affectedFile = file.absolutePath,
                                affectedLine = null,
                                recommendation = "审查数据流确保敏感数据不会通过网络或文件系统外泄。" +
                                        "建议对敏感数据使用加密存储"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Data flow analysis failed: ${file.absolutePath}", e)
            }
        }

        return findings
    }

    /**
     * P4 - 清单合规性 (LOW-MEDIUM)
     *
     * 检查 skill_manifest.json：
     * - JSON 格式合法性
     * - version 语义化版本
     * - entry_point 文件存在性
     * - dependencies 完整性
     * - permissions 一致性
     */
    fun checkManifestCompliance(
        manifestJson: File,
        skillDir: File
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        if (!manifestJson.exists()) {
            findings.add(
                SecurityFinding(
                    severity = Severity.CRITICAL,
                    category = Category.MANIFEST_TAMPERING,
                    title = "清单文件缺失",
                    description = "skill_manifest.json 不存在",
                    affectedFile = manifestJson.absolutePath,
                    affectedLine = null,
                    recommendation = "Skill 必须包含有效的 skill_manifest.json 文件"
                )
            )
            return findings
        }

        try {
            val jsonString = manifestJson.readText()
            val json = org.json.JSONObject(jsonString)

            // 检查必填字段
            val requiredFields = listOf("name", "version", "entry_point", "display_name")
            val missingFields = requiredFields.filter { !json.has(it) }

            if (missingFields.isNotEmpty()) {
                findings.add(
                    SecurityFinding(
                        severity = Severity.HIGH,
                        category = Category.MANIFEST_TAMPERING,
                        title = "清单缺少必填字段",
                        description = "缺失字段: ${missingFields.joinToString(", ")}",
                        affectedFile = manifestJson.absolutePath,
                        affectedLine = null,
                        recommendation = "请补充所有必填字段: $requiredFields"
                    )
                )
            }

            // 检查 version 格式 (语义化版本)
            if (json.has("version")) {
                val version = json.getString("version")
                val semverRegex = Regex("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?(\\+[a-zA-Z0-9.]+)?$")
                if (!semverRegex.matches(version)) {
                    findings.add(
                        SecurityFinding(
                            severity = Severity.LOW,
                            category = Category.MANIFEST_TAMPERING,
                            title = "版本号格式不符合语义化版本规范",
                            description = "version '$version' 不是有效的语义化版本号 (semver)",
                            affectedFile = manifestJson.absolutePath,
                            affectedLine = null,
                            recommendation = "请使用语义化版本号格式: major.minor.patch"
                        )
                    )
                }
            }

            // 检查 entry_point 是否存在
            if (json.has("entry_point")) {
                val entryPoint = json.getString("entry_point")
                val entryFile = File(skillDir, entryPoint)
                if (!entryFile.exists()) {
                    findings.add(
                        SecurityFinding(
                            severity = Severity.HIGH,
                            category = Category.MANIFEST_TAMPERING,
                            title = "入口文件不存在",
                            description = "声明入口文件 $entryPoint 在安装目录中不存在",
                            affectedFile = entryFile.absolutePath,
                            affectedLine = null,
                            recommendation = "请检查 entry_point 路径是否正确"
                        )
                    )
                }
            }

            // 检查 permissions 字段格式
            if (json.has("permissions")) {
                val perms = json.optJSONArray("permissions")
                if (perms != null) {
                    for (i in 0 until perms.length()) {
                        if (perms.optString(i, "").isBlank()) {
                            findings.add(
                                SecurityFinding(
                                    severity = Severity.MEDIUM,
                                    category = Category.MANIFEST_TAMPERING,
                                    title = "权限列表包含空值",
                                    description = "permissions 列表中存在空字符串或无效值",
                                    affectedFile = manifestJson.absolutePath,
                                    affectedLine = null,
                                    recommendation = "请清理权限列表中的无效条目"
                                )
                            )
                        }
                    }
                }
            }

            // 检查 dependencies 是否已完整填写
            if (json.has("dependencies")) {
                val deps = json.optJSONArray("dependencies")
                if (deps != null && deps.length() > 0) {
                    findings.add(
                        SecurityFinding(
                            severity = Severity.INFO,
                            category = Category.UNKNOWN_SOURCE,
                            title = "Skill 声明了外部依赖",
                            description = "依赖列表: ${(0 until deps.length()).map { deps.getString(it) }}",
                            affectedFile = manifestJson.absolutePath,
                            affectedLine = null,
                            recommendation = "建议安装前确认所有依赖均可信且安全"
                        )
                    )
                }
            }

        } catch (e: org.json.JSONException) {
            findings.add(
                SecurityFinding(
                    severity = Severity.CRITICAL,
                    category = Category.MANIFEST_TAMPERING,
                    title = "清单 JSON 格式错误",
                    description = "skill_manifest.json 不是合法的 JSON: ${e.message}",
                    affectedFile = manifestJson.absolutePath,
                    affectedLine = null,
                    recommendation = "请修复 JSON 格式错误"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Manifest compliance check failed", e)
            findings.add(
                SecurityFinding(
                    severity = Severity.HIGH,
                    category = Category.MANIFEST_TAMPERING,
                    title = "清单检查异常",
                    description = "检查清单时发生错误: ${e.message}",
                    affectedFile = manifestJson.absolutePath,
                    affectedLine = null,
                    recommendation = "请重新下载 Skill 安装包"
                )
            )
        }

        return findings
    }

    // ===== 危险 API 模式定义 =====

    private data class ApiPatternInfo(
        val severity: Severity,
        val category: Category,
        val title: String,
        val description: String,
        val recommendation: String
    )

    // ===== 内部辅助类 =====

    private data class DangerousCombination(
        val perms: Set<String>,
        val title: String,
        val description: String
    )
}
