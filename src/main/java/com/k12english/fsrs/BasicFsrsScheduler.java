package com.k12english.fsrs;

import java.util.Map;

/**
 * BasicScheduler（启用短时记忆时走这条），对齐 ts-fsrs 的 {@code BasicScheduler}。
 */
final class BasicFsrsScheduler extends AbstractFsrsScheduler {

    BasicFsrsScheduler(FsrsCard card, java.util.Date now, FsrsAlgorithm algorithm) {
        super(card, now, algorithm);
    }

    @Override
    protected FsrsReview newState(int grade) {
        FsrsCard next = nextDs(elapsedDays, grade, null);
        next = applyLearningSteps(next, grade, FsrsState.LEARNING);
        return new FsrsReview(next, buildLog(grade));
    }

    @Override
    protected FsrsReview learningState(int grade) {
        FsrsCard next = nextDs(elapsedDays, grade, null);
        next = applyLearningSteps(next, grade, last.state());
        return new FsrsReview(next, buildLog(grade));
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

        // next_interval(hard, good, easy, interval)
        int hardIvl = algorithm.nextInterval(hard.stability(), interval, seed);
        int goodIvl = algorithm.nextInterval(good.stability(), interval, seed);
        hardIvl = Math.min(hardIvl, goodIvl);
        goodIvl = Math.max(goodIvl, hardIvl + 1);
        int easyIvl = Math.max(algorithm.nextInterval(easy.stability(), interval, seed), goodIvl + 1);

        hard = hard.withScheduledDays(hardIvl).withDue(FsrsDateUtils.schedule(reviewTime, hardIvl, true));
        good = good.withScheduledDays(goodIvl).withDue(FsrsDateUtils.schedule(reviewTime, goodIvl, true));
        easy = easy.withScheduledDays(easyIvl).withDue(FsrsDateUtils.schedule(reviewTime, easyIvl, true));

        hard = hard.withState(FsrsState.REVIEW).withLearningSteps(0);
        good = good.withState(FsrsState.REVIEW).withLearningSteps(0);
        easy = easy.withState(FsrsState.REVIEW).withLearningSteps(0);

        again = applyLearningSteps(again, FsrsState.RATING_AGAIN, FsrsState.RELEARNING);
        again = again.withLapses(again.lapses() + 1);

        cache(FsrsState.RATING_AGAIN, new FsrsReview(again, buildLog(FsrsState.RATING_AGAIN)));
        cache(FsrsState.RATING_HARD,  new FsrsReview(hard,  buildLog(FsrsState.RATING_HARD)));
        cache(FsrsState.RATING_GOOD,  new FsrsReview(good,  buildLog(FsrsState.RATING_GOOD)));
        cache(FsrsState.RATING_EASY,  new FsrsReview(easy,  buildLog(FsrsState.RATING_EASY)));

        return cached(grade);
    }

    // ------------------------------------------------------------------

    private FsrsCard nextDs(double t, int grade, Double retrievability) {
        FsrsAlgorithm.MemoryState st = algorithm.nextState(
            new FsrsAlgorithm.MemoryState(current.difficulty(), current.stability()),
            t, grade, retrievability);
        return current.withDifficulty(st.difficulty()).withStability(st.stability());
    }

    private FsrsCard applyLearningSteps(FsrsCard nextCard, int grade, int toState) {
        Map<Integer, FsrsLearningSteps.StepInfo> strat =
            FsrsLearningSteps.strategy(params, current.state(), current.learningSteps());
        FsrsLearningSteps.StepInfo info = strat.get(grade);
        int scheduledMinutes = (info != null) ? info.scheduledMinutes() : 0;
        int nextSteps = (info != null) ? info.nextStep() : 0;

        if (scheduledMinutes > 0 && scheduledMinutes < 1440) {
            return nextCard
                .withLearningSteps(nextSteps)
                .withScheduledDays(0)
                .withState(toState)
                .withDue(FsrsDateUtils.schedule(reviewTime, scheduledMinutes, false));
        }
        if (scheduledMinutes >= 1440) {
            return nextCard
                .withState(FsrsState.REVIEW)
                .withLearningSteps(nextSteps)
                .withDue(FsrsDateUtils.schedule(reviewTime, scheduledMinutes, false))
                .withScheduledDays((int) Math.floor(scheduledMinutes / 1440.0));
        }
        int interval = algorithm.nextInterval(nextCard.stability(), elapsedDays, seed);
        return nextCard
            .withState(FsrsState.REVIEW)
            .withLearningSteps(0)
            .withScheduledDays(interval)
            .withDue(FsrsDateUtils.schedule(reviewTime, interval, true));
    }
}
