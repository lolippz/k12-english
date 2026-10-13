/**
 * 前端冒烟测试：用 Vite 的 SSR 模块加载器直接加载真实源码模块并断言行为。
 * 运行: node smoke.mjs
 *
 * 覆盖范围：词库数据质量（清洗回归、增强字段、发音音频）、词库加载层、FSRS 记忆调度。
 *
 * 注意：学习队列与学情统计的断言**不在这里**——那两块逻辑已经搬到后端
 * （StudyService / StatsService），对应 com.k12english.service.StudyServiceTest
 * 和 StatsServiceTest。同一份逻辑只测一处，避免两边漂移后测试仍然全绿。
 */
import { readFileSync } from 'node:fs'
import { createServer } from 'vite'

/** 词库本体在仓库根目录，Maven 打包时也会复制进 jar。 */
const DICT = JSON.parse(readFileSync(new URL('../data/words.json', import.meta.url), 'utf8'))

const server = await createServer({
  configFile: './vite.config.ts',
  server: { middlewareMode: true },
  appType: 'custom',
  logLevel: 'error',
})

let pass = 0
let fail = 0
function ok(name, cond, extra = '') {
  if (cond) {
    pass++
    console.log(`  \u2713 ${name}${extra ? '  ' + extra : ''}`)
  } else {
    fail++
    console.log(`  \u2717 ${name}  ${extra}`)
  }
}

