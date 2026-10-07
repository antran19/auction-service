package com.nexus.auction.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auctions")
public class AuctionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "seller_id", nullable = false)
    private String sellerId;

    @Column(name = "starting_price", nullable = false)
    private BigDecimal startingPrice;

    @Column(name = "bid_increment", nullable = false)
    private BigDecimal bidIncrement;

    @Column(name = "current_highest_bid")
    private BigDecimal currentHighestBid;

    @Column(name = "current_highest_bidder_id")
    private String currentHighestBidderId;

    @Column(nullable = false)
    private String status;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount;

    @Column(name = "winner_id")
    private String winnerId;

    @Column(name = "final_price")
    private BigDecimal finalPrice;

    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Column(name = "payment_timeout_emitted", nullable = false)
    private boolean paymentTimeoutEmitted;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AuctionJpaEntity() {
    }

    public AuctionJpaEntity(UUID id, UUID productId, String sellerId, BigDecimal startingPrice,
                             BigDecimal bidIncrement, BigDecimal currentHighestBid, String currentHighestBidderId,
                             String status, Instant startTime, Instant endTime, int extensionCount, String winnerId,
                             BigDecimal finalPrice, Instant paymentDeadline, boolean paymentTimeoutEmitted,
                             Instant paidAt, Instant createdAt, Instant updatedAt) {
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

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public String getSellerId() { return sellerId; }
    public BigDecimal getStartingPrice() { return startingPrice; }
    public BigDecimal getBidIncrement() { return bidIncrement; }
    public BigDecimal getCurrentHighestBid() { return currentHighestBid; }
    public String getCurrentHighestBidderId() { return currentHighestBidderId; }
    public String getStatus() { return status; }
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
