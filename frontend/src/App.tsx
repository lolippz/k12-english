import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from './lib/api'
import { newCard, review as applyReview, type CardRecord, type Grade } from './lib/srs'
import { DEFAULT_SETTINGS, type LogEntry, type Settings } from './lib/types'
import { loadVoices, stopAll } from './lib/speech'
import Home from './pages/Home'
import Study from './pages/Study'
import Stats from './pages/Stats'
import SettingsPage from './pages/Settings'

export type Tab = 'home' | 'stats' | 'settings'

export interface SessionState {
  queue: string[]
  idx: number
  total: number
  done: number
  correct: number
}

export default function App() {
  const [ready, setReady] = useState(false)
  const [bootError, setBootError] = useState('')
  const [tab, setTab] = useState<Tab>('home')
  const [settings, setSettings] = useState<Settings>(DEFAULT_SETTINGS)
  const [cards, setCards] = useState<Map<string, CardRecord>>(new Map())
  const [session, setSession] = useState<SessionState | null>(null)
  const [toast, setToast] = useState('')

  /* ---------- 初始化：向后端要一次本机状态 ---------- */
  useEffect(() => {
    ;(async () => {
      const st = await api.state()
      setSettings({ ...DEFAULT_SETTINGS, ...st.settings })
      setCards(new Map(st.cards.map((c) => [c.w, c])))
      loadVoices()
    })()
      .catch((e: Error) => setBootError(e.message))
      .finally(() => setReady(true))
  }, [])

  const flash = useCallback((msg: string) => {
    setToast(msg)
    setTimeout(() => setToast(''), 1800)
  }, [])

  /* ---------- 设置 ---------- */
  const updateSettings = useCallback((patch: Partial<Settings>) => {
    setSettings((prev) => {
      const next = { ...prev, ...patch }
      // 先改本地让 UI 立刻响应，落库失败再回滚提示
      api.saveSettings(next).catch((e: Error) => flash(`设置保存失败：${e.message}`))
      return next
    })
  }, [flash])

  /* ---------- 学习会话 ---------- */
  const startSession = useCallback(async () => {
    try {
      const q = await api.queue()
      const queue = [...q.reviews, ...q.fresh]
      if (!queue.length) {
        flash('今日没有需要学习的词，去设置里调整一下范围吧')
        return
      }
      setSession({ queue, idx: 0, total: queue.length, done: 0, correct: 0 })
      setTab('home')
    } catch (e) {
      flash(`无法开始学习：${(e as Error).message}`)
    }
  }, [flash])

  const exitSession = useCallback(() => {
    stopAll()
    setSession(null)
  }, [])

  const handleGrade = useCallback(
    (word: string, grade: Grade, correct: boolean, mode: string) => {
      const key = word.toLowerCase()
      const prev = cards.get(key) ?? newCard(key)
      const next = applyReview(prev, grade)
      const log: LogEntry = { w: key, ts: Date.now(), grade, mode: mode as LogEntry['mode'], correct }

      // 本地先更新，界面不等网络
      setCards((m) => {
        const n = new Map(m)
        n.set(key, next)
        return n
      })
      setSession((s) => (s ? { ...s, idx: s.idx + 1, done: s.done + 1, correct: s.correct + (correct ? 1 : 0) } : s))

      api.review(next, log).catch((e: Error) => flash(`作答未保存：${e.message}`))
    },
    [cards, flash],
  )

  /* ---------- 数据管理 ---------- */
  const exportData = useCallback(async () => {
    try {
      const payload = await api.exportAll()
      const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `english-backup-${new Date().toISOString().slice(0, 10)}.json`
      a.click()
      URL.revokeObjectURL(url)
      flash('已导出备份文件')
    } catch (e) {
      flash(`导出失败：${(e as Error).message}`)
    }
  }, [flash])

  const importData = useCallback(
    async (file: File) => {
      try {
        const data = JSON.parse(await file.text()) as unknown
        const after = await api.importAll(data)
        setCards(new Map(after.cards.map((c) => [c.w, c])))
        setSettings({ ...DEFAULT_SETTINGS, ...after.settings })
        flash(`已导入 ${after.cards.length} 条学习记录`)
      } catch (e) {
        flash(`导入失败：${(e as Error).message}`)
      }
    },
    [flash],
  )

  const resetAll = useCallback(async () => {
    try {
      await api.reset()
      setCards(new Map())
      setSession(null)
      flash('学习记录已清空')
    } catch (e) {
      flash(`清空失败：${(e as Error).message}`)
    }
  }, [flash])

  const currentWord = useMemo(() => {
    if (!session) return null
    return session.queue[session.idx] ?? null
  }, [session])

  if (!ready) {
    return (
      <div className="empty">
        <div className="big">·</div>
        正在连接本机服务…
      </div>
    )
  }

  if (bootError) {
    return (
      <div className="main">
        <div className="empty">
          <div className="big">!</div>
          <h1>连不上后端服务</h1>
          <p className="sub" style={{ marginTop: 10 }}>
            {bootError}
          </p>
          <p className="muted" style={{ fontSize: 13 }}>
            请确认 k12-english 服务已启动，然后刷新本页。
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="shell">
      <div className="topbar">
        <div className="brand">
          英语学习<small>K12 词库 · 单机版</small>
        </div>
        <div className="nav">
          <button className={tab === 'home' && !session ? 'on' : ''} onClick={() => { exitSession(); setTab('home') }}>
            今日
          </button>
          <button className={tab === 'stats' ? 'on' : ''} onClick={() => { exitSession(); setTab('stats') }}>
            学情
          </button>
          <button className={tab === 'settings' ? 'on' : ''} onClick={() => { exitSession(); setTab('settings') }}>
            设置
          </button>
        </div>
      </div>

      {session && currentWord ? (
        <Study
          key={session.idx}
          word={currentWord}
          card={cards.get(currentWord.toLowerCase()) ?? null}
          session={session}
          settings={settings}
          onGrade={handleGrade}
          onExit={exitSession}
        />
      ) : session ? (
        <div className="main">
          <div className="empty">
            <div className="big">✓</div>
            <h1>本轮完成</h1>
            <p className="sub" style={{ marginTop: 10 }}>
              共 {session.done} 题，答对 {session.correct} 题，正确率{' '}
              {session.done ? Math.round((session.correct / session.done) * 100) : 0}%
            </p>
            <button className="btn primary lg" onClick={exitSession}>
              回到今日
            </button>
          </div>
        </div>
      ) : (
        <>
          {tab === 'home' && (
            <Home
              settings={settings}
              cardCount={cards.size}
              onStart={startSession}
              onGoSettings={() => setTab('settings')}
            />
          )}
          {tab === 'stats' && <Stats settings={settings} onGoSettings={() => setTab('settings')} />}
          {tab === 'settings' && (
            <SettingsPage
              settings={settings}
              cardCount={cards.size}
              onChange={updateSettings}
              onExport={exportData}
              onImport={importData}
              onReset={resetAll}
            />
          )}
        </>
      )}

      {toast && (
        <div
          style={{
            position: 'fixed',
            bottom: 24,
            left: '50%',
            transform: 'translateX(-50%)',
            background: 'var(--text)',
            color: '#fff',
            padding: '10px 20px',
            borderRadius: 10,
            fontSize: 14,
            zIndex: 50,
          }}
        >
          {toast}
        </div>
      )}
    </div>
  )
}
