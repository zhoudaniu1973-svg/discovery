import * as cheerio from 'cheerio';

export function parseDiscovery(html) {
    const $ = cheerio.load(html);
    const threads = [];
    const parseStats = {
        totalFound: 0,
        failedCount: 0
    };

    // 提取页面信息
    const pageTitle = $('title').text().trim();

    // 提取 Flags
    const htmlLower = html.toLowerCase();
    const hasLoginForm =
        $('form[action*="logging"]').length > 0 ||
        $('input[name="formhash"]').length > 0 ||
        html.includes('登录') ||
        htmlLower.includes('login');

    // 检测 Cloudflare 和 帖子链接数量
    const hasCfChallenge =
        htmlLower.includes('cloudflare') ||
        html.includes('Checking your browser') ||
        htmlLower.includes('__cf_chl') ||
        htmlLower.includes('cf-chl');

    const viewThreadLinksCount = $('a[href*="viewthread.php?tid="]').length;
    const hasViewThreadLinks = viewThreadLinksCount; // 兼容 debug 显示

    const flags = {
        hasLoginForm,
        hasCloudflare: hasCfChallenge,
        hasViewThreadLinks: viewThreadLinksCount
    };

    // 如果被 Cloudflare 拦截 或者 帖子列表为空(且不是因为空版块)，视为 "not_ready"
    // 注意：未登录时能看到少量帖子吗？通常 Discuz 会完全隐藏。
    // 这里按要求：hasCfChallenge=true 或 hasViewThreadLinks<3 则返回空
    if (hasCfChallenge || viewThreadLinksCount < 3) {
        return {
            threads: [],
            parseStats: {
                totalFound: 0,
                failedCount: 0,
                error: hasCfChallenge ? 'cloudflare_challenge' : 'not_ready'
            },
            pageTitle,
            flags
        };
    }

    // 检查是否需要登录 (逻辑保持不变，但更新错误信息)
    const needLogin = html.includes('您还未登录') ||
        html.includes('无权访问该版块') ||
        $('#loginform').length > 0;

    if (needLogin) {
        parseStats.error = '需要登录访问';
    }

    // 策略：搜索所有包含 viewthread.php 链接的 tr 行
    // 这种方式不依赖具体的 id 或 class，更通用
    const seenTids = new Set();

    $('tr').each((i, tr) => {
        // 初步筛选：该行必须包含至少一个 viewthread.php 链接
        const links = $(tr).find('a[href*="viewthread.php"]');
        if (links.length === 0) return;

        // 尝试从链接中提取 TID
        let tid = null;
        let title = null;
        let authorName = null;
        let replyCount = null;
        let viewCount = null;
        let excerpt = null;
        let debugReason = null;

        try {
            // 1. 提取 TID 和 标题
            // 遍历所有链接，找到这就看起来像主题链接的（包含 tid 参数）
            let titleLink = null;

            for (const link of links) {
                const href = $(link).attr('href');
                const match = href.match(/tid=(\d+)/);
                if (match) {
                    // 过滤掉可能是“最后回复”的链接（通常文本很短或者是时间）
                    // 这里简单策略：优先取文本较长的链接作为标题链接
                    const text = $(link).text().trim();
                    if (!titleLink || text.length > titleLink.text().length) {
                        titleLink = $(link);
                        tid = match[1];
                    }
                }
            }

            if (!tid) {
                // 如果这行里找不到带 tid 的链接，可能不是主题行
                return;
            }

            if (seenTids.has(tid)) {
                return; // 去重
            }
            seenTids.add(tid);
            parseStats.totalFound++;

            // 提取标题
            if (titleLink) {
                title = titleLink.text().trim();
            }

            if (!title) {
                // 回退：尝试从该行其他位置找文本
                title = $(tr).text().slice(0, 30).trim() || "解析失败";
                debugReason = "未找到明确标题链接";
            }

            // 2. 提取作者 (尝试找 href 包含 space-uid 的链接，或者直接找文本)
            const authorLink = $(tr).find('a[href*="space-uid"]');
            if (authorLink.length > 0) {
                authorName = authorLink.first().text().trim();
            } else {
                // 回退，尝试找列
                const tds = $(tr).find('td');
                // 作者通常在中间某列，这里难以精确，暂置空或基于特定规则
                if (tds.length >= 3) {
                    // 暂不强求，保持 null
                }
            }

            // 3. 提取 回复/查看
            const tds = $(tr).find('td');
            tds.each((j, td) => {
                const text = $(td).text().trim();
                const statsMatch = text.match(/^(\d+)\s*\/\s*(\d+)$/);
                if (statsMatch) {
                    replyCount = parseInt(statsMatch[1], 10);
                    viewCount = parseInt(statsMatch[2], 10);
                }
            });

        } catch (e) {
            parseStats.failedCount++;
            tid = tid || `unknown-${i}`;
            title = title || "解析失败";
            debugReason = e.message;
        }

        // 构造对象
        threads.push({
            tid,
            title,
            authorName,
            authorUid: null,
            replyCount,
            viewCount,
            lastReplyTime: null,
            excerpt,
            debugReason
        });
    });

    return {
        threads,
        parseStats,
        pageTitle, // 新增
        flags      // 新增
    };
}
