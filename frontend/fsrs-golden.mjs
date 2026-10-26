import { fsrs, createEmptyCard, Rating } from 'ts-fsrs'

const BASE = new Date(Date.UTC(2026, 0, 5, 8, 0, 0))
const DAY = 24 * 60 * 60 * 1000

function toJson(card) {
  return {
    due: card.due.getTime(),
    stability: card.stability,
    difficulty: card.difficulty,
    elapsed_days: card.elapsed_days,
    scheduled_days: card.scheduled_days,
    learning_steps: card.learning_steps,
    reps: card.reps,
    lapses: card.lapses,
    state: card.state,
    last_review: card.last_review ? card.last_review.getTime() : null,
  }
}

function toLog(log) {
  return {
    rating: log.rating,
    state: log.state,
    due: log.due.getTime(),
    stability: log.stability,
    difficulty: log.difficulty,
    elapsed_days: log.elapsed_days,
    last_elapsed_days: log.last_elapsed_days,
    scheduled_days: log.scheduled_days,
    learning_steps: log.learning_steps,
    review: log.review.getTime(),
  }
}

function previewScenario(name, config, card, at) {
  const engine = fsrs(config)
  const record = engine.repeat(card, new Date(BASE.getTime() + at))
  const results = {}
  for (const g of [Rating.Again, Rating.Hard, Rating.Good, Rating.Easy]) {
    results[g] = {
      card: toJson(record[g].card),
      log: toLog(record[g].log),
    }
  }
  return { name, config, type: 'preview', at, initial: toJson(card), results }
}

function sequenceScenario(name, config, steps) {
  const engine = fsrs(config)
  let card = createEmptyCard(BASE)
  const records = []
  for (const step of steps) {
    const now = new Date(BASE.getTime() + step.at)
    const result = engine.next(card, now, step.grade)
    records.push({
      at: step.at,
      grade: step.grade,
      card: toJson(result.card),
      log: toLog(result.log),
    })
    card = result.card
  }
  return { name, config, type: 'sequence', records }
}

function retrievabilityScenario(name, config, card, checks) {
  const engine = fsrs(config)
  const results = []
  for (const c of checks) {
    const now = new Date(BASE.getTime() + c.at)
    results.push({
      at: c.at,
      value: engine.get_retrievability(card, now, false),
    })
  }
  return { name, config, type: 'retrievability', initial: toJson(card), results }
}

const scenarios = []

// 1. 新卡预览（fuzz 开启 / short-term 开启）
scenarios.push(previewScenario('preview-new-fuzz-on',
  { request_retention: 0.9, enable_fuzz: true, enable_short_term: true },
  createEmptyCard(BASE), 0))

// 2. 新卡预览（fuzz 关闭）
scenarios.push(previewScenario('preview-new-fuzz-off',
  { request_retention: 0.9, enable_fuzz: false, enable_short_term: true },
  createEmptyCard(BASE), 0))

// 3. 新卡预览（short-term 关闭 → LongTermScheduler）
scenarios.push(previewScenario('preview-new-longterm',
  { request_retention: 0.9, enable_fuzz: false, enable_short_term: false },
  createEmptyCard(BASE), 0))

// 4. 连续 Good 链（Basic）
scenarios.push(sequenceScenario('chain-good-basic',
  { request_retention: 0.9, enable_fuzz: false, enable_short_term: true },
  [
    { at: 0,       grade: Rating.Good },   // New → Learning (1m)
    { at: 60_000,  grade: Rating.Good },   // Learning → Learning (10m)
    { at: 600_000, grade: Rating.Good },   // Learning → Review
    { at: DAY,     grade: Rating.Good },   // Review +1d
    { at: DAY*4,   grade: Rating.Good },   // +3d
    { at: DAY*10,  grade: Rating.Good },   // +6d
  ]))

// 5. 混合链：Again → Good → Again → Good
scenarios.push(sequenceScenario('chain-mixed',
  { request_retention: 0.9, enable_fuzz: false, enable_short_term: true },
  [
    { at: 0,       grade: Rating.Good },
    { at: 60_000,  grade: Rating.Good },
    { at: 600_000, grade: Rating.Again },  // Review → Relearning
    { at: 600_000 + 60_000, grade: Rating.Good }, // Relearning → Review
    { at: 600_000 + 60_000 + DAY*2, grade: Rating.Again }, // Review → Relearning
  ]))

// 6. 复习卡预览（提前造一张 Review 状态卡片）
let reviewCard = createEmptyCard(BASE)
const e1 = fsrs({ request_retention: 0.9, enable_fuzz: false, enable_short_term: true })
reviewCard = e1.next(reviewCard, new Date(BASE.getTime() + 60_000), Rating.Good).card
reviewCard = e1.next(reviewCard, new Date(BASE.getTime() + 600_000), Rating.Good).card
// 此时 reviewCard 应该是 Review 状态
scenarios.push(previewScenario('preview-review-fuzz-on',
  { request_retention: 0.9, enable_fuzz: true, enable_short_term: true },
  reviewCard, DAY * 3))

// 7. retrievability
scenarios.push(retrievabilityScenario('retrievability-review',
  { request_retention: 0.9, enable_fuzz: false, enable_short_term: true },
  reviewCard,
  [{ at: 0 }, { at: DAY }, { at: DAY * 3 }, { at: DAY * 7 }]))

console.log(JSON.stringify({
  version: '5.4.2',
  baseTime: BASE.getTime(),
  scenarios,
}, null, 2))
