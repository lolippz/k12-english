import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { hydrateWords, type Word } from './data'
import './index.css'

const root = createRoot(document.getElementById('root')!)

/**
 * 先把词库从后端拉进内存再渲染。
 *
 * 这样页面里就能继续用 `findWord()` / `wordsOfStages()` 这些同步查询，
 * 不必把四个页面都改成 async；同时词库不被打进 JS，首包从 2.8 MB 降到 150 KB 左右。
 */
async function bootstrap() {
  const res = await fetch('/api/words')
  if (!res.ok) {
    throw new Error(`词库加载失败：${res.status} ${res.statusText}`)
  }
  const data = (await res.json()) as { words?: Word[] }
  hydrateWords(data.words ?? [])
  root.render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
}

function BootError({ message }: { message: string }) {
  return (
    <div className="main">
      <div className="empty">
        <div className="big">!</div>
        <h1>启动失败</h1>
        <p className="sub" style={{ marginTop: 10 }}>
          {message}
        </p>
        <p className="muted" style={{ fontSize: 13 }}>
          请确认 k12-english 服务已启动，然后刷新本页。
        </p>
      </div>
    </div>
  )
}

bootstrap().catch((e: Error) => {
  console.error(e)
  root.render(
    <StrictMode>
      <BootError message={e.message} />
    </StrictMode>,
  )
})
