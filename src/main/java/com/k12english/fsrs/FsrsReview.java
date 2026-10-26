package com.k12english.fsrs;

/**
 * 一次复习的结果：新卡片 + 日志。
 */
public record FsrsReview(FsrsCard card, FsrsReviewLog log) {
}
