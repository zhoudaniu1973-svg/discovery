package com.discovery.parser.network

import android.content.Context

object CookieStore {
    private const val PREFS_NAME = "discuz_cookie_store"
    private const val KEY_COOKIE = "cookie_header"

    @Volatile
    private var cachedCookie: String? = null

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedCookie = prefs.getString(KEY_COOKIE, null)
    }

    fun save(context: Context, cookieHeader: String) {
        cachedCookie = cookieHeader
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COOKIE, cookieHeader).apply()
    }

    fun clear(context: Context) {
        cachedCookie = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_COOKIE).apply()
    }

    fun get(): String? = cachedCookie
}
