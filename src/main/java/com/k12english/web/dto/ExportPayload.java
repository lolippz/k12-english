package com.k12english.web.dto;

import java.util.List;

import com.k12english.domain.CardRecord;
import com.k12english.domain.ReviewLog;
import com.k12english.domain.Settings;

/**
 * 备份文件结构。与前端 {@code App.tsx} 导出的 JSON 完全兼容，
 * 即老版本浏览器里导出的备份可以直接导入这里。
 */
public record ExportPayload(
        int version,
        String exportedAt,
        Settings settings,
        List<CardRecord> cards,
        List<ReviewLog> logs) {

    public static final int CURRENT_VERSION = 1;
}
