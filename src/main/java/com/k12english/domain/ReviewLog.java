package com.k12english.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 一条复习日志。字段与前端 {@code frontend/src/lib/types.ts} 的 {@code LogEntry} 一致。
 *
 * @param id      自增主键，插入前为 null
 * @param w       单词
 * @param ts      作答时间（epoch ms）
 * @param grade   评分：1 忘了 / 2 模糊 / 3 记得 / 4 简单
 * @param mode    出题模式：mix / spell / listen / meaning
 * @param correct 是否答对
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewLog(
        Integer id,
        String w,
        long ts,
        int grade,
        String mode,
        boolean correct) {

    /** 视为「答对」的最低评分。忘了（1）算错，其余算对。 */
    public static final int PASS_GRADE = 2;
}
