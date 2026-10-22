/**
 * 后端接口客户端。
 *
 * 持久化全部交给 Spring Boot 侧的 SQLite，前端不再直接碰 IndexedDB。
 * 开发时 Vite 会把 /api 代理到 127.0.0.1:8080（见 vite.config.ts）；
 * 打包后前端产物由 Spring Boot 自己托管，同源，无需代理。
 */

import type { LogEntry, Settings } from './types'
import type { CardRecord } from './srs'

/** 生产环境同源，留一个变量方便指向别的端口调试。 */
const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? ''

export interface MetaResponse {
  name: string
  version: string
  totalWords: number
  totalCards: number
  totalLogs: number
  stages: string[]
}

export interface StateResponse {
  settings: Settings
  cards: CardRecord[]
  logs: LogEntry[]
}

export interface QueueResult {
  reviews: string[]
  fresh: string[]
  dueCount: number
  remainingNew: number
  cards: Record<string, CardRecord>
}

export interface DayStat {
  date: string
  count: number
  correct: number
}

export interface StageProgress {
  total: number
  mastered: number
}

export interface HardWord {
  w: string
  seen: number
  wrong: number
  state: number
  stability: number
  retention: number
}

export interface StatsResult {
  streak: number
  today: number
  accuracy: number
  totalReviews: number
  totalCards: number
  mastered: number
  learning: number
  reviewing: number
  daily: DayStat[]
  byStage: Record<string, StageProgress>
  hardWords: HardWord[]
  dueCount: number
  remainingNew: number
  poolSize: number
}

export interface ExportPayload {
  version: number
  exportedAt: string
  settings: Settings
  cards: CardRecord[]
  logs: LogEntry[]
}

/* ---------------------------------------------------------------- 账号 */

export interface UserInfo {
  id: string
  username: string
  role: string
  admin: boolean
}

export interface AuthStatus {
  hasUsers: boolean
  userCount: number
}

export interface AdminUser {
  id: string
  username: string
  role: string
  createdAt: number
  lastLogin: number | null
  invitedBy: string
}

export interface InviteInfo {
  code: string
  maxUses: number
  used: number
  createdAt: number
  expiresAt: string
  note: string
}

/** 带状态码的错误，调用方可以据此区分「未登录」和别的失败。 */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/** 统一请求：非 2xx 时抛出后端给的可读原因，而不是裸的状态码。 */
async function req<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { ...(init.headers as Record<string, string> | undefined) }
  if (init.body) headers['Content-Type'] = 'application/json'

  let res: Response
  try {
    res = await fetch(BASE + path, { ...init, headers })
  } catch (e) {
    throw new ApiError(0, `连不上后端服务，请确认它已启动（${(e as Error).message}）`)
  }

  if (!res.ok) {
    let detail = `${res.status} ${res.statusText}`
    try {
      const body = (await res.json()) as { message?: string; error?: string }
      if (body?.message) detail = body.message
      else if (body?.error) detail = body.error
    } catch {
      /* 错误体不是 JSON，用状态码兜底 */
    }
    throw new ApiError(res.status, detail)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

export const api = {
  meta: () => req<MetaResponse>('/api/meta'),
  state: () => req<StateResponse>('/api/state'),
  stats: () => req<StatsResult>('/api/stats'),
  queue: () => req<QueueResult>('/api/study/queue'),

  /** 提交一次作答：卡片新状态 + 日志一起落库。 */
  review: (card: CardRecord, log: LogEntry) =>
    req<LogEntry>('/api/review', { method: 'POST', body: JSON.stringify({ card, log }) }),

  getSettings: () => req<Settings>('/api/settings'),
  saveSettings: (s: Settings) => req<Settings>('/api/settings', { method: 'PUT', body: JSON.stringify(s) }),

  exportAll: () => req<ExportPayload>('/api/data/export'),
  importAll: (payload: unknown) =>
    req<ExportPayload>('/api/data/import', { method: 'POST', body: JSON.stringify(payload) }),
  reset: () => req<Settings>('/api/data/reset', { method: 'POST' }),

  /* ---------------- 账号 ---------------- */

  authStatus: () => req<AuthStatus>('/api/auth/status'),
  me: () => req<UserInfo>('/api/auth/me'),
  login: (username: string, password: string) =>
    req<UserInfo>('/api/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  register: (username: string, password: string, invite: string) =>
    req<UserInfo>('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify({ username, password, invite }),
    }),
  logout: () => req<{ ok: boolean }>('/api/auth/logout', { method: 'POST' }),

  /* ---------------- 管理员 ---------------- */

  adminUsers: () => req<{ users: AdminUser[] }>('/api/admin/users'),
  adminDeleteUser: (id: string) =>
    req<{ ok: boolean }>(`/api/admin/users/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  adminSetRole: (id: string, role: string) =>
    req<{ ok: boolean }>(`/api/admin/users/${encodeURIComponent(id)}/role`, {
      method: 'POST',
      body: JSON.stringify({ role }),
    }),
  adminResetPassword: (id: string, password: string) =>
    req<{ ok: boolean }>(`/api/admin/users/${encodeURIComponent(id)}/password`, {
      method: 'POST',
      body: JSON.stringify({ password }),
    }),

  adminInvites: () => req<{ invites: InviteInfo[] }>('/api/admin/invites'),
  adminCreateInvite: (maxUses: number, expiresAt: string, note: string) =>
    req<InviteInfo>('/api/admin/invites', {
      method: 'POST',
      body: JSON.stringify({ maxUses, expiresAt, note }),
    }),
  adminDeleteInvite: (code: string) =>
    req<{ ok: boolean }>(`/api/admin/invites/${encodeURIComponent(code)}`, { method: 'DELETE' }),
}
