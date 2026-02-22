import { chromium } from 'playwright';
import fs from 'fs/promises';
import path from 'path';

// ── 单例 Browser 实例 ──────────────────────────────────────────
let browserInstance = null;
let browserLaunching = null;
let shutdownInstalled = false;

// 最大并发 Context 数量（可通过环境变量配置）
const MAX_CONTEXTS = Number.parseInt(process.env.PLAYWRIGHT_MAX_CONTEXTS || '2', 10) || 2;
let activeContexts = 0;
const waitQueue = [];

/**
 * 安装进程退出钩子，确保 Playwright 浏览器实例被正确关闭。
 * 避免进程僵死或端口占用。
 */
function installShutdownHooks() {
    if (shutdownInstalled) return;
    shutdownInstalled = true;

    const shutdown = async (code = 0) => {
        try {
            if (browserInstance) {
                await browserInstance.close();
                browserInstance = null;
            }
        } catch (e) {
            console.error('Playwright 关闭失败:', e);
        } finally {
            process.exit(code);
        }
    };

    process.once('SIGINT', () => shutdown(0));
    process.once('SIGTERM', () => shutdown(0));
    process.once('uncaughtException', (err) => {
        console.error('Uncaught exception:', err);
        shutdown(1);
    });
    process.once('unhandledRejection', (reason) => {
        console.error('Unhandled rejection:', reason);
        shutdown(1);
    });
    process.once('beforeExit', () => {
        if (!browserInstance) return;
        browserInstance.close().catch((e) => {
            console.error('beforeExit 关闭失败:', e);
        });
    });
}

// ── 并发控制：Context 槽位 ────────────────────────────────────

/**
 * 获取一个 Context 槽位。
 * 如果当前槽位已满，则等待直到有空余。
 */
async function acquireContextSlot() {
    if (activeContexts < MAX_CONTEXTS) {
        activeContexts++;
        return;
    }
    // 排队等待，避免同时打开过多 Context 消耗内存
    await new Promise((resolve) => waitQueue.push(resolve));
    activeContexts++;
}

/** 释放一个 Context 槽位，唤醒队列中的下一个等待者 */
function releaseContextSlot() {
    activeContexts = Math.max(0, activeContexts - 1);
    const next = waitQueue.shift();
    if (next) next();
}

// ── 浏览器实例管理 ────────────────────────────────────────────

/**
 * 获取（或启动）全局共享的 Chromium 浏览器实例。
 * 使用单例模式避免重复启动，同时处理并发启动竞争。
 */
async function getBrowser() {
    installShutdownHooks();
    if (browserInstance) return browserInstance;
    // 防止并发调用同时触发多次 launch
    if (browserLaunching) return browserLaunching;
    browserLaunching = chromium.launch();
    try {
        browserInstance = await browserLaunching;
        return browserInstance;
    } finally {
        browserLaunching = null;
    }
}

// ── 主要导出函数 ──────────────────────────────────────────────

/**
 * 使用 Playwright 无头浏览器抓取指定 URL 的页面内容。
 *
 * 优点：能通过 Cloudflare 等反爬挑战，支持 JS 渲染。
 * 注意：性能低于直接 fetch，适合作为降级方案使用。
 *
 * @param {string} url  目标页面 URL
 * @returns {{ content: string, finalUrl: string, status: number }}
 */
export async function browserFetch(url) {
    console.log(`正在使用 Playwright 抓取: ${url}`);
    const browser = await getBrowser();

    // 检查是否有已保存的登录状态（Session Cookie）
    const statePath = process.env.PLAYWRIGHT_STATE_PATH
        ? path.resolve(process.env.PLAYWRIGHT_STATE_PATH)
        : path.resolve(process.cwd(), '.state.json');
    let contextOptions = {};

    try {
        await fs.access(statePath);
        console.log('发现 .state.json，使用已保存的会话登录...');
        contextOptions.storageState = statePath;
    } catch (e) {
        console.log('未找到 .state.json，以匿名模式抓取...');
    }

    // 申请 Context 槽位（限制并发数量，防止内存溢出）
    await acquireContextSlot();
    let context = null;
    try {
        context = await browser.newContext(contextOptions);
        const page = await context.newPage();
        const response = await page.goto(url, { waitUntil: 'domcontentloaded' });
        const content = await page.content();
        const finalUrl = page.url();
        const status = response ? response.status() : 0;

        return { content, finalUrl, status };
    } catch (error) {
        console.error('Playwright 抓取失败:', error);
        throw error;
    } finally {
        try {
            // 只关闭 Context，保持 Browser 存活以供复用
            if (context) {
                await context.close();
            }
        } finally {
            releaseContextSlot();
        }
    }
}
