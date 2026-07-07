package com.example.myagentclient.service.handler;

import com.example.myagentclient.model.IncomingEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 備用實作：只將郵件印到日誌，供開發期間在無需 AI 的情況下驗證收件流程。
 */
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
                email.from(), email.to(), email.subject(), email.body());
        return true; // 永遠回傳成功，郵件保持已讀
    }
}
