# Discovery Android

一个简洁的 Discuz 7.2 论坛阅读器 Android 应用。

## 功能特性

- 📋 **帖子列表** - 浏览论坛帖子，支持分页加载
- 📖 **帖子详情** - 查看完整内容，支持图片显示
- 🔄 **下拉刷新** - SwipeRefreshLayout 刷新体验
- ⏳ **骨架屏** - Shimmer 加载动画
- 🔐 **自动登录** - WebView 登录并保存 Cookie
- 🛡️ **Cloudflare 绕过** - 自动降级到 WebView 抓取

## 技术栈

- **语言**: Kotlin
- **架构**: MVVM (ViewModel + LiveData)
- **网络**: OkHttp + Jsoup
- **UI**: RecyclerView + SwipeRefreshLayout + Shimmer

## 项目结构

```
app/src/main/java/com/discovery/
├── Constants.kt           # 统一常量配置
├── MainActivity.kt        # 列表页
├── DetailActivity.kt      # 详情页
├── LoginActivity.kt       # 登录页
├── viewmodel/             # ViewModel 层
├── parser/                # 网络与解析
│   ├── network/           # OkHttp 客户端
│   ├── service/           # HTML 解析器
│   └── model/             # 数据模型
├── ui/                    # Adapter
└── util/                  # 工具类
```

## 构建

1. 使用 Android Studio 打开项目
2. 等待 Gradle Sync 完成
3. 点击 Run 或 Build APK

## 依赖

```groovy
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'org.jsoup:jsoup:1.17.2'
implementation 'com.facebook.shimmer:shimmer:0.5.0'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
```

## License

MIT



# 优化说明



本说明记录本轮优化的主要变化点，便于后续排查与回归。

**客户端（Android）**
1. WebView 回退抓取保留 `append` 语义，翻页不再覆盖列表。
2. 列表/详情解析移到后台线程，避免 UI 卡顿。
3. 帖子内容渲染改为 `TextView + HtmlCompat + 图片加载器`，移除逐条 `WebView`，降低内存和滑动卡顿。
4. `PostListAdapter` 改为 `ListAdapter + DiffUtil`，提升刷新性能。
5. 详情页自动滚动问题修复：阻止子控件抢焦点 + 首屏强制滚到顶部。
6. 详情页字体加大（作者/时间/正文）。
7. HTML 内容轻量清洗：移除脚本/媒体/表单标签、去除 `style/class`，超大表格替换为提示文本。
8. 字符集回退解析：优先 BOM，其次响应头，最后 HTML `meta`。

**服务端（Node + Playwright）**
1. Playwright 生命周期与并发控制：退出自动关闭、并发上下文上限（`PLAYWRIGHT_MAX_CONTEXTS`）。
2. 路径与快照开关可配置：`PLAYWRIGHT_STATE_PATH`、`SNAPSHOT_DEBUG`、`SNAPSHOT_PATH`。

**环境变量**
1. `PLAYWRIGHT_STATE_PATH`：登录态文件路径（默认 `server/.state.json`）。
2. `PLAYWRIGHT_MAX_CONTEXTS`：Playwright 并发上下文上限（默认 `2`）。
3. `SNAPSHOT_DEBUG`：`true/1/yes` 时保存 HTML 快照。
4. `SNAPSHOT_PATH`：快照输出路径（默认 `shared/testdata/last_discovery.html`）。

**构建**
1. APK：`android_app/app/build/outputs/apk/debug/app-debug.apk`

