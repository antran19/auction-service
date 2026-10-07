package com.nexus.auction.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionResponse(String id, String productId, String sellerId, BigDecimal startingPrice,
                               BigDecimal bidIncrement, BigDecimal currentHighestBid, String currentHighestBidderId,
                               String status, Instant startTime, Instant endTime, int extensionCount,
                               String winnerId, BigDecimal finalPrice, Instant paymentDeadline, Instant paidAt) {
}
