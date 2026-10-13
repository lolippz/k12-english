#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
词库增强脚本
============
在 fetch_dicts.py 产出的 words.json 基础上补充：

  1. 音标兜底   —— qwerty 缺失时用 ECDICT；短语音标由组成单词拼接合成
  2. 词形变化   —— 复数 / 过去式 / 过去分词 / 现在分词 / 比较级 / 最高级
  3. 词频与分级 —— COCA 词频排名、柯林斯星级、牛津 3000 标记
  4. 考试标签   —— 中考 / 高考 / 四级 / 六级 / 考研 / 雅思 / 托福 / GRE
  5. 例句       —— Tatoeba 中英对照句（CC-BY），繁转简

数据来源:
  ECDICT  skywind3000/ECDICT (MIT)
  Tatoeba manythings.org/anki (CC-BY 2.0)

用法:
    python enrich.py
"""

import csv
import json
import os
import re
import sys
import zipfile

# 繁简转换。必须用受管 venv 解释器运行本脚本：
#   C:/Users/35425/.workbuddy-ai/binaries/python/envs/default/Scripts/python.exe enrich.py
# 基础解释器里没有装 zhconv，导入失败会静默导致例句全是繁体。
try:
    import zhconv
    HAS_ZHCONV = True
except ImportError:
    HAS_ZHCONV = False

csv.field_size_limit(10 ** 7)

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "cache")
IN_PATH = os.path.join(HERE, "out", "words.base.json")
OUT_PATH = os.path.abspath(os.path.join(HERE, "..", "data", "words.json"))

ECDICT = os.path.join(CACHE, "ecdict.csv")
TATOEBA = os.path.join(CACHE, "cmn-eng.zip")

# ECDICT 的 exchange 字段编码
FORM_KEYS = {
    "s": "复数",
    "3": "三单",
    "p": "过去式",
    "d": "过去分词",
    "i": "现在分词",
    "r": "比较级",
    "t": "最高级",
}

TAG_LABEL = {
    "zk": "中考",
    "gk": "高考",
    "cet4": "四级",
    "cet6": "六级",
    "ky": "考研",
    "ielts": "雅思",
    "toefl": "托福",
    "gre": "GRE",
}

# ECDICT 用西里尔字母 ә 表示 IPA 的 ə，另外 ɡ/g、ː 等也需归一
PHON_FIX = str.maketrans({
    "ә": "ə",
    "Ә": "ə",
    "ɡ": "g",
    "’": "'",
    "‘": "'",
})


def log(m):
    print(m, flush=True)


def flat(s):
    """ECDICT 的 CSV 里换行被写成字面量 \\n，需要先还原再压平。"""
    if not s:
        return ""
    s = s.replace("\\n", " ").replace("\r", " ").replace("\n", " ")
    return re.sub(r"\s+", " ", s).strip()


def flat_line(s):
    """取 ECDICT 释义的第一行，剥掉 [网络] 与 n./v./adj. 这类前缀。"""
    if not s:
        return ""
    first = s.replace("\\n", "\n").replace("\r", "\n").split("\n")[0]
    first = re.sub(r"^\s*\[[^\]]*\]\s*", "", first)
    first = re.sub(r"^[a-z]{1,5}\.\s*", "", first.strip())
    return re.sub(r"\s+", " ", first).strip()


def norm_phon(p):
    if not p:
        return ""
    p = p.strip().strip("/[]")
    p = p.translate(PHON_FIX)
    p = re.sub(r"\s+", " ", p)
    return p.strip()


def load_ecdict(needed):
    """只保留需要的词条，避免把 77 万条全读进内存。"""
    out = {}
    if not os.path.exists(ECDICT):
        log(f"  ! 未找到 {ECDICT}，跳过 ECDICT 增强")
        return out
    with open(ECDICT, encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            w = (row.get("word") or "").strip().lower()
            if w in needed and w not in out:
                out[w] = row
    return out


def parse_forms(exchange):
    """'p:came/i:coming/3:comes' -> {'过去式':'came', ...}"""
    res = {}
    if not exchange:
        return res
    for part in exchange.split("/"):
        if ":" not in part:
            continue
        k, v = part.split(":", 1)
        label = FORM_KEYS.get(k.strip())
        if label and v.strip():
            res[label] = v.strip()
    return res


def build_form_index(ecdict, target_words):
    """变化形 -> {原形}，让 'runs' / 'ran' 也能命中原形词条 run。

    只对目标词表里的词建索引，避免把 77 万条全展开。
    """
    out = {}
    for head, row in ecdict.items():
        if head not in target_words:
            continue
        for seg in (row.get("exchange") or "").split("/"):
            if ":" not in seg:
                continue
            k, v = seg.split(":", 1)
            if k.strip() == "0":          # 0 是原形标记本身
                continue
            v = v.strip().lower()
            if not v or v == head or not re.fullmatch(r"[a-z][a-z'\-]*", v):
                continue
            out.setdefault(v, set()).add(head)
    return out


def load_tatoeba(target_words, form2head=None):
    """建立 词 -> 候选例句 的倒排索引，只索引我们关心的词。

    三个匹配通道，得分从高到低，排序时精确命中优先：
      1. token 精确命中原形词条          +8
      2. 多词短语的 n-gram 命中          +6
      3. token 命中原形的某个变化形        +0
    没有第 3 条的话，run 的例句不会来自 'he runs' / 'she ran'；
    没有第 2 条的话，'go swimming' 这类短语永远匹配不到任何句子。
    """
    index = {}
    exact_heads = set()
    form2head = form2head or {}
    if not os.path.exists(TATOEBA):
        log(f"  ! 未找到 {TATOEBA}，跳过例句增强")
        return index, exact_heads
    z = zipfile.ZipFile(TATOEBA)
    raw = z.read(z.namelist()[0]).decode("utf-8", errors="replace")

    phrases = {t for t in target_words if " " in t}

    for line in raw.split("\n"):
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        eng, chn = parts[0].strip(), parts[1].strip()
        if not eng or not chn:
            continue
        # 中文里混英文、含括号注释的丢掉
        if re.search(r"[A-Za-z]", chn) or "[" in chn or "]" in chn:
            continue

        words = re.findall(r"[A-Za-z][A-Za-z'\-]*", eng.lower())
        nw = len(words)
        if nw < 3 or nw > 14:
            continue
        # 质量分：长度接近 8 词最佳，句子以大写开头结尾有标点
        score = 100 - abs(nw - 8) * 4
        if not eng[0].isupper():
            score -= 10
        if eng[-1] not in ".!?":
            score -= 6

        hits = {}                      # 词头 -> 本条句子的最高加分
        for w in words:
            if w in target_words:
                hits[w] = max(hits.get(w, 0), 8)
            for h in form2head.get(w, ()):
                if h in target_words:
                    hits[h] = max(hits.get(h, 0), 0)
        if phrases:
            for n in (2, 3, 4):
                for i in range(nw - n + 1):
                    g = " ".join(words[i:i + n])
                    if g in phrases:
                        hits[g] = max(hits.get(g, 0), 6)

        for h, bonus in hits.items():
            if bonus == 8:
                exact_heads.add(h)
            index.setdefault(h, []).append((score + bonus, eng, chn))

    # 每个词只保留分数最高的 12 条候选，控制内存
    for w in index:
        index[w].sort(key=lambda x: -x[0])
        del index[w][12:]
    return index, exact_heads


def main():
    log("=" * 66)
    log("词库增强")
    log("=" * 66)

    if not HAS_ZHCONV:
        log("  ! 未检测到 zhconv，例句中文将保持繁体。")
        log("  ! 请改用受管 venv 解释器运行：")
        log("  !   C:/Users/35425/.workbuddy-ai/binaries/python/envs/default/Scripts/python.exe enrich.py")
        log("")

    with open(IN_PATH, encoding="utf-8") as f:
        payload = json.load(f)
    words = payload["words"]
    log(f"载入 {len(words)} 个词条")

    # ---- 需要向 ECDICT 查询的键：单词本身 + 短语拆出的组成词 ----
    def components(w):
        t = w.lower().replace("’", "'")
        t = re.sub(r"\.\.\.", " ", t)
        t = re.sub(r"\([^)]*\)", " ", t)
        return [x for x in re.split(r"[\s]+", t) if x]

    needed = set()
    for x in words:
        needed.add(x["w"].lower())
        for c in components(x["w"]):
            needed.add(c.strip("'?!.,"))
    needed.discard("")

    log(f"向 ECDICT 查询 {len(needed)} 个键…")
    ecdict = load_ecdict(needed)
    log(f"  ECDICT 命中 {len(ecdict)} 条")

    # 先用 qwerty 已有音标 + ECDICT 音标，建一张全量音标表，供短语拼接使用
    phon_table = {}
    for x in words:
        if x["ph"]:
            phon_table[x["w"].lower()] = x["ph"]
    for k, row in ecdict.items():
        p = norm_phon(row.get("phonetic"))
        if p and k not in phon_table:
            phon_table[k] = p

    log(f"  音标表共 {len(phon_table)} 条")

    # ---- 例句索引 ----
    form2head = build_form_index(ecdict, needed)
    log(f"  词形索引 {len(form2head)} 个变化形（供 run <- runs/ran 这类匹配）")

    log("构建例句索引…")
    tatoeba, exact_heads = load_tatoeba(needed, form2head)
    log(f"  可配例句的词 {len(tatoeba)} 个（其中 token 精确命中 {len(exact_heads)}）")

    # ---- 逐条增强 ----
    stat = {
        "phon_from_ecdict": 0,
        "phon_from_phrase": 0,
        "phon_still_missing": 0,
        "example": 0,
        "example_exact": 0,
        "example_via_form": 0,
        "forms": 0,
        "tags": 0,
        "def": 0,
    }

    for x in words:
        w = x["w"]
        key = w.lower()

        # 1) 音标兜底
        if not x["ph"]:
            p = phon_table.get(key)
            if not p:
                # 短语：由组成词拼接
                comps = [c.strip("'?!.,") for c in components(w)]
                pieces = [phon_table.get(c) for c in comps]
                pieces = [q for q in pieces if q]
                if comps and len(pieces) == len(comps) and len(comps) > 1:
                    p = " ".join(pieces)
                    stat["phon_from_phrase"] += 1
                elif pieces and len(comps) > 1:
                    # 部分命中：只补能补的
                    p = " ".join(pieces)
                    stat["phon_from_phrase"] += 1
                else:
                    p = ""
            else:
                stat["phon_from_ecdict"] += 1
            x["ph"] = p
        if not x["ph"]:
            stat["phon_still_missing"] += 1

        # 2) ECDICT 附加信息
        row = ecdict.get(key)
        if row:
            forms = parse_forms(row.get("exchange"))
            if forms:
                x["forms"] = forms
                stat["forms"] += 1

            star = (row.get("collins") or "").strip()
            if star.isdigit() and int(star) > 0:
                x["star"] = int(star)
            if (row.get("oxford") or "").strip() == "1":
                x["oxford"] = 1

            tags = []
            for t in (row.get("tag") or "").split():
                if t in TAG_LABEL:
                    tags.append(TAG_LABEL[t])
            if tags:
                x["tags"] = tags
                stat["tags"] += 1

            frq = (row.get("frq") or "").strip()
            if frq.isdigit() and int(frq) > 0:
                x["frq"] = int(frq)

            # 英文释义：取第一行、剥掉词性前缀，作为例句缺失时的补充
            d = flat_line(row.get("definition"))
            if 12 <= len(d) <= 160:
                x["def"] = d
                stat["def"] += 1

        # 3) 例句
        cands = tatoeba.get(key)
        if cands:
            _, eng, chn = cands[0]
            if HAS_ZHCONV:
                chn = zhconv.convert(chn, "zh-cn")
            x["ex"] = [eng.strip(), chn.strip()]
            stat["example"] += 1
            if key in set(re.findall(r"[A-Za-z][A-Za-z'\-]*", eng.lower())):
                stat["example_exact"] += 1
            else:
                stat["example_via_form"] += 1

    # ---- 输出 ----
    # 繁简校验：用 zhconv 做往返比对，只有确实已经是简体的才算通过。
    # 注意不要用「繁体字字符集」去测——像「果」这种简繁同形的字会误判。
    if HAS_ZHCONV:
        not_simplified = [
            w["w"] for w in words
            if w.get("ex") and zhconv.convert(w["ex"][1], "zh-cn") != w["ex"][1]
        ]
        all_simplified = len(not_simplified) == 0
    else:
        not_simplified = []
        all_simplified = False

    payload["meta"]["enriched"] = True
    payload["meta"]["examples_all_simplified"] = all_simplified
    payload["meta"]["enrich_sources"] = {
        "phonetic_fallback": "skywind3000/ECDICT (MIT)",
        "examples": "Tatoeba via manythings.org/anki (CC-BY 2.0)",
    }
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))

    size_mb = os.path.getsize(OUT_PATH) / 1048576
    n = len(words)
    log("=" * 66)
    log(f"音标：ECDICT 补 {stat['phon_from_ecdict']} · 短语合成 {stat['phon_from_phrase']}")
    log(f"      仍缺失 {stat['phon_still_missing']}  (覆盖率 "
        f"{(n - stat['phon_still_missing']) / n * 100:.1f}%)")
    log(f"词形变化 {stat['forms']} · 考试标签 {stat['tags']} · 英文释义 {stat['def']}")
    log(f"例句 {stat['example']} / {n}  ({stat['example'] / n * 100:.1f}%)"
        f"  [精确 {stat['example_exact']} · 变化形/短语 {stat['example_via_form']}]")
    if HAS_ZHCONV:
        log(f"例句繁简校验: {'全部为简体' if all_simplified else f'仍有 {len(not_simplified)} 条未转换'}")
        if not_simplified:
            log(f"  样例: {not_simplified[:8]}")
    log(f"产出体积 {size_mb:.2f} MB -> {OUT_PATH}")
    log("=" * 66)
    return 0


if __name__ == "__main__":
    sys.exit(main())
