package com.k12english.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.k12english.domain.CardRecord;
import com.k12english.domain.ReviewLog;
import com.k12english.support.TestDatabase;

/** 卡片与日志的存取往返。 */
class CardRepositoryTest {

    private CardRepository cards;
    private LogRepository logs;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = TestDatabase.freshJdbc();
        cards = new CardRepository(jdbc);
        logs = new LogRepository(jdbc);
    }

    private static CardRecord card(String w) {
        return new CardRecord(w, 1_700_000_000_000L, 3.25, 6.5, 2, 5, 1, 4, 1, 2,
                1_699_000_000_000L, 7, 2, 1_690_000_000_000L);
    }

    @Test
    @DisplayName("保存后能按全部 14 个字段原样读回")
    void roundTripPreservesEveryField() {
        CardRecord in = card("apple");
        cards.save(in);

        CardRecord out = cards.find("apple").orElseThrow();
        assertThat(out).isEqualTo(in);
    }

    @Test
    @DisplayName("同一个词保存两次是更新而不是新增")
    void saveIsUpsert() {
        cards.save(card("apple"));
        cards.save(new CardRecord("apple", 1_800_000_000_000L, 30.0, 4.0, 10, 30, 0, 12, 2, 2,
                1_799_000_000_000L, 20, 3, 1_690_000_000_000L));

        assertThat(cards.count()).isEqualTo(1);
        CardRecord out = cards.find("apple").orElseThrow();
        assertThat(out.stability()).isEqualTo(30.0);
        assertThat(out.reps()).isEqualTo(12);
    }

    @Test
    @DisplayName("按小写建索引，大写开头的词也查得到")
    void lowerCaseIndex() {
        cards.save(card("China"));

        Map<String, CardRecord> byLower = cards.findAllByLowerWord();
        assertThat(byLower).containsKey("china");
        assertThat(byLower.get("china").w()).isEqualTo("China");
    }

    @Test
    @DisplayName("last_review 允许为空")
    void lastReviewMayBeNull() {
        cards.save(new CardRecord("newbie", 0L, 0, 0, 0, 0, 0, 0, 0, 0, null, 0, 0, 0L));
        assertThat(cards.find("newbie").orElseThrow().lastReview()).isNull();
    }

    @Test
    @DisplayName("日志按时间升序读回，并带回真实的自增 id")
    void logInsertAndOrder() {
        logs.add(new ReviewLog(null, "b", 2000L, 3, "spell", true));
        ReviewLog first = logs.add(new ReviewLog(null, "a", 1000L, 1, "listen", false));

        // id 必须是真的主键，不能是 0——连接池下用 last_insert_rowid() 取值就会踩到这个坑
        assertThat(first.id()).isNotNull().isPositive();
        assertThat(logs.findAll()).extracting(ReviewLog::w).containsExactly("a", "b");
    }

    @Test
    @DisplayName("累计正确率的分母分子取自数据库")
    void totalAndCorrect() {
        logs.add(new ReviewLog(null, "a", 1000L, 3, "spell", true));
        logs.add(new ReviewLog(null, "b", 2000L, 1, "spell", false));
        logs.add(new ReviewLog(null, "c", 3000L, 3, "spell", true));

        assertThat(logs.totalAndCorrect()).containsExactly(3L, 2L);
    }

    @Test
    @DisplayName("错词排行按错误次数倒序，且只含错过的词")
    void topWrongWords() {
        for (int i = 0; i < 3; i++) {
            logs.add(new ReviewLog(null, "hard", 1000L + i, 1, "spell", false));
        }
        logs.add(new ReviewLog(null, "mid", 2000L, 1, "spell", false));
        logs.add(new ReviewLog(null, "easy", 3000L, 3, "spell", true));

        var rows = logs.topWrongWords(12);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)[0]).isEqualTo("hard");
        assertThat(rows.get(0)[2]).isEqualTo(3L);
    }

    @Test
    @DisplayName("清空只影响本表")
    void clearIsScoped() {
        cards.save(card("apple"));
        logs.add(new ReviewLog(null, "apple", 1000L, 3, "spell", true));

        cards.clear();
        assertThat(cards.count()).isZero();
        assertThat(logs.count()).isEqualTo(1);
    }
}