try {
  const data = await server.ssrLoadModule('/src/data/index.ts')
  const srs = await server.ssrLoadModule('/src/lib/srs.ts')

  console.log('\n[0] 词库加载层')
  ok('加载前是空的', data.isHydrated() === false)
  ok('加载前查不到 apple', data.findWord('apple') === undefined)
  data.hydrateWords(DICT.words)
  ok('灌入后 isHydrated 为 true', data.isHydrated() === true)
  ok('灌入后能查到 apple', !!data.findWord('apple'))
  ok('WORDS 数组引用保持不变', data.WORDS.length === DICT.words.length, `${data.WORDS.length} 条`)
  // 重复灌入不应累加
  data.hydrateWords(DICT.words)
  ok('重复灌入不累加', data.WORDS.length === DICT.words.length, `${data.WORDS.length} 条`)

  console.log('\n[1] 词库数据')
  ok('词条数 > 6500', data.WORDS.length > 6500, `实际 ${data.WORDS.length}`)
  ok('词条无重复', new Set(data.WORDS.map((w) => w.w)).size === data.WORDS.length,
    `${new Set(data.WORDS.map((w) => w.w)).size} / ${data.WORDS.length}`)
  const apple = data.findWord('apple')
  ok('能查到 apple', !!apple)
  ok('apple 有释义', !!apple && apple.tr.length > 0, apple ? apple.tr[0] : '')
  ok('apple 有音标', !!apple && !!apple.ph, apple ? apple.ph : '')
  const allStages = new Set(data.WORDS.flatMap((w) => w.stages))
  ok('三个学段都有词', ['primary', 'junior', 'senior'].every((s) => allStages.has(s)), [...allStages].join(','))
  const primary = data.wordsOfStages(['primary'])
  const junior = data.wordsOfStages(['junior'])
  const senior = data.wordsOfStages(['senior'])
  ok('小学词池非空', primary.length > 500, `${primary.length} 词`)
  ok('初中词池非空', junior.length > 1500, `${junior.length} 词`)
  ok('高中词池非空', senior.length > 3000, `${senior.length} 词`)
  const books = data.booksOfStages(['primary', 'junior', 'senior'])
  ok('册次标签可用', books.length > 20, `${books.length} 个册次`)
  const pep7 = data.wordsOfBooks(['junior'], ['人教七年级上'])
  ok('按册次过滤生效', pep7.length > 300 && pep7.length < junior.length, `人教七年级上 ${pep7.length} 词`)

  console.log('\n[2] 增强字段')
  const withPhon = data.WORDS.filter((w) => w.ph).length
  const phonRate = withPhon / data.WORDS.length
  ok('音标覆盖率 >= 98%', phonRate >= 0.98, `${(phonRate * 100).toFixed(1)}%`)
  const mathsBook = data.findWord('maths book')
  ok('短语音标由组成词合成', !!mathsBook && /mæθs/.test(mathsBook.ph) && /bʊk/.test(mathsBook.ph), mathsBook ? mathsBook.ph : '未找到')
  const ruler = data.findWord('ruler')
  ok('词形变化已解析', !!ruler?.forms?.['复数'], ruler?.forms ? JSON.stringify(ruler.forms) : '无')
  ok('考试标签已解析', Array.isArray(ruler?.tags) && ruler.tags.length > 0, (ruler?.tags ?? []).join(','))
  ok('柯林斯星级已解析', typeof ruler?.star === 'number' && ruler.star > 0, String(ruler?.star))
  ok('牛津 3000 标记存在', ruler?.oxford === 1)
  ok('词频已解析', typeof ruler?.frq === 'number', String(ruler?.frq))
  ok('英文释义已解析', !!ruler?.def && !/\\n/.test(ruler.def), ruler?.def ?? '')
  const exCount = data.WORDS.filter((w) => w.ex).length
  ok('例句覆盖率 > 55%', exCount / data.WORDS.length > 0.55, `${exCount} 条 (${((exCount / data.WORDS.length) * 100).toFixed(1)}%)`)
  ok('例句中文已转简体', DICT.meta.examples_all_simplified === true, String(DICT.meta.examples_all_simplified))
  const badEx = data.WORDS.filter((w) => w.ex && (!w.ex[0] || !w.ex[1] || /[\r\n]/.test(w.ex[0] + w.ex[1])))
  ok('例句无空值/换行', badEx.length === 0, `异常 ${badEx.length}`)

  console.log('\n[2b] 词表清洗回归')
  // 用清洗前实际存在的字符串做精确比对。
  // 不要用 /^(go|have)\s+\w/ 这种宽泛正则——它会误伤 'go to school' / 'have a look'
  // 这类正当短语，以及 'U.K.' / 'Miss.' 这类缩写。
  const BAD_TABLES = [
    'go went gone', 'bad worse worst', 'foot feet', 'man men', 'have has had had',
    'begin began begun', 'beat beat beaten', 'drink drank drunk', 'come came come',
    'buy bought bought', 'shall should', 'criterion criteria', 'leaf leaves',
    'woman women', 'goose geese', 'take took taken', 'write wrote written',
  ]
  const leftover = data.WORDS.filter((w) => BAD_TABLES.includes(w.w))
  ok('变化表已折叠（无 "go went gone" 类词条）', leftover.length === 0,
    leftover.map((w) => w.w).join(' | ') || '无')
  ok('原变化表首词已独立成条', ['go', 'foot', 'man', 'begin', 'take'].every((x) => !!data.findWord(x)),
    ['go', 'foot', 'man', 'begin', 'take'].filter((x) => !data.findWord(x)).join(',') || '全部存在')
  // 复现 D3 的判定条件：单个句点 + 小写开头 + 词内无其它句点
  const dotLeftover = data.WORDS.filter((w) =>
    w.w.endsWith('.') && !w.w.endsWith('..') &&
    w.w[0] === w.w[0].toLowerCase() && !w.w.slice(0, -1).includes('.'))
  ok('无尾部句点的单词词条', dotLeftover.length === 0,
    dotLeftover.map((w) => w.w).join(' | ') || '无')
  ok('无别名 "=" 残留', !data.WORDS.some((w) => w.w.includes('=')),
    data.WORDS.filter((w) => w.w.includes('=')).map((w) => w.w).join(' | ') || '无')
  ok('无连续空格', !data.WORDS.some((w) => /\s{2,}/.test(w.w)),
    data.WORDS.filter((w) => /\s{2,}/.test(w.w)).map((w) => w.w).join(' | ') || '无')
  ok('无 "(be)" 前缀残留', !data.WORDS.some((w) => /^\(be\)/i.test(w.w)),
    data.WORDS.filter((w) => /^\(be\)/i.test(w.w)).map((w) => w.w).join(' | ') || '无')
  const goWord = data.findWord('go')
  ok('go 已是独立词条且有释义', !!goWord && goWord.tr.length > 0, goWord ? goWord.tr[0] : '未找到')
  const bikeWord = data.findWord('bike')
  ok('别名已提取（bike -> bicycle）', bikeWord?.alt === 'bicycle', String(bikeWord?.alt))
  const runOut = data.findWord('run out (of)')
  ok('短语介词未被误剥（run out (of)）', !!runOut, runOut ? runOut.tr[0] : '未找到')
  const overnight = data.findWord('overnight')
  ok('拼写错误已纠正（overnight）', !!overnight, overnight ? overnight.tr[0] : '未找到')

  console.log('\n[2c] 发音音频')
  const withAudio = data.WORDS.filter((w) => w.a).length
  ok('全部词条都有音频文件名', withAudio === data.WORDS.length, `${withAudio} / ${data.WORDS.length}`)
  const badSlug = data.WORDS.filter((w) => w.a && !/^[a-z0-9_]+$/.test(w.a))
  ok('音频文件名均为安全 slug', badSlug.length === 0, badSlug.slice(0, 3).map((w) => w.a).join(' | ') || '无')

  console.log('\n[3] FSRS 记忆调度')
  const t0 = Date.now()
  let card = srs.newCard('apple', t0)
  ok('新卡状态为 New', card.state === srs.State.New)
  ok('新卡 reps 为 0', card.reps === 0)

  const iv = srs.previewIntervals(card, t0)
  ok('四档间隔都有值', Object.keys(iv).length === 4, JSON.stringify(Object.fromEntries(Object.entries(iv).map(([k, v]) => [k, +v.toFixed(3)]))))
  ok('简单 >= 记得', iv['4'] >= iv['3'], `${iv['4'].toFixed(3)} vs ${iv['3'].toFixed(3)}`)

  card = srs.review(card, srs.Rating.Good, t0)
  ok('答对后 reps = 1', card.reps === 1)
  ok('答对后 due 在未来', card.due > t0)
  ok('答对后 seen = 1 且 wrong = 0', card.seen === 1 && card.wrong === 0)
  const s1 = card.stability

  let c2 = card
  let day = t0
  for (let i = 0; i < 4; i++) {
    day = c2.due
    c2 = srs.review(c2, srs.Rating.Good, day)
  }
  ok('连续答对后稳定度增长', c2.stability > s1, `${s1.toFixed(2)} -> ${c2.stability.toFixed(2)}`)
  ok('连续答对后间隔拉长', c2.scheduled_days > card.scheduled_days, `${card.scheduled_days} -> ${c2.scheduled_days} 天`)

  const before = c2.stability
  const lapsed = srs.review(c2, srs.Rating.Again, day)
  ok('答错后 lapses +1', lapsed.lapses === c2.lapses + 1)
  ok('答错后稳定度下降', lapsed.stability < before, `${before.toFixed(2)} -> ${lapsed.stability.toFixed(2)}`)
  ok('答错后 wrong 计数 +1', lapsed.wrong === c2.wrong + 1)

  const r = srs.retention(c2, day)
  ok('保持率在 0-1 之间', r >= 0 && r <= 1, r.toFixed(3))
  ok('humanInterval 可读', srs.humanInterval(0.5) === '12 小时', srs.humanInterval(0.5))
  ok('mastered 判定可用', typeof srs.isMastered(c2) === 'boolean', String(srs.isMastered(c2)))

  console.log(`\n结果: ${pass} 通过 / ${fail} 失败\n`)
} catch (e) {
  console.error('\n冒烟测试异常:', e)
  fail++
} finally {
  await server.close()
}

process.exit(fail ? 1 : 0)
