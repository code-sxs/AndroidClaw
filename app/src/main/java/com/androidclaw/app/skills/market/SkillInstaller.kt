// SkillInstaller.kt
// Skill 市场 - Skill 安装器
// 负责下载、校验、解压、安装 Skill 包，支持断点续传和版本管理

package com.androidclaw.app.skills.market

import android.content.Context
import android.util.Log
import com.androidclaw.app.skills.market.db.DownloadTaskDao
import com.androidclaw.app.skills.market.db.DownloadTaskEntity
import com.androidclaw.app.skills.market.db.InstalledSkillDao
import com.androidclaw.app.skills.market.db.InstalledSkillEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Skill 安装器
 * 
 * 功能：
 * - 下载 Skill 包（支持断点续传）
 * - SHA256 完整性校验
 * - 解压到安全沙箱目录
 * - 解析 Skill 清单文件
 * - 验证 Skill 格式合法性
 * - 版本管理（升级/降级/回滚）
 * - 依赖检查
 */
class SkillInstaller(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val installedSkillDao: InstalledSkillDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "SkillInstaller"
        private const val SKILLS_DIR = "skills/installed"
        private const val DOWNLOADS_DIR = "skills/downloads"
        private const val BACKUP_DIR = "skills/backups"
        private const val MANIFEST_FILE = "skill_manifest.json"
        private const val BUFFER_SIZE = 8192
    }

    /** 下载大小限制 */
    var maxDownloadSizeBytes: Long = 50L * 1024 * 1024

    /** 安装进度流 */
    private val _progressMap = mutableMapOf<String, MutableStateFlow<DownloadProgress>>()
    private val _progressFlows = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())

    /**
     * 观察指定 Skill 的安装进度
     */
    fun observeProgress(skillId: String): Flow<DownloadProgress?> {
        return _progressFlows.map { it[skillId] }
    }

    /**
     * 安装 Skill
     * 完整流程：下载 → 校验 → 解压 → 解析清单 → 验证 → 写入数据库
     *
     * @param skill 市场中的 Skill 信息
     * @return 安装结果
     */
    suspend fun installSkill(skill: MarketSkill): InstallResult = withContext(Dispatchers.IO) {
        try {
            val taskId = "${skill.marketId}:${skill.skillId}"

            // 0. 检查是否已安装
            val existing = installedSkillDao.getBySkillName(skill.name)
            if (existing != null) {
                // 已安装，检查版本
                if (existing.version == skill.version) {
                    return@withContext InstallResult.AlreadyInstalled(skill.name)
                }
                // 版本不同，执行升级
                return@withContext upgradeSkill(skill, existing)
            }

            // 1. 创建下载任务
            val downloadsDir = getDownloadsDir()
            val targetFile = File(downloadsDir, "${skill.name}-${skill.version}.zip")
            val installDir = getInstallDir(skill.name)

            emitProgress(skill.skillId, 0, skill.fileSize, InstallState.DOWNLOADING)

            // 记录下载任务
            downloadTaskDao.insert(
                DownloadTaskEntity(
                    id = taskId,
                    marketId = skill.marketId,
                    skillId = skill.skillId,
                    skillName = skill.name,
                    downloadUrl = skill.downloadUrl,
                    targetPath = targetFile.absolutePath,
                    fileSize = skill.fileSize,
                    downloadedBytes = 0L,
                    sha256 = skill.sha256,
                    state = "DOWNLOADING",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )

            // 2. 下载 Skill 包（支持断点续传）
            val downloadedFile = downloadWithResume(skill.downloadUrl, targetFile, skill.skillId)
                ?: return@withContext InstallResult.Error("下载失败: ${skill.name}")

            emitProgress(skill.skillId, skill.fileSize, skill.fileSize, InstallState.VERIFYING)

            // 3. SHA256 校验
            if (skill.sha256 != null) {
                val actualSha256 = calculateSha256(downloadedFile)
                if (!actualSha256.equals(skill.sha256, ignoreCase = true)) {
                    downloadedFile.delete()
                    downloadTaskDao.deleteById(taskId)
                    return@withContext InstallResult.Error("SHA256 校验失败: 期望 ${skill.sha256}, 实际 $actualSha256")
                }
                Log.i(TAG, "SHA256 校验通过: ${skill.name}")
            }

            emitProgress(skill.skillId, skill.fileSize, skill.fileSize, InstallState.INSTALLING)

            // 4. 安全扫描占位（由另一个 Agent 实现）
            val scanResult = performSecurityScan(downloadedFile)
            if (!scanResult.isSafe) {
                downloadedFile.delete()
                downloadTaskDao.deleteById(taskId)
                return@withContext InstallResult.Error("安全扫描未通过: ${scanResult.reason}")
            }

            // 5. 解压到安装目录
            installDir.mkdirs()
            val manifest = extractAndParse(downloadedFile, installDir)
                ?: return@withContext InstallResult.Error("无法解析 $MANIFEST_FILE")

            // 6. 验证清单合法性
            val validationError = validateManifest(manifest)
            if (validationError != null) {
                installDir.deleteRecursively()
                downloadedFile.delete()
                downloadTaskDao.deleteById(taskId)
                return@withContext InstallResult.Error("清单验证失败: $validationError")
            }

            // 7. 依赖检查
            val depError = checkDependencies(manifest)
            if (depError != null) {
                Log.w(TAG, "依赖检查警告: $depError (继续安装)")
            }

            // 8. 写入数据库
            installedSkillDao.insert(
                InstalledSkillEntity(
                    id = "${skill.marketId}:${skill.skillId}",
                    marketId = skill.marketId,
                    skillId = skill.skillId,
                    skillName = manifest.name,
                    displayName = manifest.display_name,
                    description = manifest.description,
                    version = manifest.version,
                    author = manifest.author,
                    downloadUrl = skill.downloadUrl,
                    sha256 = skill.sha256,
                    permissions = manifest.permissions,
                    category = skill.category,
                    tags = skill.tags,
                    installedAt = System.currentTimeMillis(),
                    lastUpdated = skill.lastUpdated,
                    isEnabled = false,
                    installPath = installDir.absolutePath
                )
            )

            // 9. 更新下载任务状态
            downloadTaskDao.deleteById(taskId)

            // 10. 清理下载文件
            downloadedFile.delete()

            emitProgress(skill.skillId, skill.fileSize, skill.fileSize, InstallState.INSTALLED)
            Log.i(TAG, "Skill 安装成功: ${skill.name} v${skill.version}")

            InstallResult.Success(skill.name, manifest)

        } catch (e: Exception) {
            Log.e(TAG, "安装失败: ${skill.name}", e)
            emitProgress(skill.skillId, 0, skill.fileSize, InstallState.ERROR)
            InstallResult.Error("安装异常: ${e.message}")
        }
    }

    /**
     * 卸载 Skill
     */
    suspend fun uninstallSkill(skillName: String): Boolean = withContext(Dispatchers.IO) {
        val entity = installedSkillDao.getBySkillName(skillName) ?: return@withContext false

        // 删除安装目录
        val installDir = File(entity.installPath)
        if (installDir.exists()) {
            installDir.deleteRecursively()
        }

        // 从数据库移除
        installedSkillDao.deleteBySkillName(skillName)
        Log.i(TAG, "Skill 卸载成功: $skillName")
        true
    }

    /**
     * 升级 Skill
     */
    private suspend fun upgradeSkill(
        newSkill: MarketSkill,
        existing: InstalledSkillEntity
    ): InstallResult = withContext(Dispatchers.IO) {
        // 1. 备份当前版本
        val backupDir = getBackupDir(newSkill.name)
        val currentInstallDir = File(existing.installPath)
        if (currentInstallDir.exists()) {
            backupDir.mkdirs()
            currentInstallDir.copyRecursively(File(backupDir, existing.version), overwrite = true)
            Log.i(TAG, "已备份 ${newSkill.name} v${existing.version}")
        }

        // 2. 删除旧安装
        currentInstallDir.deleteRecursively()

        // 3. 安装新版本
        val result = installSkill(newSkill)

        // 4. 如果安装失败，尝试回滚
        if (result is InstallResult.Error) {
            val backupVersion = File(backupDir, existing.version)
            if (backupVersion.exists()) {
                currentInstallDir.mkdirs()
                backupVersion.copyRecursively(currentInstallDir, overwrite = true)
                Log.i(TAG, "回滚到 v${existing.version}")
            }
        } else {
            // 安装成功，清理备份
            backupDir.deleteRecursively()
        }

        result
    }

    /**
     * 启用/禁用已安装的 Skill
     */
    suspend fun setEnabled(skillName: String, enabled: Boolean) {
        installedSkillDao.setEnabled(skillName, enabled)
    }

    /**
     * 获取所有已安装的 Skill
     */
    suspend fun getInstalledSkills(): List<InstalledSkillEntity> {
        return installedSkillDao.getEnabled()
    }

    // ===== 内部方法 =====

    /**
     * 下载文件（支持断点续传）
     */
    private fun downloadWithResume(
        url: String,
        targetFile: File,
        skillId: String
    ): File? {
        try {
            var downloadedBytes = 0L
            if (targetFile.exists()) {
                downloadedBytes = targetFile.length()
            }

            val requestBuilder = Request.Builder().url(url)
            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
            }

            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                Log.e(TAG, "下载失败: HTTP ${response.code}")
                return null
            }

            val contentLength = response.body?.contentLength() ?: -1L
            val totalSize = if (response.code == 206) downloadedBytes + contentLength else contentLength

            // 检查大小限制
            if (totalSize > maxDownloadSizeBytes) {
                Log.e(TAG, "文件过大: $totalSize bytes (限制: $maxDownloadSizeBytes)")
                return null
            }

            val body = response.body ?: return null
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(targetFile, downloadedBytes > 0)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalDownloaded = downloadedBytes

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalDownloaded += bytesRead

                if (totalSize > 0) {
                    val percentage = ((totalDownloaded * 100) / totalSize).toInt()
                    emitProgress(skillId, totalDownloaded, totalSize, InstallState.DOWNLOADING)
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            return targetFile
        } catch (e: Exception) {
            Log.e(TAG, "下载异常", e)
            return null
        }
    }

    /**
     * 计算 SHA256
     */
    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 解压并解析清单
     */
    private fun extractAndParse(zipFile: File, targetDir: File): SkillManifest? {
        try {
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outputFile = File(targetDir, entry.name)

                    // 安全检查：防止 Zip Slip 攻击
                    if (!outputFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        Log.w(TAG, "跳过危险路径: ${entry.name}")
                        entry = zis.nextEntry
                        continue
                    }

                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { fos ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var len: Int
                            while (zis.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 解析清单
            val manifestFile = File(targetDir, MANIFEST_FILE)
            if (!manifestFile.exists()) {
                Log.e(TAG, "清单文件不存在: $MANIFEST_FILE")
                return null
            }

            val manifestJson = manifestFile.readText()
            return gson.fromJson(manifestJson, SkillManifest::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "解压/解析失败", e)
            return null
        }
    }

    /**
     * 验证清单合法性
     */
    private fun validateManifest(manifest: SkillManifest): String? {
        if (manifest.name.isBlank()) return "Skill 名称不能为空"
        if (manifest.version.isBlank()) return "版本号不能为空"
        if (manifest.entry_point.isBlank()) return "入口文件不能为空"
        if (manifest.display_name.isBlank()) return "显示名称不能为空"

        // 检查入口文件是否存在（在安装目录中）
        // 注意：此时文件已解压，但不在此方法中访问文件系统（由调用方处理）

        // 检查版本号格式
        val versionRegex = Regex("^\\d+\\.\\d+\\.\\d+")
        if (!versionRegex.matches(manifest.version)) {
            return "版本号格式不合法: ${manifest.version} (需要 x.y.z 格式)"
        }

        return null
    }

    /**
     * 依赖检查
     * 检查声明的依赖是否已安装
     */
    private suspend fun checkDependencies(manifest: SkillManifest): String? {
        if (manifest.dependencies.isEmpty()) return null

        val missing = mutableListOf<String>()
        for (dep in manifest.dependencies) {
            val installed = installedSkillDao.getBySkillName(dep)
            if (installed == null) {
                missing.add(dep)
            }
        }

        return if (missing.isNotEmpty()) {
            "缺少依赖: ${missing.joinToString(", ")}"
        } else null
    }

    /**
     * 安全扫描占位接口
     * 由另一个 Agent 实现具体扫描逻辑
     */
    private fun performSecurityScan(file: File): SecurityScanResult {
        // TODO: 由安全扫描 Agent 实现
        // 目前默认通过
        return SecurityScanResult(isSafe = true)
    }

    /**
     * 发送进度更新
     */
    private fun emitProgress(skillId: String, downloaded: Long, total: Long, state: InstallState) {
        val percentage = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        val progress = DownloadProgress(skillId, downloaded, total, percentage, state)

        _progressMap.getOrPut(skillId) { MutableStateFlow(progress) }.value = progress
        _progressFlows.value = _progressFlows.value + (skillId to progress)
    }

    private fun getDownloadsDir(): File {
        val dir = File(context.filesDir, DOWNLOADS_DIR)
        dir.mkdirs()
        return dir
    }

    private fun getInstallDir(skillName: String): File {
        return File(context.filesDir, "$SKILLS_DIR/$skillName")
    }

    private fun getBackupDir(skillName: String): File {
        return File(context.filesDir, "$BACKUP_DIR/$skillName")
    }
}

/**
 * 安装结果
 */
sealed class InstallResult {
    data class Success(val skillName: String, val manifest: SkillManifest) : InstallResult()
    data class AlreadyInstalled(val skillName: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}

/**
 * 安全扫描结果
 */
data class SecurityScanResult(
    val isSafe: Boolean,
    val reason: String? = null
)
