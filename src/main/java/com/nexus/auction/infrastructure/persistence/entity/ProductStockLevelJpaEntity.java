package com.nexus.auction.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_stock_levels")
public class ProductStockLevelJpaEntity {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    protected ProductStockLevelJpaEntity() {
    }

    public String getProductId() { return productId; }
    public int getAvailableQuantity() { return availableQuantity; }
    public Instant getAsOf() { return asOf; }
}
