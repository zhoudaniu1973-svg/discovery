import { chromium } from 'playwright';
import fs from 'fs/promises';
import path from 'path';

let browserInstance = null;
let browserLaunching = null;
let shutdownInstalled = false;

const MAX_CONTEXTS = Number.parseInt(process.env.PLAYWRIGHT_MAX_CONTEXTS || '2', 10) || 2;
let activeContexts = 0;
const waitQueue = [];

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
            console.error('Playwright 鍏抽棴澶辫触:', e);
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
            console.error('beforeExit 鍏抽棴澶辫触:', e);
        });
    });
}

async function acquireContextSlot() {
    if (activeContexts < MAX_CONTEXTS) {
        activeContexts++;
        return;
    }
    await new Promise((resolve) => waitQueue.push(resolve));
    activeContexts++;
}

function releaseContextSlot() {
    activeContexts = Math.max(0, activeContexts - 1);
    const next = waitQueue.shift();
    if (next) next();
}

async function getBrowser() {
    installShutdownHooks();
    if (browserInstance) return browserInstance;
    if (browserLaunching) return browserLaunching;
    browserLaunching = chromium.launch();
    try {
        browserInstance = await browserLaunching;
        return browserInstance;
    } finally {
        browserLaunching = null;
    }
}

export async function browserFetch(url) {
    console.log(`姝ｅ湪浣跨敤 Playwright 鎶撳彇: ${url}`);
    const browser = await getBrowser();

    // 妫€鏌?storageState
    const statePath = process.env.PLAYWRIGHT_STATE_PATH
        ? path.resolve(process.env.PLAYWRIGHT_STATE_PATH)
        : path.resolve(process.cwd(), '.state.json');
    let contextOptions = {};

    try {
        await fs.access(statePath);
        console.log('鍙戠幇 .state.json锛屼娇鐢ㄤ細璇濈櫥褰?..');
        contextOptions.storageState = statePath;
    } catch (e) {
        console.log('鏈壘鍒?.state.json锛屽尶鍚嶆姄鍙?..');
    }

    await acquireContextSlot();
    let context = null;
    try {
        context = await browser.newContext(contextOptions);
        const page = await context.newPage();
        const response = await page.goto(url, { waitUntil: 'domcontentloaded' });
        const content = await page.content();
        const finalUrl = page.url();
        const status = response ? response.status() : 0;

        return {
            content,
            finalUrl,
            status
        };
    } catch (error) {
        console.error('Playwright 鎶撳彇澶辫触:', error);
        throw error;
    } finally {
        try {
            if (context) {
                await context.close(); // Only close Context; keep Browser alive
            }
        } finally {
            releaseContextSlot();
        }
    }
}

