// AndroidClawApplication.kt
// 应用入口，初始化依赖

package com.androidclaw.app

import android.app.Application
import android.util.Log
import com.androidclaw.app.llm.LLMManager
import com.androidclaw.app.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AndroidClawApplication : Application() {

    companion object {
        private const val TAG = "AndroidClawApp"
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 延迟初始化依赖
    private lateinit var _llmManager: LLMManager
    private lateinit var _appRepository: AppRepository

    val llmManager: LLMManager
        get() = _llmManager

    val appRepository: AppRepository
        get() = _appRepository

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AndroidClawApplication onCreate")

        // 初始化依赖
        initializeDependencies()

        // 预检测硬件 (在后台线程)
        applicationScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Detecting hardware capabilities...")
            _llmManager.detectHardware()
        }
    }

    private fun initializeDependencies() {
        // 初始化 LLM 管理器 (三引擎)
        _llmManager = LLMManager.getInstance(this)

        // 初始化数据仓库
        _appRepository = AppRepository.getInstance(this)
    }
}
