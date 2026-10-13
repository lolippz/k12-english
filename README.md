# K12 英语学习平台

覆盖小学 / 初中 / 高中全学段的英语单词学习应用。**单机运行、数据不出本机**，
启动后浏览器访问 `127.0.0.1`，学习进度存在本机 SQLite 里。

- **前端**：React 19 + TypeScript + Vite
- **后端**：Java 17 + Spring Boot 4 + SQLite
- **词库**：6877 词条（人教版 PEP / 新起点 / 人教初中 / 高中必修选修），含音标、释义、词形变化、考试标签、中英例句、离线发音音频
- **记忆算法**：FSRS（Free Spaced Repetition Scheduler）

---

## 快速开始

前置条件：**JDK 17 或更高**（[Adoptium](https://adoptium.net) 下载即可）。
Maven 不用装，仓库自带 Maven Wrapper。

### 方式一：一键启动（推荐）

```bash
# Windows
start.bat

# macOS / Linux
bash start.sh
```

脚本会自己判断缺什么补什么：前端产物没有就先 `npm run build`（需要 Node 22+），
后端 jar 没有就先 `./mvnw package`，然后启动并自动打开浏览器。

### 方式二：手动构建

```bash
git clone <your-repo-url> k12-english
cd k12-english

# 1. 构建前端（需要 Node 22+）
cd frontend && npm install && npm run build && cd ..

# 2. 构建后端（首次会下载依赖）
./mvnw package          # Windows 上也可以用 mvnw.cmd package

# 3. 运行
java -jar target/k12-english.jar
```

> **构建顺序不能反。** Maven 的 `with-frontend` profile 是按「`frontend/dist` 是否存在」
> 条件激活的，所以跳过第 1 步 `mvn package` 也会成功，只是打出来的 jar 里没有界面。
> 真出现这种情况，启动日志会打一条醒目警告告诉你。

> **关于首次构建耗时。** 打包时要把 6877 个发音 mp3 复制进 `target/classes`。
> 在普通机器上这一步是几十秒；但在开启杀毒软件实时扫描的 Windows 上，
> 小文件复制会慢得离谱（实测约 16 个/秒，整次冷构建 10 分钟）。
> 这是文件系统层面的开销，不是构建配置的问题 ——
> **增量构建（`target/` 已存在）只要约 45 秒**，所以平时不要随便 `mvn clean`。
> 想彻底解决就给项目目录加一条杀毒软件排除项。

仓库里已经提交了 `data/words.json` 和 6877 个发音 mp3，
所以**全新 clone 不需要跑任何数据脚本就能直接构建运行**
（已实测：仅用提交内容还原一份全新 clone，`./mvnw package` 构建成功，
产出的 jar 启动后首页 / 接口 / 音频均正常）。

启动后会自动打开浏览器（可用 `--app.open-browser=false` 关掉）。
手动访问 <http://127.0.0.1:8080> 也可以。

### 常用启动参数

| 参数 | 默认 | 说明 |
|---|---|---|
| `--server.port=8080` | 8080 | 监听端口 |
| `--app.open-browser=false` | true | 启动后不自动开浏览器 |
| `--app.data-dir=<路径>` | `~/.k12-english` | 进度库所在目录 |

进度库放在用户目录而不是安装目录，这样换版本、换 jar 都不会丢学习记录。

---

## 用 IntelliJ IDEA 开发

1. `File → Open`，选中项目根目录（有 `pom.xml` 的那一层），IDEA 会按 Maven 项目导入。
2. 确认 `File → Project Structure → Project SDK` 是 **JDK 17+**。
   Maven 用仓库自带的 wrapper 就行，不必在 IDEA 里额外配 Maven。
3. 运行 `com.k12english.K12EnglishApplication` 的 `main` 方法即可。
   仓库里已经放好共享运行配置（`.run/` 目录），打开后直接可用。

> **从 IDEA 直接 Run 就能用，不需要先 `mvn package`。**
> IDEA 的 `Application` 运行配置只做 `Make`，**不会**跑 Maven 的 `process-resources`，
> 所以 `target/classes` 里既没有前端产物也没有 `data/words.json`。
> 为了不让开发者一上手就撞 404，程序内置了两条开发态兜底：
>
> | 缺什么 | 兜底 |
> |---|---|
> | `classpath:static/`（前端产物） | 追加静态资源位置 `file:frontend/dist/`（见 `application.yml`） |
> | `classpath:data/words.json`（词库） | 从工作目录的 `data/words.json` 读（见 `WordService`） |
>
> 两条兜底只在 classpath 里找不到时才生效，**打包好的 jar 行为完全不变**。
> 命中的时候启动日志会写一行「改用源码目录 …（开发模式）」。
> 所以运行配置里的工作目录必须是项目根（`.run/` 里已经这么配好了）。

### 前端单独开发

后端跑起来之后，前端可以单独起 dev server，改代码有热更新：

```bash
cd frontend
npm install
npm run dev          # http://127.0.0.1:5199，/api 与 /audio 自动代理到 8080
```

如果不想开两个终端：

```bash
bash scripts/dev.sh  # 同时起后端和前端
```

前端改完后要重新构建才会进 jar：

```bash
cd frontend && npm run build   # 产出 frontend/dist，Maven 打包时自动复制进 static/
```

---

## 项目结构

```
.
├── pom.xml                     Maven 构建（Spring Boot 4 / Java 17）
├── mvnw, mvnw.cmd              Maven Wrapper，无需预装 Maven
├── start.bat / start.sh        一键启动（缺什么补什么）
├── .run/                       IDEA 共享运行配置（后端 / 前端 / 全栈）
├── .github/workflows/          CI 与 Release 工作流
├── data/
│   └── words.json              词库本体（已提交，6877 词条 / 2.8 MB）
├── docs/                       立项方案与实施方案
├── src/main/java/com/k12english/
│   ├── K12EnglishApplication   入口
│   ├── config/                 配置属性、SQLite 数据源、启动后开浏览器
│   ├── domain/                 Word / CardRecord / ReviewLog / Settings
│   ├── repo/                   JdbcTemplate 数据访问
│   ├── service/                词库、学习队列、学情聚合、数据管理
│   └── web/                    REST 控制器 + 统一异常处理
├── src/main/resources/
│   ├── application.yml         配置
│   └── schema.sql              建表脚本（幂等）
├── src/test/java/              46 个单元/集成测试
├── frontend/                   React 前端（详见 frontend/README.md）
│   ├── src/lib/api.ts          后端接口客户端
│   ├── src/lib/srs.ts          FSRS 调度（ts-fsrs）
│   ├── src/pages/              今日 / 学情 / 设置 / 答题
│   ├── public/audio/           6877 个发音 mp3
│   ├── smoke.mjs               前端冒烟测试（词库质量 + FSRS）
│   └── smoke-api.mjs           前后端契约测试
├── scripts/
│   ├── smoke-api.sh            后端接口冒烟测试
│   ├── dev.sh                  前后端一起起
│   └── package-exe.sh          用 jpackage 打免安装 exe
├── tools/                      词库数据流水线（Python，见下）
├── LICENSE                     许可：GPL-3.0
└── NOTICE.md                   词库与音频的第三方许可
```

---

## REST 接口

所有接口都在 `/api` 下。前端启动时先拉 `/api/words` 把词库灌进内存，
之后各页面按需取数据。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/meta` | 版本、词条数、卡片数、日志数 |
| GET | `/api/words` | 全量词库 `{meta, words}` |
| GET | `/api/words/{w}` | 单个词条（大小写不敏感） |
| GET | `/api/words/pool?stages=&books=` | 按学段/册次筛出的词池 |
| GET | `/api/stages` | 学段清单 |
| GET | `/api/books?stages=` | 册次清单 |
| GET | `/api/state` | 启动引导：`{settings, cards, logs}` |
| GET | `/api/study/queue` | 今日队列：到期复习 + 新词 |
| POST | `/api/review` | 提交一次作答（卡片 + 日志同事务落库） |
| GET | `/api/stats` | 学情聚合：打卡、正确率、14 天序列、学段进度、错词排行 |
| GET / PUT | `/api/settings` | 读写设置 |
| GET | `/api/data/export` | 导出备份 |
| POST | `/api/data/import` | 导入备份（整体替换） |
| POST | `/api/data/reset` | 清空学习记录（保留设置） |
| GET | `/audio/{slug}.mp3` | 发音音频（静态资源） |

示例：

```bash
curl http://127.0.0.1:8080/api/meta
curl http://127.0.0.1:8080/api/study/queue
curl http://127.0.0.1:8080/api/stats
```

---

## 测试

```bash
./mvnw test                                    # 46 个 Java 测试
cd frontend && npm run build                   # tsc --noEmit + vite build
node frontend/smoke.mjs                        # 55 项：词库质量 + FSRS
node frontend/smoke-api.mjs                    # 29 项：前后端契约（需后端已启动）
bash scripts/smoke-api.sh http://127.0.0.1:8080  # 43 项：后端接口
```

`smoke-api.mjs` 会用 Vite 的 SSR 加载器把**前端真实的 `api.ts`** 跑起来打真实后端，
所以「后端改了字段名、前端还以为叫老名字」这类问题会在测试阶段就暴露。

---

## 词库数据流水线

`data/words.json` 已经提交，正常开发**不需要**重跑。只有在想更新词表时才用得上。

```bash
cd tools
pip install -r requirements.txt

python fetch_dicts.py     # 拉 qwerty-learner 词表 + ECDICT，产出 out/words.base.json
python normalize.py       # 清洗：折叠词形变化表、剥词性后缀、修拼写、去重
python enrich.py          # 补音标 / 释义 / 词形 / 标签 / Tatoeba 例句 -> ../data/words.json
python gen_audio.py       # 用 Piper 合成发音 -> ../frontend/public/audio/
python gen_audio.py --prune   # 清理已不在词表里的孤儿音频
```

当前数据质量：

| 指标 | 数值 |
|---|---|
| 词条数 | 6877（原始 7016，清洗合并掉 139） |
| 音标覆盖 | 98.9%（缺的主要是缩写与人名） |
| 例句覆盖 | 61.6%（4235 条） |
| 发音音频 | 6877 / 6877，20.5 MB |
| 词形变化 | 4207 条 |
| 考试标签 | 4936 条 |

例句覆盖率再往上会撞 Tatoeba 语料上限（manythings.org 只分发了约 3 万句对，
`eraser`、`crayon` 这类小学词本就不在其中）。换用 Tatoeba 官方完整导出
（`links.tar.bz2`，约 150 MB）预计还能再涨几个百分点。

---

## 打包成免安装 exe

```bash
bash scripts/package-exe.sh
```

产物在 `dist/`：

- `dist/K12English/` — 应用目录，双击 `K12English.exe` 即可运行
- `dist/K12English-1.0.0-windows-x64.zip` — 约 103 MB，发给别人直接解压就能用

自带精简 JRE，**目标机器无需安装 Java**。`jpackage` 是 JDK 自带工具，不用额外装。

## 发布到 GitHub

仓库里配了两个工作流：

| 工作流 | 触发 | 做什么 |
|---|---|---|
| `ci.yml` | push / PR | 后端跑测试并打包、前端跑类型检查与冒烟测试、起真服务跑接口与契约冒烟 |
| `release.yml` | 打 `v*` 标签 | 构建 jar 与 Windows 免安装 zip，自动创建 Release 并附上产物 |

发一个版本：

```bash
git tag v1.0.0
git push origin v1.0.0
```

Release 说明里会自动带上变更日志（`generate_release_notes`）。

---

## 实现说明

### FSRS 调度留在前端

FSRS 的核心计算（稳定度、难度、遗忘曲线、间隔预测）由前端的 `ts-fsrs` 完成，
后端只负责持久化与聚合。

这么做是因为 `ts-fsrs` 是**纯函数**、有测试覆盖，而完整移植到 Java 涉及参数迁移、
学习步长策略、Alea 伪随机模糊等约 500 行状态机，风险不低。后端在
`CardRecord` 上留好了接口边界（字段与前端一一对应），要迁随时可以迁。

代价是「调度算法不在后端」，如果你打算做多端同步，这块需要重新考虑。

### 词库不进 JS 包

前端启动时从 `GET /api/words` 拉词库灌进内存，页面里继续用同步的
`findWord()` / `wordsOfStages()`。好处是 JS 包从 2.8 MB 降到 269 KB
（gzip 899 KB → 83 KB），并且词库只有 `data/words.json` 一份，不再有副本。

### 词库字段名刻意保留前端命名

`CardRecord` 里 `elapsed_days` / `scheduled_days` / `learning_steps` 是 snake_case，
`firstSeen` 是 camelCase。这是历史遗留，但**故意不动**——改名要两边同时改，
漏一边就是静默丢字段。宁可丑一点。

---

## 已知限制

- **界面视觉效果没有自动化验证。** 逻辑层测试很全（46 + 55 + 43 + 29 项），
  但没有做浏览器端截图回归。改 CSS 之后请自己看一眼。
- **例句覆盖率 61.6%**，是语料上限而不是 bug。
- **没有多端同步。** 进度只在本机 SQLite，换电脑要手动导出/导入备份。
- **qwerty-learner 词表是 GPL-3.0**，公开分发前请读 [NOTICE.md](NOTICE.md)。

## 许可

本项目按 **GNU 通用公共许可证第 3 版（GPL-3.0）** 发布，全文见 [LICENSE](LICENSE)。

> Copyright (C) 2026 lolippz
>
> 本程序是自由软件：你可以根据自由软件基金会发布的 GNU 通用公共许可证
> （第 3 版，或你选择的任何更新版本）的条款重新发布和/或修改它。
>
> 本程序是基于「有用」的希望分发的，但不提供任何担保，甚至不包含适销性或
> 特定用途适用性的默示担保。详见 GNU 通用公共许可证。

**为什么是 GPL-3.0**：`data/words.json` 里的词表选编来自
[RealKai42/qwerty-learner](https://github.com/RealKai42/qwerty-learner)（GPL-3.0），
它与本仓库一同分发时构成其衍生作品，所以整体按 GPL-3.0 发布。
其余第三方数据的许可与署名要求见 [NOTICE.md](NOTICE.md)。
