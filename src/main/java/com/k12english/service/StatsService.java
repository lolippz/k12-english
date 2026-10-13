package com.k12english.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.k12english.domain.CardRecord;
import com.k12english.domain.Settings;
import com.k12english.domain.Word;
import com.k12english.repo.CardRepository;
import com.k12english.repo.LogRepository;

/**
 * 学情聚合。
 *
 * <p>输出契约与前端 {@code frontend/src/pages/Stats.tsx} 的取值一一对应：
 * 连续打卡、今日学习量、累计正确率、最近 14 天序列、已掌握/复习中/学习中计数、
 * 按学段进度、错词排行（前 12）。
 *
 * <p>聚合放在后端做，前端就不必再拉全量日志自己算——日志量随时间线性增长，
 * 而这里每条 SQL 都是 O(log n) 或一次性扫描。
 */
@Service
public class StatsService {

    /** 最近多少天的柱状图数据。 */
    public static final int DAILY_WINDOW = 14;
    /** 错词排行取前几名。 */
    public static final int HARD_WORD_LIMIT = 12;

    /** FSRS 遗忘曲线常数，与 ts-fsrs 保持一致：FACTOR = 19/81，DECAY = -0.5。 */
    private static final double FSRS_FACTOR = 19.0 / 81.0;
    private static final double FSRS_DECAY = -0.5;
    private static final double MS_PER_DAY = 86_400_000.0;

    private final CardRepository cards;
    private final LogRepository logs;
    private final WordService words;
    private final StudyService study;

    public StatsService(CardRepository cards, LogRepository logs, WordService words, StudyService study) {
        this.cards = cards;
        this.logs = logs;
        this.words = words;
        this.study = study;
    }

    public Stats compute(Settings settings, long now) {
        List<CardRecord> all = cards.findAll();

        long mastered = 0;
        long learning = 0;
        long reviewing = 0;
        Map<String, long[]> stageAcc = new LinkedHashMap<>(); // stage -> [total, mastered]
        List<CardRecord> wrongCards = new ArrayList<>();

        for (CardRecord c : all) {
            boolean isMastered = c.isMastered();
            if (isMastered) {
                mastered++;
            }
            if (c.state() == 1 || c.state() == 3) {
                learning++;
            }
            if (c.state() == 2) {
                reviewing++;
            }
            if (c.wrong() > 0) {
                wrongCards.add(c);
            }

            Word wd = words.find(c.w()).orElse(null);
            if (wd != null) {
                for (String s : wd.stages()) {
                    long[] slot = stageAcc.computeIfAbsent(s, k -> new long[2]);
                    slot[0]++;
                    if (isMastered) {
                        slot[1]++;
                    }
                }
            }
        }

        // 错词排行：错误次数优先，其次作答次数
        wrongCards.sort(Comparator.comparingLong(CardRecord::wrong).reversed()
                .thenComparing(Comparator.comparingLong(CardRecord::seen).reversed()));
        List<HardWord> hardWords = new ArrayList<>();
        for (CardRecord c : wrongCards.subList(0, Math.min(HARD_WORD_LIMIT, wrongCards.size()))) {
            hardWords.add(new HardWord(c.w(), c.seen(), c.wrong(), c.state(), c.stability(),
                    round4(retention(c, now))));
        }

        Map<String, StageProgress> byStage = new LinkedHashMap<>();
        for (Map.Entry<String, long[]> e : stageAcc.entrySet()) {
            byStage.put(e.getKey(), new StageProgress(e.getValue()[0], e.getValue()[1]));
        }

        long[] total = logs.totalAndCorrect();
        long totalReviews = total[0];
        long totalCorrect = total[1];

        List<DayStat> daily = dailyWindow(now);
        long today = 0;
        String todayKey = LocalDate.now().toString();
        for (DayStat d : daily) {
            if (d.date().equals(todayKey)) {
                today = d.count();
                break;
            }
        }

        return new Stats(
                streak(),
                today,
                totalReviews == 0 ? 0.0 : round4((double) totalCorrect / totalReviews),
                totalReviews,
                all.size(),
                mastered,
                learning,
                reviewing,
                daily,
                byStage,
                hardWords,
                study.dueCount(now),
                study.remainingNew(settings),
                words.poolOf(settings).size());
    }

    /**
     * 最近 {@value #DAILY_WINDOW} 天的每日作答数，空缺的日期补零。
     *
     * <p>日期按本地时区切分（SQLite 侧用 {@code localtime}），避免晚上 8 点之后
     * 的学习被算进第二天。
     */
    public List<DayStat> dailyWindow(long now) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(DAILY_WINDOW - 1L);
        long sinceTs = from.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

        Map<String, long[]> bucket = new LinkedHashMap<>();
        for (Object[] row : logs.dailyCounts(sinceTs)) {
            bucket.put((String) row[0], new long[] { (Long) row[1], (Long) row[2] });
        }

        List<DayStat> out = new ArrayList<>(DAILY_WINDOW);
        for (int i = 0; i < DAILY_WINDOW; i++) {
            String key = from.plusDays(i).toString();
            long[] v = bucket.get(key);
            out.add(new DayStat(key, v == null ? 0 : v[0], v == null ? 0 : v[1]));
        }
        return out;
    }

    /**
     * 连续打卡天数。今天还没学不算断，从昨天往前推。
     *
     * <p>与前端 {@code study.ts} 的 {@code streak} 同义。
     */
    public int streak() {
        Set<String> days = new HashSet<>(logs.distinctDaysDesc());
        if (days.isEmpty()) {
            return 0;
        }
        LocalDate cursor = LocalDate.now();
        if (!days.contains(cursor.toString())) {
            cursor = cursor.minusDays(1);
        }
        int n = 0;
        while (days.contains(cursor.toString())) {
            n++;
            cursor = cursor.minusDays(1);
        }
        return n;
    }

    /**
     * 记忆保持率 0-1。
     *
     * <p>FSRS 遗忘曲线：{@code R(t, S) = (1 + FACTOR * t / S) ^ DECAY}，
     * 与前端 {@code srs.ts} 的 {@code retention()} 同一公式。
     */
    public static double retention(CardRecord c, long now) {
        if (c.state() == 0 || c.stability() <= 0) {
            return 0;
        }
        long last = c.lastReview() == null ? now : c.lastReview();
        double elapsed = Math.max(0, (now - last) / MS_PER_DAY);
        double r = Math.pow(1 + FSRS_FACTOR * elapsed / c.stability(), FSRS_DECAY);
        return Math.max(0, Math.min(1, r));
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    /* ---------------- 输出契约 ---------------- */

    public record Stats(
            int streak,
            long today,
            double accuracy,
            long totalReviews,
            long totalCards,
            long mastered,
            long learning,
            long reviewing,
            List<DayStat> daily,
            Map<String, StageProgress> byStage,
            List<HardWord> hardWords,
            long dueCount,
            long remainingNew,
            int poolSize) {
    }

    /** @param date yyyy-MM-dd（本地时区） */
    public record DayStat(String date, long count, long correct) {
    }

    public record StageProgress(long total, long mastered) {
    }

    public record HardWord(String w, long seen, long wrong, int state, double stability, double retention) {
    }
}
