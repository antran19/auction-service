package com.nexus.auction.api.dto.response;

import com.nexus.auction.application.usecase.AutoBidResult;

import java.math.BigDecimal;
import java.time.Instant;

public record AutoBidResponse(String id, String auctionId, String bidderId, BigDecimal maxAmount, Instant updatedAt) {

    public static AutoBidResponse from(AutoBidResult result) {
        return new AutoBidResponse(result.id(), result.auctionId(), result.bidderId(), result.maxAmount(),
                result.updatedAt());
    }
}
