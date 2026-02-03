package com.discovery.parser.model

enum class ParseStatus {
    SUCCESS,
    NEED_LOGIN,       // 包含 "您需要先登录" 或 "logging.php?action=login"
    CF_CHALLENGE,     // 包含 "cdn-cgi" 或 "__CF$cv_params"
    NO_PERMISSION,    // 包含 "无权"
    CAPTCHA_REQUIRED, // 包含 "验证码" 或 "seccode"
    NETWORK_ERROR,    // 网络请求失败
    PARSE_ERROR       // 解析过程异常
}

data class ParseResult<T>(
    val status: ParseStatus,
    val data: T? = null,
    val errorMessage: String? = null,
    val rawHtmlSnippet: String? = null // 出错时附带 HTML 片段
)
