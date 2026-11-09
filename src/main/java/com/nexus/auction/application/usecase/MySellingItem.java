package com.nexus.auction.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

public record MySellingItem(String auctionId, String productId, String productName, String status,
                            BigDecimal startingPrice, BigDecimal bidIncrement, BigDecimal currentHighestBid,
                            Instant startTime, Instant endTime, long bidCount, long bidderCount,
                            String winnerId, BigDecimal finalPrice, Instant paymentDeadline, Instant paidAt) {
}
