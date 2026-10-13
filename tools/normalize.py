# -*- coding: utf-8 -*-
"""
词表清洗 —— 数据流水线第 ② 步（位于 fetch_dicts.py 与 enrich.py 之间）

源词表（RealKai42/qwerty-learner）是给人看的教材附录，混进了大量非"单词"内容，
直接做成卡片会很难看。实测的六类脏数据：

  D1 不规则变化表被当成词条   'go went gone' / 'foot feet' / 'bad worse worst'
  D2 别名注记泄漏            'bike = bicycle' / 'liter/litre' / 'grey / gray'
  D3 尾部句点                'wear.' / 'house.' / 'first.' / 'in.'
  D4 双空格                  'play  chess' / 'South  Africa'
  D5 词性后缀                'could modal' / 'turning n'
  D6 拼写错误                'overnignt' / 'strorage' / 'disonest'

归一后会产生新的重复条目（例如 'wear.' 与既有 'wear'），脚本会合并它们：
学段取并集、册次保持首次出现顺序、中文释义去重拼接。

用法（必须用受管 venv 解释器）：
  C:/Users/35425/.workbuddy-ai/binaries/python/envs/default/Scripts/python.exe normalize.py --dry-run
  C:/Users/35425/.workbuddy-ai/binaries/python/envs/default/Scripts/python.exe normalize.py
"""
import argparse
import csv
import json
import os
import re
import shutil
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
IN_PATH = os.path.join(HERE, "out", "words.base.json")
ECDICT = os.path.join(HERE, "cache", "ecdict.csv")

STAGE_ORDER = ["primary", "junior", "senior"]

# 这些缩写虽然小写开头且以句点结尾，但是正确写法，不能剥掉句点
ABBREV = {"a.m.", "p.m.", "e.g.", "i.e.", "etc.", "vs.", "no.", "a.m", "p.m"}

# 词性缩写 —— 注意不要放 'art'（与名词 art 同形，会误伤 'tea art'）
POS_TOK = {"n", "v", "adj", "adv", "prep", "conj", "pron", "modal", "num", "int"}

# ECDICT 不记录情态动词之间的对应（shall/should、will/would 属于语法变化而非
# 词汇变化），性别对应词也不记录。这两类单独补一张表，否则 'shall should'
# 会残留成一张卡片。
SUPPLEMENT = {
    "shall": ["should"],
    "ambassador": ["ambassadress"],
}

# 人工确认过的拼写错误（key 必须与源词表逐字一致）
TYPO = {
    "overnignt": "overnight",
    "strorage": "storage",
    "disonest": "dishonest",
    "ajustment": "adjustment",
    "owenrship": "ownership",
    "pleasureable": "pleasurable",
    "agro-scientifc": "agro-scientific",
    "Montgommery": "Montgomery",
    "Raffesia arnoldii": "Rafflesia arnoldii",
    "staight away": "straight away",
    "softdrink": "soft drink",
    "gettogether": "get-together",
    "Guiness Book of World Records": "Guinness Book of World Records",
    "Jane Austin": "Jane Austen",
    "Sri chinmoy": "Sri Chinmoy",
}


def log(*a):
    print(*a, flush=True)


def load_exchange(heads):
    """从 ECDICT 取 exchange 字段，得到 head -> {变化形}。"""
    exch = {}
    csv.field_size_limit(10 ** 7)
    with open(ECDICT, encoding="utf-8", errors="replace", newline="") as f:
        for row in csv.DictReader(f):
            wd = (row.get("word") or "").strip().lower()
            if wd not in heads:
                continue
            forms = set()
            for seg in (row.get("exchange") or "").split("/"):
                if ":" in seg:
                    _, v = seg.split(":", 1)
                    v = v.strip().lower()
                    if v:
                        forms.add(v)
            if forms:
                exch[wd] = forms
    return exch


