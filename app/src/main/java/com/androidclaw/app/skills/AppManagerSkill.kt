// AppManagerSkill.kt
// 应用管理 Skill - 查询已安装应用、打开应用、获取应用信息

package com.androidclaw.app.skills

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * 应用管理 Skill
 * 查询已安装应用、打开应用、获取应用详情
 */
class AppManagerSkill : SkillDefinition {

    companion object {
        private const val TAG = "AppManagerSkill"
        private const val MAX_ICON_SIZE = 48 // dp

        // 应用类别常量（与 Android ApplicationInfo 中的常量值相同）
        private const val CATEGORY_FINANCE = 101
        private const val CATEGORY_COMMUNICATION = 102
        private const val CATEGORY_ENTERTAINMENT = 103
        private const val CATEGORY_EDUCATION = 104
        private const val CATEGORY_BUSINESS = 105
        private const val CATEGORY_MEDICAL = 106
    }

    private var packageManager: PackageManager? = null
    private var context: Context? = null

    override val skillName: String = "app_manager"
    override val displayName: String = "应用管理"
    override val description: String = "查询已安装应用、打开应用和获取应用详情"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.QUERY_ALL_PACKAGES
    )

    override suspend fun initialize(context: Context) {
        this.context = context
        this.packageManager = context.packageManager
        Log.i(TAG, "AppManagerSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "list_apps",
            displayName = "列出应用",
            description = "列出已安装应用，可按名称过滤",
            parameters = listOf(
                ToolParameter("query", "string", false, "搜索关键词（应用名或包名），不填则列出所有"),
                ToolParameter("include_system", "boolean", false, "是否包含系统应用，默认 false")
            ),
            returnType = "list"
        ),
        ToolDefinition(
            toolName = "launch_app",
            displayName = "打开应用",
            description = "通过包名启动应用",
            parameters = listOf(
                ToolParameter("package_name", "string", true, "应用的包名")
            ),
            returnType = "boolean"
        ),
        ToolDefinition(
            toolName = "get_app_info",
            displayName = "应用详情",
            description = "获取指定应用的详细信息（版本、大小、权限等）",
            parameters = listOf(
                ToolParameter("package_name", "string", true, "应用的包名")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "open_app_settings",
            displayName = "打开应用设置",
            description = "打开系统设置中指定应用的详情页",
            parameters = listOf(
                ToolParameter("package_name", "string", true, "应用的包名")
            ),
            returnType = "boolean"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        val pm = packageManager
            ?: return ToolResult.Error("AppManagerSkill not initialized")

        return try {
            when (toolName) {
                "list_apps" -> listApps(pm, parameters)
                "launch_app" -> launchApp(pm, parameters)
                "get_app_info" -> getAppInfo(pm, parameters)
                "open_app_settings" -> openAppSettings(parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少查询权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("执行失败: ${e.message}", e)
        }
    }

    private fun listApps(pm: PackageManager, params: Map<String, Any>): ToolResult {
        val query = params["query"] as? String
        val includeSystem = params["include_system"] as? Boolean ?: false

        // 获取所有已安装应用
        val apps = pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))

        val filteredApps = apps
            .filter { app ->
                // 过滤系统应用
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) return@filter false

                // 按关键词过滤
                if (query != null && query.isNotBlank()) {
                    val appName = pm.getApplicationLabel(app).toString().lowercase()
                    val packageName = app.packageName.lowercase()
                    appName.contains(query.lowercase()) || packageName.contains(query.lowercase())
                } else {
                    true
                }
            }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
            .map { app ->
                mapOf(
                    "name" to pm.getApplicationLabel(app).toString(),
                    "package_name" to app.packageName,
                    "is_system" to ((app.flags and ApplicationInfo.FLAG_SYSTEM) != 0),
                    "is_disabled" to (!app.enabled),
                    "category" to getCategoryName(app.category)
                )
            }

        Log.d(TAG, "Listed ${filteredApps.size} apps (query=$query, system=$includeSystem)")
        return ToolResult.Success(mapOf(
            "apps" to filteredApps,
            "total" to filteredApps.size,
            "query" to query
        ))
    }

    private fun launchApp(pm: PackageManager, params: Map<String, Any>): ToolResult {
        val packageName = params["package_name"] as? String
            ?: return ToolResult.Error("缺少参数: package_name")

        val ctx = context ?: return ToolResult.Error("Context not available")

        // 获取启动 Intent
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            return ToolResult.Error("找不到应用或无法启动: $packageName")
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launchIntent)

        Log.i(TAG, "Launched app: $packageName")
        return ToolResult.Success(mapOf(
            "status" to "launched",
            "package_name" to packageName
        ))
    }

    private fun getAppInfo(pm: PackageManager, params: Map<String, Any>): ToolResult {
        val packageName = params["package_name"] as? String
            ?: return ToolResult.Error("缺少参数: package_name")

        val packageInfo: PackageInfo = try {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(
                (PackageManager.GET_PERMISSIONS or
                        PackageManager.GET_ACTIVITIES or
                        PackageManager.GET_SERVICES or
                        PackageManager.GET_RECEIVERS).toLong()
            ))
        } catch (e: PackageManager.NameNotFoundException) {
            return ToolResult.Error("未找到应用: $packageName")
        }

        val applicationInfo = packageInfo.applicationInfo
        val appName = pm.getApplicationLabel(applicationInfo).toString()

        // 获取图标（转为 base64 避免跨进程问题）
        val iconBase64 = try {
            val icon: Drawable? = applicationInfo.loadIcon(pm)
            if (icon is BitmapDrawable) {
                val stream = ByteArrayOutputStream()
                icon.bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 80, stream)
                android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.DEFAULT)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to encode icon", e)
            null
        }

        return ToolResult.Success(mapOf(
            "name" to appName,
            "package_name" to packageName,
            "version_name" to (packageInfo.versionName ?: "unknown"),
            "version_code" to (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }),
            "install_time" to packageInfo.firstInstallTime,
            "update_time" to packageInfo.lastUpdateTime,
            "target_sdk" to applicationInfo.targetSdkVersion,
            "min_sdk" to applicationInfo.minSdkVersion,
            "data_dir" to applicationInfo.dataDir,
            "native_dir" to applicationInfo.nativeLibraryDir,
            "uid" to applicationInfo.uid,
            "is_system" to ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0),
            "is_disabled" to (!applicationInfo.enabled),
            "has_icon" to (iconBase64 != null),
            "icon_base64" to iconBase64,
            "permissions" to (packageInfo.requestedPermissions?.toList() ?: emptyList<String>()),
            "activities" to (packageInfo.activities?.size ?: 0),
            "services" to (packageInfo.services?.size ?: 0),
            "receivers" to (packageInfo.receivers?.size ?: 0)
        ))
    }

    private fun openAppSettings(params: Map<String, Any>): ToolResult {
        val packageName = params["package_name"] as? String
            ?: return ToolResult.Error("缺少参数: package_name")

        val ctx = context ?: return ToolResult.Error("Context not available")

        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ctx.startActivity(intent)
        Log.i(TAG, "Opened app settings: $packageName")
        return ToolResult.Success(mapOf(
            "status" to "opened",
            "package_name" to packageName,
            "action" to "settings"
        ))
    }

    private fun getCategoryName(category: Int): String {
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> "game"
            ApplicationInfo.CATEGORY_AUDIO -> "audio"
            ApplicationInfo.CATEGORY_VIDEO -> "video"
            ApplicationInfo.CATEGORY_IMAGE -> "image"
            ApplicationInfo.CATEGORY_SOCIAL -> "social"
            ApplicationInfo.CATEGORY_NEWS -> "news"
            ApplicationInfo.CATEGORY_MAPS -> "maps"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
            CATEGORY_FINANCE -> "finance"
            CATEGORY_COMMUNICATION -> "communication"
            CATEGORY_ENTERTAINMENT -> "entertainment"
            CATEGORY_EDUCATION -> "education"
            CATEGORY_BUSINESS -> "business"
            CATEGORY_MEDICAL -> "medical"
            ApplicationInfo.CATEGORY_UNDEFINED -> "other"
            else -> "other"
        }
    }

    override fun release() {
        context = null
        packageManager = null
        Log.i(TAG, "AppManagerSkill released")
    }
}
