/**
 * FSRS 记忆调度封装
 * 算法来源: open-spaced-repetition/ts-fsrs (MIT)
 */
import { fsrs, createEmptyCard, Rating, State, type Card, type Grade } from 'ts-fsrs'

export { Rating, State }
export type { Grade }

export const scheduler = fsrs({
  request_retention: 0.9,
  enable_fuzz: true,
  enable_short_term: true,
})

/** 落库的记忆卡片记录（时间统一用 epoch ms，便于 IndexedDB 与比较） */
export interface CardRecord {
  w: string
  due: number
  stability: number
  difficulty: number
  elapsed_days: number
  scheduled_days: number
  learning_steps: number
  reps: number
  lapses: number
  state: number
  last_review?: number
  /** 累计作答次数（含重复） */
  seen: number
  /** 累计答错次数 */
  wrong: number
  /** 首次学习时间 */
  firstSeen: number
}

export const MASTER_STABILITY = 21 // 稳定度达到 3 周视为掌握

export function newCard(w: string, now = Date.now()): CardRecord {
  const c = createEmptyCard(new Date(now))
  return {
    w,
    due: now,
    stability: c.stability,
    difficulty: c.difficulty,
    elapsed_days: c.elapsed_days,
    scheduled_days: c.scheduled_days,
    learning_steps: c.learning_steps,
    reps: 0,
    lapses: 0,
    state: State.New,
    seen: 0,
    wrong: 0,
    firstSeen: now,
  }
}

function toCard(rec: CardRecord): Card {
  return {
    due: new Date(rec.due),
    stability: rec.stability,
    difficulty: rec.difficulty,
    elapsed_days: rec.elapsed_days,
    scheduled_days: rec.scheduled_days,
    learning_steps: rec.learning_steps,
    reps: rec.reps,
    lapses: rec.lapses,
    state: rec.state as State,
    last_review: rec.last_review ? new Date(rec.last_review) : undefined,
  }
}

function fromCard(rec: CardRecord, c: Card): CardRecord {
  return {
    ...rec,
    due: c.due.getTime(),
    stability: c.stability,
    difficulty: c.difficulty,
    elapsed_days: c.elapsed_days,
    scheduled_days: c.scheduled_days,
    learning_steps: c.learning_steps,
    reps: c.reps,
    lapses: c.lapses,
    state: c.state,
    last_review: c.last_review ? c.last_review.getTime() : rec.last_review,
  }
}

/** 提交一次作答，返回更新后的记录 */
export function review(rec: CardRecord, grade: Grade, now = Date.now()): CardRecord {
  const { card } = scheduler.next(toCard(rec), new Date(now), grade)
  const next = fromCard(rec, card)
  next.seen = rec.seen + 1
  next.wrong = rec.wrong + (grade === Rating.Again ? 1 : 0)
  return next
}

/** 预览四个评分档对应的下次复习间隔（天） */
export function previewIntervals(rec: CardRecord, now = Date.now()): Record<string, number> {
  const nowD = new Date(now)
  const rl = scheduler.repeat(toCard(rec), nowD)
  const out: Record<string, number> = {}
  const grades: Grade[] = [Rating.Again, Rating.Hard, Rating.Good, Rating.Easy]
  for (const g of grades) {
    const item = rl[g]
    const days = (item.card.due.getTime() - now) / 86400000
    out[String(g)] = days
  }
  return out
}

export function isDue(rec: CardRecord, now = Date.now()): boolean {
  return rec.due <= now
}

export function isMastered(rec: CardRecord): boolean {
  return rec.state === State.Review && rec.stability >= MASTER_STABILITY
}

/** 记忆保持率 0-1，用于展示"还记得多少" */
export function retention(rec: CardRecord, now = Date.now()): number {
  if (rec.state === State.New) return 0
  try {
    return scheduler.get_retrievability(toCard(rec), new Date(now), false)
  } catch {
    return 0
  }
}

export const STATE_LABEL: Record<number, string> = {
  [State.New]: '未学',
  [State.Learning]: '学习中',
  [State.Review]: '复习中',
  [State.Relearning]: '重学',
}

/** 人类可读的间隔描述 */
export function humanInterval(days: number): string {
  if (days < 1 / 1440) return '现在'
  if (days < 1 / 24) return `${Math.round(days * 1440)} 分钟`
  if (days < 1) return `${Math.round(days * 24)} 小时`
  if (days < 30) return `${Math.round(days)} 天`
  if (days < 365) return `${(days / 30).toFixed(1)} 个月`
  return `${(days / 365).toFixed(1)} 年`
}
