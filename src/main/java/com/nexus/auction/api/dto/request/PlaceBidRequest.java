package com.nexus.auction.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlaceBidRequest(
        @NotNull @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "amount must have at most 10 digits and 2 decimals") BigDecimal amount) {
}
