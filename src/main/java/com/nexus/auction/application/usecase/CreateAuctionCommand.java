package com.nexus.auction.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateAuctionCommand(String productId, String sellerId, BigDecimal startingPrice,
                                    BigDecimal bidIncrement, BigDecimal reservePrice, Instant startTime,
                                    Instant endTime, String sellerTrustLevel) {

    // Without a reserve.
    public CreateAuctionCommand(String productId, String sellerId, BigDecimal startingPrice,
                                BigDecimal bidIncrement, Instant startTime, Instant endTime, String sellerTrustLevel) {
        this(productId, sellerId, startingPrice, bidIncrement, null, startTime, endTime, sellerTrustLevel);
    }
}
