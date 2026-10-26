import { useCallback, useEffect, useState } from 'react'
import { api, type AdminUser, type InviteInfo, type UserInfo } from '../lib/api'

interface Props {
  me: UserInfo
  flash: (msg: string) => void
}

function fmtTime(ts: number | null): string {
  if (!ts) return '—'
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * 管理员后台：账号管理与邀请码管理。
 * 对应后端 /api/admin/**，全部操作需要 admin 角色（后端 AuthFilter 会再校验一次）。
 */
export default function Admin({ me, flash }: Props) {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [invites, setInvites] = useState<InviteInfo[]>([])
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState('')

  const [newMaxUses, setNewMaxUses] = useState(1)
  const [newExpires, setNewExpires] = useState('')
  const [newNote, setNewNote] = useState('')
  const [creating, setCreating] = useState(false)

  const reload = useCallback(async () => {
    try {
      const [u, i] = await Promise.all([api.adminUsers(), api.adminInvites()])
      setUsers(u.users)
      setInvites(i.invites)
      setError('')
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setLoaded(true)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  /* ---------------- 用户操作 ---------------- */

  const toggleRole = async (u: AdminUser) => {
    const to = u.role === 'admin' ? 'user' : 'admin'
    const verb = to === 'admin' ? '设为管理员' : '降为普通用户'
    if (!confirm(`确定把「${u.username}」${verb}？`)) return
    try {
      await api.adminSetRole(u.id, to)
      flash(`已${verb}`)
      reload()
    } catch (e) {
      flash(`操作失败：${(e as Error).message}`)
    }
  }

  const resetPassword = async (u: AdminUser) => {
    const pwd = prompt(`为「${u.username}」设置新密码（至少 6 位）：`)
    if (pwd === null) return
    if (pwd.length < 6) {
      flash('密码至少 6 位')
      return
    }
    try {
      await api.adminResetPassword(u.id, pwd)
      flash('密码已重置')
    } catch (e) {
      flash(`重置失败：${(e as Error).message}`)
    }
  }

  const removeUser = async (u: AdminUser) => {
    if (!confirm(`确定删除用户「${u.username}」？其全部学习记录将一并清除，且不可恢复。`)) return
    try {
      await api.adminDeleteUser(u.id)
      flash(`已删除 ${u.username}`)
      reload()
    } catch (e) {
      flash(`删除失败：${(e as Error).message}`)
    }
  }

  /* ---------------- 邀请码操作 ---------------- */

  const createInvite = async () => {
    if (creating) return
    setCreating(true)
    try {
      const inv = await api.adminCreateInvite(newMaxUses, newExpires, newNote.trim())
      flash(`邀请码 ${inv.code} 已创建`)
      setNewMaxUses(1)
      setNewExpires('')
      setNewNote('')
      reload()
    } catch (e) {
      flash(`创建失败：${(e as Error).message}`)
    } finally {
      setCreating(false)
    }
  }

  const removeInvite = async (inv: InviteInfo) => {
    if (!confirm(`确定删除邀请码 ${inv.code}？`)) return
    try {
      await api.adminDeleteInvite(inv.code)
      flash('邀请码已删除')
      reload()
    } catch (e) {
      flash(`删除失败：${(e as Error).message}`)
    }
  }

  const copyCode = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code)
      flash('邀请码已复制')
    } catch {
      flash('复制失败，请手动选择复制')
    }
  }

  return (
    <div className="main">
      <h1>管理后台</h1>
      <p className="sub">账号与邀请码管理，仅管理员可见</p>

      {error && (
        <div className="card" style={{ borderColor: 'var(--bad)' }}>
          <p className="err" style={{ margin: 0 }}>加载失败：{error}</p>
        </div>
      )}

      <div className="card">
        <h2>用户（{users.length}）</h2>
        {!loaded ? (
          <p className="muted">加载中…</p>
        ) : users.length === 0 ? (
          <p className="muted">还没有用户</p>
        ) : (
          <div className="mlist">
            {users.map((u) => (
              <div className="it" key={u.id}>
                <div className="grow">
                  <div className="name">
                    {u.username}
                    {u.id === me.id && <span className="muted">（我）</span>}
                  </div>
                  <div className="meta">
                    注册 {fmtTime(u.createdAt)} · 最近登录 {fmtTime(u.lastLogin)}
                    {u.invitedBy ? ` · 邀请人 ${u.invitedBy}` : ''}
                  </div>
                </div>
                <span className={`tag ${u.role === 'admin' ? 'blue' : ''}`}>{u.role === 'admin' ? '管理员' : '用户'}</span>
                <button className="mini-btn" onClick={() => toggleRole(u)} disabled={u.id === me.id}>
                  {u.role === 'admin' ? '降为用户' : '设为管理员'}
                </button>
                <button className="mini-btn" onClick={() => resetPassword(u)}>重置密码</button>
                <button className="mini-btn danger" onClick={() => removeUser(u)} disabled={u.id === me.id}>
                  删除
                </button>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="card">
        <h2>邀请码（{invites.length}）</h2>
        <div className="row" style={{ flexWrap: 'wrap', marginBottom: 14 }}>
          <label className="muted" style={{ fontSize: 13 }}>
            次数
            <input
              className="num"
              style={{ marginLeft: 6 }}
              type="number"
              min={1}
              max={999}
              value={newMaxUses}
              onChange={(e) => setNewMaxUses(Math.max(1, Math.min(999, Number(e.target.value) || 1)))}
            />
          </label>
          <label className="muted" style={{ fontSize: 13 }}>
            有效期
            <input
              className="text-input"
              style={{ width: 160, marginLeft: 6 }}
              type="date"
              value={newExpires}
              onChange={(e) => setNewExpires(e.target.value)}
            />
          </label>
          <input
            className="text-input"
            style={{ width: 200 }}
            value={newNote}
            onChange={(e) => setNewNote(e.target.value)}
            placeholder="备注（可选）"
          />
          <button className="btn" onClick={createInvite} disabled={creating}>
            {creating ? '创建中…' : '生成邀请码'}
          </button>
        </div>

        {!loaded ? (
          <p className="muted">加载中…</p>
        ) : invites.length === 0 ? (
          <p className="muted">还没有邀请码，先生成一个</p>
        ) : (
          <div className="mlist">
            {invites.map((inv) => (
              <div className="it" key={inv.code}>
                <span className="code">{inv.code}</span>
                <span className={`tag ${inv.used >= inv.maxUses ? 'amber' : 'green'}`}>
                  {inv.used}/{inv.maxUses}
                </span>
                <div className="grow meta">
                  {inv.expiresAt ? `有效期至 ${inv.expiresAt}` : '长期有效'}
                  {inv.note ? ` · ${inv.note}` : ''}
                </div>
                <button className="mini-btn" onClick={() => copyCode(inv.code)}>复制</button>
                <button className="mini-btn danger" onClick={() => removeInvite(inv)}>删除</button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
