package com.kwonka.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number")
    private String orderNumber;

    @Column(name = "customer_id")
    private Long customerId;

    @ManyToOne
    @JoinColumn(name = "coffee_shop_id", nullable = false)
    private CoffeeShop coffeeShop;

    @Column(name = "coffee_type")
    private String coffeeType;

    @Column(name = "size")
    private String size;

    @Column(name = "milk_type")
    private String milkType;

    @Column(name = "syrup_type")
    private String syrupType;

    @Column(name = "total_price")
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private OrderStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum OrderStatus {
        PENDING,
        IN_PREPARATION,
        READY,
        COMPLETED,
        CANCELLED
    }
}