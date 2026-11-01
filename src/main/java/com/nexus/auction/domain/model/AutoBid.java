package com.nexus.auction.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// A bidder's standing instruction on one auction: keep bidding for me, one increment at a time,
// up to maxAmount. createdAt is kept when the maximum is raised, because it breaks ties.
public class AutoBid {

    private final String id;
    private final String auctionId;
    private final String bidderId;
    private final BigDecimal maxAmount;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AutoBid(String id, String auctionId, String bidderId, BigDecimal maxAmount,
                    Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxAmount = maxAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static AutoBid create(String auctionId, String bidderId, BigDecimal maxAmount) {
        Instant now = Instant.now();
        return new AutoBid(UUID.randomUUID().toString(), auctionId, bidderId, maxAmount, now, now);
    }

    public static AutoBid reconstitute(String id, String auctionId, String bidderId, BigDecimal maxAmount,
                                       Instant createdAt, Instant updatedAt) {
        return new AutoBid(id, auctionId, bidderId, maxAmount, createdAt, updatedAt);
    }

    public AutoBid withMaxAmount(BigDecimal newMaxAmount) {
        return new AutoBid(id, auctionId, bidderId, newMaxAmount, createdAt, Instant.now());
    }

    public String getId() { return id; }
    public String getAuctionId() { return auctionId; }
    public String getBidderId() { return bidderId; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
