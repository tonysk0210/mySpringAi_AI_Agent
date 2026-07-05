package com.example.myagentclient.service.handler;

import com.example.myagentclient.model.IncomingEmail;
import com.example.myagentclient.service.InboxMonitor;

/**
 * 處理新發現郵件的策略介面。
 * <p>
 * {@link InboxMonitor} 只負責<em>找到</em>新郵件；至於要<em>做什麼</em>
 * （分類、草擬回覆、開工單、交給 LLM 處理...）則委派給這裡。
 * 請提供自己的 {@code @Component} 實作類別來接入 Agent 的行為邏輯。
 */
@FunctionalInterface
public interface EmailHandler {

    /**
     * 處理單封新郵件。
     * @return {@code true} 表示處理成功，郵件可標為已讀；
     *         {@code false} 表示處理失敗，保持未讀以便下次輪詢重試。
     */
    boolean handle(IncomingEmail email);
}
