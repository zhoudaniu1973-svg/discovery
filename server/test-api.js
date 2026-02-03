// Node.js 18+ 原生支持 fetch，无需导入

async function testApi() {
    try {
        const res = await fetch('http://localhost:3000/api/v1/discovery?page=1');
        const data = await res.json();
        console.log(JSON.stringify(data, null, 2));
    } catch (error) {
        console.error('API 测试失败:', error);
    }
}

testApi();
