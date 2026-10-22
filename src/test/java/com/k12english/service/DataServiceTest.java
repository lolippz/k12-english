package com.k12english.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.k12english.repo.SettingsRepository;
import com.k12english.support.TestDatabase;
import com.k12english.web.dto.ExportPayload;

import tools.jackson.databind.json.JsonMapper;

/** 作答落库与备份导出/导入/重置。 */
class DataServiceTest {

    private CardRepository cards;
    private LogRepository logs;
    private SettingsRepository settings;
    private DataService data;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = TestDatabase.freshJdbc();
        cards = new CardRepository(jdbc);
        logs = new LogRepository(jdbc);
        settings = new SettingsRepository(jdbc, JsonMapper.builder().build());
        data = new DataService(cards, logs, settings);
    }

    private static CardRecord card(String w) {
        return new CardRecord(w, 1_700_000_000_000L, 2.0, 5.0, 0, 1, 0, 1, 0, 1,
                1_700_000_000_000L, 1, 0, 1_700_000_000_000L);
    }

    @Test
    @DisplayName("一次作答同时落卡片和日志")
    void commitReviewWritesBoth() {
        ReviewLog saved = data.commitReview(card("apple"),
                new ReviewLog(null, "apple", 1_700_000_000_000L, 3, "spell", true));

        assertThat(saved.id()).isPositive();
        assertThat(cards.count()).isEqualTo(1);
        assertThat(logs.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("卡片或日志缺一个也不报错（前端补交场景）")
    void commitReviewToleratesNulls() {
        data.commitReview(card("apple"), null);
        assertThat(cards.count()).isEqualTo(1);
        assertThat(logs.count()).isZero();
    }

    @Test
    @DisplayName("导出的结构与前端旧备份格式一致")
    void exportShape() {
        settings.save(Settings.defaults());
        cards.save(card("apple"));
        logs.add(new ReviewLog(null, "apple", 1L, 3, "spell", true));

        ExportPayload p = data.export();
        assertThat(p.version()).isEqualTo(ExportPayload.CURRENT_VERSION);
        assertThat(p.exportedAt()).isNotBlank();
        assertThat(p.settings()).isNotNull();
        assertThat(p.cards()).hasSize(1);
        assertThat(p.logs()).hasSize(1);
    }

    @Test
    @DisplayName("导入整体替换现有数据，不产生重复")
    void importReplacesEverything() {
        cards.save(card("old"));
        logs.add(new ReviewLog(null, "old", 1L, 3, "spell", true));

        ExportPayload incoming = new ExportPayload(1, "2026-01-01T00:00:00Z", Settings.defaults(),
                List.of(card("apple"), card("banana")),
                List.of(new ReviewLog(999, "apple", 5L, 3, "spell", true)));

        data.importAll(incoming);

        assertThat(cards.count()).isEqualTo(2);
        assertThat(cards.find("old")).isEmpty();
        assertThat(logs.count()).isEqualTo(1);
        // 导入时丢掉原 id，重新分配，避免主键冲突
        assertThat(logs.findAll().get(0).id()).isNotEqualTo(999);
    }

    @Test
    @DisplayName("缺少 cards 字段的备份被拒绝，且不破坏现有数据")
    void importRejectsMalformedPayload() {
        cards.save(card("apple"));

        assertThatThrownBy(() -> data.importAll(new ExportPayload(1, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cards");

        assertThat(cards.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("重置清空学习记录但保留设置")
    void resetKeepsSettings() {
        Settings custom = new Settings(List.of("primary"), List.of("PEP三上"), 5, 30, "spell", "uk", false);
        settings.save(custom);
        cards.save(card("apple"));
        logs.add(new ReviewLog(null, "apple", 1L, 3, "spell", true));

        Settings after = data.reset();

        assertThat(cards.count()).isZero();
        assertThat(logs.count()).isZero();
        assertThat(after.newPerDay()).isEqualTo(5);
        assertThat(after.mode()).isEqualTo("spell");
        assertThat(after.accent()).isEqualTo("uk");
    }

    @Test
    @DisplayName("设置默认值可用，且反序列化失败时回退默认值")
    void settingsFallback() {
        assertThat(settings.load().newPerDay()).isEqualTo(Settings.defaults().newPerDay());

        JdbcTemplate jdbc = TestDatabase.freshJdbc();
        jdbc.update("INSERT INTO settings (user_id, k, v) VALUES ('', 'settings', '{ 这不是 JSON')");
        SettingsRepository broken = new SettingsRepository(jdbc, JsonMapper.builder().build());

        assertThat(broken.load()).isEqualTo(Settings.defaults());
    }
}
