package com.example.mcpserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "SUPPORT_TICKETS")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Enumerated(EnumType.STRING)
    private Enums.Channel channel;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String rawMessage;

    private String detectedLanguage;

    @Enumerated(EnumType.STRING)
    private Enums.Intent intent;

    @Enumerated(EnumType.STRING)
    private Enums.Sentiment sentiment;

    @Enumerated(EnumType.STRING)
    private Enums.TicketStatus status;

    @Column(columnDefinition = "TEXT")
    private String resolution;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
