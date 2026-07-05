package com.example.mcpserver.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 對應 {@code ORDERS} 資料表。類別名稱使用 {@code CustomerOrder} 而非 {@code Order}，
 * 因為 {@code Order} 是 SQL 保留字，同時也是過於泛用的命名。
 */
@Getter
@Setter
@Entity
@Table(name = "ORDERS")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY) // 多張訂單屬於同一位顧客， 不會預先載入 Customer，等真正存取時才查詢，避免不必要的 JOIN
    @JoinColumn(name = "customer_id") // 外鍵欄位名稱是 customer_id
    private Customer customer;

    private LocalDate orderDate;

    @Enumerated(EnumType.STRING)
    private Enums.OrderStatus status;

    private String shippingAddress;
    private BigDecimal totalAmount;
    private String currency;

    // CustomerOrder.java — 這裡是「被動方」，沒有對應欄位 (mappedBy = "order" ← 告訴 JPA：去 OrderItem 的 order 欄位找關聯)
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY) // 一張訂單包含多個訂單項目，不會預先載入 OrderItem，等真正存取時才查詢，避免不必要的 JOIN　
    private List<OrderItem> items = new ArrayList<>(); // ← 純粹是 JPA 查出來幫你裝的集合，DB 沒有這欄

    // Payment.java — 這裡是「被動方」，沒有對應欄位 (mappedBy = "order" ← 告訴 JPA：去 Payment 的 order 欄位找關聯)
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY) // 一張訂單包含多個付款紀錄，不會預先載入 Payment，等真正存取時才查詢，避免不必要的 JOIN
    private List<Payment> payments = new ArrayList<>();  // ← 純粹是 JPA 查出來幫你裝的集合，DB 沒有這欄

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
/**
 * LAZY 載入的真正行為，什麼時候才真正發 SQL？
 * CustomerOrder order = orders.findById(1L).get();
 * <p>
 * // 此時還沒查 items，DB 沒有收到任何關於 ORDER_ITEMS 的 SQL
 * order.getItems();         // ← 呼叫這行的瞬間，才發出：SELECT * FROM ORDER_ITEMS WHERE order_id = 1
 */