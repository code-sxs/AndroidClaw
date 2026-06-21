// SkillMarketClient.kt
// Skill 市场 - 多源市场客户端
// 支持搜索、详情获取、下载等功能，适配多个市场源的 API 差异

package com.androidclaw.app.skills.market

import android.util.Log
import com.androidclaw.app.skills.market.db.MarketCacheDao
import com.androidclaw.app.skills.market.db.MarketCacheEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Skill 市场客户端
 * 
 * 功能：
 * - 支持多个市场源（Clawhub / Skillsmp / SkillHub / skills.sh）
 * - 搜索 API：按名称、分类、关键词搜索 Skill
 * - 获取 Skill 详情
 * - 下载 Skill 包
 * - 缓存搜索结果到 Room 数据库
 */
class SkillMarketClient(
    private val okHttpClient: OkHttpClient,
    private val cacheDao: MarketCacheDao,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "SkillMarketClient"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L  // 缓存有效期 30 分钟
        private const val SEARCH_TIMEOUT_S = 15L
        private const val MAX_PAGE_SIZE = 50
    }

    /** 下载大小限制 (默认 50MB) */
    var maxDownloadSizeBytes: Long = 50L * 1024 * 1024

    /** 当前配置的市场源 */
    private val _sources = mutableListOf<MarketSource>()

    val sources: List<MarketSource> get() = _sources.toList()

    init {
        // 加载默认市场源
        _sources.addAll(MarketSource.DEFAULT_SOURCES)
    }

    /**
     * 添加市场源
     */
    fun addSource(source: MarketSource) {
        if (_sources.none { it.id == source.id }) {
            _sources.add(source)
        }
    }

    /**
     * 移除市场源
     */
    fun removeSource(sourceId: String) {
        _sources.removeAll { it.id == sourceId }
    }

    /**
     * 启用/禁用市场源
     */
    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        val index = _sources.indexOfFirst { it.id == sourceId }
        if (index >= 0) {
            _sources[index] = _sources[index].copy(isEnabled = enabled)
        }
    }

    /**
     * 搜索 Skill
     * 优先使用缓存，缓存过期则从市场 API 拉取
     *
     * @param query 搜索关键词
     * @param category 分类筛选 (可选)
     * @param marketId 指定市场源 (null 则搜索所有启用的市场)
     * @param page 页码 (从 0 开始)
     * @param pageSize 每页数量
     */
    suspend fun searchSkills(
        query: String,
        category: String? = null,
        marketId: String? = null,
        page: Int = 0,
        pageSize: Int = 20
    ): MarketSearchResult = withContext(Dispatchers.IO) {
        val targetSources = if (marketId != null) {
            _sources.filter { it.id == marketId && it.isEnabled }
        } else {
            _sources.filter { it.isEnabled }
        }

        if (targetSources.isEmpty()) {
            return@withContext MarketSearchResult(
                skills = emptyList(),
                totalCount = 0,
                page = page,
                hasMore = false
            )
        }

        val allSkills = mutableListOf<MarketSkill>()

        for (source in targetSources) {
            // 1. 尝试从缓存读取
            val cacheKey = buildCacheKey(source.id, query, category)
            val cached = cacheDao.getValidCache(source.id, cacheKey)
            if (cached != null) {
                try {
                    val cachedSkills = parseSkillsFromJson(cached.resultJson, source.id)
                    allSkills.addAll(cachedSkills)
                    Log.d(TAG, "Cache hit for ${source.id}: $query")
                    continue
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse cache for ${source.id}", e)
                }
            }

            // 2. 从市场 API 拉取
            try {
                val result = fetchFromMarket(source, query, category, page, pageSize)
                allSkills.addAll(result.skills)

                // 3. 写入缓存
                val resultJson = gson.toJson(result.skills)
                cacheDao.insert(
                    MarketCacheEntity(
                        id = cacheKey,
                        marketId = source.id,
                        query = cacheKey,
                        category = category,
                        resultJson = resultJson,
                        cachedAt = System.currentTimeMillis(),
                        expiresAt = System.currentTimeMillis() + CACHE_TTL_MS
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch from ${source.id}", e)
            }
        }

        // 合并去重（同名 Skill 优先取第一个）
        val deduped = allSkills
            .distinctBy { "${it.marketId}:${it.name}" }
            .sortedByDescending { it.downloadCount }

        val paged = deduped.drop(page * pageSize).take(pageSize)

        MarketSearchResult(
            skills = paged,
            totalCount = deduped.size,
            page = page,
            hasMore = (page + 1) * pageSize < deduped.size
        )
    }

    /**
     * 获取 Skill 详情
     */
    suspend fun getSkillDetail(
        marketId: String,
        skillId: String
    ): MarketSkill? = withContext(Dispatchers.IO) {
        val source = _sources.find { it.id == marketId } ?: return@withContext null

        try {
            fetchSkillDetail(source, skillId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get detail for $skillId from $marketId", e)
            null
        }
    }

    /**
     * 获取所有分类
     */
    suspend fun getCategories(marketId: String? = null): List<String> = withContext(Dispatchers.IO) {
        val targetSources = if (marketId != null) {
            _sources.filter { it.id == marketId && it.isEnabled }
        } else {
            _sources.filter { it.isEnabled }
        }

        val categories = mutableSetOf<String>()
        for (source in targetSources) {
            try {
                val url = "${source.apiEndpoint}/categories"
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val listType = object : TypeToken<List<String>>() {}.type
                    val cats: List<String> = gson.fromJson(body, listType)
                    categories.addAll(cats)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch categories from ${source.id}", e)
            }
        }
        categories.sorted()
    }

    // ===== 内部方法 =====

    /**
     * 从市场 API 拉取搜索结果
     * 根据不同市场源适配不同的 API 格式
     */
    private fun fetchFromMarket(
        source: MarketSource,
        query: String,
        category: String?,
        page: Int,
        pageSize: Int
    ): MarketSearchResult {
        val url = buildSearchUrl(source, query, category, page, pageSize)
        val client = okHttpClient.newBuilder()
            .connectTimeout(SEARCH_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(SEARCH_TIMEOUT_S, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Market API returned ${response.code} for ${source.id}")
        }

        val body = response.body?.string() ?: throw IOException("Empty response from ${source.id}")

        return parseSearchResponse(source, body, page, pageSize)
    }

    /**
     * 获取单个 Skill 详情
     */
    private fun fetchSkillDetail(source: MarketSource, skillId: String): MarketSkill {
        val url = "${source.apiEndpoint}/skills/$skillId"
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Detail API returned ${response.code}")
        }

        val body = response.body?.string() ?: throw IOException("Empty response")
        return parseSkillDetail(source, body)
    }

    /**
     * 构建搜索 URL
     * 不同市场源可能使用不同的查询参数格式
     */
    private fun buildSearchUrl(
        source: MarketSource,
        query: String,
        category: String?,
        page: Int,
        pageSize: Int
    ): String {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val base = "${source.apiEndpoint}/skills"

        return when (source.id) {
            "clawhub" -> {
                // Clawhub: RESTful 风格
                buildString {
                    append("$base?query=$encodedQuery&page=$page&size=$pageSize")
                    category?.let { append("&category=$it") }
                }
            }
            "skillhub" -> {
                // SkillHub: 腾讯风格
                buildString {
                    append("$base/search?keyword=$encodedQuery&offset=${page * pageSize}&limit=$pageSize")
                    category?.let { append("&category=$it") }
                }
            }
            else -> {
                // 通用格式
                buildString {
                    append("$base?q=$encodedQuery&page=$page&per_page=$pageSize")
                    category?.let { append("&category=$it") }
                }
            }
        }
    }

    /**
     * 解析搜索响应
     * 适配不同市场源的返回格式
     */
    private fun parseSearchResponse(
        source: MarketSource,
        body: String,
        page: Int,
        pageSize: Int
    ): MarketSearchResult {
        return when (source.id) {
            "clawhub" -> parseClawhubResponse(body, source.id)
            "skillhub" -> parseSkillHubResponse(body, source.id)
            else -> parseGenericResponse(body, source.id)
        }.copy(page = page)
    }

    /**
     * 解析 Clawhub 格式
     */
    private fun parseClawhubResponse(body: String, marketId: String): MarketSearchResult {
        val root = gson.fromJson(body, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val items = root["items"] as? List<Map<String, Any?>> ?: emptyList()
        val total = (root["total"] as? Number)?.toInt() ?: items.size

        val skills = items.map { item -> parseSkillFromMap(item, marketId) }
        return MarketSearchResult(
            skills = skills,
            totalCount = total,
            page = 0,
            hasMore = skills.size < total
        )
    }

    /**
     * 解析 SkillHub 格式
     */
    private fun parseSkillHubResponse(body: String, marketId: String): MarketSearchResult {
        val root = gson.fromJson(body, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        val items = root["data"] as? List<Map<String, Any?>> ?: emptyList()
        val total = (root["total"] as? Number)?.toInt() ?: items.size

        val skills = items.map { item -> parseSkillFromMap(item, marketId) }
        return MarketSearchResult(
            skills = skills,
            totalCount = total,
            page = 0,
            hasMore = skills.size < total
        )
    }

    /**
     * 解析通用格式
     */
    private fun parseGenericResponse(body: String, marketId: String): MarketSearchResult {
        val skills = parseSkillsFromJson(body, marketId)
        return MarketSearchResult(
            skills = skills,
            totalCount = skills.size,
            page = 0,
            hasMore = false
        )
    }

    /**
     * 从 Map 解析 Skill（适配不同市场字段映射）
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseSkillFromMap(item: Map<String, Any?>, marketId: String): MarketSkill {
        val toolsRaw = item["tools"] as? List<Map<String, Any?>> ?: emptyList()
        val tools = toolsRaw.map { t ->
            ToolSummary(
                name = t["name"] as? String ?: "",
                description = t["description"] as? String ?: "",
                parameters = (t["parameters"] as? List<String>) ?: emptyList()
            )
        }

        val tagsRaw = item["tags"] as? List<String> ?: emptyList()
        val permissionsRaw = item["permissions"] as? List<String> ?: emptyList()

        return MarketSkill(
            marketId = marketId,
            skillId = (item["id"] as? String) ?: (item["skill_id"] as? String) ?: "",
            name = item["name"] as? String ?: "",
            version = item["version"] as? String ?: "1.0.0",
            description = item["description"] as? String ?: "",
            author = item["author"] as? String ?: "",
            downloadUrl = item["download_url"] as? String ?: "",
            fileSize = (item["file_size"] as? Number)?.toLong() ?: 0L,
            sha256 = item["sha256"] as? String,
            permissions = permissionsRaw,
            tools = tools,
            rating = (item["rating"] as? Number)?.toFloat(),
            downloadCount = (item["download_count"] as? Number)?.toInt() ?: 0,
            lastUpdated = item["last_updated"] as? String ?: "",
            category = item["category"] as? String ?: "general",
            tags = tagsRaw,
            iconUrl = item["icon_url"] as? String
        )
    }

    /**
     * 从 JSON 字符串解析 Skill 列表
     */
    private fun parseSkillsFromJson(json: String, marketId: String): List<MarketSkill> {
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val items: List<Map<String, Any?>> = gson.fromJson(json, type)
        return items.map { parseSkillFromMap(it, marketId) }
    }

    /**
     * 解析 Skill 详情
     */
    private fun parseSkillDetail(source: MarketSource, body: String): MarketSkill {
        val item = gson.fromJson(body, Map::class.java)
        @Suppress("UNCHECKED_CAST")
        return parseSkillFromMap(item as Map<String, Any?>, source.id)
    }

    /**
     * 构建缓存 Key
     */
    private fun buildCacheKey(marketId: String, query: String, category: String?): String {
        val raw = "$marketId:$query:${category ?: ""}"
        return raw.hashCode().toString()
    }
}
