// WeatherSkill.kt
// 天气 Skill - 获取天气信息（优先离线缓存，可选择性联网）

package com.androidclaw.app.skills

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 天气 Skill
 * 通过 Open-Meteo 免费 API（无需 API key）获取天气信息
 * 支持缓存以减少网络请求
 */
class WeatherSkill : SkillDefinition {

    companion object {
        private const val TAG = "WeatherSkill"

        // Open-Meteo API (免费，无需 API key)
        private const val CURRENT_WEATHER_URL = "https://api.open-meteo.com/v1/forecast"
        private const val CACHE_DURATION = 30 * 60 * 1000L // 30分钟缓存

        // 地理位置缓存
        private val locationCache = mutableMapOf<String, Pair<Double, Double>>()

        // 天气状况代码映射
        private val WEATHER_CODES = mapOf(
            0 to "clear",           // 晴天
            1 to "mainly_clear",    // 大部晴朗
            2 to "partly_cloudy",   // 部分多云
            3 to "overcast",        // 阴天
            45 to "foggy",          // 雾
            48 to "foggy",          // 雾凇
            51 to "drizzle",        // 小毛毛雨
            53 to "drizzle",        // 中毛毛雨
            55 to "drizzle",        // 大毛毛雨
            56 to "freezing_drizzle", // 冻毛毛雨
            57 to "freezing_drizzle",
            61 to "rain_light",     // 小雨
            63 to "rain_moderate",  // 中雨
            65 to "rain_heavy",     // 大雨
            66 to "freezing_rain",  // 冻雨
            67 to "freezing_rain",
            71 to "snow_light",     // 小雪
            73 to "snow_moderate",  // 中雪
            75 to "snow_heavy",     // 大雪
            77 to "snow_grains",    // 雪粒
            80 to "rain_showers_light", // 小阵雨
            81 to "rain_showers_moderate",
            82 to "rain_showers_violent",
            85 to "snow_showers_light",
            86 to "snow_showers_heavy",
            95 to "thunderstorm",   // 雷暴
            96 to "thunderstorm_hail", // 雷暴+冰雹
            99 to "thunderstorm_hail"
        )

        // 风向标签
        private val WIND_DIRECTIONS = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

        // 预置城市坐标
        private val CITY_COORDS = mapOf(
            "beijing" to 39.9 to 116.4,
            "shanghai" to 31.2 to 121.5,
            "guangzhou" to 23.1 to 113.3,
            "shenzhen" to 22.5 to 114.1,
            "chengdu" to 30.6 to 104.1,
            "hangzhou" to 30.3 to 120.2,
            "wuhan" to 30.6 to 114.3,
            "nanjing" to 32.1 to 118.8,
            "chongqing" to 29.6 to 106.6,
            "tianjin" to 39.1 to 117.2,
            "suzhou" to 31.3 to 120.6,
            "xian" to 34.3 to 108.9,
            "changsha" to 28.2 to 112.9,
            "qingdao" to 36.1 to 120.4,
            "dalian" to 38.9 to 121.6,
            "xiamen" to 24.5 to 118.1,
            "kunming" to 25.0 to 102.7,
            "harbin" to 45.8 to 126.6,
            "zhengzhou" to 34.8 to 113.7,
            "shenyang" to 41.8 to 123.4,
            "hongkong" to 22.3 to 114.2,
            "tokyo" to 35.7 to 139.7,
            "newyork" to 40.7 to -74.0,
            "london" to 51.5 to -0.1,
            "paris" to 48.9 to 2.3,
            "sydney" to -33.9 to 151.2,
            "singapore" to 1.3 to 103.8,
            "seoul" to 37.57 to 127.0,
            "bangkok" to 13.8 to 100.5,
            "dubai" to 25.2 to 55.3,
            "losangeles" to 34.0 to -118.2,
            "sanfrancisco" to 37.8 to -122.4,
            "moscow" to 55.8 to 37.6,
            "berlin" to 52.5 to 13.4,
            "toronto" to 43.7 to -79.4,
            "vancouver" to 49.3 to -123.1,
            "taipei" to 25.0 to 121.5,
            "kaohsiung" to 22.6 to 120.3
        )
    }

    // 天气数据缓存
    private data class WeatherCache(
        val locationKey: String,
        val data: Map<String, Any?>,
        val timestamp: Long
    )

    private var context: Context? = null
    private var currentWeatherCache: WeatherCache? = null
    private var forecastCache: WeatherCache? = null

    override val skillName: String = "weather"
    override val displayName: String = "天气"
    override val description: String = "获取天气信息（支持城市名称或坐标），使用 Open-Meteo 免费 API"
    override val requiredPermissions: List<String> = listOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    override suspend fun initialize(context: Context) {
        this.context = context
        Log.i(TAG, "WeatherSkill initialized")
    }

