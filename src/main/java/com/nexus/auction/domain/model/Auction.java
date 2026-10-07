package com.nexus.auction.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Auction {

    private final String id;
    private final String productId;
    private final String sellerId;
    private final BigDecimal startingPrice;
    private final BigDecimal bidIncrement;
    private final BigDecimal currentHighestBid;
    private final String currentHighestBidderId;
    private final AuctionStatus status;
    private final Instant startTime;
    private final Instant endTime;
    private final int extensionCount;
    private final String winnerId;
    private final BigDecimal finalPrice;
    private final Instant paymentDeadline;
    private final boolean paymentTimeoutEmitted;
    private final Instant paidAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Auction(String id, String productId, String sellerId, BigDecimal startingPrice,
                     BigDecimal bidIncrement, BigDecimal currentHighestBid, String currentHighestBidderId,
                     AuctionStatus status, Instant startTime, Instant endTime, int extensionCount,
                     String winnerId, BigDecimal finalPrice, Instant paymentDeadline,
                     boolean paymentTimeoutEmitted, Instant paidAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.productId = productId;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
        this.bidIncrement = bidIncrement;
        this.currentHighestBid = currentHighestBid;
        this.currentHighestBidderId = currentHighestBidderId;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.extensionCount = extensionCount;
        this.winnerId = winnerId;
        this.finalPrice = finalPrice;
        this.paymentDeadline = paymentDeadline;
        this.paymentTimeoutEmitted = paymentTimeoutEmitted;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Auction create(String productId, String sellerId, BigDecimal startingPrice,
                                  BigDecimal bidIncrement, Instant startTime, Instant endTime) {
        Instant now = Instant.now();
        return new Auction(UUID.randomUUID().toString(), productId, sellerId, startingPrice, bidIncrement,
                null, null, AuctionStatus.PENDING, startTime, endTime, 0, null, null, null, false, null, now, now);
    }

    public static Auction reconstitute(String id, String productId, String sellerId, BigDecimal startingPrice,
                                        BigDecimal bidIncrement, BigDecimal currentHighestBid,
                                        String currentHighestBidderId, AuctionStatus status, Instant startTime,
                                        Instant endTime, int extensionCount, String winnerId, BigDecimal finalPrice,
                                        Instant paymentDeadline, boolean paymentTimeoutEmitted, Instant paidAt,
                                        Instant createdAt, Instant updatedAt) {
        return new Auction(id, productId, sellerId, startingPrice, bidIncrement, currentHighestBid,
                currentHighestBidderId, status, startTime, endTime, extensionCount, winnerId, finalPrice,
                paymentDeadline, paymentTimeoutEmitted, paidAt, createdAt, updatedAt);
    }

    public Auction withStatus(AuctionStatus newStatus) {
        return new Auction(id, productId, sellerId, startingPrice, bidIncrement, currentHighestBid,
                currentHighestBidderId, newStatus, startTime, endTime, extensionCount, winnerId, finalPrice,
                paymentDeadline, paymentTimeoutEmitted, paidAt, createdAt, Instant.now());
    }

    public Auction withDetails(BigDecimal newStartingPrice, BigDecimal newBidIncrement,
                                Instant newStartTime, Instant newEndTime) {
        return new Auction(id, productId, sellerId, newStartingPrice, newBidIncrement, currentHighestBid,
                currentHighestBidderId, status, newStartTime, newEndTime, extensionCount, winnerId, finalPrice,
                paymentDeadline, paymentTimeoutEmitted, paidAt, createdAt, Instant.now());
    }

    public Auction withBid(String bidderId, BigDecimal amount) {
        return new Auction(id, productId, sellerId, startingPrice, bidIncrement, amount, bidderId,
                status, startTime, endTime, extensionCount, winnerId, finalPrice, paymentDeadline,
                paymentTimeoutEmitted, paidAt, createdAt, Instant.now());
    }

    public Auction withExtendedEndTime(Instant newEndTime) {
        return new Auction(id, productId, sellerId, startingPrice, bidIncrement, currentHighestBid,
                currentHighestBidderId, status, startTime, newEndTime, extensionCount + 1, winnerId, finalPrice,
                paymentDeadline, paymentTimeoutEmitted, paidAt, createdAt, Instant.now());
    }

    public Auction withSettlement(String newWinnerId, BigDecimal newFinalPrice, Instant newPaymentDeadline) {
        return new Auction(id, productId, sellerId, startingPrice, bidIncrement, currentHighestBid,
                currentHighestBidderId, AuctionStatus.ENDED, startTime, endTime, extensionCount, newWinnerId,
                newFinalPrice, newPaymentDeadline, paymentTimeoutEmitted, paidAt, createdAt, Instant.now());
    }

    public Auction withPaymentTimeoutEmitted() {
        return new Auction(id, productId, sellerId, startingPrice, bidIncrement, currentHighestBid,
                currentHighestBidderId, status, startTime, endTime, extensionCount, winnerId, finalPrice,
                paymentDeadline, true, paidAt, createdAt, Instant.now());
    }

    public Auction markPaid(Instant paidAt) {
        return new Auction(id, productId, sellerId, startingPrice, bidIncrement, currentHighestBid,
                currentHighestBidderId, status, startTime, endTime, extensionCount, winnerId, finalPrice,
                paymentDeadline, paymentTimeoutEmitted, paidAt, createdAt, Instant.now());
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public String getSellerId() { return sellerId; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public BigDecimal getBidIncrement() { return bidIncrement; }
    public BigDecimal getCurrentHighestBid() { return currentHighestBid; }
    public String getCurrentHighestBidderId() { return currentHighestBidderId; }
    public AuctionStatus getStatus() { return status; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public int getExtensionCount() { return extensionCount; }
    public String getWinnerId() { return winnerId; }
    public BigDecimal getFinalPrice() { return finalPrice; }
    public Instant getPaymentDeadline() { return paymentDeadline; }
    public boolean isPaymentTimeoutEmitted() { return paymentTimeoutEmitted; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
