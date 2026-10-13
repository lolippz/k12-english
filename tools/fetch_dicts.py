#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
K12 英语词库准备脚本
====================
从 qwerty-learner 开源词库拉取人教版小学 / 初中 / 高中全册词表 + 考纲词表，
按学段聚合、去重合并，输出应用可直接内联使用的 words.json。

数据来源: RealKai42/qwerty-learner (GPL-3.0)
镜像通道: fastly.jsdelivr.net (主) / gitcode.com (备)

用法:
    python fetch_dicts.py
"""

import json
import os
import sys
import time
import urllib.request
import urllib.parse
import urllib.error

MIRRORS = [
    "https://fastly.jsdelivr.net/gh/Kaiyiwing/qwerty-learner@master/public/dicts/",
    "https://gitcode.com/gh_mirrors/qw/qwerty-learner/raw/master/public/dicts/",
]

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "out")
CACHE_DIR = os.path.join(HERE, "cache")

# (文件名, 学段, 册次标签)
CATALOG = [
    # ---- 小学 · 人教版 PEP 三年级起点 ----
    ("PEPXiaoXue3_1_T.json", "primary", "PEP小学三年级上"),
    ("PEPXiaoXue3_2_T.json", "primary", "PEP小学三年级下"),
    ("PEPXiaoXue4_1_T.json", "primary", "PEP小学四年级上"),
    ("PEPXiaoXue4_2_T.json", "primary", "PEP小学四年级下"),
    ("PEPXiaoXue5_1_T.json", "primary", "PEP小学五年级上"),
    ("PEPXiaoXue5_2_T.json", "primary", "PEP小学五年级下"),
    ("PEPXiaoXue6_1_T.json", "primary", "PEP小学六年级上"),
    ("PEPXiaoXue6_2_T.json", "primary", "PEP小学六年级下"),
    # ---- 小学 · 人教版新起点（一年级起点）----
    ("PEP_SL_XiaoXue1_1_t.json", "primary", "新起点一年级上"),
    ("PEP_SL_XiaoXue1_2_t.json", "primary", "新起点一年级下"),
    ("PEP_SL_XiaoXue2_1_t.json", "primary", "新起点二年级上"),
    ("PEP_SL_XiaoXue2_2_t.json", "primary", "新起点二年级下"),
    ("PEP_SL_XiaoXue3_1_t.json", "primary", "新起点三年级上"),
    ("PEP_SL_XiaoXue3_2_t.json", "primary", "新起点三年级下"),
    ("PEP_SL_XiaoXue4_1_t.json", "primary", "新起点四年级上"),
    ("PEP_SL_XiaoXue4_2_t.json", "primary", "新起点四年级下"),
    ("PEP_SL_XiaoXue5_1_t.json", "primary", "新起点五年级上"),
    ("PEP_SL_XiaoXue5_2_t.json", "primary", "新起点五年级下"),
    ("PEP_SL_XiaoXue6_1_t.json", "primary", "新起点六年级上"),
    ("PEP_SL_XiaoXue6_2_t.json", "primary", "新起点六年级下"),
    # ---- 初中 · 人教版 ----
    ("PEPChuZhong7_1_T.json", "junior", "人教七年级上"),
    ("PEPChuZhong7_2_T.json", "junior", "人教七年级下"),
    ("PEPChuZhong8_1_T.json", "junior", "人教八年级上"),
    ("PEPChuZhong8_2_T.json", "junior", "人教八年级下"),
    ("PEPChuZhong9_1_T.json", "junior", "人教九年级全册"),
    ("ZhongKaoHeXin.json", "junior", "中考核心词"),
    # ---- 高中 · 人教版 ----
    ("PEPGaoZhong_1_T.json", "senior", "人教必修1"),
    ("PEPGaoZhong_2_T.json", "senior", "人教必修2"),
    ("PEPGaoZhong_3_T.json", "senior", "人教必修3"),
    ("PEPGaoZhong_4_T.json", "senior", "人教必修4"),
    ("PEPGaoZhong_5_T.json", "senior", "人教必修5"),
    ("PEPGaoZhong_6_T.json", "senior", "人教选修6"),
    ("PEPGaoZhong_7_T.json", "senior", "人教选修7"),
    ("PEPGaoZhong_8_T.json", "senior", "人教选修8"),
    ("PEPGaoZhong_9_T.json", "senior", "人教选修9"),
    ("PEPGaoZhong_10_T.json", "senior", "人教选修10"),
    ("PEPGaoZhong_11_T.json", "senior", "人教选修11"),
    ("GaoKao_3500.json", "senior", "高考3500"),
    ("GaoKaoZhenTiHeXinGaoPin.json", "senior", "高考真题高频"),
    ("gaokao-yuedu-gaopin.json", "senior", "高考阅读高频"),
]

STAGE_ORDER = {"primary": 0, "junior": 1, "senior": 2}


def log(msg):
    print(msg, flush=True)


def download(fname, use_cache=True):
    """按镜像顺序尝试下载，返回解析后的 list；全部失败返回 None。"""
    os.makedirs(CACHE_DIR, exist_ok=True)
    cache_path = os.path.join(CACHE_DIR, fname)

    if use_cache and os.path.exists(cache_path) and os.path.getsize(cache_path) > 100:
        try:
            with open(cache_path, encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass

    for base in MIRRORS:
        url = base + urllib.parse.quote(fname)
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=30) as r:
                raw = r.read().decode("utf-8")
            data = json.loads(raw)
            if isinstance(data, list) and data:
                with open(cache_path, "w", encoding="utf-8") as f:
                    f.write(raw)
                return data
        except Exception as e:
            log(f"      [{base.split('/')[2]}] 失败: {type(e).__name__}")
    return None


def clean_trans(trans):
    """释义清洗：去空、去重、保序。"""
    out = []
    for t in trans or []:
        if not isinstance(t, str):
            continue
        t = t.strip().replace("\r", " ").replace("\n", " ")
        while "  " in t:
            t = t.replace("  ", " ")
        if t and t not in out:
            out.append(t)
    return out


def main():
    log("=" * 66)
    log("K12 英语词库准备")
    log("=" * 66)

    words = {}          # key: 小写单词 -> 聚合记录
    missing = []
    fetched = 0

    for idx, (fname, stage, label) in enumerate(CATALOG, 1):
        log(f"[{idx:>2}/{len(CATALOG)}] {label:<16} {fname}")
        data = download(fname)
        if data is None:
            missing.append((fname, label))
            continue
        fetched += 1

        for item in data:
            name = (item.get("name") or "").strip()
            if not name:
                continue
            # 词条可能是短语，保留原样；索引键用规范化小写
            key = name.lower().strip()
            if len(key) > 60:
                continue

            rec = words.get(key)
            if rec is None:
                rec = {
                    "w": name,
                    "ph": (item.get("usphone") or item.get("ukphone") or "").strip(),
                    "phUk": (item.get("ukphone") or "").strip(),
                    "tr": [],
                    "stages": [],
                    "books": [],
                }
                words[key] = rec
            else:
                # 补全音标
                if not rec["ph"]:
                    rec["ph"] = (item.get("usphone") or item.get("ukphone") or "").strip()
                if not rec["phUk"]:
                    rec["phUk"] = (item.get("ukphone") or "").strip()

            for t in clean_trans(item.get("trans")):
                if t not in rec["tr"]:
                    rec["tr"].append(t)
            if stage not in rec["stages"]:
                rec["stages"].append(stage)
            if label not in rec["books"]:
                rec["books"].append(label)

    # 保持"首次出现顺序"输出：CATALOG 已按 小学 -> 初中 -> 高中 排列，
    # 同一册内保持原始词表顺序，使学习顺序与教材进度一致。
    # 不再按词长排序——否则第一课会变成 a / I / at 这类无意义顺序。
    out = list(words.values())
    for rec in out:
        rec["stages"].sort(key=lambda s: STAGE_ORDER.get(s, 9))
        rec["tr"] = rec["tr"][:4]          # 释义最多保留 4 条，控制体积
        rec["books"] = rec["books"][:6]

    os.makedirs(OUT_DIR, exist_ok=True)
    payload = {
        "meta": {
            "source": "RealKai42/qwerty-learner (GPL-3.0)",
            "generated": time.strftime("%Y-%m-%d %H:%M:%S"),
            "files": fetched,
            "total": len(out),
        },
        "words": out,
    }
    out_path = os.path.join(OUT_DIR, "words.base.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))

    size_kb = os.path.getsize(out_path) / 1024
    stats = {}
    for r in out:
        for s in r["stages"]:
            stats[s] = stats.get(s, 0) + 1

    log("=" * 66)
    log(f"成功拉取文件 : {fetched}/{len(CATALOG)}")
    if missing:
        log(f"失败文件     : {', '.join(m[1] for m in missing)}")
    log(f"词条总数     : {len(out)}")
    for s in ("primary", "junior", "senior"):
        log(f"  {s:<8}: {stats.get(s, 0)}")
    log(f"产出体积     : {size_kb:.0f} KB  ->  {out_path}")
    log("=" * 66)
    return 0 if not missing else 1


if __name__ == "__main__":
    sys.exit(main())
