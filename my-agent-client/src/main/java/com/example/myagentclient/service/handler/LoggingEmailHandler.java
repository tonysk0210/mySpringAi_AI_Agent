package com.example.myagentclient.service.handler;

import com.example.myagentclient.model.IncomingEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 預設的 {@link EmailHandler}，單純將收件匣收到的郵件記錄到日誌。
 * <p>
 * 作為基準實作，讓收件監控迴圈可以獨立驗證。
 * 待 Agent 的真實行為（分類、LLM 草擬回覆、工具呼叫...）接入後，再替換或包裝此類別。
 */
// 備用／驗證用
@Slf4j
@Component
public class LoggingEmailHandler implements EmailHandler {

    @Override
    public boolean handle(IncomingEmail email) {
        log.info("""
                        === 新郵件 ===
                        寄件人 : {}
                        收件人 : {}
                        主旨   : {}
                        內文   :
                        {}
                        =============""",
                email.from(), email.to(), email.subject(), email.body()); // 只印 log
        return true; // 永遠成功
    }
}
