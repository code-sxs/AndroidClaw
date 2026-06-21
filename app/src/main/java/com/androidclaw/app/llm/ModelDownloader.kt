// ModelDownloader.kt
// 模型下载管理器
// 支持断点续传、进度回调、校验

package com.androidclaw.app.llm

import android.content.Context
import android.util.Log
import com.androidclaw.app.llm.model.DownloadState
import com.androidclaw.app.llm.model.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * 模型下载器 - 单例模式
 */
class ModelDownloader private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ModelDownloader"
        private const val DOWNLOAD_DIR = "models"
        private const val BUFFER_SIZE = 8192 // 8KB

        private var INSTANCE: ModelDownloader? = null

        fun getInstance(context: Context): ModelDownloader {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelDownloader(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val downloadDir: File
        get() = File(context.filesDir, DOWNLOAD_DIR)

    init {
        // 创建下载目录
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
    }

    /**
     * 下载模型 (Flow 版本 - 推荐)
     * 返回下载状态流
     */
    fun downloadModel(modelConfig: ModelConfig): Flow<DownloadState> = callbackFlow {
        Log.i(TAG, "Starting download: ${modelConfig.modelName}")

        val outputFile = File(downloadDir, modelConfig.fileName)
        val tempFile = File(downloadDir, "${modelConfig.fileName}.tmp")

        try {
            // 1. 检查是否已下载
            if (outputFile.exists() && verifyChecksum(outputFile, modelConfig.md5Checksum)) {
                Log.i(TAG, "Model already downloaded and verified: ${modelConfig.modelName}")
                trySend(DownloadState.Completed(outputFile.absolutePath))
                close()
                return@callbackFlow
            }

            // 2. 创建请求
            val requestBuilder = Request.Builder()
                .url(modelConfig.downloadUrl)
                .header("User-Agent", "AndroidClaw/0.1.0")

            // 3. 断点续传
            val downloadedBytes = if (tempFile.exists()) {
                requestBuilder.header("Range", "bytes=${tempFile.length()}-")
                tempFile.length()
            } else {
                0L
            }

            val request = requestBuilder.build()
            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val error = "HTTP ${response.code}: ${response.message}"
                Log.e(TAG, "Download failed: $error")
                trySend(DownloadState.Failed(error))
                close()
                return@callbackFlow
            }

            // 4. 获取文件总大小
            val totalBytes = response.body?.contentLength() ?: modelConfig.fileSizeInBytes
            val actualTotalBytes = if (downloadedBytes > 0) {
                totalBytes + downloadedBytes
            } else {
                totalBytes
            }

            // 5. 开始下载
            response.body?.byteStream()?.use { inputStream ->
                RandomAccessFile(tempFile, "rw").use { outputStream ->
                    // 移动到已下载位置
                    if (downloadedBytes > 0) {
                        outputStream.seek(downloadedBytes)
                    }

                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesDownloaded = downloadedBytes
                    var bytesRead: Int

                    // 发送初始进度
                    val initialProgress = if (actualTotalBytes > 0) {
                        bytesDownloaded.toFloat() / actualTotalBytes.toFloat()
                    } else {
                        0f
                    }
                    trySend(DownloadState.Downloading(initialProgress, bytesDownloaded, actualTotalBytes))

                    // 读取数据
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        // 计算进度
                        val progress = if (actualTotalBytes > 0) {
                            bytesDownloaded.toFloat() / actualTotalBytes.toFloat()
                        } else {
                            0f
                        }

                        // 发送进度更新
                        trySend(DownloadState.Downloading(progress, bytesDownloaded, actualTotalBytes))
                    }
                }
            }

            // 6. 下载完成，校验 MD5
            if (verifyChecksum(tempFile, modelConfig.md5Checksum)) {
                // 重命名临时文件为正式文件
                tempFile.renameTo(outputFile)
                Log.i(TAG, "Download completed and verified: ${modelConfig.modelName}")
                trySend(DownloadState.Completed(outputFile.absolutePath))
            } else {
                val error = "MD5 checksum verification failed"
                Log.e(TAG, error)
                trySend(DownloadState.Failed(error))
            }

        } catch (e: IOException) {
            Log.e(TAG, "Download failed", e)
            trySend(DownloadState.Failed(e.message ?: "Unknown error"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            trySend(DownloadState.Failed(e.message ?: "Unknown error"))
        }

        close()
    }

    /**
     * 验证 MD5 校验和
     */
    private fun verifyChecksum(file: File, expectedMD5: String): Boolean {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val inputStream = file.inputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }

            val digest = md.digest()
            val actualMD5 = digest.joinToString("") { "%02x".format(it) }

            val match = actualMD5.equals(expectedMD5, ignoreCase = true)
            if (!match) {
                Log.w(TAG, "MD5 mismatch: expected=$expectedMD5, actual=$actualMD5")
            }
            match
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify checksum", e)
            false
        }
    }

    /**
     * 获取已下载的模型文件
     */
    fun getDownloadedModel(modelConfig: ModelConfig): File? {
        val file = File(downloadDir, modelConfig.fileName)
        return if (file.exists() && verifyChecksum(file, modelConfig.md5Checksum)) {
            file
        } else {
            null
        }
    }

    /**
     * 删除已下载的模型
     */
    fun deleteModel(modelConfig: ModelConfig): Boolean {
        val file = File(downloadDir, modelConfig.fileName)
        val tempFile = File(downloadDir, "${modelConfig.fileName}.tmp")

        var success = true
        if (file.exists()) {
            success = file.delete()
        }
        if (tempFile.exists()) {
            success = tempFile.delete() && success
        }

        return success
    }

    /**
     * 获取已下载模型的大小
     */
    fun getDownloadedModelSize(): Long {
        return downloadDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * 清空所有已下载的模型
     */
    fun clearAllModels(): Boolean {
        return downloadDir.listFiles()?.all { it.delete() } ?: true
    }
}
