import { useCallback, useEffect, useState } from 'react'
import { STAGE_LABEL, STAGE_DESC } from '../data'
import type { Settings } from '../lib/types'
import { api, type QueueResult, type StatsResult } from '../lib/api'

interface Props {
  settings: Settings
  cardCount: number
  onStart: () => void
  onGoSettings: () => void
}

export default function Home({ settings, cardCount, onStart, onGoSettings }: Props) {
  const [stats, setStats] = useState<StatsResult | null>(null)
  const [queue, setQueue] = useState<QueueResult | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [s, q] = await Promise.all([api.stats(), api.queue()])
      setStats(s)
      setQueue(q)
      setError('')
    } catch (e) {
      setError((e as Error).message)
    }
  }, [])

  // 每次回到「今日」都重新取一次：刚学完一轮回来时数字必须是最新的
  useEffect(() => {
    void load()
  }, [load, cardCount, settings])

  if (error) {
    return (
      <div className="main">
        <h1>今日学习</h1>
        <div className="empty">
          <div className="big">!</div>
          <p className="sub">读取学情失败：{error}</p>
          <button className="btn" onClick={load}>
            重试
          </button>
        </div>
      </div>
    )
  }

  if (!stats || !queue) {
    return (
      <div className="main">
        <h1>今日学习</h1>
        <div className="empty">
          <div className="big">·</div>
          正在统计…
        </div>
      </div>
    )
  }

  const target = queue.reviews.length + queue.fresh.length
  const plan = stats.today + target
  const pct = plan ? Math.min(100, Math.round((stats.today / plan) * 100)) : 0

  return (
    <div className="main">
      <h1>今日学习</h1>
      <p className="sub">
        {settings.stages.map((s) => STAGE_LABEL[s]).join(' · ')}
        {settings.books.length ? ` · 已限定 ${settings.books.length} 个册次` : ''} · 词池 {stats.poolSize} 词
      </p>

      <div className="hero">
        <div className="hero-top">
          <span className="hero-num">{stats.today}</span>
          <span className="hero-unit">词已学习</span>
          <span className="spacer" />
          <span className="tag blue">连续 {stats.streak} 天</span>
        </div>
        <div className="bar">
          <i style={{ width: `${pct}%` }} />
        </div>
        <div className="hero-actions">
          <button className="btn primary lg" onClick={onStart} disabled={target === 0}>
            {target === 0 ? '今日已完成' : `开始学习 · ${target} 词`}
          </button>
          {target > 0 && (
            <span className="muted" style={{ fontSize: 13.5 }}>
              复习 {queue.reviews.length} · 新词 {queue.fresh.length}
            </span>
          )}
          {target === 0 && (
            <button className="btn ghost" onClick={onGoSettings}>
              调整学习范围
            </button>
          )}
        </div>
      </div>

      <div className="grid3">
        <div className="stat">
          <div className="k">待复习</div>
          <div className="v">
            {stats.dueCount}
            <small>词</small>
          </div>
        </div>
        <div className="stat">
          <div className="k">已掌握</div>
          <div className="v">
            {stats.mastered}
            <small>词</small>
          </div>
        </div>
        <div className="stat">
          <div className="k">累计正确率</div>
          <div className="v">
            {Math.round(stats.accuracy * 100)}
            <small>%</small>
          </div>
        </div>
      </div>

      <div className="card">
        <h2>学习范围</h2>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {settings.stages.map((s) => (
            <div key={s} className="row">
              <span className="tag">{STAGE_LABEL[s]}</span>
              <span className="muted" style={{ fontSize: 13 }}>
                {STAGE_DESC[s]}
              </span>
            </div>
          ))}
        </div>
        <p className="muted" style={{ fontSize: 13, marginTop: 16, marginBottom: 0 }}>
          词池中还有 <b style={{ color: 'var(--text)' }}>{stats.remainingNew}</b> 个新词未学。已建立{' '}
          {cardCount} 张记忆卡片。每日新词上限 {settings.newPerDay}，复习上限 {settings.reviewPerDay}。
        </p>
      </div>
    </div>
  )
}
