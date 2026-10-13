package com.k12english.web.dto;

import java.util.List;

import com.k12english.domain.CardRecord;
import com.k12english.domain.ReviewLog;
import com.k12english.domain.Settings;

/** 应用启动时一次性拉取的本地状态：设置 + 全部卡片 + 全部日志。 */
public record StateResponse(
        Settings settings,
        List<CardRecord> cards,
        List<ReviewLog> logs) {
}
