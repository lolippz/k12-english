package com.k12english.domain;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 词条。字段名与前端 {@code frontend/src/data/index.ts} 的 {@code Word} 接口严格一致，
 * 后端直接透传给浏览器，不做二次映射。
 *
 * <p>数据来源：tools 目录下的 Python 流水线产出的 words.json
 * （qwerty-learner 词表 + ECDICT 增强 + Tatoeba 例句）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Word(
        /** 单词或短语 */
        String w,
        /** 美式音标 */
        String ph,
        /** 英式音标 */
        String phUk,
        /** 中文释义列表 */
        List<String> tr,
        /** 所属学段：primary / junior / senior */
        List<String> stages,
        /** 来源册次 */
        List<String> books,
        /** 词形变化，如 {复数=rulers, 过去式=ruled} */
        Map<String, String> forms,
        /** 柯林斯星级 1-5 */
        Integer star,
        /** 牛津 3000 标记，1 表示命中 */
        Integer oxford,
        /** 考试标签，如 [中考, 高考] */
        List<String> tags,
        /** COCA 词频排名 */
        Integer frq,
        /** 英文释义 */
        String def,
        /** 例句 [英文, 中文] */
        List<String> ex,
        /** 预生成发音音频的文件名（不含扩展名） */
        String a,
        /** 别名 / 全称 */
        String alt) {

    public Word {
        tr = tr == null ? List.of() : List.copyOf(tr);
        stages = stages == null ? List.of() : List.copyOf(stages);
        books = books == null ? List.of() : List.copyOf(books);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** 主释义，没有则返回空串。 */
    public String primaryTranslation() {
        return tr.isEmpty() ? "" : tr.get(0);
    }
}
