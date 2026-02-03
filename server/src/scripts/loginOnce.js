
import { chromium } from 'playwright';
import fs from 'fs/promises';
import path from 'path';
import 'dotenv/config';

// 读取环境变量
const BASE_URL = process.env.FORUM_BASE_URL || 'https://www.4d4y.com/forum';

// 文件路径 (可通过环境变量覆盖)
const STATE_PATH = process.env.PLAYWRIGHT_STATE_PATH
    ? path.resolve(process.env.PLAYWRIGHT_STATE_PATH)
    : path.resolve(process.cwd(), '.state.json');
const FAIL_DIR = process.env.TESTDATA_DIR
    ? path.resolve(process.env.TESTDATA_DIR)
    : path.resolve(process.cwd(), '..', 'shared', 'testdata');

async function run() {
    console.log(`启动浏览器 (Headless: false)...`);
    // 必须使用非无头模式，否则用户无法操作
    const browser = await chromium.launch({ headless: false });
    const context = await browser.newContext();
    const page = await context.newPage();

    try {
        // 1. 访问登录页
        const loginUrl = `${BASE_URL}/logging.php?action=login`;
        console.log(`访问: ${loginUrl}`);
        await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });

        console.log('\n!!! 请注意 !!!');
        console.log('请在弹出的浏览器窗口中手工输入账号密码并登录。');
        console.log('如果遇到 Cloudflare 验证，请手动完成。');
        console.log('脚本正在等待您登录成功（检测到退出链接）...\n');

        // 2. 等待用户手动登录
        // 登录成功后通常会出现 "退出" 链接 (action=logout)
        // 设置 5 分钟超时，足够用户输入和处理验证码
        try {
            await page.waitForSelector('a[href*="action=logout"]', { timeout: 300000 });
            console.log('检测到退出链接，判断为登录成功！');
        } catch (e) {
            console.error('等待登录超时 (5分钟) 或未检测到登录状态。');
            throw e;
        }

        // 3. 再次验证访问权限 (fid=2)
        const verifyUrl = `${BASE_URL}/forumdisplay.php?fid=2`;
        console.log(`正在验证访问权限: ${verifyUrl}`);
        await page.goto(verifyUrl, { waitUntil: 'domcontentloaded' });

        const content = await page.content();
        const pageTitle = await page.title();
        const currentUrl = page.url();
        const viewThreadLinks = await page.locator('a[href*="viewthread.php?tid="]').count();

        // 简单的判定
        const isSuccess = viewThreadLinks >= 3; // 只要能看到一些帖子就算成功

        if (isSuccess) {
            console.log(`\nOK | Title: ${pageTitle} | URL: ${currentUrl}`);
            // 保存状态
            await context.storageState({ path: STATE_PATH });
            console.log(`Session 已保存至: ${STATE_PATH}`);
            console.log('您现在可以运行 npm run dev 并访问 API 了。');
        } else {
            console.error(`\n验证失败 | Title: ${pageTitle} | ThreadsFound: ${viewThreadLinks}`);
            console.log('可能登录未完全成功，或者没有权限访问该版块。');
        }

    } catch (error) {
        console.error('Script Error:', error);
    } finally {
        console.log('关闭浏览器...');
        await browser.close();
    }
}

run();
