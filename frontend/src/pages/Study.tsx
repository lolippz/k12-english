import { useEffect, useMemo, useRef, useState } from 'react'
import { findWord, wordsOfStages, STAGE_LABEL, type Word } from '../data'
import { Rating, previewIntervals, humanInterval, newCard, STATE_LABEL, type CardRecord, type Grade } from '../lib/srs'
import type { Settings, StudyMode } from '../lib/types'
import { beep, playSentence, playWord, stopAll } from '../lib/speech'
import type { SessionState } from '../App'

interface Props {
  word: string
  card: CardRecord | null
  session: SessionState
  settings: Settings
  onGrade: (word: string, grade: Grade, correct: boolean, mode: string) => void
  onExit: () => void
}

type ResolvedMode = Exclude<StudyMode, 'mix'>

function resolveMode(mode: StudyMode): ResolvedMode {
  if (mode !== 'mix') return mode
  const all: ResolvedMode[] = ['spell', 'listen', 'meaning']
  return all[Math.floor(Math.random() * all.length)]
}

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function buildChoices(word: Word, pool: Word[], byMeaning: boolean): string[] {
  const correct = byMeaning ? word.tr[0] : word.w
  const seen = new Set([correct])
  const opts: string[] = []
  const candidates = shuffle(pool)
  for (const c of candidates) {
    if (opts.length >= 3) break
    if (c.w === word.w) continue
    const v = byMeaning ? c.tr[0] : c.w
    if (!v || seen.has(v)) continue
    seen.add(v)
    opts.push(v)
  }
  return shuffle([correct, ...opts])
}

