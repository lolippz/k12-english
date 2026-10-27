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

function Ring({ pct, center, label }: { pct: number; center: string; label: string }) {
  const r = 68
  const c = 2 * Math.PI * r
  const off = c * (1 - Math.min(100, Math.max(0, pct)) / 100)
  return (
    <div className="ring-wrap hero-ring" role="img" aria-label={`今日目标完成 ${pct}%`}>
      <svg width="152" height="152" viewBox="0 0 152 152" aria-hidden="true">
        <circle className="ring-track" cx="76" cy="76" r={r} fill="none" strokeWidth="10" />
        <circle className="ring-bar" cx="76" cy="76" r={r} fill="none" strokeWidth="10" strokeLinecap="round" strokeDasharray={c} strokeDashoffset={off} />
      </svg>
      <div className="ring-center"><b>{center}</b><span>{label}</span></div>
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

  useEffect(() => { void load() }, [load, cardCount, settings])

  const today = useMemo(() => {
    const d = new Date()
    return `${d.getMonth() + 1} 月 ${d.getDate()} 日 · ${WEEKDAYS[d.getDay()]}`
  }, [])

  const week = useMemo(() => {
    if (!stats) return []
    return stats.daily.slice(-7).map((d, i, arr) => ({ ...d, on: i === arr.length - 1 }))
  }, [stats])

  if (error) return (
    <main className="main dashboard-main">
      <h1 className="page-title">学习驾驶舱</h1>
      <div className="empty"><div className="big">!</div><p className="sub">读取学情失败：{error}</p><button className="btn" onClick={load}>重试</button></div>
    </main>
  )

  if (!stats || !queue) return <main className="main dashboard-main"><div className="dashboard-loading"><span className="loading-orb" />正在整理你的学习数据…</div></main>

  const target = queue.reviews.length + queue.fresh.length
  const plan = stats.today + target
  const pct = plan ? Math.round((stats.today / plan) * 100) : 0
  const weekMax = Math.max(1, ...week.map((d) => d.count))
  const totalCards = Math.max(1, stats.totalCards)
  const acc = Math.round(stats.accuracy * 100)
  const stages = settings.stages.map((s) => STAGE_LABEL[s]).join(' / ')

  return (
    <main className="main dashboard-main">
      <header className="dashboard-head dashboard-enter">
        <div>
          <div className="dashboard-eyebrow">K12 ENGLISH <span>·</span> 学习概览</div>
          <h1>{greeting()}{username ? `，${username}` : ''}</h1>
          <p>循序渐进，今天也比昨天更进一步。</p>
        </div>
        <div className="dashboard-date"><span className="date-mark" aria-hidden="true">◷</span><span>{today}</span></div>
      </header>

      <section className="today-hero dashboard-enter" aria-labelledby="today-title">
        <div className="today-copy">
          <div className="hero-kicker"><span className="live-dot" /> 今日学习计划</div>
          <h2 id="today-title">{target ? <>把今天的 <strong>{target}</strong> 个词，<br className="desktop-break" />学扎实。</> : <>今天的学习目标<br className="desktop-break" />已经完成。</>}</h2>
          <p>{target ? `包含 ${queue.reviews.length} 个复习词和 ${queue.fresh.length} 个新词，专注一会儿就能完成。` : '很棒，今日没有待完成任务。明天继续保持这个节奏。'}</p>
          <div className="today-progress-meta"><span>今日进度</span><b>{stats.today} / {plan} 词</b></div>
          <div className="today-progress-track" role="progressbar" aria-label="今日学习进度" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}><i style={{ width: `${pct}%` }} /></div>
          <button className="today-cta" onClick={onStart} disabled={target === 0}>
            {target ? '开始今天的学习' : '今日已完成'}<span aria-hidden="true">↗</span>
          </button>
        </div>
        <div className="today-visual">
          <div className="hero-orbit orbit-one" /><div className="hero-orbit orbit-two" />
          <Ring pct={pct} center={`${pct}%`} label="今日完成" />
          <div className="hero-quote">Small steps,<br /><b>every day.</b></div>
        </div>
      </section>

      <section className="dashboard-metrics" aria-label="学习关键数据">
        <article className="metric-card dashboard-enter" style={{ animationDelay: '70ms' }}>
          <div className="metric-top"><span className="metric-icon streak-icon">✦</span><span className="metric-label">连续学习</span></div>
          <div className="metric-value">{stats.streak}<small>天</small></div><div className="metric-foot">保持你的学习节奏</div>
        </article>
        <article className="metric-card dashboard-enter" style={{ animationDelay: '130ms' }}>
          <div className="metric-top"><span className="metric-icon accuracy-icon">◎</span><span className="metric-label">累计正确率</span></div>
          <div className="metric-value">{acc}<small>%</small></div><div className="metric-foot">每一次练习都在进步</div>
        </article>
        <article className="metric-card dashboard-enter" style={{ animationDelay: '190ms' }}>
          <div className="metric-top"><span className="metric-icon mastered-icon">✓</span><span className="metric-label">已掌握</span></div>
          <div className="metric-value">{stats.mastered}<small>词</small></div><div className="metric-foot">进入长期记忆的词汇</div>
        </article>
        <article className="metric-card dashboard-enter" style={{ animationDelay: '250ms' }}>
          <div className="metric-top"><span className="metric-icon due-icon">↻</span><span className="metric-label">待复习</span></div>
          <div className="metric-value">{stats.dueCount}<small>词</small></div><div className="metric-foot">复习让记忆更牢固</div>
        </article>
      </section>

      <section className="dashboard-panels">
        <article className="dash-card trend-card dashboard-enter" style={{ animationDelay: '300ms' }}>
          <div className="panel-heading"><div><div className="panel-overline">YOUR ACTIVITY</div><h2>学习趋势</h2></div><span className="panel-caption">最近 7 天</span></div>
          <div className="dash-chart" role="img" aria-label="最近七天每日学习词数">
            {week.map((d, i) => (
              <div className={`col${d.on ? ' on' : ''}`} key={d.date} title={`${d.date} · ${d.count} 词`}>
                <b>{d.count || ''}</b><i style={{ height: `${Math.max(5, (d.count / weekMax) * 100)}%`, animationDelay: `${i * 55}ms` }} /><em>{d.date.slice(5).replace('-', '/')}</em>
              </div>
            ))}
          </div>
          <div className="chart-footer"><span><i className="chart-legend-dot" />每日完成词数</span><span>累计复习 {stats.totalReviews} 次</span></div>
        </article>

        <article className="dash-card mastery-card dashboard-enter" style={{ animationDelay: '360ms' }}>
          <div className="panel-heading"><div><div className="panel-overline">VOCABULARY STATUS</div><h2>词汇掌握情况</h2></div><span className="mastery-total">{stats.totalCards}<small> 词</small></span></div>
          <div className="mastery-stack" aria-label="词汇掌握分布">
            <i className="mastered-segment" style={{ width: `${(stats.mastered / totalCards) * 100}%` }} />
            <i className="reviewing-segment" style={{ width: `${(stats.reviewing / totalCards) * 100}%` }} />
            <i className="learning-segment" style={{ width: `${(stats.learning / totalCards) * 100}%` }} />
          </div>
          <div className="mastery-list">
            <div className="mastery-item"><span className="mastery-key"><i className="mastery-dot mastered-dot" />已掌握</span><b>{stats.mastered}<small>词</small></b><span className="mastery-pct">{Math.round((stats.mastered / totalCards) * 100)}%</span></div>
            <div className="mastery-item"><span className="mastery-key"><i className="mastery-dot reviewing-dot" />复习中</span><b>{stats.reviewing}<small>词</small></b><span className="mastery-pct">{Math.round((stats.reviewing / totalCards) * 100)}%</span></div>
            <div className="mastery-item"><span className="mastery-key"><i className="mastery-dot learning-dot" />学习中</span><b>{stats.learning}<small>词</small></b><span className="mastery-pct">{Math.round((stats.learning / totalCards) * 100)}%</span></div>
          </div>
        </article>
      </section>

      <section className="scope-card dashboard-enter" style={{ animationDelay: '420ms' }}>
        <div className="scope-symbol" aria-hidden="true">A</div>
        <div className="scope-copy"><div className="scope-title">你的学习范围</div><div className="scope-detail">{stages || '尚未选择学段'}{settings.books.length ? ` · 已限定 ${settings.books.length} 个册次` : ' · 全部册次'}</div></div>
        <div className="scope-remaining"><b>{stats.remainingNew}</b><span>个新词待解锁</span></div>
        <button className="scope-edit" onClick={onGoSettings}>管理范围<span aria-hidden="true">›</span></button>
      </section>
    </main>
  )
}
