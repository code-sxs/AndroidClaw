// Desensitizer.kt
// Skill 创建器 - 脱敏处理器
// 移除代码中的敏感信息，支持分享到市场前进行脱敏

package com.androidclaw.app.skills.creator

import android.util.Log
import java.io.File

/**
 * 脱敏处理器
 * 
 * 功能：
 * - 移除代码中的敏感信息：
 *   - API Key / Secret
 *   - 硬编码的 URL（如有内网地址）
 *   - 个人信息（姓名、电话、邮箱）
 *   - 设备特定信息（IMEI、序列号等）
 *   - 文件路径（如有绝对路径）
 * - 生成脱敏报告
 * - 用户确认后才执行脱敏
 */
class Desensitizer {

    companion object {
        private const val TAG = "Desensitizer"
    }

    /**
     * 脱敏严重程度
     */
    enum class Severity {
        INFO,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * 脱敏规则
     */
    data class DesensitizeRule(
        val pattern: Regex,
        val replacement: String,
        val category: String,
        val severity: Severity,
        val description: String
    )

    /**
     * 脱敏报告
     */
    data class DesensitizeReport(
        val originalSize: Int,
        val processedSize: Int,
        val findings: List<DesensitizeFinding>,
        val hasHighRisk: Boolean,
        val summary: String
    )

    /**
     * 脱敏发现
     */
    data class DesensitizeFinding(
        val category: String,
        val severity: Severity,
        val description: String,
        val originalValue: String,
        val replacement: String,
        val lineNumber: Int?,
        val fileName: String?
    )

