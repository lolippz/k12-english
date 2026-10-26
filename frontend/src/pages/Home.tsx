import { useCallback, useEffect, useMemo, useState } from 'react'
import { STAGE_LABEL } from '../data'
import type { Settings } from '../lib/types'
import { api, type QueueResult, type StatsResult } from '../lib/api'

interface Props {
  settings: Settings
  cardCount: number
  username?: string
  onStart: () => void
  onGoSettings: () => void
}

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function greeting(): string {
  const h = new Date().getHours()
  if (h < 5) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 13) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
}

/** SVG 进度环：周长固定，用 dashoffset 表现百分比。 */
function Ring({ pct, center, label }: { pct: number; center: string; label: string }) {
  const r = 78
  const c = 2 * Math.PI * r
  const off = c * (1 - Math.min(100, Math.max(0, pct)) / 100)
  return (
    <div className="ring-wrap">
      <svg width="172" height="172" viewBox="0 0 172 172">
        <circle className="ring-track" cx="86" cy="86" r={r} fill="none" strokeWidth="13" />
        <circle
          className="ring-bar"
          cx="86" cy="86" r={r} fill="none" strokeWidth="13" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={off}
        />
      </svg>
      <div className="ring-center">
        <b>{center}</b>
        <span>{label}</span>
      </div>
    </div>
  )
}

export default function Home({ settings, cardCount, username, onStart, onGoSettings }: Props) {
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

  // 每次回到主页都重新取一次：刚学完一轮回来时数字必须是最新的
  useEffect(() => {
    void load()
  }, [load, cardCount, settings])

  const today = useMemo(() => {
    const d = new Date()
    return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · ${WEEKDAYS[d.getDay()]}`
  }, [])

  // 近 7 天柱状图数据
  const week = useMemo(() => {
    if (!stats) return []
    return stats.daily.slice(-7).map((d, i, arr) => ({
      date: d.date,
      count: d.count,
      correct: d.correct,
      on: i === arr.length - 1,
    }))
  }, [stats])

  if (error) {
    return (
      <div className="main">
        <h1>驾驶舱</h1>
        <div className="empty">
          <div className="big">!</div>
          <p className="sub">读取学情失败：{error}</p>
          <button className="btn" onClick={load}>重试</button>
        </div>
      </div>
    )
  }

  if (!stats || !queue) {
    return (
      <div className="main">
        <div className="empty">
          <div className="big">·</div>
          正在统计数据…
        </div>
      </div>
    )
  }

  const target = queue.reviews.length + queue.fresh.length
  const plan = stats.today + target
  const pct = plan ? Math.round((stats.today / plan) * 100) : 0
  const weekMax = Math.max(1, ...week.map((d) => d.count))
  const totalCards = Math.max(1, stats.totalCards)
  const acc = Math.round(stats.accuracy * 100)

  return (
    <div className="main">
      {/* 问候区 */}
      <div className="greet">
        <h1>
          {greeting()}
          {username ? `，${username}` : ''}
        </h1>
        <div className="date">{today}</div>
      </div>

      {/* 主行：进度环 + 关键指标 */}
      <div className="dash-grid">
        <div className="dash-card">
          <h2>今日进度</h2>
          <div className="row" style={{ gap: 24, flexWrap: 'wrap' }}>
            <Ring pct={pct} center={`${stats.today}`} label={`已学 / 计划 ${plan}`} />
            <div style={{ flex: 1, minWidth: 180 }}>
              <div className="dist-row" style={{ marginTop: 0 }}>
                <span className="lbl">复习</span>
                <div className="bar">
                  <i style={{ width: `${target ? (queue.reviews.length / target) * 100 : 0}%` }} />
                </div>
                <span className="num">{queue.reviews.length}</span>
              </div>
              <div className="dist-row">
                <span className="lbl">新词</span>
                <div className="bar">
                  <i style={{ width: `${target ? (queue.fresh.length / target) * 100 : 0}%` }} />
                </div>
                <span className="num">{queue.fresh.length}</span>
              </div>
              <button
                className="btn primary lg"
                style={{ width: '100%', marginTop: 14 }}
                onClick={onStart}
                disabled={target === 0}
              >
                {target === 0 ? '今日已完成 ✓' : `开始学习 · ${target} 词`}
              </button>
            </div>
          </div>
        </div>

        <div className="mini-grid">
          <div className="mini">
            <div className="k"><span className="dot" style={{ background: 'var(--warn)' }} />连续学习</div>
            <div className="v">{stats.streak}<small>天</small></div>
          </div>
          <div className="mini">
            <div className="k"><span className="dot" style={{ background: 'var(--primary)' }} />累计正确率</div>
            <div className="v">{acc}<small>%</small></div>
          </div>
          <div className="mini">
            <div className="k"><span className="dot" style={{ background: 'var(--ok)' }} />已掌握</div>
            <div className="v">{stats.mastered}<small>词</small></div>
          </div>
          <div className="mini">
            <div className="k"><span className="dot" style={{ background: 'var(--bad)' }} />待复习</div>
            <div className="v">{stats.dueCount}<small>词</small></div>
          </div>
        </div>
      </div>

      {/* 近 7 天 */}
      <div className="dash-card">
        <h2>近 7 天</h2>
        <div className="dash-chart">
          {week.map((d) => (
            <div className={`col${d.on ? ' on' : ''}`} key={d.date}>
              <b>{d.count || ''}</b>
              <i style={{ height: `${(d.count / weekMax) * 100}%` }} />
              <em>{d.date.slice(5).replace('-', '/')}</em>
            </div>
          ))}
        </div>
      </div>

      {/* 掌握分布 */}
      <div className="dash-card">
        <h2>掌握分布 · 共 {stats.totalCards} 词</h2>
        <div className="dist-row" style={{ marginTop: 4 }}>
          <span className="lbl">已掌握</span>
          <div className="bar"><i style={{ width: `${(stats.mastered / totalCards) * 100}%`, background: 'var(--ok)' }} /></div>
          <span className="num">{stats.mastered}</span>
        </div>
        <div className="dist-row">
          <span className="lbl">复习中</span>
          <div className="bar"><i style={{ width: `${(stats.reviewing / totalCards) * 100}%`, background: 'var(--primary)' }} /></div>
          <span className="num">{stats.reviewing}</span>
        </div>
        <div className="dist-row" style={{ marginBottom: 4 }}>
          <span className="lbl">学习中</span>
          <div className="bar"><i style={{ width: `${(stats.learning / totalCards) * 100}%`, background: 'var(--warn)' }} /></div>
          <span className="num">{stats.learning}</span>
        </div>
      </div>

      {/* 学习范围（精简） */}
      <div className="card">
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <span className="muted" style={{ fontSize: 13.5 }}>
            词池 {stats.poolSize} 词 · {settings.stages.map((s) => STAGE_LABEL[s]).join(' / ')}
            {settings.books.length ? ` · 已限定 ${settings.books.length} 个册次` : ''}
          </span>
          <span className="spacer" />
          <span className="muted" style={{ fontSize: 13.5 }}>
            还有 <b style={{ color: 'var(--text)' }}>{stats.remainingNew}</b> 个新词待解锁
          </span>
          <button className="btn ghost" style={{ padding: '5px 12px', fontSize: 13 }} onClick={onGoSettings}>
            调整
          </button>
        </div>
      </div>
    </div>
  )
}
