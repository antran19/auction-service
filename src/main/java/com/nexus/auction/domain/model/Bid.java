package com.nexus.auction.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Bid {

    private final String id;
    private final String auctionId;
    private final String bidderId;
    private final BigDecimal amount;
    private final Instant placedAt;

    private Bid(String id, String auctionId, String bidderId, BigDecimal amount, Instant placedAt) {
        this.id = id;
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
        this.placedAt = placedAt;
    }

    public static Bid create(String auctionId, String bidderId, BigDecimal amount) {
        return new Bid(UUID.randomUUID().toString(), auctionId, bidderId, amount, Instant.now());
    }

    public static Bid reconstitute(String id, String auctionId, String bidderId, BigDecimal amount, Instant placedAt) {
        return new Bid(id, auctionId, bidderId, amount, placedAt);
    }

    public String getId() { return id; }
    public String getAuctionId() { return auctionId; }
    public String getBidderId() { return bidderId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getPlacedAt() { return placedAt; }
}
