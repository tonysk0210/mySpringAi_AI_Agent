package com.example.mcpserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "CUSTOMERS")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private String email;
    private String phone;
    private String preferredLanguage;

    // 告訴 JPA 把 enum 值以字串名稱存入資料庫，而不是用數字索引（EnumType.ORDINAL 預設）
    @Enumerated(EnumType.STRING)
    private Enums.LoyaltyTier loyaltyTier;

    // 設定 createdAt 欄位為不可插入和更新
    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
