#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
发音音频生成脚本
================
用 Piper（离线神经网络 TTS）为每个词条合成发音，编码为 32kbps 单声道 MP3，
输出到 frontend/public/audio/，随应用一起打包，完全离线可用。

为什么不用 edge-tts：本机系统时间与真实时间存在偏差，微软的 Sec-MS-GEC
校验令牌会被拒（403），且该接口依赖在线服务。Piper 是纯离线方案。

依赖（已装入受管 venv）:
    piper-tts, lameenc
语音模型:
    tools/cache/voices/en_US-lessac-medium.onnx  (约 63 MB)

用法（必须用受管 venv 解释器）:
    C:/Users/35425/.workbuddy-ai/binaries/python/envs/default/Scripts/python.exe gen_audio.py
    C:/Users/35425/.workbuddy-ai/binaries/python/envs/default/Scripts/python.exe gen_audio.py --prune

--prune 会删掉不再被任何词条引用的 mp3。词表清洗（normalize.py）改过词头之后，
旧词头（如 'wear.' 对应的 wear_.mp3）会留下孤儿文件，跑一次即可清干净。
"""

import argparse
import json
import os
import re
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
WORDS = os.path.abspath(os.path.join(HERE, "..", "data", "words.json"))
VOICE_DIR = os.path.join(HERE, "cache", "voices")
VOICE = os.path.join(VOICE_DIR, "en_US-lessac-medium.onnx")
OUT_DIR = os.path.abspath(os.path.join(HERE, "..", "frontend", "public", "audio"))

BITRATE = 32
QUALITY = 5


def log(m):
    print(m, flush=True)


def slugify(w):
    """把词条转成安全的文件名：小写、非字母数字压成下划线。"""
    s = re.sub(r"[^a-z0-9]+", "_", w.lower()).strip("_")
    return s or "x"


def build_slugs(words):
    """生成唯一文件名，返回 {词: 文件名}。冲突时追加序号。"""
    used = {}
    out = {}
    for i, w in enumerate(words):
        s = slugify(w["w"])
        if s in used:
            s = f"{s}_{i}"
        used[s] = w["w"]
        out[w["w"]] = s
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--prune", action="store_true",
                    help="删除不再被词表引用的 mp3（词表清洗后清理孤儿文件）")
    args = ap.parse_args()

    log("=" * 66)
    log("发音音频生成（Piper 离线 TTS）")
    log("=" * 66)

    try:
        from piper import PiperVoice
    except ImportError:
        log("! 未安装 piper-tts。请用受管 venv 解释器运行本脚本。")
        return 1
    try:
        import lameenc
    except ImportError:
        log("! 未安装 lameenc。请用受管 venv 解释器运行本脚本。")
        return 1

    if not os.path.exists(VOICE):
        log(f"! 未找到语音模型 {VOICE}")
        return 1

    with open(WORDS, encoding="utf-8") as f:
        payload = json.load(f)
    words = payload["words"]
    log(f"词条 {len(words)} 条")

    slugs = build_slugs(words)
    n_slug_conflict = len(slugs) - len(set(slugs.values()))
    log(f"文件名冲突 {n_slug_conflict} 个（已追加序号解决）")

    os.makedirs(OUT_DIR, exist_ok=True)

    log("加载语音模型…")
    t0 = time.time()
    voice = PiperVoice.load(VOICE, config_path=VOICE + ".json")
    log(f"  完成 ({time.time() - t0:.1f}s)，采样率 {voice.config.sample_rate}")

    sr = voice.config.sample_rate
    done = 0
    skipped = 0
    failed = []
    total_bytes = 0
    t_start = time.time()

    for i, w in enumerate(words, 1):
        name = slugs[w["w"]]
        path = os.path.join(OUT_DIR, name + ".mp3")

        # 断点续跑：已存在且非空就跳过
        if os.path.exists(path) and os.path.getsize(path) > 200:
            skipped += 1
            total_bytes += os.path.getsize(path)
            continue

        try:
            pcm = b"".join(c.audio_int16_bytes for c in voice.synthesize(w["w"]))
            if not pcm:
                raise RuntimeError("合成结果为空")
            enc = lameenc.Encoder()
            enc.set_bit_rate(BITRATE)
            enc.set_in_sample_rate(sr)
            enc.set_channels(1)
            enc.set_quality(QUALITY)
            mp3 = enc.encode(pcm) + enc.flush()
            with open(path, "wb") as f:
                f.write(mp3)
            total_bytes += len(mp3)
            done += 1
        except Exception as e:
            failed.append((w["w"], f"{type(e).__name__}: {e}"))

        if i % 250 == 0 or i == len(words):
            el = time.time() - t_start
            rate = (done + skipped) / el if el else 0
            eta = (len(words) - i) / rate if rate else 0
            log(f"  [{i:>5}/{len(words)}] 新生成 {done} · 跳过 {skipped} · "
                f"失败 {len(failed)} · {rate:.0f} 词/秒 · 剩余约 {eta / 60:.1f} 分钟")

    # 把文件名写回词库（供前端查表），仅在有冲突或 slug 不等于默认推导时才有必要，
    # 但统一写入更稳妥，体积代价约 100 KB。
    for w in words:
        w["a"] = slugs[w["w"]]
    payload["meta"]["audio"] = {
        "engine": "Piper (en_US-lessac-medium)",
        "format": f"mp3 {BITRATE}kbps mono {sr}Hz",
        "count": len(words) - len(failed),
    }
    with open(WORDS, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, separators=(",", ":"))

    if args.prune:
        referenced = {f"{s}.mp3" for s in slugs.values()}
        orphans = [f for f in os.listdir(OUT_DIR)
                   if f.endswith(".mp3") and f not in referenced]
        freed = 0
        for f in orphans:
            p = os.path.join(OUT_DIR, f)
            freed += os.path.getsize(p)
            os.remove(p)
        log(f"清理孤儿音频 {len(orphans)} 个（{freed / 1048576:.1f} MB）")
        total_bytes -= freed

    log("=" * 66)
    log(f"生成 {done} · 跳过 {skipped} · 失败 {len(failed)}")
    if failed:
        log(f"  失败样例: {failed[:5]}")
    log(f"音频总量 {total_bytes / 1048576:.1f} MB -> {OUT_DIR}")
    log(f"总耗时 {(time.time() - t_start) / 60:.1f} 分钟")
    log("=" * 66)
    return 0 if not failed else 1


if __name__ == "__main__":
    sys.exit(main())
