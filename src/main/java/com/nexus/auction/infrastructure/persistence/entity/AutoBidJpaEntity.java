package com.nexus.auction.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auto_bids")
public class AutoBidJpaEntity {

    @Id
    private UUID id;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false)
    private String bidderId;

    @Column(name = "max_amount", nullable = false)
    private BigDecimal maxAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AutoBidJpaEntity() {
    }

    public AutoBidJpaEntity(UUID id, UUID auctionId, String bidderId, BigDecimal maxAmount,
                            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxAmount = maxAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getAuctionId() { return auctionId; }
    public String getBidderId() { return bidderId; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
