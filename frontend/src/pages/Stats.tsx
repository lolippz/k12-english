import { useCallback, useEffect, useState } from 'react'
import { findWord, STAGE_LABEL, type Stage } from '../data'
import { STATE_LABEL } from '../lib/srs'
import type { Settings } from '../lib/types'
import { api, type StatsResult } from '../lib/api'

interface Props {
  settings: Settings
  onGoSettings: () => void
}

export default function Stats({ settings, onGoSettings }: Props) {
  const [stats, setStats] = useState<StatsResult | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      setStats(await api.stats())
      setError('')
    } catch (e) {
      setError((e as Error).message)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  if (error) {
    return (
      <main className="main stats-page">
        <h1 className="page-title">学情</h1>
        <div className="empty">
          <div className="big">!</div>
          <p className="sub">读取学情失败：{error}</p>
          <button className="btn" onClick={load}>
            重试
          </button>
        </div>
      </main>
    )
  }

  if (!stats) {
    return (
      <main className="main stats-page">
        <h1 className="page-title">学情</h1>
        <div className="empty">
          <div className="big">·</div>
          正在统计…
        </div>
      </main>
    )
  }

  if (!stats.totalCards) {
    return (
      <main className="main stats-page">
        <h1 className="page-title">学情</h1>
        <div className="empty">
          <div className="big">·</div>
          还没有学习记录。回到「今日」开始第一组单词吧。
          <div style={{ marginTop: 14 }}>
            <button className="btn ghost" onClick={onGoSettings}>
              调整学习范围
            </button>
          </div>
        </div>
      </main>
    )
  }

  const maxBar = Math.max(1, ...stats.daily.map((d) => d.count))
  const acc = Math.round(stats.accuracy * 100)

  return (
    <main className="main stats-page">
      <header className="page-heading">
        <div className="page-kicker">YOUR LEARNING JOURNEY</div>
        <h1 className="page-title">学习报告</h1>
        <p className="page-sub">看看每天积累下来的进步。学习记录保存在本机，并按账号隔离。</p>
      </header>

      <div className="tiles stats-tiles">
        <div className="tile">
          <div className="k"><span className="dot" style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--warn)', display: 'inline-block' }} />连续打卡</div>
          <div className="v">{stats.streak}<small>天</small></div>
        </div>
        <div className="tile">
          <div className="k"><span className="dot" style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--primary)', display: 'inline-block' }} />今日学习</div>
          <div className="v">{stats.today}<small>词</small></div>
        </div>
        <div className="tile">
          <div className="k"><span className="dot" style={{ width: 7, height: 7, borderRadius: '50%', background: 'var(--ok)', display: 'inline-block' }} />累计正确率</div>
          <div className="v">{acc}<small>%</small></div>
        </div>
      </div>

      <section className="card stats-section trend-section">
        <div className="stats-section-heading"><div><span className="section-caption">ACTIVITY</span><h2>最近 14 天</h2></div><span className="stats-total">累计复习 {stats.totalReviews} 次</span></div>
        <div className="chart">
          {stats.daily.map((d) => (
            <div key={d.date} className={`col${d.count ? ' on' : ''}`} title={`${d.date} · ${d.count} 词`}>
              <i style={{ height: `${Math.max(3, (d.count / maxBar) * 100)}%` }} />
              <em>{d.date.slice(8)}</em>
            </div>
          ))}
        </div>
        <p className="muted stats-note" style={{ fontSize: 12.5, margin: '10px 0 0' }}>已建立 {stats.totalCards} 张记忆卡片</p>
      </section>

      <section className="card stats-section">
        <div className="stats-section-heading"><div><span className="section-caption">MASTERY</span><h2>词库进度</h2></div></div>
        <div className="row" style={{ marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
          <span className="tag green">已掌握 {stats.mastered}</span>
          <span className="tag blue">复习中 {stats.reviewing}</span>
          <span className="tag amber">学习中 {stats.learning}</span>
        </div>
        {settings.stages.map((s: Stage) => {
          const d = stats.byStage[s]
          if (!d) return null
          const pct = d.total ? Math.round((d.mastered / d.total) * 100) : 0
          return (
            <div key={s} style={{ marginBottom: 14 }}>
              <div className="row" style={{ marginBottom: 6 }}>
                <span style={{ fontSize: 13.5, fontWeight: 500 }}>{STAGE_LABEL[s]}</span>
                <span className="spacer" />
                <span className="muted" style={{ fontSize: 12.5 }}>
                  {d.mastered} / {d.total} · {pct}%
                </span>
              </div>
              <div className="bar tall">
                <i style={{ width: `${pct}%` }} />
              </div>
            </div>
          )
        })}
      </section>

      <section className="card stats-section">
        <div className="stats-section-heading"><div><span className="section-caption">FOCUS WORDS</span><h2>需要多练的词</h2></div><span className="stats-total">优先巩固薄弱项</span></div>
        {stats.hardWords.length === 0 ? (
          <p className="muted" style={{ fontSize: 13.5, margin: 0 }}>
            暂时没有错词，保持下去。
          </p>
        ) : (
          <div className="mlist">
            {stats.hardWords.map((c) => {
              const wd = findWord(c.w)
              return (
                <div className="it" key={c.w}>
                  <div className="grow">
                    <div className="name">{wd?.w ?? c.w}</div>
                    <div className="meta">{wd?.tr[0] ?? ''}</div>
                  </div>
                  <span className="tag amber">错 {c.wrong} 次</span>
                  <span className="tag">{STATE_LABEL[c.state]}</span>
                  <span className="muted" style={{ fontSize: 12.5, minWidth: 40, textAlign: 'right' }}>
                    {Math.round(c.retention * 100)}%
                  </span>
                </div>
              )
            })}
          </div>
        )}
      </section>
    </main>
  )
}
