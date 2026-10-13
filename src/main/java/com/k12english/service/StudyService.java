package com.k12english.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.k12english.domain.CardRecord;
import com.k12english.domain.Settings;
import com.k12english.domain.Word;
import com.k12english.repo.CardRepository;

/**
 * 学习队列生成。
 *
 * <p>逻辑与前端 {@code frontend/src/lib/study.ts} 的 {@code buildQueue} 一一对应：
 * 先排到期的复习卡（按到期时间升序、受 reviewPerDay 限制），再按词池顺序补新词
 * （跳过已建卡的词、受 newPerDay 限制）。
 */
@Service
public class StudyService {

    private final WordService words;
    private final CardRepository cards;

    public StudyService(WordService words, CardRepository cards) {
        this.words = words;
        this.cards = cards;
    }

    /**
     * 今日队列。
     *
     * @param settings 当前设置
     * @param now      当前时间（epoch ms），便于测试注入
     */
    public QueueResult buildQueue(Settings settings, long now) {
        Map<String, CardRecord> byLower = cards.findAllByLowerWord();

        List<CardRecord> due = new ArrayList<>();
        for (CardRecord c : byLower.values()) {
            // state != 0 表示已经学过；未学过的不算「复习」
            if (c.state() != 0 && c.due() <= now) {
                due.add(c);
            }
        }
        due.sort(Comparator.comparingLong(CardRecord::due));
        List<String> reviews = due.stream()
                .limit(Math.max(0, settings.reviewPerDay()))
                .map(CardRecord::w)
                .collect(Collectors.toList());

        List<String> fresh = new ArrayList<>();
        if (settings.newPerDay() > 0) {
            for (Word w : words.poolOf(settings)) {
                if (byLower.containsKey(w.w().toLowerCase())) {
                    continue;
                }
                fresh.add(w.w());
                if (fresh.size() >= settings.newPerDay()) {
                    break;
                }
            }
        }

        // 只把队列里用得上的卡片发给前端，避免一次性传 6800 多行
        Map<String, CardRecord> needed = new LinkedHashMap<>();
        for (String w : reviews) {
            CardRecord c = byLower.get(w.toLowerCase());
            if (c != null) {
                needed.put(w.toLowerCase(), c);
            }
        }

        return new QueueResult(reviews, fresh, due.size(), remainingNew(settings, byLower), needed);
    }

    /** 全部到期复习数（不受上限约束，用于首页展示）。 */
    public long dueCount(long now) {
        long n = 0;
        for (CardRecord c : cards.findAll()) {
            if (c.state() != 0 && c.due() <= now) {
                n++;
            }
        }
        return n;
    }

    /** 词池中尚未建立卡片的词数。 */
    public long remainingNew(Settings settings) {
        return remainingNew(settings, cards.findAllByLowerWord());
    }

    private long remainingNew(Settings settings, Map<String, CardRecord> existing) {
        long n = 0;
        for (Word w : words.poolOf(settings)) {
            if (!existing.containsKey(w.w().toLowerCase())) {
                n++;
            }
        }
        return n;
    }

    /**
     * @param reviews      今日复习队列（单词，按到期时间升序）
     * @param fresh        今日新词队列（单词，按教材顺序）
     * @param dueCount     全部到期数
     * @param remainingNew 词池中还没建卡的词数
     * @param cards        队列中这些词对应的卡片
     */
    public record QueueResult(
            List<String> reviews,
            List<String> fresh,
            long dueCount,
            long remainingNew,
            Map<String, CardRecord> cards) {
    }

    /** 学段清单，供前端渲染筛选器。 */
    public Set<String> stages() {
        return Set.copyOf(words.allStages());
    }
}
