package com.k12english.domain;

import java.util.List;

/**
 * 学习设置。字段与前端 {@code frontend/src/lib/types.ts} 的 {@code Settings} 一致。
 *
 * @param stages      参与学习的学段：primary / junior / senior
 * @param books       限定册次，空列表表示该学段全部
 * @param newPerDay   每日新词上限
 * @param reviewPerDay 每日复习上限
 * @param mode        出题模式：mix / spell / listen / meaning
 * @param accent      发音口音：us / uk
 * @param sound       答题提示音开关
 */
public record Settings(
        List<String> stages,
        List<String> books,
        int newPerDay,
        int reviewPerDay,
        String mode,
        String accent,
        boolean sound) {

    public static final List<String> ALL_STAGES = List.of("primary", "junior", "senior");

    /** 与前端 DEFAULT_SETTINGS 保持一致。 */
    public static Settings defaults() {
        return new Settings(List.copyOf(ALL_STAGES), List.of(), 20, 120, "mix", "us", true);
    }

    public Settings {
        stages = (stages == null || stages.isEmpty()) ? List.copyOf(ALL_STAGES) : List.copyOf(stages);
        books = books == null ? List.of() : List.copyOf(books);
        if (mode == null || mode.isBlank()) {
            mode = "mix";
        }
        if (accent == null || accent.isBlank()) {
            accent = "us";
        }
    }
}
