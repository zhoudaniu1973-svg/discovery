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
