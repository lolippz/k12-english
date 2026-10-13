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
    <div className="main">
      <h1>设置</h1>
      <p className="sub">调整学习范围与出题方式，改动即时生效并保存在本机</p>

      <div className="card">
        <div className="field">
          <label>学段</label>
          <div className="chips">
            {STAGES.map((s) => (
              <button key={s} className={`chip${settings.stages.includes(s) ? ' on' : ''}`} onClick={() => toggleStage(s)}>
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
              <button key={b} className={`chip${settings.books.includes(b) ? ' on' : ''}`} onClick={() => toggleBook(b)}>
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
      </div>

      <div className="card">
        <div className="field">
          <label>每日新词上限</label>
          <input
            className="num"
            type="number"
            min={0}
            max={200}
            value={settings.newPerDay}
            onChange={(e) => onChange({ newPerDay: Math.max(0, Number(e.target.value) || 0) })}
          />
          <div className="help">建议 15-25。词池越大越要控制节奏，否则复习量会滚雪球。</div>
        </div>
        <div className="field" style={{ marginBottom: 0 }}>
          <label>每日复习上限</label>
          <input
            className="num"
            type="number"
            min={10}
            max={500}
            value={settings.reviewPerDay}
            onChange={(e) => onChange({ reviewPerDay: Math.max(10, Number(e.target.value) || 10) })}
          />
          <div className="help">单次学习会话里最多安排多少张到期卡片。</div>
        </div>
      </div>

      <div className="card">
        <div className="field">
          <label>出题模式</label>
          <div className="chips">
            {MODES.map((m) => (
              <button key={m.v} className={`chip${settings.mode === m.v ? ' on' : ''}`} onClick={() => onChange({ mode: m.v })}>
                {m.label}
              </button>
            ))}
          </div>
          <div className="help">{MODES.find((m) => m.v === settings.mode)?.desc}</div>
        </div>

        <div className="field">
          <label>发音口音</label>
          <div className="chips">
            <button className={`chip${settings.accent === 'us' ? ' on' : ''}`} onClick={() => onChange({ accent: 'us' })}>
              美式
            </button>
            <button className={`chip${settings.accent === 'uk' ? ' on' : ''}`} onClick={() => onChange({ accent: 'uk' })}>
              英式
            </button>
            <button
              className="chip"
              onClick={() => speak('Good morning. Let us learn English together.', { accent: settings.accent })}
            >
              试听
            </button>
          </div>
          <div className="help">
            {voices.length ? `本机可用英语语音 ${voices.length} 个` : '尚未加载到英语语音包，可在系统设置里安装英语语音'}
          </div>
        </div>

        <div className="field" style={{ marginBottom: 0 }}>
          <label>答题提示音</label>
          <div className="chips">
            <button className={`chip${settings.sound ? ' on' : ''}`} onClick={() => onChange({ sound: true })}>
              开启
            </button>
            <button className={`chip${!settings.sound ? ' on' : ''}`} onClick={() => onChange({ sound: false })}>
              关闭
            </button>
          </div>
        </div>
      </div>

      <div className="card">
        <h2>数据</h2>
        <p className="muted" style={{ fontSize: 13.5, marginTop: 0 }}>
          当前已建立 {cardCount} 张记忆卡片。所有数据保存在本机 SQLite 数据库
          （用户目录下的 <code>.k12-english/progress.db</code>），换电脑前请先导出。
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
      </div>
    </div>
  )
}
