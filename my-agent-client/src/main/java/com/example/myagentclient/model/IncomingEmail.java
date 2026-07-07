package com.example.myagentclient.model;

import java.time.Instant;
import java.util.List;

/**
 * 單封郵件的內部資料模型，與框架解耦；Agent 的所有邏輯只操作此物件。
 *
 * @param messageId RFC 822 Message-ID，跨系統唯一識別碼，用於去重與回覆串接
 * @param subject   不為 null；原始郵件缺少主旨時補空字串
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
