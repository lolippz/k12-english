package com.k12english.web.dto;

import java.util.List;
import java.util.Map;

import com.k12english.domain.Word;

/** 全量词库。外层结构与 {@code words.json} 保持一致，便于直接落盘。 */
public record WordListResponse(Map<String, Object> meta, List<Word> words) {
}
