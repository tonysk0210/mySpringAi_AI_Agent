package com.example.mcpserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "PRODUCTS")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sku;
    private String name;

    @Column(columnDefinition = "TEXT") // TEXT 類型可存放大量文字，比預設的 VARCHAR(255) 上限大得多，適合商品描述。
    private String description;

    private String category;
    private BigDecimal price;
    private String currency;

    /**
     * 與商品類型無關的規格屬性袋，以 JSON 格式存入資料庫。
     * 在 Java 層以原始字串呈現，不做任何解析，
     * 讓電器（電壓）、書籍（頁數）、服飾（尺寸）等各類商品的規格都能原封不動地傳遞給 Agent。
     */
    @Column(columnDefinition = "json") // JSON 類型可存放 JSON 格式的數據，適合商品規格。（資料庫會驗證內容是否合法 JSON）
    private String specifications;

    private Integer warrantyMonths;
    private Integer stockQuantity;
}
