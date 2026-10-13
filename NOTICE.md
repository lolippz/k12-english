# 第三方数据与许可

本仓库的**源代码**按 MIT 许可发布（见 LICENSE）。但仓库里同时分发了若干第三方数据，
它们各有各的许可，重新分发本仓库时同样需要遵守。这里逐项说明。

> **一句话结论**：自己用、自己改，没问题。要公开分发（比如把仓库设成 public、
> 或者把打包好的 exe 发给别人），请把下面几项看完再决定。

---

## 1. 词库 `data/words.json`

这是三份数据合成的结果，由 `tools/` 下的流水线产出。

| 来源 | 用途 | 许可 |
|---|---|---|
| [RealKai42/qwerty-learner](https://github.com/RealKai42/qwerty-learner) `dicts/` | 词表本体：哪些词属于哪个教材单元 | **GPL-3.0** |
| [ECDICT](https://github.com/skywind3000/ECDICT) | 音标、中文释义、词形变化、考试标签、词频 | MIT |
| [Tatoeba](https://tatoeba.org/)（经 manythings.org/anki 分发） | 中英例句 | CC-BY 2.0 |

### 需要留意的点

**qwerty-learner 的 `dicts/` 是 GPL-3.0。** 本仓库把它的词表内容加工后放进了
`data/words.json` 并随仓库分发，因此这一份数据可以视为 GPL-3.0 的衍生作品。

单词拼写本身属于事实，不受版权保护；中文释义来自 MIT 的 ECDICT，例句来自 CC-BY 的
Tatoeba。但「哪些词被收进哪个教材单元」这个**选编结果**来自 qwerty-learner，
通常认为构成可受保护的汇编。

### 因此

- **个人自用 / 内部使用**：无需任何额外动作。
- **公开分发**（仓库 public、发布 Release、把 exe 传给别人）：
  严格来说应让**整体分发物**符合 GPL-3.0，或者把 `data/words.json` 从分发物中剥离、
  让使用者自己跑流水线生成。
- 如果你希望省心，把根目录 LICENSE 换成 GPL-3.0 全文是最稳妥的做法。

## 2. 发音音频 `frontend/public/audio/*.mp3`

由 [Piper](https://github.com/rhasspy/piper)（MIT）离线合成，音色模型为
`en_US-lessac-medium`。Piper 本身是 MIT，但**音色模型**的许可需单独确认
（rhasspy/piper-voices 中各音色分别标注，多数为 CC BY / CC0 / MIT 之一）。

商用或再分发前请到 [piper-voices](https://huggingface.co/rhasspy/piper-voices)
查一下 `en_US-lessac-medium` 的当前标注。

音频是可再生的：删掉后用 `python tools/gen_audio.py` 重新生成（需要先下载 Piper 模型）。

## 3. 前端运行时依赖

| 包 | 许可 |
|---|---|
| React / React DOM | MIT |
| [ts-fsrs](https://github.com/open-spaced-repetition/ts-fsrs) | MIT |
| Vite | MIT |

## 4. 后端运行时依赖

| 包 | 许可 |
|---|---|
| Spring Boot | Apache-2.0 |
| [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) | Apache-2.0 |
| SQLite 本体 | Public Domain |
| HikariCP | Apache-2.0 |

## 5. 署名要求

若你分发本仓库或其衍生作品，请在文档中保留以下署名：

- 词表来源：RealKai42/qwerty-learner（GPL-3.0）
- 词典数据：skywind3000/ECDICT（MIT）
- 例句语料：Tatoeba（CC-BY 2.0）— CC-BY 要求署名
- 发音合成：rhasspy/piper（MIT）+ 对应音色模型许可
