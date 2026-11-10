package com.nexus.auction.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionResult(String id, String productId, String sellerId, BigDecimal startingPrice,
                             BigDecimal bidIncrement, BigDecimal currentHighestBid, String currentHighestBidderId,
                             String status, Instant startTime, Instant endTime, int extensionCount,
                             String winnerId, BigDecimal finalPrice, Instant paymentDeadline, Instant paidAt,
                             boolean hasReserve, boolean reserveMet) {

    // The reserve price itself is never part of a result that bidders can read: only whether there is one
    // and whether it has been met. reserveMet is true when there is no reserve.
    public AuctionResult(String id, String productId, String sellerId, BigDecimal startingPrice,
                         BigDecimal bidIncrement, BigDecimal currentHighestBid, String currentHighestBidderId,
                         String status, Instant startTime, Instant endTime, int extensionCount,
                         String winnerId, BigDecimal finalPrice, Instant paymentDeadline, Instant paidAt) {
        this(id, productId, sellerId, startingPrice, bidIncrement, currentHighestBid, currentHighestBidderId, status,
                startTime, endTime, extensionCount, winnerId, finalPrice, paymentDeadline, paidAt, false, true);
    }
}
