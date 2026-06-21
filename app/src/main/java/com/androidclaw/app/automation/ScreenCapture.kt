// ScreenCapture.kt
// 屏幕截图工具 - 基于 MediaProjection API

package com.androidclaw.app.automation

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 屏幕截图工具
 * 
 * 使用 MediaProjection API 实现截图
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenCapture(
    private val mediaProjection: MediaProjection,
    private val context: Context
) {
    companion object {
        private const val TAG = "ScreenCapture"
        private const val CAPTURE_TIMEOUT_MS = 5000L
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenDensity: Int = 0

    init {
        initDisplayMetrics()
    }

    /**
     * 初始化显示参数
     */
    private fun initDisplayMetrics() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        }

        screenDensity = metrics.densityDpi

        Log.i(TAG, "Screen size: ${screenWidth}x${screenHeight}, density: $screenDensity")
    }

    /**
     * 初始化 ImageReader
     */
    private fun initImageReader() {
        if (imageReader != null) {
            return
        }

        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        ).apply {
            setOnImageAvailableListener({ reader ->
                // 图像可用时的回调
            }, null)
        }

        Log.d(TAG, "ImageReader initialized")
    }

    /**
     * 创建 VirtualDisplay
     */
    private fun createVirtualDisplay() {
        if (virtualDisplay != null) {
            return
        }

        initImageReader()

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        Log.d(TAG, "VirtualDisplay created")
    }

    /**
     * 截图
     */
    suspend fun capture(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            withTimeout(CAPTURE_TIMEOUT_MS) {
                captureInternal()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            null
        }
    }

    /**
     * 内部截图方法
     */
    private suspend fun captureInternal(): Bitmap? {
        createVirtualDisplay()

        val reader = imageReader ?: return null

        // 清空之前的图像
        var image: Image? = null
        repeat(2) {
            image?.close()
            image = reader.acquireLatestImage()
        }

        // 等待新图像
        Thread.sleep(100)

        // 获取图像
        image = reader.acquireLatestImage()

        if (image == null) {
            Log.w(TAG, "No image available")
            return null
        }

        try {
            // 转换为 Bitmap
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmapWidth = screenWidth + rowPadding / pixelStride
            val bitmap = Bitmap.createBitmap(
                bitmapWidth,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )

            bitmap.copyPixelsFromBuffer(buffer)

            // 裁剪掉多余的部分
            val croppedBitmap = if (rowPadding > 0) {
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                bitmap.recycle()
                cropped
            } else {
                bitmap
            }

            Log.d(TAG, "Screenshot captured: ${croppedBitmap.width}x${croppedBitmap.height}")
            croppedBitmap

        } finally {
            image?.close()
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        Log.i(TAG, "Releasing ScreenCapture")

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null
    }
}
