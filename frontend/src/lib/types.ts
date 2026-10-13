import type { Stage } from '../data'

export type StudyMode = 'mix' | 'spell' | 'listen' | 'meaning'

export interface Settings {
  /** 参与学习的学段 */
  stages: Stage[]
  /** 限定册次，空数组表示该学段全部 */
  books: string[]
  /** 每日新词上限 */
  newPerDay: number
  /** 每日复习上限 */
  reviewPerDay: number
  /** 出题模式 */
  mode: StudyMode
  /** 发音口音 */
  accent: 'us' | 'uk'
  /** 答题提示音 */
  sound: boolean
}

export const DEFAULT_SETTINGS: Settings = {
  stages: ['primary', 'junior', 'senior'],
  books: [],
  newPerDay: 20,
  reviewPerDay: 120,
  mode: 'mix',
  accent: 'us',
  sound: true,
}

export interface LogEntry {
  id?: number
  /** 单词 */
  w: string
  /** 时间戳 */
  ts: number
  /** 评分 1=忘了 2=模糊 3=记得 4=简单 */
  grade: number
  /** 出题模式 */
  mode: StudyMode
  /** 是否答对 */
  correct: boolean
}
