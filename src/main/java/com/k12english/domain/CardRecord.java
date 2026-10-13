package com.k12english.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 记忆卡片。字段名与前端 {@code frontend/src/lib/srs.ts} 的 {@code CardRecord} 严格一致
 * （历史原因，部分是 snake_case、部分是 camelCase，这里按前端原样保留，
 * 避免两边对不上导致静默丢字段）。
 *
 * <p>FSRS 的调度计算在前端完成（ts-fsrs，纯函数），后端只负责持久化与聚合，
 * 所以这里只当作一张数据表来用，不含算法。
 *
 * @param w             单词
 * @param due           下次到期时间（epoch ms）
 * @param stability     FSRS 稳定度
 * @param difficulty    FSRS 难度
 * @param elapsedDays   距上次复习经过的天数
 * @param scheduledDays 上次安排的间隔天数
 * @param learningSteps 学习步长下标
 * @param reps          复习次数
 * @param lapses        遗忘次数
 * @param state         状态：0 未学 / 1 学习中 / 2 复习中 / 3 重学
 * @param lastReview    上次复习时间（epoch ms），可为 null
 * @param seen          累计作答次数（含重复）
 * @param wrong         累计答错次数
 * @param firstSeen     首次学习时间（epoch ms）
 */
public record CardRecord(
        String w,
        long due,
        double stability,
        double difficulty,
        @JsonProperty("elapsed_days") int elapsedDays,
        @JsonProperty("scheduled_days") int scheduledDays,
        @JsonProperty("learning_steps") int learningSteps,
        int reps,
        int lapses,
        int state,
        @JsonProperty("last_review") Long lastReview,
        int seen,
        int wrong,
        long firstSeen) {

    /** 掌握阈值：稳定度达到 3 周视为已掌握（与前端 MASTER_STABILITY 一致）。 */
    public static final double MASTER_STABILITY = 21;

    public boolean isDue(long now) {
        return due <= now;
    }

    /** 是否已掌握：处于复习状态且稳定度达标。 */
    @JsonIgnore
    public boolean isMastered() {
        return state == 2 && stability >= MASTER_STABILITY;
    }
}