    override fun getTools(): List<ToolDefinition> = listOf(
        ToolDefinition(
            toolName = "get_current_weather",
            displayName = "当前天气",
            description = "获取指定位置的当前天气（温度、湿度、风速、天气状况等）",
            parameters = listOf(
                ToolParameter("location", "string", false,
                    "位置：城市名称（如 beijing/tokyo/newyork）或经纬度 \"com:lat,lng\" 格式，不填则使用设备位置")
            ),
            returnType = "map"
        ),
        ToolDefinition(
            toolName = "get_forecast",
            displayName = "天气预报",
            description = "获取指定位置的未来天气预报",
            parameters = listOf(
                ToolParameter("location", "string", false, "位置（同上）"),
                ToolParameter("days", "int", false, "预报天数，默认3天，最多7天")
            ),
            returnType = "list"
        )
    )

    override suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ToolResult {
        return try {
            when (toolName) {
                "get_current_weather" -> getCurrentWeather(parameters)
                "get_forecast" -> getForecast(parameters)
                else -> ToolResult.Error("Unknown tool: $toolName")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for $toolName", e)
            ToolResult.Error("缺少定位权限: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing $toolName", e)
            ToolResult.Error("获取天气失败: ${e.message}", e)
        }
    }

    private fun getCurrentWeather(params: Map<String, Any>): ToolResult {
        val location = params["location"] as? String ?: "beijing"

        // 解析坐标
        val (lat, lng) = resolveCoordinates(location)
            ?: return ToolResult.Error("无法解析位置: $location，请使用城市名称（如 beijing/tokyo）或坐标格式")

        // 检查缓存
        val cacheKey = "current_${lat}_${lng}"
        currentWeatherCache?.let { cache ->
            if (cache.locationKey == cacheKey &&
                (System.currentTimeMillis() - cache.timestamp) < CACHE_DURATION) {
                Log.d(TAG, "Using cached weather data for $location")
                return ToolResult.Success(cache.data)
            }
        }

        // 联网获取
        val url = buildString {
            append("$CURRENT_WEATHER_URL?latitude=$lat&longitude=$lng")
            append("&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl,cloud_cover,uv_index")
            append("&timezone=auto")
        }

        return try {
            val json = URL(url).readText()
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val response: Map<String, Any> = gson.fromJson(json, type)

            val current = response["current"] as? Map<String, Any>
            val currentUnits = response["current_units"] as? Map<String, Any>

            if (current == null) {
                return ToolResult.Error("天气数据格式异常")
            }

            val weatherCode = (current["weather_code"] as? Number)?.toInt() ?: 0
            val temperature = (current["temperature_2m"] as? Number)?.toDouble() ?: 0.0
            val humidity = (current["relative_humidity_2m"] as? Number)?.toInt() ?: 0
            val windSpeed = (current["wind_speed_10m"] as? Number)?.toDouble() ?: 0.0
            val windDirection = (current["wind_direction_10m"] as? Number)?.toDouble() ?: 0.0

            val weatherData = mapOf<String, Any?>(
                "location" to location,
                "latitude" to lat,
                "longitude" to lng,
                "temperature" to temperature,
                "temperature_unit" to (currentUnits?.get("temperature_2m") ?: "°C"),
                "feels_like" to (current["apparent_temperature"] as? Number)?.toDouble(),
                "humidity" to humidity,
                "humidity_unit" to (currentUnits?.get("relative_humidity_2m") ?: "%"),
                "condition" to getWeatherCondition(weatherCode),
                "condition_code" to weatherCode,
                "condition_description" to getWeatherDescription(weatherCode),
                "wind_speed" to windSpeed,
                "wind_speed_unit" to (currentUnits?.get("wind_speed_10m") ?: "km/h"),
                "wind_direction" to windDirection.toInt(),
                "wind_direction_label" to getWindDirectionLabel(windDirection),
                "wind_gusts" to (current["wind_gusts_10m"] as? Number)?.toDouble(),
                "precipitation" to (current["precipitation"] as? Number)?.toDouble(),
                "pressure" to (current["pressure_msl"] as? Number)?.toDouble(),
                "cloud_cover" to (current["cloud_cover"] as? Number)?.toInt(),
                "uv_index" to (current["uv_index"] as? Number)?.toDouble(),
                "timestamp" to System.currentTimeMillis(),
                "time_display" to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date())
            )

            // 更新缓存
            currentWeatherCache = WeatherCache(cacheKey, weatherData, System.currentTimeMillis())

            Log.d(TAG, "Weather fetched for $location: ${temperature}°C, ${getWeatherDescription(weatherCode)}")
            ToolResult.Success(weatherData)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch weather", e)
            // 如果有缓存数据，返回缓存
            currentWeatherCache?.let {
                if (it.locationKey == cacheKey) {
                    return ToolResult.Success(it.data + mapOf("from_cache" to true))
                }
            }
            ToolResult.Error("获取天气失败: ${e.message}")
        }
    }

