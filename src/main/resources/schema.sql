-- K12 English 进度库结构
--
-- 由 Spring Boot 的 spring.sql.init 在启动时执行，全部 IF NOT EXISTS，
-- 所以可以反复启动而不会破坏已有数据（也就是没有版本化迁移，单机自用够用）。

-- 记忆卡片：一个词一行。FSRS 的调度结果由前端算好后写进来。
CREATE TABLE IF NOT EXISTS cards (
  w               TEXT    PRIMARY KEY,
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
  first_seen      INTEGER NOT NULL
);

-- 复习日志：一次作答一行。学情统计全部从这里聚合。
CREATE TABLE IF NOT EXISTS logs (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  w       TEXT    NOT NULL,
  ts      INTEGER NOT NULL,
  grade   INTEGER NOT NULL,
  mode    TEXT    NOT NULL,
  correct INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_logs_ts ON logs (ts);
CREATE INDEX IF NOT EXISTS idx_logs_w  ON logs (w);

-- 设置：整体存一段 JSON，键固定为 'settings'。
-- 单机自用，没必要为 7 个字段建 7 列。
CREATE TABLE IF NOT EXISTS settings (
  k TEXT PRIMARY KEY,
  v TEXT NOT NULL
);
