package com.example.myagentclient.service.handler;

import com.example.myagentclient.model.AgentResponse;
import com.example.myagentclient.model.IncomingEmail;
import com.example.myagentclient.service.SupportAgent;
import com.example.myagentclient.service.SupportMailSender;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 真正的 {@link EmailHandler}：將每封新郵件交給 {@link SupportAgent}，
 * 讓 LLM 搭配 MCP 工具自主解決問題。
 * <p>
 * 標記 {@link Primary}，使 {@code InboxMonitor} 優先注入此類別，而非備用的 {@link LoggingEmailHandler}。
 * 若 Agent 拋出例外，回傳 {@code false} 讓郵件保持未讀，等待下次輪詢重試。
 */
@Component
@Primary
@Slf4j
@AllArgsConstructor
public class AgentEmailHandler implements EmailHandler {

    private final SupportAgent agent;
    private final SupportMailSender mailSender;

    @Override
    public boolean handle(IncomingEmail email) {
        log.info("將來自 {} 的郵件（主旨：\"{}\"）交給支援 Agent 處理", email.from(), email.subject());

        try {
            // 1. 交給 Agent 處理
            AgentResponse response = agent.resolve(email); // 交給 AI 處理
            log.info("""
                            === Agent 處理結果 ===
                            寄件人 : {}
                            主旨   : {}
                            摘要   :
                            {}
                            ====================""",
                    email.from(), email.subject(), response.operatorSummary());

            // 2. 將草擬好的回覆寄回給客戶，並串接在原信件的回覆串上。
            mailSender.sendReply(email, response); // 回信給客戶
            return true;
        } catch (Exception e) {
            // 任何錯誤（LLM/MCP 暫時失敗、工具呼叫失敗等）都會到這裡。
            // 保持郵件未讀，讓下次輪詢重試。
            log.error("Agent 處理來自 {} 的郵件失敗（主旨：\"{}\"），將重試",
                    email.from(), email.subject(), e);
            return false;
        }
    }
}
