-- K12 English 进度库结构
--
-- 由 Spring Boot 的 spring.sql.init 在启动时执行，全部 IF NOT EXISTS，
-- 所以可以反复启动而不会破坏已有数据。
--
-- 多用户版：cards / logs / settings 三张业务表都带 user_id，按账号隔离。
-- 老版本（单用户）的库由 SchemaMigrator 在启动时原地重建，已有数据归到 user_id = ''，
-- 第一个注册的账号（自动成为管理员）注册时会认领这批数据。

-- ---------------------------------------------------------------- 账号

CREATE TABLE IF NOT EXISTS users (
  id          TEXT    PRIMARY KEY,
  username    TEXT    NOT NULL,
  pwd         TEXT    NOT NULL,
  role        TEXT    NOT NULL DEFAULT 'user',
  created_at  INTEGER NOT NULL,
  last_login  INTEGER,
  invited_by  TEXT
);

-- 用户名大小写不敏感唯一。用 lower() 建索引，比在应用层查重可靠。
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_name ON users (lower(username));

-- ---------------------------------------------------------------- 邀请码

CREATE TABLE IF NOT EXISTS invites (
  code        TEXT    PRIMARY KEY,
  max_uses    INTEGER NOT NULL,
  used        INTEGER NOT NULL DEFAULT 0,
  created_at  INTEGER NOT NULL,
  expires_at  TEXT,
  note        TEXT,
  created_by  TEXT
);

-- ---------------------------------------------------------------- 会话

CREATE TABLE IF NOT EXISTS sessions (
  token      TEXT    PRIMARY KEY,
  user_id    TEXT    NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions (user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_exp  ON sessions (expires_at);

-- ---------------------------------------------------------------- 记忆卡片

-- 一个词的调度状态。FSRS 的结果由前端算好后写进来。
CREATE TABLE IF NOT EXISTS cards (
  user_id         TEXT    NOT NULL,
  w               TEXT    NOT NULL,
  due             INTEGER NOT NULL,
  stability       REAL    NOT NULL,
  difficulty      REAL    NOT NULL,
  elapsed_days    INTEGER NOT NULL,
  scheduled_days  INTEGER NOT NULL,
  learning_steps  INTEGER NOT NULL,
  reps            INTEGER NOT NULL,
  lapses          INTEGER NOT NULL,
  state           INTEGER NOT NULL,
  last_review     INTEGER,
  seen            INTEGER NOT NULL,
  wrong           INTEGER NOT NULL,
  first_seen      INTEGER NOT NULL,
  PRIMARY KEY (user_id, w)
);

-- ---------------------------------------------------------------- 复习日志

-- 一次作答一行。学情统计全部从这里聚合。
CREATE TABLE IF NOT EXISTS logs (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT    NOT NULL,
  w       TEXT    NOT NULL,
  ts      INTEGER NOT NULL,
  grade   INTEGER NOT NULL,
  mode    TEXT    NOT NULL,
  correct INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_logs_user_ts ON logs (user_id, ts);
CREATE INDEX IF NOT EXISTS idx_logs_user_w  ON logs (user_id, w);

-- ---------------------------------------------------------------- 设置

-- 每个用户一行，整段 JSON。单用户时没必要为 7 个字段建 7 列。
CREATE TABLE IF NOT EXISTS settings (
  user_id TEXT NOT NULL,
  k       TEXT NOT NULL,
  v       TEXT NOT NULL,
  PRIMARY KEY (user_id, k)
);
