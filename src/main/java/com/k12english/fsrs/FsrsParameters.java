package com.k12english.fsrs;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * FSRS-6.0 算法参数。
 *
 * <p>与 ts-fsrs 5.4.2 的默认参数保持一致，支持 17/19/21 维参数向量自动迁移。
 */
public final class FsrsParameters {

    public static final double FSRS6_DEFAULT_DECAY = 0.1542;
    public static final double FSRS5_DEFAULT_DECAY = 0.5;

    private static final double[] DEFAULT_W = {
        0.212, 1.2931, 2.3065, 8.2956, 6.4133,
        0.8334, 3.0194, 1e-3, 1.8722, 0.1666,
        0.796, 1.4835, 0.0614, 0.2629, 1.6483,
        0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
        FSRS6_DEFAULT_DECAY
    };

    private static final double S_MIN = 1e-3;
    private static final double S_MAX = 36500;
    private static final double INIT_S_MAX = 100;
    private static final double W17_W18_CEILING = 2.0;

    private static final List<double[]> CLIP_RANGES = Arrays.asList(
        new double[]{S_MIN, INIT_S_MAX},
        new double[]{S_MIN, INIT_S_MAX},
        new double[]{S_MIN, INIT_S_MAX},
        new double[]{S_MIN, INIT_S_MAX},
        new double[]{1, 10},
        new double[]{1e-3, 4},
        new double[]{1e-3, 4},
        new double[]{1e-3, 0.75},
        new double[]{0, 4.5},
        new double[]{0, 0.8},
        new double[]{1e-3, 3.5},
        new double[]{1e-3, 5},
        new double[]{1e-3, 0.25},
        new double[]{1e-3, 0.9},
        new double[]{0, 4},
        new double[]{0, 1},
        new double[]{1, 6},
        new double[]{0, W17_W18_CEILING},
        new double[]{0, W17_W18_CEILING},
        new double[]{0.01, 0.8},
        new double[]{0.1, 0.8}
    );

    public final double requestRetention;
    public final int maximumInterval;
    public final double[] w;
    public final boolean enableFuzz;
    public final boolean enableShortTerm;
    public final String[] learningSteps;
    public final String[] relearningSteps;

    private FsrsParameters(Builder b) {
        this.requestRetention = b.requestRetention;
        this.maximumInterval = b.maximumInterval;
        this.enableFuzz = b.enableFuzz;
        this.enableShortTerm = b.enableShortTerm;
        this.learningSteps = b.learningSteps;
        this.relearningSteps = b.relearningSteps;
        this.w = clipParameters(
            migrateParameters(b.w, b.relearningSteps.length, b.enableShortTerm),
            b.relearningSteps.length,
            b.enableShortTerm
        );
    }

    public static FsrsParameters defaults() {
        return new Builder().build();
    }

    /**
     * 与前端 {@code frontend/src/lib/srs.ts} 保持一致的参数：
     * retention 0.9、开启 fuzz、开启 short-term。
     */
    public static FsrsParameters appDefaults() {
        return new Builder().enableFuzz(true).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private double requestRetention = 0.9;
        private int maximumInterval = 36500;
        private double[] w;
        private boolean enableFuzz = false;
        private boolean enableShortTerm = true;
        private String[] learningSteps = new String[]{"1m", "10m"};
        private String[] relearningSteps = new String[]{"10m"};

        public Builder requestRetention(double v) {
            this.requestRetention = v;
            return this;
        }

        public Builder maximumInterval(int v) {
            this.maximumInterval = v;
            return this;
        }

        public Builder w(double[] v) {
            this.w = v;
            return this;
        }

        public Builder enableFuzz(boolean v) {
            this.enableFuzz = v;
            return this;
        }

        public Builder enableShortTerm(boolean v) {
            this.enableShortTerm = v;
            return this;
        }

        public Builder learningSteps(String[] v) {
            this.learningSteps = v;
            return this;
        }

        public Builder relearningSteps(String[] v) {
            this.relearningSteps = v;
            return this;
        }

        public FsrsParameters build() {
            return new FsrsParameters(this);
        }
    }

    /* ------------------------------------------------------------------ */

    private static double[] migrateParameters(double[] w, int numRelearningSteps, boolean enableShortTerm) {
        if (w == null) {
            return DEFAULT_W.clone();
        }
        switch (w.length) {
            case 21:
                return clipParameters(w.clone(), numRelearningSteps, enableShortTerm);
            case 19: {
                double[] out = clipParameters(w.clone(), numRelearningSteps, enableShortTerm);
                out = Arrays.copyOf(out, 21);
                out[19] = 0;
                out[20] = FSRS5_DEFAULT_DECAY;
                return out;
            }
            case 17: {
                double[] out = clipParameters(w.clone(), numRelearningSteps, enableShortTerm);
                out[4] = roundTo(out[5] * 2 + out[4], 8);
                out[5] = roundTo(Math.log(out[5] * 3 + 1) / 3, 8);
                out[6] = roundTo(out[6] + 0.5, 8);
                out = Arrays.copyOf(out, 21);
                out[17] = 0;
                out[18] = 0;
                out[19] = 0;
                out[20] = FSRS5_DEFAULT_DECAY;
                return out;
            }
            default:
                return DEFAULT_W.clone();
        }
    }

    private static double[] clipParameters(double[] w, int numRelearningSteps, boolean enableShortTerm) {
        double ceiling = W17_W18_CEILING;
        int nrs = Math.max(0, numRelearningSteps);
        if (nrs > 1) {
            double w11 = clamp(coerce(w, 11), CLIP_RANGES.get(11)[0], CLIP_RANGES.get(11)[1]);
            double w13 = clamp(coerce(w, 13), CLIP_RANGES.get(13)[0], CLIP_RANGES.get(13)[1]);
            double w14 = clamp(coerce(w, 14), CLIP_RANGES.get(14)[0], CLIP_RANGES.get(14)[1]);
            double value = -(Math.log(w11) + Math.log(Math.pow(2, w13) - 1) + w14 * 0.3) / nrs;
            ceiling = clamp(roundTo(Math.sqrt(Math.max(value, 0)), 8), 0.01, W17_W18_CEILING);
        }

        double[] out = new double[w.length];
        for (int i = 0; i < w.length; i++) {
            double[] range = CLIP_RANGES.get(i);
            double max = range[1];
            if (i == 17 || i == 18) {
                max = ceiling;
            }
            double v = coerce(w, i);
            if (i == 19) {
                double min = enableShortTerm ? 0.01 : 0;
                out[i] = clamp(v, min, max);
            } else {
                out[i] = clamp(v, range[0], max);
            }
        }
        return out;
    }

    /** 对应 JS {@code parameters[index] || 0}：NaN / Infinite / 越界 → 0。 */
    private static double coerce(double[] arr, int index) {
        if (index < 0 || index >= arr.length) return 0;
        double v = arr[index];
        return (Double.isFinite(v)) ? v : 0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.min(Math.max(v, min), max);
    }

    private static double roundTo(double num, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(num * factor) / factor;
    }
}
