// HardwareDetector.kt
// 硬件能力检测

package com.androidclaw.app.llm.model

import android.content.Context
import android.opengl.GLES20
import android.os.Build
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 硬件检测器
 * 检测设备的 GPU/NPU/CPU 能力
 */
object HardwareDetector {

    private const val TAG = "HardwareDetector"

    /**
     * 检测硬件能力
     */
    fun detect(context: Context): HardwareCapability {
        Log.i(TAG, "Starting hardware detection...")

        val hasGPU = detectGPU()
        val hasNPU = detectNPU()
        val gpuRenderer = getGPURenderer()
        val totalRAM = getTotalRAM()
        val availableRAM = getAvailableRAM()
        val androidVersion = Build.VERSION.SDK_INT
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        val capability = HardwareCapability(
            hasGPU = hasGPU,
            hasNPU = hasNPU,
            gpuRenderer = gpuRenderer,
            totalRAM = totalRAM,
            availableRAM = availableRAM,
            androidVersion = androidVersion,
            abi = abi
        )

        Log.i(TAG, "Hardware detection completed: $capability")
        return capability
    }

    /**
     * 检测 GPU
     * 检查是否支持 OpenCL / Vulkan
     */
    private fun detectGPU(): Boolean {
        return try {
            // 尝试获取 GPU 渲染器信息
            val renderer = getGPURenderer()
            val hasGPU = renderer.isNotEmpty() &&
                    !renderer.contains("llvmpipe") &&  // 排除软件渲染
                    !renderer.contains("swiftShader")

            Log.d(TAG, "GPU detected: $hasGPU (renderer: $renderer)")
            hasGPU
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect GPU", e)
            false
        }
    }

    /**
     * 获取 GPU 渲染器名称
     */
    private fun getGPURenderer(): String {
        return try {
            val renderer = android.opengl.GLES20.glGetString(GLES20.GL_RENDERER)
            renderer ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get GPU renderer", e)
            ""
        }
    }

    /**
     * 检测 NPU (神经网络加速芯片)
     * 检查是否有神经网路 API 支持
     */
    private fun detectNPU(): Boolean {
        return try {
            // 检查是否有 NNAPI (Android Neural Networks API)
            val hasNNAPI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

            // 检查常见的 NPU 厂商 (通过 GPU 渲染器名称判断)
            val renderer = getGPURenderer().lowercase()
            val hasKnownNPU = renderer.contains("adreno") ||  // Qualcomm
                    renderer.contains("mali") ||           // ARM
                    renderer.contains("powervr") ||        // Imagination
                    renderer.contains("nvidia")            // NVIDIA

            val hasNPU = hasNNAPI && hasKnownNPU
            Log.d(TAG, "NPU detected: $hasNPU")
            hasNPU
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect NPU", e)
            false
        }
    }

    /**
     * 获取总 RAM
     */
    private fun getTotalRAM(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get total RAM", e)
            0L
        }
    }

    /**
     * 获取可用 RAM
     */
    private fun getAvailableRAM(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.availMem
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available RAM", e)
            0L
        }
    }
}
