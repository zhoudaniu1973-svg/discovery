# Discovery Android（中文说明）

一个简洁的 Discuz 7.2 论坛阅读器 Android 应用，支持列表浏览、详情查看、图片加载与自动登录。

## 功能特性

- 📋 **帖子列表**：浏览论坛帖子，支持分页加载
- 📖 **帖子详情**：查看完整内容，支持图片显示
- 🖼️ **大图自适应**：详情页图片满宽显示
- 🔄 **下拉刷新**：SwipeRefreshLayout 刷新体验
- ⏳ **骨架屏**：Shimmer 加载动画
- 🔐 **自动登录**：WebView 登录并保存 Cookie
- 🛡️ **Cloudflare 绕过**：自动降级到 WebView 抓取
- 🎯 **标题栏隐藏**：列表/详情页随滚动自动隐藏

## 构建

1. 使用 Android Studio 打开项目
2. 等待 Gradle Sync 完成
3. 点击 Run 或 Build APK

命令行构建：

```bash
./gradlew :app:assembleDebug
```

APK 输出路径：

```
app/build/outputs/apk/debug/app-debug.apk
```

## 服务端说明（Node + Playwright）

服务端用于处理浏览器抓取和 Cloudflare 绕过（可选）。

启动方式：

```bash
cd server
npm install
npm run dev
```

接口示例：

- `GET /api/v1/discovery?page=1`

## 环境变量

以下环境变量用于服务端调试与 Playwright 配置：

- `PLAYWRIGHT_STATE_PATH`：登录态文件路径（默认 `server/.state.json`）
- `PLAYWRIGHT_MAX_CONTEXTS`：Playwright 并发上下文上限（默认 `2`）
- `SNAPSHOT_DEBUG`：`true/1/yes` 时保存 HTML 快照
- `SNAPSHOT_PATH`：快照输出路径（默认 `shared/testdata/last_discovery.html`）
- `TEST_FETCH_ENABLED`：`true/1/yes` 时启用 `/test-fetch` 调试接口

---

如需更详细的开发说明或贡献指南，可在 Issues 中讨论。