package com.example.myagentclient.model;

import java.time.Instant;
import java.util.List;

/**
 * 從收件匣撈取的單封郵件的乾淨、不依賴框架的資料模型。
 * Agent 的其餘邏輯只會操作這個物件，原始的 Jakarta Mail {@code Message} 不會流出 Monitor 之外。
 *
 * @param messageId RFC 822 Message-ID 標頭（每封信唯一且穩定）
 * @param from      寄件人地址
 * @param to        收件人地址列表
 * @param subject   郵件主旨（不為 null；若缺少則為空字串）
 * @param body      盡力取得的純文字內文
 * @param receivedAt 郵件伺服器收到此郵件的時間
 */
public record IncomingEmail(
        String messageId,
        String from,
        List<String> to,
        String subject,
        String body,
        Instant receivedAt
) {
}
