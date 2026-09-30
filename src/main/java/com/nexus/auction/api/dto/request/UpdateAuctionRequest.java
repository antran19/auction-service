package com.nexus.auction.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateAuctionRequest(
        @NotNull @DecimalMin(value = "0.01", message = "startingPrice must be greater than zero") BigDecimal startingPrice,
        @NotNull @DecimalMin(value = "0.01", message = "bidIncrement must be greater than zero") BigDecimal bidIncrement,
        @NotNull Instant startTime,
        @NotNull Instant endTime) {
}
