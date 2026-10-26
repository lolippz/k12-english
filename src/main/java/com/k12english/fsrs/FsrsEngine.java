package com.k12english.fsrs;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FSRS 顶层引擎，对应 ts-fsrs 的 {@code FSRS} 类。
 *
 * <p>前端配置与 {@code frontend/src/lib/srs.ts} 对齐：
 * <pre>{@code
 *   request_retention: 0.9
 *   enable_fuzz:       true
 *   enable_short_term: true
 * }</pre>
 *
 * <p>本类无内部可变状态，可作为 Spring Bean 单例使用。
 */
public final class FsrsEngine {

    private final FsrsParameters params;
    private final FsrsAlgorithm algorithm;

    public FsrsEngine(FsrsParameters params) {
        this.params = params;
        this.algorithm = new FsrsAlgorithm(params);
    }

    /** 提交一次作答，返回 {card, log}。 */
    public FsrsReview next(FsrsCard card, Date now, int grade) {
        if (grade == FsrsState.RATING_MANUAL) {
            throw new IllegalArgumentException("Cannot review a manual rating");
        }
        return createScheduler(card, now).review(grade);
    }

    /** 预览四档评分对应的下次复习卡片与日志。 */
    public Map<Integer, FsrsReview> repeat(FsrsCard card, Date now) {
        AbstractFsrsScheduler sched = createScheduler(card, now);
        Map<Integer, FsrsReview> out = new LinkedHashMap<>();
        out.put(FsrsState.RATING_AGAIN, sched.review(FsrsState.RATING_AGAIN));
        out.put(FsrsState.RATING_HARD,  sched.review(FsrsState.RATING_HARD));
        out.put(FsrsState.RATING_GOOD,  sched.review(FsrsState.RATING_GOOD));
        out.put(FsrsState.RATING_EASY,  sched.review(FsrsState.RATING_EASY));
        return out;
    }

    /** 获取当前记忆保持率（0~1）。 */
    public double retrievability(FsrsCard card, Date now) {
        if (card.state() == FsrsState.NEW || card.lastReview() == null) {
            return 0;
        }
        long t = Math.max(FsrsDateUtils.diffInDaysFloor(now, card.lastReview()), 0);
        return algorithm.forgettingCurve(t, FsrsAlgorithm.roundTo(card.stability(), 8));
    }

    /** 创建一张空卡片（state=New），对应 ts-fsrs 的 {@code createEmptyCard}。 */
    public static FsrsCard emptyCard(Date now) {
        return FsrsCard.builder().due(now).build();
    }

    // ------------------------------------------------------------------

    private AbstractFsrsScheduler createScheduler(FsrsCard card, Date now) {
        return params.enableShortTerm
            ? new BasicFsrsScheduler(card, now, algorithm)
            : new LongTermFsrsScheduler(card, now, algorithm);
    }
}
