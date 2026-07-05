package com.example.mcpserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "PAYMENTS")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Payment.java — 外鍵在這裡，這是「主控方」
    @ManyToOne(fetch = FetchType.LAZY) // 多筆付款屬於同一張訂單，不會預先載入 CustomerOrder，等真正存取時才查詢，避免不必要的 JOIN
    @JoinColumn(name = "order_id") // ← 資料庫真實存在的欄位
    private CustomerOrder order;

    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    private Enums.PaymentStatus status;

    private LocalDateTime chargedAt;
}
