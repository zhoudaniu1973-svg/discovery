package com.discovery.util

import android.util.Log

object DebugLog {
    private val isDebugBuild: Boolean by lazy {
        runCatching {
            val clazz = Class.forName("com.discovery.BuildConfig")
            clazz.getField("DEBUG").getBoolean(null)
        }.getOrDefault(false)
    }

    fun d(tag: String, message: () -> String) {
        if (isDebugBuild) {
            Log.d(tag, message())
        }
    }

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (isDebugBuild) {
            if (throwable != null) {
                Log.w(tag, message(), throwable)
            } else {
                Log.w(tag, message())
            }
        }
    }
}
