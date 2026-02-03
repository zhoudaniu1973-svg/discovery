import fs from 'fs/promises';

async function debugHtml() {
    try {
        const res = await fetch('http://localhost:3000/test-fetch');
        const html = await res.text();
        await fs.writeFile('debug_page.html', html);
        console.log('HTML 已保存到 debug_page.html');
    } catch (error) {
        console.error('获取 HTML 失败:', error);
    }
}

debugHtml();
