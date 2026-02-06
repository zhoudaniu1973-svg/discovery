import express from 'express';

import discoveryRouter from './api/discovery.js';

const app = express();
const port = 3000;

// 挂载 API 路由
app.use('/api/v1', discoveryRouter);

const TEST_FETCH_ENABLED = ['1', 'true', 'yes'].includes((process.env.TEST_FETCH_ENABLED || '').toLowerCase());

// 根路由返回 { ok: true }
app.get('/', (req, res) => {
  res.json({ ok: true });
});

if (TEST_FETCH_ENABLED) {
  const { browserFetch } = await import('./browserFetch.js');
  app.get('/test-fetch', async (req, res) => {
    try {
      const url = 'https://www.4d4y.com/forum/forumdisplay.php?fid=2';
      const { content } = await browserFetch(url);
      // 返回完整 HTML
      res.set('Content-Type', 'text/plain; charset=utf-8');
      res.send(content);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  });
}

app.listen(port, () => {
  console.log(`服务器已启动，监听端口: ${port}`);
  console.log(`访问地址: http://localhost:${port}`);
});
