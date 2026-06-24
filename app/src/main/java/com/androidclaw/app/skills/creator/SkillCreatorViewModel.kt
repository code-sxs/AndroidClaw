// SkillCreatorViewModel.kt
// Skill 创建器 - ViewModel
// 管理 Skill 生成与共享的状态

package com.androidclaw.app.skills.creator

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.androidclaw.app.llm.LLMManager
import com.androidclaw.app.skills.security.ScanResult
import com.androidclaw.app.skills.security.SkillSecurityScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

/**
 * Skill 创建器 ViewModel
 * 
 * 管理状态：
 * - 需求解析结果
 * - 生成进度
 * - 安全扫描结果
 * - 脱敏报告
 * - 分享状态
 */
class SkillCreatorViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "SkillCreatorVM"
    }

    // 依赖
    private val llmManager = LLMManager.getInstance(application)
    private val securityScanner = SkillSecurityScanner.getInstance()
    private val okHttpClient = OkHttpClient()
    
    private val requirementParser = RequirementParser(llmManager)
    private val skillGenerator = SkillGenerator(llmManager, securityScanner)
    private val desensitizer = Desensitizer()
    private val skillSharer = SkillSharer(okHttpClient, desensitizer)

    // 工作目录
    private val workDir = File(application.cacheDir, "skill_creator").apply { mkdirs() }

    // ===== 状态流 =====

    private val _uiState = MutableStateFlow<SkillCreatorUiState>(SkillCreatorUiState.Idle)
    val uiState: StateFlow<SkillCreatorUiState> = _uiState.asStateFlow()

    private val _parsedRequirement = MutableStateFlow<ParsedRequirement?>(null)
    val parsedRequirement: StateFlow<ParsedRequirement?> = _parsedRequirement.asStateFlow()

    private val _generatedCode = MutableStateFlow<String?>(null)
    val generatedCode: StateFlow<String?> = _generatedCode.asStateFlow()

    private val _manifestJson = MutableStateFlow<String?>(null)
    val manifestJson: StateFlow<String?> = _manifestJson.asStateFlow()

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    private val _desensitizeReport = MutableStateFlow<Desensitizer.DesensitizeReport?>(null)
    val desensitizeReport: StateFlow<Desensitizer.DesensitizeReport?> = _desensitizeReport.asStateFlow()

    private val _progressMessage = MutableStateFlow<String>("")
    val progressMessage: StateFlow<String> = _progressMessage.asStateFlow()

    private val _shareResults = MutableStateFlow<List<SkillSharer.ShareResult>>(emptyList())
    val shareResults: StateFlow<List<SkillSharer.ShareResult>> = _shareResults.asStateFlow()

    private val _sharedSkills = MutableStateFlow<List<SkillSharer.SharedSkill>>(emptyList())
    val sharedSkills: StateFlow<List<SkillSharer.SharedSkill>> = _sharedSkills.asStateFlow()

    // ===== 操作方法 =====

    /**
     * 解析需求
     */
    fun parseRequirement(userRequirement: String) {
        viewModelScope.launch {
            try {
                _uiState.value = SkillCreatorUiState.Parsing
                _progressMessage.value = "正在分析需求..."
                
                val result = requirementParser.parse(userRequirement)
                _parsedRequirement.value = result
                
                _uiState.value = SkillCreatorUiState.Parsed(result)
                _progressMessage.value = "需求解析完成"
                
                Log.i(TAG, "Requirement parsed: ${result.skillName}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse requirement", e)
                _uiState.value = SkillCreatorUiState.Error("需求解析失败: ${e.message}")
            }
        }
    }

    /**
     * 生成 Skill
     */
    fun generateSkill() {
        val requirement = _parsedRequirement.value ?: return
        
        viewModelScope.launch {
            try {
                _uiState.value = SkillCreatorUiState.Generating
                _progressMessage.value = "正在生成代码..."
                
                val result = skillGenerator.generate(
                    requirement = requirement,
                    outputDir = workDir,
                    onProgress = { _progressMessage.value = it }
                )
                
                if (result.success) {
                    _generatedCode.value = result.kotlinCode
                    _manifestJson.value = result.manifestJson
                    _scanResult.value = result.securityScan
                    
                    _uiState.value = SkillCreatorUiState.Generated(result)
                    _progressMessage.value = "生成完成"
                    
                    if (result.warnings.isNotEmpty()) {
                        Log.w(TAG, "Warnings: ${result.warnings}")
                    }
                } else {
                    _uiState.value = SkillCreatorUiState.Error(result.error ?: "生成失败")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate skill", e)
                _uiState.value = SkillCreatorUiState.Error("生成失败: ${e.message}")
            }
        }
    }

    /**
     * 优化代码
     */
    fun optimizeCode(feedback: String) {
        val requirement = _parsedRequirement.value ?: return
        val currentCode = _generatedCode.value ?: return
        
        viewModelScope.launch {
            try {
                _uiState.value = SkillCreatorUiState.Optimizing
                _progressMessage.value = "正在优化代码..."
                
                val optimizedCode = skillGenerator.optimize(currentCode, feedback, requirement)
                
                if (optimizedCode != null) {
                    _generatedCode.value = optimizedCode
                    _uiState.value = SkillCreatorUiState.Generated(
                        SkillGenerator.GenerationResult(
                            success = true,
                            skillName = requirement.skillName,
                            kotlinCode = optimizedCode,
                            manifestJson = _manifestJson.value,
                            readme = null,
                            securityScan = _scanResult.value,
                            error = null
                        )
                    )
                    _progressMessage.value = "优化完成"
                } else {
                    _uiState.value = SkillCreatorUiState.Error("优化失败")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to optimize code", e)
                _uiState.value = SkillCreatorUiState.Error("优化失败: ${e.message}")
            }
        }
    }

    /**
     * 保存 Skill
     */
    fun saveSkill(): File? {
        val requirement = _parsedRequirement.value ?: return null
        val code = _generatedCode.value ?: return null
        val manifest = _manifestJson.value ?: return null
        
        return try {
            val skillDir = File(workDir, "saved/${requirement.skillName}")
            skillDir.mkdirs()
            
            File(skillDir, "${requirement.skillName}_skill.kt").writeText(code)
            File(skillDir, "skill_manifest.json").writeText(manifest)
            
            Log.i(TAG, "Skill saved: ${skillDir.absolutePath}")
            skillDir
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save skill", e)
            null
        }
    }

    /**
     * 扫描敏感信息
     */
    fun scanSensitiveInfo(skillDir: File) {
        viewModelScope.launch {
            try {
                _progressMessage.value = "正在扫描敏感信息..."
                
                val report = desensitizer.scan(skillDir)
                _desensitizeReport.value = report
                
                _progressMessage.value = if (report.findings.isEmpty()) {
                    "未发现敏感信息"
                } else {
                    "发现 ${report.findings.size} 处敏感信息"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan sensitive info", e)
            }
        }
    }

    /**
     * 分享 Skill
     */
    fun shareSkill(
        skillDir: File,
        author: String,
        tags: List<String>,
        category: String,
        targetMarkets: List<String>,
        skipDesensitize: Boolean = false
    ) {
        val requirement = _parsedRequirement.value ?: return
        
        viewModelScope.launch {
            try {
                _uiState.value = SkillCreatorUiState.Sharing
                _progressMessage.value = "正在分享..."
                
                val request = SkillSharer.ShareRequest(
                    skillDir = skillDir,
                    skillName = requirement.skillName,
                    displayName = requirement.displayName,
                    description = requirement.description,
                    version = "1.0.0",
                    author = author,
                    tags = tags,
                    category = category,
                    targetMarkets = targetMarkets,
                    skipDesensitize = skipDesensitize
                )
                
                val results = skillSharer.share(request) { _progressMessage.value = it }
                _shareResults.value = results
                
                val successCount = results.count { it.success }
                if (successCount > 0) {
                    _uiState.value = SkillCreatorUiState.Shared(results)
                    _progressMessage.value = "分享成功: $successCount/${results.size}"
                    _sharedSkills.value = skillSharer.getSharedSkills()
                } else {
                    _uiState.value = SkillCreatorUiState.Error("分享失败: ${results.firstOrNull()?.error}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share skill", e)
                _uiState.value = SkillCreatorUiState.Error("分享失败: ${e.message}")
            }
        }
    }

    /**
     * 撤回分享
     */
    fun withdrawShare(skill: SkillSharer.SharedSkill) {
        viewModelScope.launch {
            try {
                _progressMessage.value = "正在撤回..."
                
                val success = skillSharer.withdraw(skill)
                
                if (success) {
                    _sharedSkills.value = skillSharer.getSharedSkills()
                    _progressMessage.value = "撤回成功"
                } else {
                    _progressMessage.value = "撤回失败"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to withdraw", e)
                _progressMessage.value = "撤回失败: ${e.message}"
            }
        }
    }

    /**
     * 重置状态
     */
    fun reset() {
        _uiState.value = SkillCreatorUiState.Idle
        _parsedRequirement.value = null
        _generatedCode.value = null
        _manifestJson.value = null
        _scanResult.value = null
        _desensitizeReport.value = null
        _progressMessage.value = ""
        _shareResults.value = emptyList()
    }

    /**
     * 加载已分享的 Skill
     */
    fun loadSharedSkills() {
        _sharedSkills.value = skillSharer.getSharedSkills()
    }
}

/**
 * UI 状态
 */
sealed class SkillCreatorUiState {
    object Idle : SkillCreatorUiState()
    object Parsing : SkillCreatorUiState()
    data class Parsed(val requirement: ParsedRequirement) : SkillCreatorUiState()
    object Generating : SkillCreatorUiState()
    data class Generated(val result: SkillGenerator.GenerationResult) : SkillCreatorUiState()
    object Optimizing : SkillCreatorUiState()
    object Sharing : SkillCreatorUiState()
    data class Shared(val results: List<SkillSharer.ShareResult>) : SkillCreatorUiState()
    data class Error(val message: String) : SkillCreatorUiState()
}
