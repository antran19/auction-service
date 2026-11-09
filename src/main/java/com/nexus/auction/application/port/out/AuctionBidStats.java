package com.nexus.auction.application.port.out;

// How lively one auction is: every bid on it (the ones the system placed for a maximum count too) and
// how many different people placed them.
public record AuctionBidStats(String auctionId, long bidCount, long bidderCount) {
}
