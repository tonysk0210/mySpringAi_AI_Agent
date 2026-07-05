package com.example.mcpserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "REFUNDS")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CustomerOrder order; // ← 一定有，退款必須對應某張訂單

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment; // 僅重複扣款退款時有值（對應傳入的 transactionRef），一般退款為 null

    private BigDecimal amount;
    private String currency;
    private String reason;

    @Enumerated(EnumType.STRING)
    private Enums.RefundType refundType;

    @Enumerated(EnumType.STRING)
    private Enums.RefundStatus status;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
