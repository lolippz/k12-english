package com.k12english.fsrs;

import java.time.Instant;
import java.util.Date;

/**
 * FSRS 卡片状态。
 *
 * <p>字段与 ts-fsrs 的 {@code Card} 一一对应，时间统一用 {@link Date}（内部是 epoch ms）。
 */
public record FsrsCard(
        Date due,
        double stability,
        double difficulty,
        int elapsedDays,
        int scheduledDays,
        int learningSteps,
        int reps,
        int lapses,
        int state,
        Date lastReview
) {

    public FsrsCard {
        due = (due != null) ? new Date(due.getTime()) : null;
        lastReview = (lastReview != null) ? new Date(lastReview.getTime()) : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public FsrsCard withDue(Date due) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withStability(double stability) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withDifficulty(double difficulty) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withElapsedDays(int elapsedDays) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withScheduledDays(int scheduledDays) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withLearningSteps(int learningSteps) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withReps(int reps) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withLapses(int lapses) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withState(int state) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public FsrsCard withLastReview(Date lastReview) {
        return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                learningSteps, reps, lapses, state, lastReview);
    }

    public static final class Builder {
        private Date due = new Date();
        private double stability = 0;
        private double difficulty = 0;
        private int elapsedDays = 0;
        private int scheduledDays = 0;
        private int learningSteps = 0;
        private int reps = 0;
        private int lapses = 0;
        private int state = FsrsState.NEW;
        private Date lastReview;

        public Builder due(Date v) {
            this.due = v;
            return this;
        }

        public Builder stability(double v) {
            this.stability = v;
            return this;
        }

        public Builder difficulty(double v) {
            this.difficulty = v;
            return this;
        }

        public Builder elapsedDays(int v) {
            this.elapsedDays = v;
            return this;
        }

        public Builder scheduledDays(int v) {
            this.scheduledDays = v;
            return this;
        }

        public Builder learningSteps(int v) {
            this.learningSteps = v;
            return this;
        }

        public Builder reps(int v) {
            this.reps = v;
            return this;
        }

        public Builder lapses(int v) {
            this.lapses = v;
            return this;
        }

        public Builder state(int v) {
            this.state = v;
            return this;
        }

        public Builder lastReview(Date v) {
            this.lastReview = v;
            return this;
        }

        public FsrsCard build() {
            return new FsrsCard(due, stability, difficulty, elapsedDays, scheduledDays,
                    learningSteps, reps, lapses, state, lastReview);
        }
    }
}
