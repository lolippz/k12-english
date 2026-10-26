package com.k12english.fsrs;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 调度器基类，对齐 ts-fsrs 的 {@code AbstractScheduler}。
 *
 * <p>一次 {@code next()} / {@code repeat()} 调用创建一个实例，实例内部持有
 * {@code last}（原始卡片，只读）与 {@code current}（工作副本，会被
 * {@code init()} 推进）。{@code next} 缓存保证 {@code repeat()} 预览四档时
 * 不会重复计算。
 */
abstract class AbstractFsrsScheduler {

    protected final FsrsAlgorithm algorithm;
    protected final FsrsParameters params;

    /** 复习前的原始卡片。 */
    protected final FsrsCard last;
    /** 工作副本：last_review / elapsed_days / reps 已在 init 里推进。 */
    protected FsrsCard current;

    protected final Date reviewTime;
    protected final int elapsedDays;
    protected final String seed;

    private final Map<Integer, FsrsReview> next = new HashMap<>();

    AbstractFsrsScheduler(FsrsCard card, Date now, FsrsAlgorithm algorithm) {
        this.algorithm = algorithm;
        this.params = algorithm.parameters();
        this.last = card;
        this.reviewTime = new Date(now.getTime());

        int interval = 0;
        if (card.state() != FsrsState.NEW && card.lastReview() != null) {
            interval = FsrsDateUtils.diffInCalendarDays(card.lastReview(), reviewTime);
        }
        this.elapsedDays = interval;
        this.current = card
            .withLastReview(reviewTime)
            .withElapsedDays(interval)
            .withReps(card.reps() + 1);

        // 对应 DefaultInitSeedStrategy：
        // `${review_time.getTime()}_${reps}_${difficulty * stability}`
        this.seed = reviewTime.getTime()
            + "_" + current.reps()
            + "_" + JsNumbers.toString(current.difficulty() * current.stability());
    }

    /** 按评分计算下一个状态。 */
    final FsrsReview review(int grade) {
        checkGrade(grade);
        FsrsReview cached = next.get(grade);
        if (cached != null) {
            return cached;
        }
        FsrsReview item = switch (last.state()) {
            case FsrsState.NEW -> newState(grade);
            case FsrsState.LEARNING, FsrsState.RELEARNING -> learningState(grade);
            case FsrsState.REVIEW -> reviewState(grade);
            default -> throw new IllegalArgumentException("Invalid state: " + last.state());
        };
        next.put(grade, item);
        return item;
    }

    protected final FsrsReview cached(int grade) {
        return next.get(grade);
    }

    protected final void cache(int grade, FsrsReview item) {
        next.put(grade, item);
    }

    protected abstract FsrsReview newState(int grade);

    protected abstract FsrsReview learningState(int grade);

    protected abstract FsrsReview reviewState(int grade);

    /** 对应 {@code AbstractScheduler.buildLog(rating)}。 */
    protected final FsrsReviewLog buildLog(int rating) {
        return new FsrsReviewLog(
            rating,
            current.state(),
            last.lastReview() != null ? last.lastReview() : last.due(),
            current.stability(),
            current.difficulty(),
            elapsedDays,
            last.elapsedDays(),
            current.scheduledDays(),
            current.learningSteps(),
            reviewTime
        );
    }

    private static void checkGrade(int grade) {
        if (grade < 1 || grade > 4) {
            throw new IllegalArgumentException("Invalid grade \"" + grade + "\", expected 1-4");
        }
    }
}
