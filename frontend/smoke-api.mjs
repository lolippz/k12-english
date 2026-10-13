/**
 * 前后端契约冒烟测试。
 *
 * 不是测后端接口本身（那个由 scripts/smoke-api.sh 覆盖），而是用 Vite 的 SSR 加载器
 * 把前端真实的 src/lib/api.ts 跑起来，对着真正运行的后端发请求。
 * 这样能抓住「后端改了字段名、前端还以为叫老名字」这类只在运行时才暴露的问题。
 *
 * 用法：
 *   # 另开终端启动后端
 *   java -jar target/k12-english.jar --server.port=8099 --app.open-browser=false
 *   node smoke-api.mjs http://127.0.0.1:8099
 */
import { createServer } from 'vite'

const BASE = process.argv[2] ?? process.env.API_BASE ?? 'http://127.0.0.1:8080'

let pass = 0
let fail = 0
const ok = (name) => { pass++; console.log(`  \x1b[32mPASS\x1b[0m ${name}`) }
const bad = (name, detail) => { fail++; console.log(`  \x1b[31mFAIL\x1b[0m ${name}${detail ? `（${detail}）` : ''}`) }
const section = (t) => console.log(`\n\x1b[1m${t}\x1b[0m`)
const eq = (name, actual, expected) =>
  actual === expected ? ok(name) : bad(name, `期望 ${expected} 实际 ${actual}`)
const truthy = (name, v) => (v ? ok(name) : bad(name, `得到 ${JSON.stringify(v)}`))
const throws = async (name, fn, match) => {
  try {
    await fn()
    bad(name, '本应抛错却成功了')
  } catch (e) {
    match && !String(e.message).includes(match)
      ? bad(name, `错误信息不含「${match}」：${e.message}`)
      : ok(name)
  }
}

/* api.ts 里 BASE 为空串（同源相对路径），Node 的 fetch 不认相对 URL，
   所以在外面套一层把 /api 补成绝对地址。应用代码本身不用改。 */
const realFetch = globalThis.fetch
let origin = BASE
globalThis.fetch = (url, init) =>
  realFetch(typeof url === 'string' && url.startsWith('/') ? origin + url : url, init)

const server = await createServer({
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'error',
})

try {
  const { api } = await server.ssrLoadModule('/src/lib/api.ts')

  section('1. 元信息与状态')
  const meta = await api.meta()
  eq('meta.totalWords = 6877', meta.totalWords, 6877)
  truthy('meta.stages 含三学段', meta.stages?.includes('senior'))

  const st0 = await api.state()
  truthy('state.settings 有 newPerDay', typeof st0.settings.newPerDay === 'number')
  truthy('state.cards 是数组', Array.isArray(st0.cards))
  truthy('state.logs 是数组', Array.isArray(st0.logs))

  section('2. 学习队列')
  const q = await api.queue()
  truthy('queue.reviews 是数组', Array.isArray(q.reviews))
  truthy('queue.fresh 非空', q.fresh.length > 0)
  truthy('queue.remainingNew 是数字', typeof q.remainingNew === 'number')

  section('3. 设置往返')
  const before = await api.getSettings()
  const changed = await api.saveSettings({ ...before, newPerDay: 7, mode: 'spell', accent: 'uk' })
  eq('保存后 newPerDay = 7', changed.newPerDay, 7)
  const reread = await api.getSettings()
  eq('重新读取仍是 7（真落库了）', reread.newPerDay, 7)
  await api.saveSettings(before)
  eq('已还原设置', (await api.getSettings()).newPerDay, before.newPerDay)

  section('4. 作答落库（前端 CardRecord 原样提交）')
  const now = Date.now()
  const card = {
    w: 'apple', due: now, stability: 1.5, difficulty: 5,
    elapsed_days: 0, scheduled_days: 1, learning_steps: 0,
    reps: 1, lapses: 0, state: 1, last_review: now,
    seen: 1, wrong: 0, firstSeen: now,
  }
  const saved = await api.review(card, { w: 'apple', ts: now, grade: 3, mode: 'spell', correct: true })
  truthy('返回的自增 id > 0', typeof saved.id === 'number' && saved.id > 0)

  const st1 = await api.state()
  const found = st1.cards.find((c) => c.w === 'apple')
  truthy('卡片已入库', !!found)
  eq('stability 未丢失', found?.stability, 1.5)
  eq('snake_case 字段 elapsed_days 未丢失', found?.elapsed_days, 0)
  eq('camelCase 字段 firstSeen 未丢失', found?.firstSeen, now)
  eq('日志已入库', st1.logs.length, 1)

  section('5. 学情聚合')
  const stats = await api.stats()
  eq('今日学习数 = 1', stats.today, 1)
  eq('卡片总数 = 1', stats.totalCards, 1)
  eq('最近 14 天序列长度', stats.daily.length, 14)
  eq('正确率 = 1', stats.accuracy, 1)
  truthy('byStage 有 primary', !!stats.byStage.primary)
  truthy('hardWords 是数组', Array.isArray(stats.hardWords))

  section('6. 备份导出 / 导入 / 重置')
  const exported = await api.exportAll()
  eq('导出卡片数 = 1', exported.cards.length, 1)
  eq('导出结构带 version', exported.version, 1)

  const imported = await api.importAll(exported)
  eq('导入后卡片数仍为 1（未重复）', imported.cards.length, 1)

  await api.reset()
  eq('重置后卡片清空', (await api.state()).cards.length, 0)
  eq('重置后日志清空', (await api.state()).logs.length, 0)

  section('7. 后端不可达时的错误可读')
  origin = 'http://127.0.0.1:1'
  await throws('抛出可读的连不上提示', () => api.meta(), '连不上后端服务')

  console.log(`\n\x1b[1m结果：${pass} 通过，${fail} 失败\x1b[0m`)
} finally {
  await server.close()
  globalThis.fetch = realFetch
}

process.exit(fail === 0 ? 0 : 1)
