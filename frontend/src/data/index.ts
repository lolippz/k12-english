/**
 * 词库访问层。
 *
 * 词条不打包进 JS，而是启动时从后端 `GET /api/words` 取一次灌进内存
 * （见 main.tsx 的 bootstrap），这里对外仍保持同步查询接口，页面代码不用改成 async。
 *
 * 数据本体在仓库根的 `data/words.json`，由 tools/ 下的 Python 流水线产出
 * （qwerty-learner 词表 + ECDICT 增强 + Tatoeba 例句），Maven 打包时复制进 jar。
 */

export type Stage = 'primary' | 'junior' | 'senior'

export interface Word {
  /** 单词 */
  w: string
  /** 美式音标 */
  ph: string
  /** 英式音标 */
  phUk: string
  /** 释义列表 */
  tr: string[]
  /** 所属学段 */
  stages: Stage[]
  /** 来源册次 */
  books: string[]
  /** 词形变化，如 { 复数: 'rulers', 过去式: 'ruled' } */
  forms?: Record<string, string>
  /** 柯林斯星级 1-5 */
  star?: number
  /** 牛津 3000 核心词 */
  oxford?: number
  /** 考试标签，如 ['中考','高考'] */
  tags?: string[]
  /** COCA 词频排名，越小越常用 */
  frq?: number
  /** 英文释义 */
  def?: string
  /** 例句 [英文, 中文] */
  ex?: [string, string]
  /** 预生成发音音频的文件名（不含扩展名） */
  a?: string
  /** 别名 / 全称，来自源词表里 'bike = bicycle' 这类写法 */
  alt?: string
}

/**
 * 全量词条，按教材顺序。启动时由 {@link hydrateWords} 填充。
 *
 * 刻意用 `const` + 原地改写而不是 `let` 重新赋值：模块导出的是这个数组的引用，
 * 直接换引用会让已经 import 过它的模块拿到旧数组。
 */
export const WORDS: Word[] = []

let index = new Map<string, Word>()

/** 用后端返回的词表替换内存里的词库，并重建查词索引。 */
export function hydrateWords(list: Word[]): void {
  WORDS.length = 0
  WORDS.push(...list)
  const next = new Map<string, Word>()
  for (const x of WORDS) next.set(x.w.toLowerCase(), x)
  index = next
}

/** 词库是否已经加载完成。 */
export function isHydrated(): boolean {
  return WORDS.length > 0
}

export const STAGES: Stage[] = ['primary', 'junior', 'senior']

export const STAGE_LABEL: Record<Stage, string> = {
  primary: '小学',
  junior: '初中',
  senior: '高中',
}

export const STAGE_DESC: Record<Stage, string> = {
  primary: '人教版 PEP / 新起点 · 重大版（三年级起点）',
  junior: '人教版 七上 — 九年级',
  senior: '人教版 必修 1-5 · 选修 6-11',
}

/** 按学段过滤词库 */
export function wordsOfStages(stages: Stage[]): Word[] {
  if (!stages.length) return []
  const set = new Set(stages)
  return WORDS.filter((x) => x.stages.some((s) => set.has(s)))
}

/** 某组学段下出现过的全部册次标签 */
export function booksOfStages(stages: Stage[]): string[] {
  const set = new Set<string>()
  for (const x of wordsOfStages(stages)) for (const b of x.books) set.add(b)
  return [...set].sort((a, b) => a.localeCompare(b, 'zh-Hans-CN'))
}

/** 按册次进一步过滤 */
export function wordsOfBooks(stages: Stage[], books: string[]): Word[] {
  const base = wordsOfStages(stages)
  if (!books.length) return base
  const set = new Set(books)
  return base.filter((x) => x.books.some((b) => set.has(b)))
}

export function findWord(w: string): Word | undefined {
  return index.get(w.toLowerCase())
}

export function displayTrans(w: Word): string {
  return w.tr[0] ?? ''
}
