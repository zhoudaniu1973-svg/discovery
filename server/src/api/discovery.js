import express from 'express';
import fs from 'fs/promises';
import path from 'path';
import { browserFetch } from '../browserFetch.js';
import { parseDiscovery } from '../parser/discoveryParser.js';

const router = express.Router();

const SNAPSHOT_ENABLED = ['1', 'true', 'yes'].includes((process.env.SNAPSHOT_DEBUG || '').toLowerCase());
const SNAPSHOT_PATH = process.env.SNAPSHOT_PATH
    ? path.resolve(process.env.SNAPSHOT_PATH)
    : path.resolve(process.cwd(), '..', 'shared', 'testdata', 'last_discovery.html');

router.get('/discovery', async (req, res) => {
    const rawPage = req.query.page;
    const parsedPage = Number.parseInt(rawPage, 10);
    if (Number.isNaN(parsedPage) || parsedPage < 1 || parsedPage > 200) {
        return res.status(400).json({
            threads: [],
            error: 'Invalid page parameter. Must be an integer between 1 and 200.'
        });
    }
    const page = parsedPage || 1;
    // TODO: 鏍规嵁 page 鏋勫缓 URL锛屾殏鏃跺彧鎶撳彇 fid=2 鐨勯椤典綔涓烘紨绀?
    // 涔嬪悗鍙牴鎹?page 淇敼 URL 鍙傛暟锛屼緥濡?&page=2
    const targetUrl = `https://www.4d4y.com/forum/forumdisplay.php?fid=2&page=${page}`;

    const debug = {
        steps: [],
        parse: { totalFound: 0, failedCount: 0 }
    };

    try {
        // 1. Fetch
        debug.steps.push({ step: 'fetch_start', url: targetUrl });
        const { content, finalUrl, status } = await browserFetch(targetUrl);

        debug.steps.push({
            step: 'fetch_complete',
            finalUrl,
            status,
            bodySnippet: content.substring(0, 200)
        });

        // 淇濆瓨 HTML 蹇収 (only in debug)
        if (SNAPSHOT_ENABLED) {
            try {
                const snapshotDir = path.dirname(SNAPSHOT_PATH);
                await fs.mkdir(snapshotDir, { recursive: true });
                await fs.writeFile(SNAPSHOT_PATH, content);
                debug.steps.push({ step: 'save_snapshot', path: SNAPSHOT_PATH });
            } catch (saveError) {
                debug.steps.push({ step: 'save_snapshot_failed', error: saveError.message });
                console.error('淇濆瓨蹇収澶辫触:', saveError);
            }
        } else {
            debug.steps.push({ step: 'save_snapshot_skipped' });
        }
        // 2. Parse
        debug.steps.push({ step: 'parse_start' });
        const { threads, parseStats, pageTitle, flags } = parseDiscovery(content);

        debug.parse = parseStats;
        debug.pageTitle = pageTitle;
        debug.flags = flags;
        debug.steps.push({ step: 'parse_complete', count: threads.length });

        res.json({
            threads,
            debug
        });

    } catch (error) {
        debug.steps.push({ step: 'error', message: error.message });
        res.status(500).json({
            threads: [],
            debug,
            error: error.message
        });
    }
});

export default router;



