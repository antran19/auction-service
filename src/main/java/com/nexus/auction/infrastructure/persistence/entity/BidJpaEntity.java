package com.nexus.auction.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bids")
public class BidJpaEntity {

    @Id
    private UUID id;

    @Column(name = "auction_id", nullable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false)
    private String bidderId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    protected BidJpaEntity() {
    }

    public BidJpaEntity(UUID id, UUID auctionId, String bidderId, BigDecimal amount, Instant placedAt) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.placedAt = placedAt;
    }

    public UUID getId() { return id; }
    public UUID getAuctionId() { return auctionId; }
    public String getBidderId() { return bidderId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getPlacedAt() { return placedAt; }
}
