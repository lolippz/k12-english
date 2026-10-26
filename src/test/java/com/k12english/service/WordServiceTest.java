package com.k12english.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.k12english.domain.Settings;
import com.k12english.support.WordServices;

/** 词库载入与筛选。 */
class WordServiceTest {

    @Test
    @DisplayName("小词表按原顺序载入")
    void loadsFixture() {
        WordService ws = WordServices.tiny();
        assertThat(ws.size()).isEqualTo(6);
        assertThat(ws.all()).extracting(w -> w.w())
                .containsExactly("apple", "banana", "cherry", "dog", "elephant", "fox");
    }

    @Test
    @DisplayName("查词不区分大小写")
    void findIsCaseInsensitive() {
        WordService ws = WordServices.tiny();
        assertThat(ws.find("APPLE")).isPresent();
        assertThat(ws.find("apple").orElseThrow().tr()).containsExactly("苹果");
        assertThat(ws.find("nope")).isEmpty();
        assertThat(ws.find(null)).isEmpty();
    }

    @Test
    @DisplayName("学段筛选：空列表视为全选")
    void stageFilter() {
        WordService ws = WordServices.tiny();
        assertThat(ws.ofStages(List.of("primary"))).extracting(w -> w.w())
                .containsExactly("apple", "banana", "cherry", "dog");
        assertThat(ws.ofStages(List.of())).hasSize(6);
        assertThat(ws.ofStages(null)).hasSize(6);
        assertThat(ws.ofStages(List.of("senior"))).extracting(w -> w.w()).containsExactly("fox");
    }

    @Test
    @DisplayName("册次清单来自词条并去重排序")
    void booksOfStages() {
        WordService ws = WordServices.tiny();
        // cherry 同属 primary 与 junior，所以小学段里也会带出「人教七上」
        assertThat(ws.booksOfStages(List.of("primary")))
                .containsExactly("PEP三上", "PEP三下", "人教七上");
        assertThat(ws.booksOfStages(List.of("senior"))).containsExactly("高考3500");
    }

    @Test
    @DisplayName("学段 + 册次双重筛选")
    void stageAndBookFilter() {
        WordService ws = WordServices.tiny();
        List<String> words = ws.ofBooks(List.of("primary"), List.of("PEP三下"))
                .stream().map(w -> w.w()).toList();
        assertThat(words).containsExactly("dog");
    }

    @Test
    @DisplayName("poolOf 按设置里的学段与册次取词池")
    void poolOfSettings() {
        WordService ws = WordServices.tiny();
        Settings s = new Settings(List.of("primary", "junior"), List.of(), 20, 120, "mix", "us", true);
        assertThat(ws.poolOf(s)).hasSize(5);
    }

    @Test
    @DisplayName("真实词库：跑过数据流水线时应为 6887 条（6877 + 重大版新增 10）")
    void realDictionary() {
        WordService ws = WordServices.real();
        // 没跑过 tools 流水线时词库为空，跳过而不是失败——保证全新 clone 上 mvn test 也能过
        assumeTrue(ws.size() > 0, "未找到 data/words.json，跳过真实词库断言");

        assertThat(ws.size()).isEqualTo(6887);
        assertThat(ws.allStages()).containsExactly("primary", "junior", "senior");
        assertThat(ws.ofStages(List.of("primary"))).isNotEmpty();
        assertThat(ws.ofBooks(List.of("primary", "junior", "senior"), List.of())).hasSize(6887);
    }
}
