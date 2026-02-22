package com.discovery.util

import android.content.Context
import android.content.SharedPreferences
import com.discovery.parser.model.FavoriteItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 收藏帖子本地存储
 *
 * 使用 SharedPreferences + JSONArray 实现轻量持久化。
 * 不引入 Room / Gson 等额外依赖，利用 Android 内置的 org.json 序列化。
 * 收藏量通常在百条以内，SharedPreferences 完全够用。
 */
object FavoriteStore {

    private const val PREF_NAME = "favorites"
    private const val KEY_LIST = "favorite_list"

    private var prefs: SharedPreferences? = null

    // 内存缓存，避免每次都反序列化
    private val cache = mutableListOf<FavoriteItem>()
    private var loaded = false

    /**
     * 初始化（应在 Activity.onCreate 中调用）
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
        if (!loaded) {
            cache.clear()
            cache.addAll(readFromPrefs())
            loaded = true
        }
    }

    /**
     * 添加收藏
     */
    fun add(item: FavoriteItem) {
        // 避免重复收藏同一帖子
        if (cache.any { it.tid == item.tid }) return
        cache.add(0, item)  // 最新收藏的排在最前面
        saveToPrefs()
    }

    /**
     * 取消收藏
     */
    fun remove(tid: String) {
        cache.removeAll { it.tid == tid }
        saveToPrefs()
    }

    /**
     * 判断帖子是否已收藏
     */
    fun isFavorite(tid: String): Boolean {
        return cache.any { it.tid == tid }
    }

    /**
     * 获取所有收藏（按收藏时间倒序，最新的在前）
     */
    fun getAll(): List<FavoriteItem> {
        return cache.toList()
    }

    // ---- 序列化 / 反序列化 ----

    private fun saveToPrefs() {
        val jsonArray = JSONArray()
        for (item in cache) {
            val obj = JSONObject().apply {
                put("tid", item.tid)
                put("title", item.title)
                put("authorName", item.authorName)
                put("savedAt", item.savedAt)
            }
            jsonArray.put(obj)
        }
        prefs?.edit()?.putString(KEY_LIST, jsonArray.toString())?.apply()
    }

    private fun readFromPrefs(): List<FavoriteItem> {
        val json = prefs?.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<FavoriteItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FavoriteItem(
                        tid = obj.getString("tid"),
                        title = obj.getString("title"),
                        authorName = obj.optString("authorName", ""),
                        savedAt = obj.optLong("savedAt", 0L)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
