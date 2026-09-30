package com.nexus.auction.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

public record BidResult(String id, String auctionId, String bidderId, BigDecimal amount, Instant placedAt) {
}
