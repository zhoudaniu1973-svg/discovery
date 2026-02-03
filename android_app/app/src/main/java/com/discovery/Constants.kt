package com.discovery

/**
 * 应用常量配置
 */
object Constants {
    // 基础 URL
    const val BASE_DOMAIN = "https://www.4d4y.com"
    const val BASE_FORUM_URL = "$BASE_DOMAIN/forum/"
    const val LOGIN_URL = "${BASE_FORUM_URL}logging.php?action=login"
    
    // 默认板块
    const val DEFAULT_FID = "2"
    
    // 构建列表页 URL
    fun buildForumDisplayUrl(fid: String = DEFAULT_FID, page: Int = 1): String {
        return "${BASE_FORUM_URL}forumdisplay.php?fid=$fid&page=$page"
    }
    
    // 构建详情页 URL
    fun buildViewThreadUrl(tid: String, page: Int = 1): String {
        return "${BASE_FORUM_URL}viewthread.php?tid=$tid&page=$page"
    }
    
    // User Agent
    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
    // 默认字符集
    const val DEFAULT_CHARSET = "GBK"
}
