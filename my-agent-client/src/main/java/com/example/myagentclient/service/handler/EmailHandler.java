package com.example.myagentclient.service.handler;

import com.example.myagentclient.model.IncomingEmail;
import com.example.myagentclient.service.InboxMonitor;

/**
 * 郵件處理策略介面；{@link InboxMonitor} 負責找信，實作類別決定怎麼處理。
 */
@FunctionalInterface
public interface EmailHandler {

    /**
     * 處理單封郵件。
     * @return {@code true} 成功（保持已讀）；{@code false} 失敗（標回未讀，等待重試）
     */
    boolean handle(IncomingEmail email);
}