export default function Study({ word, card, session, settings, onGrade, onExit }: Props) {
  const w = useMemo(() => findWord(word), [word])
  const pool = useMemo(() => wordsOfStages(settings.stages), [settings.stages])
  const mode = useMemo<ResolvedMode>(() => resolveMode(settings.mode), [settings.mode])

  const [input, setInput] = useState('')
  const [picked, setPicked] = useState<string | null>(null)
  const [answered, setAnswered] = useState(false)
  const [correct, setCorrect] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  const choices = useMemo(
    () => (w && mode !== 'spell' ? buildChoices(w, pool, mode === 'listen') : []),
    [w, pool, mode],
  )

  const intervals = useMemo(() => previewIntervals(card ?? newCard(word)), [card, word])
  const cardState = card ? card.state : 0

  const progress = session.total ? Math.round((session.idx / session.total) * 100) : 0

  /* 听音模式：进入即朗读；切题或卸载时立刻停声，避免上一题的声音串到下一题 */
  useEffect(() => {
    if (!w) return
    let timer: ReturnType<typeof setTimeout> | undefined
    if (mode === 'listen') timer = setTimeout(() => playWord(w.a, w.w, settings.accent), 220)
    if (mode === 'spell') inputRef.current?.focus()
    return () => {
      if (timer) clearTimeout(timer)
      stopAll()
    }
  }, [w, mode, settings.accent])

  if (!w) {
    return (
      <div className="main">
        <div className="empty">
          <p>词条缺失：{word}</p>
          <button className="btn" onClick={onExit}>
            返回
          </button>
        </div>
      </div>
    )
  }

  const submitSpell = () => {
    if (answered) return
    const ok = input.trim().toLowerCase() === w.w.toLowerCase()
    setCorrect(ok)
    setAnswered(true)
    if (settings.sound) beep(ok ? 'ok' : 'no')
    playWord(w.a, w.w, settings.accent)
  }

  const pickChoice = (v: string) => {
    if (answered) return
    const correctVal = mode === 'listen' ? w.tr[0] : w.w
    const ok = v === correctVal
    setPicked(v)
    setCorrect(ok)
    setAnswered(true)
    if (settings.sound) beep(ok ? 'ok' : 'no')
    if (mode === 'listen') playWord(w.a, w.w, settings.accent)
  }

  const grade = (g: Grade) => {
    onGrade(w.w, g, correct, mode)
  }

  const gradeLabel: Record<number, string> = { 1: '忘了', 2: '模糊', 3: '记得', 4: '简单' }
  const gradeClass: Record<number, string> = { 1: 'g1', 2: 'g2', 3: 'g3', 4: 'g4' }

  return (
    <div className="main">
      <div className="study-top">
        <button className="btn ghost" onClick={onExit}>
          ← 退出
        </button>
        <div className="bar">
          <i style={{ width: `${progress}%` }} />
        </div>
        <span className="study-count">
          {session.idx + 1} / {session.total}
        </span>
      </div>

      <div className={`qcard${answered ? " answered" : ""}`}>
        {/* ---------- 题干 ---------- */}
        {mode === 'spell' && (
          <>
            <div className="qtrans">{w.tr[0]}</div>
            <div className="qphon">{w.ph || '—'}</div>
            <button className="speak-btn" onClick={() => playWord(w.a, w.w, settings.accent)}>
              播放发音
            </button>
            <input
              ref={inputRef}
              className={`answer-input${answered ? (correct ? ' ok' : ' no') : ''}`}
              value={input}
              placeholder="输入英文单词后回车"
              autoComplete="off"
              autoCorrect="off"
              spellCheck={false}
              disabled={answered}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') submitSpell()
              }}
            />
            {!answered && (
              <div className="qhint">
                按回车提交
              </div>
            )}
          </>
        )}

        {mode === 'listen' && (
          <>
            <div className="qword blank">● ● ● ●</div>
            <div className="qhint">听发音，选出正确的中文意思</div>
            <button className="speak-btn" onClick={() => playWord(w.a, w.w, settings.accent)}>
              再听一次
            </button>
          </>
        )}

        {mode === 'meaning' && (
          <>
            <div className="qtrans">{w.tr[0]}</div>
            <div className="qhint">选出对应的英文单词</div>
          </>
        )}

        {/* ---------- 选项 ---------- */}
        {mode !== 'spell' && (
          <div className="choices">
            {choices.map((c) => {
              const correctVal = mode === 'listen' ? w.tr[0] : w.w
              const isCorrect = c === correctVal
              const cls = answered && isCorrect ? 'ok' : answered && c === picked ? 'no' : ''
              return (
                <button key={c} className={`choice ${cls}`} disabled={answered} onClick={() => pickChoice(c)}>
                  {c}
                </button>
              )
            })}
          </div>
        )}

        {/* ---------- 答案与评分 ---------- */}
        {answered && (
          <div className="reveal">
            <div className="row" style={{ gap: 12, alignItems: 'baseline' }}>
              <span className="qword" style={{ fontSize: 30 }}>
                {w.w}
              </span>
              <span className="mono muted" style={{ fontSize: 14 }}>
                {w.ph}
              </span>
              <button className="btn ghost" style={{ padding: '3px 10px', fontSize: 13 }} onClick={() => playWord(w.a, w.w, settings.accent)}>
                朗读
              </button>
              {w.star ? <span className="stars">{'★'.repeat(w.star)}{'☆'.repeat(5 - w.star)}</span> : null}
            </div>

            <div className="tr" style={{ marginTop: 10 }}>
              {w.tr[0]}
            </div>
            {w.tr.length > 1 && <div className="tr-sub">{w.tr.slice(1).join('；')}</div>}

            {w.alt && (
              <div className="meta" style={{ marginTop: 10 }}>
                <span className="meta-key">{/\s/.test(w.alt) ? '注' : '别名'}</span>
                <span className="tag">{w.alt}</span>
              </div>
            )}

            {w.def && !w.ex && (
              <div className="def-line">
                <span className="def-key">英文释义</span>
                {w.def}
              </div>
            )}

            {w.ex && (
              <div className="ex-box">
                <div className="ex-en">{w.ex[0]}</div>
                <div className="ex-cn">{w.ex[1]}</div>
                <button
                  className="btn ghost"
                  style={{ padding: '2px 9px', fontSize: 12, marginTop: 6 }}
                  onClick={() => playSentence(w.ex![0], settings.accent)}
                >
                  朗读例句
                </button>
              </div>
            )}

            {w.forms && Object.keys(w.forms).length > 0 && (
              <div className="meta" style={{ marginTop: 14 }}>
                <span className="meta-key">词形</span>
                {Object.entries(w.forms).map(([k, v]) => (
                  <span key={k} className="tag">
                    {k} {v}
                  </span>
                ))}
              </div>
            )}

            <div className="meta">
              {w.stages.map((s) => (
                <span key={s} className="tag">
                  {STAGE_LABEL[s]}
                </span>
              ))}
              {w.oxford ? <span className="tag green">牛津3000</span> : null}
              {(w.tags ?? []).map((t) => (
                <span key={t} className="tag blue">
                  {t}
                </span>
              ))}
              {w.books.slice(0, 2).map((b) => (
                <span key={b} className="tag">
                  {b}
                </span>
              ))}
              {w.frq ? <span className="tag">词频 {w.frq}</span> : null}
            </div>

            <div className="grade-row">
              {[Rating.Again, Rating.Hard, Rating.Good, Rating.Easy].map((g) => (
                <button
                  key={g}
                  className={`grade ${gradeClass[g]}`}
                  onClick={() => grade(g as Grade)}
                  style={
                    (correct && g === Rating.Good) || (!correct && g === Rating.Again)
                      ? { borderColor: 'var(--primary)', background: 'var(--primary-bg)' }
                      : undefined
                  }
                >
                  <b>{gradeLabel[g]}</b>
                  <small>{humanInterval(intervals[String(g)] ?? 0)}</small>
                </button>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="row" style={{ marginTop: 14, justifyContent: 'center' }}>
        <span className="muted" style={{ fontSize: 13 }}>
          {card ? (
            <>
              {STATE_LABEL[cardState]} · 已复习 {card.reps} 次
              {card.wrong > 0 ? ` · 错过 ${card.wrong} 次` : ''}
            </>
          ) : (
            '新词 · 首次学习'
          )}
        </span>
      </div>
    </div>
  )
}