    /**
     * 默认脱敏规则
     */
    private val defaultRules = listOf(
        // API Key / Secret
        DesensitizeRule(
            pattern = Regex("""(api_key|apikey|API_KEY|api-key|apiKey)\s*[=:]\s*["']?[a-zA-Z0-9_\-]{16,}["']?"""),
            replacement = "{YOUR_API_KEY}",
            category = "API_KEY",
            severity = Severity.CRITICAL,
            description = "API Key 泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""(secret|SECRET|Secret)\s*[=:]\s*["']?[a-zA-Z0-9_\-]{16,}["']?"""),
            replacement = "{YOUR_SECRET}",
            category = "API_KEY",
            severity = Severity.CRITICAL,
            description = "Secret 泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""(token|TOKEN|Token)\s*[=:]\s*["']?[a-zA-Z0-9_\-]{16,}["']?"""),
            replacement = "{YOUR_TOKEN}",
            category = "API_KEY",
            severity = Severity.HIGH,
            description = "Token 泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""Bearer\s+[a-zA-Z0-9_\-\.]{20,}"""),
            replacement = "Bearer {YOUR_TOKEN}",
            category = "API_KEY",
            severity = Severity.HIGH,
            description = "Bearer Token 泄露"
        ),
        
        // 内网 URL
        DesensitizeRule(
            pattern = Regex("""https?://192\.168\.\d{1,3}\.\d{1,3}(:\d+)?(/[^\s"'<>]*)?"""),
            replacement = "{YOUR_SERVER_URL}",
            category = "INTERNAL_URL",
            severity = Severity.HIGH,
            description = "内网地址泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""https?://10\.\d{1,3}\.\d{1,3}\.\d{1,3}(:\d+)?(/[^\s"'<>]*)?"""),
            replacement = "{YOUR_SERVER_URL}",
            category = "INTERNAL_URL",
            severity = Severity.HIGH,
            description = "内网地址泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""https?://172\.(1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}(:\d+)?(/[^\s"'<>]*)?"""),
            replacement = "{YOUR_SERVER_URL}",
            category = "INTERNAL_URL",
            severity = Severity.HIGH,
            description = "内网地址泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""https?://localhost(:\d+)?(/[^\s"'<>]*)?"""),
            replacement = "{YOUR_SERVER_URL}",
            category = "INTERNAL_URL",
            severity = Severity.MEDIUM,
            description = "本地地址泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""https?://127\.0\.0\.1(:\d+)?(/[^\s"'<>]*)?"""),
            replacement = "{YOUR_SERVER_URL}",
            category = "INTERNAL_URL",
            severity = Severity.MEDIUM,
            description = "本地地址泄露"
        ),
        
        // 个人信息
        DesensitizeRule(
            pattern = Regex("""\b[\w.-]+@[\w.-]+\.\w{2,}\b"""),
            replacement = "{YOUR_EMAIL}",
            category = "PERSONAL_INFO",
            severity = Severity.MEDIUM,
            description = "邮箱地址泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""\b1[3-9]\d{9}\b"""),
            replacement = "{YOUR_PHONE}",
            category = "PERSONAL_INFO",
            severity = Severity.MEDIUM,
            description = "手机号码泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""\b\d{17}[\dXx]\b"""),
            replacement = "{YOUR_ID_CARD}",
            category = "PERSONAL_INFO",
            severity = Severity.HIGH,
            description = "身份证号码泄露"
        ),
        
        // 设备信息
        DesensitizeRule(
            pattern = Regex("""\bIMEI[:\s]*[0-9]{15}\b"""),
            replacement = "{YOUR_IMEI}",
            category = "DEVICE_INFO",
            severity = Severity.HIGH,
            description = "IMEI 泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""\b[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}\b"""),
            replacement = "{YOUR_UUID}",
            category = "DEVICE_INFO",
            severity = Severity.MEDIUM,
            description = "UUID 泄露"
        ),
        
        // 文件路径
        DesensitizeRule(
            pattern = Regex("""/sdcard/[^\s"'<>]+"""),
            replacement = "{EXTERNAL_STORAGE_PATH}",
            category = "FILE_PATH",
            severity = Severity.LOW,
            description = "外部存储路径泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""/storage/emulated/\d+/[^\s"'<>]+"""),
            replacement = "{EXTERNAL_STORAGE_PATH}",
            category = "FILE_PATH",
            severity = Severity.LOW,
            description = "外部存储路径泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""/data/data/[a-z.]+(/[^\s"'<>]+)?"""),
            replacement = "{APP_DATA_PATH}",
            category = "FILE_PATH",
            severity = Severity.MEDIUM,
            description = "应用数据路径泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""/data/user/\d+/[a-z.]+(/[^\s"'<>]+)?"""),
            replacement = "{APP_DATA_PATH}",
            category = "FILE_PATH",
            severity = Severity.MEDIUM,
            description = "应用数据路径泄露"
        ),
        
        // 数据库连接字符串
        DesensitizeRule(
            pattern = Regex("""(mysql|postgres|mongodb|redis)://[^\s"'<>]+"""),
            replacement = "{YOUR_DATABASE_URL}",
            category = "DATABASE",
            severity = Severity.CRITICAL,
            description = "数据库连接字符串泄露"
        ),
        DesensitizeRule(
            pattern = Regex("""jdbc:[^\s"'<>]+"""),
            replacement = "{YOUR_DATABASE_URL}",
            category = "DATABASE",
            severity = Severity.CRITICAL,
            description = "JDBC 连接字符串泄露"
        ),
        
        // 密码
        DesensitizeRule(
            pattern = Regex("""(password|PASSWORD|Password|passwd|PASSWD)\s*[=:]\s*["'][^"']+["']"""),
            replacement = "{YOUR_PASSWORD}",
            category = "PASSWORD",
            severity = Severity.CRITICAL,
            description = "密码泄露"
        )
    )

    /**
     * 扫描并生成脱敏报告
     * 
     * @param skillDir Skill 目录
     * @return 脱敏报告
     */
    fun scan(skillDir: File): DesensitizeReport {
        Log.i(TAG, "Scanning skill directory: ${skillDir.absolutePath}")
        
        val findings = mutableListOf<DesensitizeFinding>()
        var totalOriginalSize = 0
        
        val sourceFiles = skillDir.walkTopDown()
            .filter { it.isFile && it.extension in listOf("kt", "kts", "java", "json", "xml", "md") }
            .toList()
        
        for (file in sourceFiles) {
            val content = file.readText()
            totalOriginalSize += content.length
            
            val lines = content.lines()
            for ((lineIndex, line) in lines.withIndex()) {
                for (rule in defaultRules) {
                    val matches = rule.pattern.findAll(line)
                    for (match in matches) {
                        findings.add(
                            DesensitizeFinding(
                                category = rule.category,
                                severity = rule.severity,
                                description = rule.description,
                                originalValue = match.value.take(50) + if (match.value.length > 50) "..." else "",
                                replacement = rule.replacement,
                                lineNumber = lineIndex + 1,
                                fileName = file.name
                            )
                        )
                    }
                }
            }
        }
        
        val hasHighRisk = findings.any { 
            it.severity == Severity.CRITICAL || it.severity == Severity.HIGH 
        }
        
        val summary = buildSummary(findings)
        
        Log.i(TAG, "Scan completed: ${findings.size} findings, highRisk=$hasHighRisk")
        
        return DesensitizeReport(
            originalSize = totalOriginalSize,
            processedSize = totalOriginalSize,
            findings = findings.sortedByDescending { it.severity.ordinal },
            hasHighRisk = hasHighRisk,
            summary = summary
        )
    }

    /**
     * 执行脱敏处理
     * 
     * @param skillDir Skill 目录
     * @param outputDir 输出目录
     * @param report 脱敏报告
     * @return 处理后的 Skill 目录
     */
    fun process(skillDir: File, outputDir: File, report: DesensitizeReport): File {
        Log.i(TAG, "Processing desensitization...")
        
        val targetDir = File(outputDir, skillDir.name + "_desensitized")
        targetDir.mkdirs()
        
        val sourceFiles = skillDir.walkTopDown()
            .filter { it.isFile }
            .toList()
        
        for (file in sourceFiles) {
            val relativePath = file.relativeTo(skillDir)
            val targetFile = File(targetDir, relativePath.path)
            targetFile.parentFile?.mkdirs()
            
            if (file.extension in listOf("kt", "kts", "java", "json", "xml", "md")) {
                // 对文本文件进行脱敏
                var content = file.readText()
                
                for (rule in defaultRules) {
                    content = rule.pattern.replace(content, rule.replacement)
                }
                
                targetFile.writeText(content)
            } else {
                // 非文本文件直接复制
                file.copyTo(targetFile, overwrite = true)
            }
        }
        
        // 生成脱敏报告文件
        val reportFile = File(targetDir, "desensitize_report.md")
        reportFile.writeText(generateReportMarkdown(report))
        
        Log.i(TAG, "Desensitization completed: ${targetDir.absolutePath}")
        return targetDir
    }

    /**
     * 构建摘要
     */
    private fun buildSummary(findings: List<DesensitizeFinding>): String {
        if (findings.isEmpty()) {
            return "未发现敏感信息，代码安全可分享"
        }
        
        val byCategory = findings.groupBy { it.category }
        val bySeverity = findings.groupBy { it.severity }
        
        val sb = StringBuilder()
        sb.append("发现 ${findings.size} 处敏感信息需要处理：\n\n")
        
        sb.append("按严重程度：\n")
        for ((severity, items) in bySeverity.entries.sortedByDescending { it.key.ordinal }) {
            sb.append("- ${severity.name}: ${items.size} 处\n")
        }
        
        sb.append("\n按类别：\n")
        for ((category, items) in byCategory) {
            sb.append("- $category: ${items.size} 处\n")
        }
        
        if (findings.any { it.severity == Severity.CRITICAL }) {
            sb.append("\n⚠️ 存在严重风险，强烈建议处理后分享！")
        }
        
        return sb.toString()
    }

    /**
     * 生成报告 Markdown
     */
    private fun generateReportMarkdown(report: DesensitizeReport): String {
        val sb = StringBuilder()
        sb.append("# 脱敏报告\n\n")
        sb.append("**扫描时间**: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(java.util.Date())}\n\n")
        sb.append("**原始大小**: ${report.originalSize} 字符\n\n")
        sb.append("**发现问题**: ${report.findings.size} 处\n\n")
        sb.append("**高风险**: ${if (report.hasHighRisk) "是" else "否"}\n\n")
        
        sb.append("---\n\n")
        sb.append("## 详细发现\n\n")
        
        val byCategory = report.findings.groupBy { it.category }
        for ((category, items) in byCategory) {
            sb.append("### $category\n\n")
            for (finding in items) {
                sb.append("- **[${finding.severity}]** ${finding.fileName}:${finding.lineNumber ?: "?"}\n")
                sb.append("  - ${finding.description}\n")
                sb.append("  - 原始值: `${finding.originalValue}`\n")
                sb.append("  - 替换为: `${finding.replacement}`\n\n")
            }
        }
        
        sb.append("---\n\n")
        sb.append("*Generated by AndroidClaw Desensitizer*\n")
        
        return sb.toString()
    }

    /**
     * 快速扫描单个文件
     */
    fun scanFile(file: File): List<DesensitizeFinding> {
        val findings = mutableListOf<DesensitizeFinding>()
        
        if (!file.exists() || !file.isFile) {
            return findings
        }
        
        val content = file.readText()
        val lines = content.lines()
        
        for ((lineIndex, line) in lines.withIndex()) {
            for (rule in defaultRules) {
                val matches = rule.pattern.findAll(line)
                for (match in matches) {
                    findings.add(
                        DesensitizeFinding(
                            category = rule.category,
                            severity = rule.severity,
                            description = rule.description,
                            originalValue = match.value.take(50) + if (match.value.length > 50) "..." else "",
                            replacement = rule.replacement,
                            lineNumber = lineIndex + 1,
                            fileName = file.name
                        )
                    )
                }
            }
        }
        
        return findings
    }

    /**
     * 快速检查字符串是否包含敏感信息
     */
    fun containsSensitiveInfo(text: String): Boolean {
        return defaultRules.any { rule -> rule.pattern.containsMatchIn(text) }
    }
}
