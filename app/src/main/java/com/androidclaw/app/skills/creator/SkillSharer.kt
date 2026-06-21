// SkillSharer.kt
// Skill 创建器 - Skill 共享客户端
// 将本地 Skill 打包、脱敏并上传到市场

package com.androidclaw.app.skills.creator

import android.util.Log
import com.androidclaw.app.skills.market.MarketSource
import com.androidclaw.app.skills.market.SkillManifest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Skill 共享客户端
 * 
 * 功能：
 * - 将本地 Skill 打包（.zip）
 * - 执行脱敏处理
 * - 上传到用户选择的 Skill 市场
 * - 填写 Skill 元数据
 * - 支持版本更新
 * - 查看已分享的 Skill 列表
 * - 撤回已分享的 Skill
 */
class SkillSharer(
    private val okHttpClient: OkHttpClient,
    private val desensitizer: Desensitizer,
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "SkillSharer"
        private const val UPLOAD_TIMEOUT_S = 60L
    }

    /**
     * 分享结果
     */
    data class ShareResult(
        val success: Boolean,
        val skillName: String,
        val shareUrl: String?,
        val marketId: String?,
        val error: String?,
        val desensitizeReport: Desensitizer.DesensitizeReport?
    )

    /**
     * 分享请求
     */
    data class ShareRequest(
        val skillDir: File,
        val skillName: String,
        val displayName: String,
        val description: String,
        val version: String = "1.0.0",
        val author: String,
        val tags: List<String> = emptyList(),
        val category: String = "general",
        val targetMarkets: List<String>,
        val skipDesensitize: Boolean = false
    )

    /**
     * 已分享的 Skill 记录
     */
    data class SharedSkill(
        val skillId: String,
        val skillName: String,
        val marketId: String,
        val shareUrl: String,
        val shareTime: Long,
        val version: String,
        val status: String // "active", "withdrawn", "error"
    )

    // 已分享的 Skill 缓存
    private val sharedSkillsCache = mutableMapOf<String, MutableList<SharedSkill>>()

    /**
     * 分享 Skill
     * 
     * @param request 分享请求
     * @param onProgress 进度回调
     */
    suspend fun share(
        request: ShareRequest,
        onProgress: (String) -> Unit = {}
    ): List<ShareResult> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Sharing skill: ${request.skillName}")
        val results = mutableListOf<ShareResult>()
        
        // 1. 脱敏检查
        onProgress("正在扫描敏感信息...")
        val desensitizeReport = desensitizer.scan(request.skillDir)
        
        var skillDirToShare = request.skillDir
        
        if (!request.skipDesensitize && desensitizeReport.findings.isNotEmpty()) {
            onProgress("发现 ${desensitizeReport.findings.size} 处敏感信息，正在处理...")
            
            val tempDir = File(System.getProperty("java.io.tmpdir"), "skill_share_${System.currentTimeMillis()}")
            skillDirToShare = desensitizer.process(request.skillDir, tempDir, desensitizeReport)
        }

        // 2. 打包
        onProgress("正在打包...")
        val zipFile = packSkill(skillDirToShare, request)
        
        if (zipFile == null) {
            results.add(
                ShareResult(
                    success = false,
                    skillName = request.skillName,
                    shareUrl = null,
                    marketId = null,
                    error = "打包失败",
                    desensitizeReport = desensitizeReport
                )
            )
            return@withContext results
        }

        // 3. 上传到各个市场
        for (marketId in request.targetMarkets) {
            onProgress("正在上传到 $marketId...")
            
            val result = uploadToMarket(zipFile, marketId, request, desensitizeReport)
            results.add(result)
            
            if (result.success) {
                // 记录分享历史
                recordSharedSkill(
                    SharedSkill(
                        skillId = UUID.randomUUID().toString(),
                        skillName = request.skillName,
                        marketId = marketId,
                        shareUrl = result.shareUrl ?: "",
                        shareTime = System.currentTimeMillis(),
                        version = request.version,
                        status = "active"
                    )
                )
            }
        }

        // 4. 清理临时文件
        if (skillDirToShare != request.skillDir) {
            skillDirToShare.deleteRecursively()
        }
        zipFile.delete()

        onProgress("分享完成！")
        Log.i(TAG, "Share completed: ${results.count { it.success }}/${results.size} succeeded")
        
        results
    }

    /**
     * 打包 Skill
     */
    private fun packSkill(skillDir: File, request: ShareRequest): File? {
        return try {
            val zipFile = File(
                System.getProperty("java.io.tmpdir"),
                "${request.skillName}_${request.version}.zip"
            )
            
            ZipOutputStream(zipFile.outputStream()).use { zos ->
                skillDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relativePath = file.relativeTo(skillDir).path
                        val entry = ZipEntry(relativePath)
                        zos.putNextEntry(entry)
                        FileInputStream(file).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
                
                // 添加元数据文件
                val metadata = mapOf(
                    "skillName" to request.skillName,
                    "displayName" to request.displayName,
                    "description" to request.description,
                    "version" to request.version,
                    "author" to request.author,
                    "tags" to request.tags,
                    "category" to request.category,
                    "packagedAt" to System.currentTimeMillis()
                )
                val metadataEntry = ZipEntry("share_metadata.json")
                zos.putNextEntry(metadataEntry)
                zos.write(gson.toJson(metadata).toByteArray())
                zos.closeEntry()
            }
            
            Log.d(TAG, "Skill packed: ${zipFile.absolutePath}, size=${zipFile.length()}")
            zipFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pack skill", e)
            null
        }
    }

    /**
     * 上传到市场
     */
    private fun uploadToMarket(
        zipFile: File,
        marketId: String,
        request: ShareRequest,
        report: Desensitizer.DesensitizeReport?
    ): ShareResult {
        val market = MarketSource.DEFAULT_SOURCES.find { it.id == marketId }
            ?: return ShareResult(
                success = false,
                skillName = request.skillName,
                shareUrl = null,
                marketId = marketId,
                error = "未知的市场: $marketId",
                desensitizeReport = report
            )

        return try {
            val uploadUrl = "${market.apiEndpoint}/skills/upload"
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", request.skillName)
                .addFormDataPart("display_name", request.displayName)
                .addFormDataPart("description", request.description)
                .addFormDataPart("version", request.version)
                .addFormDataPart("author", request.author)
                .addFormDataPart("category", request.category)
                .addFormDataPart("tags", request.tags.joinToString(","))
                .addFormDataPart(
                    "file",
                    zipFile.name,
                    zipFile.asRequestBody("application/zip".toMediaType())
                )
                .build()

            val httpRequest = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()

            val client = okHttpClient.newBuilder()
                .connectTimeout(UPLOAD_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(UPLOAD_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(UPLOAD_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val response = client.newCall(httpRequest).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val jsonResponse = responseBody?.let { gson.fromJson(it, Map::class.java) }
                
                @Suppress("UNCHECKED_CAST")
                val shareUrl = (jsonResponse?.get("url") as? String)
                    ?: "${market.baseUrl}/skills/${request.skillName}"

                Log.i(TAG, "Upload succeeded: $marketId -> $shareUrl")
                
                ShareResult(
                    success = true,
                    skillName = request.skillName,
                    shareUrl = shareUrl,
                    marketId = marketId,
                    error = null,
                    desensitizeReport = report
                )
            } else {
                val errorMsg = "上传失败: HTTP ${response.code}"
                Log.e(TAG, errorMsg)
                
                ShareResult(
                    success = false,
                    skillName = request.skillName,
                    shareUrl = null,
                    marketId = marketId,
                    error = errorMsg,
                    desensitizeReport = report
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload to $marketId", e)
            ShareResult(
                success = false,
                skillName = request.skillName,
                shareUrl = null,
                marketId = marketId,
                error = "上传异常: ${e.message}",
                desensitizeReport = report
            )
        }
    }

    /**
     * 记录已分享的 Skill
     */
    private fun recordSharedSkill(skill: SharedSkill) {
        sharedSkillsCache.getOrPut(skill.skillName) { mutableListOf() }.add(skill)
    }

    /**
     * 获取已分享的 Skill 列表
     */
    fun getSharedSkills(): List<SharedSkill> {
        return sharedSkillsCache.values.flatten().sortedByDescending { it.shareTime }
    }

    /**
     * 获取指定 Skill 的分享记录
     */
    fun getSharedSkillsByName(skillName: String): List<SharedSkill> {
        return sharedSkillsCache[skillName]?.toList() ?: emptyList()
    }

    /**
     * 撤回已分享的 Skill
     */
    suspend fun withdraw(skill: SharedSkill): Boolean = withContext(Dispatchers.IO) {
        val market = MarketSource.DEFAULT_SOURCES.find { it.id == skill.marketId }
        if (market == null) {
            Log.e(TAG, "Unknown market: ${skill.marketId}")
            return@withContext false
        }

        return@withContext try {
            val withdrawUrl = "${market.apiEndpoint}/skills/${skill.skillId}/withdraw"
            
            val request = Request.Builder()
                .url(withdrawUrl)
                .delete()
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                // 更新状态
                sharedSkillsCache[skill.skillName]?.let { list ->
                    val index = list.indexOfFirst { it.skillId == skill.skillId }
                    if (index >= 0) {
                        list[index] = skill.copy(status = "withdrawn")
                    }
                }
                Log.i(TAG, "Skill withdrawn: ${skill.skillName} from ${skill.marketId}")
                true
            } else {
                Log.e(TAG, "Withdraw failed: HTTP ${response.code}")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to withdraw skill", e)
            false
        }
    }

    /**
     * 更新已分享的 Skill
     */
    suspend fun update(
        skillDir: File,
        newVersion: String,
        previousShare: SharedSkill,
        onProgress: (String) -> Unit = {}
    ): ShareResult {
        val request = ShareRequest(
            skillDir = skillDir,
            skillName = previousShare.skillName,
            displayName = previousShare.skillName,
            description = "",
            version = newVersion,
            author = "",
            targetMarkets = listOf(previousShare.marketId)
        )
        
        val results = share(request, onProgress)
        return results.firstOrNull() ?: ShareResult(
            success = false,
            skillName = previousShare.skillName,
            shareUrl = null,
            marketId = previousShare.marketId,
            error = "更新失败",
            desensitizeReport = null
        )
    }

    /**
     * 验证 Skill 是否可以分享
     */
    fun validateForShare(skillDir: File): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()
        
        // 检查必要文件
        val manifestFile = File(skillDir, "skill_manifest.json")
        if (!manifestFile.exists()) {
            errors.add("缺少 skill_manifest.json 文件")
        }
        
        val sourceFiles = skillDir.listFiles()?.filter { 
            it.extension in listOf("kt", "kts", "java") 
        } ?: emptyList()
        
        if (sourceFiles.isEmpty()) {
            errors.add("缺少源代码文件 (.kt/.java)")
        }
        
        // 检查 manifest 内容
        if (manifestFile.exists()) {
            try {
                val manifest = gson.fromJson(manifestFile.readText(), SkillManifest::class.java)
                if (manifest.name.isBlank()) errors.add("Skill 名称不能为空")
                if (manifest.version.isBlank()) errors.add("版本号不能为空")
                if (manifest.tools.isEmpty()) errors.add("至少需要一个工具定义")
            } catch (e: Exception) {
                errors.add("skill_manifest.json 格式错误: ${e.message}")
            }
        }
        
        // 检查文件大小
        val totalSize = skillDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        if (totalSize > 50 * 1024 * 1024) {
            errors.add("Skill 总大小超过 50MB 限制")
        }
        
        return Pair(errors.isEmpty(), errors)
    }
}
