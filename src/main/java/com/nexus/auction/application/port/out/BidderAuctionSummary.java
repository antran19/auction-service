package com.nexus.auction.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;

// One auction a person has bid on: their highest bid there and when they last bid.
public record BidderAuctionSummary(String auctionId, BigDecimal myHighestBid, Instant lastBidAt) {
}