def normalize(raw, exch):
    """把一条原始词条归一成 (干净词头, 注记, 命中规则列表)。"""
    s = re.sub(r"\s+", " ", raw).strip()          # D4
    if not s:
        return None, None, []
    note = None
    rules = []
    if s != raw:
        rules.append("D4")

    # D6 拼写纠正（放在最前，后续规则基于正确拼写判断）
    if s in TYPO:
        s = TYPO[s]
        rules.append("D6")

    # D4b 省略号归一：'Either...or ......' -> 'Either...or...' / 'talk to ...' -> 'talk to...'
    s2 = re.sub(r"\.{4,}", "...", s)
    s2 = re.sub(r"\s+\.\.\.", "...", s2)
    if s2 != s:
        s = s2
        rules.append("D4b")

    # D4c 可选 be 前缀：'(be)strict with...' -> 'be strict with...'
    s2 = re.sub(r"^\(be\)\s*", "be ", s, flags=re.IGNORECASE)
    if s2 != s:
        s = s2
        rules.append("D4c")

    # D2 别名 "="：'bike = bicycle' / 'PhD=Doctor of Philosophy'
    if "=" in s:
        left, right = (t.strip() for t in s.split("=", 1))
        if left and right:
            toks = left.split()
            if len(toks) > 1:
                # 'does doesn't = does not' 这类：取首 token 作词头，其余进注记
                note = " / ".join(toks[1:] + [right])
                left = toks[0]
            else:
                note = right
            s = left
            rules.append("D2")

    # D2b 单 token 斜杠别名：'liter/litre' / 'grey / gray'
    # 左右必须都是单个 token，否则 'turn right/left' 会被错误拆分
    m = re.fullmatch(r"([A-Za-z][A-Za-z'\-]*)\s*/\s*([A-Za-z][A-Za-z'\-]*)", s)
    if m:
        s, note = m.group(1), m.group(2)
        rules.append("D2")

    # D2c 括号注记：只在括号内容是「首词的变化形」或「序数」时才剥离。
    # 否则 'run out (of)' / 'play a part (in)' 会被剥成 'run out' / 'play a part'，
    # 短语的介词被吃掉，词义就变了。
    m = re.fullmatch(r"(.+?)\s*\(([^)]+)\)", s)
    if m and not s.startswith("("):
        base, inner = m.group(1).strip(), m.group(2).strip()
        inner_l = inner.lower()
        head = base.split()[0].lower()
        is_form = inner_l in exch.get(head, set())
        is_ordinal = bool(re.fullmatch(r"\d+\s*(st|nd|rd|th)", inner_l))
        if base and (is_form or is_ordinal):
            s, note = base, inner
            rules.append("D2")

    toks = s.split()

    # D5 词性后缀：'could modal' / 'turning n'
    if len(toks) >= 2 and toks[-1].lower() in POS_TOK:
        s = " ".join(toks[:-1])
        toks = s.split()
        rules.append("D5")

    # D1 变化表：2~5 个纯字母 token，且首词之后每个都是首词的变化形
    if 2 <= len(toks) <= 5 and all(re.fullmatch(r"[A-Za-z]+", t) for t in toks):
        head = toks[0].lower()
        forms = exch.get(head, set())
        supp = {x.lower() for x in SUPPLEMENT.get(head, [])}
        rest = [t.lower() for t in toks[1:]]
        if rest and all(t == head or t in forms or t in supp for t in rest):
            # 能从 ECDICT 取到变化形的，enrich.py 的 forms 字段会展示，不必写 alt；
            # 只有靠补表才认出来的（情态动词、性别对应词），才写进 alt。
            covered = bool(forms) and all(t == head or t in forms for t in rest)
            if not covered and not note:
                note = " / ".join(toks)
            s = toks[0]
            rules.append("D1")

    # D3 尾部句点（只剥单个句点，且小写开头、非缩写）
    if (s.endswith(".") and not s.endswith("..") and s.lower() not in ABBREV
            and s[0].islower() and len(s) >= 3):
        s = s[:-1]
        rules.append("D3")

    s = re.sub(r"\s+", " ", s).strip()
    return (s or None), note, rules


