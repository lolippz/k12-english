package com.k12english.fsrs;

/**
 * FSRS 状态与评分枚举常量。
 *
 * <p>数值与 ts-fsrs 完全一致，便于前后端对齐。
 */
public final class FsrsState {

    private FsrsState() {
    }

    public static final int NEW = 0;
    public static final int LEARNING = 1;
    public static final int REVIEW = 2;
    public static final int RELEARNING = 3;

    public static final int RATING_MANUAL = 0;
    public static final int RATING_AGAIN = 1;
    public static final int RATING_HARD = 2;
    public static final int RATING_GOOD = 3;
    public static final int RATING_EASY = 4;
}
