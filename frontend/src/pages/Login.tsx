import { useState } from 'react'
import { api, type UserInfo } from '../lib/api'

interface Props {
  /** 库里是否已有用户：没有则注册免邀请码且自动成为管理员 */
  hasUsers: boolean
  onAuthed: (u: UserInfo) => void
}

/**
 * 登录 / 注册页。
 *
 * 后端规则（AuthService）：
 * - 首个注册的用户自动获得 admin 角色，且无需邀请码；
 * - 之后的注册必须携带有效邀请码。
 * 页面据此在两种模式间切换提示。
 */
export default function Login({ hasUsers, onAuthed }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>(hasUsers ? 'login' : 'register')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [invite, setInvite] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (busy) return
    setBusy(true)
    setError('')
    try {
      const name = username.trim()
      const u =
        mode === 'login'
          ? await api.login(name, password)
          : await api.register(name, password, invite.trim())
      onAuthed(u)
    } catch (err) {
      setError((err as Error).message || '操作失败，请重试')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="main">
      <div className="auth-wrap">
        <div className="brand" style={{ fontSize: 22, marginBottom: 4 }}>
          英语学习
        </div>
        <p className="sub" style={{ marginBottom: 20 }}>
          {hasUsers ? '登录后继续学习，进度按账号分别保存' : '首次使用：注册即成为管理员，无需邀请码'}
        </p>

        {hasUsers && (
          <div className="auth-toggle">
            <button className={mode === 'login' ? 'on' : ''} onClick={() => { setMode('login'); setError('') }}>
              登录
            </button>
            <button className={mode === 'register' ? 'on' : ''} onClick={() => { setMode('register'); setError('') }}>
              注册
            </button>
          </div>
        )}

        <form className="card" onSubmit={submit}>
          <div className="form-row">
            <label>用户名</label>
            <input
              className="text-input"
              value={username}
              autoComplete="username"
              onChange={(e) => setUsername(e.target.value)}
              placeholder="2–20 位，可用中英文、数字"
            />
          </div>
          <div className="form-row">
            <label>密码</label>
            <input
              className="text-input"
              type="password"
              value={password}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === 'register' ? '至少 6 位' : '请输入密码'}
            />
          </div>
          {mode === 'register' && hasUsers && (
            <div className="form-row">
              <label>邀请码</label>
              <input
                className="text-input"
                value={invite}
                onChange={(e) => setInvite(e.target.value.toUpperCase())}
                placeholder="向管理员索取"
              />
            </div>
          )}

          {error && <p className="err">{error}</p>}

          <button className="btn primary lg" style={{ width: '100%', marginTop: 14 }} disabled={busy || !username.trim() || !password}>
            {busy ? '请稍候…' : mode === 'login' ? '登录' : '注册并开始'}
          </button>
        </form>
      </div>
    </div>
  )
}
