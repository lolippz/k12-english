import { useMemo, useRef } from 'react'
import { STAGES, STAGE_LABEL, STAGE_DESC, booksOfStages, wordsOfStages, type Stage } from '../data'
import type { Settings, StudyMode } from '../lib/types'
import { listEnglishVoices, speak } from '../lib/speech'

interface Props {
  settings: Settings
  /** 已建立的记忆卡片数量 */
  cardCount: number
  onChange: (patch: Partial<Settings>) => void
  onExport: () => void
  onImport: (f: File) => void
  onReset: () => void
}

const MODES: { v: StudyMode; label: string; desc: string }[] = [
  { v: 'mix', label: '混合', desc: '每张卡随机出一种题型' },
  { v: 'spell', label: '拼写', desc: '看中文与音标，手打英文' },
  { v: 'listen', label: '听音选义', desc: '听发音，选中文意思' },
  { v: 'meaning', label: '看义选词', desc: '看中文，选英文单词' },
]

export default function SettingsPage({ settings, cardCount, onChange, onExport, onImport, onReset }: Props) {
  const fileRef = useRef<HTMLInputElement>(null)
  const books = useMemo(() => booksOfStages(settings.stages), [settings.stages])
  const poolCount = useMemo(() => wordsOfStages(settings.stages).length, [settings.stages])
  const voices = listEnglishVoices()

  const toggleStage = (s: Stage) => {
    const has = settings.stages.includes(s)
    const next = has ? settings.stages.filter((x) => x !== s) : [...settings.stages, s]
    // 学段变化后，清掉不在新范围里的册次
    const valid = new Set(booksOfStages(next))
    onChange({ stages: next, books: settings.books.filter((b) => valid.has(b)) })
  }

  const toggleBook = (b: string) => {
    const has = settings.books.includes(b)
    onChange({ books: has ? settings.books.filter((x) => x !== b) : [...settings.books, b] })
  }

  return (
    <main className="main settings-page">
      <header className="page-heading">
        <div className="page-kicker">PERSONALIZE YOUR STUDY</div>
        <h1 className="page-title">学习设置</h1>
        <p className="page-sub">按你的教材和节奏调整，修改会自动保存。</p>
      </header>

      <section className="card settings-section settings-range">
        <div className="settings-section-heading"><div><span className="section-index">01</span><h2>学习范围</h2></div><span className="section-caption">CURRICULUM</span></div>
        <div className="field">
          <label>学段</label>
          <div className="chips">
            {STAGES.map((s) => (
              <button key={s} aria-pressed={settings.stages.includes(s)} className={`chip${settings.stages.includes(s) ? ' on' : ''}`} onClick={() => toggleStage(s)}>
                {STAGE_LABEL[s]}
              </button>
            ))}
          </div>
          <div className="help">
            当前词池 {poolCount} 词
            {settings.stages.length > 0 && ' · ' + settings.stages.map((s) => STAGE_DESC[s]).join(' / ')}
          </div>
        </div>

        <div className="field" style={{ marginBottom: 0 }}>
          <label>
            限定册次
            <span className="muted" style={{ fontWeight: 400, marginLeft: 8, fontSize: 12.5 }}>
              {settings.books.length ? `已选 ${settings.books.length} 个` : '未限定，使用全部册次'}
            </span>
          </label>
          <div className="chips scroll">
            {books.map((b) => (
              <button key={b} aria-pressed={settings.books.includes(b)} className={`chip${settings.books.includes(b) ? ' on' : ''}`} onClick={() => toggleBook(b)}>
                {b}
              </button>
            ))}
          </div>
          {settings.books.length > 0 && (
            <button className="btn ghost" style={{ marginTop: 10, padding: '5px 12px', fontSize: 13 }} onClick={() => onChange({ books: [] })}>
              清除册次筛选
            </button>
          )}
        </div>
      </section>

      <section className="card settings-section">
        <div className="settings-section-heading"><div><span className="section-index">02</span><h2>每日计划</h2></div><span className="section-caption">DAILY GOALS</span></div>
        <div className="listrow">
          <div className="lr-main">
            <div className="lr-title">每日新词上限</div>
            <div className="lr-desc">建议 15–25，词池越大越要控制节奏</div>
          </div>
          <input
            className="num"
            type="number"
            min={0}
            max={200}
            value={settings.newPerDay}
            onChange={(e) => onChange({ newPerDay: Math.max(0, Number(e.target.value) || 0) })}
          />
        </div>
        <div className="listrow">
          <div className="lr-main">
            <div className="lr-title">每日复习上限</div>
            <div className="lr-desc">单次会话最多安排的到期卡片数</div>
          </div>
          <input
            className="num"
            type="number"
            min={10}
            max={500}
            value={settings.reviewPerDay}
            onChange={(e) => onChange({ reviewPerDay: Math.max(10, Number(e.target.value) || 10) })}
          />
        </div>
      </section>

      <section className="card settings-section">
        <div className="settings-section-heading"><div><span className="section-index">03</span><h2>出题与发音</h2></div><span className="section-caption">PREFERENCES</span></div>
        <div className="field">
          <label>出题模式</label>
          <div className="chips">
            {MODES.map((m) => (
              <button key={m.v} aria-pressed={settings.mode === m.v} className={`chip mode-chip${settings.mode === m.v ? ' on' : ''}`} onClick={() => onChange({ mode: m.v })}>
                {m.label}
                {settings.mode === m.v && <span className="mode-check" aria-hidden="true">✓</span>}
              </button>
            ))}
          </div>
          <div className="help">{MODES.find((m) => m.v === settings.mode)?.desc}</div>
        </div>

        <div className="listrow">
          <div className="lr-main">
            <div className="lr-title">发音口音</div>
            <div className="lr-desc">
              {voices.length ? `本机可用英语语音 ${voices.length} 个` : '未加载到英语语音包，可在系统设置里安装'}
            </div>
          </div>
          <div className="segmented">
            <button className={settings.accent === 'us' ? 'on' : ''} onClick={() => onChange({ accent: 'us' })}>
              美式
            </button>
            <button className={settings.accent === 'uk' ? 'on' : ''} onClick={() => onChange({ accent: 'uk' })}>
              英式
            </button>
          </div>
          <button
            className="mini-btn"
            onClick={() => speak('Good morning. Let us learn English together.', { accent: settings.accent })}
          >
            试听
          </button>
        </div>

        <div className="listrow">
          <div className="lr-main">
            <div className="lr-title">答题提示音</div>
            <div className="lr-desc">答对 / 答错时播放提示音</div>
          </div>
          <button
            className={`switch${settings.sound ? ' on' : ''}`}
            aria-label="答题提示音"
            aria-checked={settings.sound}
            role="switch"
            onClick={() => onChange({ sound: !settings.sound })}
          />
        </div>
      </section>

      <section className="card settings-section data-section">
        <div className="settings-section-heading"><div><span className="section-index">04</span><h2>数据与备份</h2></div><span className="section-caption">YOUR DATA</span></div>
        <p className="muted" style={{ fontSize: 13.5, marginTop: 0 }}>
          当前已建立 {cardCount} 张记忆卡片。所有数据保存在本机 SQLite 数据库
          （用户目录下的 <code>.k12-english/progress.db</code>），按账号隔离，换电脑前请先导出。
        </p>
        <div className="row" style={{ flexWrap: 'wrap' }}>
          <button className="btn" onClick={onExport}>
            导出备份
          </button>
          <button className="btn" onClick={() => fileRef.current?.click()}>
            导入备份
          </button>
          <input
            ref={fileRef}
            type="file"
            accept="application/json"
            style={{ display: 'none' }}
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) onImport(f)
              e.target.value = ''
            }}
          />
          <span className="spacer" />
          <button
            className="btn ghost"
            style={{ color: 'var(--bad)' }}
            onClick={() => {
              if (confirm('确定清空全部学习记录？此操作不可撤销，建议先导出备份。')) onReset()
            }}
          >
            清空学习记录
          </button>
        </div>
      </section>
    </main>
  )
}