    private fun getForecast(params: Map<String, Any>): ToolResult {
        val location = params["location"] as? String ?: "beijing"
        val days = (params["days"] as? Number)?.toInt()?.coerceIn(1, 7) ?: 3

        val (lat, lng) = resolveCoordinates(location)
            ?: return ToolResult.Error("无法解析位置: $location")

        val cacheKey = "forecast_${lat}_${lng}_${days}"
        forecastCache?.let { cache ->
            if (cache.locationKey == cacheKey &&
                (System.currentTimeMillis() - cache.timestamp) < CACHE_DURATION) {
                Log.d(TAG, "Using cached forecast for $location")
                return ToolResult.Success(cache.data)
            }
        }

        val url = buildString {
            append("$CURRENT_WEATHER_URL?latitude=$lat&longitude=$lng")
            append("&daily=temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min,")
            append("precipitation_sum,precipitation_probability_max,weather_code,wind_speed_10m_max,wind_direction_10m_dominant,uv_index_max")
            append("&forecast_days=$days")
            append("&timezone=auto")
        }

        return try {
            val json = URL(url).readText()
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val response: Map<String, Any> = gson.fromJson(json, type)

            val daily = response["daily"] as? Map<String, Any>
            if (daily == null) {
                return ToolResult.Error("预报数据格式异常")
            }

            val forecastList = mutableListOf<Map<String, Any?>>()
            val dates = daily["time"] as? List<String> ?: emptyList()
            val maxTemps = daily["temperature_2m_max"] as? List<Number> ?: emptyList()
            val minTemps = daily["temperature_2m_min"] as? List<Number> ?: emptyList()
            val weatherCodes = daily["weather_code"] as? List<Number> ?: emptyList()
            val precipProbs = daily["precipitation_probability_max"] as? List<Number> ?: emptyList()
            val windSpeeds = daily["wind_speed_10m_max"] as? List<Number> ?: emptyList()

            for (i in dates.indices) {
                val code = (weatherCodes.getOrNull(i)?.toInt()) ?: 0
                forecastList.add(mapOf(
                    "date" to (dates.getOrNull(i) ?: ""),
                    "temperature_max" to (maxTemps.getOrNull(i)?.toDouble()),
                    "temperature_min" to (minTemps.getOrNull(i)?.toDouble()),
                    "condition" to getWeatherCondition(code),
                    "condition_description" to getWeatherDescription(code),
                    "condition_code" to code,
                    "precipitation_probability" to (precipProbs.getOrNull(i)?.toInt()),
                    "wind_speed_max" to (windSpeeds.getOrNull(i)?.toDouble())
                ))
            }

            val forecastData = mapOf<String, Any?>(
                "location" to location,
                "latitude" to lat,
                "longitude" to lng,
                "days" to days,
                "forecast" to forecastList,
                "timestamp" to System.currentTimeMillis()
            )

            forecastCache = WeatherCache(cacheKey, forecastData, System.currentTimeMillis())

            Log.d(TAG, "Forecast fetched for $location: $days days")
            ToolResult.Success(forecastData)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch forecast", e)
            forecastCache?.let {
                if (it.locationKey == cacheKey) {
                    return ToolResult.Success(it.data + mapOf("from_cache" to true))
                }
            }
            ToolResult.Error("获取天气预报失败: ${e.message}")
        }
    }

    /**
     * 解析位置参数为经纬度
     */
    private fun resolveCoordinates(location: String): Pair<Double, Double>? {
        // 尝试从缓存获取
        locationCache[location.lowercase()]?.let { return it }

        // 检查预置城市
        cityCoords[location.lowercase().replace(" ", "")]?.let { (lat, lng) ->
            locationCache[location.lowercase()] = lat to lng
            return lat to lng
        }

        // 检查坐标格式 (lat,lng)
        val coordPattern = Regex("""^(-?\d+\.?\d*)\s*[,，]\s*(-?\d+\.?\d*)$""")
        coordPattern.find(location.trim())?.let { match ->
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                locationCache[location.lowercase()] = lat to lng
                return lat to lng
            }
        }

        return null
    }

    private val cityCoords: Map<String, Pair<Double, Double>>
        get() = CITY_COORDS

    private fun getWeatherCondition(code: Int): String {
        return WEATHER_CODES[code] ?: "unknown"
    }

    private fun getWeatherDescription(code: Int): String {
        return when (code) {
            0 -> "晴天"
            1 -> "大部晴朗"
            2 -> "部分多云"
            3 -> "阴天"
            45, 48 -> "雾天"
            51, 53, 55 -> "毛毛雨"
            56, 57 -> "冻毛毛雨"
            61, 63, 65 -> "雨天"
            66, 67 -> "冻雨"
            71, 73, 75 -> "雪天"
            77 -> "雪粒"
            80, 81, 82 -> "阵雨"
            85, 86 -> "阵雪"
            95 -> "雷暴"
            96, 99 -> "雷暴+冰雹"
            else -> "未知"
        }
    }

    private fun getWindDirectionLabel(degrees: Double): String {
        val index = ((degrees + 11.25) / 22.5).toInt() % 16
        return WIND_DIRECTIONS[index]
    }

    override fun release() {
        context = null
        currentWeatherCache = null
        forecastCache = null
        locationCache.clear()
        Log.i(TAG, "WeatherSkill released")
    }
}
