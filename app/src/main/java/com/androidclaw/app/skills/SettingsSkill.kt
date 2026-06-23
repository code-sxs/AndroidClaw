// SettingsSkill.kt
// 设置 Skill - 常用系统设置读取和修改（修改需用户确认）

package com.androidclaw.app.skills

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log

/**
 * 设置 Skill
 * 读取和修改常用系统设置（亮度、音量、蓝牙、WiFi、飞行模式）
 * 注意：修改操作需要用户在系统对话框中确认
 */
class SettingsSkill : SkillDefinition {

    companion object {
        private const val TAG = "SettingsSkill"

        // 支持读取的设置类型
        private val SUPPORTED_GET_SETTINGS = setOf(
            "brightness", "volume", "bluetooth", "wifi", "airplane"
        )

        // 需要用户确认的修改操作
        private val REQUIRE_CONFIRMATION = setOf(
            "set_setting", "toggle_bluetooth"
        )
    }

    private var context: Context? = null
    private var audioManager: AudioManager? = null
    private var wifiManager: WifiManager? = null

    override val skillName: String = "settings"
    override val displayName: String = "设置"
    override val description: String = "读取和修改常用系统设置（亮度、音量、蓝牙、WiFi、飞行模式），修改需要用户确认"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.WRITE_SETTINGS,
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_ADMIN,
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.CHANGE_WIFI_STATE,
        android.Manifest.permission.CAMERA
    )

    override suspend fun initialize(context: Context) {
        this.context = context
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        Log.i(TAG, "SettingsSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "get_setting",
            displayName = "读取设置",
            description = "读取系统设置的值，支持: brightness/volume/bluetooth/wifi/airplane",
            parameters = listOf(
                ToolParameter("type", "string", true, "设置类型: brightness(亮度)/volume(音量)/bluetooth(蓝牙)/wifi(WiFi)/airplane(飞行模式)")
            ),
            returnType = "string"
        ),
        ToolDefinition(
            toolName = "set_setting",
            displayName = "修改设置",
            description = "修改系统设置（需用户确认），支持: brightness(0-255)/volume(0-15)/wifi(on/off)/airplane(on/off)",
            parameters = listOf(
                ToolParameter("type", "string", true, "设置类型: brightness/volume/wifi/airplane"),
                ToolParameter("value", "string", true, "设置值：brightness(0-255), volume(0-15), wifi(on/off), airplane(on/off)")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "toggle_bluetooth",
            displayName = "开关蓝牙",
            description = "打开或关闭蓝牙（需用户确认）",
            parameters = listOf(
                ToolParameter("enable", "boolean", true, "true=打开, false=关闭")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "toggle_flashlight",
            displayName = "开关手电筒",
            description = "打开或关闭手电筒",
            parameters = listOf(
                ToolParameter("enable", "boolean", true, "true=打开, false=关闭")
            ),
            returnType = "boolean"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val ctx = context
            ?: return ToolResult.Error("SettingsSkill not initialized")

        return try {
            when (toolName) {
                "get_setting" -> getSetting(ctx, parameters)
                "set_setting" -> setSetting(ctx, parameters)
                "toggle_bluetooth" -> toggleBluetooth(parameters)
                "toggle_flashlight" -> toggleFlashlight(ctx, parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少设置权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    private fun getSetting(context: Context, params: Map<String, Any>): ToolResult {
        val type = params["type"] as? String
            ?: return ToolResult.Error("缺少参数: type")

        val normalizedType = type.lowercase().trim()
        if (normalizedType !in SUPPORTED_GET_SETTINGS) {
            return ToolResult.Error("不支持的类型: $type，支持: $SUPPORTED_GET_SETTINGS")
        }

        return when (normalizedType) {
            "brightness" -> getBrightness(context)
            "volume" -> getVolume()
            "bluetooth" -> getBluetoothState()
            "wifi" -> getWifiState()
            "airplane" -> getAirplaneMode(context)
            else -> ToolResult.Error("未知设置类型: $type")
        }
    }

    private fun getBrightness(context: Context): ToolResult {
        val brightness = try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
        } catch (e: Settings.SettingNotFoundException) {
            return ToolResult.Error("无法获取亮度设置")
        }

        val isAuto = try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        } catch (e: Exception) {
            false
        }

        return ToolResult.Success(mapOf(
            "type" to "brightness",
            "value" to brightness,
            "value_display" to "${(brightness * 100 / 255)}%",
            "max" to 255,
            "is_auto" to isAuto
        ))
    }

    private fun getVolume(): ToolResult {
        val am = audioManager ?: return ToolResult.Error("AudioManager not available")

        val mediaVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val mediaMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val ringVolume = am.getStreamVolume(AudioManager.STREAM_RING)
        val ringMax = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        val alarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        val alarmMax = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)

        return ToolResult.Success(mapOf(
            "type" to "volume",
            "media" to mapOf("value" to mediaVolume, "max" to mediaMax),
            "ring" to mapOf("value" to ringVolume, "max" to ringMax),
            "alarm" to mapOf("value" to alarmVolume, "max" to alarmMax)
        ))
    }

    private fun getBluetoothState(): ToolResult {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            return ToolResult.Success(mapOf(
                "type" to "bluetooth",
                "available" to false,
                "message" to "设备不支持蓝牙"
            ))
        }

        return ToolResult.Success(mapOf(
            "type" to "bluetooth",
            "available" to true,
            "enabled" to adapter.isEnabled,
            "state" to when (adapter.state) {
                BluetoothAdapter.STATE_OFF -> "off"
                BluetoothAdapter.STATE_TURNING_ON -> "turning_on"
                BluetoothAdapter.STATE_ON -> "on"
                BluetoothAdapter.STATE_TURNING_OFF -> "turning_off"
                else -> "unknown"
            },
            "name" to adapter.name,
            "address" to adapter.address
        ))
    }

    private fun getWifiState(): ToolResult {
        val wm = wifiManager ?: return ToolResult.Error("WifiManager not available")

        return ToolResult.Success(mapOf(
            "type" to "wifi",
            "enabled" to wm.isWifiEnabled,
            "state" to when (wm.wifiState) {
                WifiManager.WIFI_STATE_DISABLED -> "off"
                WifiManager.WIFI_STATE_ENABLED -> "on"
                WifiManager.WIFI_STATE_DISABLING -> "turning_off"
                WifiManager.WIFI_STATE_ENABLING -> "turning_on"
                else -> "unknown"
            }
        ))
    }

    private fun getAirplaneMode(context: Context): ToolResult {
        val isAirplaneMode = try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON
            ) != 0
        } catch (e: Exception) {
            false
        }

        return ToolResult.Success(mapOf(
            "type" to "airplane",
            "enabled" to isAirplaneMode,
            "message" to if (isAirplaneMode) "飞行模式已开启" else "飞行模式已关闭"
        ))
    }

    /**
     * 修改设置（通过 Intent 打开系统设置页面让用户确认）
     */
    private fun setSetting(context: Context, params: Map<String, Any>): ToolResult {
        val type = params["type"] as? String
            ?: return ToolResult.Error("缺少参数: type")
        val value = params["value"] as? String
            ?: return ToolResult.Error("缺少参数: value")

        return when (type.lowercase().trim()) {
            "brightness" -> setBrightness(context, value)
            "volume" -> setVolume(value)
            "wifi" -> setWifi(value)
            "airplane" -> setAirplaneMode(context)
            else -> ToolResult.Error("不支持修改的设置类型: $type")
        }
    }

    private fun setBrightness(context: Context, value: String): ToolResult {
        val brightness = value.toIntOrNull()
            ?: return ToolResult.Error("无效的亮度值: $value（应为 0-255 的数字）")

        if (brightness !in 0..255) {
            return ToolResult.Error("亮度值超出范围: $brightness（应为 0-255）")
        }

        // 需要 WRITE_SETTINGS 权限
        if (!Settings.System.canWrite(context)) {
            return ToolResult.Error(
                "未获得修改系统设置权限，请先在应用权限管理中授予"
            )
        }

        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightness
        )

        Log.i(TAG, "Brightness set to $brightness")
        return ToolResult.Success(mapOf(
            "status" to "updated",
            "type" to "brightness",
            "value" to brightness,
            "value_display" to "${(brightness * 100 / 255)}%"
        ))
    }

    private fun setVolume(value: String): ToolResult {
        val am = audioManager ?: return ToolResult.Error("AudioManager not available")

        val volume = value.toIntOrNull()
            ?: return ToolResult.Error("无效的音量值: $value（应为 0-15 的数字）")

        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (volume !in 0..maxVolume) {
            return ToolResult.Error("音量值超出范围: $volume（应为 0-$maxVolume）")
        }

        am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)

        Log.i(TAG, "Media volume set to $volume")
        return ToolResult.Success(mapOf(
            "status" to "updated",
            "type" to "volume",
            "stream" to "media",
            "value" to volume,
            "max" to maxVolume
        ))
    }

    private fun setWifi(value: String): ToolResult {
        val wm = wifiManager ?: return ToolResult.Error("WifiManager not available")

        val enable = when (value.lowercase()) {
            "on", "true", "1", "enable" -> true
            "off", "false", "0", "disable" -> false
            else -> return ToolResult.Error("无效的 WiFi 值: $value（应为 on/off）")
        }

        wm.isWifiEnabled = enable

        Log.i(TAG, "WiFi turned ${if (enable) "on" else "off"}")
        return ToolResult.Success(mapOf(
            "status" to if (enable) "enabled" else "disabled",
            "type" to "wifi"
        ))
    }

    private fun setAirplaneMode(context: Context): ToolResult {
        // 通过 Intent 打开飞行模式设置页面
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        return ToolResult.Success(mapOf(
            "status" to "user_confirmation_required",
            "type" to "airplane",
            "message" to "请在打开的设置页面中手动切换飞行模式"
        ))
    }

    /**
     * 开关蓝牙（通过 Intent 让用户确认权限）
     */
    private fun toggleBluetooth(params: Map<String, Any>): ToolResult {
        val enable = params["enable"] as? Boolean
            ?: return ToolResult.Error("缺少参数: enable")

        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            return ToolResult.Error("设备不支持蓝牙")
        }

        val ctx = context ?: return ToolResult.Error("Context not available")

        // Android 10+ 需要用户通过系统设置页面确认
        // 使用 ACTION_REQUEST_ENABLE 可以让系统弹窗请求用户确认
        if (enable && !adapter.isEnabled) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return ToolResult.Success(mapOf(
                "status" to "user_confirmation_required",
                "type" to "bluetooth",
                "action" to "enable",
                "message" to "请在弹出的蓝牙权限请求中确认"
            ))
        } else if (!enable && adapter.isEnabled) {
            // 关闭蓝牙可以直接操作
            val success = adapter.disable()
            return if (success) {
                ToolResult.Success(mapOf(
                    "status" to "disabled",
                    "type" to "bluetooth"
                ))
            } else {
                ToolResult.Error("蓝牙关闭失败")
            }
        }

        return ToolResult.Success(mapOf(
            "type" to "bluetooth",
            "enabled" to adapter.isEnabled,
            "message" to "蓝牙状态未变更"
        ))
    }

    /**
     * 开关手电筒
     */
    private fun toggleFlashlight(context: Context, params: Map<String, Any>): ToolResult {
        val enable = params["enable"] as? Boolean
            ?: return ToolResult.Error("缺少参数: enable")

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            ?: return ToolResult.Error("CameraManager not available")

        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(
                    android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
                ) ?: false
                flashAvailable
            } ?: return ToolResult.Error("找不到可用的闪光灯")

            cameraManager.setTorchMode(cameraId, enable)

            Log.i(TAG, "Flashlight turned ${if (enable) "on" else "off"}")
            return ToolResult.Success(mapOf(
                "status" to if (enable) "on" else "off",
                "type" to "flashlight"
            ))
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied for flashlight", e)
            ToolResult.Error("缺少相机权限来使用手电筒")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
            ToolResult.Error("手电筒操作失败: ${e.message}", e)
        }
    }

    override fun release() {
        context = null
        audioManager = null
        wifiManager = null
        Log.i(TAG, "SettingsSkill released")
    }
}
