package com.k12english.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.k12english.domain.CardRecord;
import com.k12english.domain.ReviewLog;
import com.k12english.domain.Settings;
import com.k12english.repo.CardRepository;
import com.k12english.repo.LogRepository;
import com.k12english.repo.SettingsRepository;
import com.k12english.service.DataService;
import com.k12english.web.dto.ExportPayload;
import com.k12english.web.dto.ReviewRequest;
import com.k12english.web.dto.StateResponse;

/** 应用状态、复习提交与数据管理（导出 / 导入 / 重置）。 */
@RestController
@RequestMapping("/api")
public class StateController {

    private final CardRepository cards;
    private final LogRepository logs;
    private final SettingsRepository settings;
    private final DataService data;

    public StateController(CardRepository cards, LogRepository logs, SettingsRepository settings,
            DataService data) {
        this.cards = cards;
        this.logs = logs;
        this.settings = settings;
        this.data = data;
    }

    /** 启动引导：一次拿齐设置、全部卡片、全部日志。 */
    @GetMapping("/state")
    public StateResponse state() {
        List<CardRecord> cardRows = cards.findAll();
        List<ReviewLog> logRows = logs.findAll();
        return new StateResponse(settings.load(), cardRows, logRows);
    }

    /** 提交一次作答：落卡片 + 追加日志，同一事务内完成。 */
    @PostMapping("/review")
    public ReviewLog review(@RequestBody ReviewRequest req) {
        return data.commitReview(req.card(), req.log());
    }

    /** 导出备份。结构与老版本前端导出的 JSON 一致。 */
    @GetMapping("/data/export")
    public ExportPayload export() {
        return data.export();
    }

    /** 导入备份：整体替换现有卡片与日志。 */
    @PostMapping("/data/import")
    public ExportPayload importData(@RequestBody ExportPayload payload) {
        return data.importAll(payload);
    }

    /** 清空学习记录（保留设置）。 */
    @PostMapping("/data/reset")
    public Settings reset() {
        return data.reset();
    }
}
