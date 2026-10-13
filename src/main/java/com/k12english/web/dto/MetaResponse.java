package com.k12english.web.dto;

import java.util.List;

/** 词库元信息。供前端渲染筛选器与健康检查使用。 */
public record MetaResponse(
        String name,
        String version,
        int totalWords,
        long totalCards,
        long totalLogs,
        List<String> stages) {
}
