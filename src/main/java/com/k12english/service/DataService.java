package com.k12english.service;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.k12english.domain.CardRecord;
import com.k12english.domain.ReviewLog;
import com.k12english.domain.Settings;
import com.k12english.repo.CardRepository;
import com.k12english.repo.LogRepository;
import com.k12english.repo.SettingsRepository;
import com.k12english.web.dto.ExportPayload;

/**
 * 数据管理：单次作答落库、整包导出、整包导入、重置。
 *
 * <p>导入与重置都开事务——导入要先把旧数据清干净再灌新的，
 * 中途失败必须回滚，否则用户的学习记录会丢。
 */
@Service
public class DataService {

    private final CardRepository cards;
    private final LogRepository logs;
    private final SettingsRepository settings;

    public DataService(CardRepository cards, LogRepository logs, SettingsRepository settings) {
        this.cards = cards;
        this.logs = logs;
        this.settings = settings;
    }

    /** 落一张卡片 + 追加一条日志。 */
    @Transactional
    public ReviewLog commitReview(CardRecord card, ReviewLog log) {
        if (card != null) {
            cards.save(card);
        }
        return log == null ? null : logs.add(log);
    }

    public ExportPayload export() {
        return new ExportPayload(
                ExportPayload.CURRENT_VERSION,
                OffsetDateTime.now().toString(),
                settings.load(),
                cards.findAll(),
                logs.findAll());
    }

    @Transactional
    public ExportPayload importAll(ExportPayload payload) {
        if (payload == null || payload.cards() == null) {
            throw new IllegalArgumentException("备份内容缺少 cards 字段");
        }
        cards.clear();
        logs.clear();
        cards.saveAll(payload.cards());
        if (payload.logs() != null) {
            // 导入时丢掉原 id，让 SQLite 重新分配，避免自增主键冲突
            for (ReviewLog l : payload.logs()) {
                logs.add(new ReviewLog(null, l.w(), l.ts(), l.grade(), l.mode(), l.correct()));
            }
        }
        if (payload.settings() != null) {
            settings.save(payload.settings());
        }
        return export();
    }

    @Transactional
    public Settings reset() {
        cards.clear();
        logs.clear();
        return settings.load();
    }

    /** 供测试与健康检查使用。 */
    public List<CardRecord> allCards() {
        return cards.findAll();
    }
}
