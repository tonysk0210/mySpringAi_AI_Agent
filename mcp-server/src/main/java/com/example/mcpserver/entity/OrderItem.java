package com.example.mcpserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "ORDER_ITEMS")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // OrderItem.java — 外鍵在這裡，這是「主控方」
    @ManyToOne(fetch = FetchType.LAZY) // 多個訂單項目屬於同一張訂單，不會預先載入 CustomerOrder，等真正存取時才查詢，避免不必要的 JOIN
    @JoinColumn(name = "order_id") // ← 資料庫真實存在的欄位
    private CustomerOrder order;

    // OrderItem.java — 外鍵在這裡，這是「主控方」
    @ManyToOne(fetch = FetchType.LAZY) // 多個訂單項目屬於同一個商品，不會預先載入 Product，等真正存取時才查詢，避免不必要的 JOIN
    @JoinColumn(name = "product_id") // ← 資料庫真實存在的欄位
    private Product product;

    private Integer quantity;
    private BigDecimal unitPrice;
}

/**
 * 但要注意：JPA 只透過主控方來維護外鍵，所以新增關聯時必須設定主控方：
 * <p>
 * orderItem.setOrder(order);  // ← 這行才會實際寫入 order_id 到資料庫
 * // order.getItems().add(orderItem)  ← 這行只是更新記憶體，DB 不會變
 */