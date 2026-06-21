// SkillSecurityScanner.kt
// Skill 安全扫描器核心
// 负责对第三方 Skill 进行全面的安全扫描，包括静态代码分析、权限风险评估、
// 清单校验、网络行为检测和数据外泄检测
//
// 安全扫描器使用分层检测策略：
//   1. 预扫描检查 (文件大小、格式、签名)
//   2. 清单完整性校验 (skill_manifest.json)
//   3. 静态代码分析 (危险 API 调用检测)
//   4. 权限风险评估 (权限组合 + 权限强度)
//   5. 网络行为检测 (联网代码识别)
//   6. 数据外泄检测 (文件写入/发送检测)
//
// @security 所有扫描结果持久化到 Room 数据库，支持历史查询

package com.androidclaw.app.skills.security

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * 安全扫描器 - 单例模式
 * 集成所有安全检测规则，对第三方 Skill 进行全方位安全评估
 */
class SkillSecurityScanner private constructor() {

    companion object {
        private const val TAG = "SkillSecurity"
        private const val MAX_SKILL_SIZE_MB = 100L
        private const val MAX_SKILL_SIZE_BYTES = MAX_SKILL_SIZE_MB * 1024 * 1024
        private const val MAX_FILE_COUNT = 5000
        private const val MAX_SINGLE_FILE_SIZE_MB = 50L

        private var INSTANCE: SkillSecurityScanner? = null

        fun getInstance(): SkillSecurityScanner {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SkillSecurityScanner().also { INSTANCE = it }
            }
        }
    }

    private val rulesEngine = SecurityRules()
    private val knownMalwareHashSet = mutableSetOf<String>()

    init {
        Log.i(TAG, "SkillSecurityScanner initializing")
        loadKnownMalwareHashes()
    }

    /**
     * 加载已知恶意模式哈希库
     */
    private fun loadKnownMalwareHashes() {
        knownMalwareHashSet.addAll(KnownMalwarePatterns.builtInHashes)
        Log.i(TAG, "Loaded ${knownMalwareHashSet.size} known malware hashes")
    }

    /**
     * 执行完整安全扫描
     *
     * @param skillDir       Skill 安装目录
     * @param manifestJson   skill_manifest.json 文件路径
     * @param policy         安全策略配置
     * @return ScanResult    扫描结果
     */
    suspend fun scanSkill(
        skillDir: File,
        manifestJson: File?,
        policy: SecurityPolicy = SecurityPolicy()
    ): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.nanoTime()
        val findings = mutableListOf<SecurityFinding>()
        val skillName = manifestJson?.let { parseSkillName(it) } ?: skillDir.name
        var scannedFiles = 0

        Log.w(TAG, "Starting security scan for skill: $skillName")

        try {
            // Phase 1: 预扫描检查
            Log.w(TAG, "[Phase 1] Pre-scan checks for: $skillName")
            val preScanFindings = performPreScan(skillDir, policy)
            findings.addAll(preScanFindings)

            if (hasBlockedIssues(preScanFindings)) {
                Log.e(TAG, "Pre-scan FAILED for: $skillName")
                return@withContext buildScanResult(
                    skillName = skillName, status = ScanStatus.BLOCKED,
                    findings = findings, scannedFiles = countFiles(skillDir),
                    startTimeNanos = startTime
                )
            }

            // Phase 2: 清单完整性校验 (P4)
            Log.w(TAG, "[Phase 2] Manifest verification for: $skillName")
            if (manifestJson != null && manifestJson.exists()) {
                val manifestFindings = rulesEngine.checkManifestCompliance(manifestJson, skillDir)
                findings.addAll(manifestFindings)
                scannedFiles++
            } else {
                findings.add(
                    SecurityFinding(
                        severity = Severity.CRITICAL, category = Category.MANIFEST_TAMPERING,
                        title = "缺少 skill_manifest.json",
                        description = "Skill 目录中未找到 skill_manifest.json 文件",
                        affectedFile = "skill_manifest.json", affectedLine = null,
                        recommendation = "请联系开发者提供有效的 skill_manifest.json 文件"
                    )
                )
                if (policy.requireManifestSignature) {
                    Log.e(TAG, "Manifest missing, blocking skill: $skillName")
                    return@withContext buildScanResult(
                        skillName = skillName, status = ScanStatus.BLOCKED,
                        findings = findings, scannedFiles = scannedFiles,
                        startTimeNanos = startTime
                    )
                }
            }

            // Phase 3: 静态代码分析 (P2)
            Log.w(TAG, "[Phase 3] Static code analysis for: $skillName")
            val sourceFiles = skillDir.walkTopDown()
                .filter { it.isFile && it.extension in listOf("kt", "kts", "java", "js", "ts", "py") }
                .toList()

            for (file in sourceFiles) {
                scannedFiles++
                findings.addAll(analyzeSourceFile(file, policy))
            }

            // Phase 4: 权限风险评估 (P1)
            Log.w(TAG, "[Phase 4] Permission risk assessment for: $skillName")
            val requestedPermissions = extractRequestedPermissions(skillDir, manifestJson)
            findings.addAll(rulesEngine.checkDangerousPermissionCombinations(requestedPermissions))

            // Phase 5: 数据流分析 (P3)
            Log.w(TAG, "[Phase 5] Data flow analysis for: $skillName")
            findings.addAll(rulesEngine.analyzeDataFlow(sourceFiles))

            // Phase 6: 已知恶意模式检测 (P5)
            Log.w(TAG, "[Phase 6] Known malware pattern detection for: $skillName")
            val malwareFindings = checkKnownMalwarePatterns(skillDir, sourceFiles)
            findings.addAll(malwareFindings)

            if (hasCriticalMalwareFindings(malwareFindings)) {
                Log.e(TAG, "Known malware detected, blocking skill: $skillName")
                return@withContext buildScanResult(
                    skillName = skillName, status = ScanStatus.BLOCKED,
                    findings = findings, scannedFiles = scannedFiles,
                    startTimeNanos = startTime
                )
            }

            // 综合评估
            val overallStatus = evaluateOverallStatus(findings, policy)
            Log.w(TAG, "Scan completed for: $skillName -> status=$overallStatus, " +
                    "findings=${findings.size}, files=$scannedFiles")

            buildScanResult(
                skillName = skillName, status = overallStatus,
                findings = findings, scannedFiles = scannedFiles,
                startTimeNanos = startTime
            )

        } catch (e: Exception) {
            Log.e(TAG, "Scan failed for: $skillName", e)
            buildScanResult(
                skillName = skillName, status = ScanStatus.BLOCKED,
                findings = listOf(
                    SecurityFinding(
                        severity = Severity.HIGH, category = Category.UNKNOWN_SOURCE,
                        title = "扫描过程异常",
                        description = "扫描过程中发生未预期异常: ${e.message}",
                        affectedFile = null, affectedLine = null,
                        recommendation = "请重试扫描或联系技术支持"
                    )
                ),
                scannedFiles = scannedFiles, startTimeNanos = startTime
            )
        }
    }

    private fun performPreScan(skillDir: File, policy: SecurityPolicy): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        if (!skillDir.exists() || !skillDir.isDirectory) {
            findings.add(
                SecurityFinding(
                    severity = Severity.CRITICAL, category = Category.MANIFEST_TAMPERING,
                    title = "Skill 目录无效",
                    description = "指定的 Skill 目录不存在或不是一个有效的目录",
                    affectedFile = skillDir.absolutePath, affectedLine = null,
                    recommendation = "请确认 Skill 安装目录正确"
                )
            )
            return findings
        }

        val totalSize = skillDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        if (totalSize > MAX_SKILL_SIZE_BYTES) {
            findings.add(
                SecurityFinding(
                    severity = Severity.HIGH, category = Category.DENIAL_OF_SERVICE,
                    title = "Skill 包体积过大",
                    description = "Skill 总大小 ${totalSize / (1024 * 1024)}MB，" +
                            "超过限制 ${MAX_SKILL_SIZE_MB}MB",
                    affectedFile = skillDir.absolutePath, affectedLine = null,
                    recommendation = "请压缩 Skill 大小，删除不必要的文件"
                )
            )
        }

        val fileCount = countFiles(skillDir)
        if (fileCount > MAX_FILE_COUNT) {
            findings.add(
                SecurityFinding(
                    severity = Severity.MEDIUM, category = Category.DENIAL_OF_SERVICE,
                    title = "文件数量过多",
                    description = "Skill 包含 $fileCount 个文件，超过限制 $MAX_FILE_COUNT",
                    affectedFile = null, affectedLine = null,
                    recommendation = "请减少不必要的文件"
                )
            )
        }

        skillDir.walkTopDown()
            .filter { it.isFile && it.length() > MAX_SINGLE_FILE_SIZE_MB * 1024 * 1024 }
            .forEach { file ->
                findings.add(
                    SecurityFinding(
                        severity = Severity.MEDIUM, category = Category.DENIAL_OF_SERVICE,
                        title = "超大文件",
                        description = "文件 ${file.name} 大小超过 ${MAX_SINGLE_FILE_SIZE_MB}MB",
                        affectedFile = file.absolutePath, affectedLine = null,
                        recommendation = "请检查文件是否必要"
                    )
                )
            }

        return findings
    }

    private fun analyzeSourceFile(file: File, policy: SecurityPolicy): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        try {
            val content = file.readText()
            val lines = content.lines()

            // 危险 API 调用检测 (P2)
            findings.addAll(rulesEngine.checkDangerousApiCalls(content, file.name, file.absolutePath))

            // 网络行为检测
            if (!policy.allowNetworkAccess) {
                val networkPatterns = listOf(
                    "HttpURLConnection" to "使用 HttpURLConnection 进行网络请求",
                    "OkHttpClient" to "使用 OkHttp 进行网络请求",
                    "URL.openConnection" to "打开网络连接",
                    ".openStream()" to "打开网络数据流",
                    "Socket(" to "创建 Socket 连接",
                    "WebSocket" to "使用 WebSocket",
                    "http://" to "硬编码 HTTP URL",
                    "https://" to "硬编码 HTTPS URL",
                    "fetch(" to "使用 fetch API",
                    "axios." to "使用 axios",
                    "XMLHttpRequest" to "使用 XMLHttpRequest",
                    "requests." to "使用 requests 库",
                    "urllib" to "使用 urllib 库"
                )

                for ((lineIndex, line) in lines.withIndex()) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("*")) continue
                    for ((pattern, desc) in networkPatterns) {
                        if (line.contains(pattern, ignoreCase = true)) {
                            findings.add(
                                SecurityFinding(
                                    severity = Severity.MEDIUM, category = Category.NETWORK_ABUSE,
                                    title = "未声明的网络访问",
                                    description = "检测到 $desc，但 Skill 未声明需要网络权限",
                                    affectedFile = file.absolutePath, affectedLine = lineIndex + 1,
                                    recommendation = "如果 Skill 需要联网，请在 securityProfile 中声明"
                                )
                            )
                        }
                    }
                }
            }

            // 数据外泄检测
            val exfilPatterns = listOf(
                "getExternalStorageDirectory" to "写入外部存储",
                "getExternalFilesDir" to "写入外部文件目录",
                "getExternalMediaDirs" to "写入外部媒体目录",
                "Environment.getExternalStoragePublicDirectory" to "写入公共目录",
                "MediaStore" to "写入媒体库",
                "ContentValues" to "创建内容值（可能写入敏感数据）",
                "SmsManager" to "发送短信",
                "sendTextMessage" to "发送文本短信",
                "sendMultipartTextMessage" to "发送多媒体短信",
                "Intent.ACTION_CALL" to "拨打电话",
                "Intent.ACTION_DIAL" to "拨打界面",
                "clipboard" to "访问剪贴板",
                "ClipboardManager" to "剪贴板管理"
            )

            for ((lineIndex, line) in lines.withIndex()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("//") || trimmed.startsWith("#")) continue
                for ((pattern, desc) in exfilPatterns) {
                    if (line.contains(pattern, ignoreCase = true)) {
                        findings.add(
                            SecurityFinding(
                                severity = Severity.HIGH, category = Category.DATA_EXFILTRATION,
                                title = "可能的数据外泄",
                                description = "检测到: $desc",
                                affectedFile = file.absolutePath, affectedLine = lineIndex + 1,
                                recommendation = "请确认此操作是必要的，且数据不会外传到第三方"
                            )
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze file: ${file.absolutePath}", e)
            findings.add(
                SecurityFinding(
                    severity = Severity.LOW, category = Category.UNKNOWN_SOURCE,
                    title = "文件分析失败",
                    description = "无法分析文件 ${file.name}: ${e.message}",
                    affectedFile = file.absolutePath, affectedLine = null,
                    recommendation = "请确保文件编码正确且未被损坏"
                )
            )
        }

        return findings
    }

    private fun checkKnownMalwarePatterns(
        skillDir: File, sourceFiles: List<File>
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        // SHA256 黑名单检查
        for (file in skillDir.walkTopDown().filter { it.isFile }) {
            try {
                val sha256 = computeSha256(file)
                if (sha256 in knownMalwareHashSet) {
                    findings.add(
                        SecurityFinding(
                            severity = Severity.CRITICAL, category = Category.UNKNOWN_SOURCE,
                            title = "已知恶意文件",
                            description = "文件 ${file.name} 的 SHA256 哈希匹配已知恶意模式库",
                            affectedFile = file.absolutePath, affectedLine = null,
                            recommendation = "立即阻止此 Skill 安装，此文件被社区标记为恶意",
                            cveId = KnownMalwarePatterns.hashToCveMap[sha256]
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hash computation failed: ${file.absolutePath}", e)
            }
        }

        // YARA 式规则匹配
        for (file in sourceFiles) {
            try {
                val content = file.readText()
                for ((pattern, rule) in KnownMalwarePatterns.yaraRules) {
                    val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE))
                    if (regex.containsMatchIn(content)) {
                        findings.add(
                            SecurityFinding(
                                severity = Severity.CRITICAL, category = Category.CODE_INJECTION,
                                title = "已知恶意代码特征",
                                description = rule.description,
                                affectedFile = file.absolutePath, affectedLine = null,
                                recommendation = rule.recommendation,
                                cveId = rule.cveId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "YARA rule match failed: ${file.absolutePath}", e)
            }
        }

        return findings
    }

    private fun extractRequestedPermissions(skillDir: File, manifestJson: File?): List<String> {
        if (manifestJson == null || !manifestJson.exists()) return emptyList()
        return try {
            val json = org.json.JSONObject(manifestJson.readText())
            val arr = json.optJSONArray("permissions") ?: return emptyList()
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract permissions", e)
            emptyList()
        }
    }

    private fun parseSkillName(manifestJson: File): String {
        return try {
            val json = org.json.JSONObject(manifestJson.readText())
            json.optString("name", manifestJson.parentFile.name)
        } catch (e: Exception) {
            manifestJson.parentFile.name
        }
    }

    private fun evaluateOverallStatus(findings: List<SecurityFinding>, policy: SecurityPolicy): ScanStatus {
        val riskScore = calculateRiskScore(findings)
        return when {
            riskScore >= policy.blockThreshold -> ScanStatus.DANGEROUS
            riskScore >= policy.warningThreshold -> ScanStatus.WARNING
            riskScore <= policy.maxRiskScoreForAutoApprove -> ScanStatus.SAFE
            else -> ScanStatus.WARNING
        }
    }

    private fun calculateRiskScore(findings: List<SecurityFinding>): Int {
        if (findings.isEmpty()) return 0
        var score = 0
        for (finding in findings) {
            score += when (finding.severity) {
                Severity.CRITICAL -> 40
                Severity.HIGH -> 20
                Severity.MEDIUM -> 10
                Severity.LOW -> 3
                Severity.INFO -> 0
            }
        }
        return minOf(score, 100)
    }

    private fun hasBlockedIssues(findings: List<SecurityFinding>): Boolean =
        findings.any { it.severity == Severity.CRITICAL }

    private fun hasCriticalMalwareFindings(findings: List<SecurityFinding>): Boolean =
        findings.any { it.category == Category.UNKNOWN_SOURCE && it.severity == Severity.CRITICAL }

    private fun countFiles(dir: File): Int = dir.walkTopDown().count { it.isFile }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun buildScanResult(
        skillName: String, status: ScanStatus, findings: List<SecurityFinding>,
        scannedFiles: Int, startTimeNanos: Long
    ): ScanResult {
        val durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000
        val riskScore = calculateRiskScore(findings)
        return ScanResult(
            skillName = skillName, scanTimestamp = Instant.now().toEpochMilli(),
            overallStatus = status, riskScore = riskScore,
            findings = findings.sortedByDescending { it.severity.ordinal },
            scannedFiles = scannedFiles, scanDurationMs = durationMs
        )
    }
}

// ===== 数据类定义 =====

data class ScanResult(
    val skillName: String,
    val scanTimestamp: Long,
    val overallStatus: ScanStatus,
    val riskScore: Int,
    val findings: List<SecurityFinding>,
    val scannedFiles: Int,
    val scanDurationMs: Long
)

data class SecurityFinding(
    val severity: Severity,
    val category: Category,
    val title: String,
    val description: String,
    val affectedFile: String?,
    val affectedLine: Int?,
    val recommendation: String,
    val cveId: String? = null
)

enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
enum class ScanStatus { SAFE, WARNING, DANGEROUS, BLOCKED }
enum class Category {
    PERMISSION_ABUSE, DATA_EXFILTRATION, CODE_INJECTION,
    NETWORK_ABUSE, FILE_SYSTEM_ABUSE, PRIVACY_VIOLATION,
    DENIAL_OF_SERVICE, CRYPTO_ISSUE, MANIFEST_TAMPERING, UNKNOWN_SOURCE
}
