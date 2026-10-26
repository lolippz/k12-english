package com.k12english.fsrs;

import java.util.Date;

/**
 * 一次复习的日志，字段与 ts-fsrs 的 {@code ReviewLog} 一一对应。
 *
 * <p>{@code state} 是<b>复习前</b>的状态（与 ts-fsrs 一致）。
 */
public record FsrsReviewLog(
        int rating,
        int state,
        Date due,
        double stability,
        double difficulty,
        int elapsedDays,
        int lastElapsedDays,
        int scheduledDays,
        int learningSteps,
        Date review
) {

    public FsrsReviewLog {
        due = (due != null) ? new Date(due.getTime()) : null;
        review = (review != null) ? new Date(review.getTime()) : null;
    }
}
