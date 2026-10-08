package com.nexus.auction.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateAuctionCommand(String productId, String sellerId, BigDecimal startingPrice,
                                    BigDecimal bidIncrement, Instant startTime, Instant endTime,
                                    String sellerTrustLevel) {
}
