# 前端与词库数据

这个目录是前端应用（React + TypeScript + Vite）。**完整的安装、构建、运行说明在
[仓库根 README](../README.md)**，这里只讲前端单独开发时要注意的事，以及词库数据是怎么来的。

---

## 单独开发前端

后端要先跑起来（前端启动时会拉 `/api/words` 拿词库）：

```bash
# 终端 1：后端
./mvnw spring-boot:run

# 终端 2：前端
cd frontend
npm install
npm run dev          # http://127.0.0.1:5199
```

`vite.config.ts` 里配了代理，`/api` 和 `/audio` 都会转发到 `127.0.0.1:8080`，
所以前端不用管跨域。后端端口不是 8080 时：

```bash
BACKEND_URL=http://127.0.0.1:9000 npm run dev
```

改完前端要重新构建才会进 jar：

```bash
npm run build        # tsc --noEmit + vite build，产出 dist/
cd .. && ./mvnw package   # Maven 把 dist/ 复制进 target/classes/static
```

### 常用命令

```bash
npm run dev          # 开发服务器（热更新）
npm run build        # 类型检查 + 生产构建
npm run preview      # 预览构建产物（注意：这里没有后端，接口会 404）
npm run smoke        # 冒烟测试：词库质量 + FSRS（不需要后端）
npm run smoke:api    # 前后端契约测试（需要后端已启动）
npm run full         # 同时起前后端
```

---

## 词库规模

| 学段 | 词池 | 来源 |
|---|---|---|
| 小学 | 1226 词 | 人教版 PEP 三起 3上—6下（8 册）、人教版新起点 1上—6下（12 册） |
| 初中 | 2655 词 | 人教版 七上—九年级全册（5 册）、中考核心词 |
| 高中 | 5924 词 | 人教版 必修 1-5、选修 6-11（11 册）、高考 3500、高考真题高频、高考阅读高频 |

合计 **6877 个词条**，共 40 个册次。词条按**教材单元顺序**排列，所以学习顺序与课本进度一致——
小学从 `ruler / pencil / eraser` 开始，初中从 `Good morning!` 开始，高中从 `add up` 开始。

> **词表清洗**：源词表是给人看的教材附录，混进了大量非"单词"内容。`tools/normalize.py`
> 会先做一轮清洗，实测命中 330 处（7016 → 6877 条）：
>
> | 类型 | 数量 | 例 |
> |---|---|---|
> | 不规则变化表被当成词条 | 97 | `go went gone` → `go`、`foot feet` → `foot`、`bad worse worst` → `bad` |
> | 别名 / 全称泄漏 | 29 | `bike = bicycle` → `bike`、`liter/litre` → `liter` |
> | 重复条目合并 | 139 | `wear.` 并入 `wear`、`play  chess` 并入 `play chess` |
> | 尾部句点 | 12 | `wear.` → `wear`、`first.` → `first` |
> | 拼写错误 | 15 | `overnignt` → `overnight`、`strorage` → `storage` |
> | 省略号 / 可选 be / 词性后缀 | 22 | `(be)strict with...` → `be strict with...`、`could modal` → `could` |
>
> 变化表的识别是**数据驱动**的：只有当一个词条首词之后的每个 token 都能在 ECDICT 的
> `exchange` 字段里找到"属于首词的变化形"时，才判定为变化表。这样 `go boating`、
> `have a look` 这类正当短语不会被误伤。
>
> ECDICT 不记录情态动词之间的对应（`shall/should`、`will/would` 属于语法变化而非词汇变化），
> 所以额外维护了一张 `SUPPLEMENT` 补表。
>
> 括号注记只在括号内容是**首词的变化形**（`foot (feet)`）或**序数**（`fifth (5th)`）时才剥离——
> 否则 `run out (of)`、`play a part (in)` 会被剥成 `run out`、`play a part`，介词被吃掉，词义就变了。
> 教材句型（`Good morning!` / `How are you?` / `What about...?`）和缩写（`U.K.` / `Miss.` / `a.m.`）
> 原样保留。

### 每个词条携带的信息

| 字段 | 覆盖 | 来源 |
|---|---|---|
| 中文释义 | 100% | qwerty-learner |
| 音标 | **98.9%** | qwerty-learner 为主，ECDICT 兜底；**短语音标由组成词拼接合成** |
| 发音音频 | 100% | Piper 离线合成，mp3 32kbps 单声道，随包分发 |
| 词形变化 | 4207 条 | ECDICT（复数 / 过去式 / 过去分词 / 现在分词 / 比较级 / 最高级） |
| 考试标签 | 4936 条 | ECDICT（中考 / 高考 / 四级 / 六级 / 考研 / 雅思 / 托福 / GRE） |
| 英文释义 | 5330 条 | ECDICT |
| 中英例句 | **4235 条（61.6%）** | Tatoeba（CC-BY 2.0） |
| 别名 / 全称 | 31 条 | 清洗阶段从 `X = Y` 写法和补表中还原 |
| 柯林斯星级 / 牛津3000 / 词频 | 部分 | ECDICT |

