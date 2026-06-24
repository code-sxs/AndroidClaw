// SkillManager.kt
// Skill 管理中心 - 单例模式
// 统一管理所有 Skill 的注册、状态、配置

package com.androidclaw.app.skills

import android.content.Context
import android.util.Log
import com.androidclaw.app.agent.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Skill 信息
 * 用于 UI 展示
 */
data class SkillInfo(
    val skillName: String,
    val displayName: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "Unknown",
    val isEnabled: Boolean = true,
    val isBuiltIn: Boolean = false,
    val requiredPermissions: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val tools: List<ToolDefinition> = emptyList()
)

/**
 * Skill 状态
 */
enum class SkillStatus {
    NOT_INSTALLED,
    INSTALLED,
    ENABLED,
    DISABLED,
    ERROR
}

/**
 * Skill 管理中心
 */
object SkillManager {
    private const val TAG = "SkillManager"
    
    private var initialized = false
    private var context: Context? = null
    private var toolRegistry: ToolRegistry? = null
    
    // Skill 列表
    private val _skills = MutableStateFlow<List<SkillInfo>>(emptyList())
    val skills: StateFlow<List<SkillInfo>> = _skills.asStateFlow()
    
    // 已启用的 Skills
    private val _enabledSkills = MutableStateFlow<Set<String>>(emptySet())
    val enabledSkills: StateFlow<Set<String>> = _enabledSkills.asStateFlow()
    
    /**
     * 初始化
     */
    fun initialize(ctx: Context) {
        if (initialized) return
        
        context = ctx.applicationContext
        toolRegistry = ToolRegistry.getInstance(ctx)
        
        // 注册内置 Skills
        registerBuiltinSkills()
        
        initialized = true
        Log.i(TAG, "SkillManager initialized with ${_skills.value.size} skills")
    }
    
    /**
     * 注册内置 Skills
     */
    private fun registerBuiltinSkills() {
        val builtinSkills = listOf(
            AppManagerSkill(),
            AutomationSkill(),
            CalculatorSkill(),
            CalendarSkill(),
            ClipboardSkill(),
            ContactsSkill(),
            FileOpsSkill(),
            MusicControlSkill(),
            NetworkSkill(),
            NoteSkill(),
            PhoneSkill(),
            ReminderSkill(),
            ScreenCaptureSkill(),
            SettingsSkill(),
            ShareSkill(),
            SmsSkill(),
            TranslationSkill(),
            WeatherSkill()
        )
        
        val skillInfos = builtinSkills.map { skill ->
            SkillInfo(
                skillName = skill.skillName,
                displayName = skill.displayName,
                description = skill.description,
                isBuiltIn = true,
                isEnabled = true,
                requiredPermissions = skill.requiredPermissions,
                tools = skill.getTools()
            )
        }
        
        _skills.value = skillInfos
        _enabledSkills.value = skillInfos.filter { it.isEnabled }.map { it.skillName }.toSet()
    }
    
    /**
     * 获取 Skill 信息
     */
    fun getSkillInfo(skillName: String): SkillInfo? {
        return _skills.value.find { it.skillName == skillName }
    }
    
    /**
     * 启用 Skill
     */
    fun enableSkill(skillName: String): Boolean {
        val currentEnabled = _enabledSkills.value.toMutableSet()
        currentEnabled.add(skillName)
        _enabledSkills.value = currentEnabled
        
        // 更新 Skill 状态
        _skills.value = _skills.value.map { skill ->
            if (skill.skillName == skillName) {
                skill.copy(isEnabled = true)
            } else {
                skill
            }
        }
        
        Log.i(TAG, "Skill enabled: $skillName")
        return true
    }
    
    /**
     * 禁用 Skill
     */
    fun disableSkill(skillName: String): Boolean {
        val currentEnabled = _enabledSkills.value.toMutableSet()
        currentEnabled.remove(skillName)
        _enabledSkills.value = currentEnabled
        
        // 更新 Skill 状态
        _skills.value = _skills.value.map { skill ->
            if (skill.skillName == skillName) {
                skill.copy(isEnabled = false)
            } else {
                skill
            }
        }
        
        Log.i(TAG, "Skill disabled: $skillName")
        return true
    }
    
    /**
     * 检查 Skill 是否已启用
     */
    fun isSkillEnabled(skillName: String): Boolean {
        return _enabledSkills.value.contains(skillName)
    }
    
    /**
     * 安装 Skill
     */
    fun installSkill(skillDefinition: SkillDefinition): Boolean {
        val skillInfo = SkillInfo(
            skillName = skillDefinition.skillName,
            displayName = skillDefinition.displayName,
            description = skillDefinition.description,
            isBuiltIn = false,
            isEnabled = true,
            requiredPermissions = skillDefinition.requiredPermissions
        )
        
        _skills.value = _skills.value + skillInfo
        _enabledSkills.value = _enabledSkills.value + skillDefinition.skillName
        
        Log.i(TAG, "Skill installed: ${skillDefinition.skillName}")
        return true
    }
    
    /**
     * 卸载 Skill
     */
    fun uninstallSkill(skillName: String): Boolean {
        val skill = getSkillInfo(skillName)
        if (skill == null || skill.isBuiltIn) {
            Log.w(TAG, "Cannot uninstall built-in skill: $skillName")
            return false
        }
        
        _skills.value = _skills.value.filter { it.skillName != skillName }
        _enabledSkills.value = _enabledSkills.value - skillName
        
        Log.i(TAG, "Skill uninstalled: $skillName")
        return true
    }
}
