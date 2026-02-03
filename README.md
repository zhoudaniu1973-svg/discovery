# Discovery 项目优化说明

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

