package com.k12english.fsrs;

/**
 * FSRS-6.0 核心算法（纯函数，无状态）。
 *
 * <p>所有公式与 ts-fsrs 5.4.2 逐行对齐，用于后端独立计算卡片调度。
 *
 * <p>关于 {@link StrictMath}：ts-fsrs 跑在 V8 上，V8 的 {@code Math.pow/exp/log}
 * 来自 fdlibm；JDK 的 {@link StrictMath} 同样是 fdlibm 移植，而 {@link Math}
 * 在 x86 上会走硬件指令、末位可能不同。这里全部改用 {@code StrictMath}，
 * 让「稳定度 → 间隔」这条链路在浮点层面与前端逐位一致。
 *
 * <p>关于随机种子：本类刻意<b>不持有</b>种子状态，fuzz 所需的 seed 由调度器
 * 显式传入。这样同一个 {@code FsrsAlgorithm} 实例可以被多线程共享（多用户场景），
 * 不会出现 A 用户的种子被 B 用户覆盖的竞态。
 */
public final class FsrsAlgorithm {

    private static final double S_MIN = 1e-3;
    private static final double S_MAX = 36500;

    private final FsrsParameters param;
    private final double intervalModifier;
    private final double factor;
    private final double decay;

    public FsrsAlgorithm(FsrsParameters param) {
        this.param = param;
        double[] computed = computeDecayFactor(param.w[20]);
        this.decay = computed[0];
        this.factor = computed[1];
        this.intervalModifier = calculateIntervalModifier(param.requestRetention);
    }

    /* ---------------- 参数与工具 ---------------- */

    private static double[] computeDecayFactor(double decayValue) {
        double decay = -decayValue;
        double factor = StrictMath.exp(StrictMath.pow(decay, -1) * StrictMath.log(0.9)) - 1;
        return new double[]{decay, roundTo(factor, 8)};
    }

    private double calculateIntervalModifier(double requestRetention) {
        if (requestRetention <= 0 || requestRetention > 1) {
            throw new IllegalArgumentException("Requested retention rate should be in the range (0,1]");
        }
        return roundTo((StrictMath.pow(requestRetention, 1 / decay) - 1) / factor, 8);
    }

    private static double clamp(double v, double min, double max) {
        return Math.min(Math.max(v, min), max);
    }

    static double roundTo(double num, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(num * f) / f;
    }

    public FsrsParameters parameters() {
        return param;
    }

    public double intervalModifier() {
        return intervalModifier;
    }

    /* ---------------- 遗忘曲线 ---------------- */

    /**
     * R(t, S) = (1 + FACTOR * t / S) ^ DECAY
     */
    public double forgettingCurve(double elapsedDays, double stability) {
        return roundTo(StrictMath.pow(1 + factor * elapsedDays / stability, decay), 8);
    }

    /* ---------------- 初始值 ---------------- */

    /** S0(G) = max(w[G-1], 0.1) */
    public double initStability(int grade) {
        return Math.max(param.w[grade - 1], 0.1);
    }

    /** D0(G) = w4 - e^((G-1)*w5) + 1 */
    public double initDifficulty(int grade) {
        double d = param.w[4] - StrictMath.exp((grade - 1) * param.w[5]) + 1;
        return roundTo(d, 8);
    }

    /* ---------------- 难度更新 ---------------- */

    /** linear_damping(delta_d, old_d) = delta_d * (10 - old_d) / 9 */
    public double linearDamping(double deltaD, double oldD) {
        return roundTo(deltaD * (10 - oldD) / 9, 8);
    }

    /** mean_reversion(init, current) = w7 * init + (1 - w7) * current */
    public double meanReversion(double init, double current) {
        return roundTo(param.w[7] * init + (1 - param.w[7]) * current, 8);
    }

    /** next_difficulty(d, g) */
    public double nextDifficulty(double d, int grade) {
        double deltaD = -param.w[6] * (grade - 3);
        double nextD = d + linearDamping(deltaD, d);
        return clamp(meanReversion(initDifficulty(FsrsState.RATING_EASY), nextD), 1, 10);
    }

