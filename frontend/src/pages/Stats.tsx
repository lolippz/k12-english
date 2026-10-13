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
      <div className="main">
        <h1>学情</h1>
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

  if (!stats) {
    return (
      <div className="main">
        <h1>学情</h1>
        <div className="empty">
          <div className="big">·</div>
          正在统计…
        </div>
      </div>
    )
  }

  if (!stats.totalCards) {
    return (
      <div className="main">
        <h1>学情</h1>
        <div className="empty">
          <div className="big">·</div>
          还没有学习记录。回到「今日」开始第一组单词吧。
          <div style={{ marginTop: 14 }}>
            <button className="btn ghost" onClick={onGoSettings}>
              调整学习范围
            </button>
          </div>
        </div>
      </div>
    )
  }

  const maxBar = Math.max(1, ...stats.daily.map((d) => d.count))

  return (
    <div className="main">
      <h1>学情</h1>
      <p className="sub">数据保存在本机 SQLite 数据库，不上传任何服务器</p>

      <div className="grid3">
        <div className="stat">
          <div className="k">连续打卡</div>
          <div className="v">
            {stats.streak}
            <small>天</small>
          </div>
        </div>
        <div className="stat">
          <div className="k">今日学习</div>
          <div className="v">
            {stats.today}
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
        <h2>最近 14 天</h2>
        <div className="chart">
          {stats.daily.map((d) => (
            <div key={d.date} className={`col${d.count ? ' on' : ''}`} title={`${d.date} · ${d.count} 词`}>
              <i style={{ height: `${Math.max(3, (d.count / maxBar) * 100)}%` }} />
              <em>{d.date.slice(8)}</em>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h2>词库进度</h2>
        <div className="row" style={{ marginBottom: 12 }}>
          <span className="tag green">已掌握 {stats.mastered}</span>
          <span className="tag blue">复习中 {stats.reviewing}</span>
          <span className="tag amber">学习中 {stats.learning}</span>
          <span className="spacer" />
          <span className="muted" style={{ fontSize: 13 }}>
            共建立 {stats.totalCards} 张卡片
          </span>
        </div>
        {settings.stages.map((s: Stage) => {
          const d = stats.byStage[s]
          if (!d) return null
          const pct = d.total ? Math.round((d.mastered / d.total) * 100) : 0
          return (
            <div key={s} style={{ marginBottom: 12 }}>
              <div className="row" style={{ marginBottom: 5 }}>
                <span style={{ fontSize: 13.5 }}>{STAGE_LABEL[s]}</span>
                <span className="spacer" />
                <span className="muted" style={{ fontSize: 12.5 }}>
                  {d.mastered} / {d.total}
                </span>
              </div>
              <div className="bar">
                <i style={{ width: `${pct}%` }} />
              </div>
            </div>
          )
        })}
      </div>

      <div className="card">
        <h2>错得最多的词</h2>
        {stats.hardWords.length === 0 ? (
          <p className="muted" style={{ fontSize: 13.5, margin: 0 }}>
            暂时没有错词，保持下去。
          </p>
        ) : (
          <div className="list">
            {stats.hardWords.map((c) => {
              const wd = findWord(c.w)
              return (
                <div className="it" key={c.w}>
                  <span className="w">{wd?.w ?? c.w}</span>
                  <span className="t">{wd?.tr[0] ?? ''}</span>
                  <span className="tag amber">错 {c.wrong} 次</span>
                  <span className="tag">{STATE_LABEL[c.state]}</span>
                  <span className="muted" style={{ fontSize: 12, minWidth: 44, textAlign: 'right' }}>
                    {Math.round(c.retention * 100)}%
                  </span>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
