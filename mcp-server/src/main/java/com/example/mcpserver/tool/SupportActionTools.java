package com.example.mcpserver.tool;


import com.example.mcpserver.dto.SupportDtos;
import com.example.mcpserver.entity.*;
import com.example.mcpserver.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 寫入型 MCP 工具：Agent 決定處理方式後所採取的行動——
 * 發起退款（並將對應付款標記為已退款），以及將此次互動記錄為工單。
 * 此類別中的所有方法均會寫入資料庫。
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SupportActionTools {

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final SupportTicketRepository tickets;

    /**
     * ┌────────────────────┬──────────┬───────────────────────────┐
     * │                    │ 一般退款   │       重複扣款退款          │
     * ├────────────────────┼──────────┼───────────────────────────┤
     * │ 需要知道訂單?        │ 是        │ 是                        │
     * ├────────────────────┼──────────┼───────────────────────────┤
     * │ 需要指定哪筆付款?     │ 不需要    │ 需要，因為有多筆 CAPTURED    │
     * ├────────────────────┼──────────┼───────────────────────────┤
     * │ transactionRef     │ 不傳      │  傳入                      │
     * └────────────────────┴──────────┴───────────────────────────┘
     *
     */
    /* 資料寫入流程圖

      issueRefund 呼叫
              │
              ├─ 寫入 REFUNDS 表（一定發生）
              │       └─ order_id, amount, currency, reason, refundType, status=PROCESSED
              │
              └─ 若有 transactionRef（選擇性）
                      ├─ 更新 PAYMENTS 表：status → REFUNDED
                      └─ 寫入 REFUNDS.payment_id（建立關聯）
    */
    // ---------------------------------------------------------------------
    // 執行行動：發起退款
    // ---------------------------------------------------------------------
    @McpTool(name = "issue_refund",
            description = "對訂單發起退款並記錄。可選擇性地透過交易參考號綁定特定一筆付款（例如退還重複扣款中的其中一筆），該付款狀態將被標記為 REFUNDED。此工具會執行真實操作——僅在確認退款合理後才呼叫。")
    public SupportDtos.RefundResult issueRefund(
            @McpToolParam(description = "要退款的訂單編號，例如 4471")
            String orderNumber,
            @McpToolParam(description = "退款金額，例如 199.99")
            BigDecimal amount,
            @McpToolParam(description = "退款類型，說明退款的性質")
            Enums.RefundType refundType,
            @McpToolParam(description = "退款原因的簡短說明")
            String reason,
            @McpToolParam(required = false, description = "要沖銷的特定付款交易參考號；可選。若提供，該筆付款將被標記為 REFUNDED。") // 對應 PAYMENTS 資料表的 transaction_ref 欄位，針對重複扣款退款做沖銷
            String transactionRef) {

        // 1. 確認訂單存在
        CustomerOrder order = requireOrder(orderNumber);

        // 2. 建立 Refund entity
        Refund refund = new Refund();
        refund.setOrder(order);
        refund.setAmount(amount);
        refund.setCurrency(order.getCurrency()); // ← 幣別從訂單取，不讓 Agent 自行傳
        refund.setReason(reason);
        refund.setRefundType(refundType);
        refund.setStatus(Enums.RefundStatus.PROCESSED);

        // 3. 如果有提供 transactionRef，則查找該筆付款並標記為 REFUNDED
        if (transactionRef != null && !transactionRef.isBlank()) {
            Payment payment = payments.findByTransactionRef(transactionRef.trim())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "找不到此交易參考號對應的付款記錄：" + transactionRef));
            payment.setStatus(Enums.PaymentStatus.REFUNDED); // ← 標記 REFUND 狀態
            refund.setPayment(payment); // ← 建立關聯
        }

        // 4. 寫入資料庫並回傳結果
        Refund saved = refunds.save(refund);
        String summary = "退款 %s %s 已完成，訂單 %s（退款類型：%s）：%s"
                .formatted(amount, order.getCurrency(), orderNumber, refundType, reason);
        return new SupportDtos.RefundResult(saved.getId(), orderNumber, amount, order.getCurrency(),
                refundType.name(), saved.getStatus().name(), summary);
    }

    // ---------------------------------------------------------------------
    // 記錄處理結果：建立工單
    // ---------------------------------------------------------------------
    @McpTool(name = "log_support_ticket",
            description = "將此次電子郵件互動記錄為工單，保存原始訊息、偵測語言、分類後的意圖與情緒，以及處理結果。請最後呼叫此工具以記錄決策與執行內容。")
    public SupportDtos.TicketLogResult logSupportTicket(
            @McpToolParam(description = "工單對應顧客的電子郵件地址")
            String customerEmail,
            @McpToolParam(description = "顧客的原始訊息（逐字保存）")
            String rawMessage,
            @McpToolParam(description = "電子郵件的意圖分類")
            Enums.Intent intent,
            @McpToolParam(description = "顧客的情緒分類")
            Enums.Sentiment sentiment,
            @McpToolParam(description = "電子郵件的簡短主旨摘要")
            String subject,
            @McpToolParam(description = "偵測到的語言代碼，例如 en、hi 或 en+hi")
            String detectedLanguage,
            @McpToolParam(description = "決策內容與執行結果，以及回覆給顧客的重點")
            String resolution,
            @McpToolParam(required = false, description = "相關訂單編號（若有）")
            String orderNumber,
            @McpToolParam(required = false, description = "相關商品 SKU（若有）")
            String sku) {

        // 1. 確認顧客存在
        Customer customer = customers.findByEmailIgnoreCase(customerEmail.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "找不到此電子郵件對應的顧客帳號：" + customerEmail));

        // 2. 建立 SupportTicket entity
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomer(customer);
        ticket.setChannel(Enums.Channel.EMAIL); // ← 固定為 EMAIL 整個系統只處理電郵，無需 Agent 判斷
        ticket.setRawMessage(rawMessage);
        ticket.setIntent(intent);
        ticket.setSentiment(sentiment);
        ticket.setSubject(subject);
        ticket.setDetectedLanguage(detectedLanguage);
        ticket.setResolution(resolution);
        ticket.setStatus(Enums.TicketStatus.RESOLVED); // ← 固定為已解決 Agent 呼叫此工具即代表已完成處理，不存在「記錄中但未解決」的狀態
        ticket.setResolvedAt(LocalDateTime.now());

        // 3. 選擇性關聯訂單與商品（若有）JPA 會自動從這兩個物件取出 id，寫入外鍵欄位：order_id 與 product_id
        if (orderNumber != null && !orderNumber.isBlank()) {
            orders.findByOrderNumber(orderNumber.trim()).ifPresent(ticket::setOrder);
        }
        if (sku != null && !sku.isBlank()) {
            products.findBySkuIgnoreCase(sku.trim()).ifPresent(ticket::setProduct);
        }

        SupportTicket saved = tickets.save(ticket);
        return new SupportDtos.TicketLogResult(saved.getId(), saved.getStatus().name(),
                "已建立工單 #%d，顧客：%s。".formatted(saved.getId(), customerEmail));
    }

    // ---------------------------------------------------------------------
    // 內部輔助方法
    // ---------------------------------------------------------------------

    private CustomerOrder requireOrder(String orderNumber) {
        return orders.findByOrderNumber(orderNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("找不到此訂單編號：" + orderNumber));
    }
}