    /* ---------------- 稳定度更新 ---------------- */

    /** 回忆后的稳定度 S'r */
    public double nextRecallStability(double d, double s, double r, int grade) {
        double[] w = param.w;
        double hardPenalty = (grade == FsrsState.RATING_HARD) ? w[15] : 1;
        double easyBound = (grade == FsrsState.RATING_EASY) ? w[16] : 1;
        return roundTo(
            clamp(
                s * (1 + StrictMath.exp(w[8]) * (11 - d) * StrictMath.pow(s, -w[9])
                    * (StrictMath.exp((1 - r) * w[10]) - 1) * hardPenalty * easyBound),
                S_MIN, S_MAX
            ), 8
        );
    }

    /** 遗忘后的稳定度 S'f */
    public double nextForgetStability(double d, double s, double r) {
        double[] w = param.w;
        return roundTo(
            clamp(
                w[11] * StrictMath.pow(d, -w[12]) * (StrictMath.pow(s + 1, w[13]) - 1)
                    * StrictMath.exp((1 - r) * w[14]),
                S_MIN, S_MAX
            ), 8
        );
    }

    /**
     * 短时记忆稳定度 S's。
     *
     * <p>注意 {@code maskedSinc} 的阈值是 {@code g >= Rating.Hard}（即 2），
     * 不是 3 —— 对应 ts-fsrs 第 924 行
     * {@code const maskedSinc = g >= Rating.Hard ? Math.max(sinc, 1) : sinc}。
     */
    public double nextShortTermStability(double s, int grade) {
        double[] w = param.w;
        double sinc = StrictMath.pow(s, -w[19]) * StrictMath.exp(w[17] * (grade - 3 + w[18]));
        double maskedSinc = (grade >= FsrsState.RATING_HARD) ? Math.max(sinc, 1) : sinc;
        return roundTo(clamp(s * maskedSinc, S_MIN, S_MAX), 8);
    }

    /* ---------------- 间隔计算 ---------------- */

    /** 应用 fuzz（随机扰动） */
    public int applyFuzz(double ivl, double elapsedDays, String seed) {
        if (!param.enableFuzz || ivl < 2.5) {
            return (int) Math.round(ivl);
        }
        Alea generator = new Alea(seed);
        double fuzzFactor = generator.next();
        FuzzRange range = getFuzzRange(ivl, elapsedDays, param.maximumInterval);
        return (int) Math.floor(fuzzFactor * (range.maxIvl - range.minIvl + 1) + range.minIvl);
    }

    /** 下次间隔（天） */
    public int nextInterval(double s, double elapsedDays, String seed) {
        double newInterval = Math.min(
            Math.max(1, Math.round(s * intervalModifier)),
            param.maximumInterval
        );
        return applyFuzz(newInterval, elapsedDays, seed);
    }

    /* ---------------- 状态转移 ---------------- */

    /** 统一的状态转移入口 */
    public MemoryState nextState(MemoryState state, double t, int grade, Double retrievability) {
        if (t < 0) {
            throw new IllegalArgumentException("Invalid delta_t \"" + t + "\"");
        }
        if (grade < 0 || grade > 4) {
            throw new IllegalArgumentException("Invalid grade \"" + grade + "\"");
        }
        double d = (state != null) ? state.difficulty : 0;
        double s = (state != null) ? state.stability : 0;

        if (d == 0 && s == 0) {
            return new MemoryState(clamp(initDifficulty(grade), 1, 10), initStability(grade));
        }
        if (grade == FsrsState.RATING_MANUAL) {
            return new MemoryState(d, s);
        }
        if (d < 1 || s < S_MIN) {
            throw new IllegalArgumentException("Invalid memory state { difficulty: " + d + ", stability: " + s + " }");
        }

        double r = (retrievability != null) ? retrievability : forgettingCurve(t, s);
        double newS;
        if (t == 0 && param.enableShortTerm) {
            newS = nextShortTermStability(s, grade);
        } else if (grade == FsrsState.RATING_AGAIN) {
            double sAfterFail = nextForgetStability(d, s, r);
            double w17 = param.enableShortTerm ? param.w[17] : 0;
            double w18 = param.enableShortTerm ? param.w[18] : 0;
            double nextSMin = s / StrictMath.exp(w17 * w18);
            newS = clamp(roundTo(nextSMin, 8), S_MIN, sAfterFail);
        } else {
            newS = nextRecallStability(d, s, r, grade);
        }
        double newD = nextDifficulty(d, grade);
        return new MemoryState(newD, newS);
    }

