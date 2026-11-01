package com.nexus.auction.application.usecase;

import java.math.BigDecimal;
import java.time.Instant;

public record AutoBidResult(String id, String auctionId, String bidderId, BigDecimal maxAmount, Instant updatedAt) {
}
