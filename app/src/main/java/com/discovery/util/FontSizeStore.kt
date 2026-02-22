package com.discovery.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 帖子内容字体大小偏好存储
 *
 * 使用 SharedPreferences 持久化用户选择的字体大小（单位 sp）。
 * 默认 19sp，范围 14-28sp。
 */
object FontSizeStore {

    private const val PREF_NAME = "font_settings"
    private const val KEY_SIZE = "content_font_size"

    const val DEFAULT_SIZE = 19
    const val MIN_SIZE = 14
    const val MAX_SIZE = 28

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    /** 获取当前字体大小（sp） */
    fun getSize(): Int {
        return prefs?.getInt(KEY_SIZE, DEFAULT_SIZE) ?: DEFAULT_SIZE
    }

    /** 保存字体大小（sp） */
    fun setSize(sizeSp: Int) {
        val clamped = sizeSp.coerceIn(MIN_SIZE, MAX_SIZE)
        prefs?.edit()?.putInt(KEY_SIZE, clamped)?.apply()
    }
}
