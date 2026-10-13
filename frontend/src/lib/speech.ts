/**
 * 发音朗读 —— 使用浏览器内置 Web Speech API
 * 零依赖、离线可用（依赖系统已安装的语音包）。
 * 后续如需更高质量，可替换为 edge-tts 预生成的 mp3 资源。
 */

let voices: SpeechSynthesisVoice[] = []
let loaded = false

export function ttsAvailable(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
}

export function loadVoices(): Promise<SpeechSynthesisVoice[]> {
  if (!ttsAvailable()) return Promise.resolve([])
  if (loaded && voices.length) return Promise.resolve(voices)

  return new Promise((resolve) => {
    const read = () => {
      voices = window.speechSynthesis.getVoices().filter((v) => v.lang.toLowerCase().startsWith('en'))
      if (voices.length) {
        loaded = true
        resolve(voices)
      }
    }
    read()
    if (!voices.length) {
      window.speechSynthesis.onvoiceschanged = () => read()
      setTimeout(() => resolve(voices), 1200)
    }
  })
}

export function listEnglishVoices(): SpeechSynthesisVoice[] {
  return voices
}

function pick(accent: 'us' | 'uk'): SpeechSynthesisVoice | null {
  if (!voices.length) return null
  const want = accent === 'us' ? 'en-us' : 'en-gb'
  const exact = voices.find((v) => v.lang.toLowerCase().replace('_', '-') === want)
  if (exact) return exact
  // 退而求其次：任意美音 / 英音 / 任意英语
  const us = voices.find((v) => v.lang.toLowerCase().includes('us'))
  const uk = voices.find((v) => v.lang.toLowerCase().includes('gb'))
  return (accent === 'us' ? us || uk : uk || us) || voices[0]
}

export interface SpeakOptions {
  accent?: 'us' | 'uk'
  rate?: number
}

export function speak(text: string, opts: SpeakOptions = {}): void {
  if (!ttsAvailable() || !text) return
  const { accent = 'us', rate = 0.9 } = opts
  window.speechSynthesis.cancel()
  const u = new SpeechSynthesisUtterance(text)
  const v = pick(accent)
  if (v) {
    u.voice = v
    u.lang = v.lang
  } else {
    u.lang = accent === 'us' ? 'en-US' : 'en-GB'
  }
  u.rate = rate
  u.pitch = 1
  window.speechSynthesis.speak(u)
}

export function stopSpeaking(): void {
  if (ttsAvailable()) window.speechSynthesis.cancel()
}

/* ---------------- 预生成音频播放 ---------------- */

let currentAudio: HTMLAudioElement | null = null

export function stopAudio(): void {
  if (currentAudio) {
    try {
      currentAudio.pause()
      currentAudio.currentTime = 0
    } catch {
      /* 忽略 */
    }
    currentAudio = null
  }
}

export function stopAll(): void {
  stopSpeaking()
  stopAudio()
}

/**
 * 播放预生成的 mp3；文件缺失或解码失败时自动退回系统 TTS。
 * @param url  音频地址，为空表示没有预生成音频
 * @param text 回退朗读的文本
 */
export function playAudio(url: string | null, text: string, opts: SpeakOptions = {}): void {
  stopAll()
  if (!url) {
    speak(text, opts)
    return
  }
  let fellBack = false
  const fallback = () => {
    if (fellBack) return
    fellBack = true
    speak(text, opts)
  }
  try {
    const a = new Audio(url)
    a.onerror = fallback
    const p = a.play()
    if (p && typeof p.catch === 'function') p.catch(fallback)
    currentAudio = a
  } catch {
    fallback()
  }
}

/** 词条发音：优先用随包分发的 mp3，英式口音或音频缺失时用系统 TTS */
export function playWord(slug: string | undefined, word: string, accent: 'us' | 'uk' = 'us'): void {
  const url = slug && accent === 'us' ? `${import.meta.env.BASE_URL}audio/${slug}.mp3` : null
  playAudio(url, word, { accent })
}

/** 例句只有系统 TTS，不预生成音频 */
export function playSentence(text: string, accent: 'us' | 'uk' = 'us'): void {
  playAudio(null, text, { accent, rate: 0.88 })
}

/** 播放一段提示音（答对 / 答错），不依赖任何音频文件 */
export function beep(kind: 'ok' | 'no'): void {
  try {
    const Ctx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext
    const ctx = new Ctx()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.type = 'sine'
    if (kind === 'ok') {
      osc.frequency.setValueAtTime(660, ctx.currentTime)
      osc.frequency.setValueAtTime(990, ctx.currentTime + 0.08)
    } else {
      osc.frequency.setValueAtTime(300, ctx.currentTime)
      osc.frequency.setValueAtTime(200, ctx.currentTime + 0.1)
    }
    gain.gain.setValueAtTime(0.06, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.22)
    osc.start()
    osc.stop(ctx.currentTime + 0.24)
    setTimeout(() => ctx.close(), 400)
  } catch {
    /* 忽略：音频不可用不影响主流程 */
  }
}