    public MemoryState nextState(MemoryState state, double t, int grade) {
        return nextState(state, t, grade, null);
    }

    /* ---------------- 内部工具 ---------------- */

    private static FuzzRange getFuzzRange(double interval, double elapsedDays, int maximumInterval) {
        double[][] FUZZ_RANGES = {
            {2.5, 7, 0.15},
            {7, 20, 0.1},
            {20, Double.POSITIVE_INFINITY, 0.05}
        };
        double delta = 1;
        for (double[] range : FUZZ_RANGES) {
            delta += range[2] * Math.max(Math.min(interval, range[1]) - range[0], 0);
        }
        interval = Math.min(interval, maximumInterval);
        int minIvl = Math.max(2, (int) Math.round(interval - delta));
        int maxIvl = Math.min((int) Math.round(interval + delta), maximumInterval);
        if (interval > elapsedDays) {
            minIvl = Math.max(minIvl, (int) elapsedDays + 1);
        }
        minIvl = Math.min(minIvl, maxIvl);
        return new FuzzRange(minIvl, maxIvl);
    }

    private record FuzzRange(int minIvl, int maxIvl) {
    }

    public record MemoryState(double difficulty, double stability) {
    }

    /* ---------------- Alea PRNG（与 ts-fsrs 逐位一致） ---------------- */

    /**
     * Alea 伪随机数发生器。
     *
     * <p>移植自 seedrandom / ts-fsrs 的 {@code Alea}，刻意保留 JS 的 Number 语义：
     * {@code Mash} 里的 {@code n} 必须用 {@code double} 而非 {@code long}，
     * 因为 JS 版本在每轮末尾执行 {@code n += h * 4294967296}，会把小数部分留在
     * {@code n} 里带进下一轮。若截断成整数，fuzz 结果会与前端不一致。
     */
    private static final class Alea {
        private double c = 1;
        private double s0;
        private double s1;
        private double s2;

        Alea(String seed) {
            Mash mash = new Mash();
            this.s0 = mash.mash(" ");
            this.s1 = mash.mash(" ");
            this.s2 = mash.mash(" ");
            if (seed == null) seed = String.valueOf(System.currentTimeMillis());
            this.s0 -= mash.mash(seed);
            if (this.s0 < 0) this.s0 += 1;
            this.s1 -= mash.mash(seed);
            if (this.s1 < 0) this.s1 += 1;
            this.s2 -= mash.mash(seed);
            if (this.s2 < 0) this.s2 += 1;
        }

        double next() {
            double t = 2091639 * this.s0 + this.c * 23283064365386963e-26;
            this.s0 = this.s1;
            this.s1 = this.s2;
            this.c = (int) t;
            this.s2 = t - this.c;
            return this.s2;
        }
    }

    /**
     * Mash 哈希。状态 {@code n} 在同一个实例的多次调用间累积（与 JS 闭包一致），
     * {@code Alea} 构造时先用同一个实例喂三次 {@code " "} 再喂三次 seed。
     */
    private static final class Mash {
        private double n = 4022871197.0;

        double mash(String data) {
            for (int i = 0; i < data.length(); i++) {
                n += data.charAt(i);
                double h = 0.02519603282416938 * n;
                n = toUint32(h);
                h -= n;
                h *= n;
                n = toUint32(h);
                h -= n;
                n += h * 4294967296.0;
            }
            return toUint32(n) * 23283064365386963e-26;
        }
    }

    /** JS 的 {@code value >>> 0}：向零截断后对 2^32 取模。 */
    private static double toUint32(double v) {
        double t = (v < 0) ? Math.ceil(v) : Math.floor(v);
        t = t % 4294967296.0;
        if (t < 0) t += 4294967296.0;
        return t;
    }
}
