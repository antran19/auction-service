package com.nexus.auction.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "product_snapshots")
public class ProductSnapshotJpaEntity {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductSnapshotJpaEntity() {
    }

    public ProductSnapshotJpaEntity(String productId, String sellerId, String status, Instant updatedAt) {
        this.productId = productId;
        this.sellerId = sellerId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public String getProductId() { return productId; }
    public String getSellerId() { return sellerId; }
    public String getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
}