> **关于音标**：仍缺的 79 条主要是缩写（`USA` / `GPS` / `kg`）与人名（`Julie` / `Annie`），
> 词典本就不收录，但发音音频是齐的。
>
> **关于例句**：覆盖率能到 61.6%，靠的是三条匹配通道叠加——**token 精确命中**（3363 条）、
> **词形变化命中**（`run` 的例句可以来自 `he runs` / `she ran`）、**短语 n-gram 命中**
> （`go swimming` / `take photos` 这类多词短语，纯 token 倒排索引永远匹配不到）。后两条共贡献 872 条。
> 再往上就撞到语料上限了：Tatoeba 的中英句对总共只有 3 万条，小学词汇
> （`eraser` / `crayon` / `panda`）本就不出现在里面。缺例句的词会显示英文释义作为补充。

---

## 数据流水线

词库本体在**仓库根的 `data/words.json`**，已经提交，正常开发不需要重跑。
想更新词表时：

```bash
cd tools
pip install -r requirements.txt

python fetch_dicts.py     # ① 拉 qwerty-learner 词表 + ECDICT -> out/words.base.json
python normalize.py       # ② 清洗 -> 原地改写 out/words.base.json（先备份 .raw.json）
python enrich.py          # ③ 补音标 / 词形 / 标签 / 释义 / 例句 -> ../data/words.json
python gen_audio.py       # ④ Piper 合成发音 -> ../frontend/public/audio/
python gen_audio.py --prune   # 清理已不在词表里的孤儿音频
```

> `normalize.py --dry-run` 可以只看清洗报告、不写文件。
>
> `gen_audio.py` 支持断点续跑：已存在的 mp3 会跳过，中断后重跑即可继续。
> 词表清洗改过词头之后，**务必带上 `--prune`**，否则旧词头的 mp3 会变成孤儿文件。
>
> 重跑 `normalize.py` 前先把 `out/words.base.raw.json` 拷回 `out/words.base.json`，
> 否则等于在已清洗的结果上再洗一遍（`meta.total_raw` 会失真）。
>
> `tools/out/` 是中间产物目录，已在 `.gitignore` 里；`data/words.json` 是最终产物，**要提交**。

### 发音音频

**默认走预生成音频**：6877 个词条的发音由 Piper 离线神经网络 TTS 合成，
编码为 32kbps 单声道 mp3，随应用一起分发（约 20 MB），完全离线、零延迟。

**自动回退**：音频缺失、或切到英式口音时，自动回退到浏览器内置的 Web Speech API。
例句朗读始终用系统 TTS。

> 为什么不用 edge-tts（微软在线语音）：本机系统时间与真实时间存在偏差，
> 微软的 `Sec-MS-GEC` 校验令牌会被拒（403），且该接口依赖在线服务。
> Piper 是纯离线方案，不依赖网络也不依赖系统语音包。

---

## 记忆算法

使用 **FSRS**（Free Spaced Repetition Scheduler，`ts-fsrs` 5.4.2，MIT 许可），
目标保持率 90%，开启模糊化。判定为「已掌握」的条件是卡片进入复习状态且稳定度 ≥ 21 天。

FSRS 的调度计算**留在前端**（`src/lib/srs.ts`），后端只负责持久化与聚合。
原因和迁移路径见根 README 的「实现说明」。

---

## 前端结构

```
frontend/
├── src/
│   ├── main.tsx            入口：先拉 /api/words 灌词库，再渲染
│   ├── App.tsx             会话状态容器（设置、卡片、学习会话）
│   ├── data/index.ts       词库访问层（同步查询，启动时 hydrate）
│   ├── lib/api.ts          后端接口客户端
│   ├── lib/srs.ts          FSRS 调度封装
│   ├── lib/speech.ts       音频播放与 TTS 回退
│   ├── lib/types.ts        Settings / LogEntry
│   └── pages/              今日 / 学习 / 学情 / 设置
├── public/audio/           6877 个发音 mp3
├── smoke.mjs               冒烟测试（词库质量 + FSRS）
└── smoke-api.mjs           前后端契约测试
```

---

## 后续可以做的事

1. **补全剩余 79 条音标** —— 主要是缩写和人名，价值有限
2. **例句音频** —— 目前例句用系统 TTS，可为 4235 条例句预生成 mp3
3. **跟读评测** —— 录音 + 回放 + 原文对照，进阶接 whisper.cpp 做词级比对
4. **分级阅读** —— 接入 StoryWeaver（CC-BY）绘本，点词查义、一键加词表
5. **扩大例句语料** —— 换用 Tatoeba 官方完整导出（links.tar.bz2 约 150 MB）
   可把中英句对从 3 万提到约 5 万，预计覆盖率再涨几个百分点
6. **更多教材版本** —— 外研版 / 北师大版 / 译林版 / 冀教版词库在 qwerty-learner 里都有，
   加进 `fetch_dicts.py` 的 CATALOG 即可，不用改代码

---

## 许可

本仓库按 **GPL-3.0** 发布，全文见 [../LICENSE](../LICENSE)。
词库与发音音频来自第三方，各有各的许可，署名要求见 [NOTICE.md](../NOTICE.md)。
