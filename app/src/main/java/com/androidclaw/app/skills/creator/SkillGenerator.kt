// SkillGenerator.kt
// Skill 创建器 - AI 驱动的 Skill 生成器
// 接收用户的自然语言需求描述，调用本地 LLM 生成完整的 Skill 代码

package com.androidclaw.app.skills.creator

import android.util.Log
import com.androidclaw.app.llm.LLMManager
import com.androidclaw.app.skills.SkillDefinition
import com.androidclaw.app.skills.ToolDefinition
import com.androidclaw.app.skills.ToolParameter
import com.androidclaw.app.skills.security.ScanResult
import com.androidclaw.app.skills.security.SkillSecurityScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Skill 生成器
 * 
 * 功能：
 * - 接收用户的自然语言需求描述
 * - 调用本地 LLM 生成完整的 Skill 代码
 * - 支持迭代优化（用户反馈 → 重新生成）
 * - 生成代码必须符合 SkillDefinition 接口规范
 * - 自动生成配套的 skill_manifest.json
 * - 自动生成测试用例（基础模板）
 */
class SkillGenerator(
    private val llmManager: LLMManager,
    private val securityScanner: SkillSecurityScanner
) {

    companion object {
        private const val TAG = "SkillGenerator"
        private const val MAX_ITERATIONS = 5
    }

    /**
     * 生成结果
     */
    data class GenerationResult(
        val success: Boolean,
        val skillName: String,
        val kotlinCode: String?,
        val manifestJson: String?,
        val readme: String?,
        val securityScan: ScanResult?,
        val error: String?,
        val warnings: List<String> = emptyList()
    )

    /**
     * 生成 Skill
     * 
     * @param requirement 用户需求
     * @param outputDir 输出目录
     * @param onProgress 进度回调
     */
    suspend fun generate(
        requirement: ParsedRequirement,
        outputDir: File,
        onProgress: (String) -> Unit = {}
    ): GenerationResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Generating skill: ${requirement.skillName}")
        onProgress("开始生成 ${requirement.displayName}...")

        try {
            // 1. 生成 Kotlin 代码
            onProgress("正在生成 Kotlin 代码...")
            val kotlinCode = generateKotlinCode(requirement)
            
            if (kotlinCode.isNullOrBlank()) {
                return@withContext GenerationResult(
                    success = false,
                    skillName = requirement.skillName,
                    kotlinCode = null,
                    manifestJson = null,
                    readme = null,
                    securityScan = null,
                    error = "生成 Kotlin 代码失败"
                )
            }

            // 2. 生成 manifest
            onProgress("正在生成配置文件...")
            val manifestJson = CodeTemplates.manifestTemplate(
                skillName = requirement.skillName,
                displayName = requirement.displayName,
                description = requirement.description,
                permissions = requirement.requiredPermissions,
                tools = requirement.tools
            )

            // 3. 生成 README
            val readme = CodeTemplates.readmeTemplate(
                skillName = requirement.skillName,
                displayName = requirement.displayName,
                description = requirement.description,
                tools = requirement.tools,
                permissions = requirement.requiredPermissions
            )

            // 4. 写入临时文件
            onProgress("正在写入文件...")
            val skillDir = File(outputDir, requirement.skillName)
            skillDir.mkdirs()

            val kotlinFile = File(skillDir, "${requirement.skillName}_skill.kt")
            kotlinFile.writeText(kotlinCode)

            val manifestFile = File(skillDir, "skill_manifest.json")
            manifestFile.writeText(manifestJson)

            val readmeFile = File(skillDir, "README.md")
            readmeFile.writeText(readme)

            // 5. 安全扫描
            onProgress("正在进行安全扫描...")
            val scanResult = securityScanner.scanSkill(
                skillDir = skillDir,
                manifestJson = manifestFile,
                policy = com.androidclaw.app.skills.security.SecurityPolicy()
            )

            val warnings = mutableListOf<String>()
            
            // 检查扫描结果
            if (scanResult.overallStatus == com.androidclaw.app.skills.security.ScanStatus.BLOCKED) {
                return@withContext GenerationResult(
                    success = false,
                    skillName = requirement.skillName,
                    kotlinCode = kotlinCode,
                    manifestJson = manifestJson,
                    readme = readme,
                    securityScan = scanResult,
                    error = "安全扫描未通过: 发现高危风险",
                    warnings = scanResult.findings.map { it.title }
                )
            }

            if (scanResult.overallStatus == com.androidclaw.app.skills.security.ScanStatus.DANGEROUS) {
                warnings.add("安全扫描发现中等风险，建议人工审核")
            }

            onProgress("生成完成！")
            Log.i(TAG, "Skill generated: ${requirement.skillName}, status=${scanResult.overallStatus}")

            GenerationResult(
                success = true,
                skillName = requirement.skillName,
                kotlinCode = kotlinCode,
                manifestJson = manifestJson,
                readme = readme,
                securityScan = scanResult,
                error = null,
                warnings = warnings
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate skill", e)
            GenerationResult(
                success = false,
                skillName = requirement.skillName,
                kotlinCode = null,
                manifestJson = null,
                readme = null,
                securityScan = null,
                error = "生成失败: ${e.message}"
            )
        }
    }

    /**
     * 使用 LLM 生成 Kotlin 代码
     */
    private suspend fun generateKotlinCode(requirement: ParsedRequirement): String? {
        // 先使用模板生成基础代码
        val baseCode = CodeTemplates.baseSkillTemplate(
            skillName = requirement.skillName,
            displayName = requirement.displayName,
            description = requirement.description,
            permissions = requirement.requiredPermissions,
            tools = requirement.tools
        )

        // 如果 LLM 可用，尝试增强代码
        return try {
            val enhancedCode = enhanceWithLLM(requirement, baseCode)
            enhancedCode ?: baseCode
        } catch (e: Exception) {
            Log.w(TAG, "LLM enhancement failed, using template", e)
            baseCode
        }
    }

    /**
     * 使用 LLM 增强代码
     */
    private suspend fun enhanceWithLLM(requirement: ParsedRequirement, baseCode: String): String? {
        val prompt = buildEnhancementPrompt(requirement, baseCode)
        
        val response = llmManager.generateText(prompt)
        
        // 提取代码块
        return extractCodeBlock(response)
    }

    /**
     * 构建增强提示词
     */
    private fun buildEnhancementPrompt(requirement: ParsedRequirement, baseCode: String): String {
        return """
你是一个 Android Kotlin 开发专家。请完善以下 Skill 代码的实现部分。

## Skill 需求
- 名称: ${requirement.displayName}
- 描述: ${requirement.description}
- 权限: ${requirement.requiredPermissions.joinToString(", ")}
- API: ${requirement.androidApis.joinToString(", ")}

## 工具列表
${requirement.tools.joinToString("\n") { tool ->
    "- ${tool.name}: ${tool.description}\n  参数: ${tool.parameters.joinToString(", ") { "${it.name}:${it.type}" }}"
}}

## 基础代码
```kotlin
$baseCode
```

## 要求
1. 完善 execute${requirement.tools.firstOrNull()?.name?.capitalize() ?: "Execute"} 方法的具体实现
2. 添加必要的错误处理
3. 使用 Kotlin 协程处理异步操作
4. 添加适当的日志输出
5. 确保代码符合 Android 开发规范

请输出完整的 Kotlin 代码（包含所有导入和类定义）：
""".trimIndent()
    }

    /**
     * 从 LLM 响应中提取代码块
     */
    private fun extractCodeBlock(response: String): String? {
        // 尝试提取 markdown 代码块
        val codeBlockPattern = Regex("""```kotlin\s*([\s\S]*?)```""")
        codeBlockPattern.find(response)?.let { match ->
            return match.groupValues[1].trim()
        }

        // 尝试提取普通代码块
        val plainCodePattern = Regex("""```\s*([\s\S]*?)```""")
        plainCodePattern.find(response)?.let { match ->
            return match.groupValues[1].trim()
        }

        // 如果响应本身就是代码
        if (response.contains("package com.androidclaw.app.skills")) {
            return response.trim()
        }

        return null
    }

    /**
     * 迭代优化
     * 
     * @param originalCode 原始代码
     * @param feedback 用户反馈
     * @param requirement 需求
     */
    suspend fun optimize(
        originalCode: String,
        feedback: String,
        requirement: ParsedRequirement
    ): String? = withContext(Dispatchers.IO) {
        val prompt = """
你是一个 Android Kotlin 开发专家。请根据用户反馈优化以下 Skill 代码。

## 原始代码
```kotlin
$originalCode
```

## Skill 需求
- 名称: ${requirement.displayName}
- 描述: ${requirement.description}

## 用户反馈
$feedback

## 要求
1. 保持代码结构不变
2. 只修改用户反馈中提到的部分
3. 添加必要的注释说明修改内容
4. 确保修改后的代码符合 Android 开发规范

请输出优化后的完整 Kotlin 代码：
""".trimIndent()

        try {
            val response = llmManager.generateText(prompt)
            extractCodeBlock(response)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to optimize code", e)
            null
        }
    }

    /**
     * 动态加载生成的 Skill
     * 注意：Android 不支持运行时加载 .kt 文件，这里只是模拟接口
     */
    suspend fun loadSkill(skillDir: File): SkillDefinition? = withContext(Dispatchers.IO) {
        // Android 上无法直接加载 .kt 文件
        // 需要用户手动将代码复制到项目中，或使用动态加载方案（如 DexClassLoader 加载 .dex）
        Log.w(TAG, "Dynamic skill loading not supported on Android")
        null
    }
}
