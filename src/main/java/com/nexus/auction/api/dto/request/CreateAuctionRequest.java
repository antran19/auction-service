package com.nexus.auction.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateAuctionRequest(
        @NotBlank String productId,
        @NotNull @DecimalMin(value = "0.01", message = "startingPrice must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "startingPrice must have at most 10 digits and 2 decimals") BigDecimal startingPrice,
        @NotNull @DecimalMin(value = "0.01", message = "bidIncrement must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "bidIncrement must have at most 10 digits and 2 decimals") BigDecimal bidIncrement,
        @DecimalMin(value = "0.01", message = "reservePrice must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "reservePrice must have at most 10 digits and 2 decimals") BigDecimal reservePrice,
        @NotNull Instant startTime,
        @NotNull Instant endTime) {
}
