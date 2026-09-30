package com.nexus.auction.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record BidResponse(String id, String auctionId, String bidderId, BigDecimal amount, Instant placedAt) {
}
