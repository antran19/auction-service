package com.nexus.auction.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

// One auction in "the auctions I am bidding in". myStatus is where this person stands:
// LEADING / OUTBID while the auction runs, WON / LOST once it has ended, CANCELLED if it was cancelled.
// myMaxAutoBid is null when the person has not set a maximum on this auction.
public record MyBiddingItem(String auctionId, String productId, String productName, String auctionStatus, String myStatus,
                            Instant endTime, BigDecimal currentHighestBid, BigDecimal myHighestBid,
                            BigDecimal myMaxAutoBid) {
}
