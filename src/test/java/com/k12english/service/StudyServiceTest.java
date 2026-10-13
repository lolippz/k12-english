package com.k12english.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.k12english.domain.CardRecord;
import com.k12english.domain.Settings;
import com.k12english.repo.CardRepository;
import com.k12english.support.TestDatabase;
import com.k12english.support.WordServices;

/**
 * 学习队列生成。
 *
 * <p>这些断言对应前端 {@code frontend/src/lib/study.ts} 里 {@code buildQueue} 的语义，
 * 两边必须一致——否则会出现「首页说 20 个新词、点进去只有 12 个」这类错位。
 */
class StudyServiceTest {

    private static final long NOW = 1_700_000_000_000L;

    private CardRepository cards;
    private StudyService study;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = TestDatabase.freshJdbc();
        cards = new CardRepository(jdbc);
        study = new StudyService(WordServices.tiny(), cards);
    }

    /** 只学小学段，词池顺序为 apple, banana, cherry, dog。 */
    private static Settings primary(int newPerDay, int reviewPerDay) {
        return new Settings(List.of("primary"), List.of(), newPerDay, reviewPerDay, "mix", "us", true);
    }

    private static CardRecord withState(String w, int state, long due) {
        return new CardRecord(w, due, 5.0, 5.0, 0, 1, 0, 1, 0, state, NOW - 86_400_000L, 1, 0,
                NOW - 86_400_000L);
    }

    @Test
    @DisplayName("没有任何卡片时，队列全是新词且不超过每日上限")
    void allFreshWhenNoCards() {
        StudyService.QueueResult q = study.buildQueue(primary(2, 120), NOW);

        assertThat(q.reviews()).isEmpty();
        assertThat(q.fresh()).containsExactly("apple", "banana");
        assertThat(q.remainingNew()).isEqualTo(4);
    }

    @Test
    @DisplayName("新词按词池顺序取，且跳过已经建过卡的词")
    void freshSkipsExistingCards() {
        cards.save(withState("apple", 1, NOW + 86_400_000L));

        StudyService.QueueResult q = study.buildQueue(primary(3, 120), NOW);
        assertThat(q.fresh()).containsExactly("banana", "cherry", "dog");
        assertThat(q.remainingNew()).isEqualTo(3);
    }

    @Test
    @DisplayName("到期且已学过的卡进复习队列，按到期时间升序")
    void dueCardsGoToReviewsSortedByDue() {
        cards.save(withState("banana", 2, NOW - 1000));
        cards.save(withState("cherry", 2, NOW - 9000));

        StudyService.QueueResult q = study.buildQueue(primary(0, 120), NOW);
        assertThat(q.reviews()).containsExactly("cherry", "banana");
        assertThat(q.dueCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("未到期的卡不进复习队列")
    void notYetDueExcluded() {
        cards.save(withState("banana", 2, NOW + 86_400_000L));

        StudyService.QueueResult q = study.buildQueue(primary(0, 120), NOW);
        assertThat(q.reviews()).isEmpty();
        assertThat(q.dueCount()).isZero();
    }

    @Test
    @DisplayName("state=0 的卡不算复习，哪怕 due 已经过去")
    void newStateNeverCountedAsReview() {
        cards.save(withState("banana", 0, NOW - 1000));

        StudyService.QueueResult q = study.buildQueue(primary(0, 120), NOW);
        assertThat(q.reviews()).isEmpty();
        assertThat(q.dueCount()).isZero();
    }

    @Test
    @DisplayName("复习数量受 reviewPerDay 限制，但 dueCount 报全量")
    void reviewLimitDoesNotAffectDueCount() {
        cards.save(withState("apple", 2, NOW - 3000));
        cards.save(withState("banana", 2, NOW - 2000));
        cards.save(withState("cherry", 2, NOW - 1000));

        StudyService.QueueResult q = study.buildQueue(primary(0, 2), NOW);
        assertThat(q.reviews()).containsExactly("apple", "banana");
        assertThat(q.dueCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("只把队列用得上的卡片发给前端，避免传全表")
    void onlyQueueCardsAreReturned() {
        cards.save(withState("apple", 2, NOW - 1000));
        cards.save(withState("banana", 2, NOW + 86_400_000L)); // 未到期，不该出现在结果里

        StudyService.QueueResult q = study.buildQueue(primary(0, 120), NOW);
        assertThat(q.cards()).containsOnlyKeys("apple");
    }

    @Test
    @DisplayName("newPerDay 为 0 时只复习不学新词")
    void zeroNewPerDay() {
        StudyService.QueueResult q = study.buildQueue(primary(0, 120), NOW);
        assertThat(q.fresh()).isEmpty();
    }

    @Test
    @DisplayName("学段筛选生效：只选小学时不出现初中/高中词")
    void stageFilter() {
        StudyService.QueueResult q = study.buildQueue(primary(10, 120), NOW);
        assertThat(q.fresh()).containsExactly("apple", "banana", "cherry", "dog");
        assertThat(q.fresh()).doesNotContain("elephant", "fox");
    }

    @Test
    @DisplayName("册次筛选生效")
    void bookFilter() {
        Settings s = new Settings(List.of("primary"), List.of("PEP三下"), 10, 120, "mix", "us", true);
        StudyService.QueueResult q = study.buildQueue(s, NOW);
        assertThat(q.fresh()).containsExactly("dog");
    }
}
