package com.k12english.fsrs;

/**
 * LongTermScheduler（关闭短时记忆时走这条），对齐 ts-fsrs 的 {@code LongTermScheduler}。
 */
final class LongTermFsrsScheduler extends AbstractFsrsScheduler {

    LongTermFsrsScheduler(FsrsCard card, java.util.Date now, FsrsAlgorithm algorithm) {
        super(card, now, algorithm);
    }

    @Override
    protected FsrsReview newState(int grade) {
        FsrsReview cached = cached(grade);
        if (cached != null) {
            return cached;
        }

        current = current.withScheduledDays(0).withElapsedDays(0);
        int firstInterval = 0;

        FsrsCard again = nextDs(firstInterval, FsrsState.RATING_AGAIN, null);
        FsrsCard hard  = nextDs(firstInterval, FsrsState.RATING_HARD,  null);
        FsrsCard good  = nextDs(firstInterval, FsrsState.RATING_GOOD,  null);
        FsrsCard easy  = nextDs(firstInterval, FsrsState.RATING_EASY,  null);

        again = applyInterval(again, algorithm.nextInterval(again.stability(), firstInterval, seed));
        hard  = applyInterval(hard,  algorithm.nextInterval(hard.stability(),  firstInterval, seed));
        good  = applyInterval(good,  algorithm.nextInterval(good.stability(),  firstInterval, seed));
        easy  = applyInterval(easy,  algorithm.nextInterval(easy.stability(),  firstInterval, seed));

        // 对应 JS next_interval 的互相约束（顺序至关重要）
        int a = again.scheduledDays();
        int h = hard .scheduledDays();
        int g = good .scheduledDays();
        int e = easy .scheduledDays();
        a = Math.min(a, h);
        h = Math.max(h, a + 1);
        g = Math.max(g, h + 1);
        e = Math.max(e, g + 1);
        again = applyInterval(again, a);
        hard  = applyInterval(hard,  h);
        good  = applyInterval(good,  g);
        easy  = applyInterval(easy,  e);

        again = again.withState(FsrsState.REVIEW).withLearningSteps(0);
        hard  = hard .withState(FsrsState.REVIEW).withLearningSteps(0);
        good  = good .withState(FsrsState.REVIEW).withLearningSteps(0);
        easy  = easy .withState(FsrsState.REVIEW).withLearningSteps(0);

        updateNext(again, hard, good, easy);
        return cached(grade);
    }

    @Override
    protected FsrsReview learningState(int grade) {
        return reviewState(grade);
    }

    @Override
    protected FsrsReview reviewState(int grade) {
        FsrsReview cached = cached(grade);
        if (cached != null) {
            return cached;
        }

        int interval = elapsedDays;
        double r = algorithm.forgettingCurve(interval, current.stability());

        FsrsCard again = nextDs(interval, FsrsState.RATING_AGAIN, r);
        FsrsCard hard  = nextDs(interval, FsrsState.RATING_HARD,  r);
        FsrsCard good  = nextDs(interval, FsrsState.RATING_GOOD,  r);
        FsrsCard easy  = nextDs(interval, FsrsState.RATING_EASY,  r);

        again = applyInterval(again, algorithm.nextInterval(again.stability(), interval, seed));
        hard  = applyInterval(hard,  algorithm.nextInterval(hard.stability(),  interval, seed));
        good  = applyInterval(good,  algorithm.nextInterval(good.stability(),  interval, seed));
        easy  = applyInterval(easy,  algorithm.nextInterval(easy.stability(),  interval, seed));

        int a = again.scheduledDays();
        int h = hard .scheduledDays();
        int g = good .scheduledDays();
        int e = easy .scheduledDays();
        a = Math.min(a, h);
        h = Math.max(h, a + 1);
        g = Math.max(g, h + 1);
        e = Math.max(e, g + 1);
        again = applyInterval(again, a);
        hard  = applyInterval(hard,  h);
        good  = applyInterval(good,  g);
        easy  = applyInterval(easy,  e);

        again = again.withState(FsrsState.REVIEW).withLearningSteps(0);
        hard  = hard .withState(FsrsState.REVIEW).withLearningSteps(0);
        good  = good .withState(FsrsState.REVIEW).withLearningSteps(0);
        easy  = easy .withState(FsrsState.REVIEW).withLearningSteps(0);

        again = again.withLapses(again.lapses() + 1);
        updateNext(again, hard, good, easy);
        return cached(grade);
    }

    // ------------------------------------------------------------------

    private FsrsCard nextDs(double t, int grade, Double retrievability) {
        FsrsAlgorithm.MemoryState st = algorithm.nextState(
            new FsrsAlgorithm.MemoryState(current.difficulty(), current.stability()),
            t, grade, retrievability);
        return current.withDifficulty(st.difficulty()).withStability(st.stability());
    }

    private FsrsCard applyInterval(FsrsCard card, int ivl) {
        return card.withScheduledDays(ivl)
                   .withDue(FsrsDateUtils.schedule(reviewTime, ivl, true));
    }

    private void updateNext(FsrsCard again, FsrsCard hard, FsrsCard good, FsrsCard easy) {
        cache(FsrsState.RATING_AGAIN, new FsrsReview(again, buildLog(FsrsState.RATING_AGAIN)));
        cache(FsrsState.RATING_HARD,  new FsrsReview(hard,  buildLog(FsrsState.RATING_HARD)));
        cache(FsrsState.RATING_GOOD,  new FsrsReview(good,  buildLog(FsrsState.RATING_GOOD)));
        cache(FsrsState.RATING_EASY,  new FsrsReview(easy,  buildLog(FsrsState.RATING_EASY)));
    }
}
