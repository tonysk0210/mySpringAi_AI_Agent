package com.example.mcpserver.entity;

/**
 * 業務領域列舉，對應資料庫的 ENUM 欄位。以 STRING 方式持久化，
 * 確保資料庫值與 Java 名稱一一對應，並讓 MCP 工具 schema 能向 Agent 廣播每個欄位的合法值。
 */
public final class Enums {

    private Enums() {
    }

    public enum LoyaltyTier {
        STANDARD, SILVER, GOLD, PLATINUM
    }

    public enum OrderStatus {
        PENDING, PAID, SHIPPED, DELIVERED, CANCELLED, RETURNED
    }

    public enum PaymentStatus {
        AUTHORIZED, CAPTURED, FAILED, REFUNDED, PARTIALLY_REFUNDED
    }

    public enum RefundType {
        GOODWILL, DUPLICATE_CHARGE, WARRANTY, RETURN, OTHER
    }

    public enum RefundStatus {
        REQUESTED, APPROVED, PROCESSED, REJECTED
    }

    public enum Channel {
        EMAIL, CHAT, PHONE
    }

    public enum Intent {
        REFUND_REQUEST, PRESALES_QUESTION, BILLING_ISSUE,
        WARRANTY_CLAIM, COMPLAINT, GENERAL, OTHER
    }

    public enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE, ANGRY
    }

    public enum TicketStatus {
        OPEN, RESOLVED, ESCALATED
    }
}
