// AppRepository.kt
// 应用数据仓库 - 提供统一的数据访问接口

package com.androidclaw.app.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用数据仓库
 * 
 * 功能:
 * 1. 管理应用设置
 * 2. 提供数据访问接口
 * 3. 协调本地数据源
 */
class AppRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AppRepository"
        private var INSTANCE: AppRepository? = null

        fun getInstance(context: Context): AppRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // DataStore for preferences
    private val Context.dataStore by preferencesDataStore(name = "androidclaw_prefs")
    
    init {
        Log.i(TAG, "AppRepository initialized")
    }

    // 示例：保存和读取设置
    suspend fun saveSetting(key: String, value: String) {
        val prefKey = stringPreferencesKey(key)
        context.dataStore.edit { preferences ->
            preferences[prefKey] = value
        }
    }

    fun getSetting(key: String): Flow<String?> {
        val prefKey = stringPreferencesKey(key)
        return context.dataStore.data.map { preferences ->
            preferences[prefKey]
        }
    }

    // 清理资源
    fun cleanup() {
        Log.i(TAG, "Cleaning up AppRepository")
        // 清理缓存、关闭数据库连接等
    }
}
