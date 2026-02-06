package com.discovery.viewmodel

import com.discovery.parser.model.ParseStatus

sealed class LoadResult<out T> {
    data class Success<T>(val data: T) : LoadResult<T>()
    data class NeedWebViewFetch(val url: String) : LoadResult<Nothing>()
    object NeedLogin : LoadResult<Nothing>()
    data class Error(val message: String, val status: ParseStatus) : LoadResult<Nothing>()
}