package com.k12english.web.dto;

import com.k12english.domain.CardRecord;
import com.k12english.domain.ReviewLog;

/**
 * 提交一次作答。
 *
 * <p>卡片的新状态由前端算好（FSRS 调度暂留前端，见 {@link CardRecord} 类注释），
 * 后端在一个事务里落卡片 + 追加日志，避免两次往返导致状态不一致。
 */
public record ReviewRequest(CardRecord card, ReviewLog log) {
}
