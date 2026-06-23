// NetworkSkill.kt
// WiFi/网络 Skill - WiFi 管理和网络状态查询

package com.androidclaw.app.skills

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.util.Log

/**
 * 网络 Skill
 * 提供 WiFi 信息查询、网络连接状态和网络类型检测
 */
class NetworkSkill : SkillDefinition {

    companion object {
        private const val TAG = "NetworkSkill"
    }

    private var wifiManager: WifiManager? = null
    private var connectivityManager: ConnectivityManager? = null

    override val skillName: String = "network"
    override val displayName: String = "网络"
    override val description: String = "WiFi 信息查询、网络连接状态和网络类型检测"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.ACCESS_WIFI_STATE,
        android.Manifest.permission.ACCESS_NETWORK_STATE,
        android.Manifest.permission.INTERNET
    )

    override suspend fun initialize(context: Context) {
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        Log.i(TAG, "NetworkSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "get_wifi_info",
            displayName = "获取 WiFi 信息",
            description = "获取当前连接的 WiFi 网络信息（SSID、IP、信号强度等）",
            parameters = emptyList(),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "is_connected",
            displayName = "是否联网",
            description = "检查设备当前是否有网络连接",
            parameters = emptyList(),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "get_network_type",
            displayName = "网络类型",
            description = "获取当前网络连接类型（wifi/mobile/ethernet/none）",
            parameters = emptyList(),
            returnType = "string"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        return try {
            when (toolName) {
                "get_wifi_info" -> getWifiInfo()
                "is_connected" -> checkConnectivity()
                "get_network_type" -> getNetworkType()
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少网络权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    private fun getWifiInfo(): ToolResult {
        val wm = wifiManager
            ?: return ToolResult.Error("WifiManager not available")

        val wifiInfo: WifiInfo? = wm.connectionInfo

        if (wifiInfo == null || wifiInfo.ssid == null || wifiInfo.ssid == "<unknown ssid>") {
            return ToolResult.Success(mapOf(
                "ssid" to null,
                "bssid" to null,
                "ip_address" to null,
                "signal_strength" to null,
                "signal_level" to "none",
                "frequency" to null,
                "link_speed" to null,
                "is_connected" to false,
                "message" to "未连接 WiFi"
            ))
        }

        // 计算信号强度等级
        val rssi = wifiInfo.rssi
        val signalLevel = WifiManager.calculateSignalLevel(rssi, 5)
        val signalLabel = when (signalLevel) {
            0 -> "very_weak"
            1 -> "weak"
            2 -> "good"
            3 -> "strong"
            4 -> "excellent"
            else -> "unknown"
        }

        // IP 地址转换
        val ipInt = wifiInfo.ipAddress
        val ipAddress = String.format(
            "%d.%d.%d.%d",
            ipInt and 0xFF,
            (ipInt shr 8) and 0xFF,
            (ipInt shr 16) and 0xFF,
            (ipInt shr 24) and 0xFF
        )

        // 去除 SSID 的引号
        val ssid = wifiInfo.ssid?.trim('"')

        return ToolResult.Success(mapOf(
            "ssid" to ssid,
            "bssid" to wifiInfo.bssid,
            "ip_address" to ipAddress,
            "signal_strength" to rssi,
            "signal_level" to signalLabel,
            "signal_level_number" to signalLevel,
            "frequency" to wifiInfo.frequency,
            "link_speed" to wifiInfo.linkSpeed,
            "is_connected" to true
        ))
    }

    private fun checkConnectivity(): ToolResult {
        val cm = connectivityManager
            ?: return ToolResult.Error("ConnectivityManager not available")

        val network = cm.activeNetwork
        val capabilities = network?.let { cm.getNetworkCapabilities(it) }

        val isConnected = capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))

        return ToolResult.Success(mapOf(
            "connected" to isConnected,
            "has_internet" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false),
            "is_validated" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
        ))
    }

    private fun getNetworkType(): ToolResult {
        val cm = connectivityManager
            ?: return ToolResult.Error("ConnectivityManager not available")

        val network = cm.activeNetwork
        val capabilities = network?.let { cm.getNetworkCapabilities(it) }

        val type = when {
            capabilities == null -> "none"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "unknown"
        }

        return ToolResult.Success(mapOf(
            "type" to type,
            "is_connected" to (type != "none"),
            "has_internet" to (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false)
        ))
    }

    override fun release() {
        wifiManager = null
        connectivityManager = null
        Log.i(TAG, "NetworkSkill released")
    }
}