def merge_into(store, key, entry):
    """把 entry 合并进 store[key]（保留首次出现位置）。"""
    if key not in store:
        store[key] = entry
        return
    tgt = store[key]
    for st in entry["stages"]:
        if st not in tgt["stages"]:
            tgt["stages"].append(st)
    tgt["stages"].sort(key=lambda x: STAGE_ORDER.index(x) if x in STAGE_ORDER else 99)
    for b in entry["books"]:
        if b not in tgt["books"]:
            tgt["books"].append(b)
    for t in entry["tr"]:
        if t not in tgt["tr"]:
            tgt["tr"].append(t)
    if not tgt.get("ph") and entry.get("ph"):
        tgt["ph"] = entry["ph"]
    if not tgt.get("phUk") and entry.get("phUk"):
        tgt["phUk"] = entry["phUk"]
    if entry.get("alt") and not tgt.get("alt"):
        tgt["alt"] = entry["alt"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只打印将要做的改动")
    args = ap.parse_args()

    log("=" * 70)
    log("词表清洗")
    log("=" * 70)

    with open(IN_PATH, encoding="utf-8") as f:
        payload = json.load(f)
    words = payload["words"]
    log(f"载入 {len(words)} 条  <- {os.path.relpath(IN_PATH, HERE)}")

    heads = {w["w"].split()[0].lower() for w in words if w["w"].split()}
    exch = load_exchange(heads)
    log(f"ECDICT 变化形索引 {len(exch)} 个首词（用于识别变化表）")
    log("")

    store = OrderedDict()
    stat = {}
    samples = {}

    for raw in words:
        clean, note, rules = normalize(raw["w"], exch)
        if not clean:
            log(f"  ! 归一后为空，已丢弃: {raw['w']!r}")
            continue
        for r in rules:
            stat[r] = stat.get(r, 0) + 1
        key = clean
        entry = {
            "w": clean,
            "ph": raw.get("ph", ""),
            "phUk": raw.get("phUk", ""),
            "tr": list(raw.get("tr", [])),
            "stages": list(raw.get("stages", [])),
            "books": list(raw.get("books", [])),
        }
        if note:
            # D2 的别名/全称，以及 D1 里靠补表才认出的变化形，都写进 alt。
            # 普通 D1（ECDICT 有 exchange）不写——enrich.py 的 forms 字段会显示，
            # 再写一遍是重复信息。
            entry["alt"] = note
        if rules and clean != raw["w"]:
            samples.setdefault("+".join(rules), []).append((raw["w"], clean, note))
        if key in store:
            stat["merged"] = stat.get("merged", 0) + 1
            samples.setdefault("merged", []).append((raw["w"], clean, note))
        merge_into(store, key, entry)

    out_words = list(store.values())

    log("--- 规则命中 ---")
    label = {
        "D1": "D1 变化表折叠",
        "D2": "D2 别名/括号注记",
        "D3": "D3 剥尾部句点",
        "D4": "D4 空白归一",
        "D4b": "D4b 省略号归一",
        "D4c": "D4c 可选 be 前缀",
        "D5": "D5 剥词性后缀",
        "D6": "D6 拼写纠正",
        "merged": "合并重复条目",
    }
    for k, v in sorted(stat.items(), key=lambda x: -x[1]):
        log(f"  {label.get(k, k):<16} {v}")

    log("")
    log("--- 改动样例 ---")
    for k in sorted(samples):
        if k == "merged":
            continue
        rows = samples[k]
        shown = rows if len(rows) <= 60 else rows[:12]
        log(f"  [{label.get(k, k)}] 共 {len(rows)} 条，显示 {len(shown)}：")
        for a, b, n in shown:
            tail = f"   注记: {n}" if n else ""
            log(f"      {a!r} -> {b!r}{tail}")

    if "merged" in samples:
        rows = samples["merged"]
        log(f"  [合并重复条目] 共 {len(rows)} 条，前 20：")
        for a, b, n in rows[:20]:
            log(f"      {a!r} -> 并入既有 {b!r}")

    log("")
    log(f"词条数 {len(words)} -> {len(out_words)}（净减 {len(words) - len(out_words)}）")

    # 剩余可疑条目：仍含非字母字符或拼写不在词典中的单 token
    log("")
    log("--- 仍需人工确认的条目 ---")
    weird = [w["w"] for w in out_words
             if re.search(r"[^A-Za-z0-9\-' ./&()]", w["w"]) or re.search(r"\d", w["w"])]
    log(f"  含特殊字符/数字 {len(weird)}: {weird[:25]}")

    if args.dry_run:
        log("")
        log("(--dry-run，未写入任何文件)")
        return 0

    if os.path.exists(IN_PATH) and not os.path.exists(IN_PATH.replace(".json", ".raw.json")):
        shutil.copy2(IN_PATH, IN_PATH.replace(".json", ".raw.json"))
        log(f"原始词表已备份 -> {os.path.relpath(IN_PATH.replace('.json', '.raw.json'), HERE)}")

    payload["words"] = out_words
    meta = payload.setdefault("meta", {})
    meta["total_raw"] = len(words)
    meta["total"] = len(out_words)
    meta["normalized"] = True
    meta["normalize_note"] = (
        "折叠变化表/别名/词性后缀，剥尾部句点，纠正已知拼写错误，并合并重复条目"
    )
    with open(IN_PATH, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))
    log(f"已写入 {os.path.relpath(IN_PATH, HERE)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
