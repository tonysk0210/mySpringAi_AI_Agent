package com.example.myagentclient.service.handler;

import com.example.myagentclient.model.AgentResponse;
import com.example.myagentclient.model.IncomingEmail;
import com.example.myagentclient.service.SupportAgent;
import com.example.myagentclient.service.SupportMailSender;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 主要實作：將郵件交給 {@link SupportAgent}（LLM + MCP 工具）自主處理並回覆。
 * {@code @Primary} 確保優先於 {@link LoggingEmailHandler} 注入；例外時回傳 {@code false} 觸發重試。
 */
@Component
@Primary
@Slf4j
@AllArgsConstructor
public class AgentEmailHandler implements EmailHandler {

    private final SupportAgent agent;
    private final SupportMailSender supportMailSender;

    @Override
    public boolean handle(IncomingEmail email) {
        log.info("將來自 {} 的郵件（主旨：\"{}\"）交給支援 Agent 處理", email.from(), email.subject());

        try {
            // 1. LLM 搭配 MCP 工具分析郵件、呼叫工具，產生結構化回應（replyBody + operatorSummary）
            AgentResponse response = agent.resolve(email);

            log.info("""

                            === Agent 處理結果 ===
                            寄件人 : {}
                            主旨   : {}
                            摘要   :
                            {}
                            ====================""",
                    email.from(), email.subject(), response.operatorSummary());

            // 2. 將 Agent 草擬的回覆寄出，In-Reply-To 標頭確保串接在原信件的回覆串上
            supportMailSender.sendReply(email, response);

            return true; // 郵件保持已讀
        } catch (Exception e) {
            // LLM 逾時、MCP 工具失敗等暫時性錯誤；回傳 false 讓郵件保持未讀，等待下次輪詢重試
            log.error("Agent 處理來自 {} 的郵件失敗（主旨：\"{}\"），將重試",
                    email.from(), email.subject(), e);
            return false;
        }
    }
}
