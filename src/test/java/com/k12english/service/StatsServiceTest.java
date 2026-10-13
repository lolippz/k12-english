package com.k12english.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.k12english.domain.CardRecord;
import com.k12english.domain.ReviewLog;
import com.k12english.domain.Settings;
import com.k12english.repo.CardRepository;
import com.k12english.repo.LogRepository;
import com.k12english.support.TestDatabase;
import com.k12english.support.WordServices;

/**
 * 学情聚合。
 *
 * <p>断言对应前端 {@code frontend/src/pages/Stats.tsx} 的取值：连续打卡、今日学习数、
 * 累计正确率、最近 14 天序列、掌握/复习中/学习中计数、按学段进度、错词排行。
 */
class StatsServiceTest {

    private static final long MS_PER_DAY = 86_400_000L;

    private CardRepository cards;
    private LogRepository logs;
    private StatsService stats;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = TestDatabase.freshJdbc();
        cards = new CardRepository(jdbc);
        logs = new LogRepository(jdbc);
        WordService words = WordServices.tiny();
        stats = new StatsService(cards, logs, words, new StudyService(words, cards));
    }

    private static Settings allStages() {
        return new Settings(List.of("primary", "junior", "senior"), List.of(), 20, 120, "mix", "us", true);
    }

    /** 本地时区下第 n 天前的某个时刻。 */
    private static long daysAgo(int n) {
        return LocalDate.now().minusDays(n)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 36_000_000L;
    }

    private static CardRecord card(String w, int state, double stability, int seen, int wrong) {
        return card(w, state, stability, seen, wrong, daysAgo(0));
    }

    private static CardRecord card(String w, int state, double stability, int seen, int wrong, long due) {
        return new CardRecord(w, due, stability, 5.0, 0, 1, 0, seen, 0, state, daysAgo(0), seen,
                wrong, daysAgo(30));
    }

    @Test
    @DisplayName("遗忘曲线：经过时间等于稳定度时保持率约 0.9")
    void retentionAtStabilityIsNinetyPercent() {
        CardRecord c = new CardRecord("apple", 0, 10.0, 5.0, 0, 10, 0, 1, 0, 2,
                daysAgo(0) - 10 * MS_PER_DAY, 1, 0, daysAgo(20));

        assertThat(StatsService.retention(c, daysAgo(0))).isCloseTo(0.9, org.assertj.core.data.Offset.offset(0.005));
    }

    @Test
    @DisplayName("遗忘曲线：刚复习完保持率为 1，未学过的为 0")
    void retentionBoundaries() {
        CardRecord justReviewed = new CardRecord("apple", 0, 10.0, 5.0, 0, 10, 0, 1, 0, 2, daysAgo(0), 1, 0,
                daysAgo(1));
        CardRecord untouched = new CardRecord("banana", 0, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 0);

        assertThat(StatsService.retention(justReviewed, daysAgo(0))).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(0.001));
        assertThat(StatsService.retention(untouched, daysAgo(0))).isZero();
    }

    @Test
    @DisplayName("连续打卡：今天学了就从今天往前数")
    void streakCountsFromToday() {
        for (int i = 0; i < 3; i++) {
            logs.add(new ReviewLog(null, "apple", daysAgo(i), 3, "spell", true));
        }
        assertThat(stats.streak()).isEqualTo(3);
    }

    @Test
    @DisplayName("连续打卡：今天还没学不算断，从昨天往前数")
    void streakToleratesNotStudyingYetToday() {
        for (int i = 1; i <= 3; i++) {
            logs.add(new ReviewLog(null, "apple", daysAgo(i), 3, "spell", true));
        }
        assertThat(stats.streak()).isEqualTo(3);
    }

    @Test
    @DisplayName("连续打卡：中间断一天就归零到最近一段")
    void streakBreaksOnGap() {
        logs.add(new ReviewLog(null, "apple", daysAgo(0), 3, "spell", true));
        logs.add(new ReviewLog(null, "apple", daysAgo(1), 3, "spell", true));
        logs.add(new ReviewLog(null, "apple", daysAgo(4), 3, "spell", true));

        assertThat(stats.streak()).isEqualTo(2);
    }

    @Test
    @DisplayName("连续打卡：完全没有记录时为 0")
    void streakEmpty() {
        assertThat(stats.streak()).isZero();
    }

    @Test
    @DisplayName("最近 14 天序列固定 14 项，没有记录的日子补 0")
    void dailyWindowIsZeroFilled() {
        logs.add(new ReviewLog(null, "apple", daysAgo(0), 3, "spell", true));
        logs.add(new ReviewLog(null, "apple", daysAgo(0), 1, "spell", false));
        logs.add(new ReviewLog(null, "banana", daysAgo(3), 3, "spell", true));

        List<StatsService.DayStat> daily = stats.dailyWindow(daysAgo(0));
        assertThat(daily).hasSize(14);
        assertThat(daily.get(13).count()).isEqualTo(2);
        assertThat(daily.get(13).correct()).isEqualTo(1);
        assertThat(daily.get(10).count()).isEqualTo(1);
        assertThat(daily.get(0).count()).isZero();
        assertThat(daily.get(0).date()).isEqualTo(LocalDate.now().minusDays(13).toString());
    }

    @Test
    @DisplayName("今日学习数与累计正确率")
    void todayAndAccuracy() {
        logs.add(new ReviewLog(null, "apple", daysAgo(0), 3, "spell", true));
        logs.add(new ReviewLog(null, "apple", daysAgo(0), 1, "spell", false));
        logs.add(new ReviewLog(null, "banana", daysAgo(2), 3, "spell", true));
        logs.add(new ReviewLog(null, "banana", daysAgo(2), 3, "spell", true));

        StatsService.Stats s = stats.compute(allStages(), daysAgo(0));
        assertThat(s.today()).isEqualTo(2);
        assertThat(s.totalReviews()).isEqualTo(4);
        assertThat(s.accuracy()).isCloseTo(0.75, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    @DisplayName("卡片按状态分类计数，掌握需要稳定度达到 21")
    void cardBuckets() {
        cards.save(card("apple", 2, 30.0, 10, 0));   // 复习中 + 掌握
        cards.save(card("banana", 2, 5.0, 4, 0));    // 复习中，未掌握
        cards.save(card("cherry", 1, 1.0, 2, 0));    // 学习中
        cards.save(card("dog", 3, 0.5, 3, 1));       // 重学
        cards.save(card("elephant", 0, 0, 0, 0));    // 未学

        StatsService.Stats s = stats.compute(allStages(), daysAgo(0));
        assertThat(s.totalCards()).isEqualTo(5);
        assertThat(s.mastered()).isEqualTo(1);
        assertThat(s.reviewing()).isEqualTo(2);
        assertThat(s.learning()).isEqualTo(2);
    }

    @Test
    @DisplayName("按学段统计进度：cherry 同属小学与初中，两边都要计")
    void progressByStage() {
        cards.save(card("apple", 2, 30.0, 10, 0));  // 仅 primary，已掌握
        cards.save(card("cherry", 1, 1.0, 2, 0));   // primary + junior

        StatsService.Stats s = stats.compute(allStages(), daysAgo(0));
        assertThat(s.byStage().get("primary").total()).isEqualTo(2);
        assertThat(s.byStage().get("primary").mastered()).isEqualTo(1);
        assertThat(s.byStage().get("junior").total()).isEqualTo(1);
        assertThat(s.byStage().get("junior").mastered()).isZero();
        assertThat(s.byStage()).doesNotContainKey("senior");
    }

    @Test
    @DisplayName("错词排行按错误次数倒序，未错过的词不出现")
    void hardWordsRanking() {
        cards.save(card("apple", 1, 1.0, 5, 4));
        cards.save(card("banana", 1, 1.0, 3, 2));
        cards.save(card("cherry", 1, 1.0, 9, 0)); // 没错过，不进榜

        StatsService.Stats s = stats.compute(allStages(), daysAgo(0));
        assertThat(s.hardWords()).hasSize(2);
        assertThat(s.hardWords().get(0).w()).isEqualTo("apple");
        assertThat(s.hardWords().get(0).wrong()).isEqualTo(4);
        assertThat(s.hardWords().get(1).w()).isEqualTo("banana");
    }

    @Test
    @DisplayName("错词排行最多 12 条")
    void hardWordsLimitedToTwelve() {
        for (int i = 0; i < 20; i++) {
            cards.save(card("word" + i, 1, 1.0, 3, 1));
        }
        assertThat(stats.compute(allStages(), daysAgo(0)).hardWords()).hasSize(StatsService.HARD_WORD_LIMIT);
    }

    @Test
    @DisplayName("池规模、待复习数、剩余新词一起返回")
    void poolAndCounts() {
        cards.save(card("apple", 2, 30.0, 10, 0));                                  // 已到期
        cards.save(card("banana", 2, 30.0, 10, 0, daysAgo(0) + 5 * MS_PER_DAY));    // 5 天后才到期

        StatsService.Stats s = stats.compute(allStages(), daysAgo(0));
        assertThat(s.poolSize()).isEqualTo(6);      // 小词表全选
        assertThat(s.remainingNew()).isEqualTo(4);  // 6 个词，建了 2 张卡
        assertThat(s.dueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有日志时正确率为 0 而不是除零异常")
    void emptyAccuracy() {
        assertThat(stats.compute(allStages(), daysAgo(0)).accuracy()).isZero();
    }
}
